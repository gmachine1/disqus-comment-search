package com.gmachine1729.disqus_comment_search

case class ValidationForm(
                           username: String,
                           query: Option[String],
                           comment_download_limit: Int,
                           match_all_terms: Boolean
                         )