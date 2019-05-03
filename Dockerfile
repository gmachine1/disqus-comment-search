FROM jetty:9.3-jre8

ADD http://peaceful-lake-98175.herokuapp.com/war_file/disqus-comment-search_2.12-0.1.1.jar /usr/local/bin/
ADD http://peaceful-lake-98175.herokuapp.com/war_file/init.sh /usr/local/bin/init.sh
RUN chmod a+x /usr/local/bin/init.sh

RUN mkdir -p /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/
ADD http://peaceful-lake-98175.herokuapp.com/war_file/english-left3words-distsim.tagger /usr/local/share/disqus-comment-search/edu/stanford/nlp/models/pos-tagger/english-left3words/

RUN mkdir -p /usr/local/share/disqus-comment-search/python
ADD http://peaceful-lake-98175.herokuapp.com/war_file/filter_json_response.py /usr/local/share/disqus-comment-search/python

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/init.sh"]