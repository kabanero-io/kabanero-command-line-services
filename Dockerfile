# Docker build for Kabanero CLI Microservice
FROM openliberty/open-liberty:webProfile7-ubi-min
# The following labels are required for Redhat container certification
LABEL vendor="Kabanero" \
      name="Kabanero CLI Service" \
      summary="Image for Kabanero CLI Service" \
      description="This image contains the service for the Kabanero CLI.  See https://github.com/kabanero-io/kabanero-command-line-services/"

# The licence must be here for Redhat container certification
COPY LICENSE /licenses/ 

#FROM open-liberty:webProfile7-java8-openj9
COPY --chown=1001:0 /target/kabanero-cli-service-1.0-SNAPSHOT.war /config/apps
COPY --chown=1001:0 /src/main/liberty/config/cacerts /config/resources/security/cacerts
COPY --chown=1001:0 /target/liberty/wlp/usr/servers/defaultServer/server.xml /config
COPY --chown=1001:0 /src/main/liberty/config/jvm.options /config

RUN mkdir -p /opt/ol/wlp/output/defaultServer/resources/security
RUN chown -R 1001:0 /opt/ol/wlp/output/defaultServer/resources/security
RUN chmod -R g+rw /opt/ol/wlp/output/defaultServer/resources/security
RUN chmod 444 /config/server.xml
RUN chmod 444 /config/server.env
RUN chmod 444 /config/jvm.options
RUN chmod 444 /config/resources/security/cacerts
RUN rm /config/configDropins/defaults/open-default-port.xml

# get buildah
#FROM quay.io/buildah/stable:v1.9.0
#RUN yum -y install buildah
sudo subscription-manager repos --enable=rhel-7-server-extras-rpms
sudo yum -y install buildah


