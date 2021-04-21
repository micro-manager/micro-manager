///////////////////////////////////////////////////////////////////////////////
// FILE:          PointAndShootAnalysis.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     PointAndShootAnalysis plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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

package org.micromanager.pointandshootanalysis;

import ij.plugin.PlugIn;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** @author nico */
@Plugin(type = MenuPlugin.class)
public class PointAndShootAnalysis implements PlugIn, MenuPlugin, SciJavaPlugin {
  Studio studio_;
  PointAndShootDialog dialog_;

  @Override
  public void run(String string) {
    if (dialog_ != null && !dialog_.wasDisposed()) {
      dialog_.setVisible(true);
      dialog_.toFront();
    } else {
      dialog_ = new PointAndShootDialog(studio_);
    }
  }

  @Override
  public String getSubMenu() {
    return "Analysis";
  }

  @Override
  public void onPluginSelected() {
    run("");
  }

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
    studio_.events().registerForEvents(this);
  }

  @Override
  public String getName() {
    return "Point and Shoot Analysis";
  }

  @Override
  public String getHelpText() {
    return "Plugin to analysis data generate by bleaching spots using the Projector plugin Point And Shoot mode";
  }

  @Override
  public String getVersion() {
    return "0.1";
  }

  @Override
  public String getCopyright() {
    return "Regents of the University of California, 2018";
  }
}
