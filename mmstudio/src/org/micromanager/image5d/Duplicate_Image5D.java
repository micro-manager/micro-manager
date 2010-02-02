package org.micromanager.image5d;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

import org.micromanager.metadata.MMAcqDataException;

/*
 * Duplicates a Micro-Manager Image5D
 */

public class Duplicate_Image5D implements PlugIn {

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
        Image5DWindow oldWindow = (Image5DWindow) currentImage.getWindow();
        
        Image5D newi5d = i5d.duplicate();
        newi5d.show();
        newi5d.setDisplayMode(i5d.getDisplayMode());

        // Copy the Micro-Manager metadata
        Image5DWindow newWindow = (Image5DWindow) newi5d.getWindow();            
        try {
         newWindow.setAcquisitionData(oldWindow.getAcquisitionData().createCopy());
      } catch (MMAcqDataException e) {
         IJ.error(e.getMessage());
      }

    }

}
