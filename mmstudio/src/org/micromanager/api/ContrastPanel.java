/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.api;

import ij.ImagePlus;

/**
 *
 * @author Henry
 */
public interface ContrastPanel {

   public void applyLUTToImage(ImagePlus img, ImageCache cache);

   public void displayChanged(ImagePlus img, ImageCache cache);

   public boolean getAutoStretch();

   public boolean getSlowHist();

   public boolean getLogHist();

   public boolean getRejectOutliers();

   public double getFractionToReject();

   public void imageChanged(ImagePlus img, ImageCache cache, boolean drawHist);

   public void setChannelContrast(int channelIndex, int min, int max, double gamma);
   
   public void setupChannelControls(ImageCache cache);
   
   public void calcAndDisplayHistAndStats(ImagePlus img, boolean drawHist);
   
   public void autostretch();
   

}
