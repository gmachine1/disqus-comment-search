# Pull base image
From tomcat:8-jre8

# Maintainer
MAINTAINER "gmachine1729 <gmachine1729@foxmail.com">

# Copy to images tomcat path
ADD http://peaceful-lake-98175.herokuapp.com/war_file/disqus-comment-search_2.12-0.1.0-SNAPSHOT.war /usr/local/tomcat/webapps/

RUN mkdir -p /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/
ADD http://peaceful-lake-98175.herokuapp.com/war_file/english-left3words-distsim.tagger /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/

EXPOSE 8080

CMD ["catalina.sh", "run"]