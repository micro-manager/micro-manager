///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionData.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$
//

package org.micromanager.metadata;

import ij.ImagePlus;
import ij.io.Opener;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Encapsulation of the acquisition data files.
 * Enables the user access pixels and and other data through a simple interface. 
 */
public class AcquisitionData {
   public static final String METADATA_FILE_NAME = "metadata.txt";
   
   
   JSONObject metadata_;
   JSONObject summary_;
   int frames_=0;
   int slices_=0;
   int channels_=0;
   private int imgWidth_=0;
   private int imgHeight_=0;
   private int imgDepth_=0;
   private int ijType_ = 0;
   String basePath_;
   
   /**
    * Constructs an empty object.
    * load() must be called before use.
    */
   public AcquisitionData() {
   }
   
   public static boolean hasMetadata(String dir) {
      File metaFile = new File(dir + "/" + METADATA_FILE_NAME);
      if (metaFile.exists())
         return true;
      else
         return false;
   }
   
   public String getBasePath() {
      return new String(basePath_);
   }
   
   /**
    * Loads the metadata from the acquisition files stored on disk and initializes the object.
    * This method must be called prior to any other calls.
    * @param basePath - acquisition directory path
    * @throws MMAcqDataException
    */
   public void load(String basePath) throws MMAcqDataException {
      // attempt to open metafile
      basePath_ = basePath;
      File metaFile = new File(basePath_ + "/" + METADATA_FILE_NAME);
      if (!metaFile.exists()) {
         throw new MMAcqDataException("Metadata file missing.\n" +
               "Specified directory does not exist or does not contain acquisition data.");
      }
      
      // load meatadata
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
         summary_ = metadata_.getJSONObject(SummaryKeys.SUMMARY);

         // extract summary data
         frames_ = summary_.getInt(SummaryKeys.NUM_FRAMES);
         channels_ = summary_.getInt(SummaryKeys.NUM_CHANNELS);
         slices_ = summary_.getInt(SummaryKeys.NUM_SLICES);
         imgWidth_ = summary_.getInt(SummaryKeys.IMAGE_WIDTH);
         imgHeight_ = summary_.getInt(SummaryKeys.IMAGE_HEIGHT);
         imgDepth_ = summary_.getInt(SummaryKeys.IMAGE_DEPTH);
         ijType_ = summary_.getInt(SummaryKeys.IJ_IMAGE_TYPE);
         
      } catch (JSONException e) {
         throw new MMAcqDataException(e);
      } catch (IOException e) {
         throw new MMAcqDataException(e);
      }
   }
   
   public int getNumberOfFrames() {
      return frames_;
   }
   
   public int getNumberOfSlices() {
      return slices_;
   }
   
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
    * ImageJ pixel type constant.
    */
   public int getImageJType() {
      return ijType_;
   }
   
   /**
    * Returns serialized metadata.
    */
   public String getMetadata() {
      try {
         return metadata_.toString(3);
      } catch (JSONException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
         return null;
      }
   }
   
   /**
    * Returns channel names.
    * @return - array of channel names
    * @throws MMAcqDataException
    */
   public String[] getChannelNames() throws MMAcqDataException {
      JSONArray metaNames;
      String names[] = new String[channels_];
      if (channels_ > 0) {
         try {
            metaNames = summary_.getJSONArray(SummaryKeys.CHANNEL_NAMES);
            for (int i=0; i<channels_; i++) {
               names[i] = metaNames.getString(i);
            }
         } catch (JSONException e) {
            throw new MMAcqDataException(e);
         }
      }
      return names;
   }
   
   /**
    * Returns all available summary attributes as an array of strings.
    * @return - an array of all attributes in the summary data
    * @throws MMAcqDataException 
    */
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
    * Returns a value associated with the key.
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
   
   public boolean hasSummaryValue(String key) {
      return summary_.has(key);
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
    * Returns an entire set of available keys (properties) associated with the specified image coordinates.
    * @param frame
    * @param channel
    * @param slice
    * @return - array of available keys
    * @throws MMAcqDataException
    */
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
      JSONArray metaColors;
      Color colors[] = new Color[channels_];
      if (channels_ > 0) {
         try {
            metaColors = summary_.getJSONArray(SummaryKeys.CHANNEL_COLORS);
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
    * Returns display settings for channels.
    * @return - an array of settings or null if not available.
    * @throws MMAcqDataException 
    */
   public DisplaySettings[] getChannelDisplaySettings() throws MMAcqDataException {
      JSONArray minContrast = null;
      JSONArray maxContrast = null;
      DisplaySettings settings[] = new DisplaySettings[channels_];
      if (summary_.has(SummaryKeys.CHANNEL_CONTRAST_MIN) && summary_.has(SummaryKeys.CHANNEL_CONTRAST_MAX)) {
         try {
            minContrast = summary_.getJSONArray(SummaryKeys.CHANNEL_CONTRAST_MIN);
            maxContrast = summary_.getJSONArray(SummaryKeys.CHANNEL_CONTRAST_MAX);
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
    * Returns pixel array for the entire image specified by coordinates: frame, channel, slice.
    * Pixels are packed by lines. The type of the array depends on the pixel depth (getPixelDepth()), e.g.
    * byte[] (depth 1), or short[] (depth 2).
    * Size of the array is width * height (getImageWidth() and getImageHeight())
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
