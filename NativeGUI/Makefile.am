NativeGUISOURCES = \
	NativeGUI.h \
	NativeGUIMac.mm \
	NativeGUI.i \
	NativeGUIstub.java \
	QuitHandlerMac.java \
	QuitHandlerStub.java

NativeGUITARGETS = \
	NativeGUI.jar \
	libNativeGUI.jnilib

$(NativeGUITARGETS): $(NativeGUISOURCES)
  ifeq ($(OSNAME),Darwin)
	$(SWIG) -c++ -java -module NativeGUI -package org.micromanager.nativegui -outdir . NativeGUI.i
	$(CXX) $(CXXFLAGS) -dynamiclib NativeGUIMac.mm NativeGUI_wrap.cxx -o libNativeGUI.jnilib $(JAVAINC) NativeGUI.h -framework Cocoa
	cp QuitHandlerMac.java QuitHandler.java
	$(JAVAC) NativeGUI.java NativeGUIJNI.java QuitHandler.java -d .
	$(JAR) cf NativeGUI.jar org
  else
	cp NativeGUIstub.java NativeGUI.java
	cp QuitHandlerStub.java QuitHandler.java
	$(JAVAC) NativeGUI.java QuitHandler.java -d .
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
	rm org/micromanager/nativegui/*.class || echo "OK"
	rm NativeGUI.java || echo "OK"
	rm NativeGUIJNI.java || echo "OK"
	rm QuitHandler.java || echo "OK"
	rm *.cxx || echo "OK"
	rm libNativeGUI.jnilib || echo "OK"
	rm NativeGUI.jar || echo "OK"
	

