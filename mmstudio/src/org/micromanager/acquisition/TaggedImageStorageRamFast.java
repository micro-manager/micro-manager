/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
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
public class TaggedImageStorageRam implements TaggedImageStorage {
   final ByteOrder nativeOrder = ByteOrder.nativeOrder();
   public static String menuName_ = null;
   private boolean finished_ = false;

   protected TreeMap<String, DirectTaggedImage> imageMap_;
   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private int lastFrame_ = -1;
   private Executor putExecutor_;
   private class DirectTaggedImage {
       ByteBuffer pixelBuffer;
       JSONObject tags;
   }
   
   private DirectTaggedImage taggedImageToDirectTaggedImage(TaggedImage taggedImage) throws JSONException, MMScriptException{
      DirectTaggedImage direct = new DirectTaggedImage();
      direct.tags = taggedImage.tags;
      String type = MDUtils.getPixelType(taggedImage.tags);
      Object pix = taggedImage.pix;
      if (pix instanceof short[]) {
        direct.pixelBuffer = ByteBuffer.allocateDirect(2*((short []) pix).length);
        direct.pixelBuffer.order(nativeOrder);
        direct.pixelBuffer.asShortBuffer().put((short [])pix);
      } else if (pix instanceof byte[]) {
        direct.pixelBuffer = ByteBuffer.allocateDirect(((byte []) pix).length);
        direct.pixelBuffer.put((byte []) pix);
      } 
      return direct;
   }
   
   public TaggedImageStorageRam(JSONObject summaryMetadata) {
      imageMap_ = new TreeMap<String,DirectTaggedImage>(new ImageLabelComparator());
      setSummaryMetadata(summaryMetadata);
      displaySettings_ = new JSONObject();
      putExecutor_ = Executors.newFixedThreadPool(1);
      
   }

    public void putImage(final TaggedImage taggedImage) throws MMException {
        putExecutor_.execute(new Runnable() {
            public void run() {
                String label = MDUtils.getLabel(taggedImage.tags);
                try {
                    imageMap_.put(label, taggedImageToDirectTaggedImage(taggedImage));
                } catch (Exception ex) {
                    ReportingUtils.logError(ex);;
                }
                try {
                    lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImage.tags));
                } catch (Exception ex) {
                    ReportingUtils.logError(ex);
                }
            }
        });
    }

    public TaggedImage getImage(int channel, int slice, int frame, int position) {
        if (imageMap_ == null) {
            return null;
        }

        DirectTaggedImage directImage = imageMap_.get(MDUtils.generateLabel(channel, slice, frame, position));
        if (directImage != null) {
            ShortBuffer shortBuffer = directImage.pixelBuffer.asShortBuffer();
            short[] shorts = new short[shortBuffer.capacity()];
            shortBuffer.get(shorts);
            return new TaggedImage(shorts, directImage.tags);
        }
        return null;
    }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return this.getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   public Set<String> imageKeys() {
      return imageMap_.keySet();
   }

   public void finished() {
      finished_ = true;
   }

   public boolean isFinished() {
      return finished_;
   }

   public void setSummaryMetadata(JSONObject md) {
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

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public void setDisplayAndComments(JSONObject settings) {
      displaySettings_ = settings;
   }

   public JSONObject getDisplayAndComments() {
      return displaySettings_;
   }

   public void close() {
      imageMap_ = null;
      summaryMetadata_ = null;
      displaySettings_ = null;
      // do nothing for now.
   }

   public String getDiskLocation() {
      return null;
   }

   public int lastAcquiredFrame() {
      return lastFrame_;
   }

   public long getDataSetSize() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public void writeDisplaySettings() {
     //Do nothing
   }



}
