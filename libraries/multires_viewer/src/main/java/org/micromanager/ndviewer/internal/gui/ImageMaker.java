package org.micromanager.ndviewer.internal.gui;

import org.micromanager.ndviewer.internal.gui.DataViewCoords;
import org.micromanager.ndviewer.internal.gui.contrast.HistogramUtils;
import org.micromanager.ndviewer.internal.gui.contrast.LUT;
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
import org.json.JSONObject;
import org.micromanager.ndviewer.internal.gui.contrast.DisplaySettings;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.api.DataSourceInterface;

/**
 * This Class essentially replaces CompositeImage in ImageJ, and uses low level
 * classes to build a multicolor Image from pixels and contrast settings
 */
public class ImageMaker {

   public static final int EIGHTBIT = 0;
   public static final int SIXTEENBIT = 1;

   private final TreeMap<String, MagellanImageProcessor> channelProcessors_ = new TreeMap<String, MagellanImageProcessor>();

   private int imageWidth_, imageHeight_;
   private int[] rgbPixels_;
   private DataSourceInterface imageCache_;
   private Image displayImage_;
   private MemoryImageSource imageSource_;
   DirectColorModel rgbCM_ = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
   private JSONObject latestTags_;
   private NDViewer display_;

   public ImageMaker(NDViewer c, DataSourceInterface data) {
      display_ = c;
      imageCache_ = data;
   }

   public void close() {
      display_ = null;
      imageCache_ = null;
   }

   public JSONObject getLatestTags() {
      return latestTags_;
   }

   /**
    * Do neccesary calcualtion to get image for display
    *
    * @return
    */
   public Image makeOrGetImage(DataViewCoords viewCoords) {
      boolean remakeDisplayImage = false; //remake the acutal Imge object if size has changed, otherwise just set pixels
      if (viewCoords.getSourceImageSizeAtResLevel().x != imageWidth_
              || viewCoords.getSourceImageSizeAtResLevel().y != imageHeight_) {
         imageWidth_ = (int) viewCoords.getSourceImageSizeAtResLevel().x;
         imageHeight_ = (int) viewCoords.getSourceImageSizeAtResLevel().y;
         rgbPixels_ = new int[imageWidth_ * imageHeight_];
         remakeDisplayImage = true;
      }

      //update pixels
      for (String channel : display_.getChannels()) {
         //create channel processors as needed
         synchronized (this) {
            if (!channelProcessors_.containsKey(channel)) {
               channelProcessors_.put(channel, new MagellanImageProcessor(imageWidth_, imageHeight_, channel));
            }
         }
         if (!display_.getDisplaySettingsObject().isActive(channel)) {
            continue;
         }

         int imagePixelWidth = (int) (viewCoords.getFullResSourceDataSize().x / viewCoords.getDownsampleFactor());
         int imagePixelHeight = (int) (viewCoords.getFullResSourceDataSize().y / viewCoords.getDownsampleFactor());
         long viewOffsetAtResX = (long) (viewCoords.getViewOffset().x / viewCoords.getDownsampleFactor());
         long viewOffsetAtResY = (long) (viewCoords.getViewOffset().y / viewCoords.getDownsampleFactor());

         //replace channel axis position with the specific channel 
         HashMap<String, Integer> axes = new HashMap<String, Integer>(viewCoords.getAxesPositions());
         axes.put("c", display_.getChannelIndex(channel));
         TaggedImage imageForDisplay = imageCache_.getImageForDisplay(
                 axes, viewCoords.getResolutionIndex(),
                 viewOffsetAtResX, viewOffsetAtResY, imagePixelWidth, imagePixelHeight);
                 
                 
         if (viewCoords.getActiveChannel().equals(channel)) {
            latestTags_ = imageForDisplay.tags;
         }
         channelProcessors_.get(channel).changePixels(imageForDisplay.pix, imageWidth_, imageHeight_);

      }

      try {
         boolean firstActive = true;
         Arrays.fill(rgbPixels_, 0);
         int redValue, greenValue, blueValue;
         for (String c : channelProcessors_.keySet()) {
            if (!display_.getDisplaySettingsObject().isActive(c) ) {
               continue;
            }
            String channelName = c;
            if (display_.getDisplaySettingsObject().isActive(channelName)) {
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
         throw new RuntimeException(e);
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

   public HashMap<String, int[]> getHistograms() {
      HashMap<String, int[]> hists = new HashMap<String, int[]>();
      for (String channel : channelProcessors_.keySet()) {
         hists.put(channel, channelProcessors_.get(channel).displayHistogram_);
      }
      return hists;
   }

   public HashMap<String, Integer> getPixelMins() {
      HashMap<String, Integer> hists = new HashMap<String, Integer>();
      for (String i : channelProcessors_.keySet()) {
         hists.put(i, channelProcessors_.get(i).pixelMin_);
      }
      return hists;
   }

   public HashMap<String, Integer> getPixelMaxs() {
      HashMap<String, Integer> hists = new HashMap<String, Integer>();
      for (String i : channelProcessors_.keySet()) {
         hists.put(i, channelProcessors_.get(i).pixelMax_);
      }
      return hists;
   }

   private class MagellanImageProcessor {

      LUT lut;
      int contrastMin_, contrastMax_;
      Object pixels;
      int width, height;
      int pixelMin_, pixelMax_, minAfterRejectingOutliers_, maxAfterRejectingOutliers_;
      byte[] eightBitImage = null;
      int[] reds = null;
      int[] blues = null;
      int[] greens = null;
      int[] rawHistogram = null;
      final String channelName_;
      int[] displayHistogram_;

      public MagellanImageProcessor(int w, int h, String name) {
         width = w;
         height = h;
         channelName_ = name;
      }

      public void changePixels(Object pix, int w, int h) {
         pixels = pix;
         rawHistogram = pixels instanceof short[] ? new int[65536] : new int[256];
         width = w;
         height = h;
         eightBitImage = null;
      }

      public void recompute() {
         contrastMin_ = display_.getDisplaySettingsObject().getContrastMin(channelName_);
         contrastMax_ = display_.getDisplaySettingsObject().getContrastMax(channelName_);
         create8BitImage();
         processHistogram();
         if (display_.getDisplaySettingsObject().getAutoscale()) {
            if (display_.getDisplaySettingsObject().ignoreFractionOn()) {
               contrastMax_ = maxAfterRejectingOutliers_;
               contrastMin_ = minAfterRejectingOutliers_;
            } else {
               contrastMin_ = pixelMin_;
               contrastMax_ = pixelMax_;
            }
            display_.getDisplaySettingsObject().setContrastMin(channelName_, contrastMin_);
            display_.getDisplaySettingsObject().setContrastMax(channelName_, contrastMax_);
            //need to redo this with autoscaled contrast now
            create8BitImage();
            processHistogram();
         }
         lut = makeLUT(display_.getDisplaySettingsObject().getColor(channelName_), 
                 display_.getDisplaySettingsObject().getContrastGamma(channelName_));
         splitLUTRGB();
      }

      private void processHistogram() {
         //Compute stats
         int totalPixels = 0;
         for (int i = 0; i < rawHistogram.length; i++) {
            totalPixels += rawHistogram[i];
         }

         pixelMin_ = -1;
         pixelMax_ = 0;
         int binSize = rawHistogram.length / 256;
         int numBins = (int) Math.min(rawHistogram.length / binSize, DisplaySettings.NUM_DISPLAY_HIST_BINS);
         displayHistogram_ = new int[DisplaySettings.NUM_DISPLAY_HIST_BINS];
         for (int i = 0; i < numBins; i++) {
            displayHistogram_[i] = 0;
            for (int j = 0; j < binSize; j++) {
               int rawHistIndex = (int) (i * binSize + j);
               int rawHistVal = rawHistogram[rawHistIndex];
               displayHistogram_[i] += rawHistVal;
               if (rawHistVal > 0) {
                  pixelMax_ = rawHistIndex;
                  if (pixelMin_ == -1) {
                     pixelMin_ = rawHistIndex;
                  }
               }
            }
            if (display_.getDisplaySettingsObject().isLogHistogram()) {
               displayHistogram_[i] = displayHistogram_[i] > 0 ? (int) (1000 * Math.log(displayHistogram_[i])) : 0;
            }
         }
         maxAfterRejectingOutliers_ = (int) totalPixels;
         // specified percent of pixels are ignored in the automatic contrast setting
         HistogramUtils hu = new HistogramUtils(rawHistogram, totalPixels, 0.01 * 
                 display_.getDisplaySettingsObject().percentToIgnore());
         minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
         maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();

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
         double scale = 256.0 / (contrastMax_ - contrastMin_ + 1);
         for (int i = 0; i < size; i++) {
            if (pixels instanceof short[]) {
               int pixVal = (((short[]) pixels)[i] & 0xffff);
               value = pixVal - contrastMin_;
               rawHistogram[pixVal]++;
            } else {
               int pixVal = (((byte[]) pixels)[i] & 0xffff);
               value = pixVal - contrastMin_;
               rawHistogram[pixVal]++;
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
