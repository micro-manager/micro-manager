///////////////////////////////////////////////////////////////////////////////
// FILE:          AssembleData.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     AssembleData plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2019
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

package org.micromanager.assembledata;

import ij.plugin.PlugIn;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** @author kthorn */
@Plugin(type = MenuPlugin.class)
public class AssembleData implements PlugIn, MenuPlugin, SciJavaPlugin {
  public static final String MENUNAME = "Assemble Data";
  public static final String TOOLTIPDESCRIPTION =
      "Combines one or more data sets into one.  Also combines positions.";

  public static final String VERSIONNUMBER = "0.1";

  private Studio studio_;
  private AssembleDataForm form_;

  @Override
  public void run(String string) {
    if (form_ != null && !form_.wasDisposed()) {
      form_.setVisible(true);
      form_.toFront();
    } else {
      form_ = new AssembleDataForm(studio_);
    }
  }

  @Override
  public void onPluginSelected() {
    run("");
  }

  @Override
  public String getSubMenu() {
    return "Analysis";
  }

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
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
    return VERSIONNUMBER;
  }

  @Override
  public String getCopyright() {
    return "University of California, 2019";
  }
}
