package org.micromanager.orthogonalviewer;

import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Gear-menu plugin that opens an orthogonal (XY/XZ/YZ) slice viewer.
 */
@Plugin(type = DisplayGearMenuPlugin.class)
public class OrthogonalViewerPlugin implements DisplayGearMenuPlugin, SciJavaPlugin {

   public static final String MENUNAME = "Orthogonal Views";

   private Studio studio_;

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      new OrthogonalViewerFrame(studio_, display);
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
      return "Shows orthogonal XY, XZ and YZ slices of a Z-stack dataset";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Regents of the University of California, 2025";
   }
}
