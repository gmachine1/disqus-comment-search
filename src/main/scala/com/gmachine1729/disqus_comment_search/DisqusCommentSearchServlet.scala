package com.gmachine1729.disqus_comment_search

import java.io.PrintWriter
import java.net.URL
import java.util.Properties

import com.gmachine1729.disqus_comment_search.db.DatabaseSessionSupport
import com.gmachine1729.disqus_comment_search.models.{Tables, Visit}
import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import org.scalatra.forms._
import upickle.default._
import org.jsoup.Jsoup
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.ling.CoreAnnotations._
import org.scalatra.i18n.I18nSupport
import org.slf4j.LoggerFactory
import scalaj.http.{Http, HttpOptions}

import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._
import scala.util.{Success, Try}


class DisqusCommentSearchServlet extends ScalatraServlet with ScalateSupport with FormSupport with I18nSupport with SessionSupport
  with DatabaseSessionSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val API_KEY = "E8Uh5l5fHZ6gD8U3KycjAIAk46f68Zw7C6eW8WSjZvCLXebZ7p0r1yrYDrLilk2F"
  val BASE_URL = "https://disqus.com/api/3.0/timelines/activities"
  val INITIAL_LIMIT: Integer = 5
  val DEFAULT_LIMIT: Integer = 10
  val props = new Properties()
  props.put("annotators", "tokenize, ssplit, pos, lemma")
  props.put("pos.model", "/usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger")
  val pipeline = new StanfordCoreNLP(props)

  def time[R](logMsg: String)(block: => R): R= {
    val s = System.currentTimeMillis
    val res = block
    logger.info(String.format("%s in %s ms", logMsg, (System.currentTimeMillis - s).toString))
    res
  }

  def getUrl(username: String, cursor: String, limit: Integer): String = {
    String.format("%s?type=profile&index=comments&target=user:username:%s&cursor=%s&limit=%d&api_key=%s",
      BASE_URL, username, cursor, limit, API_KEY)
  }

  def containsSearchTerms(searchTermsLemmatized: Set[String], matchAll: Boolean)(comment: Comment): Int = {
    if (searchTermsLemmatized.isEmpty) 1
    else {
      val doc = new Annotation(Jsoup.parse(comment.message).text().toLowerCase)
      pipeline.annotate(doc)
      val sentences = doc.get(classOf[SentencesAnnotation])
      var numMatches = 0
      if (!matchAll) {
        for (sentence <- sentences; token <- sentence.get(classOf[TokensAnnotation])) {
          val lemma = token.get(classOf[LemmaAnnotation])
          if (searchTermsLemmatized.contains(lemma)) numMatches += 1
        }
        numMatches
      } else {
        val wordsInDoc = sentences.toIterable.map(_.get(classOf[TokensAnnotation]).map(_.get(classOf[LemmaAnnotation])).toIterable).flatten.toSet
        if (searchTermsLemmatized.subsetOf(wordsInDoc)) 1
        else 0
      }
    }
  }

  private def requestFilteredDisqusJson(username: String, cursor: String, limit: Int): String = {
    val url = getUrl(username, cursor, limit)
    time(String.format("Retrieved from %s", url)) {
      (new URL(url) #> String.format("python src/main/python/filter_json_response.py %s", username)).!!
    }
  }

  def downloadComments(username: String, numAccumResults: Int, searchTermsLemmatized: Set[String], limitParam: Int, commentDownloadLimit: Int, matchAllTerms: Boolean)(
      cursor: String, numDownloaded: Int, previousFilteredJson: Option[Future[String]])(implicit output: PrintWriter): (Int, Int) = {
    val commentsResponse = previousFilteredJson match {
      case Some(asyncRequest) => read[CommentsResponse](Await.result[String](asyncRequest, Duration.Inf))
      case _                  => read[CommentsResponse](requestFilteredDisqusJson(username, cursor, INITIAL_LIMIT))
    }
    val futureFilteredJson = commentsResponse.cursor.next match {
      case null       => None
      case cursorNext => Some(Future {
        requestFilteredDisqusJson(username, cursorNext, DEFAULT_LIMIT)
      })
    }
    logger.info(String.format("Deserialized %d comments.", new Integer(commentsResponse.response.objects.size)))
    val commentBatch = commentsResponse.response.objects.map(_._2).filter(
      comment => containsSearchTerms(searchTermsLemmatized, matchAllTerms)(comment) > 0).toSeq.sorted
    for (comment <- commentBatch) {
      output.write(comment.toHtml)
      output.write("<hr>")
      output.flush()
    }

    val numAccumResultsUpdated = numAccumResults + commentBatch.length
    val numDownloadedUpdated = numDownloaded + commentsResponse.response.objects.size

    def getTotals = {
      logger.info(String.format("%d search results, %d comments downloaded", new Integer(numAccumResultsUpdated), new Integer(numDownloadedUpdated)))
      (numAccumResultsUpdated, numDownloadedUpdated)
    }

    commentsResponse.cursor.next match {
      case null       => {
        getTotals
      }
      case cursorNext => {
        val remainingNumComments = commentDownloadLimit - numDownloadedUpdated
        if (remainingNumComments > 0)
          downloadComments(username, numAccumResultsUpdated, searchTermsLemmatized, getDisqusLimitParam(remainingNumComments), commentDownloadLimit, matchAllTerms)(
            cursorNext, numDownloadedUpdated, futureFilteredJson)
        else {
          getTotals
        }
      }
    }
  }

  private def usernameExists(username: String): Boolean = {
    val usernameUrl: String = String.format("https://disqus.com/by/%s/", username)
    val resp = Http(usernameUrl).option(HttpOptions.followRedirects(true)).asString
    resp.is2xx
  }

  private def getQueryTokens(query: String): Set[String] = {
    val queryStringAnnotation = new Annotation(query)
    pipeline.annotate(queryStringAnnotation)
    queryStringAnnotation.get(classOf[TokensAnnotation]).toSeq.map(_.lemma()).toSet
  }

  private def getDisqusLimitParam(remainingNumComments: Int): Int = {
    Math.min(DEFAULT_LIMIT, remainingNumComments)
  }

  post("/feedback") {
    contentType = "text/plain"
    val form = mapping(
      "name" -> label("Name", text(required, maxlength(100))),
      "email" -> label("Email", optional(text(pattern(".+@.+"), maxlength(40)))),
      "message" -> label("Message", text(required, maxlength(100000)))
    )(FeedbackForm.apply)
    val visitFuture: Future[Visit] = VisitLogger.genVisitRecord(request, VisitLogger.VisitType.FEEDBACK.id)

    def submitFeedback(feedbackFormValidated: FeedbackForm): String = {
      Future {
        val visit = Await.result(visitFuture, Duration.Inf)
        visit.name = Some(feedbackFormValidated.name)
        visit.email = feedbackFormValidated.email
        visit.message = Some(feedbackFormValidated.message)
        Visit.create(visit)
        println("logged feedback visit to db")
      }
      "Feedback successfully submitted, you might get a response from the creator of Disqus comment search."
    }

    def formErrorToHtmlResponse(hasErrors: Seq[(String, String)]): String = {
      val errorMsg = hasErrors.map(_._2).fold("")(_ ++ "\n" ++ _)
      errorMsg
    }
    validate(form)(formErrorToHtmlResponse, submitFeedback)
  }

  get("/search") {
    val startTime = System.currentTimeMillis()
    val visitFuture: Future[Visit] = VisitLogger.genVisitRecord(request, VisitLogger.VisitType.SEARCH.id)

    response.setHeader("Content-Type", "text/html")
    response.setHeader("Transfer-Encoding", "chunked")
    response.setStatus(200)

    implicit val out: PrintWriter = response.writer

    def formToHtmlResponse(validatedForm: ValidationForm): (ValidationForm, Unit) = {
      val username = validatedForm.username
      val query = validatedForm.query.getOrElse("").toLowerCase
      val commentDownloadLimit = validatedForm.comment_download_limit
      val matchAllTerms = validatedForm.match_all_terms
      if (!usernameExists(username)) {
        Future {
          val visit = Await.result(visitFuture, Duration.Inf)
          visit.setFormParameters(validatedForm)
          Visit.create(visit)
          logger.info("wrote no username found visit to db")
        }
        out.write(String.format("<div>Did not find any Disqus user with username <b>%s</b></div>", username))
        out.flush()
      } else {
        val cursor: String = ""
        val (numSearchResults, _) = downloadComments (username, 0, getQueryTokens (query), getDisqusLimitParam (commentDownloadLimit), commentDownloadLimit, matchAllTerms) (
          cursor, 0, None)
        Future {
          val visit = Await.result(visitFuture, Duration.Inf)
          visit.setFormParameters(validatedForm)
          visit.foundUser = true
          visit.numResults = Some(numSearchResults)
          visit.latency = Some((System.currentTimeMillis() - startTime)/1000.0)
          Visit.create(visit)
          logger.info("wrote returned search results visit to db")
        }
      }
      (validatedForm, {})
    }

    def formErrorToHtmlResponse(hasErrors: Seq[(String, String)]): (ValidationForm, Unit) = {
      Future {
        val visit = Await.result(visitFuture, Duration.Inf)
        Visit.create(visit)
        logger.info("wrote form validation error visit to db")
      }
      val limit = Try(params("comment_download_limit").toInt) match {
        case Success(value) => value
        case _ => 1000
      }
      val validationForm = ValidationForm(params("username"), Some(params("query")), limit, params("comment_download_limit").equals("on"))
      val errorMsg = hasErrors.map(_._2).fold("")(_ ++ "<br>" ++ _)
      (validationForm, {
        out.write(errorMsg)
        out.flush()
      })
    }

    val form = mapping(
      "username" -> label("Username", text(required, maxlength(100))),
      "query" -> label("Query", optional(text(required, maxlength(100)))),
      "comment_download_limit" -> label("Comment Download Limit", number()),
      "match_all_terms" -> label("Require match all terms", boolean())
    )(ValidationForm.apply)
    validate(form)(formErrorToHtmlResponse, formToHtmlResponse)
    ""
  }

  get("/") {
    contentType = "text/html"
    val visitFuture: Future[Visit] = VisitLogger.genVisitRecord(request, VisitLogger.VisitType.HOME_PAGE.id)
    val _ = Future {
      Visit.create(Await.result(visitFuture, Duration.Inf))
      logger.info("wrote home page visit to db")
    }
    ssp("/WEB-INF/templates/views/results.ssp", "form" -> ValidationForm("", Some(""), 1000, false), "htmlResponse" -> "")
  }
}
