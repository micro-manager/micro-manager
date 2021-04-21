///////////////////////////////////////////////////////////////////////////////
// FILE:          PtcTools.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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

package org.micromanager.ptctools;

import org.micromanager.MenuPlugin;
import org.micromanager.data.ProcessorPlugin;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import org.micromanager.Studio;

/**
 * Micro-Manager plugin acuiring a data set for Photon Transfer Curve analysis
 *
 * @author nico
 */
@Plugin(type = ProcessorPlugin.class)
public class PtcTools implements MenuPlugin, SciJavaPlugin {
  public static final String MENU_NAME = "Photon Transfer Curve assistant";
  public static final String TOOL_TIP_DESCRIPTION =
      "Helps create a dataset for photon transfer curve analysis";
  private Studio studio_;
  private PtcToolsFrame ptcFrame_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getName() {
    return MENU_NAME;
  }

  @Override
  public String getHelpText() {
    return TOOL_TIP_DESCRIPTION;
  }

  @Override
  public String getVersion() {
    return "0.1";
  }

  @Override
  public String getCopyright() {
    return "University of California, 2018";
  }

  @Override
  public String getSubMenu() {
    return "Acquisition Tools";
  }

  @Override
  public void onPluginSelected() {
    if (!PtcToolsFrame.WINDOWOPEN) {
      ptcFrame_ = new PtcToolsFrame(studio_);
    }
    ptcFrame_.setVisible(true);
    ptcFrame_.toFront();
  }
}
