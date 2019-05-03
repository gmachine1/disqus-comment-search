FROM jetty:9.3-jre8

USER root

RUN mkdir -p /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/
ADD http://peaceful-lake-98175.herokuapp.com/war_file/english-left3words-distsim.tagger /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/

RUN mkdir -p /usr/local/share/disqus-comment-search/python
ADD http://peaceful-lake-98175.herokuapp.com/war_file/filter_json_response.py /usr/local/share/disqus-comment-search/python

ADD http://peaceful-lake-98175.herokuapp.com/war_file/disqus-comment-search_2.12-0.1.1.war /var/lib/jetty/webapps/

RUN apt-get -y update
RUN apt-get -y install python

EXPOSE 8080