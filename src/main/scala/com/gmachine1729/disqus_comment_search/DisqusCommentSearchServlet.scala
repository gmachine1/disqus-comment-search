package com.gmachine1729.disqus_comment_search

import java.net.URL
import java.util.Properties

import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import upickle.default._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.ling.CoreAnnotations._

import scala.collection.JavaConversions._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._

class DisqusCommentSearchServlet extends ScalatraServlet with ScalateSupport {
  val API_KEY = "E8Uh5l5fHZ6gD8U3KycjAIAk46f68Zw7C6eW8WSjZvCLXebZ7p0r1yrYDrLilk2F"
  val BASE_URL = "https://disqus.com/api/3.0/timelines/activities"
  val LIMIT: Integer = 100
  val props = new Properties()
  props.put("annotators", "tokenize, ssplit, pos, lemma")
  val pipeline = new StanfordCoreNLP(props)

  def getUrl(username: String, cursor: String): String = {
    String.format("%s?type=profile&index=comments&target=user:username:%s&cursor=%s&limit=%d&api_key=%s",
      BASE_URL, username, cursor, LIMIT, API_KEY)
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

  def downloadComments(username: String, searchTermsLemmatized: Set[String])(cursor: String, accumComments: ArrayBuffer[Comment], processedComments: Option[Future[Iterable[Comment]]]): String = {
    val filteredJson = (new URL(getUrl(username, cursor)) #> String.format("python src/main/python/filter_json_response.py %s", username)).!!
    processedComments match {
      case Some(future) => {
        Await.ready[Iterable[Comment]](future, Duration.Inf)
        future.map(accumComments.appendAll)
      }
      case None =>
    }
    val commentsResponse = read[CommentsResponse](filteredJson)
    val commentBatch = Future {
        commentsResponse.response.objects.map(_._2).filter(comment => containsSearchTerms(searchTermsLemmatized)(comment) > 0)
    }
    commentsResponse.cursor.next match {
      case null       => {
        Await.ready[Iterable[Comment]](commentBatch, Duration.Inf)
        commentBatch.map(accumComments.appendAll)
        /*accumComments.sortWith(
          (c1, c2) => Comment.dateTimeParser.parse(c1.createdAt).getTime - Comment.dateTimeParser.parse(c2.createdAt).getTime > 0)*/
        accumComments.sorted.map(_.toHtml).foldLeft("")(_ ++ "\n" ++ _)
      }
      case cursorNext => downloadComments(username, searchTermsLemmatized)(cursorNext, accumComments, Some(commentBatch))
    }
  }

  get("/search") {
    val username: String = params("username")
    val query: String = params("query").toLowerCase
    val queryStringAnnotation = new Annotation(query)
    pipeline.annotate(queryStringAnnotation)

    val queryTokens: Set[String] = queryStringAnnotation.get(classOf[TokensAnnotation]).toSeq.map(_.lemma()).toSet
    val cursor: String = ""
    val accumComments: ArrayBuffer[Comment] = new ArrayBuffer[Comment]()
    val results = downloadComments(username, queryTokens)(cursor, accumComments, None)
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/index.ssp", "results" -> results)
  }

  get("/") {
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/index.ssp", "results" -> "")
  }
}
