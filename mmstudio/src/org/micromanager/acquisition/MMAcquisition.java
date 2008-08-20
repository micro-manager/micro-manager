package org.micromanager.acquisition;

import ij.ImagePlus;

import java.awt.Color;

import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.SummaryKeys;
import org.micromanager.utils.MMScriptException;

public class MMAcquisition {
   private int numFrames_;
   private int numChannels_;
   private int numSlices_;
   private String name_;
   private int width_;
   private int height_;
   private int depth_;
   private boolean initialized_ = false;
   private Image5DWindow imgWin_;
   private String rootDirectory_;
   private AcquisitionData acqData_;
   
   public MMAcquisition(String name, String dir) {
      numFrames_ = 0;
      numChannels_ = 0;
      numSlices_ = 0;
      name_ = name;
      imgWin_ = null;
      acqData_ = new AcquisitionData();
      try {
         acqData_.createNew(name_, dir, true);
      } catch (MMAcqDataException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      rootDirectory_ = new String();
   }

   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Can't image change dimensions - the acquisition is already initialized");
      width_ = width;
      height_ = height;
      depth_ = depth;
      try {
         acqData_.setImagePhysicalDimensions(width, height, depth);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }
   
   public void setDimensions(int frames, int channels, int slices) throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");
      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      
      try {
         acqData_.setDimensions(numFrames_, numChannels_, numSlices_);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }
   
   public void setRootDirectory(String dir) throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Can't change root directory - the acquisition is already initialized");
      rootDirectory_ = dir;
   }
   
   public void initialize() throws MMScriptException {
      int type;
      if (depth_ == 1)
         type = ImagePlus.GRAY8;
      else if (depth_ == 2)
         type = ImagePlus.GRAY16;
      else {
         depth_ = 0;
         throw new MMScriptException("Unsupported pixel depth");
      }

      Image5D img5d = new Image5D(name_, type, width_, height_, numChannels_, numSlices_, numFrames_, false);
      imgWin_ = new Image5DWindow(img5d);
      Color colors[];
      String names[];
      try {
         colors = acqData_.getChannelColors();
         names = acqData_.getChannelNames();
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
      
      // set-up colors, contrast and channel names
      for (int i=0; i<numChannels_; i++) {
         img5d.setChannelColorModel(i+1, ChannelDisplayProperties.createModelFromColor(colors[i]));
         ChannelCalibration chcal = img5d.getChannelCalibration(i+1);
         chcal.setLabel(names[i]);
         img5d.setChannelCalibration(i+1, chcal);
      }
      
      if (numChannels_ == 1)
         img5d.setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
      else
         img5d.setDisplayMode(ChannelControl.OVERLAY);

      initialized_ = true;
   }
      
   public void insertImage(Object pixels, int frame, int channel, int slice) throws MMScriptException {
      if (!initialized_)
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
            
      // update acq data
      try {
         acqData_.insertImage(pixels, frame, channel, slice);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
      
      // update display
      if (imgWin_ != null) {
         Image5D i5d = imgWin_.getImage5D();
         i5d.setPixels(pixels, channel+1, slice+1, frame+1);
         i5d.setCurrentPosition(0, 0, channel, slice, frame);
         imgWin_.setAcquisitionData(acqData_);
      }

   }
   
   public void close() {
      imgWin_ = null;
      initialized_ = false;
      try {
         acqData_.saveMetadata();
      } catch (MMAcqDataException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   public boolean isInitialized() {
      return initialized_;
   }
   
   public void closeImage5D() {
      if (initialized_) {
         imgWin_.dispose();
      }
   }

   public void setComment(String comment) throws MMScriptException {
      try {
         acqData_.setSummaryValue(SummaryKeys.COMMENT, comment);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }

   public void setChannelName(int channel, String name) throws MMScriptException {
      // TODO: update image window if present
      try {
         acqData_.setChannelName(channel, name);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }     
   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      try {
         acqData_.setChannelColor(channel, rgb);
         if (imgWin_ != null) {
            imgWin_.getImage5D().setChannelColorModel(channel+1, ChannelDisplayProperties.createModelFromColor(new Color(rgb))); 
         }
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }
   
   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      try {
         DisplaySettings ds = new DisplaySettings();
         ds.min = min;
         ds.max = max;
         acqData_.setChannelDisplaySetting(channel, ds);
         
         if (imgWin_ != null)
            imgWin_.getImage5D().setChannelMinMax(channel+1, min, max);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }

   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      try {
         DisplaySettings[] settings = acqData_.setChannelContrastBasedOnFrameAndSlice(frame, slice);
         if (imgWin_ != null) {
            for (int i=0; i<settings.length; i++)
               imgWin_.getImage5D().setChannelMinMax(i+1, settings[i].min, settings[i].max);
         }
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }

}
