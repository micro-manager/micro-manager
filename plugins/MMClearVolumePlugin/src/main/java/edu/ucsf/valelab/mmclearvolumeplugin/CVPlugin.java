package edu.ucsf.valelab.mmclearvolumeplugin;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Bindings to ClearVolume 3D viewer: View Micro-Manager datasets in 3D.
 *
 * <p>AUTHOR: Nico Stuurman COPYRIGHT: Regents of the University of California,
 * 2015
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

 * More or less boiler plate code to become a Micro-Manager 2.0 plugin
 * Most of the action happens in the CVViewer class
 *
 * @author nico
 */

@Plugin(type = DisplayGearMenuPlugin.class)
public class CVPlugin implements MenuPlugin, DisplayGearMenuPlugin, SciJavaPlugin {
   private Studio studio_;
   public static final String VERSION_INFO = "1.5.2";
   private static final String COPYRIGHT_NOTICE = "Copyright by UCSF, 2015-2018";
   private static final String DESCRIPTION = "View Micro-Manager data in the ClearVolume viewer";
   private static final String NAME = "3D (ClearVolume)";

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   @Override
   public String getHelpText() {
      return DESCRIPTION;
   }

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public String getVersion() {
      return VERSION_INFO;
   }

   @Override
   public void onPluginSelected() {
      try {
         CVViewer viewer = new CVViewer(studio_);
         viewer.register();
      } catch (Exception ex) {
         if (studio_ != null) {
            studio_.logs().logError(ex);
         }
      }
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      try {
         CVViewer viewer = new CVViewer(studio_, display.getDataProvider());
         viewer.register();
      } catch (Exception ex) {
         if (studio_ != null) {
            studio_.logs().logError(ex);
         }
      }
   }

}