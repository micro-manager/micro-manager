/*
* ASI_CRISP_V2.java
* Micro Manager Plugin for ASIs CRISP Autofocus
* Based on Nico Stuurman's original ASI CRISP Control plugin.
* Modified by Vikram Kopuri, ASI
* Last Updated 9/10/2014

*/
package com.asiimaging.CRISPv2;

import org.micromanager.Studio;
import java.awt.event.WindowEvent;

import org.micromanager.MenuPlugin;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** @author Vik */
@Plugin(type = MenuPlugin.class)
public class ASI_CRISP_V2 implements MenuPlugin, SciJavaPlugin {

  public static String menuName = "ASI CRISP V2[Beta]";
  public static String tooltipDescription = "Interface for ASIs CRISP Autofocus ";
  private Studio gui_;
  private ASI_CRISP_Frame myFrame_;

  @Override
  public void setContext(Studio app) {
    gui_ = app;
  }

  @Override
  public String getSubMenu() {
    return "Beta";
  }

  @Override
  public void onPluginSelected() {
    if (myFrame_ != null) {
      WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
      myFrame_.dispatchEvent(wev);
      myFrame_ = null;
    }
    if (myFrame_ == null) {
      try {
        myFrame_ = new ASI_CRISP_Frame(gui_);
      } catch (Exception e) {
        gui_.logs().showError(e, "Failed to open " + menuName);
        return;
      }
    }
    myFrame_.setVisible(true);
  }

  @Override
  public String getName() {
    return menuName;
  }

  @Override
  public String getHelpText() {
    return "Interface for ASIs CRISP Autofocus. Written by ASI";
  }

  @Override
  public String getVersion() {
    return "2.0";
  }

  @Override
  public String getCopyright() {
    return "(C) 2014 ASI";
  }
}
