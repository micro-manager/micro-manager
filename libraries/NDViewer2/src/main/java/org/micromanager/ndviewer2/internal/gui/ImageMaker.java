package org.micromanager.ndviewer2.internal.gui;

import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndviewer2.NDViewer2DataSource;
import org.micromanager.ndviewer2.internal.gui.contrast.DisplaySettings;
import org.micromanager.ndviewer2.internal.gui.contrast.HistogramUtils;
import org.micromanager.ndviewer2.internal.gui.contrast.LUT;
import org.micromanager.ndviewer2.main.NDViewer;

/**
 * This Class essentially replaces CompositeImage in ImageJ, and uses low level
 * classes to build a multicolor Image from pixels and contrast settings
 */
public class ImageMaker {

   public static final int EIGHTBIT = 0;
   public static final int SIXTEENBIT = 1;

   private final ConcurrentHashMap<String, NDVImageProcessor> channelProcessors_ =
            new ConcurrentHashMap<>();

   private int imageWidth_;
   private int imageHeight_;
   private int[] rgbPixels_;
   private NDViewer2DataSource data_;
   private Image displayImage_;
   private MemoryImageSource imageSource_;
   DirectColorModel rgbCM_ = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
   private JSONObject latestTags_;
   private NDViewer display_;
   private boolean closed_ = false;

   public ImageMaker(NDViewer c, NDViewer2DataSource data) {
      display_ = c;
      data_ = data;
   }

   public void close() {
      closed_ = true;
      display_ = null;
      data_ = null;
   }

   public JSONObject getLatestTags() {
      return latestTags_;
   }

   private TaggedImage getDisplayImage(HashMap<String, Object> axes,
                                       int resolutionindex, double xOffset, double yOffset,
                                       int imageWidth, int imageHeight) {

      // The axes requested correspond to every scrollbar in the viewer. But all axes don't
      // have to apply to every channel (for example, a maximum intensity projection doesn't
      // have z axis). So search through all axes currently stored for this channel, and
      // delete any axes from the request that arent present
      Set<HashMap<String, Object>> allImageKeys = data_.getImageKeys();
      HashSet<String> axesInChannel = new HashSet<String>();
      // If some axes aren't provided,
      for (HashMap<String, Object> key : allImageKeys) {
         if ((key.containsKey("channel") && axes.containsKey("channel")
                  && key.get("channel").equals(axes.get("channel")))) {
            axesInChannel.addAll(key.keySet());
         } else if (!axes.containsKey("channel")) {
            axesInChannel.addAll(axes.keySet());
         }
      }

      String[] requestedAxes = axes.keySet().toArray(new String[0]);
      for (String axis : requestedAxes) {
         if (!axis.equals("channel") && !axesInChannel.contains(axis)) {
            axes.remove(axis);
         }
      }

      return data_.getImageForDisplay(
              axes, resolutionindex, xOffset, yOffset, imageWidth, imageHeight);
   }

   /**
    * Do neccesary calcualtion to get image for display
    *
    * @return
    */
   public synchronized Image makeOrGetImage(DataViewCoords viewCoords) {
      if (closed_) {
         return null;
      }
      try {

         boolean remakeDisplayImage = false; //remake the actual Image object if size
         // has changed, otherwise just set pixels
         if (((int) viewCoords.getSourceImageSizeAtResLevel().x) != imageWidth_
                 || ((int) viewCoords.getSourceImageSizeAtResLevel().y) != imageHeight_) {
            imageWidth_ = (int) viewCoords.getSourceImageSizeAtResLevel().x;
            imageHeight_ = (int) viewCoords.getSourceImageSizeAtResLevel().y;
            rgbPixels_ = new int[imageWidth_ * imageHeight_];
            remakeDisplayImage = true;
         }


         // If there are ever channels being computed that don't actually exist, get rid of them
         for (String existingChannelName : channelProcessors_.keySet()) {
            if (!display_.getDisplayModel().getDisplayedChannels().contains(existingChannelName)) {
               channelProcessors_.remove(existingChannelName);
            }
         }

         //update pixels
         if (display_.getDisplayModel().getDisplayedChannels() != null) {
            latestTags_ = null;
            DisplayModel displayModel = display_.getDisplayModel();
            List<String> channels = new LinkedList<String>(displayModel.getDisplayedChannels());
            for (String channel : channels) {
               //create channel processors as needed
               if (!channelProcessors_.containsKey(channel)) {
                  channelProcessors_.put(channel, viewCoords.isRGB() ? new NDVImageProcessorRGB(
                           imageWidth_, imageHeight_, channel) :
                          new NDVImageProcessor(imageWidth_, imageHeight_, channel));
               }

               if (!display_.getDisplaySettingsObject().isActive(channel)) {
                  continue;
               }

               int imagePixelWidth = (int) (viewCoords.getFullResSourceDataSize().x
                        / viewCoords.getDownsampleFactor());
               int imagePixelHeight = (int) (viewCoords.getFullResSourceDataSize().y
                        / viewCoords.getDownsampleFactor());
               long viewOffsetAtResX = (long) (viewCoords.getViewOffset().x
                        / viewCoords.getDownsampleFactor());
               long viewOffsetAtResY = (long) (viewCoords.getViewOffset().y
                        / viewCoords.getDownsampleFactor());

               HashMap<String, Object> axes = new HashMap<>(viewCoords.getAxesPositions());
               // axes contains a single position for channel, reflecting where the scrollbar
               // is set. But we actually want to display all channels at once, so replace this
               // with the one we are currently adding UNLESS the one we are currently adding
               // is actually a dummy channel name because there are no channels
               if (!channel.equals(NDViewer.NO_CHANNEL)) {
                  axes.put(NDViewer.CHANNEL_AXIS, channel);
               } else {
                  axes.remove(NDViewer.CHANNEL_AXIS);
               }

               TaggedImage imageForDisplay = getDisplayImage(axes, viewCoords.getResolutionIndex(),
                       viewOffsetAtResX, viewOffsetAtResY, imagePixelWidth, imagePixelHeight);

               if (latestTags_ == null
                        || (viewCoords.getAxesPositions().containsKey(NDViewer.CHANNEL_AXIS)
                        && viewCoords.getAxesPositions().get(NDViewer.CHANNEL_AXIS)
                        .equals(channel))) {
                  latestTags_ = imageForDisplay.tags;
               }
               channelProcessors_.get(channel).changePixels(imageForDisplay.pix,
                        imageWidth_, imageHeight_);
            }
         }

         boolean firstActive = true;
         Arrays.fill(rgbPixels_, 0);
         int redValue;
         int greenValue;
         int blueValue;
         for (String c : channelProcessors_.keySet()) {
            if (!display_.getDisplaySettingsObject().isActive(c)) {
               continue;
            }
            String channelName = c;
            if (display_.getDisplaySettingsObject().isActive(channelName)) {
               //get the appropriate pixels from the data view

               //recompute 8 bit image
               channelProcessors_.get(c).recompute();
               if (firstActive) {
                  if (channelProcessors_.get(c) instanceof NDVImageProcessorRGB) {
                     byte[] bytesR = ((NDVImageProcessorRGB) channelProcessors_.get(c)).rProcessor_
                              .eightBitImage;
                     byte[] bytesG = ((NDVImageProcessorRGB) channelProcessors_.get(c)).gProcessor_
                              .eightBitImage;
                     byte[] bytesB = ((NDVImageProcessorRGB) channelProcessors_.get(c)).bProcessor_
                              .eightBitImage;
                     for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                        redValue = ((NDVImageProcessorRGB) channelProcessors_.get(c)).rProcessor_
                                 .reds[bytesR[p] & 0xff];
                        greenValue = ((NDVImageProcessorRGB) channelProcessors_.get(c)).gProcessor_
                                 .greens[bytesG[p] & 0xff];
                        blueValue = ((NDVImageProcessorRGB) channelProcessors_.get(c)).bProcessor_
                                 .blues[bytesB[p] & 0xff];
                        rgbPixels_[p] = redValue | greenValue | blueValue;
                     }
                  } else {
                     byte[] bytes = channelProcessors_.get(c).eightBitImage;
                     for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                        redValue = channelProcessors_.get(c).reds[bytes[p] & 0xff];
                        greenValue = channelProcessors_.get(c).greens[bytes[p] & 0xff];
                        blueValue = channelProcessors_.get(c).blues[bytes[p] & 0xff];
                        rgbPixels_[p] = redValue | greenValue | blueValue;
                     }
                  }
                  firstActive = false;
               } else {
                  //add subsequent channels onto the first one
                  int pixel;
                  if (channelProcessors_.get(c) instanceof NDVImageProcessorRGB) {
                     byte[] bytesR = ((NDVImageProcessorRGB) channelProcessors_.get(c)).rProcessor_
                              .eightBitImage;
                     byte[] bytesG = ((NDVImageProcessorRGB) channelProcessors_.get(c)).gProcessor_
                              .eightBitImage;
                     byte[] bytesB = ((NDVImageProcessorRGB) channelProcessors_.get(c)).bProcessor_
                              .eightBitImage;
                     for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                        pixel = rgbPixels_[p];
                        redValue = (pixel & 0x00ff0000) + ((NDVImageProcessorRGB) channelProcessors_
                                 .get(c)).rProcessor_.reds[bytesR[p] & 0xff];
                        greenValue = (pixel & 0x0000ff00) + ((NDVImageProcessorRGB)
                                 channelProcessors_.get(c)).gProcessor_.greens[bytesG[p] & 0xff];
                        blueValue = (pixel & 0x000000ff) + ((NDVImageProcessorRGB)
                                 channelProcessors_.get(c)).bProcessor_.blues[bytesB[p] & 0xff];

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
                  } else {
                     byte[] bytes = channelProcessors_.get(c).eightBitImage;
                     for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                        pixel = rgbPixels_[p];
                        redValue = (pixel & 0x00ff0000) + channelProcessors_.get(c)
                                 .reds[bytes[p] & 0xff];
                        greenValue = (pixel & 0x0000ff00) + channelProcessors_.get(c)
                                 .greens[bytes[p] & 0xff];
                        blueValue = (pixel & 0x000000ff) + channelProcessors_.get(c)
                                 .blues[bytes[p] & 0xff];

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

         }


         if (imageSource_ == null || remakeDisplayImage) {
            imageSource_ = new MemoryImageSource(imageWidth_, imageHeight_, rgbCM_,
                     rgbPixels_, 0, imageWidth_);
            imageSource_.setAnimated(true);
            imageSource_.setFullBufferUpdates(true);
            displayImage_ = Toolkit.getDefaultToolkit().createImage(imageSource_);
         } else {
            imageSource_.newPixels(rgbPixels_, rgbCM_, 0, imageWidth_);
         }
      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException(e);
      }
      return displayImage_;
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
         hists.put(channel, channelProcessors_.get(channel).rawHistogram);
      }
      return hists;
   }

   private class NDVImageProcessorRGB extends NDVImageProcessor {

      private NDVImageProcessor rProcessor_;
      private NDVImageProcessor bProcessor_;
      private NDVImageProcessor gProcessor_;

      public NDVImageProcessorRGB(int w, int h, String name) {
         super(w, h, name);
         rProcessor_ = new NDVImageProcessor(w, h, name);
         gProcessor_ = new NDVImageProcessor(w, h, name);
         bProcessor_ = new NDVImageProcessor(w, h, name);
      }

      public void changePixels(Object pix, int w, int h) {
         byte[] rPix = new byte[w * h];
         byte[] gPix = new byte[w * h];
         byte[] bPix = new byte[w * h];
         for (int i = 0; i < w * h; i++) {
            bPix[i] = ((byte[]) pix)[4 * i ];
            gPix[i] = ((byte[]) pix)[4 * i + 1];
            rPix[i] = ((byte[]) pix)[4 * i + 2];
         }

         rProcessor_.changePixels(rPix, w, h);
         gProcessor_.changePixels(gPix, w, h);
         bProcessor_.changePixels(bPix, w, h);
      }

      public void recompute() {
         contrastMin_ = display_.getDisplaySettingsObject().getContrastMin(channelName_);
         contrastMax_ = display_.getDisplaySettingsObject().getContrastMax(channelName_);
         rProcessor_.contrastMin_ = contrastMin_;
         rProcessor_.contrastMax_ = contrastMax_;
         gProcessor_.contrastMin_ = contrastMin_;
         gProcessor_.contrastMax_ = contrastMax_;
         bProcessor_.contrastMin_ = contrastMin_;
         bProcessor_.contrastMax_ = contrastMax_;
         rProcessor_.create8BitImage();
         gProcessor_.create8BitImage();
         bProcessor_.create8BitImage();
         rawHistogram = new int[rProcessor_.rawHistogram.length];
         for (int i = 0; i < rawHistogram.length; i++) {
            rawHistogram[i] += rProcessor_.rawHistogram[i];
            rawHistogram[i] += gProcessor_.rawHistogram[i];
            rawHistogram[i] += bProcessor_.rawHistogram[i];
         }
         processHistogram(rawHistogram);

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
            rProcessor_.create8BitImage();
            gProcessor_.create8BitImage();
            bProcessor_.create8BitImage();
            //Merge the histograms of R, G, and B
            rawHistogram = new int[rProcessor_.rawHistogram.length];
            for (int i = 0; i < rawHistogram.length; i++) {
               rawHistogram[i] += rProcessor_.rawHistogram[i];
               rawHistogram[i] += gProcessor_.rawHistogram[i];
               rawHistogram[i] += bProcessor_.rawHistogram[i];
            }
         }
         rProcessor_.lut = makeLUT(Color.red, display_.getDisplaySettingsObject()
                  .getContrastGamma(channelName_));
         gProcessor_.lut = makeLUT(Color.green, display_.getDisplaySettingsObject()
                  .getContrastGamma(channelName_));
         bProcessor_.lut = makeLUT(Color.blue, display_.getDisplaySettingsObject()
                  .getContrastGamma(channelName_));
         rProcessor_.splitLUTRGB();
         gProcessor_.splitLUTRGB();
         bProcessor_.splitLUTRGB();
      }

      private void processHistogram(int[] rawHistogram) {
         // Compute stats
         int totalPixels = 0;
         for (int i = 0; i < rawHistogram.length; i++) {
            totalPixels += rawHistogram[i];
         }

         pixelMin_ = -1;
         pixelMax_ = 0;
         int binSize = rawHistogram.length / 256;
         int numBins = (int) Math.min(rawHistogram.length / binSize,
                  DisplaySettings.NUM_DISPLAY_HIST_BINS);
         for (int i = 0; i < numBins; i++) {
            for (int j = 0; j < binSize; j++) {
               int rawHistIndex = (int) (i * binSize + j);
               int rawHistVal = rawHistogram[rawHistIndex];
               if (rawHistVal > 0) {
                  pixelMax_ = rawHistIndex;
                  if (pixelMin_ == -1) {
                     pixelMin_ = rawHistIndex;
                  }
               }
            }
         }
         maxAfterRejectingOutliers_ = (int) totalPixels;
         // specified percent of pixels are ignored in the automatic contrast setting
         double percentToIgnore = 0.0;
         try  {
            percentToIgnore = display_.getDisplaySettingsObject().percentToIgnore();
         } catch (Exception e) {
            System.err.println(e);
         }
         HistogramUtils hu = new HistogramUtils(rawHistogram, totalPixels, 0.01 * percentToIgnore);
         minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
         maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();

      }
   }

   private class NDVImageProcessor {
      LUT lut;
      int contrastMin_;
      int contrastMax_;
      Object pixels;
      int width;
      int height;
      int pixelMin_;
      int pixelMax_;
      int minAfterRejectingOutliers_;
      int maxAfterRejectingOutliers_;
      byte[] eightBitImage = null;
      int[] reds = null;
      int[] blues = null;
      int[] greens = null;
      int[] rawHistogram = null;
      final String channelName_;

      public NDVImageProcessor(int w, int h, String name) {
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
         DisplaySettings ds = display_.getDisplaySettingsObject();
         contrastMin_ = ds.getContrastMin(channelName_);
         contrastMax_ = ds.getContrastMax(channelName_);
         create8BitImage();
         processHistogram(rawHistogram);
         if (ds.getAutoscale()) {
            if (ds.ignoreFractionOn()) {
               contrastMax_ = maxAfterRejectingOutliers_;
               contrastMin_ = minAfterRejectingOutliers_;
            } else {
               contrastMin_ = pixelMin_;
               contrastMax_ = pixelMax_;
            }
            ds.setContrastMin(channelName_, contrastMin_);
            ds.setContrastMax(channelName_, contrastMax_);
            //need to redo this with autoscaled contrast now
            create8BitImage();
            processHistogram(rawHistogram);
         }
         lut = makeLUT(display_.getDisplaySettingsObject().getColor(channelName_),
                 display_.getDisplaySettingsObject().getContrastGamma(channelName_));
         splitLUTRGB();
      }

      private void processHistogram(int[] rawHistogram) {
         //Compute stats
         int totalPixels = 0;
         for (int i = 0; i < rawHistogram.length; i++) {
            totalPixels += rawHistogram[i];
         }

         pixelMin_ = -1;
         pixelMax_ = 0;
         int binSize = rawHistogram.length / 256;
         int numBins = (int) Math.min(rawHistogram.length / binSize,
                  DisplaySettings.NUM_DISPLAY_HIST_BINS);
         for (int i = 0; i < numBins; i++) {
            for (int j = 0; j < binSize; j++) {
               int rawHistIndex = (int) (i * binSize + j);
               int rawHistVal = rawHistogram[rawHistIndex];
               if (rawHistVal > 0) {
                  pixelMax_ = rawHistIndex;
                  if (pixelMin_ == -1) {
                     pixelMin_ = rawHistIndex;
                  }
               }
            }
         }
         maxAfterRejectingOutliers_ = (int) totalPixels;
         // specified percent of pixels are ignored in the automatic contrast setting
         double percentToIgnore = 0.0;
         try  {
            percentToIgnore = display_.getDisplaySettingsObject().percentToIgnore();
         } catch (Exception e) {
            System.err.println(e);
         }
         HistogramUtils hu = new HistogramUtils(rawHistogram, totalPixels, 0.01 * percentToIgnore);
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
         if (pixels == null) {
            return;
         }
         int value;
         double scale = 256.0 / (contrastMax_ - contrastMin_ + 1);
         for (int i = 0; i < size; i++) {
            if (pixels instanceof short[]) {
               int pixVal = (((short[]) pixels)[i] & 0xffff);
               value = pixVal - contrastMin_;
               rawHistogram[pixVal]++;
            } else {
               int pixVal = (((byte[]) pixels)[i] & 0xff);
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
