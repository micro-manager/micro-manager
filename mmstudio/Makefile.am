jar_DATA = MMJ_.jar

.PHONY: MMJ_.jar

MMJ_.jar:
	$(ANT) $(ANTFLAGS) jar


# TODO Clean up javadoc build
javadoc:
	-rm -rf doc
	$(MKDIR_P) doc
	javadoc -public \
		-classpath ../MMCoreJ_wrap/MMCoreJ.jar:MMJ_.jar:$(IJJARPATH):$(CLASSEXT)/"*" \
		-d doc \
		-sourcepath $(srcdir)/src \
		-subpackages org.micromanager.api


clean-local:
	$(ANT) $(ANTFLAGS) clean
	-rm -rf doc


EXTRA_DIST = bin src build.xml license.txt
