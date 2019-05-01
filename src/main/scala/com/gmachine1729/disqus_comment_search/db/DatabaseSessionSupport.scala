package com.gmachine1729.disqus_comment_search.db

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.scalatra._

object DatabaseSessionSupport {
  val key = {
    val n = getClass.getName
    if (n.endsWith("$")) n.dropRight(1) else n
  }
}

trait DatabaseSessionSupport { this: ScalatraBase =>
  import DatabaseSessionSupport._

  def dbSession = request.get(key).orNull.asInstanceOf[Session]

  before() {
    request(key) = SessionFactory.newSession
    dbSession.bindToCurrentThread
  }

  after() {
    if (dbSession != null) {
      dbSession.close
      dbSession.unbindFromCurrentThread
    }
  }

}