/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import java.awt.Color;
import java.awt.image.DirectColorModel;
import mmcorej.Metadata;
import org.json.JSONObject;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.MMScriptException;

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
   private Image5DWindow imgWin_;
   private Image5D img5d_;
   private Metadata [] displaySettings_;
   private AcquisitionVirtualStack virtualStack_;
   
   public MMVirtualAcquisition(String name, String dir) {
      name_ = name;
      dir_ = dir;
   }

   public void setDimensions(int frames, int channels, int slices) throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");
      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      displaySettings_ = new Metadata[channels];
      for (int i=0;i<channels;++i)
         displaySettings_[i] = new Metadata();
   }

   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {
      width_ = width;
      height_ = height;
      depth_ = depth;
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
      imgWin_ = null;
      initialized_ = false;
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
      int type;
      if (depth_ == 1)
         type = ImagePlus.GRAY8;
      else if (depth_ == 2)
         type = ImagePlus.GRAY16;
      else if (4 ==depth_)
         type = ImagePlus.COLOR_RGB;
      else {
         depth_ = 0;
         throw new MMScriptException("Unsupported pixel depth");
      }

      imageCache_ = new MMImageCache(dir_);
      virtualStack_ = new AcquisitionVirtualStack(width_, height_, null, dir_, imageCache_, numChannels_ * numSlices_ * numFrames_);

      initialized_ = true;
   }

   public void insertImage(Object pixels, int frame, int channel, int slice) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void insertImage(MMImageBuffer imgBuf) throws MMScriptException {
      int index = numChannels_ * numSlices_ * imgBuf.md.getFrame() + numChannels_ * imgBuf.md.getSlice() + imgBuf.md.getChannelIndex() + 1;
      virtualStack_.insertImage(index, imgBuf);
      if (img5d_ == null) {
         img5d_ = new Image5D(name_, virtualStack_, numChannels_, numSlices_, numFrames_, true);
         imgWin_ = new Image5DWindow(img5d_);
         if (numChannels_ == 1) {
            img5d_.setDisplayMode(ChannelControl.ONE_CHANNEL_GRAY);
         } else {
            img5d_.setDisplayMode(ChannelControl.OVERLAY);
         }

         for (int channel = 0; channel < numChannels_; ++channel) {
            int rgb = Integer.parseInt(displaySettings_[channel].get("ChannelColor"));
            if (imgWin_ != null) {
               if (imgWin_.getImage5D().getProcessor(channel + 1) instanceof ColorProcessor) {
                  imgWin_.getImage5D().setChannelColorModel(channel + 1, new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF));
               } else {
                  imgWin_.getImage5D().setChannelColorModel(channel + 1, ChannelDisplayProperties.createModelFromColor(new Color(rgb)));
               }
            }

            ChannelCalibration chcal = img5d_.getChannelCalibration(channel+1);
            chcal.setLabel(displaySettings_[channel].get("ChannelName"));
            img5d_.setChannelCalibration(channel+1, chcal);
         }
      }
      if ((img5d_.getCurrentFrame() - 1) > (imgBuf.md.getFrame() - 2)) {
         img5d_.setCurrentPosition(0, 0, imgBuf.md.getChannelIndex(), imgBuf.md.getSlice(), imgBuf.md.getFrame());
      }
      img5d_.updateAndRepaintWindow();
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
      // Do nothing for now.
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported.");
   }

   public boolean windowClosed() {
      return false;
   }

}
