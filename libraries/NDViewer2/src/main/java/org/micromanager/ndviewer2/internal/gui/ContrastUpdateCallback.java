package org.micromanager.ndviewer2.internal.gui;

/**
 * Callback for autostretch write-back from ImageMaker to MM DisplaySettings.
 */
public interface ContrastUpdateCallback {
   /**
    * Called by ImageMaker after autostretch computes new contrast bounds.
    *
    * @param channelName the NDViewer channel name
    * @param newMin      the computed contrast minimum
    * @param newMax      the computed contrast maximum
    */
   void onContrastUpdated(String channelName, int newMin, int newMax);
}
