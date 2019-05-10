FROM couchbase:community

RUN wget https://packages.couchbase.com/releases/couchbase-sync-gateway/2.5.0/couchbase-sync-gateway-community_2.5.0_x86_64.deb && \
      dpkg-deb -R couchbase-sync-gateway-community_2.5.0_x86_64.deb /

ADD ./script/couchbase-sync-gateway /etc/service/

RUN chmod +x /etc/service/couchbase-sync-gateway/run

ADD ./sg-config.json /
