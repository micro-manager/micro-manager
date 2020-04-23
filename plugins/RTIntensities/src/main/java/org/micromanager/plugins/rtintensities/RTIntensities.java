/**
 * A very simple Micro-Manager plugin, intended to be used as an example for
 * developers wishing to create their own, actually useful plugins. This one
 * demonstrates performing various common tasks, but does not do anything
 * really useful.
 *
 * Copy this code to a location of your choice, change the name of the project
 * (and the classes), build the jar file and copy it to the mmplugins folder
 * in your Micro-Manager directory.
 *
 * Once you have it loaded and running, you can attach the NetBean debugger
 * and use all of NetBean's functionality to debug your code.  If you make a
 * generally useful plugin, please do not hesitate to send a copy to
 * info@micro-managaer.org for inclusion in the Micro-Manager source code
 * repository.
 *
 * Nico Stuurman, 2012
 * copyright University of California
 *
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */


package org.micromanager.plugins.rtintensities;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class RTIntensities implements SciJavaPlugin, MenuPlugin {
   private Studio studio_;
   private RTIntensitiesFrame frame_;

   /**
    * This method receives the Studio object, which is the gateway to the
    * Micro-Manager API. You should retain a reference to this object for the
    * lifetime of your plugin. This method should not do anything except for
    * store that reference, as Micro-Manager is still busy starting up at the
    * time that this is called.
    */
   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   /**
    * This method is called when your plugin is selected from the Plugins menu.
    * Typically at this time you should show a GUI (graphical user interface)
    * for your plugin.
    */
   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         // We have never before shown our GUI, so now we need to create it.
         frame_ = new RTIntensitiesFrame(studio_);
      }
      frame_.setVisible(true);
   }

   /**
    * This string is the sub-menu that the plugin will be displayed in, in the
    * Plugins menu.
    */
   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   /**
    * The name of the plugin in the Plugins menu.
    */
   @Override
   public String getName() {
      return "Realtime intensities plugin";
   }

   @Override
   public String getHelpText() {
      return "Display ROI intensity plot during acquisition.";
   }

   @Override
   public String getVersion() {
      return "2.0";
   }

   @Override
   public String getCopyright() {
      return "University of California et. al., 2012-2020";
   }
}
