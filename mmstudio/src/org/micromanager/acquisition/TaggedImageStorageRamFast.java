/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.io.UnsupportedEncodingException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
public class TaggedImageStorageRamFast implements TaggedImageStorage {
   final ByteOrder nativeOrder = ByteOrder.nativeOrder();
   public static String menuName_ = null;
   private boolean finished_ = false;

   private TreeMap<String, DirectTaggedImage> imageMap_;
   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private int lastFrame_ = -1;
   
   private class DirectTaggedImage {
       Buffer pixelBuffer;
       ByteBuffer tagsBuffer;
   }
   
   public TaggedImageStorageRamFast(JSONObject summaryMetadata) {
      imageMap_ = new TreeMap<String, DirectTaggedImage>(new ImageLabelComparator());
      setSummaryMetadata(summaryMetadata);
      displaySettings_ = new JSONObject();
   }

   private ByteBuffer bufferFromBytes(byte[] bytes) {
      return ByteBuffer.allocateDirect(bytes.length).put(bytes);
   }
   
   private ShortBuffer bufferFromShorts(short[] shorts) {
      return ByteBuffer.allocateDirect(2*shorts.length).order(nativeOrder).asShortBuffer().put(shorts);
   }
   
   private IntBuffer bufferFromInts(int[] ints) {
      return ByteBuffer.allocateDirect(4*ints.length).order(nativeOrder).asIntBuffer().put(ints);
   }
      
   private byte[] bytesFromBuffer(ByteBuffer buffer) {
      byte[] bytes = new byte[buffer.capacity()];
      buffer.rewind();
      buffer.get(bytes);
      return bytes;
   }
   
   private short[] shortsFromBuffer(ShortBuffer buffer) {
      short[] shorts = new short[buffer.capacity()];
      buffer.rewind();
      buffer.get(shorts);
      return shorts;
   }

   private int[] intsFromBuffer(IntBuffer buffer) {
      int[] ints = new int[buffer.capacity()];
      buffer.rewind();
      buffer.get(ints);
      return ints;
   }

   private Object arrayFromBuffer(Buffer buffer) {
      if (buffer instanceof ByteBuffer) {
         return bytesFromBuffer((ByteBuffer) buffer);
      } else if (buffer instanceof ShortBuffer) {
         return shortsFromBuffer((ShortBuffer) buffer);
      } else if (buffer instanceof IntBuffer) {
         return intsFromBuffer((IntBuffer) buffer);
      }
      return null;
   }
   
   private Buffer bufferFromArray(Object primitiveArray) {
      if (primitiveArray instanceof byte[]) {
         return bufferFromBytes((byte []) primitiveArray);
      } else if (primitiveArray instanceof short[]) {
         return bufferFromShorts((short []) primitiveArray);
      } else if (primitiveArray instanceof int[]) {
         return bufferFromInts((int []) primitiveArray);
      }
      return null;
   }
   
   private ByteBuffer bufferFromString(String string) {
      try {
         return bufferFromBytes(string.getBytes("UTF-8"));
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   private String stringFromBuffer(ByteBuffer byteBuffer) {
      try {
         return new String(bytesFromBuffer(byteBuffer), "UTF-8");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }
   
   private ByteBuffer bufferFromJSON(JSONObject json) {
      return bufferFromString(json.toString());
   }
   
   private JSONObject JSONFromBuffer(ByteBuffer byteBuffer) throws JSONException {
      return new JSONObject(stringFromBuffer(byteBuffer));
   }
   
   private DirectTaggedImage taggedImageToDirectTaggedImage(TaggedImage taggedImage) throws JSONException, MMScriptException{
      DirectTaggedImage direct = new DirectTaggedImage();
      direct.tagsBuffer = bufferFromJSON(taggedImage.tags);
      direct.pixelBuffer = bufferFromArray(taggedImage.pix);
      return direct;
   }
   
   private TaggedImage directTaggedImageToTaggedImage(DirectTaggedImage directImage) {
        if (directImage != null) {
            try {
                return new TaggedImage(arrayFromBuffer(directImage.pixelBuffer),
                                       JSONFromBuffer(directImage.tagsBuffer));
            } catch (JSONException ex) {
               ReportingUtils.logError(ex);
               return null;
            }
        } else {
           return null;
        }
   }
   
   public void putImage(final TaggedImage taggedImage) throws MMException {
      String label = MDUtils.getLabel(taggedImage.tags);
      try {
         imageMap_.put(label, taggedImageToDirectTaggedImage(taggedImage));
         lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImage.tags));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

    public TaggedImage getImage(int channel, int slice, int frame, int position) {
        if (imageMap_ == null) {
            return null;
        }
        return directTaggedImageToTaggedImage(imageMap_.get(MDUtils.generateLabel(channel, slice, frame, position)));
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
