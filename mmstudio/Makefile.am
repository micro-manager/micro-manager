

# TODO Fix classpath (avoid magic).
MMJ_.jar: $(shell find $(srcdir)/src -name '*.java') \
	$(srcdir)/src/MANIFEST.MF \
	$(srcdir)/src/org/micromanager/license.txt \
	$(srcdir)/src/org/micromanager/conf2/*.html \
	$(srcdir)/src/org/micromanager/icons/*.png \
	$(srcdir)/bin/plugins_mmstudio.config
	test -d build || mkdir build
	rm -rf build/*
	cp $(srcdir)/bin/plugins_mmstudio.config build/plugins.config
	$(JAVAC) $(JAVACFLAGS) -sourcepath ../MMCoreJ_wrap:$(srcdir)/src:. -classpath $(IJJARPATH):$(BSHJARPATH):$(SWINGXJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(CLASSEXT)/*:. $(srcdir)/src/*.java $(srcdir)/src/org/micromanager/acquisition/*.java $(srcdir)/src/org/micromanager/utils/*.java $(srcdir)/src/org/micromanager/api/*.java $(srcdir)/src/org/micromanager/conf2/*.java -d build
	cp -rf $(srcdir)/src/org/micromanager/icons build/org/micromanager
	cp -rf $(srcdir)/src/org/micromanager/conf2/*html build/org/micromanager/conf2
	cd build && jar cf ../MMJ_.jar *


# TODO Remove together with plugins_reader.config (obsolete)
MMReader_.jar: \
   $(srcdir)/MMJ_.jar  \
	$(srcdir)/bin/plugins_reader.config
	cp -f $(srcdir)/bin/plugins_reader.config build/plugins.config
	cd build && jar cf ../MMReader_.jar *


jar_DATA = MMJ_.jar


javadoc:
	-rm -rf doc
	$(MKDIR_P) doc
	javadoc -public \
		-classpath ../MMCoreJ_wrap/MMCoreJ.jar:MMJ_.jar:$(IJJARPATH):$(CLASSEXT)/"*" \
		-d doc \
		-sourcepath $(srcdir)/src \
		-subpackages org.micromanager.api


CLEANFILES = MMJ_.jar MMReader_.jar

clean-local:
	-find . -name \*.class -exec rm {} \;
	-rm -rf doc


EXTRA_DIST = bin src build.xml license.txt
