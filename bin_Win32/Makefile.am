install:
	if  [ ! -d $(DESTDIR)$(IJPATH)/oldcfg ]  ; then mkdir -p $(DESTDIR)$(IJPATH)/oldcfg; fi;
	if [ -e $(DESTDIR)$(IJPATH)/MMConfig.cfg ] ; then mv $(DESTDIR)$(IJPATH)/*.cfg $(IJPATH)/oldcfg; fi;
	cp MMConfig_demo.cfg $(DESTDIR)$(IJPATH)
