package org.micromanager.internal.utils;

import ij.gui.ImageWindow;

/**
 * To use register your implementation of ImageFocusListener,
 * call GUIUtils.registerImageFocusListener(ImageFocusListener l).
 */
public interface ImageFocusListener {
   void focusReceived(ImageWindow focusedWindow);
}
