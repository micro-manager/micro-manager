## Process this file with automake to produce Makefile.in
hcs100x_.jar:  \
	$(srcdir)/src/com/imaging100x/hcs/HCSException.java \
	$(srcdir)/src/com/imaging100x/hcs/PlateAcquisitionData.java \
	$(srcdir)/src/com/imaging100x/hcs/PlateEditor.java \
	$(srcdir)/src/com/imaging100x/hcs/PlatePanel.java \
	$(srcdir)/src/com/imaging100x/hcs/SBSPlate.java \
	$(srcdir)/src/com/imaging100x/hcs/TestPlateAcquisitionData.java \
	$(srcdir)/src/com/imaging100x/hcs/TestPlateScan.java \
	$(srcdir)/src/com/imaging100x/hcs/TestSBSPlate.java\
	$(srcdir)/src/com/imaging100x/hcs/WellPositionList.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath ../MMCoreJ_wrap:$(srcdir)/src:../mmstudio/src:. -classpath $(IJJARPATH):$(BSHJARPATH):$(topsrcdir)/bin/MMCoreJ.jar:. $(srcdir)/src/com/imaging100x/hcs/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../hcs100x_.jar * 

all: hcs100x_.jar

install: 
	test -d $(IJPATH)/plugins/Micro-Manager || mkdir $(IJPATH)/plugins/Micro-Manager
	cp hcs100x_.jar $(IJPATH)/plugins/Micro-Manager

clean:
	rm hcs100x_.jar

javadoc:
	test -d $(srcdir)/doc || mkdir $(srcdir)/doc
	rm -rf $(srcdir)/doc/*
	javadoc -public -classpath $(IJJARPATH):$(BSHJARPATH):$(IJPATH)/plugins/Micro-Manager/MMCoreJ.jar:$(IJPATH)/plugins/Micro-Manager/MMJ_.jar:. -d doc $(srcdir)/src/org/micromanager/*.java $(srcdir)/src/org/micromanager/*/*.java

EXTRA_DIST = bin src build.xml license.txt
