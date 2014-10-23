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
import org.micromanager.api.TaggedImageStorage;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.display.DisplayWindow;

import org.micromanager.data.DefaultDatastore;
import org.micromanager.data.DefaultImage;
import org.micromanager.data.DefaultSummaryMetadata;

import org.micromanager.dialogs.AcqControlDlg;

import org.micromanager.imagedisplay.dev.DefaultDisplayWindow;

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
   private Datastore store_;
   private DefaultDisplayWindow display_;
   private final boolean existing_;
   private final boolean virtual_;
   private final boolean show_;
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
      store_ = new DefaultDatastore();
      try {
         if (summaryMetadata.has("Directory") && summaryMetadata.get("Directory").toString().length() > 0) {
            try {
               String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), summaryMetadata.getString("Prefix"));
               summaryMetadata.put("Prefix", acqDirectory);
               String acqPath = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
               imageFileManager = ImageUtils.newImageStorageInstance(acqPath, true, (JSONObject) null);
               store_.setStorage(new StorageRAM(store_));
//               if (!virtual_) {
//                  imageCache_.saveAs(new TaggedImageStorageRamFast(null), true);
//               }
            } catch (Exception e) {
               ReportingUtils.showError(e, "Unable to create directory for saving images.");
               eng.stop(true);
            }
         } else {
//            imageFileManager = new TaggedImageStorageRamFast(null);
//            imageCache_ = new MMImageCache(imageFileManager);
            store_.setStorage(new StorageRAM(store_));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't adjust summary metadata.");
      }

      try {
         store_.setSummaryMetadata(DefaultSummaryMetadata.legacyFromJSON(summaryMetadata));
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.logError(e, "Couldn't set summary metadata");
      }
      if (show_) {
         display_ = new DefaultDisplayWindow(store_, null);
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

         store_ = new DefaultDatastore();
         store_.setStorage(new StorageRAM(store_));
         try {
            store_.setSummaryMetadata((new DefaultSummaryMetadata.Builder().build()));
         }
         catch (DatastoreLockedException e) {
            ReportingUtils.logError(e, "Couldn't set summary metadata");
         }
//         imageCache_ = new MMImageCache(imageFileManager);
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
         store_ = new DefaultDatastore();
         store_.setStorage(new StorageRAM(store_));
         try {
            store_.setSummaryMetadata(DefaultSummaryMetadata.legacyFromJSON(summary_));
         }
         catch (DatastoreLockedException e) {
            ReportingUtils.logError(e, "Couldn't set summary metadata");
         }
//         imageCache_ = new MMImageCache(imageFileManager);
      }

      if (!virtual_ && !existing_) {
         imageFileManager = new TaggedImageStorageRamFast(null);
         store_ = new DefaultDatastore();
         store_.setStorage(new StorageRAM(store_));
         try {
            store_.setSummaryMetadata((new DefaultSummaryMetadata.Builder().build()));
         }
         catch (DatastoreLockedException e) {
            ReportingUtils.logError(e, "Couldn't set summary metadata");
         }
//         imageCache_ = new MMImageCache(imageFileManager);
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

         store_ = new DefaultDatastore();
         store_.setStorage(new StorageRAM(store_));
         try {
            store_.setSummaryMetadata((new DefaultSummaryMetadata.Builder().build()));
         }
         catch (DatastoreLockedException e) {
            ReportingUtils.logError(e, "Couldn't set summary metadata");
         }
//         imageCache_ = new MMImageCache(tempImageFileManager);
//         if (tempImageFileManager.getDataSetSize() > 0.9 * JavaUtils.getAvailableUnusedMemory()) {
//            throw new MMScriptException("Not enough room in memory for this data set.\nTry opening as a virtual data set instead.");
//         }
//         imageFileManager = new TaggedImageStorageRamFast(null);
//         imageCache_.saveAs(imageFileManager);
      }

      CMMCore core = MMStudio.getInstance().getCore();
      if (!existing_) {
         createDefaultAcqSettings();
      }

      if (store_.getSummaryMetadata() != null) {
         if (show_) {
            display_ = new DefaultDisplayWindow(store_, null);
         }
         initialized_ = true;
      }
   }
   
  
   private void createDefaultAcqSettings() {
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
         // TODO: set channel name, color, min, max from defaults.
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
         try {
            store_.setSummaryMetadata(DefaultSummaryMetadata.legacyFromJSON(summaryMetadata));
         }
         catch (DatastoreLockedException e) {
            ReportingUtils.logError(e, "Couldn't set summary metadata");
         }
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

         long elapsedTimeMillis = System.currentTimeMillis() - startTimeMs_;
         MDUtils.setElapsedTimeMs(tags, elapsedTimeMillis);
         MDUtils.setImageTime(tags, MDUtils.getCurrentTime());
      } catch (Exception ex) {
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

      try {
         DefaultImage image = new DefaultImage(taggedImg);
         image.splitMultiComponentIntoStore(store_);
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't generate DefaultImage from TaggedImage.");
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError(e, "Couldn't insert image into datastore.");
      }
   }

   public void close() {
      if (display_ != null) {
         display_.requestToClose();
      }
//      store_.lock();
   }

   public boolean isInitialized() {
      return initialized_;
   }

   public void promptToSave(boolean promptToSave) {
      ReportingUtils.logError("TODO: Prompt to save!");
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
      if (display_ != null && !display_.getIsClosed()) {
         return true;
      }
      return false;
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
            return "RGB32";
         case 8:
            return "RGB64";
      }
      return null;
   }

   public int getLastAcquiredFrame() {
      return store_.getMaxIndex("channel") + 1;
   }

   public Datastore getDatastore() {
      return store_;
   }

   public DisplayWindow getDisplay() {
      return display_;
   }

   public void setAsynchronous() {
      isAsynchronous_ = true;
   }
}
