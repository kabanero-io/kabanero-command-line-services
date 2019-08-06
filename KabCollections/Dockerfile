FROM websphere-liberty:19.0.0.3-webProfile7
# FROM websphere-liberty@sha256:e99527c9275af659208e8ee28c9935f2f6c88cf24cf1819558578d8ddbcad112
LABEL maintainer="IBM Java Engineering at IBM Cloud"
COPY /target/liberty/wlp/usr/servers/defaultServer /config/
COPY /target/liberty/wlp/usr/shared/resources /config/resources/
COPY /src/main/liberty/config/jvmbx.options /config/jvm.options
# Grant write access to apps folder, this is to support old and new docker versions.
# Liberty document reference : https://hub.docker.com/_/websphere-liberty/
USER root
RUN chmod g+w /config/apps
RUN configure.sh
USER 1001
