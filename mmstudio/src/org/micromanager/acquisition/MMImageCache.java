/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.WindowManager;
import java.awt.Color;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import org.micromanager.api.TaggedImageStorage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.prefs.Preferences;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class MMImageCache implements TaggedImageStorage, ImageCache {

   public static String menuName_ = null;
   public ArrayList<ImageCacheListener> imageStorageListeners_ = new ArrayList<ImageCacheListener>();
   private TaggedImageStorage imageStorage_;
   private Set<String> changingKeys_;
   private JSONObject firstTags_;
   private HashMap<String, SoftReference<TaggedImage>> softTable_;
   private int lastFrame_ = -1;
   private JSONObject lastTags_;
   private boolean conserveRam_;
   private VirtualAcquisitionDisplay display_;

   public void addImageCacheListener(ImageCacheListener l) {
      imageStorageListeners_.add(l);
   }

   public ImageCacheListener[] getImageStorageListeners() {
      return (ImageCacheListener[]) imageStorageListeners_.toArray();
   }

   public void removeImageStorageListener(ImageCacheListener l) {
      imageStorageListeners_.remove(l);
   }

   public MMImageCache(TaggedImageStorage imageStorage) {
      imageStorage_ = imageStorage;
      changingKeys_ = new HashSet<String>();
      softTable_ = new HashMap<String, SoftReference<TaggedImage>>();
      conserveRam_ = MMStudioMainFrame.getInstance().getConserveRamOption();
   }

   public void finished() {
      imageStorage_.finished();
      String path = getDiskLocation();
      for (ImageCacheListener l : imageStorageListeners_) {
         l.imagingFinished(path);
      }
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

   public void close() {
      softTable_ = null;
      imageStorage_.close();
   }

   public void saveAs(TaggedImageStorage newImageFileManager) {
      saveAs(newImageFileManager, true);
   }
          
   public void saveAs(TaggedImageStorage newImageFileManager, boolean useNewStorage) {
      if (newImageFileManager == null) {
         return;
      }
      for (String label : imageStorage_.imageKeys()) {
         int pos[] = MDUtils.getIndices(label);
         try {
            newImageFileManager.putImage(getImage(pos[0], pos[1], pos[2], pos[3]));
         } catch (MMException ex) {
            ReportingUtils.logError(ex);
         }
      }
      newImageFileManager.setDisplayAndComments(this.getDisplayAndComments());

      if (useNewStorage) {
         imageStorage_ = newImageFileManager;
      }
   }

   public void putImage(TaggedImage taggedImg) {
      try {
         if (!conserveRam_) {
            softTable_.put(MDUtils.getLabel(taggedImg.tags), new SoftReference(taggedImg));
         }

         checkForChangingTags(taggedImg);
         imageStorage_.putImage(taggedImg);
         synchronized (this) {
            lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImg.tags));
            lastTags_ = taggedImg.tags;
         }

         for (ImageCacheListener l : imageStorageListeners_) {
            l.imageReceived(taggedImg);
         }
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
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      TaggedImage taggedImg = null;
      if (softTable_ == null) {
         return null;
      }
      if (softTable_.containsKey(label)) {
         taggedImg = softTable_.get(label).get();
      }
      if (taggedImg == null) {
         taggedImg = imageStorage_.getImage(channel, slice, frame, position);
         if (taggedImg != null) {
            checkForChangingTags(taggedImg);
            if (!conserveRam_) {
               softTable_.put(label, new SoftReference(taggedImg));
            }
         }
      }
      return taggedImg;
   }

   public JSONObject getImageTags(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      JSONObject tags = null;
      if (softTable_ == null) {
         return null;
      }
      if (softTable_.containsKey(label)) {
         tags = softTable_.get(label).get().tags;
      }
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

   public void setDisplay(VirtualAcquisitionDisplay disp) {
      display_ = disp;
   }

   public ImagePlus getImagePlus() {
      if (display_ == null) {
         return null;
      }
      return display_.getHyperImage();
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
   public void storeChannelDisplaySettings(int channelIndex, int min, int max, double gamma, int histMax) {
      try {
         JSONObject settings = getChannelSetting(channelIndex);
         settings.put("Max", max);
         settings.put("Min", min);
         settings.put("Gamma", gamma);
         settings.put("HistogramMax", histMax);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public JSONObject getChannelSetting(int channel) {
      try {
         JSONArray array = getDisplayAndComments().getJSONArray("Channels");
         if (array != null && !array.isNull(channel)) {
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
         return getChannelSetting(channelIndex).getString("Name");
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

   public void setChannelVisibility(int channelIndex, boolean visible) {
      if (display_ == null) {
         return;
      }
      ImagePlus img = display_.getHyperImage();
      if (!img.isComposite()) {
         return;
      }
      CompositeImage ci = (CompositeImage) img;
      ci.getActiveChannels()[channelIndex] = visible;
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

   public int getNumChannels() {
      JSONArray array;
      try {
         array = getDisplayAndComments().getJSONArray("Channels");
      } catch (Exception ex) {
         return 1;
      }

      return array.length();
   }
}
