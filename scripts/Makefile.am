
scriptdir = $(pkgdatadir)/scripts

install-data-hook:
	$(INSTALL) -d $(DESTDIR)$(scriptdir)
	for script in $(srcdir)/*.bsh; do \
		$(INSTALL_DATA) -c $$script $(DESTDIR)$(scriptdir); \
	done

uninstall-hook:
	for script in $(srcdir)/*.bsh; do \
		rm -f $(DESTDIR)$(scriptdir)/`basename $$script`; \
	done
