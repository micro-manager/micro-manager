package org.micromanager.patternoverlay;

import org.micromanager.display.OverlayPanel;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.Studio;

/**
 * Generates new panels that can be used to configure overlays on different
 * displays.
 */
public class PatternOverlayFactory implements OverlayPanelFactory {
   private final Studio studio_;

   public PatternOverlayFactory(Studio studio) {
      studio_ = studio;
   }

   @Override
   public OverlayPanel createOverlayPanel() {
      return new PatternOverlayPanel(studio_);
   }
}
