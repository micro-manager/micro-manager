
package org.micromanager.acquisition;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.DirectBuffers;
import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 *
 * This class stores a collection of TaggedImages, all in ram.
 */
public class TaggedImageStorageRamFast implements TaggedImageStorage {

   
   private class DirectTaggedImage {
       Buffer pixelBuffer;
       ByteBuffer tagsBuffer;
   }
   
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

   private TreeMap<String, DirectTaggedImage> imageMap_;
   private LRUCache<String, TaggedImage> lruCache_;
   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private int lastFrame_ = -1;

   private String diskLocation_;
   
   public TaggedImageStorageRamFast(JSONObject summaryMetadata) {
      imageMap_ = new TreeMap<String, DirectTaggedImage>(new ImageLabelComparator());
      setSummaryMetadata(summaryMetadata);
      displaySettings_ = new JSONObject();
      lruCache_ = new LRUCache<String, TaggedImage>(10);
   }

   private ByteBuffer bufferFromJSON(JSONObject json) {
      return DirectBuffers.bufferFromString(json.toString());
   }
   
   private JSONObject JSONFromBuffer(ByteBuffer byteBuffer) throws JSONException {
      return new JSONObject(DirectBuffers.stringFromBuffer(byteBuffer));
   }
   
   private DirectTaggedImage taggedImageToDirectTaggedImage(TaggedImage taggedImage) throws JSONException, MMScriptException{
      DirectTaggedImage direct = new DirectTaggedImage();
      direct.tagsBuffer = bufferFromJSON(taggedImage.tags);
      direct.pixelBuffer = DirectBuffers.bufferFromArray(taggedImage.pix);
      return direct;
   }
   
   private TaggedImage directTaggedImageToTaggedImage(DirectTaggedImage directImage) {
        if (directImage != null) {
            try {
                return new TaggedImage(DirectBuffers.arrayFromBuffer(directImage.pixelBuffer),
                                       JSONFromBuffer(directImage.tagsBuffer));
            } catch (JSONException ex) {
               ReportingUtils.logError(ex);
               return null;
            }
        } else {
           return null;
        } 
   }
   
   @Override
   public void putImage(final TaggedImage taggedImage) throws MMException {
      String label = MDUtils.getLabel(taggedImage.tags);
      try {
         // Allocate the direct tagged image before altering any data, in case
         // OutOfMemoryError is thrown.
         DirectTaggedImage directImage =
               taggedImageToDirectTaggedImage(taggedImage);

         lruCache_.put(label, taggedImage);
         imageMap_.put(label, directImage);
         lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImage.tags));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   @Override
    public TaggedImage getImage(int channel, int slice, int frame, int position) {
        if (imageMap_ == null) {
            return null;
        }
        String label = MDUtils.generateLabel(channel, slice, frame, position);
        TaggedImage cachedImage = lruCache_.get(label);
        if (cachedImage != null) {
           return cachedImage;
        } else { // cache miss
           return directTaggedImageToTaggedImage(imageMap_.get(label));
        }
    }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return this.getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   @Override
   public Set<String> imageKeys() {
      return imageMap_.keySet();
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
      summaryMetadata_ = md;
      if (summaryMetadata_ != null) {
         try {
            boolean slicesFirst = summaryMetadata_.getBoolean("SlicesFirst");
            boolean timeFirst = summaryMetadata_.getBoolean("TimeFirst");
            TreeMap<String, DirectTaggedImage> oldImageMap = imageMap_;
            imageMap_ = new TreeMap<String,DirectTaggedImage>(new ImageLabelComparator(slicesFirst,timeFirst));    
            imageMap_.putAll(oldImageMap);
         } catch (JSONException ex) {
            ReportingUtils.logError("Couldn't find SlicesFirst or TimeFirst in summary metadata");
         }
      }  
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displaySettings_ = settings;
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displaySettings_;
   }

   @Override
   public void close() {
      imageMap_.clear();
      lruCache_.clear();
      summaryMetadata_ = null;
      displaySettings_ = null;
      // do nothing for now.
   }

   /**
    * We allow outsiders to tell us that we represent data at a specific
    * location, even though all of our data is actually in RAM now.
    */
   public void setDiskLocation(String diskLocation) {
      diskLocation_ = diskLocation;
   }

   @Override
   public String getDiskLocation() {
      return diskLocation_;
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
