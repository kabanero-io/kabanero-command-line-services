# Build a Release of the Kabanero CLI Microservice
### Cut the release
in: https://github.com/kabanero-io/kabanero-command-line-services
* select the release tab
* push the `Draft a new release` button
* specify the tag version number (semver) in the `Tag version` entry box
* Click `Publish release` button at the bottom of the page

### Verify Travis build
* check travis build for success: https://travis-ci.org/kabanero-io/kabanero-command-line-services/builds
* verify that it passed and that the docker build and docker push completed successfully (click on grey ellipses at bottom to expand)
	
### Verify Docker image in docker hub
* Verify that the release (semver specified above) tagged image is listed at: https://hub.docker.com/r/kabanero/kabanero-command-line-services/tags

