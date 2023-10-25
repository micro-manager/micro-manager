///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//

package org.micromanager.magellan.internal.main;

import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.magellan.internal.gui.GUI;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class Magellan implements MenuPlugin, SciJavaPlugin {

   public static final String VERSION = "2.2.1";

   public static final String menuName = "Micro-Magellan";
   public static final String tooltipDescription = "High throughout, automated microscopy for "
         + "slidescanning or volumetric imaging";

   private static Studio studio_;
   private static GUI gui_;

   public Magellan() {
   }

   public static Studio getStudio() {
      return studio_;
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected() {
      if (gui_ == null) {
         gui_ = new GUI(VERSION, studio_.profile());
      } else {
         gui_.setVisible(true);
      }
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Micro-Magellan";
   }

   @Override
   public String getHelpText() {
      return "";
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Copyright Henry Pinkard 2014-2016";
   }

   public static CMMCore getCore() {
      return studio_.core();
   }
}
