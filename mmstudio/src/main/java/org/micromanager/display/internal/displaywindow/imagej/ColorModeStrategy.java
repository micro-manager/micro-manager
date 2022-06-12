/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.displaywindow.imagej;

import ij.ImagePlus;
import java.awt.Color;

/**
 * Pluggable implementations for applying scaling or LUT to ImagePlus.
 *
 * @author mark
 */
interface ColorModeStrategy {
   void applyModeToImagePlus(ImagePlus imagePlus);

   void applyHiLoHighlight(boolean enable);

   void applyColor(int index, Color color);

   void applyScaling(int index, int min, int max);

   void applyGamma(int index, double gamma);

   void applyVisibleInComposite(int index, boolean visible);

   void displayedImageDidChange();

   void releaseImagePlus();
}