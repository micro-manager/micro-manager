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
/**
 * This class is used to execute most of the acquisition and image display
 * functionality in the ScriptInterface
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import java.awt.Color;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mmcorej.CMMCore;

import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.micromanager.AcqControlDlg;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.ImageCache;

import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class MMAcquisition {
   
   public static final Color[] DEFAULT_COLORS = {Color.red, Color.green, Color.blue,
      Color.orange, Color.pink, Color.yellow};
   
   private int numFrames_ = 0;
   private int numChannels_ = 0;
   private int numSlices_ = 0;
   private int numPositions_ = 0;
   protected String name_;
   protected int width_ = 0;
   protected int height_ = 0;
   protected int depth_ = 1;
   protected int bitDepth_ = 8;    
   protected int multiCamNumCh_ = 1;
   private boolean initialized_ = false;
   private long startTimeMs_;
   private String comment_ = "";
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

   public MMAcquisition(String name, JSONObject summaryMetadata, boolean diskCached, AcquisitionEngine eng) {
      TaggedImageStorage imageFileManager;
      name_ = name;
      diskCached_ = diskCached;
      existing_ = false;
      show_ = true;
      if (diskCached) {
         try {
            String acqPath = createAcqPath(summaryMetadata.getString("Directory"),
                    summaryMetadata.getString("Prefix"));
            imageFileManager = ImageUtils.newImageStorageInstance(acqPath,
                    true, (JSONObject) null);
         } catch (Exception e) {
            ReportingUtils.showError(e, "Unable to create directory for saving images.");
            eng.stop(true);
            imageFileManager = null;
         }
      } else {
         imageFileManager = new TaggedImageStorageRam(null);
      }


      MMImageCache imageCache_ = new MMImageCache(imageFileManager);
      imageCache_.setSummaryMetadata(summaryMetadata);

      virtAcq_ = new VirtualAcquisitionDisplay(imageCache_, eng);
      imageCache_.addImageCacheListener(virtAcq_);
      this.summary_ = summaryMetadata;
   }

   private String createAcqPath(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
      File acqDir = new File(root + "/" + prefix + "_" + (1 + curIndex));
      return acqDir.getAbsolutePath();
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               //e.g.: "blah_32.ome.tiff"
               Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
               Matcher m = p.matcher(theName);
               if (m.matches()) {
                  number = Integer.parseInt(m.group(1));
                  if (number >= maxNumber) {
                     maxNumber = number;
                  }
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }

   public void setImagePhysicalDimensions(int width, int height,
           int depth, int bitDepth, int multiCamNumCh) throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Can't image change dimensions - the acquisition is already initialized");
      }
      width_ = width;
      height_ = height;
      depth_ = depth;
      bitDepth_ = bitDepth;
      multiCamNumCh_ = multiCamNumCh;
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
   
   public int getBitDepth() {
      return bitDepth_;
   }

   public int getMultiCameraNumChannels() {
      return multiCamNumCh_;
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

   //used to initialize snap and live, which only sotre a single image at a time
   public void initializeSimpleAcq() throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Acquisition is already initialized");
      }

      TaggedImageStorage imageFileManager = new TaggedImageStorageRam(null);
      MMImageCache imageCache = new MMImageCache(imageFileManager);

      if (!existing_) {
         createDefaultAcqSettings(name_, imageCache);
      }
      MMStudioMainFrame.createSimpleDisplay(name_, imageCache);
      virtAcq_ = MMStudioMainFrame.getSimpleDisplay();
      if (show_) {
         virtAcq_.show();
      }

      initialized_ = true;
   }

   public void initialize() throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Acquisition is already initialized");
      }

      TaggedImageStorage imageFileManager;
      String name = name_;

      if (diskCached_) {
         String dirName = rootDirectory_ + File.separator + name;
         if (!existing_ && (new File(dirName)).exists()) {
            try {
               dirName = createAcqPath(rootDirectory_, name_);
            } catch (Exception ex) {
               throw new MMScriptException("Failed to figure out acq saving path.");
            }
         }
         imageFileManager = new TaggedImageStorageDiskDefault(dirName,
                 !existing_, new JSONObject());
      } else {
         imageFileManager = new TaggedImageStorageRam(null);
      }


      MMImageCache imageCache = new MMImageCache(imageFileManager);

      CMMCore core = MMStudioMainFrame.getInstance().getCore();
      if (!existing_) {
         int camCh = (int) core.getNumberOfCameraChannels();
         if (camCh > 1) {
            for (int i = 0; i < camCh; i++)
               this.setChannelName(i, core.getCameraChannelName(i));
         } else {
            this.setChannelName(0, "Default");
         }
         createDefaultAcqSettings(name, imageCache);
      }

      virtAcq_ = new VirtualAcquisitionDisplay(imageCache, null, name);

      if (show_ && diskCached_ && existing_) {
         // start loading all other images in a background thread
         PreLoadDataThread t = new PreLoadDataThread(virtAcq_);
         new Thread(t, "PreLoadDataThread").start();
      }
      if (show_) {
         virtAcq_.show();
      }

      initialized_ = true;
   }

   private void createDefaultAcqSettings(String name, ImageCache imageCache) {
      //if (new File(rootDirectory_).exists()) {
      //   name = generateRootName(name, rootDirectory_);
      //}

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
         String compName = null;
         try {
            compName = InetAddress.getLocalHost().getHostName();
         } catch (UnknownHostException e) {
            ReportingUtils.showError(e);
         }
         if (compName != null) {
            summaryMetadata.put("ComputerName", compName);
         }
         summaryMetadata.put("Date", new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime()));
         summaryMetadata.put("Depth", core.getBytesPerPixel());
         summaryMetadata.put("Frames", numFrames_);
         summaryMetadata.put("GridColumn", 0);
         summaryMetadata.put("GridRow", 0);
         summaryMetadata.put("Height", height_);
         int ijType = -1;
         if (depth_ == 1) {
            ijType = ImagePlus.GRAY8;
         } else if (depth_ == 2) {
            ijType = ImagePlus.GRAY16;
         } else if (depth_ == 8) {
            ijType = 64;
         } else if (depth_ == 4 && core.getNumberOfComponents() == 1) {
            ijType = ImagePlus.GRAY32;
         } else if (depth_ == 4 && core.getNumberOfComponents() == 4) {
            ijType = ImagePlus.COLOR_RGB;
         }
         summaryMetadata.put("IJType", ijType);
         summaryMetadata.put("MetadataVersion", 10);
         summaryMetadata.put("MicroManagerVersion", MMStudioMainFrame.getInstance().getVersion());
         summaryMetadata.put("NumComponents", 1);
         summaryMetadata.put("Positions", numPositions_);
         summaryMetadata.put("Source", "Micro-Manager");
         summaryMetadata.put("PixelAspect", 1.0);
         summaryMetadata.put("PixelSize_um", core.getPixelSizeUm());
         summaryMetadata.put("PixelType", (core.getNumberOfComponents() == 1 ? "GRAY" : "RGB") + (8 * depth_));
         summaryMetadata.put("Slices", numSlices_);
         summaryMetadata.put("StartTime", MDUtils.getCurrentTime());
         summaryMetadata.put("Time", Calendar.getInstance().getTime());
         summaryMetadata.put("UserName", System.getProperty("user.name"));
         summaryMetadata.put("UUID", UUID.randomUUID());
         summaryMetadata.put("Width", width_);
         startTimeMs_ = System.currentTimeMillis();
         imageCache.setSummaryMetadata(summaryMetadata);
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }
   }
   
   public static int getMultiCamDefaultChannelColor(int index, String channelName) {
      Preferences root = Preferences.userNodeForPackage(AcqControlDlg.class);
      Preferences colorPrefs = root.node(root.absolutePath() + "/" + AcqControlDlg.COLOR_SETTINGS_NODE);
      int color = DEFAULT_COLORS[index % DEFAULT_COLORS.length].getRGB();
      String channelGroup = MMStudioMainFrame.getInstance().getCore().getChannelGroup();
      if (channelGroup.length() == 0) 
         color = colorPrefs.getInt("Color_Camera_" + channelName, color);
      else 
         color = colorPrefs.getInt("Color_" + channelGroup
                 + "_" + channelName, color);
      
      return color;
   }

   private void setDefaultChannelTags(JSONObject md) {

      JSONArray channelMaxes = new JSONArray();
      JSONArray channelMins = new JSONArray(); 

      // Both channelColors_ and channelNames_ may, or may not yet contain values
      // Since we don't know the size in the constructor, we can not pre-initialize
      // the data.  Therefore, fill in the blanks with deafults here:
      channelColors_ = new JSONArray();
      
      if (numChannels_ == 1)
         try {
            channelColors_.put(0, Color.white.getRGB());
            channelNames_.put(0,"Default");
            channelMins.put(0);
            channelMaxes.put( Math.pow(2, md.getInt("BitDepth"))-1 );
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      else
         for (Integer i = 0; i < numChannels_; i++) {
            try {
               channelColors_.put(getMultiCamDefaultChannelColor(i, channelNames_.getString(i)));
            } catch (JSONException ex) {
               ReportingUtils.logError(ex);
            }
            
            try {
               channelNames_.get(i);
            } catch (JSONException ex) {
               try {
                  channelNames_.put(i, String.valueOf(i));
               } catch (JSONException exx) {
                  ;
               }
            }
            try {
               channelMaxes.put(Math.pow(2, md.getInt("BitDepth")) - 1);
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
         MDUtils.setPixelTypeFromByteDepth(tags, depth_);

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

         tags.put("FrameIndex", frame);
         tags.put("Frame", frame);
         tags.put("ChannelIndex", channel);
         tags.put("SliceIndex", slice);
         MDUtils.setPixelTypeFromByteDepth(tags, depth_);
         tags.put("PositionIndex", 0);
         insertImage(taggedImg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {
      insertImage(taggedImg, show_);
   }

   public void insertImage(TaggedImage taggedImg, boolean updateDisplay) throws MMScriptException {
      insertImage(taggedImg, updateDisplay, true);
   }

   /*
    * This is the insertImage version that actually puts data into the acquisition
    */
   public void insertImage(TaggedImage taggedImg,
           boolean updateDisplay,
           boolean waitForDisplay) throws MMScriptException {
      if (!initialized_) {
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
      }

      try {
         JSONObject tags = taggedImg.tags;

         if (!(MDUtils.getWidth(tags) == width_
                 && MDUtils.getHeight(tags) == height_)) {
            ReportingUtils.logError("Metadata width and height: " + MDUtils.getWidth(tags) + "  " +
                    MDUtils.getHeight(tags) + "   Acquisition Width and height: " + width_ + " "+
                    height_);
            throw new MMScriptException("Image dimensions do not match MMAcquisition.");
         }
         if (!MDUtils.getPixelType(tags).contentEquals(getPixelType(depth_))) {
            throw new MMScriptException("Pixel type does not match MMAcquisition.");
         }

         int channel = tags.getInt("ChannelIndex");
         int frame = MDUtils.getFrameIndex(tags);
         if (!MDUtils.getPixelType(tags).startsWith("RGB"))
            tags.put("Channel", getChannelName(channel));
         long elapsedTimeMillis = System.currentTimeMillis() - startTimeMs_;
         tags.put("ElapsedTime-ms", elapsedTimeMillis);
         tags.put("Time", MDUtils.getCurrentTime());
      } catch (JSONException ex) {
         throw new MMScriptException(ex);
      }
      try {
         virtAcq_.imageCache_.putImage(taggedImg);
         virtAcq_.albumChanged();
      } catch (Exception ex) {
         throw new MMScriptException(ex);
      }
      if (updateDisplay) {
         try {
            if (virtAcq_ != null) {
               virtAcq_.showImage(taggedImg.tags, waitForDisplay);
            }
         } catch (Exception e) {
            ReportingUtils.logError(e);
            throw new MMScriptException("Unable to show image");
         }
      }
   }

   public void close() {
      if (virtAcq_ != null) {
         if (virtAcq_.acquisitionIsRunning()) {
            virtAcq_.abort();
         }
         virtAcq_.imageCache_.finished();
      }
   }

   public boolean isInitialized() {
      return initialized_;
   }

   /**
    * Same as close(), but also closes the display
    */
   public void closeImage5D() {
      close();
      if (virtAcq_ != null) {
         virtAcq_.close();
      }
   }

   /**
    * Brings the window displaying this acquisition to the front
    */
   public void toFront() {
      virtAcq_.getHyperImage().getWindow().toFront();
   }

   /**
    * Adds a comment to the metadata associated with this acquisition
    * @param comment Comment that will be added
    * @throws MMScriptException 
    */
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

   /**
    * @deprecated 
    * @return 
    */
   public AcquisitionData getAcqData() {
      return null;
   }

   public ImageCache getImageCache() {
      if (virtAcq_ == null) {
         return null;
      } else {
         return virtAcq_.getImageCache();
      }
   }

   /*
    * Provides the summary metadata, i.e. metadata applying to the complete
    * acquisition rather than indviviudal images.
    * Metadata are returned as a JSONObject
    */
   public JSONObject getSummaryMetadata() {
      if (isInitialized()) {
         return virtAcq_.imageCache_.getSummaryMetadata();
      }
      return null;
   }

   public String getChannelName(int channel) {
      if (isInitialized()) {
         String name = "";
         try {
            JSONArray chNames =  getSummaryMetadata().getJSONArray("ChNames");
            if (chNames == null || channel >= chNames.length() )
               return "";
            name = chNames.getString(channel);
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
            virtAcq_.imageCache_.setChannelColor(channel, rgb);
            virtAcq_.imageCache_.getSummaryMetadata().getJSONArray("ChColors").put(channel, rgb);
            virtAcq_.updateAndDraw();
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

   public void promptToSave(boolean promptToSave) {
      VirtualAcquisitionDisplay.getDisplay(virtAcq_.getHyperImage()).promptToSave(promptToSave);
   }

   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      if (isInitialized()) {
         virtAcq_.setChannelContrast(channel, min, max,1.0);
      } else
         throw new MMScriptException(NOTINITIALIZED);
   }

   /**
    * @deprecated 
    * @param frame
    * @param slice
    * @throws MMScriptException 
    */
   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      if (!isInitialized()) {
         throw new MMScriptException(NOTINITIALIZED);
      }

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

   /**
    * Sets a property in summary metadata
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

   /**
    * Gets a property from the summary metadata
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

   /**
    * Sets a property in the metadata of the specified image
    * 
    * @param frame
    * @param channel
    * @param slice
    * @param propName
    * @param value
    * @throws MMScriptException 
    */
   public void setProperty(int frame, int channel, int slice, String propName,
           String value) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = virtAcq_.imageCache_.getImage(channel, slice, frame, 0).tags;
            tags.put(propName, value);
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      } else {
         throw new MMScriptException("Can not set property before acquisition is initialized");
      }
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
      } else {
         throw new MMScriptException("Can not set system state before acquisition is initialized");
      }
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

   private static String getPixelType(int depth) {
      switch (depth) {
         case 1:
            return "GRAY8";
         case 2:
            return "GRAY16";
         case 4:
            return "RGB32";
         case 8:
            return "RGB64";
      }
      return null;
   }

   public int getLastAcquiredFrame() {
      return virtAcq_.imageCache_.lastAcquiredFrame();
   }

   public VirtualAcquisitionDisplay getAcquisitionWindow() {
      return virtAcq_;
   }
}
