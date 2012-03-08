///////////////////////////////////////////////////////////////////////////////
//FILE:          ContrastPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com Dec 1, 2011
//
// COPYRIGHT:    University of California, San Francisco, 2011
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.api;

import ij.ImagePlus;
import javax.swing.JPanel;
import org.micromanager.utils.ContrastSettings;

/**
 * Interface for contrast panels, which display and control Image contrast
 * @author Henry Pinkard
 */
public interface Histograms  {

    /**
     * Creates and applies a look up table to the image based on the previously
     * set values of contrast min, max and gamma.  Also stores min max and gamma
     * in the image cache and redraws the histogram contrast cursors
     * @param img the image
     * @param cache the ImageCahce corresponding to the image
     */
   public abstract void applyLUTToImage(ImagePlus img, ImageCache cache);

   /**
    * Called when the window corresponding to the contrast panel changes.  Initializes
    * the relevant fields of this contrast panel to the new image.  May load contrast 
    * settings from image cache
    * @param img the image
    * @param cache the ImageCahce corresponding to the image
    */
   public abstract void displayChanged(ImagePlus img, ImageCache cache);

   /**
    * Called just before the image is about to draw itself.  Calls 
    * calcAndDisplayHistAndStats and applyLutToImage to make sure that the image has
    * the correct LUT and that the histogram is in sync with the image
    * @param img the Image
    * @param cache the ImageCahce corresponding to the image
    * @param drawHist flag that gets passed to calcAndDisplayHistAndStats to determine
    * if the histogram and image statistics should be updated
    */
   public abstract void imageChanged(ImagePlus img, ImageCache cache, boolean drawHist, boolean slowHistUpdate);

   /**
    * Manually set the contrast min, max and gamma values for this channel (channel = 0)
    * for single channel contrast panel).  Does not redraw image or histogram
    * @param channelIndex Index of the channel to set the contrast of
    * @param min Contrast min
    * @param max Contrast max
    * @param gamma Contrast gamma
    */
   public abstract void setChannelContrast(int channelIndex, int min, int max, double gamma);
   
   /**
    * Calculates the image histogram and optionally displays image statistics
    * (pixel min, max, mean, etc) and draws the histogram)
    * @param img The image for which to calculate statistics/histogram
    * @param drawHist flag for optional display of statistics and drawing of histogram
    */
   public abstract void calcAndDisplayHistAndStats(ImagePlus img, boolean drawHist);
   
   /*
    * Sets the contrast min and max to the stored pixel min and max values
    * (or to the appropriate values if reject outliers is selected).  Does
    * not calculate histogram or redraw image
    */
   public abstract void autostretch();
      
   /*
    * Returns the contrastSettings associated with the channel of the current image
    */
   public  abstract ContrastSettings getChannelContrastSettings(int channel);

   public abstract void setChannelHistogramDisplayMax(int channelIndex, int histMax);
   
   public abstract void setLogScale();
   
      public abstract void rejectOutliersChangeAction();
      

   public abstract void autoscaleAllChannels() ;


}
