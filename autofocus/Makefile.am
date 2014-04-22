
AUTOMAKE_OPTIONS = foreign subdir-objects

JARS=MMAutofocus.jar MMAutofocusTB.jar MMAutofocusDuo.jar MMOughtaFocus.jar

MM%.jar: BUILD=$(patsubst MM%.jar,build-%,$@)

$(JARS):MM%.jar: $(srcdir)/%.java
	mkdir -p $(BUILD)
	rm -rf $(BUILD)/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):../mmstudio/MMJ_.jar:. $^ -d $(BUILD)
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd $(BUILD) && jar cf ../$@ *


autofocusdir = $(pkgdatadir)/mmautofocus
autofocus_DATA = $(JARS)


CLEANFILES = $(JARS)

clean-local:
	-rm -rf $(patsubst MM%.jar,build-%,$(JARS))
