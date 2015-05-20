package org.micromanager.patternoverlay;

import java.awt.Color;
import java.awt.event.WindowEvent;

import org.micromanager.Studio;
import org.micromanager.MMPlugin;

/**
 * Provides various overlays on the image display window. Adapted from the
 * original PatternOverlayPlugin written by Mathijs and Jon.
 * @author Chris
 */
public class PatternOverlayPlugin implements MMPlugin {

   public static String menuName = "Pattern Overlay";
   public final static String tooltipDescription = "Overlay pattern on viewer window";
   public final static Color borderColor = Color.gray;

   private Studio studio_;


   @Override
   public void setApp(Studio studio) {
      studio_ = studio;
      studio_.displays().registerOverlay(new PatternOverlayFactory(studio));
   }

   /**
    * The main app calls this method to remove the module window
    */
   @Override
   public void dispose() {
      studio_.logs().logError("TODO: allow unregistration of overlays");
   }

   @Override
   public void show() {
   }

   /**
    * General purpose information members.
    * @return
    */
   @Override public String getDescription() {
      return "Add an overlay shape to the image window.";
   }
   @Override public String getInfo() {
      return menuName;
   }
   @Override public String getVersion() {
      return "3";
   }
   @Override public String getCopyright() {
      return "Applied Scientific Instrumentation, 2014";
   }
}
