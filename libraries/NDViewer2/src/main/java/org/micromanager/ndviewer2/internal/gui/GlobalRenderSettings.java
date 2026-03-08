package org.micromanager.ndviewer2.internal.gui;

/**
 * Global rendering parameters derived from MM DisplaySettings.
 */
public final class GlobalRenderSettings {
   public final boolean autostretch;
   public final boolean ignoreOutliers;
   public final double percentToIgnore;
   public final boolean composite;
   public final boolean logHistogram;

   public GlobalRenderSettings(boolean autostretch, boolean ignoreOutliers,
                                double percentToIgnore, boolean composite, boolean logHistogram) {
      this.autostretch = autostretch;
      this.ignoreOutliers = ignoreOutliers;
      this.percentToIgnore = percentToIgnore;
      this.composite = composite;
      this.logHistogram = logHistogram;
   }
}
