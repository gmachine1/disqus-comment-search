package com.gmachine1729.disqus_comment_search

import org.scalatra.test.scalatest._

class DisqusCommentSearchServletTests extends ScalatraFunSuite {

  addServlet(classOf[DisqusCommentSearchServlet], "/*")

  test("GET / on DisqusCommentSearchServlet should return status 200") {
    get("/") {
      status should equal (200)
    }
  }

}
