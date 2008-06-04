package org.micromanager.image5d;


import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.process.ImageConverter;

/** Converts the current timeframe of an Image5D to an RGB stack using the current 
 * view settings, and sends this to the VolumeViewer plugin.
 * @author Nico Stuurman, based on code by Joachim Walter
 */

public class Image5D_to_VolumeViewer implements PlugIn {

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
        int currentSlice = currentImage.getCurrentSlice();
        
        currentImage.killRoi();
        
        ImagePlus rgbImage = IJ.createImage(title+"-RGB", "RGB black", width, height, 1);
        ImageStack rgbStack = rgbImage.getStack();
        
        for (int i=1; i<=depth; i++) {
            currentImage.setSlice(i);
            currentImage.updateImage();
            currentImage.copy(false);
            
            ImagePlus rgbClip = ImagePlus.getClipboard();
            if (rgbClip.getType()!=ImagePlus.COLOR_RGB)
                new ImageConverter(rgbClip).convertToRGB();
            if (i>1) {
                rgbStack.addSlice(currentImage.getStack().getSliceLabel(i), 
                        rgbClip.getProcessor().getPixels());
            } else {
                rgbStack.setPixels(rgbClip.getProcessor().getPixels(), 1);
                rgbStack.setSliceLabel(currentImage.getStack().getSliceLabel(1), 1);
            }
        }

        currentImage.setSlice(currentSlice);
        rgbImage.setStack(null, rgbStack);
        rgbImage.setSlice(currentSlice);
        rgbImage.copyScale(currentImage);
        
        rgbImage.killRoi();
        // if we do not show the image, volume viewer only displays the red channel???
        rgbImage.show();

        if (!(currentImage instanceof Image5D)) {
            IJ.error("Image ceased to be a Image5D.");
            return;
        }

        // We now have the stack, move it into VolumeViewer
        // Npte that the arguments do not seem to make it into 
        IJ.runPlugIn("Volume_Viewer", "display_mode=5 lut=0 z-aspect=10 dist=-300 thresh=10 axes=0 markers=0 scale=1"); 

        rgbImage.hide();

    }

}
