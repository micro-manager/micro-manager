package org.micromanager.CRISP;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** @author Nico Stuurman */
@Plugin(type = MenuPlugin.class)
public class CRISP implements MenuPlugin, SciJavaPlugin {
  public static final String MENUNAME = "ASI CRISP Control";
  public static final String TOOLTIPDESCRIPTION = "Control the ASI CRISP Autofocus System";

  @SuppressWarnings("unused")
  private Studio gui_;

  private CRISPFrame myFrame_;

  @Override
  public void setContext(Studio app) {
    gui_ = app;
  }

  @Override
  public String getSubMenu() {
    return "Device Control";
  }

  @Override
  public void onPluginSelected() {
    if (myFrame_ == null) {
      try {
        myFrame_ = new CRISPFrame(gui_, this);
      } catch (Exception e) {
        gui_.logs().logError(e);
        return;
      }
    }
    myFrame_.setVisible(true);
  }

  public void tellFrameClosed() {
    myFrame_ = null;
  }

  @Override
  public String getName() {
    return MENUNAME;
  }

  @Override
  public String getHelpText() {
    return TOOLTIPDESCRIPTION;
  }

  @Override
  public String getVersion() {
    return "0.10";
  }

  @Override
  public String getCopyright() {
    return "University of California, 2011-2016";
  }
}
