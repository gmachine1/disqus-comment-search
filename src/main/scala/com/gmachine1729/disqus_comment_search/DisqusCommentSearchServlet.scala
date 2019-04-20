package com.gmachine1729.disqus_comment_search

import java.net.URL

import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import upickle.default._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._

class DisqusCommentSearchServlet extends ScalatraServlet with ScalateSupport {
  val API_KEY = "E8Uh5l5fHZ6gD8U3KycjAIAk46f68Zw7C6eW8WSjZvCLXebZ7p0r1yrYDrLilk2F"
  val BASE_URL = "https://disqus.com/api/3.0/timelines/activities"
  val LIMIT: Integer = 100

  def getUrl(username: String, cursor: String): String = {
    String.format("%s?type=profile&index=comments&target=user:username:%s&cursor=%s&limit=%d&api_key=%s",
      BASE_URL, username, cursor, LIMIT, API_KEY)
  }

  def downloadComments(username: String, cursor: String, accumComments: ArrayBuffer[Comment], processedComments: Option[Future[Iterable[Comment]]]): String = {
    val filteredJson = (new URL(getUrl(username, cursor)) #> String.format("python src/main/python/filter_json_response.py %s", username)).!!
    processedComments match {
      case Some(future) => future.map {
        comments => accumComments.appendAll(comments)
      }
      case None =>
    }
    val commentsResponse = read[CommentsResponse](filteredJson)
    val commentBatch = Future {
      // TODO: filter by search terms
      commentsResponse.response.objects.map(_._2)
    }
    commentsResponse.cursor.next match {
      case null       => {
        commentBatch.map {
          comments => accumComments.appendAll(comments)
        }
        accumComments.sorted.map(_.toHtml).foldLeft("")(_ ++ "\n" ++ _)
      }
      case cursorNext => downloadComments(username, cursorNext, accumComments, Some(commentBatch))
    }
  }

  get("/search") {
    val username: String = params("username")
    val cursor: String = ""
    val accumComments: ArrayBuffer[Comment] = new ArrayBuffer[Comment]()
    val results = downloadComments(username, cursor, accumComments, None)
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/index.ssp", "results" -> results)
  }

  get("/") {
    contentType = "text/html"
    ssp("/WEB-INF/templates/views/index.ssp", "results" -> "")
  }
}
