package com.gmachine1729.disqus_comment_search.models

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, PersistenceStatus, Schema}
import java.sql.Timestamp
import java.util.Calendar

import com.gmachine1729.disqus_comment_search.ValidationForm

class Visit(val id: Long, val timestamp: Timestamp, var ip: Option[String],
            var referrer: Option[String], var country: Option[String], var province: Option[String], var city: Option[String], var isp: Option[String],
            var visitType: Int, var latency: Option[Double], var username: Option[String], var query: Option[String], var matchAll: Option[Boolean],
            var commentDownloadLimit: Option[Int], var formValidated: Boolean, var foundUser: Boolean, var numResults: Option[Int],
            var name: Option[String], var email: Option[String], var message: Option[String]) extends ScalatraRecord {
  def this() = this(0L, new java.sql.Timestamp(Calendar.getInstance().getTime().getTime()), None: Option[String], None: Option[String],
    None: Option[String], None: Option[String], None: Option[String], None: Option[String], -1, None: Option[Double],
    None: Option[String], None: Option[String], None: Option[Boolean], None: Option[Int], false, false,
    None: Option[Int], None: Option[String], None: Option[String], None: Option[String])

  def setFormParameters(form: ValidationForm): Unit = {
    formValidated = true
    username = Some(form.username)
    query = form.query match {
      case None => Some("")
      case _    => form.query
    }
    matchAll = Some(form.match_all_terms)
    commentDownloadLimit = Some(form.comment_download_limit)
  }
}

object Visit {

  def create(visit: Visit): Boolean = {
    inTransaction {
      val result = Tables.visits.insert(visit)
      if (result.isPersisted) {
        true
      } else {
        false
      }
    }
  }

}

object Tables extends Schema {
  val visits = table[Visit]

  on(visits)(v => declare(
    v.id is (autoIncremented),
    v.message is (dbType("text"))
  ))
}


trait ScalatraRecord extends KeyedEntity[Long] with PersistenceStatus