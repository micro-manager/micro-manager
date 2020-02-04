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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import org.micromanager.magellan.internal.gui.GUI;
import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.magellan.api.MagellanAPI;
import org.micromanager.internal.zmq.ZMQServer;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class Magellan implements MenuPlugin, SciJavaPlugin {

   public static final String VERSION = "2.2.0";

   public static final String menuName = "Micro-Magellan";
   public static final String tooltipDescription = "High throughout, automated micrscopy for slidescanning or volumetric imaging";

   private static Studio mmAPI_;
   private static GUI gui_;
   private static ZMQServer bridge_;
   private static MagellanAPI api_;

   public Magellan() {

   }
   
   public static MagellanAPI getAPI() {
      return api_;
   }

   public static Studio getStudio() {
      return mmAPI_;
   }

//   public static String getConfigFileName() {
//      try {
//         return mmAPI_.getInstance().getSysConfigFile();
//      } catch (Exception e) {
//         //since this is not an API method
//         return "";
//      }    
//   }
   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected() {
      if (gui_ == null) {
         gui_ = new GUI(VERSION);
      } else {
         gui_.setVisible(true);
      }
      if (api_ == null) {
         api_ = new MagellanAPI();
      }
   }

   @Override
   public void setContext(Studio studio) {
      mmAPI_ = studio;
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
      return mmAPI_.core();
   }
}
