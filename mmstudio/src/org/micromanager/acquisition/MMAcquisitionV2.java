package org.micromanager.acquisition;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.Color;

import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.micromanager.api.AcquisitionInterface;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class MMAcquisitionV2 implements AcquisitionInterface {
   private int numFrames_ = 0;
   private int numChannels_ = 0;
   private int numSlices_ = 0;
   private int numPositions_ = 0;
   protected String name_;
   protected int width_;
   protected int height_;
   protected int depth_ = 1;
   private boolean initialized_ = false;
   private boolean show_ = true;
   private boolean diskCached_ = false;

   private String comment_ = "Acquisition from a script";
   private int[] channelColors_;
   private String[] channelNames_;

   protected Image5DWindow imgWin_;
   @SuppressWarnings("unused")
   private String rootDirectory_;
   //private AcquisitionData acqData_;
   private MMVirtualAcquisitionDisplay virtAcq_;
   
   public MMAcquisitionV2(String name, String dir) {
      this(name, dir, false, false);
   }

   public MMAcquisitionV2(String name, String dir, boolean show) {
      this(name, dir, show, false);
      virtAcq_.show(0);
      show_ = show;
   }

   public MMAcquisitionV2(String name, String dir, boolean show, boolean diskCached) {
      name_ = name;
      rootDirectory_ = dir;
      TaggedImageStorage imageFileManager;
      if (diskCached) imageFileManager = new TaggedImageStorageDiskDefault(dir);
      else imageFileManager = new TaggedImageStorageRam(null);

      MMImageCache imageCache = new MMImageCache(imageFileManager);
      virtAcq_ = new MMVirtualAcquisitionDisplay(dir, false, diskCached);
      virtAcq_.setCache(imageCache);
      if (show && diskCached) {
         try {
            virtAcq_.initialize();
            virtAcq_.show(0);
            initialized_ = true;
         } catch (MMScriptException ex) {
            ReportingUtils.showError( ex);
         }
         
      }
      show_ = show;
      diskCached_ = diskCached;
   }
  
   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {   
      if (initialized_)
         throw new MMScriptException("Can't image change dimensions - the acquisition is already initialized");
      width_ = width;
      height_ = height;
      depth_ = depth;
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
      setDimensions(frames, channels, slices, 0);
   }

   public void setDimensions(int frames, int channels, int slices, int positions) throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");

      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      numPositions_ = positions;

      channelColors_ = new int[numChannels_];
      channelNames_ = new String[numChannels_];
      for (Integer i = 0; i<numChannels_; i++) {
         channelColors_[i] = Color.WHITE.getRGB();
         channelNames_[i] = i.toString();
      }
   }

   public void setRootDirectory(String dir) throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Can't change root directory - the acquisition is already initialized");
      rootDirectory_ = dir;
   }

   
   public void initialize() throws MMScriptException {

      JSONObject tags = new JSONObject();
      try {
         tags.put("Width", width_);
         tags.put("Height", height_);
         tags.put("Slices", numSlices_);
         tags.put("Frames", numFrames_);
         tags.put("Channels", numChannels_);
         tags.put("Positions", numPositions_);
         tags.put("NumComponents", 1);
         tags.put("Comment", comment_);
         tags.put("MetadataVersion", 10);
         tags.put("Source", "Micro-Manager");
         //tags.put("PixelSize_um", core_.getPixelSizeUm());
         tags.put("PixelAspect", 1.0);
         tags.put("GridColumn", 0);
         tags.put("GridRow", 0);
         //tags.put("ComputerName", getComputerName());
         tags.put("UserName", System.getProperty("user.name"));
         if (depth_ == 1)
            tags.put("PixelType", "GRAY8");
         else if (depth_ == 2)
            tags.put("PixelType", "GRAY16");
      } catch (JSONException ex) {
         Logger.getLogger(MMAcquisitionV2.class.getName()).log(Level.SEVERE, null, ex);
      }

      setDefaultChannelTags(tags);
      
      virtAcq_.imageCache_.setSummaryMetadata(tags);

      virtAcq_.imageCache_.setDisplaySettings(MDUtils.getDisplaySettingsFromSummary(tags));

      virtAcq_.initialize();
      if (show_)
         virtAcq_.show(0);

      initialized_ = true;
   }

    private void setDefaultChannelTags(JSONObject md) {
      JSONArray channelColors = new JSONArray();
      JSONArray channelNames = new JSONArray();
      JSONArray channelMaxes = new JSONArray();
      JSONArray channelMins = new JSONArray();

      for (Integer i=0; i<numChannels_; i++) {
         channelColors.put(channelColors_[i]);
         channelNames.put(channelNames_[i]);
         try {
            channelMaxes.put(255);
            channelMins.put(0);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
      try {
         md.put("ChColors", channelColors);
         md.put("ChNames", channelNames);
         md.put("ChContrastMax", channelMaxes);
         md.put("ChContrastMin", channelMins);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   public void insertImage(Object pixels, int frame, int channel, int slice) throws MMScriptException {
      if (!initialized_)
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
            
      // update acq data
      try {
         JSONObject tags = new JSONObject();
         tags.put("Time", MDUtils.getCurrentTime());
         tags.put("Frame", frame);
         tags.put("ChannelIndex", channel);
         tags.put("Channel", channelNames_[channel]);
         tags.put("Slice", slice);
         tags.put("PositionIndex", 0);
         tags.put("Width", width_);
         tags.put("Height", height_);
         if (depth_ == 1)
            tags.put("PixelType", "GRAY8");
         else if (depth_ == 2)
            tags.put("PixelType", "GRAY16");
         TaggedImage tg = new TaggedImage(pixels,tags);
         insertImage(tg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }
   
   public void close() {
      if (virtAcq_.acquisitionIsRunning())
         virtAcq_.abort();
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
      if (isInitialized())
      try {
         virtAcq_.imageCache_
                 .getSummaryMetadata()
                 .put("COMMENT", comment_);
      } catch (JSONException e) {
         throw new MMScriptException("Failed to set Comment");
      } else 
         comment_ = comment;

   }
   
   public AcquisitionData getAcqData() {
	   return null;

   }

   public void setChannelName(int channel, String name) throws MMScriptException {
      if (isInitialized()) {
         try {
            virtAcq_.imageCache_
                       .getDisplaySettings()
                       .getJSONArray("Channels")
                       .getJSONObject(channel)
                       .put("Name", name);
            virtAcq_.imageCache_
                    .getSummaryMetadata()
                    .getJSONArray("ChNames")
                    .put(channel,name);
         } catch (JSONException e) {
            throw new MMScriptException("Problem setting Channel name");
         }
      }
      else {
         if (channelNames_ == null)
            throw new MMScriptException("Dimensions need to be set first");
         if (channelNames_.length > channel)
            channelNames_[channel] = name;
      }
   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
       if (isInitialized())
          virtAcq_.setChannelColor(channel, rgb);
       else {
         if (channelNames_ == null)
            throw new MMScriptException("Dimensions need to be set first");
          if (channelColors_.length > channel)
             channelColors_[channel] = rgb;
       }
   }
   
   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
         virtAcq_.setChannelDisplayRange(channel, min, max);
   }

   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      // TODO
      /*
      try {
         DisplaySettings[] settings = acqData_.setChannelContrastBasedOnFrameAndSlice(frame, slice);
         if (imgWin_ != null) {
            for (int i=0; i<settings.length; i++)
               imgWin_.getImage5D().setChannelMinMax(i+1, settings[i].min, settings[i].max);
         }
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }

       */
   }

   public void setProperty(String propertyName, String value) throws MMScriptException {
      if (isInitialized()) {
         try {
            virtAcq_.imageCache_
                    .getSummaryMetadata()
                    .put(propertyName, value);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to set property: " + propertyName);
         }
      } else
         // TODO:
      ;
   }

   public String getProperty(String propertyName) throws MMScriptException {
      if (isInitialized()) {
         try {
            return virtAcq_.imageCache_
                    .getSummaryMetadata()
                    .getString(propertyName);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to get property: " + propertyName);
         }
      } else
         // TODO
         ;
      return "";
   }
   
   public void setProperty(int frame, int channel, int slice, String propName,
         String value) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_.getImage(channel, slice, frame, 0).tags;
            tags.put(propName, value);
            //acqData_.setImageValue(frame, channel, slice, propName, value);
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      } else
         // TODO
         ;
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      // TODO
      /*
      try {
         acqData_.setSystemState(frame, channel, slice, state);
      } catch (MMAcqDataException e) {
         throw new MMScriptException(e);
      }
       *
       */
   }
   
   
   public String getProperty(int frame, int channel, int slice, String propName
	         ) throws MMScriptException {
      // TODO
      /*
	      try {
	         return acqData_.getImageValue(frame, channel, slice, propName);
	      } catch (MMAcqDataException e) {
	         throw new MMScriptException(e);
	      }
       *
       */
      return "";
	   }
   
   protected Image5DWindow createImage5DWindow(Image5D img5d) {
	   return new Image5DWindow(img5d);
	   
   }
   
   public boolean hasActiveImage5D() {
	   return ! (this.imgWin_ == null);
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {
      virtAcq_.imageCache_.putImage(taggedImg);
      if (show_)
         virtAcq_.insertImage(taggedImg);
   }

   public void setSummaryProperties(JSONObject md) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public boolean windowClosed() {
      return imgWin_.isClosed();
   }

   public void setSystemState(JSONObject md) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setEngine(AcquisitionEngine eng) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setCache(MMImageCache imageCache) {
      virtAcq_.setCache(imageCache);
   }

}

