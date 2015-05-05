///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageCache.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
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

package acq;

import ij.CompositeImage;
import imagedisplay.DisplayPlus;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import misc.MagelUtils;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.utils.*;

/**
 * MMImageCache: central repository of Images
 * Holds pixels and metadata to be used for display or save on disk
 * 
 * 
 * @author arthur
 */
public class MMImageCache {
   private DisplayPlus display_;
   private MultiResMultipageTiffStorage imageStorage_;
   private Set<String> changingKeys_;
   private JSONObject firstTags_;
   private int lastFrame_ = -1;
   private JSONObject lastTags_;
   private final ExecutorService listenerExecutor_;

   public void setDisplay(DisplayPlus d) {
      display_ = d;
   }

   public MMImageCache(MultiResMultipageTiffStorage imageStorage) {
      imageStorage_ = imageStorage;
      changingKeys_ = new HashSet<String>();
      listenerExecutor_ = Executors.newFixedThreadPool(1);
   }

   public void finished() {
      imageStorage_.finished();
      String path = getDiskLocation();
      display_.imagingFinished(path);
      listenerExecutor_.shutdown();
   }

   public boolean isFinished() {
      return imageStorage_.isFinished();
   }

   public int lastAcquiredFrame() {
      synchronized (this) {
         lastFrame_ = Math.max(imageStorage_.lastAcquiredFrame(), lastFrame_);
         return lastFrame_;
      }
   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

   public void setDisplayAndComments(JSONObject settings) {
      imageStorage_.setDisplayAndComments(settings);
   }

   public JSONObject getDisplayAndComments() {
      return imageStorage_.getDisplayAndComments();
   }
   
   public void writeDisplaySettings() {
      imageStorage_.writeDisplaySettings();
   }

   public void close() {
      imageStorage_.close();
      display_ = null;
   }

   public void saveAs(MultiResMultipageTiffStorage newImageFileManager) {
      saveAs(newImageFileManager, true);
      this.finished();
   }
          
   public void saveAs(final MultiResMultipageTiffStorage newImageFileManager, final boolean useNewStorage) {
      if (newImageFileManager == null) {
         return;
      }

      newImageFileManager.setSummaryMetadata(imageStorage_.getSummaryMetadata());
      newImageFileManager.setDisplayAndComments(this.getDisplayAndComments());

//      final String progressBarTitle = (newImageFileManager instanceof TaggedImageStorageRamFast) ? "Loading images..." : "Saving images...";
      final String progressBarTitle =  "Saving images...";
      final ProgressBar progressBar = new ProgressBar(progressBarTitle, 0, 100);
      ArrayList<String> keys = new ArrayList<String>(imageKeys());
      final int n = keys.size();
      progressBar.setRange(0, n);
      progressBar.setProgress(0);
      progressBar.setVisible(true);
      boolean wasSuccessful = true;
      for (int i = 0; i < n; ++i) {
         final int i1 = i;
         int pos[] = MagelUtils.getIndices(keys.get(i));
         try {
            newImageFileManager.putImage(getImage(pos[0], pos[1], pos[2], pos[3]));
         } catch (MMException ex) {
            ReportingUtils.logError(ex);
         } 
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               progressBar.setProgress(i1);
            }
         });
      }
      if (wasSuccessful) {
         // Successfully saved all images.
         newImageFileManager.finished();
      }
      progressBar.setVisible(false);
      if (useNewStorage) {
         imageStorage_ = newImageFileManager;
      }
   }

   public void putImage(final TaggedImage taggedImg) {
      try {
         
         checkForChangingTags(taggedImg);
         imageStorage_.putImage(taggedImg);
         
           synchronized (this) {
            lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImg.tags));
            lastTags_ = taggedImg.tags;
         }
         JSONObject displayAndComments = imageStorage_.getDisplayAndComments();
         if (displayAndComments.length() > 0) {
            JSONArray channelSettings = imageStorage_.getDisplayAndComments().getJSONArray("Channels");
            JSONObject imageTags = taggedImg.tags;
            int chanIndex = MDUtils.getChannelIndex(imageTags);
            if (chanIndex >= channelSettings.length()) {
               JSONObject newChanObject = new JSONObject();
               MDUtils.setChannelName(newChanObject, MDUtils.getChannelName(imageTags));
               MDUtils.setChannelColor(newChanObject, MDUtils.getChannelColor(imageTags));
               channelSettings.put(chanIndex, newChanObject);
            }
         }

         listenerExecutor_.submit(
                 new Runnable() {

                    @Override
                    public void run() {
                       display_.imageReceived(taggedImg);
                    }
                 });
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public JSONObject getLastImageTags() {
      synchronized (this) {
         return lastTags_;
      }
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      TaggedImage taggedImg = null;
      if (taggedImg == null) {
         taggedImg = imageStorage_.getImage(channel, slice, frame, position);
         if (taggedImg != null) {
            checkForChangingTags(taggedImg);
         }
      }
      return taggedImg;
   }

   public JSONObject getImageTags(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      JSONObject tags = null;
      if (tags == null) {
         tags = imageStorage_.getImageTags(channel, slice, frame, position);
      }
      return tags;
   }

   private void checkForChangingTags(TaggedImage taggedImg) {
      if (firstTags_ == null) {
         firstTags_ = taggedImg.tags;
      } else {
         Iterator<String> keys = taggedImg.tags.keys();
         while (keys.hasNext()) {
            String key = keys.next();
            try {
               if (!taggedImg.tags.isNull(key)) {
                  if (!firstTags_.has(key) || firstTags_.isNull(key)) {
                     changingKeys_.add(key);
                  } else if (!taggedImg.tags.getString(key).contentEquals(firstTags_.getString(key))) {
                     changingKeys_.add(key);
                  }
               }
            } catch (Exception e) {
               ReportingUtils.logError(e);
            }
         }
      }
   }

   private JSONObject getCommentsJSONObject() {
      if (imageStorage_ == null) {
         ReportingUtils.logError("imageStorage_ is null in getCommentsJSONObject");
         return null;
      }

      JSONObject comments;
      try {
         comments = imageStorage_.getDisplayAndComments().getJSONObject("Comments");
      } catch (JSONException ex) {
         comments = new JSONObject();
         try {
            imageStorage_.getDisplayAndComments().put("Comments", comments);
         } catch (JSONException ex1) {
            ReportingUtils.logError(ex1);
         }
      }
      return comments;
   }

   public boolean getIsOpen() {
      return (getDisplayAndComments() != null);
   }

   public void setComment(String text) {
      JSONObject comments = getCommentsJSONObject();
      try {
         comments.put("Summary", text);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public void setImageComment(String comment, JSONObject tags) {
      JSONObject comments = getCommentsJSONObject();
      String label = MDUtils.getLabel(tags);
      try {
         comments.put(label, comment);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }

   }

   public String getImageComment(JSONObject tags) {
      if (tags == null) {
         return "";
      }
      try {
         String label = MDUtils.getLabel(tags);
         return getCommentsJSONObject().getString(label);
      } catch (Exception ex) {
         return "";
      }
   }

   public String getComment() {
      try {
         return getCommentsJSONObject().getString("Summary");
      } catch (Exception ex) {
         return "";
      }
   }

   public JSONObject getSummaryMetadata() {
      if (imageStorage_ == null) {
         ReportingUtils.logError("imageStorage_ is null in getSummaryMetadata");
         return null;
      }
      return imageStorage_.getSummaryMetadata();
   }

   public void setSummaryMetadata(JSONObject tags) {
      if (imageStorage_ == null) {
         ReportingUtils.logError("imageStorage_ is null in setSummaryMetadata");
         return;
      }
      imageStorage_.setSummaryMetadata(tags);
   }

   public Set<String> getChangingKeys() {
      return changingKeys_;
   }

   public Set<String> imageKeys() {
     return imageStorage_.imageKeys();
   }

   private boolean isRGB() throws JSONException, MMScriptException {
      return MDUtils.isRGB(getSummaryMetadata());
   }

   public String getPixelType() {
      try {
         return MDUtils.getPixelType(getSummaryMetadata());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   /////////////////////Channels section/////////////////////////
   /*
    * this function gets called whenever contrast settings are changed 
    */
   public void storeChannelDisplaySettings(int channelIndex, int min, int max, 
           double gamma, int histMax, int displayMode) {
      try {
         JSONObject settings = getChannelSetting(channelIndex);
         settings.put("Max", max);
         settings.put("Min", min);
         settings.put("Gamma", gamma);
         settings.put("HistogramMax", histMax);
         settings.put("DisplayMode", displayMode);         
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
  
   public JSONObject getChannelSetting(int channel) {
      try {
         JSONArray array = getDisplayAndComments().getJSONArray("Channels");
         if (channel >= array.length()) {
            //expand size
            array.put(channel, new JSONObject(array.getJSONObject(0).toString()));
         }
         if (!array.isNull(channel)) {
            return array.getJSONObject(channel);
         } else {
            return null;
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public int getBitDepth() {
      try {
         return imageStorage_.getSummaryMetadata().getInt("BitDepth");
      } catch (JSONException ex) {
         ReportingUtils.logError("MMImageCache.BitDepth: no tag BitDepth found");
      }
      return 16;
   }

   public Color getChannelColor(int channelIndex) {
      try {
         if (isRGB()) {
            return channelIndex == 0 ? Color.red : (channelIndex == 1 ? Color.green : Color.blue);
         }
         return new Color(getChannelSetting(channelIndex).getInt("Color"));
      } catch (Exception ex) {
         return Color.WHITE;
      }
   }

   public void setChannelColor(int channel, int rgb) {
      JSONObject chan = getChannelSetting(channel);
      try {
         if (chan == null) {
            return;  //no channel settings for rgb images
         }
         chan.put("Color", rgb);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public String getChannelName(int channelIndex) {
      try {
         if (isRGB()) {
            return channelIndex == 0 ? "Red" : (channelIndex == 1 ? "Green" : "Blue");
         }
         JSONObject channelSetting = getChannelSetting(channelIndex);
         if (channelSetting.has("Name")) {
            return channelSetting.getString("Name");
         }
         return "";
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }

   public void setChannelName(int channel, String channelName) {
      try {
         if (isRGB()) {
            return;
         }
         JSONObject displayAndComments = getDisplayAndComments();
         JSONArray channelArray;
         if (displayAndComments.has("Channels")) {
            channelArray = displayAndComments.getJSONArray("Channels");
         } else {
            channelArray = new JSONArray();
            displayAndComments.put("Channels", channelArray);
         }
         if (channelArray.isNull(channel)) {
            channelArray.put(channel, new JSONObject().put("Name", channelName));
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

   }
   
   public int getDisplayMode() {
      try {
         return getChannelSetting(0).getInt("DisplayMode");
      } catch (JSONException ex) {
         return CompositeImage.COMPOSITE;
      }
   }

   public int getChannelMin(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getInt("Min");
      } catch (Exception ex) {
         return 0;
      }
   }

   public int getChannelMax(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getInt("Max");
      } catch (Exception ex) {
         return -1;
      }
   }

   public double getChannelGamma(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getDouble("Gamma");
      } catch (Exception ex) {
         return 1.0;
      }
   }
   
   public int getChannelHistogramMax(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getInt("HistogramMax");
      } catch (JSONException ex) {
         return -1;
      }
   }

   public int getNumDisplayChannels() {
      JSONArray array;
      try {
         array = getDisplayAndComments().getJSONArray("Channels");
      } catch (Exception ex) {
         return 1;
      }

      return array.length();
   }

   public long getDataSetSize() {
      throw new UnsupportedOperationException("Not supported yet.");
   }
}
