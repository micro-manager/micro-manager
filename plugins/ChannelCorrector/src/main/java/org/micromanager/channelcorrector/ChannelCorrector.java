///////////////////////////////////////////////////////////////////////////////
// FILE:          ChannelCorrector.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     ChannelCorrector plugin
//
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2020
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

package org.micromanager.channelcorrector;

import org.micromanager.Studio;
import org.micromanager.MenuPlugin;

import org.micromanager.internal.utils.WindowPositioning;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Plugin that copies (parts of) Micro-Manager datasets to a new datastore
 *
 * @author nico
 */
// replace with:
// @plugin(type=MenuPlugin.class)
// to make the code show up in the gearmenu when running under Netbeans
@Plugin(type = MenuPlugin.class)
public class ChannelCorrector implements MenuPlugin, SciJavaPlugin {
  public static final String MENUNAME = "Correct Channels...";
  private Studio studio_;
  private ChannelCorrectorFrame ourFrame_;

  @Override
  public String getSubMenu() {
    return "Analysis";
  }

  @Override
  public void onPluginSelected() {
    if (ourFrame_ != null) {
      studio_.displays().unregisterForEvents(ourFrame_);
      ourFrame_.dispose();
    }
    ourFrame_ = new ChannelCorrectorFrame(studio_);
    studio_.displays().registerForEvents(ourFrame_);
    WindowPositioning.setUpLocationMemory(ourFrame_, ChannelCorrector.class, null);
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
    return "Corrects spatial aberratons between channels in Micro-Manager datasets";
  }

  @Override
  public String getVersion() {
    return "Version 0.1-beta";
  }

  @Override
  public String getCopyright() {
    return "Regents of the University of California, 2020";
  }
}
