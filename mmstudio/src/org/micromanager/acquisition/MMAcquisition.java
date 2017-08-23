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

package org.micromanager.acquisition;

import ij.ImagePlus;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.micromanager.MMStudio;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MMTags;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class is used to execute most of the acquisition and image display
 * functionality in the ScriptInterface
 */
public class MMAcquisition {
   
   public static final Color[] DEFAULT_COLORS = {Color.red, Color.green, Color.blue,
      Color.pink, Color.orange, Color.yellow};
   
   /** 
    * Final queue of images immediately prior to insertion into the ImageCache.
    * Only used when running in asynchronous mode.
    */
   private BlockingQueue<TaggedImage> outputQueue_ = null;
   private boolean isAsynchronous_ = false;
   private int numFrames_ = 0;
   private int numChannels_ = 0;
   private int numSlices_ = 0;
   private int numPositions_ = 0;
   protected String name_;
   protected int width_ = 0;
   protected int height_ = 0;
   protected int byteDepth_ = 1;
   protected int bitDepth_ = 8;    
   protected int multiCamNumCh_ = 1;
   private boolean initialized_ = false;
   private long startTimeMs_;
   private final String comment_ = "";
   private String rootDirectory_;
   private VirtualAcquisitionDisplay virtAcq_;
   private ImageCache imageCache_;
   private final boolean existing_;
   private final boolean virtual_;
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
      virtual_ = diskCached;
   }

   public MMAcquisition(String name, JSONObject summaryMetadata, boolean diskCached, 
           AcquisitionEngine eng, boolean show) {
      TaggedImageStorage imageFileManager;
      name_ = name;
      virtual_ = diskCached;
      existing_ = false;
      show_ = show;
      try {
         if (summaryMetadata.has("Directory") && summaryMetadata.get("Directory").toString().length() > 0) {
            try {
               String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), summaryMetadata.getString("Prefix"));
               summaryMetadata.put("Prefix", acqDirectory);
               String acqPath = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
               imageFileManager = ImageUtils.newImageStorageInstance(acqPath, true, (JSONObject) null);
               imageCache_ = new MMImageCache(imageFileManager);
               if (!virtual_) {
                  imageCache_.saveAs(new TaggedImageStorageRamFast(null), true);
               }
            } catch (Exception e) {
               ReportingUtils.showError(e, "Unable to create directory for saving images.");
               eng.stop(true);
               imageCache_ = null;
            }
         } else {
            imageFileManager = new TaggedImageStorageRamFast(null);
            imageCache_ = new MMImageCache(imageFileManager);
         }
  
      imageCache_.setSummaryMetadata(summaryMetadata);
      if (show_) {
         virtAcq_ = new VirtualAcquisitionDisplay(imageCache_, eng, name, false);
         imageCache_.addImageCacheListener(virtAcq_);
      }
         this.summary_ = summaryMetadata;
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }
  }
   
   private String createAcqDirectory(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
      return prefix + "_" + (1 + curIndex);
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
           int byteDepth, int bitDepth, int multiCamNumCh) throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Can't change image dimensions - the acquisition is already initialized");
      }
      width_ = width;
      height_ = height;
      byteDepth_ = byteDepth;
      bitDepth_ = bitDepth;
      multiCamNumCh_ = multiCamNumCh;
   }

   public int getWidth() {
      return width_;
   }

   public int getHeight() {
      return height_;
   }

   public int getByteDepth() {
      return byteDepth_;
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

   public int getPositions() {
      return numPositions_;
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

   //used to initialize snap and live, which only store a single image at a time
   public void initializeSimpleAcq() throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Acquisition is already initialized");
      }

      TaggedImageStorage imageFileManager = new TaggedImageStorageLive();
      MMImageCache imageCache = new MMImageCache(imageFileManager);

      if (!existing_) {
         createDefaultAcqSettings(imageCache);
      }
      MMStudio.getInstance().getSnapLiveManager().createSnapLiveDisplay(name_, imageCache);
      if (show_) {
         virtAcq_ = MMStudio.getInstance().getSnapLiveManager().getSnapLiveDisplay();
         virtAcq_.show();
         imageCache_ = virtAcq_.getImageCache();
         imageCache_.addImageCacheListener(virtAcq_);
      }

      initialized_ = true;
   }

   
   
   public void initialize() throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Acquisition is already initialized");
      }

      TaggedImageStorage imageFileManager;
      String name = name_;
      
      if (virtual_ && existing_) {
         String dirName = rootDirectory_ + File.separator + name;
         try {
            boolean multipageTiff = MultipageTiffReader.isMMMultipageTiff(dirName);
            if (multipageTiff) {
               imageFileManager = new TaggedImageStorageMultipageTiff(dirName, false, null);
            } else {
               imageFileManager = new TaggedImageStorageDiskDefault(dirName, false, null);
            }
         } catch (Exception ex) {
            throw new MMScriptException(ex);
         }

         imageCache_ = new MMImageCache(imageFileManager);
      }

      if (virtual_ && !existing_) {
         String dirName = rootDirectory_ + File.separator + name;
         if ((new File(dirName)).exists()) {
            try {
               String acqDirectory = createAcqDirectory(rootDirectory_, name_);
               if (summary_ != null) {
                  summary_.put("Prefix", acqDirectory);
                  summary_.put("Channels", numChannels_);
                  MDUtils.setPixelTypeFromByteDepth(summary_, byteDepth_);
               }
               dirName = rootDirectory_ + File.separator + acqDirectory;
            } catch (Exception ex) {
               throw new MMScriptException("Failed to figure out acq saving path.");
            }
         }
         
         imageFileManager = ImageUtils.newImageStorageInstance(dirName, true, summary_);
         imageCache_ = new MMImageCache(imageFileManager);
      }

      if (!virtual_ && !existing_) {
         imageFileManager = new TaggedImageStorageRamFast(null);
         imageCache_ = new MMImageCache(imageFileManager);
      }

      if (!virtual_ && existing_) {
         String dirName = rootDirectory_ + File.separator + name;
         TaggedImageStorage tempImageFileManager;
         boolean multipageTiff;
         try {
            multipageTiff = MultipageTiffReader.isMMMultipageTiff(dirName);
            if (multipageTiff) {
               tempImageFileManager = new TaggedImageStorageMultipageTiff(dirName, false, null);
            } else {
               tempImageFileManager = new TaggedImageStorageDiskDefault(dirName, false, null);
            }
         } catch (Exception ex) {
            throw new MMScriptException(ex);
         }

         imageCache_ = new MMImageCache(tempImageFileManager);
         if (tempImageFileManager.getDataSetSize() > 0.9 * JavaUtils.getAvailableUnusedMemory()) {
            throw new MMScriptException("Not enough room in memory for this data set.\nTry opening as a virtual data set instead.");
         }
         TaggedImageStorageRamFast ramStore = new TaggedImageStorageRamFast(null);
         ramStore.setDiskLocation(tempImageFileManager.getDiskLocation());
         imageFileManager = ramStore;
         imageCache_.saveAs(imageFileManager);
      }

      CMMCore core = MMStudio.getInstance().getCore();
      if (!existing_) {
         int camCh = (int) core.getNumberOfCameraChannels();
         if (camCh > 1) {
            for (int i = 0; i < camCh; i++) {
               if (channelNames_.length() < (1+i)) {
                  this.setChannelName(i, core.getCameraChannelName(i));
               }
            }
         } else {
            for (int i = 0; i < numChannels_; i++) {
               if (channelNames_.length() < (1+i)) {
                  this.setChannelName(i, "Default" + i);
               }
            }
         }
         // If we don't ensure that bit depth is initialized, then the
         // histograms will have problems down the road.
         if (bitDepth_ == 0) {
            bitDepth_ = (int) core.getImageBitDepth();
         }
         createDefaultAcqSettings(imageCache_);
      }

      if (imageCache_.getSummaryMetadata() != null) {
         if (show_) {
            virtAcq_ = new VirtualAcquisitionDisplay(imageCache_, null, name, true);
            imageCache_.addImageCacheListener(virtAcq_);
            virtAcq_.show();
         }
         
         // need to update the MMAcquisition members from SummaryMetadata
         // or the script interface will not work
         if (existing_) {
            JSONObject summaryMetadata = imageCache_.getSummaryMetadata();
            try {
               if (summaryMetadata.has(MMTags.Summary.FRAMES)) {
                  numFrames_ = summaryMetadata.getInt(MMTags.Summary.FRAMES);
               }
               if (summaryMetadata.has(MMTags.Summary.CHANNELS)) {
                  numChannels_ = summaryMetadata.getInt(MMTags.Summary.CHANNELS);
               }
               if (summaryMetadata.has(MMTags.Summary.SLICES)) {
                  numSlices_ = summaryMetadata.getInt(MMTags.Summary.SLICES);
               }
               if (summaryMetadata.has(MMTags.Summary.POSITIONS)) {
                  numPositions_ = summaryMetadata.getInt(MMTags.Summary.POSITIONS);
               }
               if (summaryMetadata.has(MMTags.Summary.WIDTH)) {
                  width_ = summaryMetadata.getInt(MMTags.Summary.WIDTH);
               }
               if (summaryMetadata.has(MMTags.Summary.HEIGHT)) {
                  height_ = summaryMetadata.getInt(MMTags.Summary.HEIGHT);
               }
               if (summaryMetadata.has("Depth")) {
                  byteDepth_ = summaryMetadata.getInt("Depth");
               }
               if (summaryMetadata.has(MMTags.Summary.BIT_DEPTH)) {
                  bitDepth_ = summaryMetadata.getInt(MMTags.Summary.BIT_DEPTH);
               }

            } catch (JSONException ex) {
               Logger.getLogger(MMAcquisition.class.getName()).log(Level.SEVERE, null, ex);
            }
         }

         initialized_ = true;
         
      }
   }
   
  
   private void createDefaultAcqSettings(ImageCache imageCache) {

      String keys[] = new String[summary_.length()];
      Iterator<String> it = summary_.keys();
      int i = 0;
      while (it.hasNext()) {
         keys[0] = it.next();
         i++;
      }

      try {
         JSONObject summaryMetadata = new JSONObject(summary_, keys);
         CMMCore core = MMStudio.getInstance().getCore();

         summaryMetadata.put("BitDepth", bitDepth_);
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
         if (byteDepth_ == 1) {
            ijType = ImagePlus.GRAY8;
         } else if (byteDepth_ == 2) {
            ijType = ImagePlus.GRAY16;
         } else if (byteDepth_ == 8) {
            ijType = 64;
         } else if (byteDepth_ == 4 && core.getNumberOfComponents() == 1) {
            ijType = ImagePlus.GRAY32;
         } else if (byteDepth_ == 4 && core.getNumberOfComponents() == 4) {
            ijType = ImagePlus.COLOR_RGB;
         }
         summaryMetadata.put("IJType", ijType);
         summaryMetadata.put("MetadataVersion", 10);
         summaryMetadata.put("MicroManagerVersion", MMStudio.getInstance().getVersion());
         summaryMetadata.put("NumComponents", 1);
         summaryMetadata.put("Positions", numPositions_);
         summaryMetadata.put("Source", "Micro-Manager");
         summaryMetadata.put("PixelAspect", 1.0);
         summaryMetadata.put("PixelSize_um", core.getPixelSizeUm());
         summaryMetadata.put("PixelType", (core.getNumberOfComponents() == 1 ? "GRAY" : "RGB") + (8 * byteDepth_));
         summaryMetadata.put("Slices", numSlices_);
         summaryMetadata.put("SlicesFirst", false);
         summaryMetadata.put("StartTime", MDUtils.getCurrentTime());
         summaryMetadata.put("Time", Calendar.getInstance().getTime());
         summaryMetadata.put("TimeFirst", true);
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
      String channelGroup = MMStudio.getInstance().getCore().getChannelGroup();
      if (channelGroup == null)
         channelGroup = "";
      color = colorPrefs.getInt("Color_Camera_" + channelName, colorPrefs.getInt("Color_" + channelGroup
                 + "_" + channelName, color));
      return color;
   }

   private void setDefaultChannelTags(JSONObject md) {

      JSONArray channelMaxes = new JSONArray();
      JSONArray channelMins = new JSONArray(); 

      // Both channelColors_ and channelNames_ may, or may not yet contain 
      // values (currently they should only contain values we actually care 
      // about if a Beanshell script sets them prior to adding any images to
      // the acquisition). Augment any existing values with additional
      // defaults, if not enough information is provided. But first make
      // certain we don't have more entries than we have channels.
      // If we upgraded our JSON library then we could use the .remove() method
      // of JSONArray instead of having to build separate arrays that only
      // include the elements we want; however, the new version raises an
      // exception in JSONObject.getString() if the object isn't a string,
      // which breaks us rather horribly.
      JSONArray newColors = new JSONArray();
      JSONArray newNames = new JSONArray();
      for (int i = 0; i < numChannels_; ++i) {
         try {
            if (i < channelColors_.length()) {
               newColors.put(i, channelColors_.get(i));
            }
            if (i < channelNames_.length()) {
               newNames.put(i, channelNames_.get(i));
            }
         }
         catch (JSONException e) {
            // Should never happen since we're doing our own bounds checking.
            ReportingUtils.logError(e, "Couldn't copy over names and colors!");
         }
      }
      channelColors_ = newColors;
      channelNames_ = newNames;
      if (numChannels_ == 1) {
         try {
            if (channelColors_.length() == 0) {
               // No preset color for this channel.
               channelColors_.put(0, Color.white.getRGB());
            }
            if (channelNames_.length() == 0) {
               // No preset name for this channel.
               channelNames_.put(0, "Default");
            }
            try {
               CMMCore core = MMStudio.getInstance().getCore();
               String name = core.getCurrentConfigFromCache(core.getChannelGroup());
               // Only use empty-string names (caused by having a null channel
               // group) if we don't already have a better name.
               if (!name.equals("") || channelNames_.length() == 0) {
                  channelNames_.put(0, name);
               }
            } catch (Exception e) {}
            channelMins.put(0);
            channelMaxes.put( Math.pow(2, md.getInt("BitDepth"))-1 );
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
      else {
         for (int i = 0; i < numChannels_; i++) {
            if (channelColors_.length() > i) {
               try {
                  channelColors_.put(i, getMultiCamDefaultChannelColor(i, channelNames_.getString(i)));
               } catch (JSONException ex) {
                  ReportingUtils.logError(ex);
               }
            }
            
            try {
               channelNames_.get(i);
            } catch (JSONException ex) {
               try {
                  channelNames_.put(i, String.valueOf(i));
               } catch (JSONException exx) {
                  
               }
            }
            try {
               channelMaxes.put(Math.pow(2, md.getInt("BitDepth")) - 1);
               channelMins.put(0);
            } catch (JSONException e) {
               ReportingUtils.logError(e);
            }
         }
      }
      try {
         md.put("ChColors", channelColors_);
         md.put("ChNames", channelNames_);
         md.put("ChContrastMax", channelMaxes);
         md.put("ChContrastMin", channelMins);
      } catch (JSONException e) {
         ReportingUtils.logError(e);
      }
   }

   /**
    * @param pixels
    * @param frame
    * @param channel
    * @param slice
    * @throws org.micromanager.utils.MMScriptException
    * @Deprecated transition towards the use of TaggedImaged rather than raw pixel data
    */
   public void insertImage(Object pixels, int frame, int channel, int slice)
           throws MMScriptException {
      insertImage(pixels, frame, channel, slice, 0);
   }

   /**
    * @Deprecated transition towards the use of TaggedImaged rather than raw pixel data
    */
   public void insertImage(Object pixels, int frame, int channel, int slice, int position) throws MMScriptException {
      if (!initialized_) {
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
      }

      // update acq data
      try {

         JSONObject tags = new JSONObject();

         MDUtils.setChannelName(tags, getChannelName(channel));
         MDUtils.setChannelIndex(tags, channel);
         MDUtils.setFrameIndex(tags, frame);
         MDUtils.setPositionIndex(tags, position);
         // the following influences the format data will be saved!
         if (numPositions_ > 1) {
            MDUtils.setPositionName(tags, "Pos" + position);
         }
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setHeight(tags, height_);
         MDUtils.setWidth(tags, width_);
         MDUtils.setPixelTypeFromByteDepth(tags, byteDepth_);

         TaggedImage tg = new TaggedImage(pixels, tags);
         insertImage(tg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }

   // Somebody please comment on why this is a separate method from insertImage.
   public void insertTaggedImage(TaggedImage taggedImg, int frame, int channel, int slice)
           throws MMScriptException {
      if (!initialized_) {
         throw new MMScriptException("Acquisition data must be initialized before inserting images");
      }

      // update acq data
      try {
         JSONObject tags = taggedImg.tags;

         MDUtils.setFrameIndex(tags, frame);
         MDUtils.setChannelIndex(tags, channel);
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setPixelTypeFromByteDepth(tags, byteDepth_);
         MDUtils.setPositionIndex(tags, 0);
         insertImage(taggedImg);
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
   }

   public void insertImage(TaggedImage taggedImg, int frame, int channel, int slice,
           int position) throws MMScriptException, JSONException {
      JSONObject tags = taggedImg.tags;
      MDUtils.setFrameIndex(tags, frame);
      MDUtils.setChannelIndex(tags, channel);
      MDUtils.setSliceIndex(tags, slice);
      MDUtils.setPositionIndex(tags, position);
      insertImage(taggedImg, show_);
   }

   public void insertImage(TaggedImage taggedImg, int frame, int channel, int slice,
           int position, boolean updateDisplay) throws MMScriptException, JSONException {
      JSONObject tags = taggedImg.tags;
      MDUtils.setFrameIndex(tags, frame);
      MDUtils.setChannelIndex(tags, channel);
      MDUtils.setSliceIndex(tags, slice);
      MDUtils.setPositionIndex(tags, position);
      insertImage(taggedImg, updateDisplay, true);
   }

   public void insertImage(TaggedImage taggedImg, int frame, int channel, int slice,
           int position, boolean updateDisplay, boolean waitForDisplay) throws MMScriptException, JSONException {
      JSONObject tags = taggedImg.tags;
      MDUtils.setFrameIndex(tags, frame);
      MDUtils.setChannelIndex(tags, channel);
      MDUtils.setSliceIndex(tags, slice);
      MDUtils.setPositionIndex(tags, position);
      insertImage(taggedImg, updateDisplay, waitForDisplay);
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {
      insertImage(taggedImg, show_);
   }

   public void insertImage(TaggedImage taggedImg, boolean updateDisplay) throws MMScriptException {
      insertImage(taggedImg, updateDisplay && show_ , true);
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
            ReportingUtils.logError("Metadata width and height: " + MDUtils.getWidth(tags) + "  "
                    + MDUtils.getHeight(tags) + "   Acquisition Width and height: " + width_ + " "
                    + height_);
            throw new MMScriptException("Image dimensions do not match MMAcquisition.");
         }
         if (!MDUtils.getPixelType(tags).contentEquals(getPixelType(byteDepth_))) {
            throw new MMScriptException("Pixel type does not match MMAcquisition.");
         }

         if (!MDUtils.getPixelType(tags).startsWith("RGB")) {
            int channel = MDUtils.getChannelIndex(tags);
            MDUtils.setChannelName(tags, getChannelName(channel));
         }
         long elapsedTimeMillis = System.currentTimeMillis() - startTimeMs_;
         MDUtils.setElapsedTimeMs(tags, elapsedTimeMillis);
         MDUtils.setImageTime(tags, MDUtils.getCurrentTime());
         
         if (isAsynchronous_) {
            if (outputQueue_ == null) {
               // Set up our output queue now.
               outputQueue_ = new LinkedBlockingQueue<TaggedImage>(1);
               DefaultTaggedImageSink sink = new DefaultTaggedImageSink(
                     outputQueue_, imageCache_);
               sink.start();
            }
            if (!outputQueue_.offer(taggedImg, 1L, TimeUnit.SECONDS)) {
               throw new IllegalStateException("Queue full");
            }
         }
         else {
            imageCache_.putImage(taggedImg);
         }
      } catch (IOException ex) {
         throw new MMScriptException(ex);
      } catch (IllegalStateException ex) {
         throw new MMScriptException(ex);
      } catch (InterruptedException ex) {
         throw new MMScriptException(ex);
      } catch (JSONException ex) {
         throw new MMScriptException(ex);
      } catch (MMException ex) {
         throw new MMScriptException(ex);
      } catch (MMScriptException ex) {
         throw new MMScriptException(ex);
      }
      

      if (show_) {
         try {
            virtAcq_.albumChanged();
         } catch (Exception ex) {
            throw new MMScriptException(ex);
         }
         if (updateDisplay) {
            try {
               if (virtAcq_ != null) {
                  virtAcq_.updateDisplay(taggedImg);
               }
            } catch (Exception e) {
               ReportingUtils.logError(e);
               throw new MMScriptException("Unable to show image");
            }
         }
      }
   }

   public void close() {
      if (virtAcq_ != null) {
         if (virtAcq_.acquisitionIsRunning()) {
            virtAcq_.abort();
         }
      }
      if (outputQueue_ != null) {
         // Ensure our queue consumer cleans up after themselves.
         outputQueue_.add(TaggedImageQueue.POISON);
         outputQueue_ = null;
      }
      if (imageCache_ != null && !imageCache_.isFinished()) {
         imageCache_.finished();
      }
   }

   public boolean isInitialized() {
      return initialized_;
   }

   /**
    * Same as close(), but also closes the display
    * @return false if canceled by user, true otherwise
    */
   public boolean closeImageWindow() {
      if (virtAcq_ != null) {
         if (!virtAcq_.close()) {
            return false;
         }
      }
      close();
      return true;
   }

   
   public ImageCache getImageCache() {
      return imageCache_;
   }

   /*
    * Provides the summary metadata, i.e. metadata applying to the complete
    * acquisition rather than indviviudal images.
    * Metadata are returned as a JSONObject
    */
   public JSONObject getSummaryMetadata() {
      if (isInitialized()) {
         return imageCache_.getSummaryMetadata();
      }
      return null;
   }

   public String getChannelName(int channel) {
      if (isInitialized()) {
         try {
            JSONArray chNames =  getSummaryMetadata().getJSONArray("ChNames");
            if (chNames == null || channel >= chNames.length() )
               return "";
            String name = chNames.getString(channel);
            return name;
         } catch (JSONException e) {
            ReportingUtils.logError(e);
            return "";
         }
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
            imageCache_.getDisplayAndComments().getJSONArray("Channels").getJSONObject(channel).put("Name", name);
            imageCache_.getSummaryMetadata().getJSONArray("ChNames").put(channel, name);
            if (show_) {
               virtAcq_.updateChannelNamesAndColors();
            }
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
            imageCache_.setChannelColor(channel, rgb);
            imageCache_.getSummaryMetadata().getJSONArray("ChColors").put(channel, rgb);
            if (show_) {
               virtAcq_.updateChannelNamesAndColors();
               virtAcq_.updateAndDraw(true);
            }
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
      if (show_) {
         VirtualAcquisitionDisplay.getDisplay(virtAcq_.getHyperImage()).promptToSave(promptToSave);
      }
   }

   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      if (show_) {
         if (isInitialized()) {
            virtAcq_.setChannelContrast(channel, min, max, 1.0);
         } else {
            throw new MMScriptException(NOTINITIALIZED);
         }
      }
   }


   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      if (show_) {
         if (!isInitialized()) {
            throw new MMScriptException(NOTINITIALIZED);
         }
         int currentFrame = virtAcq_.getHyperImage().getFrame();
         int currentSlice = virtAcq_.getHyperImage().getSlice();
         int currentChannel = virtAcq_.getHyperImage().getChannel();
         virtAcq_.getHyperImage().setPosition(currentChannel, slice, frame);
         virtAcq_.getHistograms().autoscaleAllChannels();
         virtAcq_.getHyperImage().setPosition(currentChannel, currentSlice, currentFrame);
      }
   }

   /**
    * Sets a property in summary metadata
    * @param propertyName
    * @param value
    * @throws org.micromanager.utils.MMScriptException
    */
   public void setProperty(String propertyName, String value) throws MMScriptException {
      if (isInitialized()) {
         try {
            imageCache_.getSummaryMetadata().put(propertyName, value);
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
            JSONObject tags = imageCache_.getImage(channel, slice, frame, 0).tags;
            tags.put(propName, value);
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      } else {
         throw new MMScriptException("Can not set property before acquisition is initialized");
      }
   }

   public void setSummaryProperties(JSONObject md) throws MMScriptException {
      if (isInitialized()) {
         try {
            JSONObject tags = imageCache_.getSummaryMetadata();
            Iterator<String> iState = md.keys();
            while (iState.hasNext()) {
               String key = iState.next();
               tags.put(key, md.get(key));
            }
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }
      } else {
         try {
            Iterator<String> iState = md.keys();
            while (iState.hasNext()) {
               String key = iState.next();
               summary_.put(key, md.get(key));
            }
         } catch (JSONException ex) {
            throw new MMScriptException(ex);
         }
      }
   }

   /**
    * Tests whether the window associated with this acquisition is closed
    * 
    * @return true when acquisition has an open window, false otherwise 
    */
   public boolean windowClosed() {
      if (!show_ || !initialized_) {
         return false;
      }
      if (virtAcq_ != null && !virtAcq_.windowClosed()) {
         return false;
      }
      return true;
   }
   
   /**
    * Returns show flag, indicating whether this acquisition was opened with
    * a request to show the image in a window
    * 
    * @return flag for request to display image in window
    */
   public boolean getShow() {
      return show_;
   }

   private static String getPixelType(int depth) {
      switch (depth) {
         case 1:
            return "GRAY8";
         case 2:
            return "GRAY16";
         case 4:
            if (MMStudio.getInstance().getCore().getNumberOfComponents() == 1) {
               return "GRAY32";
            }
            return "RGB32";
         case 8:
            return "RGB64";
      }
      return null;
   }

   public int getLastAcquiredFrame() {
      return (imageCache_ != null) ? imageCache_.lastAcquiredFrame() : 0;
   }

   public VirtualAcquisitionDisplay getAcquisitionWindow() {
      return virtAcq_;
   }

   public void setAsynchronous() {
      isAsynchronous_ = true;
   }
}
