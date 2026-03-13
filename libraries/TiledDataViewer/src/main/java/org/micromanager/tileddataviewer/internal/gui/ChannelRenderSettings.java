package org.micromanager.tileddataviewer.internal.gui;

import java.awt.Color;

/**
 * Rendering parameters for one channel, derived from MM DisplaySettings.
 */
public final class ChannelRenderSettings {
   public final int contrastMin;
   public final int contrastMax;
   public final double gamma;
   public final Color color;
   public final boolean active;

   public ChannelRenderSettings(int min, int max, double gamma, Color color, boolean active) {
      this.contrastMin = min;
      this.contrastMax = max;
      this.gamma = gamma;
      this.color = color;
      this.active = active;
   }
}
