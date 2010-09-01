package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.plugin.Animator;
import ij.process.ImageStatistics;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class MMVirtualAcquisition implements AcquisitionInterface {
   private final String dir_;
   private final String name_;
   MMImageCache imageCache_;
   private int numChannels_;
   private int depth_;
   private int numFrames_;
   private int height_;
   private int numSlices_;
   private int width_;
   private int numComponents_ = 1;
   private boolean initialized_;
   private ImagePlus hyperImage_;
   private Map<String,String>[] displaySettings_;
   private AcquisitionVirtualStack virtualStack_;
   private String pixelType_;
   private ImageFileManagerInterface imageFileManager_ = null;
   private Map<String,String> summaryMetadata_ = null;
   private final boolean newData_;
   private Map<String,String> systemMetadata_ = null;
   private int numGrayChannels_;
   private final boolean diskCached_;
   private AcquisitionEngine eng_;


   MMVirtualAcquisition(String name, String dir, boolean newData, boolean virtual) {
      name_ = name;
      dir_ = dir;
      newData_ = newData;
      diskCached_ = virtual;
   }

   public void setDimensions(int frames, int channels, int slices) throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");
      }
      if (summaryMetadata_ == null) {
         summaryMetadata_ = new HashMap<String,String>();
      }
      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      displaySettings_ = new Map[channels];
      for (int i = 0; i < channels; ++i) {
         displaySettings_[i] = new HashMap<String,String>();
      }

      MDUtils.put(summaryMetadata_,"Acquisition-Channels",numChannels_);
      MDUtils.put(summaryMetadata_,"Acquisition-Slices",numSlices_);
      MDUtils.put(summaryMetadata_,"Acquisition-Frames",numFrames_);
   }

   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {
      width_ = width;
      height_ = height;
      depth_ = depth;
      int type = 0;

      if (depth_ == 1)
         type = ImagePlus.GRAY8;
      if (depth_ == 2)
         type = ImagePlus.GRAY16;
      if (depth_ == 4)
         type = ImagePlus.COLOR_RGB;
      if (depth_ == 8)
         type = 64;
      if ((depth_ == 1) || (depth_ == 2))
         numComponents_ = 1;
      else if ((depth_ == 4 || depth_ == 8))
         numComponents_ = 3;

      try {
         MDUtils.setWidth(summaryMetadata_, width);
         MDUtils.setHeight(summaryMetadata_, height);
         MDUtils.setImageType(summaryMetadata_, type);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public int getChannels() {
      return numChannels_;
   }

   public int getDepth() {
      return depth_;
   }

   public int getFrames() {
      return numFrames_;
   }

   public int getHeight() {
      return height_;
   }

   public int getSlices() {
      return numSlices_;
   }

   public int getWidth() {
      return width_;
   }

   public boolean isInitialized() {
      return initialized_;
   }

   public void close() {
      //compositeImage_.hide();
      initialized_ = false;
      if (imageFileManager_ != null)
         imageFileManager_.finishWriting();
   }

   public void closeImage5D() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public AcquisitionData getAcqData() {
      throw new UnsupportedOperationException("Not supported.");
   }

   public String getProperty(String propertyName) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getProperty(int frame, int channel, int slice, String propName) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public boolean hasActiveImage5D() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void initialize() throws MMScriptException {
      if (diskCached_)
         imageFileManager_ = new DefaultImageFileManager(dir_, newData_,
                 summaryMetadata_, systemMetadata_);
      imageCache_ = new MMImageCache(imageFileManager_);
      if (!newData_) {
         try {
            summaryMetadata_ = imageFileManager_.getSummaryMetadata();
            width_ = MDUtils.getWidth(summaryMetadata_);
            height_ = MDUtils.getHeight(summaryMetadata_);
            pixelType_ = MDUtils.getPixelType(summaryMetadata_);
            numSlices_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Slices");
            numFrames_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Frames");
            numChannels_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Channels");
            numComponents_ = MDUtils.getNumberOfComponents(summaryMetadata_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      imageCache_.setAcquisitionMetadata(summaryMetadata_);
      numGrayChannels_ = numComponents_ * numChannels_;
      virtualStack_ = new AcquisitionVirtualStack(width_, height_, null,
              imageCache_, numGrayChannels_ * numSlices_ * numFrames_);
      try {
         virtualStack_.setType(MDUtils.getSingleChannelType(summaryMetadata_));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      initialized_ = true;
   }

   public void insertImage(Object pixels, int frame, int channel, int slice)
           throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private void updateTitle() {
      String status = "";
      if (newData_) {
         if (acquisitionIsRunning()) {
            if (!abortRequested()) {
               if (isPaused()) {
                  status = "Paused";
               } else {
                  status = "Running";
               }
            } else {
               status = "Interrupted";
            }
         } else {
            status = "Finished";
         }
      } else {
         status = "On disk";
      }
      hyperImage_.getWindow().setTitle(new File(dir_).getName() + " (" + status + ")");
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {

      virtualStack_.insertImage(taggedImg);

      if (hyperImage_ == null) {
         show();
      }

      updateTitle();
      Map<String,String> md = taggedImg.tags;
      
      if (numChannels_ > 1) {
         ((CompositeImage) hyperImage_).setChannelsUpdated();
      }

      if (hyperImage_.getFrame() == 1) {
         int middleSlice = 1 + hyperImage_.getNSlices() / 2;

         try {
            ImageStatistics stat = hyperImage_.getStatistics();
            if (MDUtils.isRGB(taggedImg)) {
               for (int i=1;i<=numGrayChannels_;++i) {
                  (hyperImage_).setPosition(i,MDUtils.getFrameIndex(taggedImg.tags), MDUtils.getFrameIndex(taggedImg.tags));
                  hyperImage_.setDisplayRange(stat.min,stat.max);
                  hyperImage_.updateAndDraw();
               }
            } else {
                  double min = hyperImage_.getDisplayRangeMin();
                  double max = hyperImage_.getDisplayRangeMax();
                  if (hyperImage_.getSlice() == 0) {
                     min = Double.MAX_VALUE;
                     max = Double.MAX_VALUE;
                  }
               hyperImage_.setDisplayRange(Math.min(min,stat.min), Math.max(max,stat.max));
               hyperImage_.updateAndDraw();
            }
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
         
      }

      try {
        // if ((hyperImage_.getFrame() - 1) > (MDUtils.getFrame(md) - 2)) {
            hyperImage_.setPosition(1 + MDUtils.getChannelIndex(md), 1 + MDUtils.getSliceIndex(md), 1 + MDUtils.getFrameIndex(md));
       //  }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   boolean pause() {
      if (eng_.isPaused())
         eng_.setPause(false);
      else
         eng_.setPause(true);
      updateTitle();
      return (eng_.isPaused());
   }

   void abort() {
      eng_.abortRequest();
      updateTitle();
   }

   public void setEngine(AcquisitionEngine eng) {
      eng_ = eng;
   }

   public boolean acquisitionIsRunning() {
      return eng_.isAcquisitionRunning();
   }

   public boolean abortRequested() {
      return eng_.abortRequested();
   }

   private boolean isPaused() {
      return eng_.isPaused();
   }

   private class ImagePlusExpandable extends ImagePlus {

      private ImagePlusExpandable(String dir_, AcquisitionVirtualStack virtualStack_) {
         super(dir_, virtualStack_);
      }

      public void expandFrames(int n) {
         this.nFrames = n;
      }
   }

   public void show() {
      ImagePlus imgp = new ImagePlusExpandable(dir_, virtualStack_);
      virtualStack_.setImagePlus(imgp);
      imgp.setDimensions(numGrayChannels_, numSlices_, numFrames_);
      if (numGrayChannels_ > 1) {
         hyperImage_ = new CompositeImage(imgp, CompositeImage.COMPOSITE);
      } else {
         hyperImage_ = imgp;
         imgp.setOpenAsHyperStack(true);
      }
      if (numGrayChannels_ == numChannels_)
         updateChannelColors();
      hyperImage_.show();
      ImageWindow win = hyperImage_.getWindow();
      HyperstackControls hc = new HyperstackControls(this, win);
      win.add(hc);
      hyperImage_.addImageListener(hc);
      win.pack();

      if (!newData_) {
         for (Map<String,String> md:imageFileManager_.getImageMetadata()) {
            virtualStack_.rememberImage(md);
         }
         ((CompositeImage) hyperImage_).setChannelsUpdated();
         hyperImage_.updateAndDraw();
      }
      updateTitle();
   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      displaySettings_[channel].put("ChannelColor", String.format("%d", rgb));
   }

   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      displaySettings_[channel].put("ChannelContrastMin", String.format("%d", min));
      displaySettings_[channel].put("ChannelContrastMax", String.format("%d", max));
   }

   public void setChannelName(int channel, String name) throws MMScriptException {
      displaySettings_[channel].put("ChannelName", name);
   }

   public Map<String,String> getCurrentMetadata() {
      int index = getCurrentFlatIndex();
      return virtualStack_.getTaggedImage(index).tags;
   }

   private int getCurrentFlatIndex() {
      return hyperImage_.getCurrentSlice();
   }

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public void setComment(String comment) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setProperty(String propertyName, String value) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setProperty(int frame, int channel, int slice, String propName, String value) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setRootDirectory(String dir) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setSystemProperties(Map<String,String> md) throws MMScriptException {
      systemMetadata_ = md;
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported.");
   }

   public boolean windowClosed() {
      return false;
   }

   void showFolder() {
      if (dir_.length() != 0) {
         try {
            if (JavaUtils.isWindows()) {
               Runtime.getRuntime().exec("Explorer /n,/select," + dir_);
            } else if (JavaUtils.isMac()) {
               Runtime.getRuntime().exec("open " + dir_);
            }
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   private void updateChannelColors() {
      if (hyperImage_ instanceof CompositeImage) {
         if (displaySettings_ != null) {
            CompositeImage compositeImage = (CompositeImage) hyperImage_;
            for (int channel = 0; channel < compositeImage.getNChannels(); ++channel) {
               int color = Integer.parseInt(displaySettings_[channel].get("ChannelColor"));
               Color col = new Color(color);
               compositeImage.setChannelLut(compositeImage.createLutFromColor(col), 1 + channel);
            }
         }
      }
   }

   public void setSummaryProperties(Map<String,String> md) {
      summaryMetadata_.putAll(md);
   }

   public void setSystemState(Map<String,String> md) {
      systemMetadata_ = md;
   }

   void setPlaybackFPS(double fps) {
      if (hyperImage_ != null) {
         try {
            JavaUtils.setRestrictedFieldValue(null, Animator.class, "animationRate", (double) fps);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.showError(ex);
         }
      }
   }

   double getPlaybackFPS() {
      return Animator.getFrameRate();
   }
}
