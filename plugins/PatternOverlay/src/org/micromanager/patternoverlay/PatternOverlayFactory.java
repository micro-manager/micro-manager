package org.micromanager.patternoverlay;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.OverlayPanel;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.Studio;

/**
 * Generates new panels that can be used to configure overlays on different
 * displays.
 */
public class PatternOverlayFactory implements OverlayPanelFactory {
   private Studio studio_;

   public PatternOverlayFactory(Studio studio) {
      studio_ = studio;
   }

   @Override
   public OverlayPanel createOverlayPanel(DisplayWindow display) {
      return new PatternOverlayPanel(studio_, display);
   }
}
