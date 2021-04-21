///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Display implementation
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.gearmenu;

import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.LineProfile;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = DisplayGearMenuPlugin.class)
public final class LineProfileItem implements DisplayGearMenuPlugin, SciJavaPlugin {
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getName() {
    return "Line Profile";
  }

  @Override
  public String getHelpText() {
    return "Show a plot of image pixel intensity over the current ROI";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public String getCopyright() {
    return "Copyright (c) Regents of the University of California";
  }

  @Override
  public String getSubMenu() {
    return "";
  }

  @Override
  public void onPluginSelected(DisplayWindow display) {
    new LineProfile(display);
  }
}
