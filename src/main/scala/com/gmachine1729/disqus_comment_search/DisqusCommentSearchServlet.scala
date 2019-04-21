package com.gmachine1729.disqus_comment_search

import java.net.URL
import java.util.Properties

import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import upickle.default._
import org.jsoup.Jsoup
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.ling.CoreAnnotations._
import scalaj.http.Http

import scala.collection.JavaConversions._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._

class DisqusCommentSearchServlet extends ScalatraServlet with ScalateSupport {
  val API_KEY = "E8Uh5l5fHZ6gD8U3KycjAIAk46f68Zw7C6eW8WSjZvCLXebZ7p0r1yrYDrLilk2F"
  val BASE_URL = "https://disqus.com/api/3.0/timelines/activities"
  val DEFAULT_LIMIT: Integer = 20
  val props = new Properties()
  props.put("annotators", "tokenize, ssplit, pos, lemma")
  val pipeline = new StanfordCoreNLP(props)

  def getUrl(username: String, cursor: String, limit: Integer): String = {
    String.format("%s?type=profile&index=comments&target=user:username:%s&cursor=%s&limit=%d&api_key=%s",
      BASE_URL, username, cursor, limit, API_KEY)
  }

  def containsSearchTerms(searchTermsLemmatized: Set[String])(comment: Comment): Int = {
    if (searchTermsLemmatized.isEmpty) 1
    else {
      val doc = new Annotation(Jsoup.parse(comment.message).text().toLowerCase)
      pipeline.annotate(doc)
      val sentences = doc.get(classOf[SentencesAnnotation])
      var numMatches = 0
      for (sentence <- sentences; token <- sentence.get(classOf[TokensAnnotation])) {
        val lemma = token.get(classOf[LemmaAnnotation])
        if (searchTermsLemmatized.contains(lemma)) numMatches += 1
      }
      numMatches
    }
  }

  def downloadComments(username: String, searchTermsLemmatized: Set[String], commentDownloadLimit: Int)(
      cursor: String, numDownloaded: Int, accumComments: ArrayBuffer[Comment], prevCommentBatch: Option[Future[Iterable[Comment]]]): String = {
    val t0 = System.nanoTime()
    val filteredJson = (new URL(getUrl(username, cursor, DEFAULT_LIMIT)) #> String.format("python src/main/python/filter_json_response.py %s", username)).!!
    val t1 = System.nanoTime()
    val secondsTaken = (t1-t0) / 1000000000.0
    println("Url: " + getUrl(username, cursor, DEFAULT_LIMIT))
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
        commentsResponse.response.objects.map(_._2).filter(comment => containsSearchTerms(searchTermsLemmatized)(comment) > 0)
    }

    def commentsHtml: String = {
      val filteredCommentBatch = Await.result[Iterable[Comment]](commentBatch, Duration.Inf)
      accumComments.appendAll(filteredCommentBatch)
      val t0 = System.nanoTime()
      //val htmlResponse = accumComments.sorted.map(_.toHtml).foldLeft("")(_ ++ "\n" ++ _)
      val htmlResponse = new StringBuffer()
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
        if (numDownloadedUpdated < commentDownloadLimit)
          downloadComments(username, searchTermsLemmatized, commentDownloadLimit)(
            cursorNext, numDownloadedUpdated, accumComments, Some(commentBatch))
        else
          commentsHtml
      }
    }
  }

  def usernameExists(username: String): Boolean = {
    val usernameUrl: String = String.format("https://disqus.com/by/%s/", username)
    val resp = Http(usernameUrl).asString
    resp.isCodeInRange(200, 299)
  }

  get("/search") {
    val username: String = params("username")
    val htmlResponse = {
      if (!usernameExists(username)) {
        String.format("<div>Did not find any Disqus user with username <b>%s</b></div>", username)
      } else {
        val query: String = params("query").toLowerCase
        val commentDownloadLimit: Int = params("comment_download_limit").toInt
        val queryStringAnnotation = new Annotation(query)
        pipeline.annotate(queryStringAnnotation)

        val queryTokens: Set[String] = queryStringAnnotation.get(classOf[TokensAnnotation]).toSeq.map(_.lemma()).toSet
        val cursor: String = ""
        val accumComments: ArrayBuffer[Comment] = new ArrayBuffer[Comment]()
        downloadComments(username, queryTokens, commentDownloadLimit)(cursor, 0, accumComments, None)
      }
    }
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/index.ssp", "htmlResponse" -> htmlResponse)
  }

  get("/") {
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/index.ssp", "htmlResponse" -> "")
  }
}
