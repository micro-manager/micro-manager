///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package imagedisplay;

import com.google.common.eventbus.EventBus;

import ij.ImagePlus;
import ij.ImageStack;

public class MMImagePlus extends ImagePlus implements IMMImagePlus {

   private EventBus bus_;

   MMImagePlus(String title, ImageStack stack, EventBus bus) {
      super(title, stack);
      bus_ = bus;
   }

   @Override
   public int getImageStackSize() {
      return super.nChannels * super.nSlices * super.nFrames;
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
   public void mouseMoved(int x, int y) {
      super.mouseMoved(x, y);
      bus_.post(new MouseIntensityEvent(x, y, getPixel(x, y)));
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
      bus_.post(new DrawEvent());
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

