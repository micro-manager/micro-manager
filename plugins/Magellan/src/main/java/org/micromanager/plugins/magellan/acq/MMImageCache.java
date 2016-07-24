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

package org.micromanager.plugins.magellan.acq;

import ij.CompositeImage;
import org.micromanager.plugins.magellan.imagedisplay.DisplayPlus;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONException;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.MD;

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

   public void putImage(final MagellanTaggedImage taggedImg) {
      try {
         
         checkForChangingTags(taggedImg);
         imageStorage_.putImage(taggedImg);
         
           synchronized (this) {
            lastFrame_ = Math.max(lastFrame_, MD.getFrameIndex(taggedImg.tags));
            lastTags_ = taggedImg.tags;
         }
         JSONObject displayAndComments = imageStorage_.getDisplayAndComments();
         if (displayAndComments.length() > 0) {
            JSONArray channelSettings = imageStorage_.getDisplayAndComments().getJSONArray("Channels");
            JSONObject imageTags = taggedImg.tags;
//            int chanIndex = MD.getChannelIndex(imageTags);
//            if (chanIndex >= channelSettings.length()) {
//               JSONObject newChanObject = new JSONObject();
//               MD.setChannelName(newChanObject, MD.getChannelName(imageTags));
//               MD.setChannelColor(newChanObject, MD.getChannelColor(imageTags));
//               channelSettings.put(chanIndex, newChanObject);
//            }
         }

         listenerExecutor_.submit(
                 new Runnable() {

                    @Override
                    public void run() {
                       display_.imageReceived(taggedImg);
                    }
                 });
      } catch (Exception ex) {
         Log.log(ex, true);
      }
   }

   public JSONObject getLastImageTags() {
      synchronized (this) {
         return lastTags_;
      }
   }

   public MagellanTaggedImage getImage(int channel, int slice, int frame, int position) {
      MagellanTaggedImage taggedImg = null;
      if (taggedImg == null) {
         taggedImg = imageStorage_.getImage(channel, slice, frame, position);
         if (taggedImg != null) {
            checkForChangingTags(taggedImg);
         }
      }
      return taggedImg;
   }

   public JSONObject getImageTags(int channel, int slice, int frame, int position) {
      String label = MD.generateLabel(channel, slice, frame, position);
      JSONObject tags = null;
      if (tags == null) {
         tags = imageStorage_.getImageTags(channel, slice, frame, position);
      }
      return tags;
   }

   private void checkForChangingTags(MagellanTaggedImage taggedImg) {
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
               Log.log(e);
            }
         }
      }
   }

   public boolean getIsOpen() {
      return (getDisplayAndComments() != null);
   }

   public JSONObject getSummaryMetadata() {
      if (imageStorage_ == null) {
         Log.log("imageStorage_ is null in getSummaryMetadata", true);
         return null;
      }
      return imageStorage_.getSummaryMetadata();
   }

   public void setSummaryMetadata(JSONObject tags) {
      if (imageStorage_ == null) {
         Log.log("imageStorage_ is null in setSummaryMetadata", true);
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

   private boolean isRGB() throws JSONException {
      return MD.isRGB(getSummaryMetadata());
   }

   public String getPixelType() {
      try {
         return MD.getPixelType(getSummaryMetadata());
      } catch (Exception ex) {
         Log.log(ex);
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
         Log.log(ex);
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
         Log.log(ex);
         return null;
      }
   }

   public int getBitDepth() {
      try {
         return imageStorage_.getSummaryMetadata().getInt("BitDepth");
      } catch (JSONException ex) {
         Log.log("MMImageCache.BitDepth: no tag BitDepth found", true);
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
         Log.log(ex);
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
         Log.log(ex);
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
         Log.log(ex);
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
         JSONObject channelSetting = getChannelSetting(channelIndex);
         return channelSetting != null ? channelSetting.getInt("Min") : 0 ;
      } catch (Exception ex) {
         return 0;
      }
   }

   public int getChannelMax(int channelIndex) {
      try {
         JSONObject channelSetting = getChannelSetting(channelIndex);
         return channelSetting != null ? channelSetting.getInt("Max") : 1 << (8*Magellan.getCore().getBytesPerPixel()) - 1 ;
      } catch (Exception ex) {
         return -1;
      }
   }

   public double getChannelGamma(int channelIndex) {
      try {
        JSONObject channelSetting = getChannelSetting(channelIndex);
        return channelSetting != null ? channelSetting.getInt("Gamma") : 1.0 ;
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
