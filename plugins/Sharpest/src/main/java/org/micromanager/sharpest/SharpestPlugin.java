///////////////////////////////////////////////////////////////////////////////
//FILE:          SharpestPlugin.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Sharpest plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Altos Labs, 2024
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

package org.micromanager.sharpest;

import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;


/**
 * Plugin that selects the sharpest image along the z axis.
 *
 * @author nico
 */
// to make the code show up in the gearmenu when running under Netbeans
@Plugin(type = DisplayGearMenuPlugin.class)
public class SharpestPlugin implements DisplayGearMenuPlugin, SciJavaPlugin {
   public static final String MENUNAME = "Sharpest...";
   public static final String SHARPNESS = "SharpnessMethod";
   public static final String SAVE = "Save";
   public static final String SHOW_SHARPNESS_GRAPH = "ShowSharpnessGraph";
   public static final String KEEP_PLANES = "KeepPlanes";
   public static final String EACH_CHANNEL = "EachChannel";
   public static final String CHANNEL = "Channel";

   private Studio studio_;

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      // no need to hold on to the instance, we just want to create the frame
      SharpestPluginFrame ourFrame = new SharpestPluginFrame(studio_, display);
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
      return "Selects the Sharpest image of a Micro-Manager datasets along the Z axis";
   }

   @Override
   public String getVersion() {
      return "Version 0.1";
   }

   @Override
   public String getCopyright() {
      return "Altos Labs, 2024, based on code copyright UCSF, 2017-2019";
   }


}