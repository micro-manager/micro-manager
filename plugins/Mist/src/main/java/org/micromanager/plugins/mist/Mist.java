/**
 * A very simple Micro-Manager plugin, intended to be used as an example for
 * developers wishing to create their own, actually useful plugins. This one
 * demonstrates performing various common tasks, but does not do anything
 * really useful.
 *
 * <p>Copy this code to a location of your choice, change the name of the project
 * (and the classes), build the jar file and copy it to the mmplugins folder
 * in your Micro-Manager directory.
 *
 * <p>Once you have it loaded and running, you can attach the NetBean debugger
 * and use all of NetBean's functionality to debug your code.  If you make a
 * generally useful plugin, please do not hesitate to send a copy to
 * info@micro-manager.org for inclusion in the Micro-Manager source code
 * repository.
 *
 * <p>LICENSE:      This file is distributed under the BSD license.
 * License text is included with the source distribution.
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 *
 * @author Nico Stuurman, 2012
 * @copyright University of California
 */


package org.micromanager.plugins.mist;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class Mist implements SciJavaPlugin, DisplayGearMenuPlugin {
   private Studio studio_;
   private MistFrame frame_;

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
    * This string is the sub-menu that the plugin will be displayed in, in the
    * Plugins menu.
    */
   @Override
   public String getSubMenu() {
      return "Analysis";
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      if (frame_ != null) {
         frame_.dispose();
      }
      frame_ = new MistFrame(studio_, display);
      frame_.setVisible(true);
   }

   /**
    * The name of the plugin in the Plugins menu.
    */
   @Override
   public String getName() {
      return "Stitching using the Mist Fiji plugin";
   }

   @Override
   public String getHelpText() {
      return "Will need to write this once I figure it out.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Altos Labs, 2024";
   }
}
