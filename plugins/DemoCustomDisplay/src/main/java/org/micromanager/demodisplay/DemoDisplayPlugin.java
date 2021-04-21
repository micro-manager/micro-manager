///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
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

package org.micromanager.demodisplay;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * This plugin provides an example implementation of a DataViewer, for creating a custom image
 * display window. This class just implements the MenuPlugin interface.
 */
@Plugin(type = MenuPlugin.class)
public class DemoDisplayPlugin implements MenuPlugin, SciJavaPlugin {
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getName() {
    return "Demo Display";
  }

  @Override
  public String getHelpText() {
    return "Example third-party image display window";
  }

  @Override
  public String getVersion() {
    return "v0.1";
  }

  @Override
  public String getCopyright() {
    return "Copyright (c) 2015 Open Imaging Inc.";
  }

  @Override
  public String getSubMenu() {
    return "Demo";
  }

  @Override
  public void onPluginSelected() {
    new DemoDisplay(studio_);
  }
}
