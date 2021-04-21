///////////////////////////////////////////////////////////////////////////////
// FILE:          ZProjectorPlugin.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     ZProjector plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2017-2019
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

package org.micromanager.zprojector;

import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Plugin that projects Micro-Manager datasets along a selected axis Usually, this is a projection
 * along the z-axis (but others such as t, can be useful as well)
 *
 * @author nico
 */

// to make the code show up in the gearmenu when running under Netbeans
@Plugin(type = DisplayGearMenuPlugin.class)
public class ZProjectorPlugin implements DisplayGearMenuPlugin, SciJavaPlugin {
  public static final String MENUNAME = "Project...";
  public static final String AXISKEY = "AxisKey";
  public static final String PROJECTION_METHOD = "ProjectionMethod";
  public static final String SAVE = "Save";

  private Studio studio_;

  @Override
  public String getSubMenu() {
    return "";
  }

  @Override
  public void onPluginSelected(DisplayWindow display) {
    // no need to hold on to the instance, we just want to create the frame
    ZProjectorPluginFrame ourFrame = new ZProjectorPluginFrame(studio_, display);
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
    return "Generates a Projection of a Micro-Manager datasets along the selected axis";
  }

  @Override
  public String getVersion() {
    return "Version 0.2";
  }

  @Override
  public String getCopyright() {
    return "Regents of the University of California, 2017-2019";
  }
}
