## Process this file with automake to produce Makefile.in
MMAutofocus_.jar:  \
	$(srcdir)/AFOptionsDlg.java \
	$(srcdir)/Autofocus_.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/AFOptionsDlg.java $(srcdir)/Autofocus_.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMAutofocus_.jar * 

MMAutofocusTB_.jar:  \
	$(srcdir)/AFOptionsDlgTB.java \
	$(srcdir)/AutofocusTB_.java
	test -d buildTB || mkdir buildTB
	rm -rf buildTB/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/AFOptionsDlgTB.java $(srcdir)/AutofocusTB_.java -d buildTB
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd buildTB && jar cf ../MMAutofocusTB_.jar * 

all: MMAutofocus_.jar MMAutofocusTB_.jar

install: 
	test -d $(IJPATH)/plugins/Micro-Manager || mkdir $(IJPATH)/plugins/Micro-Manager
	cp MMAutofocus_.jar $(IJPATH)/plugins/Micro-Manager
	cp MMAutofocusTB_.jar $(IJPATH)/plugins/Micro-Manager

clean:
	test -e MMAutofocus_.jar && rm MMAutofocus_.jar || echo "MMAutofocus_.jar not found"
	test -e MMAutofocusTB_.jar && rm MMAutofocusTB_.jar || echo "MMAutofocusTB_.jar not found"
