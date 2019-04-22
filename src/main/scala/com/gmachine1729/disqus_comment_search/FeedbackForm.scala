package com.gmachine1729.disqus_comment_search

case class FeedbackForm(
                           name: String,
                           email: Option[String],
                           message: String,
                       )
