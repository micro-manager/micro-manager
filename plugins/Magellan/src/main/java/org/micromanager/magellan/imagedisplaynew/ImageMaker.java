package org.micromanager.magellan.imagedisplaynew;

import org.micromanager.magellan.imagedisplaynew.events.ContrastUpdatedEvent;
import com.google.common.eventbus.Subscribe;
import ij.process.LUT;
import java.awt.Color;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.MemoryImageSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;
import mmcorej.TaggedImage;
import org.micromanager.magellan.imagedisplay.DisplaySettings;
import org.micromanager.magellan.misc.Log;

/**
 * This Class essentially replaces CompositeImage in ImageJ, and uses low level
 * classes to build a multicolor Image from pixels and contrast settings
 */
class ImageMaker {

   public static final int EIGHTBIT = 0;
   public static final int SIXTEENBIT = 1;

   private final TreeMap<Integer, MagellanImageProcessor> channelProcessors_ = new TreeMap<Integer, MagellanImageProcessor>();

   private int imageWidth_, imageHeight_;
   private int[] rgbPixels_;
   private MagellanImageCache imageCache_;
   private Image displayImage_;
   private MemoryImageSource imageSource_;
   DirectColorModel rgbCM_ = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
   private DisplaySettings displaySettings_;

   public ImageMaker(MagellanDisplayController c, MagellanImageCache data, int width, int height) {
      c.registerForEvents(this);
      displaySettings_ = c.getDisplaySettings();
      imageWidth_ = 512;
      imageHeight_ = 512;
      rgbPixels_ = new int[imageWidth_ * imageHeight_];
      imageCache_ = data;
   }

   void close() {
      imageCache_ = null;
      displaySettings_ = null;
   }

   /**
    * Get the histgram, which was automaitically calculated by makeorgetiamge
    *
    * @return
    */
   public int[] getHistogram(int channelIndex) {
      return channelProcessors_.get(channelIndex).histogram;
   }

   /**
    * Do neccesary calcualtion to get image for display
    *
    * @return
    */
   public Image makeOrGetImage(MagellanDataViewCoords viewCoords) {
      boolean remakeDisplayImage = false; //remake the acutal Imge object if size has changed, otherwise just set pixels
      if (viewCoords.getDisplayImageSizeAtResLevel().x != imageWidth_
              || viewCoords.getDisplayImageSizeAtResLevel().y != imageHeight_) {
         imageWidth_ = (int) viewCoords.getDisplayImageSizeAtResLevel().x;
         imageHeight_ = (int) viewCoords.getDisplayImageSizeAtResLevel().y;
         rgbPixels_ = new int[imageWidth_ * imageHeight_];
         remakeDisplayImage = true;
      }

      //update pixels
      for (Integer c : viewCoords.getChannelIndices()) {
         synchronized (this) {
            if (!channelProcessors_.containsKey(c)) {
               channelProcessors_.put(c, new MagellanImageProcessor(imageWidth_, imageHeight_));
            }
         }
         if (!displaySettings_.isActive(viewCoords.getChannelName(c))) {
            continue;
         }

         TaggedImage imageForDisplay = imageCache_.getImageForDisplay(c, viewCoords);
         channelProcessors_.get(c).changePixels(imageForDisplay.pix, imageWidth_, imageHeight_);

      }

      //apply contrast settings
      for (Integer c : channelProcessors_.keySet()) {
         if (!displaySettings_.isActive(viewCoords.getChannelName(c))) {
            continue;
         }
         //only update acrtive channels for speed

         String channelName = viewCoords.getChannelName(c);
         LUT lut = makeLUT(displaySettings_.getColor(channelName), displaySettings_.getGamma(channelName));
         channelProcessors_.get(c).setContrast(lut, displaySettings_.getContrastMin(channelName), displaySettings_.getContrastMax(channelName));
      }

      try {
         boolean firstActive = true;
         Arrays.fill(rgbPixels_, 0);
         int redValue, greenValue, blueValue;
         for (Integer c : channelProcessors_.keySet()) {
            if (!displaySettings_.isActive(viewCoords.getChannelName(c))) {
               continue;
            }
            String channelName = viewCoords.getChannelName(c);
            if (displaySettings_.isActive(channelName)) {
               //get the appropriate pixels from the data view

               //recompute 8 bit image
               channelProcessors_.get(c).recompute();
               byte[] bytes;
               bytes = channelProcessors_.get(c).eightBitImage;
               if (firstActive) {
                  for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                     redValue = channelProcessors_.get(c).reds[bytes[p] & 0xff];
                     greenValue = channelProcessors_.get(c).greens[bytes[p] & 0xff];
                     blueValue = channelProcessors_.get(c).blues[bytes[p] & 0xff];
                     rgbPixels_[p] = redValue | greenValue | blueValue;
                  }
                  firstActive = false;
               } else {
                  int pixel;
                  for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                     pixel = rgbPixels_[p];
                     redValue = (pixel & 0x00ff0000) + channelProcessors_.get(c).reds[bytes[p] & 0xff];
                     greenValue = (pixel & 0x0000ff00) + channelProcessors_.get(c).greens[bytes[p] & 0xff];
                     blueValue = (pixel & 0x000000ff) + channelProcessors_.get(c).blues[bytes[p] & 0xff];

                     if (redValue > 16711680) {
                        redValue = 16711680;
                     }
                     if (greenValue > 65280) {
                        greenValue = 65280;
                     }
                     if (blueValue > 255) {
                        blueValue = 255;
                     }
                     rgbPixels_[p] = redValue | greenValue | blueValue;

                  }
               }
            }

         }
      } catch (Exception e) {
         e.printStackTrace();
         Log.log(e, true);
      }

      if (imageSource_ == null || remakeDisplayImage) {
         imageSource_ = new MemoryImageSource(imageWidth_, imageHeight_, rgbCM_, rgbPixels_, 0, imageWidth_);
         imageSource_.setAnimated(true);
         imageSource_.setFullBufferUpdates(true);
         displayImage_ = Toolkit.getDefaultToolkit().createImage(imageSource_);
      } else {
         imageSource_.newPixels(rgbPixels_, rgbCM_, 0, imageWidth_);
      }
      return displayImage_;
//			newPixels = false;

//      DirectColorModel rgbCM = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
//      WritableRaster wr = rgbCM.createCompatibleWritableRaster(1, 1);
//      SampleModel sampleModel = wr.getSampleModel();
//      sampleModel = sampleModel.createCompatibleSampleModel(imageWidth_, imageHeight_);
//      DataBuffer dataBuffer = new DataBufferInt(rgbPixels_, imageWidth_ * imageHeight_, 0);
//      WritableRaster rgbRaster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
//      return new BufferedImage(rgbCM, rgbRaster, false, null);
   }

   public static LUT makeLUT(Color color, double gamma) {
      int r = color.getRed();
      int g = color.getGreen();
      int b = color.getBlue();

      int size = 256;
      byte[] rs = new byte[size];
      byte[] gs = new byte[size];
      byte[] bs = new byte[size];

      double xn;
      double yn;
      for (int x = 0; x < size; ++x) {
         xn = x / (double) (size - 1);
         yn = Math.pow(xn, gamma);
         rs[x] = (byte) (yn * r);
         gs[x] = (byte) (yn * g);
         bs[x] = (byte) (yn * b);
      }
      return new LUT(8, size, rs, gs, bs);
   }

   HashMap<Integer, int[]> getHistograms() {
      HashMap<Integer, int[]> hists = new HashMap<Integer, int[]>();
      for (Integer i : channelProcessors_.keySet()) {
         hists.put(i, channelProcessors_.get(i).histogram);
      }
      return hists;
   }

   private class MagellanImageProcessor {

      LUT lut;
      int min, max;
      Object pixels;
      int width, height;
      byte[] eightBitImage = null;
      int[] reds = null;
      int[] blues = null;
      int[] greens = null;
      int[] histogram = null;

      public MagellanImageProcessor(int w, int h) {
         width = w;
         height = h;
      }

      public void changePixels(Object pix, int w, int h) {
         pixels = pix;
         histogram = pixels instanceof short[] ? new int[65536] : new int[256];
         width = w;
         height = h;
         eightBitImage = null;
      }

      public void recompute() {
         create8BitImage();
      }

      public void setContrast(LUT l, int minn, int maxx) {
//         boolean reset = min != minn || max != maxx;
         //reset 8 bit monochrome image if min and max xhange
//         if (reset) {
//            create8BitImage();
//         }
         //reset LUT    
         min = minn;
         max = maxx;
         lut = l;
         splitLUTRGB();
      }

      /**
       * split LUT in RGB for fast lookup
       */
      private void splitLUTRGB() {
         IndexColorModel icm = (IndexColorModel) lut;
         int mapSize = icm.getMapSize();
         if (reds == null || reds.length != mapSize) {
            reds = new int[mapSize];
            greens = new int[mapSize];
            blues = new int[mapSize];
         }
         byte[] tmp = new byte[mapSize];
         icm.getReds(tmp);
         for (int i = 0; i < mapSize; i++) {
            reds[i] = (tmp[i] & 0xff) << 16;
         }
         icm.getGreens(tmp);
         for (int i = 0; i < mapSize; i++) {
            greens[i] = (tmp[i] & 0xff) << 8;
         }
         icm.getBlues(tmp);
         for (int i = 0; i < mapSize; i++) {
            blues[i] = tmp[i] & 0xff;
         }
      }

      //Create grayscale image with LUT min and max applied, but no color mapping
      //Also compute histogram in the process
      private void create8BitImage() {
         int size = width * height;
         if (eightBitImage == null) {
            eightBitImage = new byte[size];
         }
         int value;
         double scale = 256.0 / (max - min + 1);
         for (int i = 0; i < size; i++) {
            if (pixels instanceof short[]) {
               int pixVal = (((short[]) pixels)[i] & 0xffff);
               value = pixVal - min;
               histogram[pixVal]++;
            } else {
               int pixVal = (((byte[]) pixels)[i] & 0xffff);
               value = pixVal - min;
               histogram[pixVal]++;
            }
            if (value < 0) {
               value = 0;
            }
            value = (int) (value * scale + 0.5);
            if (value > 255) {
               value = 255;
            }
            eightBitImage[i] = (byte) value;
         }
      }

   }

}
