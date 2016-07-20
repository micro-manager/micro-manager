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
import org.micromanager.display.DisplayWindow;


import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class CropperPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String MENUNAME = "Crop";
   private Studio studio_;

   @Override
   public String getSubMenu() {
      return "Image";
   }

   @Override
   public void onPluginSelected() {
      DisplayWindow ourWindow = studio_.displays().getCurrentWindow();
      if (ourWindow == null) {
         studio_.logs().showMessage("No Micro-Manager viewer found");
         return;
      }
      // no need to hold on to the instance, we just want to create the frame
      CropperPluginFrame ourFrame = new CropperPluginFrame(studio_, ourWindow);
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

}
