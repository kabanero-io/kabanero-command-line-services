FROM openliberty/open-liberty:webProfile7-ubi-min
# The following labels are required for Redhat container certification
LABEL vendor="Kabanero" \
      name="Kabanero CLI Service" \
      summary="Image for Kabanero CLI Service" \
      description="This image contains the service for the Kabanero CLI.  See https://github.com/kabanero-io/kabanero-command-line-services/"

# The licence must be here for Redhat container certification
COPY LICENSE /licenses/ 

#FROM open-liberty:webProfile7-java8-openj9
COPY --chown=1001:0 /target/kabanero-cli-service-1.0-SNAPSHOT.war /config/dropins
COPY --chown=1001:0 /src/main/liberty/config/cacerts /config/resources/security/cacerts
COPY --chown=1001:0 /src/main/liberty/config/keystore.xml /config/configDropins/defaults
COPY --chown=1001:0 /target/liberty/wlp/usr/servers/defaultServer/server.xml /config
RUN mkdir -p /opt/ol/wlp/output/defaultServer/resources/security
RUN chown -R 1001:0 /opt/ol/wlp/output/defaultServer/resources/security
RUN chmod -R g+rw /opt/ol/wlp/output/defaultServer/resources/security
COPY /src/main/liberty/config/cacerts /opt/ol/wlp/output/defaultServer/resources/security/cacerts
COPY /target/liberty/wlp/usr/servers/defaultServer/server.xml /opt/ol/wlp/output/defaultServer/server.xml

