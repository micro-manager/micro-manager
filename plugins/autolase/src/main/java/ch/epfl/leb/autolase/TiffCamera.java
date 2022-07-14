/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.epfl.leb.autolase;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import java.io.IOException;

/**
 *
 * @author pengo
 */
public class TiffCamera implements Camera {
    
    ImageStack stack;
    ImagePlus win;
    
    public TiffCamera(java.io.File path) throws IOException {
        win = new Opener().openTiff(path.getParent(), path.getName());
        stack = win.getImageStack();
        
        win.show();
    }

    @Override
    public short[] getNewImage() throws Exception {
        if (win.getSlice()<stack.getSize())
            win.setSlice(win.getSlice()+1);
        else
            win.setSlice(1);
        
        return (short[]) stack.getPixels(win.getSlice());
    }

    @Override
    public int getWidth() {
        return stack.getWidth();
    }

    @Override
    public int getHeight() {
        return stack.getHeight();
    }

    @Override
    public int getBytesPerPixel() {
        if(stack.getPixels(win.getSlice()) instanceof short[])
            return 2;
        if(stack.getPixels(win.getSlice()) instanceof char[])
            return 1;
        else
            return 4;
    }

    @Override
    public boolean isAcquiring() {
        return true;
    }
    
}
