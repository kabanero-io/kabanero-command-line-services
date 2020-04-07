# Docker build for Kabanero CLI Microservice
#FROM openliberty/open-liberty:webProfile7-ubi-min
FROM openliberty/open-liberty:kernel-java8-openj9-ubi
#FROM open-liberty

USER root
# The following labels are required for Redhat container certification
LABEL vendor="Kabanero" \
      name="Kabanero CLI Service" \
      summary="Image for Kabanero CLI Service" \
      description="This image contains the service for the Kabanero CLI.  See https://github.com/kabanero-io/kabanero-command-line-services/"

# The licence must be here for Redhat container certification
COPY LICENSE /licenses/ 

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


#FROM quay.io/buildah/stable:v1.9.0
#RUN yum module install container-tools
#RUN yum -y install buildah

# get buildah please
#. /etc/os-release
#sudo sh -c "echo 'deb http://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/x${ID^}_${VERSION_ID}/ /' > /etc/apt/sources.list.d/devel:kubic:libcontainers:stable.list"
#wget -nv https://download.opensuse.org/repositories/devel:kubic:libcontainers:stable/x${ID^}_${VERSION_ID}/Release.key -O Release.key
#sudo apt-key add - < Release.key
#sudo apt-get update -qq
#sudo apt-get -qq -y install buildah

### Copy repository configuration for temporary tools needed during the build that will be removed after skopeo is built.
COPY ./yum.repos.d /etc/yum.repos.d

### Add necessary Red Hat repos here
## Note: The UBI has different repos than the RHEL repos.
RUN REPOLIST=ubi-8-baseos,ubi-8-codeready-builder,ubi-8-appstream \

### Add your package needs here
    SKOPEO_VERSION_NAME=0.1.40 \
    SKOPEO_SRC_PKG_NAME=v${SKOPEO_VERSION_NAME}.tar.gz \
    SKOPEO_SRC_ROOT_NAME=skopeo-${SKOPEO_VERSION_NAME} \
    INSTALL_PKGS="ostree-libs" \
    TEMP_BUILD_UBI_PKGS="wget make golang gpgme-devel libassuan-devel device-mapper-devel" && \
    yum -y update-minimal --disablerepo "*" --enablerepo ubi-8* --setopt=tsflags=nodocs \
      --security --sec-severity=Important --sec-severity=Critical && \
    yum repolist && \
    yum -y install --disablerepo "*" --enablerepo ${REPOLIST} --setopt=tsflags=nodocs ${INSTALL_PKGS} ${TEMP_BUILD_UBI_PKGS} && \

### Install your application here -- add all other necessary items to build your image
    GOPATH=$(pwd) && \
    mkdir -p /src/github.com/containers && \
    cd /src/github.com/containers && \
    wget https://github.com/containers/skopeo/archive/${SKOPEO_SRC_PKG_NAME} && \
    tar -xzpf ${SKOPEO_SRC_PKG_NAME} && \
    rm -f ${SKOPEO_SRC_PKG_NAME} && \
    mv ${SKOPEO_SRC_ROOT_NAME} skopeo && \
    cd skopeo && \
    make binary-local && \
    mv skopeo /usr/local/bin && \
    # Remove source tree
    rm -rf ${GOPATH}/src && \
    # Create required config file
    mkdir -p /etc/containers && \
    echo $'{\n    \"default\": [\n        {\n            \"type\": \"insecureAcceptAnything\"\n        }\n    ]\n}' \
    > /etc/containers/policy.json && \
    cat /etc/containers/policy.json && \
    yum -y remove --setopt=tsflags=nodocs ${TEMP_BUILD_UBI_PKGS} ${TEMP_BUILD_OTHER_PKGS} && \
    yum clean all -y && \
    # Remove repos' configs
    rm -rf /etc/rhsm /etc/yum.repos.d /etc/pki/entitlement /etc/pki/rpm-gpg
