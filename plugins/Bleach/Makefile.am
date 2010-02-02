## Process this file with automake to produce Makefile.in
MMBleach_.jar:  \
	$(srcdir)/src/BleachControlDlg.java \
	$(srcdir)/src/BleachThread.java \
	$(srcdir)/src/MMBleachPlugin_.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/src/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMBleach_.jar * 

all: MMBleach_.jar

install: 
	test -d $(DESTDIR)$(IJPATH)/plugins/Micro-Manager || mkdir -p $(DESTDIR)$(IJPATH)/plugins/Micro-Manager
	cp MMBleach_.jar $(DESTDIR)$(IJPATH)/plugins/Micro-Manager

clean:
	rm MMBleach_.jar 

EXTRA_DIST = src build.xml
