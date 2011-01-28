package org.micromanager.acquisition;

import ij.process.LUT;
import java.awt.event.AdjustmentEvent;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.measure.Calibration;
import ij.plugin.Animator;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class VirtualAcquisitionDisplay {

   private static Hashtable<ImagePlus, VirtualAcquisitionDisplay> 
           acquisitionDisplays_
           = new Hashtable<ImagePlus, VirtualAcquisitionDisplay>();
   
   public static VirtualAcquisitionDisplay getDisplay(ImagePlus imgp) {
      if (acquisitionDisplays_.containsKey(imgp))
         return acquisitionDisplays_.get(imgp);
      else
         return null;
   }


   final MMImageCache imageCache_;
   final private AcquisitionEngine eng_;
   final private ImagePlus hyperImage_;
   final private HyperstackControls hc_;
   final AcquisitionVirtualStack virtualStack_;
   final private ScrollbarWithLabel pSelector_;
   private ChannelDisplaySettings[] channelSettings_;
   private int numComponents_ = 1;

   public VirtualAcquisitionDisplay(boolean newData, MMImageCache imageCache, AcquisitionEngine eng) {
      imageCache_ = imageCache;
      eng_ = eng;
      pSelector_ = createPositionScrollbar();

      JSONObject summaryMetadata = getSummaryMetadata();
      int numSlices = 1;
      int numFrames = 1;
      int numChannels = 1;
      int numGrayChannels;
      int numPositions = 1;
      int width = 0;
      int height = 0;

      try {
         width = MDUtils.getWidth(summaryMetadata);
         height = MDUtils.getHeight(summaryMetadata);
         numSlices = Math.max(summaryMetadata.getInt("Slices"), 1);
         numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);
         numChannels = Math.max(summaryMetadata.getInt("Channels"), 1);
         numPositions = Math.max(summaryMetadata.getInt("Positions"), 1);
         numComponents_ = MDUtils.getNumberOfComponents(summaryMetadata);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      numGrayChannels = numComponents_ * numChannels;
      int type = 0;
      try {
         type = MDUtils.getSingleChannelType(summaryMetadata);
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Unable to determine acquisition type.");
      }
      virtualStack_ = new AcquisitionVirtualStack(width, height, type, null,
              imageCache, numGrayChannels * numSlices * numFrames, this);
      if (summaryMetadata.has("PositionIndex")) {
         try {
            virtualStack_.setPositionIndex(MDUtils.getPositionIndex(summaryMetadata));
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      if (channelSettings_ == null) {
         channelSettings_ = new ChannelDisplaySettings[numGrayChannels];
         for (int i = 0; i < numGrayChannels; ++i) {
            channelSettings_[i] = new ChannelDisplaySettings();
         }
      }

      hc_ = new HyperstackControls(this);
      hyperImage_ = createImagePlus(numChannels, numSlices, numFrames, virtualStack_, hc_);
      acquisitionDisplays_.put(hyperImage_, this);
      applyPixelSizeCalibration(hyperImage_);
      createWindow(hyperImage_, hc_);
      setNumPositions(numPositions);
      updateAndDraw();
      updateWindow();
      readChannelSettingsFromCache(true);
   }

   private void applyPixelSizeCalibration(final ImagePlus hyperImage) {
      // Set ImageJ pixel size calibration
      try {
         double pixSizeUm = getSummaryMetadata().getDouble("PixelSize_um");
         if (pixSizeUm > 0) {
            Calibration cal = new Calibration();
            cal.setUnit("um");
            cal.pixelWidth = pixSizeUm;
            cal.pixelHeight = pixSizeUm;
            hyperImage.setCalibration(cal);
         }
      } catch (JSONException ex) {
         // no pixelsize defined.  Nothing to do
      }
   }

   private void setNumPositions(int n) {
      pSelector_.setMaximum(n + 1);
      ImageWindow win = hyperImage_.getWindow();
      if (n > 1 && pSelector_.getParent() == null) {
         win.add(pSelector_, win.getComponentCount() - 1);
      } else if (n == 1 && pSelector_.getParent() != null) {
         win.remove(pSelector_);
      }
      win.pack();
   }

   public ImagePlus getHyperImage() {
      return hyperImage_;
   }

   private void updateAndDraw() {
      if (getNumGrayChannels() > 1) {
         ((CompositeImage) hyperImage_).setChannelsUpdated();
      }
      hyperImage_.updateAndDraw();
   }

   public void updateWindow() {
      if (hc_ == null) {
         return;
      }

      String status = "";

      if (eng_ != null) {
         if (acquisitionIsRunning()) {
            if (!abortRequested()) {
               hc_.enableAcquisitionControls(true);
               if (isPaused()) {
                  status = "paused";
               } else {
                  status = "running";
               }
            } else {
               hc_.enableAcquisitionControls(false);
               status = "interrupted";
            }
         } else {
            hc_.enableAcquisitionControls(false);
            if (!status.contentEquals("interrupted")) {
               status = "finished";
            }
         }
         status += ", ";
      } else {
         hc_.enableAcquisitionControls(false);
      }
      if (isDiskCached()) {
         status += "on disk";
      } else {
         status += "not yet saved";
      }

      hc_.enableShowFolderButton(imageCache_.getDiskLocation() != null);
      String path = isDiskCached() ? new File(imageCache_.getDiskLocation()).getName() + " " : "Untitled ";
      hyperImage_.getWindow().setTitle(path + "(" + status + ")");
   }

   public void showImage(TaggedImage taggedImg) throws MMScriptException {

      try {
         if (hyperImage_.getSlice() == 1) {
            hyperImage_.getProcessor().setPixels(
                    hyperImage_.getStack().getPixels(1));
         }

         updateWindow();
         JSONObject md = taggedImg.tags;

         try {
            int p = 1 + MDUtils.getPositionIndex(taggedImg.tags);
            if (p >= getNumPositions()) {
               setNumPositions(p+1);
            }
            setPosition(1 + MDUtils.getPositionIndex(taggedImg.tags));
            hyperImage_.setPosition(1 + MDUtils.getChannelIndex(md),
                                    1 + MDUtils.getSliceIndex(md),
                                    1 + MDUtils.getFrameIndex(md));
            //setPlaybackLimits(1, 1 + MDUtils.getFrameIndex(md));
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
         if (hyperImage_.getFrame() == 1) {
            try {
               int pixelMin = ImageUtils.getMin(taggedImg.pix);
               int pixelMax = ImageUtils.getMax(taggedImg.pix);
               if (MDUtils.isRGB(taggedImg)) {
                  for (int i = 1; i <= getNumGrayChannels(); ++i) {
                     (hyperImage_).setPosition(i,
                             MDUtils.getSliceIndex(taggedImg.tags),
                             MDUtils.getFrameIndex(taggedImg.tags));
                     hyperImage_.setDisplayRange(pixelMin, pixelMax);
                     updateAndDraw();
                  }
               } else {

                  int chan = MDUtils.getChannelIndex(md);

                  int min = (int) hyperImage_.getDisplayRangeMin();
                  int max = (int) hyperImage_.getDisplayRangeMax();
                  if (hyperImage_.getSlice() == 1
                          || channelSettings_[chan].min == Integer.MAX_VALUE
                          || channelSettings_[chan].max == Integer.MIN_VALUE) {
                     min = Integer.MAX_VALUE;
                     max = Integer.MIN_VALUE;
                  }

                  readChannelSettingsFromCache(false);
                  setChannelColor(chan, channelSettings_[chan].color.getRGB());
                  min = Math.min(min, pixelMin);
                  max = Math.max(max, pixelMax);
                  channelSettings_[chan].min = (int) min;
                  channelSettings_[chan].max = (int) max;

                  hyperImage_.setDisplayRange(min, max);
                  getSummaryMetadata().getJSONArray("ChContrastMax").put(chan, max);
                  getSummaryMetadata().getJSONArray("ChContrastMin").put(chan, min);
                  writeChannelSettingsToCache(chan);
               }
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
         updateAndDraw();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void updatePosition(int p) {
      virtualStack_.setPositionIndex(p - 1);
      if (!isComposite()) {
         Object pixels = virtualStack_.getPixels(hyperImage_.getCurrentSlice());
         hyperImage_.getProcessor().setPixels(pixels);
      }
      updateAndDraw();
   }

   public void setPosition(int p) {
      pSelector_.setValue(p);
   }

   boolean pause() {
      if (eng_ != null) {
         if (eng_.isPaused()) {
            eng_.setPause(false);
         } else {
            eng_.setPause(true);
         }
         updateWindow();
         return (eng_.isPaused());
      }
      return false;
   }

   boolean abort() {
      if (eng_ != null) {
         if (eng_.abortRequest()) {
            updateWindow();
            return true;
         }
      }
      return false;
   }

   public boolean acquisitionIsRunning() {
      if (eng_ != null) {
         return eng_.isAcquisitionRunning();
      } else {
         return false;
      }
   }

   public long getNextWakeTime() {
      return eng_.getNextWakeTime();
   }

   public boolean abortRequested() {
      if (eng_ != null) {
         return eng_.abortRequested();
      } else {
         return false;
      }
   }

   private boolean isPaused() {
      if (eng_ != null) {
         return eng_.isPaused();
      } else {
         return false;
      }
   }

   boolean saveAs() {
      String prefix;
      String root;
      for (;;) {
         File f = FileDialogs.save(hyperImage_.getWindow(),
                 "Please choose a location for the data set",
                 MMStudioMainFrame.MM_DATA_SET);
         if (f == null) // Canceled.
         {
            return false;
         }
         prefix = f.getName();
         root = new File(f.getParent()).getAbsolutePath();
         if (f.exists()) {
            ReportingUtils.showMessage(prefix
                    + " is write only! Please choose another name.");
         } else {
            break;
         }
      }

      TaggedImageStorageDiskDefault newFileManager = new TaggedImageStorageDiskDefault(root + "/" + prefix, true,
              getSummaryMetadata());
      for (int i = 0; i < getNumGrayChannels(); i++) {
         writeChannelSettingsToCache(i);
      }
      imageCache_.saveAs(newFileManager);
      MMStudioMainFrame.getInstance().setAcqDirectory(root);
      updateWindow();
      return true;
   }

   public ImagePlus createImagePlus(int channels, int slices,
           int frames, final AcquisitionVirtualStack virtualStack,
           HyperstackControls hc) {
      MMImagePlus imgp = new MMImagePlus(imageCache_.getDiskLocation(), virtualStack);
      final ImagePlus hyperImage;

      imgp.setDimensions(channels, slices, frames);
      if (channels > 1) {
         hyperImage = new CompositeImage(imgp, CompositeImage.COMPOSITE);
      } else {
         hyperImage = imgp;
         imgp.setOpenAsHyperStack(true);
      }
      return hyperImage;
   }

   private void createWindow(ImagePlus hyperImage, HyperstackControls hc) {
      final ImageWindow win = new StackWindow(hyperImage) {

         private boolean windowClosingDone_ = false;
         private boolean closed_ = false;

         @Override
         public void windowClosing(WindowEvent e) {
            if (windowClosingDone_) {
               return;
            }

            if (eng_ != null && eng_.isAcquisitionRunning()) {
               if (!abort()) {
                  return;
               }
            }

            if (imageCache_.getDiskLocation() == null) {
               int result = JOptionPane.showConfirmDialog(this,
                       "This data set has not yet been saved.\n"
                       + "Do you want to save it?",
                       "Micro-Manager",
                       JOptionPane.YES_NO_CANCEL_OPTION);

               if (result == JOptionPane.YES_OPTION) {
                  if (!saveAs()) {
                     return;
                  }
               } else if (result == JOptionPane.CANCEL_OPTION) {
                  return;
               }
            }

            // push current display settings to cache
            if (imageCache_ != null) {
               imageCache_.close();
            }

            if (!closed_) {
               close();
            }

            super.windowClosing(e);
            windowClosingDone_ = true;
            closed_ = true;
         }

         @Override
         public void windowClosed(WindowEvent E) {
            this.windowClosing(E);
            super.windowClosed(E);
         }

         @Override
         public void windowActivated(WindowEvent e) {
            if (!isClosed()) {
               super.windowActivated(e);
            }
         }
      };


      win.setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
      MMStudioMainFrame.getInstance().addMMBackgroundListener(win);

      win.add(hc);
      ImagePlus.addImageListener(hc);
      win.pack();
   }

   private ScrollbarWithLabel createPositionScrollbar() {
      final ScrollbarWithLabel pSelector = new ScrollbarWithLabel(null, 1, 1, 1, 2, 'p') {

         @Override
         public void setValue(int v) {
            if (this.getValue() != v) {
               super.setValue(v);
               updatePosition(v);
            }
         }
      };

      // prevents scroll bar from blinking on Windows:
      pSelector.setFocusable(false);
      pSelector.setUnitIncrement(1);
      pSelector.setBlockIncrement(1);
      pSelector.addAdjustmentListener(new AdjustmentListener() {

         public void adjustmentValueChanged(AdjustmentEvent e) {
            updatePosition(pSelector.getValue());
            // ReportingUtils.logMessage("" + pSelector.getValue());
         }
      });
      return pSelector;
   }

   public JSONObject getCurrentMetadata() {
      try {
         if (hyperImage_ != null) {
            TaggedImage image = virtualStack_.getTaggedImage(hyperImage_.getCurrentSlice());
            if (image != null) {
               return image.tags;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (NullPointerException ex) {
         return null;
      }
   }

   public int getCurrentPosition() {
      return virtualStack_.getPositionIndex();
   }

   public int getNumSlices() {
      return hyperImage_.getNSlices();
   }

   public int getNumFrames() {
      return hyperImage_.getNFrames();
   }

   public int getNumPositions() {
      return pSelector_.getMaximum() - 1;
   }

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public ImagePlus getImagePlus(int position) {
      ImagePlus iP = new ImagePlus();
      iP.setStack(virtualStack_);
      iP.setDimensions(numComponents_ * getNumChannels(), getNumSlices(), getNumFrames());
      return iP;
   }

   public void setComment(String comment) throws MMScriptException {
      try {
         getSummaryMetadata().put("Comment", comment);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public final JSONObject getSummaryMetadata() {
      return imageCache_.getSummaryMetadata();
   }

   public void close() {
      if (hyperImage_ != null) {
         hyperImage_.close();
      }
   }

   public synchronized boolean windowClosed() {
      ImageWindow win = hyperImage_.getWindow();
      return (win == null || win.isClosed());
   }

   public void showFolder() {
      if (isDiskCached()) {
         try {
            if (JavaUtils.isWindows()) {
               Runtime.getRuntime().exec("Explorer /n,/select," + imageCache_.getDiskLocation());
            } else if (JavaUtils.isMac()) {
               Runtime.getRuntime().exec("open " + imageCache_.getDiskLocation());
            }
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   public void setPlaybackFPS(double fps) {
      if (hyperImage_ != null) {
         try {
            JavaUtils.setRestrictedFieldValue(null, Animator.class,
                    "animationRate", (double) fps);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.showError(ex);
         }
      }
   }

   public void setPlaybackLimits(int firstFrame, int lastFrame) {
      if (hyperImage_ != null) {
         try {
            JavaUtils.setRestrictedFieldValue(null, Animator.class,
                    "firstFrame", firstFrame);
            JavaUtils.setRestrictedFieldValue(null, Animator.class,
                    "lastFrame", lastFrame);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.showError(ex);
         }
      }
   }

   public double getPlaybackFPS() {
      return Animator.getFrameRate();
   }

   public String getSummaryComment() {
      return imageCache_.getComment();
   }

   public void setSummaryComment(String comment) {
      imageCache_.setComment(comment);
   }

   void setImageComment(String comment) {
      imageCache_.setImageComment(comment, getCurrentMetadata());
   }

   String getImageComment() {
      try {
         return imageCache_.getImageComment(getCurrentMetadata());
      } catch (NullPointerException ex) {
         return "";
      }
   }

   public boolean isDiskCached() {
      MMImageCache imageCache = imageCache_;
      if (imageCache == null)
         return false;
      else
         return imageCache.getDiskLocation() != null;
   }

   public void show() {
      hyperImage_.show();
      hyperImage_.getWindow().toFront();
   }

   public boolean isComposite() {
      return hyperImage_ instanceof CompositeImage;
   }

   // CHANNEL SECTION ////////////
   public int getNumChannels() {
      return hyperImage_.getNChannels();
   }

   public int getNumGrayChannels() {
      return numComponents_ * getNumChannels();
   }

   public String[] getChannelNames() {
      if (isComposite()) {
         int nChannels = getNumGrayChannels();
         String[] chanNames = new String[nChannels];
         for (int i = 0; i < nChannels; ++i) {
            try {
               chanNames[i] = imageCache_.getDisplayAndComments().getJSONArray("Channels").getJSONObject(i).getString("Name");
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
         return chanNames;
      } else {
         return null;
      }
   }

   public void setChannelVisibility(int channelIndex, boolean visible) {
      if (!isComposite()) {
         return;
      }
      CompositeImage ci = (CompositeImage) hyperImage_;
      ci.getActiveChannels()[channelIndex] = visible;
      updateAndDraw();
   }

   public int[] getChannelHistogram(int channelIndex) {
      if (hyperImage_ == null || hyperImage_.getProcessor() == null) {
         return null;
      }
      if (hyperImage_.isComposite()
              && ((CompositeImage) hyperImage_).getMode()
              == CompositeImage.COMPOSITE) {
         ImageProcessor ip = ((CompositeImage) hyperImage_).getProcessor(channelIndex + 1);
         if (ip == null) {
            return null;
         }
         return ip.getHistogram();
      } else {
         if (hyperImage_.getChannel() == (channelIndex + 1)) {
            return hyperImage_.getProcessor().getHistogram();
         } else {
            return null;
         }
      }
   }

   public int getChannelMax(int channelIndex) {
      return channelSettings_[channelIndex].max;
   }

   public int getChannelMin(int channelIndex) {
      return channelSettings_[channelIndex].min;
   }

   public double getChannelGamma(int channelIndex) {
      return channelSettings_[channelIndex].gamma;
   }

   public Color getChannelColor(int channelIndex) {
      return channelSettings_[channelIndex].color;
   }

   private void readChannelSettingsFromCache(boolean updateDisplay) {
      try {
         JSONArray channelsArray = imageCache_.getDisplayAndComments().getJSONArray("Channels");
         for (int i = 0; i < channelSettings_.length; ++i) {
            try {
               JSONObject channel = channelsArray.getJSONObject(i);
               channelSettings_[i].color = new Color(channel.getInt("Color"));
               channelSettings_[i].min = channel.getInt("Min");
               channelSettings_[i].max = channel.getInt("Max");
               channelSettings_[i].gamma = channel.getDouble("Gamma");
               if (updateDisplay) {
                  setChannelDisplaySettings(i, channelSettings_[i]);
               }
            } catch (JSONException ex) {
               //ReportingUtils.logError(ex);
            }
         }
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void writeChannelSettingsToCache(int channelIndex) {
      try {
         JSONObject jsonSetting = imageCache_.getDisplayAndComments().
                 getJSONArray("Channels").
                 getJSONObject(channelIndex);
         ChannelDisplaySettings setting = channelSettings_[channelIndex];
         jsonSetting.put("Color", setting.color.getRGB());
         jsonSetting.put("Gamma", setting.gamma);
         jsonSetting.put("Min", setting.min);
         jsonSetting.put("Max", setting.max);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void setChannelWithoutUpdate(int channel) {
      if (hyperImage_ != null) {
         int z = hyperImage_.getSlice();
         int t = hyperImage_.getFrame();

         hyperImage_.setPositionWithoutUpdate(channel, z, t);
      }
   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      double gamma;
      if (channelSettings_ == null) {
         gamma = 1.0;
      } else {
         gamma = channelSettings_[channel].gamma;
      }

      setChannelLut(channel, new Color(rgb), gamma);
      writeChannelSettingsToCache(channel);
   }

   public void setChannelGamma(int channel, double gamma) {
      setChannelLut(channel, channelSettings_[channel].color, gamma);
      writeChannelSettingsToCache(channel);
   }

   public void setChannelDisplaySettings(int channel,
           ChannelDisplaySettings settings) {
      setChannelLut(channel, settings.color, settings.gamma);
      setChannelDisplayRange(channel, settings.min, settings.max);
      channelSettings_[channel] = settings;
   }

   public ChannelDisplaySettings getChannelDisplaySettings(int channel) {
      return channelSettings_[channel];
   }

   public void setChannelLut(int channel, Color color, double gamma) {
      // Note: both hyperImage_ and channelSettings_ can be
      // null when this function is called
      // null pointer exception will ensue!
      if (hyperImage_ == null) {
         return;
      }
      LUT lut = ImageUtils.makeLUT(color, gamma, 8);
      if (hyperImage_.isComposite()) {
         CompositeImage ci = (CompositeImage) hyperImage_;
         setChannelWithoutUpdate(channel + 1);
         ci.setChannelColorModel(lut);
      } else {
         hyperImage_.getProcessor().setColorModel(lut);
      }
      updateAndDraw();

      channelSettings_[channel].color = color;
      channelSettings_[channel].gamma = gamma;
   }

   public void setChannelDisplayRange(int channel, int min, int max) {
      if (hyperImage_ == null) {
         return;
      }
      setChannelWithoutUpdate(channel + 1);
      hyperImage_.updateImage();
      hyperImage_.setDisplayRange(min, max);
      updateAndDraw();
      channelSettings_[channel].min = min;
      channelSettings_[channel].max = max;

      writeChannelSettingsToCache(channel);
   }
}
