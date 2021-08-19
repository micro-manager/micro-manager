jar_DATA = MMJ_.jar

.PHONY: MMJ_.jar

MMJ_.jar:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) jar


check_SCRIPTS = ant_test.sh
TESTS = ant_test.sh
.PHONY: ant_test.sh
ant_test.sh:
	@echo "#!/bin/sh" > $@
	@echo "find $(srcdir)/src/test/resources -name '*.gz' | xargs gunzip -kv" >> $@
	@echo "if test -n \"\$$1\"; then testclassarg=-Dtest.class=\"\$$1\"; fi" >> $@
	@echo "$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" \$$testclassarg $(ANTFLAGS) test-only" >> $@
	@chmod u+x $@


# TODO Clean up javadoc build
javadoc:
	-rm -rf doc
	$(MKDIR_P) doc
	javadoc -public \
		-classpath ../$(MM_CPP_DIR)/MMCoreJ_wrap/MMCoreJ.jar:../dependencies/artifacts/compile/"*":../dependencies/artifacts/imagej/"*" \
		-d doc \
		-sourcepath $(srcdir)/src/main/java \
		-subpackages org.micromanager \
		-exclude `cd $(srcdir); find src/main/java -type d |grep '\<internal\>' |tr / . |sed 's/^src\.main\.java\.//' |paste -sd ':' -`


clean-local:
	$(ANT) $(ANTFLAGS) clean
	-rm -rf doc

CLEANFILES = ant_test.sh

EXTRA_DIST = bin src build.xml license.txt
