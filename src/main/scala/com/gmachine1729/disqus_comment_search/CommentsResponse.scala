package com.gmachine1729.disqus_comment_search

import upickle.default.{ReadWriter => RW, macroRW}
import scalatags.Text.all._
import java.text.SimpleDateFormat

case class Cursor(next: String)
object Cursor {
  implicit val rw: RW[Cursor] = macroRW
}

case class Response(objects: Map[String, Comment])
object Response {
  implicit val rw: RW[Response] = macroRW
}

case class Author(username: String, about: String, name: String)
object Author {
  implicit val rw: RW[Author] = macroRW
}

case class Comment(message: String, author: Author, createdAt: String, url: String, forum: String, likes: Int, dislikes: Int) extends Ordered[Comment] {
  def toHtml: String = {
    div(a(href:=url)(createdAt.substring(0, 10) ++ " on " ++ forum), message).toString()
  }

  def compare(other: Comment): Int = {
    val parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    if (parser.parse(other.createdAt).getTime - parser.parse(other.createdAt).getTime > 0) 1
    -1
  }
}
object Comment {
  implicit val rw: RW[Comment] = macroRW
}

case class CommentsResponse(cursor: Cursor, response: Response)
object CommentsResponse {
  implicit val rw: RW[CommentsResponse] = macroRW
}
