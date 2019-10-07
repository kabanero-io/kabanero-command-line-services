## Microservice
Kabanero CLI Microservice 


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
The ports are set in the pom.xml file


### Run

To build and run the application:
1. to build please see: `https://github.com/kabanero-io/kabanero-command-line-services/build-help.md`
2. to run see README at: `https://github.com/kabanero-io/kabanero-command-line`


To run the application in Docker use the Docker file called `Dockerfile`. If you do not want to install Maven locally you can use `Dockerfile-tools` to build a container with Maven installed.

### Endpoints

The application exposes the following endpoints:
* CLI endpoint: `<cli-route>`
* Health endpoint: `<cli-route>/health`

( `<cli-route>` can be obtained by running the `oc get routes` command )

