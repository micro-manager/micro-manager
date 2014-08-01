jar_DATA = MMAcqEngine.jar

.PHONY: MMAcqEngine.jar

MMAcqEngine.jar:
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) jar

clean-local:
	$(ANT) $(ANTFLAGS) clean
