package com.gmachine1729.disqus_comment_search

import java.net.URL
import java.util.Properties

import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import org.scalatra.forms._
import upickle.default._
import org.jsoup.Jsoup
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.ling.CoreAnnotations._
import org.scalatra.i18n.I18nSupport
import scalaj.http.Http

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._
import scala.util.{Try,Success,Failure}


class DisqusCommentSearchServlet extends ScalatraServlet with ScalateSupport with FormSupport with I18nSupport {
  val API_KEY = "E8Uh5l5fHZ6gD8U3KycjAIAk46f68Zw7C6eW8WSjZvCLXebZ7p0r1yrYDrLilk2F"
  val BASE_URL = "https://disqus.com/api/3.0/timelines/activities"
  val MIN_LIMIT: Integer = 5
  val MAX_LIMIT: Integer = 100
  val props = new Properties()
  props.put("annotators", "tokenize, ssplit, pos, lemma")
  val pipeline = new StanfordCoreNLP(props)

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

  def downloadComments(username: String, searchTermsLemmatized: Set[String], limitParam: Int, commentDownloadLimit: Int, matchAllTerms: Boolean)(
      cursor: String, numDownloaded: Int, accumComments: ArrayBuffer[Comment], prevCommentBatch: Option[Future[Iterable[Comment]]]): String = {
    val t0 = System.nanoTime()
    val filteredJson = (new URL(getUrl(username, cursor, limitParam)) #> String.format("python src/main/python/filter_json_response.py %s", username)).!!
    val t1 = System.nanoTime()
    val secondsTaken = (t1-t0) / 1000000000.0
    println("Url: " + getUrl(username, cursor, limitParam))
    println("Retrieving and filtering json took: " + secondsTaken)
    prevCommentBatch match {
      case Some(future) => {
        val t0 = System.nanoTime()
        val filteredCommentBatch = Await.result[Iterable[Comment]](future, Duration.Inf)
        val t1 = System.nanoTime()
        val secondsTaken = (t1-t0) / 1000000000.0
        println("Waited time for filter future: " + secondsTaken)
        accumComments.appendAll(filteredCommentBatch)
      }
      case None =>
    }
    val commentsResponse = read[CommentsResponse](filteredJson)
    val commentBatch = Future {
        commentsResponse.response.objects.map(_._2).filter(comment => containsSearchTerms(searchTermsLemmatized, matchAllTerms)(comment) > 0)
    }

    def commentsHtml: String = {
      val t2 = System.nanoTime()
      val filteredCommentBatch = Await.result[Iterable[Comment]](commentBatch, Duration.Inf)
      val t3 = System.nanoTime()
      val secondsTaken2 = (t3-t2) / 1000000000.0
      System.out.println("Waiting for final comment batch took: " + secondsTaken2)
      accumComments.appendAll(filteredCommentBatch)
      val t0 = System.nanoTime()
      //val htmlResponse = accumComments.sorted.map(_.toHtml).foldLeft("")(_ ++ "\n" ++ _)
      val htmlResponse = new StringBuffer(String.format("<h4><b>%d</b> results</h4><hr>", new Integer(accumComments.length)))
      println("ArrayBuffer[Comment] length: " + accumComments.length)
      for (comment <- accumComments.sorted) {
        htmlResponse.append(comment.toHtml)
        htmlResponse.append("<hr>")
      }
      val t1 = System.nanoTime()
      val secondsTaken = (t1-t0) / 1000000000.0
      System.out.println(secondsTaken)
      println("ArrayBuffer[Comment] length again: " + accumComments.length)
      htmlResponse.toString
    }

    System.out.println(commentsResponse.response.objects.size)

    commentsResponse.cursor.next match {
      case null       => commentsHtml
      case cursorNext => {
        val numDownloadedUpdated = numDownloaded + commentsResponse.response.objects.size
        val remainingNumComments = commentDownloadLimit - numDownloadedUpdated
        if (remainingNumComments > 0)
          downloadComments(username, searchTermsLemmatized, getDisqusLimitParam(remainingNumComments), commentDownloadLimit, matchAllTerms)(
            cursorNext, numDownloadedUpdated, accumComments, Some(commentBatch))
        else
          commentsHtml
      }
    }
  }

  private def usernameExists(username: String): Boolean = {
    val usernameUrl: String = String.format("https://disqus.com/by/%s/", username)
    val resp = Http(usernameUrl).asString
    resp.isCodeInRange(200, 299)
  }

  private def getQueryTokens(query: String): Set[String] = {
    val queryStringAnnotation = new Annotation(query)
    pipeline.annotate(queryStringAnnotation)
    queryStringAnnotation.get(classOf[TokensAnnotation]).toSeq.map(_.lemma()).toSet
  }

  val estimatedActualToLimitParamRatio: Float = 2.5.toFloat

  private def getDisqusLimitParam(remainingNumComments: Int): Int = {
    Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, (remainingNumComments / estimatedActualToLimitParamRatio).toInt))
  }

  get("/search") {
    def formToHtmlResponse(validatedForm: ValidationForm): (ValidationForm, String) = {
      val username = validatedForm.username
      val query = validatedForm.query.getOrElse("").toLowerCase
      val commentDownloadLimit = validatedForm.comment_download_limit
      val matchAllTerms = validatedForm.match_all_terms
      if (!usernameExists(username)) {
        (validatedForm, String.format ("<div>Did not find any Disqus user with username <b>%s</b></div>", username))
      } else {
        val cursor: String = ""
        val accumComments: ArrayBuffer[Comment] = new ArrayBuffer[Comment] (100)
        val t2 = System.nanoTime ()
        val res = downloadComments (username, getQueryTokens (query), getDisqusLimitParam (commentDownloadLimit), commentDownloadLimit, matchAllTerms) (
          cursor, 0, accumComments, None)
        val t3 = System.nanoTime ()
        val secondsTaken2 = (t3 - t2) / 1000000000.0
        System.out.println ("total time: " + secondsTaken2)
        (validatedForm, res)
      }
    }

    def formErrorToHtmlResponse(hasErrors: Seq[(String, String)]): (ValidationForm, String) = {
      val limit = Try(params("comment_download_limit").toInt) match {
        case Success(value) => value
        case _ => 100
      }
      val validationForm = ValidationForm(params("username"), Some(params("query")), limit, params("comment_download_limit").equals("on"))
      val errorMsg = hasErrors.map(_._2).fold("")(_ ++ "<br>" ++ _)
      (validationForm, errorMsg)
    }

    val form = mapping(
      "username" -> label("Username", text(required, maxlength(100))),
      "query" -> label("Query", optional(text(required, maxlength(100)))),
      "comment_download_limit" -> label("Comment Download Limit", number()),
      "match_all_terms" -> label("Require match all terms", boolean())
    )(ValidationForm.apply)
    val (formParams, htmlResponse) = validate(form)(formErrorToHtmlResponse, formToHtmlResponse)
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/results.ssp", "form" -> formParams, "htmlResponse" -> htmlResponse)
  }

  get("/") {
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/results.ssp", "form" -> ValidationForm("", Some(""), 100, false), "htmlResponse" -> "")
  }
}
