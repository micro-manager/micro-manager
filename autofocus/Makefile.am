## Process this file with automake to produce Makefile.in
MMAutofocus.jar:  \
	$(srcdir)/Autofocus.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Autofocus.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMAutofocus.jar * 

MMAutofocusTB.jar:  \
	$(srcdir)/AutofocusTB.java
	test -d buildTB || mkdir buildTB
	rm -rf buildTB/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/AutofocusTB.java -d buildTB
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd buildTB && jar cf ../MMAutofocusTB.jar * 

MMAutofocusDuo.jar:  \
	$(srcdir)/AutofocusDuo.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/AutofocusDuo.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMAutofocusDuo.jar * 

MMOughtaFocus.jar:  \
	$(srcdir)/OughtaFocus.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/OughtaFocus.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMOughtaFocus.jar * 

all: MMAutofocus.jar MMAutofocusTB.jar MMAutofocusDuo.jar MMOughtaFocus.jar

install: 
	test -d $(DESTDIR)$(IJPATH)/mmautofocus || mkdir $(DESTDIR)$(IJPATH)/mmautofocus
	cp MMAutofocus.jar $(DESTDIR)$(IJPATH)/mmautofocus/
	cp MMAutofocusTB.jar $(DESTDIR)$(IJPATH)/mmautofocus/
	cp MMAutofocusDuo.jar $(DESTDIR)$(IJPATH)/mmautofocus/
	cp MMOughtaFocus.jar $(DESTDIR)$(IJPATH)/mmautofocus/
	
clean:
	test -e MMAutofocus.jar && rm MMAutofocus.jar || echo "MMAutofocus.jar not found"
	test -e MMAutofocusTB.jar && rm MMAutofocusTB.jar || echo "MMAutofocusTB.jar not found"
	test -e MMAutofocusDuo.jar && rm MMAutofocusDuo.jar || echo "MMAutofocusDuo.jar not found"
	test -e MMOughtaFocus.jar && rm MMOughtaFocus.jar || echo "MMOughtaFocus.jar not found"
