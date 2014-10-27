check_SCRIPTS = ant_test.sh
TESTS = ant_test.sh
.PHONY: ant_test.sh
ant_test.sh:
	@echo "#!/bin/sh" > $@
	@echo "if test -n \"\$$1\"; then testclassarg=-Dtest.class=\"\$$1\"; fi" >> $@
	@echo "export MMCOREJ_LIBRARY_PATH=../../MMCoreJ_wrap/.libs" >> $@
	@echo "export MMTEST_ADAPTER_PATH=../../DeviceAdapters/SequenceTester/.libs" >> $@
	@echo "$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" \$$testclassarg $(ANTFLAGS) test-only" >> $@
	@chmod u+x $@

clean-local:
	$(ANT) $(ANTFLAGS) clean
