.PHONY: help test test-cloud coverage build build-cloud install

MVN ?= mvn
GEOSERVER_SRC ?=

help:
	@printf '%s\n' 'Targets: test test-cloud coverage build build-cloud install'
	@printf '%s\n' 'Use GEOSERVER_SRC=/path/to/geoserver to build with the GeoServer community parent.'

test:
	@if [ -n "$(GEOSERVER_SRC)" ]; then \
		tmp="$(GEOSERVER_SRC)/src/community/portolan-build-check"; \
		if [ -e "$$tmp" ]; then echo "temporary path exists: $$tmp"; exit 2; fi; \
		mkdir "$$tmp"; \
		cp -R pom.xml Makefile README.md docs src "$$tmp"/; \
		$(MVN) -f "$$tmp/pom.xml" test; \
		status=$$?; \
		mv "$$tmp" "/tmp/portolan-build-check-$$$$"; \
		exit $$status; \
	else \
		$(MVN) test; \
	fi

test-cloud:
	$(MVN) -f cloud/pom.xml test

coverage:
	@if [ -n "$(GEOSERVER_SRC)" ]; then \
		tmp="$(GEOSERVER_SRC)/src/community/portolan-build-check"; \
		if [ -e "$$tmp" ]; then echo "temporary path exists: $$tmp"; exit 2; fi; \
		mkdir "$$tmp"; \
		cp -R pom.xml Makefile README.md docs src "$$tmp"/; \
		$(MVN) -f "$$tmp/pom.xml" test; \
		status=$$?; \
		report="/tmp/portolan-coverage-$$$$"; \
		mv "$$tmp" "$$report"; \
		echo "Coverage report: $$report/target/site/jacoco/index.html"; \
		exit $$status; \
	else \
		$(MVN) test; \
		echo 'Coverage report: target/site/jacoco/index.html'; \
	fi

build:
	@if [ -n "$(GEOSERVER_SRC)" ]; then \
		tmp="$(GEOSERVER_SRC)/src/community/portolan-build-check"; \
		if [ -e "$$tmp" ]; then echo "temporary path exists: $$tmp"; exit 2; fi; \
		mkdir "$$tmp"; \
		cp -R pom.xml Makefile README.md docs src "$$tmp"/; \
		$(MVN) -f "$$tmp/pom.xml" package; \
		status=$$?; \
		mv "$$tmp" "/tmp/portolan-build-check-$$$$"; \
		exit $$status; \
	else \
		$(MVN) package; \
	fi

build-cloud:
	$(MVN) -f cloud/pom.xml package

install:
	@if [ -n "$(GEOSERVER_SRC)" ]; then \
		tmp="$(GEOSERVER_SRC)/src/community/portolan-build-check"; \
		if [ -e "$$tmp" ]; then echo "temporary path exists: $$tmp"; exit 2; fi; \
		mkdir "$$tmp"; \
		cp -R pom.xml Makefile README.md docs src "$$tmp"/; \
		$(MVN) -f "$$tmp/pom.xml" install; \
		status=$$?; \
		mv "$$tmp" "/tmp/portolan-build-check-$$$$"; \
		exit $$status; \
	else \
		$(MVN) install; \
	fi
