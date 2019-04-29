package com.gmachine1729.disqus_comment_search


import com.gmachine1729.disqus_comment_search.models.Visit
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import upickle.default.{macroRW, ReadWriter => RW}
import upickle.default._
import scalaj.http._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

case class GeoLocation(city: String, country: String, isp: String, region: String)
object GeoLocation {
  implicit val rw: RW[GeoLocation] = macroRW
}

object VisitLogger {
  object VisitType extends Enumeration {
    val HOME_PAGE, SEARCH, FEEDBACK = Value
  }

  private def getIpAndReferrer(request: HttpServletRequest): (String, Option[String]) = {
    val ip = {
      val ipMaybe = request.getHeader("X-Forwarded-For")
      if (ipMaybe == null)
        request.getRemoteAddr
      else
        ipMaybe
    }
    val referer = {
      val refererMaybe = request.getHeader("Referer")
      if (refererMaybe == null)
        None
      else
        Some(refererMaybe)
    }
    (ip, referer)
  }

  private def getGeoLocation(ip: String)(urlFormat: String): Try[GeoLocation] = {
    Try(read[GeoLocation](Http(String.format(urlFormat, ip)).asString.body))
  }

  def genVisitRecord(request: HttpServletRequest, visitType: Int): Future[Visit] = {
    Future {
      val (ip, referer) = getIpAndReferrer(request)
      val visit = new Visit()
      visit.visitType = visitType
      visit.ip = Some(ip)
      visit.referrer = referer
      val maybeGeoLocation = Iterator[String]("http://free.ipwhois.io/json/%s", "http://ip-api.com/json/%s").map(getGeoLocation(ip)).map {
        case success@Success(geoLocation) => {
          visit.city = Some(geoLocation.city)
          visit.province = Some(geoLocation.region)
          visit.country = Some(geoLocation.country)
          visit.isp = Some(geoLocation.isp)
          success
        }
        case failure@Failure(exception) => {
          LoggerFactory.getLogger(getClass).warn(exception.getMessage)
          failure
        }
      }.find(_.isSuccess)
      maybeGeoLocation match {
        case None => LoggerFactory.getLogger(getClass).warn(String.format("Could not get geolocation data for %", ip))
        case _    =>
      }
      visit
    }
  }

}
