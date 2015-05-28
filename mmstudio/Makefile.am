jar_DATA = MMJ_.jar

.PHONY: MMJ_.jar

MMJ_.jar:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) jar


check_SCRIPTS = ant_test.sh
TESTS = ant_test.sh
.PHONY: ant_test.sh
ant_test.sh:
	@echo "#!/bin/sh" > $@
	@echo "if test -n \"\$$1\"; then testclassarg=-Dtest.class=\"\$$1\"; fi" >> $@
	@echo "$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" \$$testclassarg $(ANTFLAGS) test-only" >> $@
	@chmod u+x $@


# TODO Clean up javadoc build
javadoc:
	-rm -rf doc
	$(MKDIR_P) doc
	javadoc -public \
		-classpath ../MMCoreJ_wrap/MMCoreJ.jar:MMJ_.jar:../dependencies/artifacts/compile/"*":../dependencies/artifacts/imagej/"*" \
		-d doc \
		-sourcepath $(srcdir)/src \
		-subpackages org.micromanager.api


clean-local:
	$(ANT) $(ANTFLAGS) clean
	-rm -rf doc

CLEANFILES = ant_test.sh

EXTRA_DIST = bin src build.xml license.txt
