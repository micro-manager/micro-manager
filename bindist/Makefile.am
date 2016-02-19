mmdata_DATA = any-platform/MMConfig_demo.cfg

# We can't use Automake's nobase_ prefix here since we need to strip
# 'any-platform' from the path (and we cannot create an
# 'any-platform/Makefile.am' because that would get shipped on Windows).
# So use a different way. See the section about nobase_ and dist_ in the
# Automake manual.
if INSTALL_AS_IMAGEJ_PLUGIN
ijtoolsetdir = $(imagejdir)/macros/toolsets
dist_ijtoolset_DATA = any-platform/macros/toolsets/diSPIM_Tools.txt
endif
