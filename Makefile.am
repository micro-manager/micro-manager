
AUTOMAKE_OPTIONS = foreign
ACLOCAL_AMFLAGS = -I mmCoreAndDevices/m4

if BUILD_MMCOREJ

fetchdeps:
	$(ANT) $(ANTFLAGS) -f buildscripts/fetchdeps.xml

endif

if BUILD_CPP_TESTS
TESTING_DIR = testing
endif


if BUILD_MMCORE
MMCORE_DIR = mmCoreAndDevices/MMCore
endif

if BUILD_MMCOREJ
ANTEXTENSIONS = buildscripts/AntExtensions
MMCOREJ_DIR = mmCoreAndDevices/MMCoreJ_wrap
endif

if BUILD_SECRETDEVICEADAPTERS
SECRETDEVICEADAPTERS = mmCoreAndDevices/SecretDeviceAdapters
endif


if BUILD_JAVA_APP

ANTEXTENSIONS = buildscripts/AntExtensions
JAVA_APP_DIRS = mmstudio acqEngine libraries autofocus plugins mmAsImageJMacros scripts
SYSTEMTEST_DIR = systemtest

if INSTALL_AS_IMAGEJ_PLUGIN

launcher_SCRIPTS = buildscripts/launchers/mmimagej

# Script to launch as ImageJ plugin
buildscripts/launchers/mmimagej: $(srcdir)/buildscripts/launchers/mmimagej.in
	sed \
		-e 's%@imagejdir[@]%$(imagejdir)%g' \
		-e 's%@imagejjar[@]%$(imagejjar)%g' \
		-e 's%@JAVA[@]%$(JAVA)%g' \
		$(srcdir)/buildscripts/launchers/mmimagej.in >$@

# We don't want our copy if ij.jar, because we are using an existing ImageJ.
install-data-hook:
	rm -f $(DESTDIR)$(jardir)/ij.jar

# There is no reason to keep the .la files when installing into ImageJ.
install-exec-hook:
	rm -f $(DESTDIR)$(wrappermoduledir)/*.la
	rm -f $(DESTDIR)$(deviceadapterdir)/*.la
	@echo "" 1>&2; \
	echo "##############################################################################" 1>&2; \
	echo "Micro-Manager has been installed as an ImageJ plugin in" 1>&2; \
	echo "$(imagejdir)" 1>&2; \
	echo "" 1>&2; \
	echo "You can start Micro-Manager from the command line by typing" 1>&2; \
	echo "$(launcherdir)/mmimagej" 1>&2; \
	echo "or from the ImageJ menu (Plugins > Micro-Manager > Micro-Manager Studio)." 1>&2; \
	echo "##############################################################################" 1>&2; \
	echo "" 1>&2

else # INSTALL_AS_IMAGEJ_PLUGIN

launcher_SCRIPTS = buildscripts/launchers/micromanager

# Launch script for running outside of ImageJ
buildscripts/launchers/micromanager: $(srcdir)/buildscripts/launchers/micromanager.in
	sed \
		-e 's%@wrappermoduledir[@]%$(wrappermoduledir)%g' \
		-e 's%@mmplugindir[@]%$(mmplugindir)%g' \
		-e 's%@mmlibrarydir[@]%$(mmlibrarydir)%g' \
		-e 's%@mmautofocusdir[@]%$(mmautofocusdir)%g' \
		-e 's%@mmdatadir[@]%$(mmdatadir)%g' \
		-e 's%@jardir[@]%$(jardir)%g' \
		-e 's%@imagejjar[@]%$(imagejjar)%g' \
		-e 's%@JAVA[@]%$(JAVA)%g' \
		$(srcdir)/buildscripts/launchers/micromanager.in >$@

install-exec-hook:
	@echo "" 1>&2; \
	echo "##############################################################################" 1>&2; \
	echo "Micro-Manager has been installed in" 1>&2; \
	echo "$(prefix)" 1>&2; \
	echo "" 1>&2; \
	echo "You can run Micro-Manager by typing" 1>&2; \
	echo "$(launcherdir)/micromanager" 1>&2; \
	echo "##############################################################################" 1>&2; \
	echo "" 1>&2

endif # INSTALL_AS_IMAGEJ_PLUGIN
endif # BUILD_JAVA_APP


if INSTALL_DEPENDENCY_JARS

if INSTALL_AS_IMAGEJ_PLUGIN
# When installing into existing ImageJ, don't install ij.jar
DEPENDENCY_JAR_CONFS=compile,runtime
else
DEPENDENCY_JAR_CONFS=compile,imagej,runtime
endif

install-data-local:
	$(INSTALL) -d $(DESTDIR)$(jardir)
	@for jar in $(srcdir)/dependencies/artifacts/{$(DEPENDENCY_JAR_CONFS)}/*.jar; do \
		echo $(INSTALL_DATA) -c $$jar $(DESTDIR)$(jardir); \
		$(INSTALL_DATA) -c $$jar $(DESTDIR)$(jardir); \
	done

uninstall-local:
	@for jar in $(srcdir)/dependencies/artifacts/{$(DEPENDENCY_JAR_CONFS)}/*.jar; do \
		echo rm -f $(DESTDIR)$(jardir)/`basename $$jar`; \
		rm -f $(DESTDIR)$(jardir)/`basename $$jar`; \
	done

endif # INSTALL_DEPENDENCY_JARS


CLEANFILES = $(launcher_SCRIPTS)


# TODO: Building of DeviceAdapters could be made optional.
SUBDIRS = \
	$(ANTEXTENSIONS) \
	$(TESTING_DIR) \
	mmCoreAndDevices/MMDevice \
	$(MMCORE_DIR) \
	$(MMCOREJ_DIR) \
	$(JAVA_APP_DIRS) \
	mmCoreAndDevices/DeviceAdapters \
	$(SECRETDEVICEADAPTERS) \
	$(SYSTEMTEST_DIR)
	bindist


dox:
	-rm -rf doxygen/out
	$(MKDIR_P) doxygen/out/MMDevice
	$(MKDIR_P) doxygen/out/MMCore
	doxygen doxygen/MMDevice
	doxygen doxygen/MMCore
