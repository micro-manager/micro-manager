package org.micromanager.image5d;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;

/*
 * Duplicates the ROI of Micro-Manager Image5D and deletes the original
 */

public class Crop_Image5D implements PlugIn {

   public void run(String arg) {
      ImagePlus currentImage = WindowManager.getCurrentImage();
      if (currentImage==null) {
         IJ.noImage();
         return;
      }
      if (currentImage.getRoi() == null) {
         IJ.error("Crop: Selection required");
         return;
      }
      if (!(currentImage instanceof Image5D)) {
         IJ.error("Image is not an Image5D.");
         return;
      }

      Image5D i5d = (Image5D) currentImage;
      Image5DWindow oldWindow = (Image5DWindow) currentImage.getWindow();

      Image5D newi5d = i5d.crop();
      newi5d.show();
      newi5d.setDisplayMode(i5d.getDisplayMode());

      // Copy the Micro-Manager metadata
      Image5DWindow newWindow = (Image5DWindow) newi5d.getWindow();
      try {
         // update the image physical parameters
         AcquisitionData ad = oldWindow.getAcquisitionData().createCopy();
         newWindow.setAcquisitionData(ad);
         int w = newi5d.getWidth();
         int h = newi5d.getHeight();
         ad.setImagePhysicalDimensions(w, h, ad.getPixelDepth());
      } catch (MMAcqDataException e) {
         IJ.error(e.getMessage());
      }
    }

}
