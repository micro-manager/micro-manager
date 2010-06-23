package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.process.ColorProcessor;

import java.awt.Color;
import java.awt.image.DirectColorModel;

import org.json.JSONObject;
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
import org.micromanager.utils.ReportingUtils;

public class MMAcquisition {
   protected int numFrames_;
   private int numChannels_;
   private int numSlices_;
   protected String name_;
   protected int width_;
   protected int height_;
   protected int depth_;
   private boolean initialized_ = false;
   private boolean show_ = true;
   protected Image5DWindow imgWin_;
   @SuppressWarnings("unused")
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
         ReportingUtils.showError(e);
      }
      rootDirectory_ = new String();
   }

   public MMAcquisition(String name, String dir, boolean show) {
      this(name, dir);
      show_ = show;
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
   
   public int getWidth() {
	   return width_;
   }
   
   public int getHeight() {
	   return height_;
   }
   
   public int getDepth() {
	   return depth_;
   }
   
   public int getFrames() {
      return numFrames_;
   }

   public int getChannels() {
      return numChannels_;
   }

   public int getSlices() {
      return numSlices_;
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
      if (!show_) {
         initialized_ = true;
         return;
      }

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

      Color colors[];
      String names[];
      try {
         colors = acqData_.getChannelColors();
         if (colors == null) {
            colors = new Color[numChannels_];
            for (int i=0; i<numChannels_; i++)
               colors[i] = Color.WHITE;
         }
         names = acqData_.getChannelNames();
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
      
      // new function createImage5D(...) will be called here instead of following line; override with Image5DSnap?
      Image5D img5d = new Image5D(name_, type, width_, height_, numChannels_, numSlices_, numFrames_, false);
      imgWin_ = createImage5DWindow(img5d);

      // set-up colors, contrast and channel names
      for (int i=0; i<numChannels_; i++) {
         //N.B. this call to setChannelColorModel is very soon over-written inside setChannelColor
         if(img5d.getProcessor(i+1) instanceof ColorProcessor)
             img5d.setChannelColorModel(i + 1, new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF));
         else{
            img5d.setChannelColorModel(i+1, ChannelDisplayProperties.createModelFromColor(colors[i]));
         }
         ChannelCalibration chcal = img5d.getChannelCalibration(i+1);
         chcal.setLabel(names[i]);
         img5d.setChannelCalibration(i+1, chcal);
      }
      
      if (numChannels_ == 1)
         img5d.setDisplayMode(ChannelControl.ONE_CHANNEL_GRAY);
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
      if (imgWin_ != null && show_) {
         Image5D i5d = imgWin_.getImage5D();

         if (frame >= i5d.getNFrames()) {
            i5d.expandDimension(4, frame + 1, true);
         }
         i5d.setPixels(pixels, channel+1, slice+1, frame+1);

         if (i5d.getCurrentFrame() >= (frame - 1))
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
         ReportingUtils.showError(e);
      }
      acqData_ = null;
   }

   public boolean isInitialized() {
      return initialized_;
   }
   
   public void closeImage5D() {
      if ((imgWin_ != null) && initialized_) {
         imgWin_.close();
      }
   }

   public void setComment(String comment) throws MMScriptException {
      try {
         acqData_.setSummaryValue(SummaryKeys.COMMENT, comment);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }
   
   public AcquisitionData getAcqData() {
	   return acqData_;
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
            if( imgWin_.getImage5D().getProcessor(channel+1)instanceof ColorProcessor ){
                imgWin_.getImage5D().setChannelColorModel(channel+ 1, new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF));
            }else
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

   public void setProperty(String propertyName, String value) throws MMScriptException {
      try {
         acqData_.setSummaryValue(propertyName, value);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }

   public String getProperty(String propertyName) throws MMScriptException {
	   try {
		   return acqData_.getSummaryValue(propertyName);
	   } catch (MMAcqDataException e) {
		   throw new MMScriptException(e);
	   }
   }
   
   public void setProperty(int frame, int channel, int slice, String propName,
         String value) throws MMScriptException {
      try {
         acqData_.setImageValue(frame, channel, slice, propName, value);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      try {
         acqData_.setSystemState(frame, channel, slice, state);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
   }
   
   
   public String getProperty(int frame, int channel, int slice, String propName
	         ) throws MMScriptException {
	      try {
	         return acqData_.getImageValue(frame, channel, slice, propName);
	      } catch (MMAcqDataException e) {
	         throw new MMScriptException(e);
	      }
	   }
   
   protected Image5DWindow createImage5DWindow(Image5D img5d) {
	   return new Image5DWindow(img5d);
	   
   }
   
   public boolean hasActiveImage5D() {
	   return ! (this.imgWin_ == null);
   }

}

