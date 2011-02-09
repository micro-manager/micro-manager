/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import ij.gui.ImageWindow;

/**
 * To use register your implementation of ImageFocusListener,
 * call GUIUtils.registerImageFocusListener(ImageFocusListener l);
 */
public interface ImageFocusListener {
   public void focusReceived(ImageWindow focusedWindow);
}
