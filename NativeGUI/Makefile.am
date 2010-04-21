NativeGUISOURCES = \
	NativeGUI.h \
	NativeGUIMac.mm \
	NativeGUI.i \
	NativeGUIstub.java

NativeGUITARGETS = \
	NativeGUI.jar \
	libNativeGUI.jnilib

$(NativeGUITARGETS): $(NativeGUISOURCES)
  ifeq ($(OSNAME),Darwin)
	$(SWIG) -c++ -java -module NativeGUI -package org.micromanager.nativegui -outdir . NativeGUI.i
	$(CXX) $(CXXFLAGS) -dynamiclib NativeGUIMac.mm NativeGUI_wrap.cxx -o libNativeGUI.jnilib $(JAVAINC) NativeGUI.h -framework Cocoa
	$(JAVAC) NativeGUI.java NativeGUIJNI.java -d .
	$(JAR) cf NativeGUI.jar org
  else
	cp NativeGUIstub.java NativeGUI.java
	$(JAVAC) NativeGUI.java -d .
	$(JAR) cf NativeGUI.jar org
  endif

all:
	make $(NativeGUITARGETS)

install:
  ifeq ($(OSNAME),Darwin)
	cp NativeGUI.jar $(DESTDIR)$(JARPATH)
	cp libNativeGUI.jnilib $(DESTDIR)$(DEVICEADAPTERPATH)
  else
	cp NativeGUI.jar $(DESTDIR)$(JARPATH)
  endif

clean:
	rm org/micromanager/nativegui/*.class
	rm NativeGUI.java
	rm NativeGUIJNI.java
	rm *.cxx
	rm libNativeGUI.jnilib
	rm NativeGUI.jar


