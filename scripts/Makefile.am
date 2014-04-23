install-data-local:
	$(INSTALL) -d $(DESTDIR)$(mmscriptdir)
	@for script in $(srcdir)/*.bsh; do \
		echo $(INSTALL_DATA) -c $$script $(DESTDIR)$(mmscriptdir); \
		$(INSTALL_DATA) -c $$script $(DESTDIR)$(mmscriptdir); \
	done

uninstall-local:
	@for script in $(srcdir)/*.bsh; do \
		echo rm -f $(DESTDIR)$(mmscriptdir)/`basename $$script`; \
		rm -f $(DESTDIR)$(mmscriptdir)/`basename $$script`; \
	done
