/**
 * Navigation Plugin for Micro-Manager
 *
 * <p>Allows microscope operators to navigate using a low-resolution reference image.
 * The operator establishes correspondence between the reference image and the
 * microscope stage by defining at least 3 matching points, then can click anywhere
 * on the reference image to move the stage to that location.
 *
 * <p>LICENSE:This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               <p>This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 *
 * @author Nico Stuurman
 * @copyright Regents of the University of California, 2026
 */

package org.micromanager.navigationbymap;

import com.google.common.eventbus.Subscribe;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class NavigationByMap implements SciJavaPlugin, MenuPlugin {
   private static final String NAVIGATION_FRAME_OPEN = "NavigationFrameOpen";

   private Studio studio_;
   private NavigationFrame frame_;

   /**
    * This method receives the Studio object, which is the gateway to the
    * Micro-Manager API. Store a reference to this object for the
    * lifetime of the plugin.
    */
   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
      studio_.events().registerForEvents(this);
   }

   /**
    * This method is called when the plugin is selected from the Plugins menu.
    * Show the GUI for the plugin.
    */
   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         // Create the GUI the first time it's needed
         frame_ = new NavigationFrame(studio_);
      }
      frame_.setVisible(true);
   }

   /**
    * The sub-menu that the plugin will be displayed in, in the Plugins menu.
    */
   @Override
   public String getSubMenu() {
      return "Navigation Tools";
   }

   /**
    * The name of the plugin in the Plugins menu.
    */
   @Override
   public String getName() {
      return "Navigation Plugin";
   }

   @Override
   public String getHelpText() {
      return "Navigate the microscope stage using a reference image. "
               + "Load a low-resolution image of your sample, define at least 3 "
               + "correspondence points, then click anywhere on the image to move "
               + "the stage to that location.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "2026";
   }

   /**
    * Save the window visibility state when Micro-Manager is shutting down.
    */
   @Subscribe
   public void closeRequested(ShutdownCommencingEvent sce) {
      if (!sce.isCanceled() && frame_ != null) {
         studio_.profile().getSettings(this.getClass()).putBoolean(
               NAVIGATION_FRAME_OPEN, frame_.isVisible());
         frame_.dispose();
      }
   }

   /**
    * Restore the window if it was open when Micro-Manager was last shut down.
    */
   @Subscribe
   public void onStartupComplete(StartupCompleteEvent event) {
      if (studio_.profile().getSettings(this.getClass()).getBoolean(NAVIGATION_FRAME_OPEN, false)) {
         // If the dialog was open when MM was shut down, restore it now
         // Only restore if there's an XY stage configured
         try {
            if (!studio_.core().getXYStageDevice().isEmpty()) {
               if (frame_ == null) {
                  frame_ = new NavigationFrame(studio_);
               }
               frame_.setVisible(true);
            }
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Error restoring Navigation Plugin window");
         }
      }
   }
}
