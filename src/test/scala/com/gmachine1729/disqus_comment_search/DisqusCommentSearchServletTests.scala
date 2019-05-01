package com.gmachine1729.disqus_comment_search

import com.gmachine1729.disqus_comment_search.db.DatabaseInit
import org.jsoup.Jsoup
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.junit.JUnitRunner
import org.scalatra.test.scalatest._

trait SearchResultInfoExtractor {
  def getNumResults(htmlResponse: String): Int = {
    val document = Jsoup.parse(htmlResponse)
    document.getElementsByClass("result_comment").size()
  }
}

@RunWith(classOf[JUnitRunner])
class DisqusCommentSearchServletTests extends ScalatraFunSuite with DatabaseInit with BeforeAndAfter with SearchResultInfoExtractor {

  addServlet(classOf[DisqusCommentSearchServlet], "/*")

  before {
    configureDb()
  }

  after {
    //closeDbConnection()
  }

  private val defaultParams = Map("username" -> "gmachine1729", "query" -> "", "comment_download_limit" -> "1000", "match_all_terms" -> "false")

  test("GET / on home page should return status 200") {
    get("") {
      status should equal (200)
    }
  }

  test("sufficient number of results returned") {
    get("search", defaultParams) {
      status should equal (200)
      getNumResults(body) shouldBe >(70)
    }
  }

  test("limit parameter limits comments") {
    get("search", defaultParams ++ Map("username" -> "infoproc", "comment_download_limit" -> "100")) {
      status should equal (200)
      getNumResults(body) shouldBe <(120)
    }
  }

  test("match all terms option on should return fewer results") {
    get("search", defaultParams ++ Map("query" -> "china west", "match_all_terms" -> "true")) {
      status should equal (200)
      val numResultsWithMatchAll = getNumResults(body)
      get("/search", defaultParams ++ Map("query" -> "china west", "match_all_terms" -> "false")) {
        status should equal (200)
        val numResultsWithoutMatchAll = getNumResults(body)
        numResultsWithMatchAll shouldBe < (numResultsWithoutMatchAll)
      }
    }
  }

  test("capitalization shouldn't matter") {
    get("search", defaultParams ++ Map("username" -> "18Polo")) {
      status should equal (200)
      val numResultsWithIncorrectCaps = getNumResults(body)
      get("search", defaultParams ++ Map("username" -> "18polo")) {
        status should equal (200)
        val numResultsWithCorrectCaps = getNumResults(body)
        numResultsWithIncorrectCaps should equal (numResultsWithCorrectCaps)
      }
    }

    get("search", defaultParams ++ Map("username" -> "18polo", "query" -> "China")) {
      status should equal (200)
      val numResultsWithCapsInQuery = getNumResults(body)
      get("search", defaultParams ++ Map("username" -> "18polo", "query" -> "china")) {
        status should equal (200)
        val numResultsWithNoCapsInQuery = getNumResults(body)
        numResultsWithCapsInQuery should equal (numResultsWithNoCapsInQuery )
      }
    }
  }

}
