package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ImageStatistics;
import java.awt.Color;
import java.io.IOException;
import mmcorej.Metadata;
import org.json.JSONObject;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.NumberUtils;
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
   private boolean initialized_;
   private ImagePlus hyperImage_;
   private Metadata[] displaySettings_;
   private AcquisitionVirtualStack virtualStack_;
   private int type_;
   private ImageFileManagerInterface imageFileManager_;
   private Metadata summaryMetadata_ = null;
   private final boolean newData_;
   private Metadata systemMetadata_ = null;

   public MMVirtualAcquisition(String name, String dir, boolean newData) {
      name_ = name;
      dir_ = dir;
      newData_ = newData;
   }

   public void setDimensions(int frames, int channels, int slices) throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");
      }
      if (summaryMetadata_ == null) {
         summaryMetadata_ = new Metadata();
      }
      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      displaySettings_ = new Metadata[channels];
      for (int i = 0; i < channels; ++i) {
         displaySettings_[i] = new Metadata();
      }

      summaryMetadata_.put("Channels", NumberUtils.intToCoreString(numChannels_));
      summaryMetadata_.put("Slices", NumberUtils.intToCoreString(numSlices_));
      summaryMetadata_.put("Frames", NumberUtils.intToCoreString(numFrames_));
   }

   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {
      width_ = width;
      height_ = height;
      depth_ = depth;

      if (depth_ == 1) {
         type_ = ImagePlus.GRAY8;
      } else if (depth_ == 2) {
         type_ = ImagePlus.GRAY16;
      } else if (4 == depth_) {
         type_ = ImagePlus.COLOR_RGB;
      } else {
         depth_ = 0;
         throw new MMScriptException("Unsupported pixel depth");
      }

      summaryMetadata_.put("Width", NumberUtils.intToCoreString(width_));
      summaryMetadata_.put("Height", NumberUtils.intToCoreString(height_));
      summaryMetadata_.put("Type", NumberUtils.intToCoreString(type_));
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
      imageFileManager_ = new DefaultImageFileManager(dir_, newData_, summaryMetadata_, systemMetadata_);
      imageCache_ = new MMImageCache(imageFileManager_);
      if (!newData_) {
         summaryMetadata_ = imageFileManager_.getSummaryMetadata();
         width_ = summaryMetadata_.getIntProperty("Width");
         height_ = summaryMetadata_.getIntProperty("Height");
         type_ = summaryMetadata_.getIntProperty("Type");
         numSlices_ = summaryMetadata_.getIntProperty("Slices");
         numFrames_ = summaryMetadata_.getIntProperty("Frames");
         numChannels_ = summaryMetadata_.getIntProperty("Channels");
      }
      virtualStack_ = new AcquisitionVirtualStack(width_, height_, null, dir_, imageCache_, numChannels_ * numSlices_ * numFrames_);
      virtualStack_.setType(type_);
      initialized_ = true;
   }

   public void insertImage(Object pixels, int frame, int channel, int slice) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {

      virtualStack_.insertImage(taggedImg);
      if (hyperImage_ == null) {
         show();
      }

      if (numChannels_ > 1) {
         ((CompositeImage) hyperImage_).setChannelsUpdated();
      }

      if (hyperImage_.getFrame() == 1) {
         int middleSlice = 1 + hyperImage_.getNSlices() / 2;
         if (hyperImage_.getSlice() == middleSlice) {
            ImageStatistics stat = hyperImage_.getStatistics();
            hyperImage_.setDisplayRange(stat.min, stat.max);
            hyperImage_.updateAndDraw();
         }
      }
      if ((hyperImage_.getFrame() - 1) > (taggedImg.md.getFrame() - 2)) {
         hyperImage_.setPosition(1 + taggedImg.md.getChannelIndex(), 1 + taggedImg.md.getSlice(), 1 + taggedImg.md.getFrame());
      }
   }

   public void show() {

      
      ImagePlus imgp = new ImagePlus(dir_, virtualStack_);
      virtualStack_.setImagePlus(imgp);
      imgp.setDimensions(numChannels_, numSlices_, numFrames_);
      if (numChannels_ > 1) {
         hyperImage_ = new CompositeImage(imgp, CompositeImage.COMPOSITE);
      } else {
         hyperImage_ = imgp;
         imgp.setOpenAsHyperStack(true);
      }
      updateChannelColors();
      hyperImage_.show();
      ImageWindow win = hyperImage_.getWindow();
      HyperstackControls hc = new HyperstackControls(this);
      win.add(hc);
      win.pack();

      if (!newData_) {
         for (Metadata md:imageFileManager_.getImageMetadata()) {
            virtualStack_.rememberImage(md);
         }
         ((CompositeImage) hyperImage_).setChannelsUpdated();
         hyperImage_.updateAndDraw();
      }
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

   public Metadata getCurrentMetadata() {
      int index = getCurrentFlatIndex();
      return virtualStack_.getTaggedImage(index).md;
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

   public void setSummaryProperties(Metadata md) throws MMScriptException {
      summaryMetadata_ = md;
   }

   public void setSystemProperties(Metadata md) throws MMScriptException {
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

   public void setSystemState(Metadata md) throws MMScriptException {
      systemMetadata_ = md;
   }
}
