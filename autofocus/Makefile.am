
AUTOMAKE_OPTIONS = foreign subdir-objects

JARS=MMAutofocus.jar MMAutofocusTB.jar MMAutofocusDuo.jar MMOughtaFocus.jar

MM%.jar: BUILD=$(patsubst MM%.jar,build-%,$@)

$(JARS):MM%.jar: $(srcdir)/%.java
	mkdir -p $(BUILD)
	rm -rf $(BUILD)/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):../mmstudio/MMJ_.jar:. $^ -d $(BUILD)
	cd $(BUILD) && jar cf ../$@ *


mmautofocus_DATA = $(JARS)

CLEANFILES = $(JARS)

clean-local:
	-rm -rf $(patsubst MM%.jar,build-%,$(JARS))
