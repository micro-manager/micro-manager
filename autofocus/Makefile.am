## Process this file with automake to produce Makefile.in
JARS=MMAutofocus.jar MMAutofocusTB.jar MMAutofocusDuo.jar MMOughtaFocus.jar

MM%.jar: BUILD=$(patsubst MM%.jar,build-%,$@)

$(JARS):MM%.jar: $(srcdir)/%.java
	mkdir -p $(BUILD)
	rm -rf $(BUILD)/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $^ -d $(BUILD)
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd $(BUILD) && jar cf ../$@ *

all: $(JARS)

install: 
	test -d $(DESTDIR)$(IJPATH)/mmautofocus || mkdir $(DESTDIR)$(IJPATH)/mmautofocus
	cp $(JARS) $(DESTDIR)$(IJPATH)/mmautofocus/
	
clean:
	for f in $(JARS); \
	do \
		test -e $$f && rm $$f || echo "$$f not found"; \
	done
