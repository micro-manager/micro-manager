noinst_DATA = AntExtensions.jar

.PHONY: AntExtensions.jar

AntExtensions.jar:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) jar

clean-local:
	$(ANT) $(ANTFLAGS) clean
