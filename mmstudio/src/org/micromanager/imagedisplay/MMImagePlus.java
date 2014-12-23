package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;

import ij.ImagePlus;
import ij.ImageStack;

public class MMImagePlus extends ImagePlus implements IMMImagePlus {

   public MMImagePlus() {
      super();
   }

   public MMImagePlus(String title, ImageStack stack) {
      super(title, stack);
   }

   @Override
   public int getStackSize() {
      return getImageStackSize();
   }

   @Override
   public int getNChannelsUnverified() {
      return super.nChannels;
   }

   @Override
   public int getNSlicesUnverified() {
      return super.nSlices;
   }

   @Override
   public int getNFramesUnverified() {
      return super.nFrames;
   }

   @Override
   public void setNChannelsUnverified(int nChannels) {
      super.nChannels = nChannels;
   }

   @Override
   public void setNSlicesUnverified(int nSlices) {
      super.nSlices = nSlices;
   }

   @Override
   public void setNFramesUnverified(int nFrames) {
      super.nFrames = nFrames;
   }

   private void superDraw() {
      if (super.win != null ) {
         super.getCanvas().repaint();
      } 
   }

   @Override
   public void draw() {
      getWindow().getCanvas().setImageUpdated();
      superDraw();
   }

   @Override
   public void drawWithoutUpdate() {
      getWindow().getCanvas().setImageUpdated();
      superDraw();
   }

   @Override
   public int[] getPixelIntensities(int x, int y) {
      return super.getPixel(x, y);
   }
}
