package com.gmachine1729.disqus_comment_search

import java.net.URL

import org.scalatra._
import scalatags.Text
import upickle.default._

import scala.sys.process._
import ProcessBuilder._
import scalatags.Text.all._

class DisqusCommentSearchServlet extends ScalatraServlet {
  val API_KEY = "E8Uh5l5fHZ6gD8U3KycjAIAk46f68Zw7C6eW8WSjZvCLXebZ7p0r1yrYDrLilk2F"
  val BASE_URL = "https://disqus.com/api/3.0/timelines/activities"
  val LIMIT: Integer = 100

  def getUrl(username: String, cursor: String): String = {
    String.format("%s?type=profile&index=comments&target=user:username:%s&cursor=%s&limit=%d&api_key=%s",
      BASE_URL, username, cursor, LIMIT, API_KEY)
  }

  def downloadComments(username: String, cursor: String, stringBuilder: StringBuilder): String = {
    val filteredJson = (new URL(getUrl(username, cursor)) #> String.format("python src/main/python/filter_json_response.py %s", username)).!!
    val commentsResponse = read[CommentsResponse](filteredJson)
    val commentsReverseChrono = commentsResponse.response.objects.map(_._2).toSeq.sorted
    stringBuilder.append(commentsReverseChrono.map(_.toHtml).foldLeft("")(_ ++ "\n" ++ _))
    commentsResponse.cursor.next match {
      case null       => stringBuilder.result()
      case cursorNext => downloadComments(username, cursorNext, stringBuilder)
    }
  }

  get("/search") {
    val username: String = params("username")
    val cursor: String = ""
    val stringBuilder: StringBuilder = new StringBuilder()
    downloadComments(username, cursor, stringBuilder)
  }

  get("/") {
    contentType = "text/html"
    <html>
      <head><title>Disqus comment search</title></head>
      <body>
        <form action="/search">
          Disqus username: <input type="text" name="username"/>
          <input type="submit" value="Submit"/>
        </form>
        <div></div>
      </body>
    </html>
  }
}
