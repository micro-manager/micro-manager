package org.micromanager.image5d;


import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.ImageConverter;
/** Converts the current timeframe of an Image5D to an RGB stack using the current 
 * view settings.
 * @author Joachim Walter / Nico Stuurman
 */
// creates an RGB Stack from an Image5D Stack
// show the time series at the current z slice
public class Image5D_Stack_to_RGB_t implements PlugIn {

    public void run(String arg) {
        ImagePlus thisImage = WindowManager.getCurrentImage();
        if (thisImage==null) {
            IJ.noImage();
            return;
        }
        if (!(thisImage instanceof Image5D)) {
            IJ.error("Image is not an Image5D.");
            return;
        }
        
        Image5D currentImage = (Image5D) thisImage;
        String title = currentImage.getTitle();
        int width = currentImage.getWidth();
        int height = currentImage.getHeight();
        int depth = currentImage.getNSlices();
        int frames = currentImage.getNFrames();
        int currentSlice = currentImage.getCurrentSlice();
        int currentFrame = currentImage.getCurrentFrame();
        
         currentImage.killRoi();
        
        ImagePlus rgbImage = IJ.createImage(title+"-RGB", "RGB black", width, height, 1);
        ImageStack rgbStack = rgbImage.getStack();
        
        for (int i=1; i<=frames; i++) {
            // The -1 is needed to copy the right frame.  No idea why, but it works
            currentImage.setCurrentPosition(4,i-1);
            currentImage.updateImage();
            currentImage.copy(false);
            
            ImagePlus rgbClip = ImagePlus.getClipboard();
            if (rgbClip.getType()!=ImagePlus.COLOR_RGB)
                new ImageConverter(rgbClip).convertToRGB();
            if (i>1) {
                rgbStack.addSlice(currentImage.getStack().getSliceLabel(1), 
                        rgbClip.getProcessor().getPixels());
            } else {
                rgbStack.setPixels(rgbClip.getProcessor().getPixels(), 1);
                rgbStack.setSliceLabel(currentImage.getStack().getSliceLabel(1), 1);
            }
        }

        // The -1 is needed to go to the right frame.  No idea why, but it works
        currentImage.setCurrentPosition(4,currentFrame-1);
        rgbImage.setStack(null, rgbStack);
        rgbImage.setSlice(currentFrame);
        
        rgbImage.killRoi();
        rgbImage.show();
    }

}
