# The Docker image in format repository:tag. Repository may contain a remote reference.
# Override in order to customize.  Override to build (my fork and branch) and push to my private docker hub account
IMAGE ?= kabanero/kabanero-command-line-services:latest

# Computed repository name (no tag) including repository host/path reference
REPOSITORY=$(firstword $(subst :, ,${IMAGE}))

.PHONY: build deploy build-image push-image


build-image: 
	docker build -t ${IMAGE} --build-arg IMAGE=${IMAGE} .

push-image:
ifneq "$(IMAGE)" "kabanero/kabanero-command-line-services:latest"
	# Default push
	docker push $(IMAGE)
endif

# tag and push if tagged for release in git
ifdef TRAVIS_TAG
	# This is a Travis tag build. Pushing using Docker tag TRAVIS_TAG
	docker tag $(IMAGE) $(REPOSITORY):$(TRAVIS_TAG)
	docker push $(REPOSITORY):$(TRAVIS_TAG)
endif

ifdef TRAVIS_BRANCH
	# This is a Travis branch build. Pushing using Docker tag TRAVIS_BRANCH
	docker tag $(IMAGE) $(REPOSITORY):$(TRAVIS_BRANCH)
	docker push $(REPOSITORY):$(TRAVIS_BRANCH)
endif

push-manifest:
	echo "IMAGE="$(IMAGE)
	docker manifest create $(IMAGE) $(IMAGE)-amd64 $(IMAGE)-ppc64le $(IMAGE)-s390x
	docker manifest annotate $(IMAGE) $(IMAGE)-amd64   --os linux --arch amd64
	docker manifest annotate $(IMAGE) $(IMAGE)-ppc64le --os linux --arch ppc64le
	docker manifest annotate $(IMAGE) $(IMAGE)-s390x   --os linux --arch s390x
	docker manifest inspect $(IMAGE)
	docker manifest push $(IMAGE) -p
