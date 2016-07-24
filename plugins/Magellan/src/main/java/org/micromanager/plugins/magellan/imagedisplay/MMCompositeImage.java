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
package org.micromanager.plugins.magellan.imagedisplay;

import com.google.common.eventbus.EventBus;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.plugin.frame.ContrastAdjuster;
import ij.process.LUT;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;


public class MMCompositeImage extends CompositeImage implements IMMImagePlus {
   private ImagePlus hyperImage_;
   private String title_;
   private EventBus bus_;

   MMCompositeImage(ImagePlus imgp, int type, String title, EventBus bus) {
      super(imgp, type);
      hyperImage_ = imgp;
      title_ = title;
      bus_ = bus;
   }
   
   @Override
   public void mouseMoved(int x, int y) {
      super.mouseMoved(x, y);
      bus_.post(new MouseIntensityEvent(x, y, getPixel(x, y)));
   }

   @Override
   public String getTitle() {
      return title_;
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

   private void superReset() {
      super.reset();
   }

   // Must be called on EDT so to prevent exception that occurs when
   // currentChannel gets set to -1 while updateImage() is running
   @Override
   public void reset() {
      if (SwingUtilities.isEventDispatchThread()) {
         super.reset();
      } else {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               superReset();
            }
         });
      }
   }

   @Override
   public synchronized void setMode(final int mode) {
      superSetMode(mode);
   }

   private void superSetMode(int mode) {
      super.setMode(mode);
   }

   @Override
   public synchronized void setChannelLut(final LUT lut) {
      superSetLut(lut);
   }

   private void superSetLut(LUT lut) {
      super.setChannelLut(lut);
   }

   @Override
   public synchronized void updateImage() {
      superUpdateImage();
   }

   private void superUpdateImage() {
      Runnable runnable = new Runnable() {

         @Override
         public void run() {
            try {
               // Need to set this field to null, or else an infinite loop can be 
               // entered when the imageJ contrast adjuster is open
               Object curVal = null;
               try {
                  curVal = JavaUtils.getRestrictedFieldValue(null,
                          ContrastAdjuster.class, "instance");
                  JavaUtils.setRestrictedFieldValue(null, ContrastAdjuster.class, "instance", null);
               } catch (NoSuchFieldException e) {
                  Log.log("ImageJ ContrastAdjuster doesn't have field named instance",true);
               }

               MMCompositeImage.super.updateImage();

               // Restore the value we had previously set. Bizarrely, not doing this
               // creates significant memory leaks (several MB per file we open/close).
               // I have no idea why.
               try {
                  JavaUtils.setRestrictedFieldValue(null, ContrastAdjuster.class,
                          "instance", curVal);
               } catch (NoSuchFieldException e) {
                  Log.log( "Couldn't restore ImageJ ContrastAdjuster instance.",true);

               }

            } catch (NullPointerException exx) {
               exx.printStackTrace();
               //swallow null pointer exception that happens at startup of acq and breaks everything
               //exception is because rgbPixels gets set to null while CompositeImage.updateImage is running
               Log.log("Null RGB pixels",true);
            }
         }
      };

      if (SwingUtilities.isEventDispatchThread()) {
         runnable.run();
      } else {
         SwingUtilities.invokeLater(runnable);
      }
   }

   public void updateAndDraw(boolean forceUpdateAndPaint) {
      if (forceUpdateAndPaint) {
         // there may be a paint pending, but we want to make sure this update
         // gets called regardless
         CanvasPaintPending.removePaintPending(hyperImage_.getCanvas(), this);
         updateAndDraw();
      } else {    
         updateAndDraw();
      }
   }

   /*
    * called when image pixels change
    */
   @Override
   public void updateAndDraw() {
      if (CanvasPaintPending.isMyPaintPending(super.getCanvas(), this)) {
         return;
      }
       CanvasPaintPending.setPaintPending(super.getCanvas(), this);

       try {
           superUpdateImage();
      } catch (ArrayIndexOutOfBoundsException aex) {
         Log.log(aex);
         CanvasPaintPending.removePaintPending(super.getCanvas(), this);
         return;
      }
      bus_.post(new DrawEvent());
      if (SwingUtilities.isEventDispatchThread()) {
         try {
            JavaUtils.invokeRestrictedMethod(this, ImagePlus.class, "notifyListeners", 2);
         } catch (NoSuchMethodException ex) {
         } catch (IllegalAccessException ex) {
         } catch (IllegalArgumentException ex) {
         } catch (InvocationTargetException ex) {
         }
         superDraw();
      } else {
         SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
               try {
                  JavaUtils.invokeRestrictedMethod(this, ImagePlus.class, "notifyListeners", 2);
               } catch (NoSuchMethodException ex) {
               } catch (IllegalAccessException ex) {
               } catch (IllegalArgumentException ex) {
               } catch (InvocationTargetException ex) {
               }
               superDraw();
            }
         });
      }
   }

   private void superDraw() {
      if (super.win != null) {
         super.getCanvas().repaint();
      }
   }

   /*
    * Doesn't get called during acquisition, animation, contrast adjustment,
    * but may get called sometimes by internal imageJ functions
    */
   @Override
   public void draw() {
      bus_.post(new DrawEvent());
      superDraw();
   }

   /*
    * Called when images changes due to changes in contrast, but not pixels
    * themselves Histogram controls tell image to update without LUTS without
    * updating pixels, metadata, etc
    */
   @Override
   public void drawWithoutUpdate() {
      // dont check for paint pending because always want this to reflect the
      // most recent changes
      super.getCanvas().setPaintPending(true);
      getWindow().getCanvas().setImageUpdated();
      superDraw();
   }

   @Override
   public int[] getPixelIntensities(int x, int y) {
      return super.getPixel(x, y);
   }
}


