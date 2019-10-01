## Microservice
Kabanero CLI Microservice 

[![](https://img.shields.io/badge/IBM%20Cloud-powered-blue.svg)](https://bluemix.net)
[![Platform](https://img.shields.io/badge/platform-java-lightgrey.svg?style=flat)](https://www.ibm.com/developerworks/learn/java/)

### Table of Contents
* [Summary](#summary)
* [Requirements](#requirements)
* [Configuration](#configuration)
* [Project contents](#project-contents)
* [Run](#run)

### Summary

The Kabanero CLI Microservice provides the service endpoints in support of all of the functions of the Kabanero command line functional keywords (login, logout, list, refresh, onboard and version)


### Requirements
* [Maven](https://maven.apache.org/install.html)
* Java 8: Any compliant JVM should work.
  * [Java 8 JDK from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
  * [Java 8 JDK from IBM (AIX, Linux, z/OS, IBM i)](http://www.ibm.com/developerworks/java/jdk/),
    or [Download a Liberty server package](https://developer.ibm.com/assets/wasdev/#filter/assetTypeFilters=PRODUCT)
    that contains the IBM JDK (Windows, Linux)

### Configuration
The application is configured to provide JAX-RS REST capabilities, JNDI, JSON parsing and Contexts and Dependency Injection (CDI).

These capabilities are provided through dependencies in the pom.xml file and Liberty features enabled in the server config file found in `src/main/liberty/config/server.xml`.

### Project contents
The microservice application has a health endpoint which is accessible at `<host>:<port>/kabanero-cli-service/health`. The context root is set in the `src/main/webapp/WEB-INF/ibm-web-ext.xml` file. The ports are set in the pom.xml file and exposed to the CLI in the cli-config.yml file.

Credentials are either taken from the VCAP_SERVICES environment variable that IBM Cloud provides or from environment variables passed in by JNDI (see the server config file `src/main/liberty/config/server.xml`).

### Run

To build and run the application:
1. to build please see: `build-help.md`
2. to run see README at: `https://github.com/kabanero-io/kabanero-command-line`


To run the application in Docker use the Docker file called `Dockerfile`. If you do not want to install Maven locally you can use `Dockerfile-tools` to build a container with Maven installed.

### Endpoints

The application exposes the following endpoints:
* Health endpoint: `<host>:<port>/<contextRoot>/health`


### Notices

This project was generated using:
* generator-ibm-java v5.14.1
* generator-ibm-service-enablement v3.2.1
* generator-ibm-cloud-enablement v1.7.7
* generator-ibm-java-liberty v
