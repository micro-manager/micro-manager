jar_DATA = MM2MacroExtensions_.jar

.PHONY: MM2MacroExtensions_.jar

MM2MacroExtensions_.jar:
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



clean-local:
	$(ANT) $(ANTFLAGS) clean
	-rm -rf doc

CLEANFILES = ant_test.sh

EXTRA_DIST = src build.xml license.txt README.md
