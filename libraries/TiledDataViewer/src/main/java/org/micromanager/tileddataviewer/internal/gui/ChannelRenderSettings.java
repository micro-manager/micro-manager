package org.micromanager.tileddataviewer.internal.gui;

import java.awt.Color;

/**
 * Rendering parameters for one channel, derived from MM DisplaySettings.
 *
 * <p>For RGB images, {@code componentMin} and {@code componentMax} hold per-component
 * (R=0, G=1, B=2) scaling ranges so that white-balance adjustments stored in
 * MM's per-component {@code ComponentDisplaySettings} are honoured by ImageMaker.
 * Both arrays are {@code null} for grayscale channels.</p>
 */
public final class ChannelRenderSettings {
   public final int contrastMin;
   public final int contrastMax;
   public final double gamma;
   public final Color color;
   public final boolean active;
   /** Per-component contrast minima for RGB (length 3: R, G, B), or null for grayscale. */
   public final int[] componentMin;
   /** Per-component contrast maxima for RGB (length 3: R, G, B), or null for grayscale. */
   public final int[] componentMax;

   public ChannelRenderSettings(int min, int max, double gamma, Color color, boolean active) {
      this.contrastMin = min;
      this.contrastMax = max;
      this.gamma = gamma;
      this.color = color;
      this.active = active;
      this.componentMin = null;
      this.componentMax = null;
   }

   public ChannelRenderSettings(int min, int max, double gamma, Color color, boolean active,
                                int[] componentMin, int[] componentMax) {
      this.contrastMin = min;
      this.contrastMax = max;
      this.gamma = gamma;
      this.color = color;
      this.active = active;
      this.componentMin = componentMin;
      this.componentMax = componentMax;
   }
}
