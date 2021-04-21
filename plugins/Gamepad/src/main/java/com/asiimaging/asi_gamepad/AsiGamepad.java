///////////////////////////////////////////////////////////////////////////////
// FILE:          asi_gamepad.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:      asi gamepad plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Vikram Kopuri
//
// COPYRIGHT:    Applied Scientific Instrumentation (ASI), 2018
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.asiimaging.asi_gamepad;

import java.awt.event.WindowEvent;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;

import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class AsiGamepad implements MenuPlugin, SciJavaPlugin {

  public static final String MENU_NAME = "ASI Gamepad";
  public static final String TOOLTIP_DESCRIPTION = "XBox Controller for MicroManager";
  public static final String VERSION_STRING = "0.0";
  public static final String COPYRIGHT_STRING = "Applied Scientific Instrumentation (ASI), 2018";

  private static AsiGamepadFrame asiGamepadFrame = null;

  private Studio mm_;

  @Override
  public void setContext(Studio mm) {
    mm_ = mm;
  }

  public static AsiGamepadFrame getFrame() {
    return asiGamepadFrame;
  }

  @Override
  public String getCopyright() {
    return COPYRIGHT_STRING;
  }

  @Override
  public void onPluginSelected() {
    // close frame before re-load if already open
    dispose();
    // create brand new instance of plugin frame every time
    try {
      asiGamepadFrame = new AsiGamepadFrame(mm_);
      // gui_.addMMListener(agf_frame);
      // gui_.addMMBackgroundListener(agf_frame);
    } catch (Exception e) {
      mm_.logs().showError(e);
    }
  }

  @Override
  public String getHelpText() {
    return TOOLTIP_DESCRIPTION;
  }

  @Override
  public String getName() {
    return MENU_NAME;
  }

  @Override
  public String getVersion() {
    if (asiGamepadFrame != null) {
      return Float.toString(asiGamepadFrame.plugin_ver);
    } else {
      return VERSION_STRING;
    }
  }

  public void dispose() {
    if (asiGamepadFrame != null && asiGamepadFrame.isDisplayable()) {
      WindowEvent wev = new WindowEvent(asiGamepadFrame, WindowEvent.WINDOW_CLOSING);
      // ReportingUtils.logMessage("!!!!closed from main gamepad class!!!!");
      asiGamepadFrame.dispatchEvent(wev);
    }
  }

  /**
   * Indicate which sub-menu of the Plugins menu this plugin should appear in. If that sub-menu does
   * not exist, it will be created. If an empty string is returned, then the plugin will be inserted
   * directly into the Plugins menu, instead of into a sub-menu.
   *
   * @return Sub-menu of the Plugins menu hosting this entry
   */
  @Override
  public String getSubMenu() {
    return "Device Control";
  }
}
