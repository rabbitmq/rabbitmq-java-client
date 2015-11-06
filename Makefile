VERSION=0.0.0
PACKAGE_NAME=rabbitmq-java-client
JAVADOC_ARCHIVE=$(PACKAGE_NAME)-javadoc-$(VERSION)
SRC_ARCHIVE=$(PACKAGE_NAME)-$(VERSION)
SIGNING_KEY=056E8E56
GNUPG_PATH=~

WEB_URL=http://www.rabbitmq.com/
NEXUS_STAGE_URL=http://oss.sonatype.org/service/local/staging/deploy/maven2
MAVEN_NEXUS_VERSION=1.7

AMQP_CODEGEN_DIR=$(shell fgrep sibling.codegen.dir build.properties | sed -e 's:sibling\.codegen\.dir=::')

MAVEN_RSYNC_DESTINATION=maven@195.224.125.254:/home/maven/rabbitmq-java-client/

ANT ?= ant
ANT_FLAGS ?=

all:
	$(ANT) $(ANT_FLAGS) build

clean:
	$(ANT) $(ANT_FLAGS) clean

distclean: clean
	$(MAKE) -C $(AMQP_CODEGEN_DIR) clean

dist: distclean srcdist dist_all maven-bundle

dist_all: dist1.5 javadoc-archive

jar:
	$(ANT) $(ANT_FLAGS) jar

maven-bundle:
	$(ANT) $(ANT_FLAGS) -Dimpl.version=$(VERSION) maven-bundle

dist1.5:
	$(ANT) $(ANT_FLAGS) -Ddist.out=build/$(PACKAGE_NAME)-bin-$(VERSION) -Dimpl.version=$(VERSION) dist
	$(MAKE) post-dist TARBALL_NAME=$(PACKAGE_NAME)-bin-$(VERSION)

javadoc-archive:
	$(ANT) $(ANT_FLAGS) javadoc
	cp -Rp build/doc/api build/$(JAVADOC_ARCHIVE)
	(cd build; tar -zcf $(JAVADOC_ARCHIVE).tar.gz $(JAVADOC_ARCHIVE))
	(cd build; zip -q -r $(JAVADOC_ARCHIVE).zip $(JAVADOC_ARCHIVE))
	(cd build; rm -rf $(JAVADOC_ARCHIVE))

post-dist:
	@[ -n "$(TARBALL_NAME)" ] || (echo "Please set TARBALL_NAME."; false)
	chmod a+x build/$(TARBALL_NAME)/*.sh
	cp LICENSE* build/$(TARBALL_NAME)
	(cd build; tar -zcf $(TARBALL_NAME).tar.gz $(TARBALL_NAME))
	(cd build; zip -q -r $(TARBALL_NAME).zip $(TARBALL_NAME))
	(cd build; rm -rf $(TARBALL_NAME))

srcdist: distclean
	mkdir -p build/$(SRC_ARCHIVE)
	cp -Rp `ls | grep -v '^\(build\|README.in\)$$'` build/$(SRC_ARCHIVE)

	mkdir -p build/$(SRC_ARCHIVE)/codegen
	cp -r $(AMQP_CODEGEN_DIR)/* build/$(SRC_ARCHIVE)/codegen/.

	if [ -f README.in ]; then \
		cp README.in build/$(SRC_ARCHIVE)/README; \
		elinks -dump -no-references -no-numbering $(WEB_URL)build-java-client.html \
			>> build/$(SRC_ARCHIVE)/README; \
	fi
	(cd build; tar -zcf $(SRC_ARCHIVE).tar.gz $(SRC_ARCHIVE))
	(cd build; zip -q -r $(SRC_ARCHIVE).zip $(SRC_ARCHIVE))
	(cd build; rm -rf $(SRC_ARCHIVE))

stage-and-promote-maven-bundle:
	( \
	  cd build/bundle; \
	  NEXUS_USERNAME=`cat $(GNUPG_PATH)/../nexus/username`; \
	  NEXUS_PASSWORD=`cat $(GNUPG_PATH)/../nexus/password`; \
	  VERSION=$(VERSION) \
	  SIGNING_KEY=$(SIGNING_KEY) \
	  GNUPG_PATH=$(GNUPG_PATH) \
	  CREDS="$$NEXUS_USERNAME:$$NEXUS_PASSWORD" \
	  ../../nexus-upload.sh \
	    amqp-client-$(VERSION).pom \
	    amqp-client-$(VERSION).jar \
	    amqp-client-$(VERSION)-javadoc.jar \
	    amqp-client-$(VERSION)-sources.jar \
	)

