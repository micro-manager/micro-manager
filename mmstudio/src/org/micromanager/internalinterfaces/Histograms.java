///////////////////////////////////////////////////////////////////////////////
//FILE:          Histograms.java
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
package org.micromanager.internalinterfaces;

import org.micromanager.utils.ContrastSettings;

/**
 * Interface for histograms, which control Image contrast and can optionally
 * draw themselves
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
   public abstract void applyLUTToImage();

   /**
    * Called just before the image is about to draw itself.  Calls 
    * calcAndDisplayHistAndStats and applyLutToImage to make sure that the image has
    * the correct LUT and that the histogram is in sync with the image
    * @param img the Image
    * @param cache the ImageCahce corresponding to the image
    * @param drawHist flag that gets passed to calcAndDisplayHistAndStats to determine
    * if the histogram and image statistics should be updated
    */
   public abstract void imageChanged();

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
   public abstract void calcAndDisplayHistAndStats(boolean drawHist);
   
   /*
    * Sets the contrast min and max to the stored pixel min and max values
    * (or to the appropriate values if reject outliers is selected).  Does
    * not calculate histogram or redraw image
    */
   public abstract void autostretch();
      
   /*
    * Returns the contrastSettings associated with the channel of the current image
    * @param channel - index of the channel to get settigns from
    */
   public  abstract ContrastSettings getChannelContrastSettings(int channel);

   /*
    * sets the histogram display range fropdown box to the option corresponding to the
    * value histMax or to the "Camera Depth" automatic option if histMax is equal to -1
    */
   public abstract void setChannelHistogramDisplayMax(int channelIndex, int histMax);
   
   /*
    * Called when a change in the reject outliers checkbox of reject percent spinner occurs
    * to redraw the histogram and image appropriately
    */
   public abstract void rejectOutliersChangeAction();    

   /**
    * Equivalent to pressing the Auto button for each channel
    */
   public abstract void autoscaleAllChannels() ;


}
