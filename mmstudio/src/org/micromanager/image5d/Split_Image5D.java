package org.micromanager.image5d;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

import java.awt.Color;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;

/*
 * Splits a single channel Image5D into a Image5D with two channels, one for the left, the other for the right side of the original
 */

public class Split_Image5D implements PlugIn {

    public void run(String arg) {
        ImagePlus currentImage = WindowManager.getCurrentImage();
        if (currentImage==null) {
            IJ.noImage();
            return;
        }
        if (!(currentImage instanceof Image5D)) {
            IJ.error("Image is not an Image5D.");
            return;
        }
        Image5D i5d = (Image5D) currentImage;
        if (i5d.getNChannels() != 1) {
           IJ.error("This plugin only works on an Image5D with a single channel");
           return;
        }
        Image5DWindow oldWindow = (Image5DWindow) currentImage.getWindow();
        
        Image5D newi5d = i5d.split();
        newi5d.setChannelColorModel(1, ChannelDisplayProperties.createModelFromColor(Color.red));
        newi5d.setChannelColorModel(2, ChannelDisplayProperties.createModelFromColor(Color.green));
        newi5d.show();
        newi5d.setDisplayMode(ChannelControl.OVERLAY);

        // Copy the Micro-Manager metadata
        Image5DWindow newWindow = (Image5DWindow) newi5d.getWindow();
        AcquisitionData ad;
      try {
         ad = oldWindow.getAcquisitionData().createCopy();
         int w = newi5d.getWidth();
         int h = newi5d.getHeight();
         ad.setImagePhysicalDimensions(w, h, ad.getPixelDepth());
         ad.setDimensions(ad.getNumberOfFrames(), 2, ad.getNumberOfSlices());
         newWindow.setAcquisitionData(ad);
      } catch (MMAcqDataException e) {
         IJ.error(e.getMessage());
      }
    }

}
