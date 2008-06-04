## Process this file with automake to produce Makefile.in
MMAutofocus_.jar:  \
	$(srcdir)/AFOptionsDlg.java \
	$(srcdir)/Autofocus_.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMAutofocus_.jar * 

all: MMAutofocus_.jar

install: 
	test -d $(IJPATH)/plugins/Micro-Manager || mkdir $(IJPATH)/plugins/Micro-Manager
	cp MMAutofocus_.jar $(IJPATH)/plugins/Micro-Manager

clean:
	rm MMAutofocus_.jar

EXTRA_DIST = build.xml 
