package org.micromanager.image5d;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.PlugIn;

public class Image5D_Channels_to_Stacks implements PlugIn {

    public void run(String arg) {
        ImagePlus theImage = WindowManager.getCurrentImage();
        if (theImage==null) {
            IJ.noImage();
            return;
        }
        if (!(theImage instanceof Image5D)) {
            IJ.error("Image is not an Image5D.");
            return;
        }
        
        Image5D currentImage = (Image5D) theImage;
        
        ImageStack currentImageStack = currentImage.getImageStack();
        
        // Copy references to pixel arrays to new image. Don't just copy the reference to the stack,
        // because the stack is disassembled when the currentImage is flushed.

        for (int channel = 1; channel <= currentImage.getNChannels(); channel++) 
        {
           ImagePlus newImage = new ImagePlus();
           newImage.copyScale(currentImage);
           newImage.setTitle(currentImage.getTitle() + "_" + currentImage.getChannelCalibration(channel).getLabel() );
           ImageStack newStack = new ImageStack (currentImage.getWidth(), currentImage.getHeight());
           for (int slice=1; slice<=currentImage.getNSlices(); slice++) {
              for (int frame = 1; frame <= currentImage.getNFrames(); frame++)
               newStack.addSlice(currentImageStack.getSliceLabel(channel), currentImageStack.getPixels(currentImage.getImageStackIndex(channel,slice,frame)));
           }
           newImage.setStack(null, newStack);
           
           newImage.setDimensions(1, currentImage.getNSlices(), currentImage.getNFrames());
           newImage.setCalibration(currentImage.getCalibration().copy());
           
           newImage.getProcessor().resetMinAndMax();
           newImage.show(); 
        
        // currentImage.getWindow().close();

           if(newImage.getWindow() != null)
              WindowManager.setCurrentWindow(newImage.getWindow());
        }
    }

}
