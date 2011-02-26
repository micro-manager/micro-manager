///////////////////////////////////////////////////////////////////////////////
//FILE:          MMAcquisition.java
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

import java.awt.Color;
import java.io.File;
import java.util.Iterator;
import mmcorej.CMMCore;

import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.micromanager.MMStudioMainFrame;

import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class MMAcquisition {

   private int numFrames_ = 0;
   private int numChannels_ = 0;
   private int numSlices_ = 0;
   private int numPositions_ = 0;
   protected String name_;
   protected int width_ = 0;
   protected int height_ = 0;
   protected int depth_ = 1;
   private boolean initialized_ = false;
   private long startTimeMs_;
   private String comment_ = "Acquisition from a script";
   private String rootDirectory_;
   private VirtualAcquisitionDisplay virtAcq_;
   private final boolean existing_;
   private final boolean diskCached_;
   private final boolean show_;
   private JSONArray channelColors_ = new JSONArray();
   private JSONArray channelNames_ = new JSONArray();
   private JSONObject summary_ = new JSONObject();
   private final String NOTINITIALIZED = "Acquisition was not initialized";

   public MMAcquisition(String name, String dir) throws MMScriptException {
      this(name, dir, false, false, false);
   }

   public MMAcquisition(String name, String dir, boolean show) throws MMScriptException {
      this(name, dir, show, false, false);
   }

   public MMAcquisition(String name, String dir, boolean show,
           boolean diskCached, boolean existing) throws MMScriptException {
      name_ = name;
      rootDirectory_ = dir;
      show_ = show;
      existing_ = existing;
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
      if (initialized_) {
         throw new MMScriptException("Can't image change dimensions - the acquisition is already initialized");
      }
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
      if (initialized_) {
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");
      }

      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      numPositions_ = positions;
   }

   public void setRootDirectory(String dir) throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Can't change root directory - the acquisition is already initialized");
      }
      rootDirectory_ = dir;
   }

   public void initialize() throws MMScriptException {
      if (initialized_)
         throw new MMScriptException("Acquisition is already initialized");
      
      TaggedImageStorage imageFileManager;
      String name = name_;

      if (diskCached_) {
         String dirname = rootDirectory_ + File.separator + name;
         imageFileManager = new TaggedImageStorageDiskDefault(dirname,
                 !existing_, new JSONObject());
      } else {
         imageFileManager = new TaggedImageStorageRam(null);
      }


      MMImageCache imageCache = new MMImageCache(imageFileManager);

      if (!existing_) {
         createDefaultAcqSettings(name, imageCache);
      }

      virtAcq_ = new VirtualAcquisitionDisplay(imageCache, null);

      if (show_ && diskCached_ && existing_) {
         // start loading all other images in a background thread
         PreLoadDataThread t = new PreLoadDataThread(virtAcq_);
         new Thread(t).start();
      }
      if (show_) {
         virtAcq_.show();
      }

      initialized_ = true;
   }

   private void createDefaultAcqSettings(String name, MMImageCache imageCache) {
      if (new File(rootDirectory_).exists()) {
         name = generateRootName(name, rootDirectory_);
      }

      String keys[] = new String[summary_.length()];
      Iterator<String> it = summary_.keys();
      int i = 0;
      while (it.hasNext()) {
         keys[0] = it.next();
         i++;
      }
      
      try {
         JSONObject summaryMetadata = new JSONObject(summary_, keys);
         CMMCore core = MMStudioMainFrame.getInstance().getCore();

         summaryMetadata.put("BitDepth", core.getImageBitDepth());
         summaryMetadata.put("Channels", numChannels_);
         setDefaultChannelTags(summaryMetadata);
         summaryMetadata.put("Comment", comment_);
         summaryMetadata.put("Depth", core.getBytesPerPixel());
         summaryMetadata.put("Frames", numFrames_);
         summaryMetadata.put("GridColumn", 0);
         summaryMetadata.put("GridRow", 0);
         summaryMetadata.put("Height", height_);
         summaryMetadata.put("MetadataVersion", 10);
         summaryMetadata.put("MicroManagerVersion", MMStudioMainFrame.getInstance().getVersion());
         summaryMetadata.put("NumComponents", 1);
         summaryMetadata.put("Positions", numPositions_);
         summaryMetadata.put("Source", "Micro-Manager");
         summaryMetadata.put("PixelAspect", 1.0);
         summaryMetadata.put("PixelSize_um", core.getPixelSizeUm());
         if (depth_ == 1) {
            summaryMetadata.put("PixelType", "GRAY8");
         } else if (depth_ == 2) {
            summaryMetadata.put("PixelType", "GRAY16");
         }
         summaryMetadata.put("Slices", numSlices_);
         summaryMetadata.put("StartTime", MDUtils.getCurrentTime());
         summaryMetadata.put("UserName", System.getProperty("user.name"));
         summaryMetadata.put("Width", width_);
         startTimeMs_ = System.currentTimeMillis();
         imageCache.setSummaryMetadata(summaryMetadata);
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }
   }

   private void setDefaultChannelTags(JSONObject md) {

      JSONArray channelMaxes = new JSONArray();
      JSONArray channelMins = new JSONArray();

      // Both channelColors_ and channelNames_ may, or may not yet contain values
      // Since we don't know the size in the constructor, we can not pre-initialize
      // the data.  Therefore, fill in the blanks with deafults here:
      for (Integer i = 0; i < numChannels_; i++) {
         try {
            channelColors_.get(i);
         } catch (JSONException ex) {
            try {
               channelColors_.put(i, (Object) Color.white.getRGB());
            } catch (JSONException exx ) {;}
         }
         try {
            channelNames_.get(i);
         } catch (JSONException ex) {
            try {
               channelNames_.put(i, String.valueOf(i));
            } catch (JSONException exx) {;}
         }
         try {
            channelMaxes.put(255);
            channelMins.put(0);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
      try {
         md.put("ChColors", channelColors_);
         md.put("ChNames", channelNames_);
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
      if (!initialized_) {
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
      }

      // update acq data
      try {
         
         JSONObject tags = new JSONObject();

         tags.put("Channel", getChannelName(channel));
         tags.put("ChannelIndex", channel);
         tags.put("Frame", frame);
         tags.put("Height", height_);
         tags.put("PositionIndex", position);
         // the following influences the format data will be saved!
         if (numPositions_ > 1) {
            tags.put("PositionName", "Pos" + position);
         }
         tags.put("Slice", slice);
         tags.put("Width", width_);
         if (depth_ == 1) {
            tags.put("PixelType", "GRAY8");
         } else if (depth_ == 2) {
            tags.put("PixelType", "GRAY16");
         }
         TaggedImage tg = new TaggedImage(pixels, tags);
         insertImage(tg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }

   public void insertTaggedImage(TaggedImage taggedImg, int frame, int channel, int slice)
           throws MMScriptException {
      if (!initialized_) {
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
      }

      // update acq data
      try {
         JSONObject tags = taggedImg.tags;

         tags.put("Frame", frame);
         tags.put("ChannelIndex", channel);
         tags.put("Slice", slice);
         if (depth_ == 1) {
            tags.put("PixelType", "GRAY8");
         } else if (depth_ == 2) {
            tags.put("PixelType", "GRAY16");
         }
         tags.put("PositionIndex", 0);
         insertImage(taggedImg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {
      insertImage(taggedImg, show_);
   }

   /*
    * This is the insertImage version that actually puts data into the acquisition
    */
   public void insertImage(TaggedImage taggedImg, boolean updateDisplay) throws MMScriptException {
      if (!initialized_) {
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
      }
      try {
         JSONObject tags = taggedImg.tags;
         int channel = tags.getInt("ChannelIndex");
         tags.put("Channel", getChannelName(channel));
         long elapsedTimeMillis = System.currentTimeMillis() - startTimeMs_;
         tags.put("ElapsedTime-ms", elapsedTimeMillis);
         tags.put("Time", MDUtils.getCurrentTime());

         CMMCore core = MMStudioMainFrame.getInstance().getCore();
         MDUtils.addConfiguration(taggedImg.tags, core.getSystemStateCache());
         try {
            taggedImg.tags.put("Binning", core.getProperty (core.getCameraDevice(), "Binning"));
         } catch (Exception ex) {

         }
         tags.put("BitDepth", core.getImageBitDepth());
         tags.put("PixelSizeUm",core.getPixelSizeUm());

      } catch (JSONException ex) {
         throw new MMScriptException(ex);
      }
      virtAcq_.imageCache_.putImage(taggedImg);
      if (updateDisplay) {
         virtAcq_.showImage(taggedImg);
      }
   }

   public void close() {
      if (virtAcq_ != null) {
         if (virtAcq_.acquisitionIsRunning()) {
            virtAcq_.abort();
         }
         //virtAcq_.close();
      }
   }

   public boolean isInitialized() {
      return initialized_;
   }

   public void closeImage5D() {
      close();
      virtAcq_.close();
   }

   public void setComment(String comment) throws MMScriptException {
      if (isInitialized()) {
         try {
            virtAcq_.imageCache_.getSummaryMetadata().put("COMMENT", comment);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to set Comment");
         }
      } else {
         comment_ = comment;
      }

   }

   public AcquisitionData getAcqData() {
      return null;
   }

   public JSONObject getSummaryMetadata() {
      if (isInitialized())
         return virtAcq_.imageCache_.getSummaryMetadata();
      return null;
   }

   public String getChannelName(int channel) {
      if (isInitialized()) {
         String name = "";
         try {
            name = (String) getSummaryMetadata().getJSONArray("ChNames").get(channel);
         } catch (JSONException e) {
            ReportingUtils.logError(e);
            return "";
         }
         return name;
      } else {
         try {
            return channelNames_.getString(channel);
         } catch (JSONException ex) {
            // not found, do nothing
         }
      }
      return "";
   }

   public void setChannelName(int channel, String name) throws MMScriptException {
      if (isInitialized()) {
         try {
            virtAcq_.imageCache_.getDisplayAndComments().getJSONArray("Channels").getJSONObject(channel).put("Name", name);
            virtAcq_.imageCache_.getSummaryMetadata().getJSONArray("ChNames").put(channel, name);
         } catch (JSONException e) {
            throw new MMScriptException("Problem setting Channel name");
         }
      } else {
         try {
            channelNames_.put(channel, name);
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }
      }

   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      if (isInitialized()) {
         try {
            virtAcq_.setChannelColor(channel, rgb, true);
            virtAcq_.imageCache_.getSummaryMetadata().getJSONArray("ChColors").put(channel, rgb);
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }
      } else {
         try {
            channelColors_.put(channel, rgb);
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }
      }
   }

   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      if (isInitialized())
         virtAcq_.setChannelDisplayRange(channel, min, max, true);
      throw new MMScriptException (NOTINITIALIZED);
   }

   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      if (!isInitialized()) 
         throw new MMScriptException (NOTINITIALIZED);

      ReportingUtils.logError("API call setContrastBasedOnFrame is not implemented!");

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
            virtAcq_.imageCache_.getSummaryMetadata().put(propertyName, value);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to set property: " + propertyName);
         }
      } else {
         try {
            summary_.put(propertyName, value);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to set property: " + propertyName);
         }
      }
   }

   /*
    * Get a property from the summary metadata
    */
   public String getProperty(String propertyName) throws MMScriptException {
      if (isInitialized()) {
         try {
            return virtAcq_.imageCache_.getSummaryMetadata().getString(propertyName);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to get property: " + propertyName);
         }
      } else {
         try {
            return summary_.getString(propertyName);
         } catch (JSONException e) {
            throw new MMScriptException("Failed to get property: " + propertyName);
         }
      }
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
         throw new MMScriptException("Can not set property before acquisition is initialized");
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
         throw new MMScriptException("Can not set system state before acquisition is initialized");
  }

   public String getProperty(int frame, int channel, int slice, String propName) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_.getImage(channel, slice, frame, 0).tags;
            return tags.getString(propName);
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }

      } else {
      }
      return "";
   }

   public boolean hasActiveImage5D() {
      return virtAcq_.windowClosed();
   }

   public void setSummaryProperties(JSONObject md) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_.getSummaryMetadata();
            Iterator<String> iState = md.keys();
            while (iState.hasNext()) {
               String key = iState.next();
               tags.put(key, md.get(key));
            }
         } catch (Exception ex) {
            throw new MMScriptException(ex);
         }
      } else {
         try {
            Iterator<String> iState = md.keys();
            while (iState.hasNext()) {
               String key = iState.next();
               summary_.put(key, md.get(key));
            }
         } catch (Exception ex) {
            throw new MMScriptException(ex);
         }
      }
   }

   public boolean windowClosed() {
      if (!initialized_) {
         return false;
      }
      if (virtAcq_ != null && !virtAcq_.windowClosed()) {
         return false;
      }
      return true;
   }

   public void setSystemState(JSONObject md) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }
}
