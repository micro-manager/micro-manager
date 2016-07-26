///////////////////////////////////////////////////////////////////////////////
//FILE:          CropperPlugin.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Cropper plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2016
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

package org.micromanager.cropper;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;


import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class CropperPlugin implements DisplayGearMenuPlugin, MenuPlugin, SciJavaPlugin {
   public static final String MENUNAME = "Duplicate...";
   private Studio studio_;

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      // no need to hold on to the instance, we just want to create the frame
      CropperPluginFrame ourFrame = new CropperPluginFrame(studio_, display);
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
      return "Generates a cropped copy of a Micro-Manager datasets";
   }

   @Override
   public String getVersion() {
      return "Version 0.1-alpha";
   }

   @Override
   public String getCopyright() {
      return "Regents of the University of California, 2016";
   }

   @Override
   public void onPluginSelected() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

}
