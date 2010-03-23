///////////////////////////////////////////////////////////////////////////////
//FILE:           AcquisitionData.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      mmstudio and 3rd party applications
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
//COPYRIGHT:      University of California, San Francisco, 2006
//                100X Imaging Inc, www.100ximaging.com, 2008
//
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// HISTORY:       Revised, March 2007, N. Amodaj: Allows writing as well as reading
//                of micro-manager data files. More comprehensive commands for setting
//                color and contrast.
//
//CVS:            $Id$


package org.micromanager.metadata;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.process.ColorProcessor;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.quirkware.guid.PlatformIndependentGuidGen;
import org.micromanager.utils.ReportingUtils;

/**
 * Encapsulation of the acquisition data files.
 * Enables the user access pixels and and other data through a simple interface. 
 */
public class AcquisitionData {
   public static final String METADATA_FILE_NAME = "metadata.txt";
   public static final String METADATA_SITE_PREFIX = "site";
   public static final String DATE_FORMAT = "yyyy-MM-dd";
   public static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

   private JSONObject metadata_;
   private JSONObject summary_;
   private JSONObject positionProperties_;

   private int frames_=0;
   private int slices_=0;
   private int channels_=0;
   private int imgWidth_=0;
   private int imgHeight_=0;
   private int imgDepth_=0;
   private double pixelSize_um_ = 0.0;
   private double pixelAspect_ = 1.0;
   private int ijType_ = 0;
   private String basePath_;
   private String name_;
   private int version_ = 0;
   private double imageInterval_ms_;
   private double imageZStep_um_;
   private String channelNames_[];
   private boolean inmemory_;
   private Hashtable<String, ImageProcessor> images_;

   private PlatformIndependentGuidGen guidgen_;

   private Calendar creationTime_;

   private static final String ERR_METADATA_CREATE = "Internal error creating metadata";
   private static final String ERR_CHANNEL_INDEX = "Channel index out of bounds";
   private String lastImageFilePath_ = "";

   /**
    * Constructs an empty object.
    * load() or createNew() must be called before use.
    */
   public AcquisitionData() {
      positionProperties_ = new JSONObject();
      guidgen_ = PlatformIndependentGuidGen.getInstance();
      creationTime_ = Calendar.getInstance();
      inmemory_ = true;
      images_ = null;
      name_ = new String();
      basePath_ = null;
      metadata_ = new JSONObject();
   }
   /**
    * Loads the metadata from the acquisition files stored on disk and initializes the object.
    * This method must be called prior to any other calls.
    * @param basePath - acquisition directory path
    * @throws MMAcqDataException
    */
   public void load(String basePath) throws MMAcqDataException {
      reset();
      // attempt to open metadata file
      basePath_ = basePath;
      images_ = null;
      inmemory_ = false;
      
      File metaFile = new File(basePath_ + "/" + METADATA_FILE_NAME);
      if (!metaFile.exists()) {
         throw new MMAcqDataException("Metadata file missing.\n" +
         "Specified directory does not exist or does not contain acquisition data.");
      }

      // load metadata
      // --------------

      StringBuffer contents = new StringBuffer();
      try {
         // read metadata from file            
         BufferedReader input = null;
         input = new BufferedReader(new FileReader(metaFile));
         String line = null;
         while (( line = input.readLine()) != null){
            contents.append(line);
            contents.append(System.getProperty("line.separator"));
         }
         metadata_ = new JSONObject(contents.toString());
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      } catch (IOException e) {
         throw new MMAcqDataException(e);
      }
      
      File bp = new File(basePath_);
      name_ = bp.getName();
      parse();
   }

   /**
    * Loads contents from the JSON object.
    * This method should be used only within Micro-manager for efficiency purposes,
    * i.e. to avoid conversions between string and object representations of metadata.
    * @param metadata JSON object
    * @throws MMAcqDataException
    */
   public void load (JSONObject metadata) throws MMAcqDataException {
      reset();
      metadata_ = metadata;
      parse();
   }
 
   /**
    * Converts an in-memory data to persistent disk based directory.
    * @param path
    * @throws MMAcqDataException 
    */
   public void save(String name, String path, boolean autoName, SaveProgressCallback scb) throws MMAcqDataException {
      if (!inmemory_)
         throw new MMAcqDataException("This data is already created as persistent - location can't be changed.");

      // create directory
      String actualName = name; 
      if (autoName)
         actualName = generateRootName(name, path);
      
      String bp = path + "/" + actualName;
      File outDir = new File(bp);
      if(!outDir.exists()){
         if (!outDir.mkdirs()) {
            throw new MMAcqDataException("Unable to create directory: " + bp);
         }
      } else{
          if( !outDir.isDirectory() )
              throw new MMAcqDataException( bp + " is not a directory");
      }

      basePath_ = bp;
      name_ = actualName;

      // write initial metadata
      writeMetadata();
      
      // write images
      for (Enumeration<String> e = images_.keys() ; e.hasMoreElements(); ) {
         String frameKey = e.nextElement();
         JSONObject frameData;
         String fname;
         try {
            frameData = metadata_.getJSONObject(frameKey);
            if (frameData.has(ImagePropertyKeys.FILE)) {
               fname = frameData.getString(ImagePropertyKeys.FILE);
               String filePath = basePath_ + "/" + fname;
               ImagePlus imp = new ImagePlus(fname, images_.get(frameKey));
               FileSaver fs = new FileSaver(imp);
               fs.saveAsTiff(filePath);
            }
         } catch (JSONException exc) {
            throw new MMAcqDataException(exc);
         }
         
         if (scb != null)
            scb.imageSaved();
      }
      
      // finally tag the object as persistent (not in-memory)
      inmemory_ = false;
      images_ = null;
   }
   
   /**
    * Commits currently cached metadata to disk.
    * @throws MMAcqDataException
    */
   public void saveMetadata() throws MMAcqDataException {
      if (inmemory_)
         throw new MMAcqDataException("Unable to save metadata - this acquisition is defined as 'in-memory'.");
      
      writeMetadata();
   }

   /**
    * Creates a copy of the entire data set with exact replica of the metadata.
    * However, files are not copied at the second location. Instead, file references are removed
    * from the original instance, and retained in the new one.
    * 
    * TODO: it is not clear why is this method required and how is it intended to be used
    * Consider removing
    * 
    * @return - new instance
    * @throws MMAcqDataException
    */
   public AcquisitionData createCopy() throws MMAcqDataException {
      AcquisitionData ad = new AcquisitionData();
      try {
         ad.createNew();
         ad.load(metadata_);
         
         // remove image references and turn
         for (int i=0; i<frames_; i++) {
            for (int j=0; j<channels_; j++) {
               for (int k=0; k<slices_; k++) {
                  String key = ImageKey.generateFrameKey(i, j, k);
                  if (metadata_.has(key)) {
                     JSONObject imageData = metadata_.getJSONObject(key);
                     if (imageData.has(ImagePropertyKeys.FILE)) {
                        imageData.remove(ImagePropertyKeys.FILE);
                        metadata_.put(key, imageData);
                     }
                  }
               }
                  
            }
         }         
      } catch (MMAcqDataException e) {
         throw new MMAcqDataException("Internal error: unable to create a copy of the metadata.");
      } catch (JSONException e) {
         throw new MMAcqDataException("Internal error: unable to remove image file references.");
      }
      
      return ad;
   }


   /**
    * Creates the new acquisition data set.
    * If the object was already pointing to another data set, it
    * will be simply disconnected from it - no data will be lost.
    * The actual name of the directory for the data set will be
    * created using "name" variable as the prefix and the acquisition
    * number as the suffix. Acquisition numbers are automatically generated:
    * e.g. "name_0", "name_1", etc.
    * @param name - acquisition name (title)
    * @param path - root directory for the acquisition
    * @throws MMAcqDataException
    */
   public void createNew(String name, String path, boolean autoName) throws MMAcqDataException {

      metadata_ = new JSONObject();
      summary_ = new JSONObject();
      positionProperties_ = new JSONObject();
      
      frames_=0;
      slices_=0;
      channels_=0;

      imgWidth_= 0;
      imgHeight_= 0;
      imgDepth_= 0;

      pixelSize_um_ = 0.0;
      pixelAspect_ = 1.0;
      ijType_ = 0;

      channelNames_ = new String[channels_];

      // set initial summary data
      try {
         summary_.put(SummaryKeys.GUID, guidgen_.genNewGuid());
         version_ = SummaryKeys.VERSION;
         summary_.put(SummaryKeys.METADATA_VERSION, version_);
         summary_.put(SummaryKeys.METADATA_SOURCE, SummaryKeys.SOURCE);

         summary_.put(SummaryKeys.NUM_FRAMES, frames_);
         summary_.put(SummaryKeys.NUM_CHANNELS, channels_);
         summary_.put(SummaryKeys.NUM_SLICES, slices_);
         summary_.put(SummaryKeys.IMAGE_WIDTH, imgWidth_);
         summary_.put(SummaryKeys.IMAGE_HEIGHT, imgHeight_);
         summary_.put(SummaryKeys.IMAGE_DEPTH, imgDepth_);
         summary_.put(SummaryKeys.IJ_IMAGE_TYPE, ijType_);
         summary_.put(SummaryKeys.IMAGE_PIXEL_SIZE_UM, pixelSize_um_);
         summary_.put(SummaryKeys.IMAGE_PIXEL_ASPECT, pixelAspect_);
         summary_.put(SummaryKeys.IMAGE_INTERVAL_MS, 0.0);
         summary_.put(SummaryKeys.IMAGE_Z_STEP_UM, 0.0);

         // set time and date of creation
         creationTime_ = new GregorianCalendar();
         SimpleDateFormat sdfDate = new SimpleDateFormat(DATE_FORMAT);
         SimpleDateFormat sdfTime = new SimpleDateFormat(TIME_FORMAT);

         summary_.put(SummaryKeys.TIME, sdfTime.format(creationTime_.getTime()));
         summary_.put(SummaryKeys.DATE, sdfDate.format(creationTime_.getTime()));
         summary_.put(SummaryKeys.COMMENT, "empty");
         
         // set user name and computer name
         try
         {
            java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();   
            summary_.put(SummaryKeys.COMPUTER_NAME, localMachine.getHostName());
         }
         catch(UnknownHostException e)
         {
            ReportingUtils.showError(e);
         }
         summary_.put(SummaryKeys.USER_NAME, System.getProperty("user.name"));

         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);
         
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (path != null) {
      // create directory
	      String actualName = name; 
	      if (autoName)
	         actualName = generateRootName(name, path);
	
         basePath_ = path + "/" + actualName;
	      
         File outDir = new File(basePath_);
         if (!outDir.mkdirs())
	         throw new MMAcqDataException("Unable to create directory: " + basePath_ + ". It already exists.");
	      
         name_ = actualName;

     	   inmemory_ = false;
         images_ = null;

         // write initial metadata
         writeMetadata();
      } else {
         inmemory_ = true;
         images_ = new Hashtable<String, ImageProcessor>();
         basePath_ = null;
         name_ = "in-memory";
      }
   }


   /**
    * Creates a new in-memory acquisition data set.
    * If the object was already pointing to another data set, it
    * will be simply disconnected from it - no data will be lost.
    * @throws MMAcqDataException
    */
   public void createNew() throws MMAcqDataException {
	   createNew("in-memory",null,false);
   }

   /**
    * Defines physical dimensions of the image: height and width in pixels,
    * depth in bytes.
    * @param width - X dimension (columns)
    * @param height - Y dimension (rows)
    * @param depth - bytes per pixel
    * @throws MMAcqDataException
    */
   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMAcqDataException {
      imgWidth_= width;
      imgHeight_= height;
      imgDepth_= depth;

      if (imgDepth_ == 1)
         ijType_ = ImagePlus.GRAY8;
      else if (imgDepth_ == 2)
         ijType_ = ImagePlus.GRAY16;
      else if( 4 == imgDepth_)
         ijType_ = ImagePlus.COLOR_RGB;
      else
         throw new MMAcqDataException("Unsupported pixel depth: " + imgDepth_);

      try {
         summary_.put(SummaryKeys.IMAGE_WIDTH, imgWidth_);
         summary_.put(SummaryKeys.IMAGE_HEIGHT, imgHeight_);
         summary_.put(SummaryKeys.IMAGE_DEPTH, imgDepth_);
         summary_.put(SummaryKeys.IJ_IMAGE_TYPE, ijType_);

         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (!inmemory_)
         writeMetadata();
   }

   /**
    * Sets time, wavelength and focus dimensions of the data set.
    * @param frames - number or frames (time)
    * @param channels - number of channels (wavelength)
    * @param slices - number of slices (Z, or focus)
    * @throws MMAcqDataException
    */
   public void setDimensions(int frames, int channels, int slices) throws MMAcqDataException {
      
      boolean resetNames = false;
      if (channels != channels_)
         resetNames = true;
      
      frames_ = frames;
      channels_ = channels;
      slices_ = slices;

      // update summary
      try {
         summary_.put(SummaryKeys.NUM_FRAMES, frames_);
         summary_.put(SummaryKeys.NUM_CHANNELS, channels_);
         summary_.put(SummaryKeys.NUM_SLICES, slices_);

         // TODO: non-destructive fill-in of default names
         if (resetNames)
            defaultChannelNames();

         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);

      } catch (JSONException e) {
         ReportingUtils.showError(e);
      }

      //if (!inmemory_)
         //writeMetadata();
   }

   /**
    * Checks if the specified directory contains micro-manager metadata file
    * @param dir - directory path
    * @return - true if correct metadata file was identified
    */
   public static boolean hasMetadata(String dir) {
      File metaFile = new File(dir + "/" + METADATA_FILE_NAME);
      if (metaFile.exists())
         return true;
      else
         return false;
   }

   /**
    * Returns the path containing a full data set: metadata and images.
    * @return - path
    */
   public String getBasePath() throws MMAcqDataException {
      if (inmemory_)
         throw new MMAcqDataException("Base path not defined - acquisition data is created in-memory.");
      return new String(basePath_);
   }
   
   /**
    * Returns the data mode: in memory or on disk
    * @return - true if data is stored in the memory
    */
   public boolean isInMemory() {
      return inmemory_;
   }
   
   /**
    * Returns the name of the acquisition data set
    * @return - name
    */
   public String getName() {
      return name_;
   }


   /**
    * Returns number of frames - time dimension.
    * @return - number of frames
    */
   public int getNumberOfFrames() {
      return frames_;
   }

   /**
    * Returns number of slices - focus (Z) dimension.
    * @return - number of slices
    */
   public int getNumberOfSlices() {
      return slices_;
   }

   /**
    * Returns number of channels - usually a wavelength dimension.
    * @return - number of channels
    */   
   public int getNumberOfChannels() {
      return channels_;
   }

   /**
    * Width of the image in pixels.
    */
   public int getImageWidth() {
      return imgWidth_;
   }

   /**
    * Height of the image in pixels.
    */
   public int getImageHeight() {
      return imgHeight_;
   }

   /**
    * Depth of the pixel expressed in bytes.
    */
   public int getPixelDepth() {
      return imgDepth_;
   }

   /**
    * Size of the he pixel in microns.
    * We are assuming square pixels here. In order to deal with non-square pixels
    * use getPixelAspect() 
    */
   public double getPixelSize_um() {
      return pixelSize_um_;
   }

   /**
    * Returns aspect ratio of the pixel X:Y
    * @return - aspect ratio
    */
   public double getPixelAspect() {
      return pixelAspect_;
   }

   /**
    * ImageJ pixel type constant.
    */
   public int getImageJType() {
      return ijType_;
   }
   /**
    * @return the imageInterval
    */
   public double getImageIntervalMs() {
      return imageInterval_ms_;
   }

   /**
    * Sets image interval annotation.
    * @param imageInterval_ms
    */
   public void setImageIntervalMs(double imageInterval_ms) {
      imageInterval_ms_ = imageInterval_ms;
      try {
         summary_.put(SummaryKeys.IMAGE_INTERVAL_MS, imageInterval_ms);
      } catch (JSONException e) {
         ReportingUtils.showError(e);
      }
   }

   /**
    * Returns focus step for slices.
    * @return the imageZStep_um_
    */
   public double getImageZStepUm() {
      return imageZStep_um_;
   }

   /**
    * Sets focus step for slices.
    * @param imageZStep_um
    */
   public void setImageZStepUm(double imageZStep_um) {
      imageZStep_um_ = imageZStep_um;
      try {
         summary_.put(SummaryKeys.IMAGE_Z_STEP_UM, imageZStep_um);
      } catch (JSONException e) {
         ReportingUtils.showError(e);
      }
   }
   
   /**
    * Sets the comment field in the summary section
    * @param comment
    * @throws MMAcqDataException
    */
   public void setComment(String comment) throws MMAcqDataException {
      setSummaryValue(SummaryKeys.COMMENT, comment);
   }
   
   /**
    * Returns the comment field from the summary section
    * @return - comment field
    * @throws MMAcqDataException
    */
   public String getComment() throws MMAcqDataException {
      return getSummaryValue(SummaryKeys.COMMENT);
   }

   /**
    * Defines the pixel size parameter
    * @param pixelSize_um - pixel size in microns
    * @throws MMAcqDataException
    */
   public void setPixelSizeUm(double pixelSize_um) throws MMAcqDataException {
      setSummaryValue(SummaryKeys.IMAGE_PIXEL_SIZE_UM, Double.toString(pixelSize_um));
   }

   /**
    * Returns serialized metadata.
    */
   public String getMetadata() {
      try {
         return metadata_.toString(3);
      } catch (JSONException e) {
         // TODO Auto-generated catch block
         ReportingUtils.showError(e);
         return null;
      }
   }

   public JSONObject getMetadataObject() {
      return metadata_;
   }


   /**
    * Returns channel names.
    * @return - array of channel names
    * @throws MMAcqDataException
    */
   public String[] getChannelNames() throws MMAcqDataException {
      return channelNames_;
   }

   /**
    * Set channel names.
    * This function will resize the number channels according to the length of
    * the names buffer
    * @param names - array of channel names
    * @throws MMAcqDataException 
    */
   public void setChannelNames(String names[]) throws MMAcqDataException {
      channels_ = names.length;
      channelNames_ = names;
      JSONArray channelNames = new JSONArray();
      for (int i=0; i<channels_; i++)
         channelNames.put(names[i]);

      try {
         summary_.put(SummaryKeys.CHANNEL_NAMES_ARRAY, channelNames);
         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
      
      if (!inmemory_)
         writeMetadata();
   }

   /**
    * Sets a channel name for a specified channel index
    * @param channelIdx - channel index
    * @param name - label
    * @throws MMAcqDataException
    */
   public void setChannelName(int channelIdx, String name) throws MMAcqDataException {
      if (channels_ <= channelIdx)
         throw new MMAcqDataException(ERR_CHANNEL_INDEX);

      JSONArray channelNames;

      try {
         if (summary_.has(SummaryKeys.CHANNEL_NAMES_ARRAY))
            channelNames = summary_.getJSONArray(SummaryKeys.CHANNEL_NAMES_ARRAY);
         else
            channelNames = new JSONArray();

         channelNames.put(channelIdx, name);
         summary_.put(SummaryKeys.CHANNEL_NAMES_ARRAY, channelNames);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
      channelNames_[channelIdx] = name;
   }

   /**
    * Returns all available summary attributes as an array of strings.
    * @return - an array of all attributes in the summary data
    * @throws MMAcqDataException 
    */
   @SuppressWarnings("unchecked")
   public String[] getSummaryKeys() throws MMAcqDataException {
      if (summary_ == null)
         throw new MMAcqDataException("No summary data available.");

      String keys[] = new String[summary_.length()];
      int count = 0;
      for (Iterator i = summary_.keys(); i.hasNext();)
         keys[count++] = (String)i.next();
      return keys;
   }
   
   /**
    * Returns the entire summary metadata in the JSON format.
    * TODO: this method exposes internal data formats
    * Consider removing
    * @return - JSON object with summary metadata
    * @throws MMAcqDataException
    */
   public JSONObject getSummaryMetadata() throws MMAcqDataException {
      if (summary_ == null)
         throw new MMAcqDataException("The acquisition data is empty.");
      
      try {
         return new JSONObject(summary_.toString());
      } catch (JSONException e) {
         throw new MMAcqDataException("Internal error. Unable to create a copy of the summary data.");
      }
   }

   /**
    * Returns all available keys associated with the position.
    * "Position" in this context usually means XY location
    * @return - an array of keys
    */
   @SuppressWarnings("unchecked")
   public String[] getPositionPropertyKeys() {
      String keys[] = new String[positionProperties_.length()];
      int count = 0;
      for (Iterator i = positionProperties_.keys(); i.hasNext();)
         keys[count++] = (String)i.next();
      return keys;
   }

   /**
    * Returns a property value associated with the position
    * @param key
    * @return - value
    * @throws JSONException
    */
   public String getPositionProperty(String key) throws JSONException {
      return positionProperties_.getString(key);
   }

   /**
    * Sets a property value associated with the position
    * @param key
    * @throws JSONException
    */
   public void setPositionProperty(String key, String prop) throws MMAcqDataException {
      try {
         positionProperties_.put(key, prop);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }

   /**
    * Returns a summary value associated with the key.
    * @param key
    * @return - value
    * @throws MMAcqDataException
    */
   public String getSummaryValue(String key) throws MMAcqDataException {
      try {
         return summary_.getString(key);
      } catch (JSONException e) {
         throw new MMAcqDataException(e); 
      }
   }

   /**
    * Sets the value for the specified summary key.
    * This method should be used with caution because setting wrong values
    * may violate the integrity of the multidimensional data.
    * @param key
    * @param value
    * @throws MMAcqDataException
    */
   public void setSummaryValue(String key, String value) throws MMAcqDataException {
      try {
         summary_.put(key, value);
      } catch (JSONException e) {
         throw new MMAcqDataException(e); 
      }
   }

   /**
    * Test whether the particular key exists in the summary metadata
    * @param key
    * @return - true if it exists
    */
   public boolean hasSummaryValue(String key) {
      return summary_.has(key);
   }
   
   /**
    * Test whether the particular image coordinate exists
    * @return - true if metadata exists
    * @throws MMAcqDataException 
    */
   public boolean hasImageMetadata(int frame, int channel, int slice) {
      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      return metadata_.has(frameKey);
   }
   
   /**
    * Returns entire metadata associated with a single image in the JSON format.
    * TODO: this method exposes internal data formats. Consider removing.
    * @param frame
    * @param channel
    * @param slice
    * @return - JSON object with image metadata
    * @throws MMAcqDataException
    */
   public JSONObject getImageMetadata(int frame, int channel, int slice) throws MMAcqDataException {
      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      try {
         return metadata_.getJSONObject(frameKey);
      } catch (JSONException e) {
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);
      }

   }

   /**
    * Returns a value associated with the specified key and image coordinates.
    * @param frame
    * @param channel
    * @param slice
    * @param key
    * @return - value
    * @throws MMAcqDataException
    */
   public String getImageValue(int frame, int channel, int slice, String key) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      try {
         JSONObject frameData = metadata_.getJSONObject(frameKey);
         return frameData.getString(key);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }
   
   /**
    * Sets string property value associated with key for a single image, defined by image coordinates
    * @param frame
    * @param channel
    * @param slice
    * @param key
    * @param value
    * @throws MMAcqDataException
    */
   public void setImageValue(int frame, int channel, int slice, String key, String value) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      try {
         JSONObject frameData = metadata_.getJSONObject(frameKey);
         frameData.put(key, value);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }
   
   /**
    * Sets integer property value associated with key for a single image, defined by image coordinates
    * @param frame
    * @param channel
    * @param slice
    * @param key
    * @param value
    * @throws MMAcqDataException
    */
   public void setImageValue(int frame, int channel, int slice, String key, int value) throws MMAcqDataException {
      setImageValue(frame, channel, slice, key, Integer.toString(value));
   }
   
   /**
    * Sets integer property value associated with key for a single image, defined by image coordinates
    * @param frame
    * @param channel
    * @param slice
    * @param key
    * @param value
    * @throws MMAcqDataException
    */
   public void setImageValue(int frame, int channel, int slice, String key, double value) throws MMAcqDataException {
      setImageValue(frame, channel, slice, key, Double.toString(value));
   }
   
   /**
    * Sets entire state data for a single image in the form of the JSON object. The data in JSON object
    * is expected to be flat, i.e. a collection of property value pairs
    * @param frame
    * @param channel
    * @param slice
    * @param state
    * @throws MMAcqDataException
    */
   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      JSONObject stateData;
      try {
         if (metadata_.has(SummaryKeys.SYSTEM_STATE_OBJ))
            stateData = metadata_.getJSONObject(SummaryKeys.SYSTEM_STATE_OBJ);
         else
            stateData = new JSONObject();
         stateData.put(frameKey, state);
         metadata_.put(SummaryKeys.SYSTEM_STATE_OBJ, stateData);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }
   
   /**
    * Returns state data as JSON object, associated with a single image.
    * @param frame
    * @param channel
    * @param slice
    * @return - JSON data object containing a collection of property-value pairs
    * @throws MMAcqDataException
    */
   public JSONObject getSystemState(int frame, int channel, int slice) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);
      
      if (!metadata_.has(SummaryKeys.SYSTEM_STATE_OBJ))
         return new JSONObject();

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      JSONObject stateData;
      try {
         stateData = metadata_.getJSONObject(SummaryKeys.SYSTEM_STATE_OBJ);
         if (stateData.has(frameKey))
            return stateData.getJSONObject(frameKey);
         else
            return new JSONObject();
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }
   
   /**
    * 
    * @param frame
    * @param channel
    * @param slice
    * @param key
    * @return - value
    * @throws MMAcqDataException
    */
   public String getSystemStateValue(int frame, int channel, int slice, String key) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);
      
      if (!metadata_.has(SummaryKeys.SYSTEM_STATE_OBJ))
         return "";

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      try {
         JSONObject stateData = metadata_.getJSONObject(SummaryKeys.SYSTEM_STATE_OBJ);
         JSONObject state = stateData.getJSONObject(frameKey);
         return state.getString(key);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }
 
   /**
    * Returns an entire set of available keys (properties) associated with the specified image coordinates.
    * @param frame
    * @param channel
    * @param slice
    * @return - array of available keys
    * @throws MMAcqDataException
    */
   @SuppressWarnings("unchecked")
   public String[] getImageKeys(int frame, int channel, int slice) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      JSONObject frameData;
      try {
         frameData = metadata_.getJSONObject(frameKey);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      String keys[] = new String[frameData.length()];
      int count = 0;
      for (Iterator i = frameData.keys(); i.hasNext();)
         keys[count++] = (String)i.next();
      return keys;
   }

   /**
    * Returns channel colors.
    * @return - array of colors
    * @throws MMAcqDataException
    */
   public Color[] getChannelColors() throws MMAcqDataException {
      Color colors[] = new Color[channels_];

      if (!summary_.has(SummaryKeys.CHANNEL_COLORS_ARRAY)) {
         for (int i=0; i<channels_; i++) {
            colors[i] = Color.GRAY;
         }
         
         return colors;
      }

      JSONArray metaColors;
      if (channels_ > 0) {
         try {
            metaColors = summary_.getJSONArray(SummaryKeys.CHANNEL_COLORS_ARRAY);
            for (int i=0; i<channels_; i++) {
               colors[i] = new Color(metaColors.getInt(i));
            }
         } catch (JSONException e) {
            throw new MMAcqDataException(e);
         }
      }
      return colors;
   }

   /**
    * Sets channel colors.
    * The number of colors should match the number of channels.
    * @param colors
    * @throws MMAcqDataException
    */
   public void setChannelColors(Color[] colors) throws MMAcqDataException {
      JSONArray jsonColors = new JSONArray();
      for (int i=0; i<colors.length; i++) {
         jsonColors.put(colors[i].getRGB());
      }
      try {
         summary_.put(SummaryKeys.CHANNEL_COLORS_ARRAY, jsonColors);
         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (!inmemory_)
         writeMetadata();
   }

   /**
    * Sets a color associated with a  single channel.
    * @param channel - channel index
    * @param rgb - integer representation of the RGB value
    * @throws MMAcqDataException
    */
   public void setChannelColor(int channel, int rgb) throws MMAcqDataException {
      if (channels_ <= channel)
         throw new MMAcqDataException(ERR_CHANNEL_INDEX);

      JSONArray chanColors;
      try {
         if (summary_.has(SummaryKeys.CHANNEL_COLORS_ARRAY))
            chanColors = summary_.getJSONArray(SummaryKeys.CHANNEL_COLORS_ARRAY);
         else {
            chanColors = new JSONArray();
         }
         chanColors.put(channel, rgb);
         summary_.put(SummaryKeys.CHANNEL_COLORS_ARRAY, chanColors);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }

   /**
    * Returns display settings for channels.
    * @return - an array of settings or null if not available.
    * @throws MMAcqDataException 
    */
   public DisplaySettings[] getChannelDisplaySettings() throws MMAcqDataException {
      JSONArray minContrast = null;
      JSONArray maxContrast = null;
      DisplaySettings settings[] = new DisplaySettings[channels_];
      if (summary_.has(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY) && summary_.has(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY)) {
         try {
            minContrast = summary_.getJSONArray(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY);
            maxContrast = summary_.getJSONArray(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY);
            for (int i=0; i<channels_; i++) {
               settings[i] = new DisplaySettings(minContrast.getDouble(i), maxContrast.getDouble(i));
            }
         } catch (JSONException e) {
            throw new MMAcqDataException(e);
         }
      } else {
         return null;
      }
      return settings;
   }

   /**
    * Sets contrast limits for all channels.
    * @param ds - display settings array
    * @throws MMAcqDataException
    */
   public void setChannelDisplaySettings(DisplaySettings[] ds) throws MMAcqDataException {
      JSONArray minContrast = new JSONArray();
      JSONArray maxContrast = new JSONArray();

      try {
         for (int i=0; i<ds.length; i++) {
            minContrast.put(ds[i].min);
            maxContrast.put(ds[i].max);
         }

         summary_.put(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY, minContrast);
         summary_.put(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY, maxContrast);

         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);

      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (!inmemory_)
         writeMetadata();
   }

   /**
    * Sets display parameters for a single channel 
    * @param channelIdx
    * @param ds
    * @throws MMAcqDataException
    */
   public void setChannelDisplaySetting(int channelIdx, DisplaySettings ds) throws MMAcqDataException {
      JSONArray minContrast;
      JSONArray maxContrast;
      try {
         if (summary_.has(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY))
            minContrast = summary_.getJSONArray(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY);
         else
            minContrast = new JSONArray();

         if (summary_.has(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY))
            maxContrast = summary_.getJSONArray(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY);
         else
            maxContrast = new JSONArray();

         minContrast.put(channelIdx, ds.min);
         maxContrast.put(channelIdx, ds.max);

         summary_.put(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY, minContrast);
         summary_.put(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY, maxContrast);

      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }


   /**
    * Returns pixel array for the entire image specified by coordinates: frame, channel, slice.
    * Pixels are packed by lines. The type of the array depends on the pixel depth (getPixelDepth()), e.g.
    * byte[] (depth 1), or short[] (depth 2).
    * Size of the array is width * height (getImageWidth() and getImageHeight())
    * If the frame is missing, this method returns null.
    * @param frame
    * @param channel
    * @param slice
    * @return - pixel array
    * @throws MMAcqDataException
    */
   public Object getPixels(int frame, int channel, int slice) throws MMAcqDataException {
      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      JSONObject frameData;
      String fileName;
      if (metadata_.has(frameKey)) {
         if (inmemory_) {
            // access pixel data from the memory
            ImageProcessor ip = images_.get(frameKey);
            if (ip == null)
               return null;
            else
               return ip.getPixels();
         } else {
            // access pixel data from a file
            try {
               frameData = metadata_.getJSONObject(frameKey);
               fileName = frameData.getString(ImagePropertyKeys.FILE);
            } catch (JSONException e) {
               throw new MMAcqDataException(e);
            }

            ImagePlus imp;
            try {
               Opener opener = new Opener();
               imp = opener.openTiff(basePath_ + "/", fileName);
            } catch (OutOfMemoryError e) {
               throw new MMAcqDataException("Out of Memory...");
            }
            if (imp == null) {
               throw new MMAcqDataException("Unable to open file " + fileName);
            }

            return imp.getProcessor().getPixels();
         }
      }
      else
         // frame does not exist
         return null;
   }

   /**
    * Returns full path of the image file.
    * 
    * @param frame
    * @param channel
    * @param slice
    * @return String - full file path
    * @throws MMAcqDataException
    */
   public String getImagePath(int frame, int channel, int slice) throws MMAcqDataException {
      if (inmemory_)
         throw new MMAcqDataException("Image path not defined - acquisition data is created in-memory.");   
      return basePath_ + "/" + getImageFileName(frame, channel, slice);
   }
   
   /**
    * Returns the path of the metadata file.
    * @throws MMAcqDataException 
    */
   public String getMetadataPath() throws MMAcqDataException {
      if (inmemory_)
         throw new MMAcqDataException("Metadata path not defined - acquisition data is created in-memory.");   
      return basePath_ + "/" + METADATA_FILE_NAME;
   }

   /**
    * Returns image file name associated with given coordinates
    * @param frame
    * @param channel
    * @param slice
    * @return - full path of the image file
    * @throws MMAcqDataException
    */
   public String getImageFileName(int frame, int channel, int slice) throws MMAcqDataException {

      if (metadata_ == null)
         throw new MMAcqDataException("No image data available.");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      // TODO: change to parsing
      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      JSONObject frameData;
      String fileName;
      try {
         frameData = metadata_.getJSONObject(frameKey);
         fileName = frameData.getString(ImagePropertyKeys.FILE);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      return fileName;
   }

   /**
    * ImageJ specific command, to provide calibration information.
    * @return - Calibration object (ImageJ)
    */
   public Calibration ijCal() {
      Calibration cal = new Calibration();

      if (getPixelSize_um() != 0)
      {
         cal.setUnit("um");
         cal.pixelWidth = getPixelSize_um();
         cal.pixelHeight = getPixelSize_um();
      }

      if (getNumberOfSlices() > 1) {
         double zDist;
         try {
            if (summary_.has(SummaryKeys.IMAGE_Z_STEP_UM))
               zDist = summary_.getDouble(SummaryKeys.IMAGE_Z_STEP_UM);
            else {
               String z1, z2;
               z1 = getImageValue(1, 1, 0, "Z-um");
               z2 = getImageValue(1, 1, 1, "Z-um");
               zDist = Double.valueOf(z2).doubleValue() - Double.valueOf(z1).doubleValue();
            }
         }
         catch (JSONException j) {
            ReportingUtils.logError(j);
            return null;
         }
         catch (MMAcqDataException e) {
            ReportingUtils.logError(e);
            return null;
         }
         cal.pixelDepth = zDist;
         cal.setUnit("um");
      }

      if (getNumberOfFrames() > 1) {
         double interval;
         try {
            if (summary_.has(SummaryKeys.IMAGE_INTERVAL_MS))
               interval = summary_.getDouble(SummaryKeys.IMAGE_INTERVAL_MS);
            else {
               String t1, t2;
               t1 = getImageValue(0, 1, 1, "ElapsedTime-ms");
               t2 = getImageValue(getNumberOfFrames() - 1, 1, 1, "ElapsedTime-ms");
               interval = Double.valueOf(t2).doubleValue() - Double.valueOf(t1).doubleValue();
               interval = interval / (getNumberOfFrames() - 1);
            }
         }
         catch (JSONException j) {
            ReportingUtils.logError(j, "JSON exception in t");
            return null;
         }
         catch (MMAcqDataException e) {
            ReportingUtils.logError(e);
            return null;
         }
         cal.frameInterval = interval;
         cal.setTimeUnit("ms");
      }

      return cal;
   }
   
   /**
    * Determine contrast display settings based on the specified image
    * @param frame
    * @param slice
    * @throws MMAcqDataException 
    */
   public DisplaySettings[] setChannelContrastBasedOnFrameAndSlice(int frame, int slice) throws MMAcqDataException {

      JSONArray minArray = new JSONArray();
      JSONArray maxArray = new JSONArray();
      DisplaySettings[] settings = new DisplaySettings[channels_];
      
      try {
         for (int i=0; i<channels_; i++) {
            Object img = getPixels(frame, i, slice);
            if (img == null)
               throw new MMAcqDataException("Image does not exist for specified coordinates.");
            ImageProcessor ip;
            if (img instanceof byte[])
               ip = new ByteProcessor(imgWidth_, imgHeight_);
            else if (img instanceof short[])
               ip = new ShortProcessor(imgWidth_, imgHeight_);
            else
               throw new MMAcqDataException("Internal error: unrecognized pixel type");

            ip.setPixels(img);
            ImagePlus imp = new ImagePlus("test", ip);
            ImageStatistics istat = imp.getStatistics();
            minArray.put(istat.min);
            maxArray.put(istat.max);
            settings[i] = new DisplaySettings();
            settings[i].min = istat.min;
            settings[i].max = istat.max;
         }
         summary_.put(SummaryKeys.CHANNEL_CONTRAST_MIN_ARRAY, minArray);
         summary_.put(SummaryKeys.CHANNEL_CONTRAST_MAX_ARRAY, maxArray);
         
         metadata_.put(SummaryKeys.SUMMARY_OBJ, summary_);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
      
      if (!inmemory_)
         writeMetadata();
      return settings;
   }

   /**
    * Inserts a single image into the acquisition data set.
    * @param img - image object pixels
    * @param frame - frame (time sample)index
    * @param channel - channel index
    * @param slice - slice (focus) index
    * @return - actual file name without the path
    * @throws MMAcqDataException
    */
   public String insertImage(Object img, int frame, int channel, int slice) throws MMAcqDataException {

      if (metadata_ == null)
         throw new MMAcqDataException("Summary metadata not initialized");

      if (frame <0 || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);
      
      String fname = ImageKey.generateFileName(frame, channelNames_[channel], slice);
      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);

      try {
         // increase frame count if necessary
         if (frame >= frames_) {
            frames_ = frame + 1;
            summary_.put(SummaryKeys.NUM_FRAMES, frames_);
         }
         JSONObject frameData = new JSONObject();
         frameData.put(ImagePropertyKeys.FILE, fname);
         frameData.put(ImagePropertyKeys.FRAME, frame);
         frameData.put(ImagePropertyKeys.CHANNEL, channelNames_[channel]);
         frameData.put(ImagePropertyKeys.SLICE, slice);
         GregorianCalendar gc = new GregorianCalendar();
         frameData.put(ImagePropertyKeys.TIME, gc.getTime());
         frameData.put(ImagePropertyKeys.ELAPSED_TIME_MS, gc.getTimeInMillis() - creationTime_.getTimeInMillis() );
         //frameData.put(ImagePropertyKeys.EXPOSURE_MS, 0.0); // TODO

         metadata_.put(frameKey, frameData);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (inmemory_) {
         // save pixels to memory
         ImageProcessor ip = createCompatibleIJImageProcessor(img);
         images_.put(frameKey, ip);
      } else {
         // save pixels to disk
         saveImageFile(basePath_ + "/" + fname, img, (int)imgWidth_, (int)imgHeight_);
         try {
            lastImageFilePath_ = new File(basePath_ + "/" + fname).getCanonicalPath();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
         //writeMetadata();
      }

      return fname; 
   }
   
   /**
    * Attaches a single image into the acquisition data set.
    * The metadata for a given coordinates must already exist.
    * @param img - image object pixels
    * @param frame - frame (time sample)index
    * @param channel - channel index
    * @param slice - slice (focus) index
    * @return - actual file name without the path
    * @throws MMAcqDataException
    */
   public String attachImage(Object img, int frame, int channel, int slice) throws MMAcqDataException {

      if (metadata_ == null)
         throw new MMAcqDataException("Summary metadata not initialized");

      if (frame <0 || frame >= frames_ || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);
      
      String fname = ImageKey.generateFileName(frame, channelNames_[channel], slice);
      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);
      
      if (!metadata_.has(frameKey))
         throw new MMAcqDataException("Could not attach image file: metadata does not exist for these coordinates.");

      try {
         JSONObject frameData = metadata_.getJSONObject(frameKey);
         frameData.put(ImagePropertyKeys.FILE, fname);
         metadata_.put(frameKey, frameData);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (inmemory_) {
         // save pixels to memory
         ImageProcessor ip = createCompatibleIJImageProcessor(img);
         images_.put(frameKey, ip);
      } else {
         // save pixels to disk
         boolean success = saveImageFile(basePath_ + "/" + fname, img, (int)imgWidth_, (int)imgHeight_);
         try {
            lastImageFilePath_ = new File(basePath_ + "/" + fname).getCanonicalPath();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
         // writeMetadata();
      }

      return fname; 
   }

   /**
    * Inserts a single image metadata into the acquisition data set.
    * This method is used to generate metadata without recording pixel data in any way.
    * @param frame - frame (time sample)index
    * @param channel - channel index
    * @param slice - slice (focus) index
    * @throws MMAcqDataException
    */
   public void insertImageMetadata(int frame, int channel, int slice) throws MMAcqDataException {
      
      if (metadata_ == null)
         throw new MMAcqDataException("Summary metadata not initialized");

      if (frame <0 || channel < 0 || channel >= channels_ || slice < 0 || slice >= slices_)
         throw new MMAcqDataException("Invalid image coordinates (frame,channel,slice): " + frame + "," + channel + "," + slice);

      // increase frame count if necessary
      if (frame >= frames_) {
         frames_ = frame + 1;
      }
      
      String frameKey = ImageKey.generateFrameKey(frame, channel, slice);

      try {
         JSONObject frameData = new JSONObject();
         frameData.put(ImagePropertyKeys.FRAME, frame);
         frameData.put(ImagePropertyKeys.CHANNEL, channelNames_[channel]);
         frameData.put(ImagePropertyKeys.SLICE, slice);
         GregorianCalendar gc = new GregorianCalendar();
         frameData.put(ImagePropertyKeys.TIME, gc.getTime());
         frameData.put(ImagePropertyKeys.ELAPSED_TIME_MS, gc.getTimeInMillis() - creationTime_.getTimeInMillis() );
         frameData.put(ImagePropertyKeys.EXPOSURE_MS, 0.0); // TODO

         metadata_.put(frameKey, frameData);
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }

      if (!inmemory_) {
         //writeMetadata();
      }
   }
   
   /**
    * Utility to save TIF files.
    * No connection to the metadata.
    * @param fname - file path
    * @param img - pixels
    * @param width
    * @param height
    * @return - true if successful
    */
   static public boolean saveImageFile(String fname, Object img, int width, int height) {
      ImageProcessor ip;
      if (img instanceof byte[]) {
         ip = new ByteProcessor(width, height);
         ip.setPixels((byte[])img);
      }
      else if (img instanceof short[]) {
         ip = new ShortProcessor(width, height);
         ip.setPixels((short[])img);
      }
      else if (img instanceof int[]){
         ip= new ColorProcessor( width, height);
         ip.setPixels((int[])img);
      }else
         return false;

      ImagePlus imp = new ImagePlus(fname, ip);
      FileSaver fs = new FileSaver(imp);
      return fs.saveAsTiff(fname);
   }

   ////////////////////////////////////////////////////////////////////////////
   // Private methods
   ////////////////////////////////////////////////////////////////////////////
   /*
   static private String generateRootPath(String name, String baseDir) {
      return baseDir + "/" + generateRootName(name, baseDir);
   }
   */
   
   static private String generateRootName(String name, String baseDir) {
      // create new acquisition directory
      int suffixCounter = 0;
      String testPath;
      File testDir;
      String testName;
      do {
         testName = name + "_" + suffixCounter;
         testPath = new String(baseDir + "/" + testName);
         suffixCounter++;
         testDir = new File(testPath);
      } while (testDir.exists());
      return testName;
   }

   private void reset() {
      metadata_ = new JSONObject();
      summary_ = new JSONObject();
      inmemory_ = true;
      positionProperties_ = new JSONObject();

      frames_=0;
      slices_=0;
      channels_=0;

      imgWidth_= 0;
      imgHeight_= 0;
      imgDepth_= 0;

      pixelSize_um_ = 0.0;
      pixelAspect_ = 1.0;
      ijType_ = 0;

      channelNames_ = new String[0];
      basePath_ = new String();
      name_ = new String();

      creationTime_ = new GregorianCalendar();
   }

   private void writeMetadata() throws MMAcqDataException {
      
      File outDir = new File(basePath_);
      try {
         String metaStream = metadata_.toString(3);
         FileWriter fw = new FileWriter(new File(outDir.getAbsolutePath() + "/" + ImageKey.METADATA_FILE_NAME));
         fw.write(metaStream);
         fw.close();
      } catch (IOException e) {
         reset();
         throw new MMAcqDataException("Unable to create metadata file");
      } catch (JSONException e) {
         reset();
         throw new MMAcqDataException(ERR_METADATA_CREATE);
      }

   }

   private void parse() throws MMAcqDataException {
      try {
         summary_ = metadata_.getJSONObject(SummaryKeys.SUMMARY_OBJ);

         // extract position properties (if any)
         if (summary_.has(SummaryKeys.POSITION_PROPERTIES_OBJ)) {
            positionProperties_ = summary_.getJSONObject(SummaryKeys.POSITION_PROPERTIES_OBJ);
         } else {
            // initialize to empty
            positionProperties_ = new JSONObject();
         }

         // extract summary data
         frames_ = summary_.getInt(SummaryKeys.NUM_FRAMES);
         channels_ = summary_.getInt(SummaryKeys.NUM_CHANNELS);
         slices_ = summary_.getInt(SummaryKeys.NUM_SLICES);
         imgWidth_ = summary_.getInt(SummaryKeys.IMAGE_WIDTH);
         imgHeight_ = summary_.getInt(SummaryKeys.IMAGE_HEIGHT);
         imgDepth_ = summary_.getInt(SummaryKeys.IMAGE_DEPTH);
         ijType_ = summary_.getInt(SummaryKeys.IJ_IMAGE_TYPE);

         if (summary_.has(SummaryKeys.METADATA_VERSION))
            version_ = summary_.getInt(SummaryKeys.METADATA_VERSION);
         else
            version_ = 0; // unknown

         if (summary_.has(SummaryKeys.IMAGE_PIXEL_SIZE_UM))
            pixelSize_um_ = summary_.getDouble(SummaryKeys.IMAGE_PIXEL_SIZE_UM);
         else
            pixelSize_um_ = 0.0; // uncalibrated

         if (summary_.has(SummaryKeys.IMAGE_PIXEL_ASPECT))
            pixelAspect_ = summary_.getDouble(SummaryKeys.IMAGE_PIXEL_ASPECT);
         else
            pixelAspect_ = 1.0; // square pixels are default

         if (summary_.has(SummaryKeys.IMAGE_INTERVAL_MS))
            imageInterval_ms_ = summary_.getDouble(SummaryKeys.IMAGE_INTERVAL_MS);
         else {
            imageInterval_ms_ = 0.0;
         }

         if (summary_.has(SummaryKeys.IMAGE_Z_STEP_UM))
            imageZStep_um_ = summary_.getDouble(SummaryKeys.IMAGE_Z_STEP_UM);
         else {
            imageZStep_um_ = 0.0;
         }

         if (!summary_.has(SummaryKeys.CHANNEL_NAMES_ARRAY)) {
            defaultChannelNames();
         }

         JSONArray metaNames;
         channelNames_ = new String[channels_];
         if (channels_ > 0) {
            try {
               metaNames = summary_.getJSONArray(SummaryKeys.CHANNEL_NAMES_ARRAY);
               for (int i=0; i<channels_; i++) {
                  channelNames_[i] = metaNames.getString(i);
               }
            } catch (JSONException e) {
               throw new MMAcqDataException(e);
            }
         }

      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      }
   }

   private void defaultChannelNames() throws JSONException {
      JSONArray channelNames = new JSONArray();
      channelNames_ = new String[channels_];
      for (int i=0; i<channels_; i++) {
         channelNames_[i] = new String("Channel-" + i);
         channelNames.put(channelNames_[i]);
      }
      summary_.put(SummaryKeys.CHANNEL_NAMES_ARRAY, channelNames);
   }
   
   private ImageProcessor createCompatibleIJImageProcessor(Object img) throws MMAcqDataException {
      ImageProcessor ip = null;
      if (img instanceof byte[]) {
         ip = new ByteProcessor(imgWidth_, imgHeight_);
         ip.setPixels((byte[])img);
      }
      else if (img instanceof short[]) {
         ip = new ShortProcessor(imgWidth_, imgHeight_);
         ip.setPixels((short[])img);
      }
      
      if (ip == null)
         throw new MMAcqDataException("Unrecognized pixel type.");
      else
         return ip;
   }

   public String getLastImageFilePath() {
       return lastImageFilePath_;
   }

   public Boolean inMemory() {
      return inmemory_;
   }
}

