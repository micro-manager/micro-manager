package org.micromanager.patternoverlay;

import java.awt.Color;
import java.awt.event.WindowEvent;

import org.micromanager.Studio;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.display.OverlayPlugin;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Provides various overlays on the image display window. Adapted from the
 * original PatternOverlayPlugin written by Mathijs and Jon.
 * @author Chris
 */
@Plugin(type = OverlayPlugin.class)
public class PatternOverlayPlugin implements OverlayPlugin, SciJavaPlugin {

   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public OverlayPanelFactory createFactory() {
      return new PatternOverlayFactory(studio_);
   }

   @Override
   public String getName() {
      return "Pattern Overlay";
   }
   @Override
   public String getHelpText() {
      return "Draw various overlays on the image window.";
   }
   @Override
   public String getVersion() {
      return "3";
   }
   @Override
   public String getCopyright() {
      return "Applied Scientific Instrumentation, 2014";
   }
}
