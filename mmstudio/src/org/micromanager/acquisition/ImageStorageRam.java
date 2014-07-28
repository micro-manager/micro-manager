
package org.micromanager.acquisition;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;
import org.micromanager.api.data.SummaryMetadata;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultDisplaySettings;
import org.micromanager.data.DefaultImage;
import org.micromanager.data.DefaultMetadata;
import org.micromanager.data.DefaultSummaryMetadata;

import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * RAM-based storage of DefaultImages. Based on the old 
 * ImageStorageRam class.
 */
public class ImageStorageRam implements TaggedImageStorage {   

   private class LRUCache<T,U> extends LinkedHashMap<T,U> {
      final long max_size_;
      
      LRUCache(long max_size) {
         max_size_ = max_size;
      }
      
      @Override
      protected boolean removeEldestEntry(Map.Entry eldest) {
         return super.size() > max_size_;
      }
   }

   private boolean finished_ = false;

   private HashMap<Coords, Image> imageHash_;
   private LRUCache<Coords, Image> lruCache_;
   private SummaryMetadata summaryMetadata_;
   private DisplaySettings displaySettings_;
   private int lastFrame_ = -1;
   
   public ImageStorageRam(JSONObject summaryMetadata) {
      imageHash_ = new HashMap<Coords, Image>();
      setSummaryMetadata(summaryMetadata);
      displaySettings_ = new DefaultDisplaySettings.Builder().build();
      lruCache_ = new LRUCache<Coords, Image>(10);
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      Image image = null;
      try {
         image = new DefaultImage(taggedImage);
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't put image into cache");
         return;
      }
      catch (MMScriptException e) {
         ReportingUtils.logError(e, "Couldn't put image into cache");
         return;
      }
      Coords coords = image.getCoords();
      try {
         lruCache_.put(coords, image);
         imageHash_.put(coords, image);
         // TODO: why do we care about the last frame specifically?
         lastFrame_ = Math.max(lastFrame_, coords.getPositionAt("time"));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   @Override
   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      Coords coords = new DefaultCoords.Builder()
            .position("time", frame)
            .position("slice", slice)
            .position("position", position)
            .position("channel", channel)
            .build();
      return getImage(coords);
   }

   public TaggedImage getImage(Coords coords) {
      if (imageHash_ == null) {
         return null;
      }
      Image image = lruCache_.get(coords);
      if (image == null) {
         image = imageHash_.get(coords);
      }
      return ((DefaultImage) image).legacyToTaggedImage();
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return this.getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   @Override
   public Set<String> imageKeys() {
      HashSet<String> result = new HashSet<String>();
      for (Coords key : imageHash_.keySet()) {
         result.add(key.toString());
      }
      return result;
   }

   @Override
   public void finished() {
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return finished_;
   }

   @Override
   public final void setSummaryMetadata(JSONObject md) {
      summaryMetadata_ = DefaultSummaryMetadata.legacyFromJSON(md);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_.legacyToJSON();
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displaySettings_ = DefaultDisplaySettings.legacyFromJSON(settings);
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displaySettings_.legacyToJSON();
   }

   @Override
   public void close() {
      imageHash_.clear();
      lruCache_.clear();
      summaryMetadata_ = null;
      displaySettings_ = null;
      // do nothing for now.
   }

   @Override
   public String getDiskLocation() {
      return null;
   }

   @Override
   public int lastAcquiredFrame() {
      return lastFrame_;
   }

   @Override
   public long getDataSetSize() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public void writeDisplaySettings() {
     //Do nothing
   }
}
