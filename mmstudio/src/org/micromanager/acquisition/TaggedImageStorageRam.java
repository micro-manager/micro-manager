
package org.micromanager.acquisition;

import java.util.Set;
import java.util.TreeMap;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 *
 * This class stores a collection of TaggedImages, all in ram.
 */
public class TaggedImageStorageRam implements TaggedImageStorage {
   private boolean finished_ = false;

   protected TreeMap<String, TaggedImage> imageMap_;
   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private int lastFrame_ = -1;
   
   public TaggedImageStorageRam(JSONObject summaryMetadata) {
      imageMap_ = new TreeMap<String,TaggedImage>(new ImageLabelComparator());
      setSummaryMetadata(summaryMetadata);
      displaySettings_ = new JSONObject();
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      String label = MDUtils.getLabel(taggedImage.tags);
      imageMap_.put(label, taggedImage);
      try {
         lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImage.tags));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   @Override
   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      if (imageMap_ == null)
         return null;
      return imageMap_.get(MDUtils.generateLabel(channel, slice, frame, position));
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
            TreeMap<String, TaggedImage> oldImageMap = imageMap_;
            imageMap_ = new TreeMap<String,TaggedImage>(new ImageLabelComparator(slicesFirst,timeFirst));    
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
      imageMap_ = null;
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
