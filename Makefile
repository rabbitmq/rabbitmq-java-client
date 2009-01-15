VERSION=0.0.0

PACKAGE_NAME=rabbitmq-java-client

JAVADOC_ARCHIVE=$(PACKAGE_NAME)-javadoc-$(VERSION)
SRC_ARCHIVE=$(PACKAGE_NAME)-$(VERSION)

WEB_URL=http://stage.rabbitmq.com/

AMQP_CODEGEN_DIR=$(shell fgrep sibling.codegen.dir build.properties | sed -e 's:sibling\.codegen\.dir=::')

MAVEN_RSYNC_DESTINATION=maven@195.224.125.254:/home/maven/rabbitmq-java-client/

all:
	ant build

clean:
	ant clean

distclean: clean
	make -C $(AMQP_CODEGEN_DIR) clean

dist: distclean srcdist dist1.5 dist1.4 javadoc-archive

maven-bundle: distclean
	ant -Dimpl.version=$(VERSION) maven-bundle

dist1.5:
	ant -Ddist.out=build/$(PACKAGE_NAME)-bin-$(VERSION) -Dimpl.version=$(VERSION) dist
	$(MAKE) post-dist TARBALL_NAME=$(PACKAGE_NAME)-bin-$(VERSION)

dist1.4:
	ant -Ddist.out=build/$(PACKAGE_NAME)-java1.4bin-$(VERSION) -Dimpl.version=$(VERSION) dist1.4
	$(MAKE) post-dist TARBALL_NAME=$(PACKAGE_NAME)-java1.4bin-$(VERSION)

javadoc-archive:
	ant javadoc
	cp -Rp build/doc/api build/$(JAVADOC_ARCHIVE)
	(cd build; tar -zcf $(JAVADOC_ARCHIVE).tar.gz $(JAVADOC_ARCHIVE))
	(cd build; zip -r $(JAVADOC_ARCHIVE).zip $(JAVADOC_ARCHIVE))
	(cd build; rm -rf $(JAVADOC_ARCHIVE))

post-dist:
	@[ -n "$(TARBALL_NAME)" ] || (echo "Please set TARBALL_NAME."; false)
	chmod a+x build/$(TARBALL_NAME)/*.sh
	cp LICENSE* build/$(TARBALL_NAME)
	(cd build; tar -zcf $(TARBALL_NAME).tar.gz $(TARBALL_NAME))
	(cd build; zip -r $(TARBALL_NAME).zip $(TARBALL_NAME))
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
	(cd build; zip -r $(SRC_ARCHIVE).zip $(SRC_ARCHIVE))
	(cd build; rm -rf $(SRC_ARCHIVE))

deploy-maven-bundle: maven-bundle
	rsync -r build/bundle/* $(MAVEN_RSYNC_DESTINATION)
