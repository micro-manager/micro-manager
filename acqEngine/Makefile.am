jar_DATA = MMAcqEngine.jar

.PHONY: MMAcqEngine.jar

MMAcqEngine.jar:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) jar

check_SCRIPTS = ant_test.sh
TESTS = ant_test.sh
.PHONY: ant_test.sh
ant_test.sh:
	@echo "#!/bin/sh" > $@
	@echo "if test -n \"\$$1\"; then testclassarg=-Dtest.class=\"\$$1\"; fi" >> $@
	@echo "$(ANT) -Dmm.javacflags='"$(JAVACFLAGS)"' \$$testclassarg $(ANTFLAGS) test-only" >> $@
	@chmod u+x $@

CLEANFILES = ant_test.sh

.PHONY: pitest
pitest:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) pitest

.PHONY: pitest-golden
pitest-golden:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" -Dpitest.targetTests="org.micromanager.internal.jacque.*GoldenTest" $(ANTFLAGS) pitest

.PHONY: pitest-golden-seqgen
pitest-golden-seqgen:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" -Dpitest.targetTests=org.micromanager.internal.jacque.SequenceGeneratorGoldenTest $(ANTFLAGS) pitest

.PHONY: pitest-golden-exec
pitest-golden-exec:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" -Dpitest.targetTests=org.micromanager.internal.jacque.EventExecutionGoldenTest $(ANTFLAGS) pitest

clean-local:
	$(ANT) $(ANTFLAGS) clean
