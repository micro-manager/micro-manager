///////////////////////////////////////////////////////////////////////////////
//FILE:          MMAcquisitionV2.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, November 2010
//
// COPYRIGHT:    University of California, San Francisco, 2010
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

/*
 * This class is used to execute most of the acquisition and image display
 * functionality in the ScriptInterface
 */


package org.micromanager.acquisition;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.Color;
import java.io.File;
import java.util.Iterator;

import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.micromanager.api.AcquisitionInterface;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.TaggedImageStorage;
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
   private long startTimeMs_;
   private boolean show_ = true;
   private boolean diskCached_ = false;

   private String comment_ = "Acquisition from a script";
   private int[] channelColors_;
   private String[] channelNames_;

   //protected Image5DWindow imgWin_;
   @SuppressWarnings("unused")
   private String rootDirectory_;
   private MMVirtualAcquisitionDisplay virtAcq_;
   
   public MMAcquisitionV2(String name, String dir) throws MMScriptException {
      this(name, dir, false, false, false);
   }

   public MMAcquisitionV2(String name, String dir, boolean show) throws MMScriptException {
      this(name, dir, show, false, false);
      virtAcq_.show(0);
      show_ = show;
   }

   public MMAcquisitionV2(String name, String dir, boolean show, 
           boolean diskCached, boolean existing) throws MMScriptException {
      name_ = name;
      rootDirectory_ = dir;
      TaggedImageStorage imageFileManager;
      if (!existing) {
         if (new File(dir).exists()) {
            name = generateRootName(name, rootDirectory_);
         }
      }
      if (diskCached) {
         String dirname = dir + File.separator + name;
         File f = new File(dirname);
         // this is quite stupid but a way to find out if this name was generated upstream
         if (!f.isDirectory())
            dirname = dirname.substring(0, dirname.length() - 2);

         imageFileManager = new TaggedImageStorageDiskDefault(dirname,
              !existing, new JSONObject());
      }
      else
         imageFileManager = new TaggedImageStorageRam(null);

      MMImageCache imageCache = new MMImageCache(imageFileManager);
      virtAcq_ = new MMVirtualAcquisitionDisplay(dir + File.separator + name, false, diskCached);
      virtAcq_.setCache(imageCache);
      
      if (show && diskCached && existing) {
         try {
            virtAcq_.initialize();
            virtAcq_.show(0);
            // start loading all other images in a background thread
            PreLoadDataThread t = new PreLoadDataThread(virtAcq_);
            new Thread(t).start();
            initialized_ = true;
         } catch (MMScriptException ex) {
            ReportingUtils.showError( ex);
         }
      }
      
      show_ = show;
      diskCached_ = diskCached;
   }


   static private String generateRootName(String name, String baseDir) {
      // create new acquisition directory
      int suffixCounter = 0;
      String testPath;
      File testDir;
      String testName;
      do {
         testName = name + "_" + suffixCounter;
         testPath = baseDir + File.separator + testName;
         suffixCounter++;
         testDir = new File(testPath);
      } while (testDir.exists());
      return testName;
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
      for (Integer i = 0; i<numChannels_; i++) {
         channelColors_[i] = Color.WHITE.getRGB();
      }

      channelNames_ = new String[numChannels_];
      for (Integer i = 0; i<numChannels_; i++) {
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
         tags.put("StartTime", MDUtils.getCurrentTime());
         startTimeMs_ = System.currentTimeMillis();
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

   public void insertImage(Object pixels, int frame, int channel, int slice)
           throws MMScriptException {
      insertImage(pixels, frame, channel, slice, 0);
   }

   public void insertImage(Object pixels, int frame, int channel, int slice, int position) throws MMScriptException {
      if (!initialized_)
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
            
      // update acq data
      try {
         JSONObject tags = new JSONObject();
         tags.put("Frame", frame);
         tags.put("ChannelIndex", channel);
         tags.put("Channel", channelNames_[channel]);
         tags.put("Slice", slice);
         tags.put("PositionIndex", position);
         // the following influences the format data will be saved!
         if (numPositions_ > 1)
            tags.put("PositionName", "Pos" + position);
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

   public void insertTaggedImage(TaggedImage taggedImg, int frame, int channel, int slice)
           throws MMScriptException {
      if (!initialized_)
         throw new MMScriptException("Acquisition data must be initialized before inserting images");

      // update acq data
      try {
         JSONObject tags = taggedImg.tags;
         tags.put("Frame", frame);
         tags.put("ChannelIndex", channel);
         tags.put("Slice", slice);
         tags.put("PositionIndex", 0);
         //tags.put("Width", width_);
         //tags.put("Height", height_);
         if (depth_ == 1)
            tags.put("PixelType", "GRAY8");
         else if (depth_ == 2)
            tags.put("PixelType", "GRAY16");
         insertImage(taggedImg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {
      insertImage(taggedImg, show_);
   }

   public void insertImage(TaggedImage taggedImg, boolean updateDisplay) throws MMScriptException {
      try {
         int channel = taggedImg.tags.getInt("ChannelIndex");
         taggedImg.tags.put("Channel", channelNames_[channel]);
         long elapsedTimeMillis = System.currentTimeMillis() - startTimeMs_;
         taggedImg.tags.put("ElapsedTime-ms", elapsedTimeMillis);
         taggedImg.tags.put("Time", MDUtils.getCurrentTime());
      } catch (JSONException ex) {
         throw new MMScriptException(ex);
      }
      virtAcq_.imageCache_.putImage(taggedImg);
      if (updateDisplay)
         virtAcq_.showImage(taggedImg);
   }
   
   public void close() {
      if (virtAcq_.acquisitionIsRunning())
         virtAcq_.abort();
      //virtAcq_.close();
   }

   public boolean isInitialized() {
      return initialized_;
   }
   
   public void closeImage5D() {
      close();
      virtAcq_.close();
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
            channelNames_[channel] = name;
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
       if (isInitialized()) {
          try {
          virtAcq_.setChannelColor(channel, rgb);
          virtAcq_.imageCache_
                    .getSummaryMetadata()
                    .getJSONArray("ChColors")
                    .put(channel,rgb);
          channelColors_[channel] = rgb;
          } catch (JSONException ex) {
             throw new MMScriptException(ex);
          }
       }
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

   /*
    * Set a property in summary metadata
    */
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

   /*
    * Get a property from the summary metadata
    */
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
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      } else
         // TODO
         ;
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_.getImage(channel, slice, frame, 0).tags;
            Iterator<String> iState = state.keys();
            while (iState.hasNext()) {
               String key = iState.next();
               tags.put(key, state.get(key));
            }
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      } else
         // TODO
         ;
   }
   
   
   public String getProperty(int frame, int channel, int slice, String propName
	         ) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_.getImage(channel, slice, frame, 0).tags;
            return tags.getString(propName);
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }
         
      } else {}
         return "";
	   }
   
   
   public boolean hasActiveImage5D() {
      return virtAcq_.windowClosed();
   }



   public void setSummaryProperties(JSONObject md) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_
                    .getSummaryMetadata();
            Iterator<String> iState = md.keys();
            while (iState.hasNext()) {
               String key = iState.next();
               tags.put(key, md.get(key));
            }
         } catch (Exception ex) {
            throw new MMScriptException (ex);
         }
      }
   }

   public boolean windowClosed() {
      if (virtAcq_ != null && ! virtAcq_.windowClosed())
         return false;
      return true;
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

