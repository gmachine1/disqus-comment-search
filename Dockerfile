# Pull base image
From tomcat:8-jre8

# Maintainer
MAINTAINER "gmachine1729 <gmachine1729@foxmail.com">

# Copy to images tomcat path
COPY ./target/scala-2.12/disqus-comment-search_2.12-0.1.0-SNAPSHOT.war /usr/local/tomcat/webapps/

RUN mkdir -p /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/
COPY ./edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/

CMD ["catalina.sh", "run"]