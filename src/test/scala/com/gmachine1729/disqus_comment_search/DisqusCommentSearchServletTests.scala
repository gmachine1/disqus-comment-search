package com.gmachine1729.disqus_comment_search

import com.gmachine1729.disqus_comment_search.db.DatabaseInit
import com.gmachine1729.disqus_comment_search.models.Visit
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.junit.JUnitRunner
import org.scalatra.test.scalatest._

@RunWith(classOf[JUnitRunner])
class DisqusCommentSearchServletTests extends ScalatraFunSuite with DatabaseInit with BeforeAndAfter {

  addServlet(classOf[DisqusCommentSearchServlet], "/*")

  before {
    configureDb()
  }

  after {
    closeDbConnection()
  }

  test("GET / on DisqusCommentSearchServlet should return status 200") {
    get("/") {
      /*val visit = new Visit()
      val res = Visit.create(visit)
      println(res)*/
      status should equal (200)
    }
  }

}
