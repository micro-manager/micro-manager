check_SCRIPTS = ant_test.sh
TESTS = ant_test.sh
.PHONY: ant_test.sh
ant_test.sh:
	@echo "#!/bin/sh" > $@
	@echo "if test -n \"\$$1\"; then testclassarg=-Dtest.class=\"\$$1\"; fi" >> $@
	@echo "export MMCOREJ_LIBRARY_PATH=../../mmCoreAndDevices/MMCoreJ_wrap/.libs" >> $@
	@echo "export MMTEST_ADAPTER_PATH=../../mmCoreAndDevices/DeviceAdapters/SequenceTester/.libs:../../mmCoreAndDevices/DeviceAdapters/Utilities/.libs" >> $@
	@echo "$(ANT) -Dmm.javacflags='"$(JAVACFLAGS)"' \$$testclassarg $(ANTFLAGS) test-only" >> $@
	@chmod u+x $@

CLEANFILES = ant_test.sh

clean-local:
	$(ANT) $(ANTFLAGS) clean
