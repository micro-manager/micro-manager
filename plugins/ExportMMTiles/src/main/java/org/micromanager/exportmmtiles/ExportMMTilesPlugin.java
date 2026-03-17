package org.micromanager.exportmmtiles;

import org.micromanager.Studio;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Gear-menu plugin that assembles a tiled MM dataset (created via TileCreatorDlg)
 * into a single image, with optional alignment and blending, and saves the result
 * as an MM Datastore.
 *
 * <p>Stage positions are read from per-image metadata (XPositionUm / YPositionUm)
 * and converted to row/col grid indices based on image size and pixel size, so that
 * ExportTiles can assemble and optionally align/blend the tiles.</p>
 */
@Plugin(type = DisplayGearMenuPlugin.class)
public class ExportMMTilesPlugin implements DisplayGearMenuPlugin, SciJavaPlugin {

   public static final String MENU_NAME = "Stitch...";

   private Studio studio_;

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      new ExportMMTilesFrame(studio_, display);
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return MENU_NAME;
   }

   @Override
   public String getHelpText() {
      return "Assembles a tiled Micro-Manager dataset into a single image using ExportTiles.";
   }

   @Override
   public String getVersion() {
      return "0.1";
   }

   @Override
   public String getCopyright() {
      return "Regents of the University of California, 2024";
   }
}
