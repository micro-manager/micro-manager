/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import ij.ImagePlus;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.UUID;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author arthur
 */
public class MDUtils {
   private final static SimpleDateFormat iso8601modified_ =
           new SimpleDateFormat("yyyy-MM-dd E HH:mm:ss Z");

   public static JSONObject copy(JSONObject map) {
      try {
         return new JSONObject(map.toString());
      } catch (JSONException e) {
         return null;
      }
   }
   
   public static int getPositionIndex(JSONObject map) throws Exception {
      return map.getInt("PositionIndex");
   }

   public static void setPositionIndex(JSONObject map, int positionIndex) throws Exception {
      map.put("PositionIndex", positionIndex);
   }

   public static int getBitDepth(JSONObject map) throws Exception {
      return map.getInt("BitDepth");
   }

   public static void setBitDepth(JSONObject map, int bitDepth) throws Exception {
      map.put("BitDepth", bitDepth);
   }

   public static int getWidth(JSONObject map) throws Exception {
      return map.getInt("Width");
   }

   public static void setWidth(JSONObject map, int width) throws Exception {
      map.put("Width", width);
   }

   public static int getHeight(JSONObject map) throws Exception {
      return map.getInt("Height");
   }

   public static void setHeight(JSONObject map, int height) throws Exception {
      map.put("Height", height);
   }

   public static int getBinning(JSONObject map) throws Exception {
      return map.getInt("Binning");
   }

   public static void setBinning(JSONObject map, int binning) throws Exception {
      map.put("Binning", binning);
   }

   public static int getSliceIndex(JSONObject map) throws Exception {
      return map.getInt("Slice");
   }

   public static void setSliceIndex(JSONObject map, int sliceIndex) throws Exception {
      map.put("Slice", sliceIndex);
   }

   public static int getChannelIndex(JSONObject map) throws Exception {
      return map.getInt("ChannelIndex");
   }

   public static void setChannelIndex(JSONObject map, int channelIndex) throws Exception {
      map.put("ChannelIndex", channelIndex);
   }

   public static int getFrameIndex(JSONObject map) throws Exception {
      return map.getInt("Frame");
   }

   public static void setFrameIndex(JSONObject map, int frameIndex) throws Exception {
      map.put("Frame", frameIndex);
   }

   public static String getPositionName(JSONObject map) throws Exception {
      if (map.has("PositionName") && !map.isNull("PositionName")) {
         return map.getString("PositionName");
      } else {
         return null;
      }
   }

   public static void setPositionName(JSONObject map, String positionName) throws Exception {
      map.put("PositionName", positionName);
   }

   public static String getChannelName(JSONObject map) throws Exception {
      return map.getString("Channel");
   }

   public static String getFileName(JSONObject map) throws Exception {
      return map.getString("FileName");
   }

   public static void setFileName(JSONObject map, String filename) throws Exception {
      map.put("FileName", filename);
   }

   public static String getPixelType(JSONObject map)  throws Exception {
      try {
         return map.getString("PixelType");
      } catch (Exception e) {
         try {
            int ijType = map.getInt("IJType");
            if (ijType == ImagePlus.GRAY8)
               return "GRAY8";
            else if (ijType == ImagePlus.GRAY16)
               return "GRAY16";
            else if (ijType == ImagePlus.COLOR_RGB)
               return "RGB32";
            else throw new Exception();
            // There is no IJType for RGB64.
         } catch (Exception e2) {
            throw new Exception ("Can't figure out pixel type");
         }
      }
   }

   public static void addRandomUUID(JSONObject map) throws Exception {
      UUID uuid = UUID.randomUUID();
      map.put("UUID", uuid.toString());
   }

   public static UUID getUUID(JSONObject map) throws Exception {
      if (map.has("UUID"))
         return UUID.fromString(map.getString("UUID"));
      else
         return null;
   }

   public static void setPixelType(JSONObject map, int type) throws Exception {
      switch (type) {
         case ImagePlus.GRAY8:
            map.put("PixelType", "GRAY8");
         break;
         case ImagePlus.GRAY16:
            map.put("PixelType", "GRAY16");
         break;
         case ImagePlus.COLOR_RGB:
            map.put("PixelType", "RGB32");
         break;
         case 64:
            map.put("PixelType", "RGB64");
         break;
      }
   }

   public static int getSingleChannelType(JSONObject map) throws Exception {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals("GRAY8")) {
         return ImagePlus.GRAY8;
      } else if (pixelType.contentEquals("GRAY16")) {
         return ImagePlus.GRAY16;
      } else if (pixelType.contentEquals("RGB32")) {
         return ImagePlus.GRAY8;
      } else if (pixelType.contentEquals("RGB64")) {
         return ImagePlus.GRAY16;
      } else {
         throw new Exception();
      }
   }

   public static int getNumberOfComponents(JSONObject map) throws Exception {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals("GRAY8"))
           return 1;
      else if (pixelType.contentEquals("GRAY16"))
           return 1;
      else if (pixelType.contentEquals("RGB32"))
           return 3;
      else if (pixelType.contentEquals("RGB64"))
           return 3;
      else {
         throw new Exception("Pixel type not recognized!");
      }
   }

   public static boolean isGRAY8(JSONObject map) throws Exception {
      return getPixelType(map).contentEquals("GRAY8");
   }

   public static boolean isGRAY16(JSONObject map) throws Exception {
      return getPixelType(map).contentEquals("GRAY16");
   }

   public static boolean isRGB32(JSONObject map) throws Exception {
      return getPixelType(map).contentEquals("RGB32");
   }

   public static boolean isRGB64(JSONObject map) throws Exception {
      return getPixelType(map).contentEquals("RGB64");
   }

   public static boolean isGRAY8(TaggedImage img) throws Exception {
      return isGRAY8(img.tags);
   }

   public static boolean isGRAY16(TaggedImage img) throws Exception {
      return isGRAY16(img.tags);
   }

   public static boolean isRGB32(TaggedImage img) throws Exception {
      return isRGB32(img.tags);
   }

   public static boolean isRGB64(TaggedImage img) throws Exception {
      return isRGB64(img.tags);
   }

   public static boolean isGRAY(JSONObject map) throws Exception {
      return (isGRAY8(map) || isGRAY16(map));
   }

   public static boolean isRGB(JSONObject map) throws Exception {
      return (isRGB32(map) || isRGB64(map));
   }

   public static boolean isGRAY(TaggedImage img) throws Exception {
      return isGRAY(img.tags);
   }

   public static boolean isRGB(TaggedImage img) throws Exception {
      return isRGB(img.tags);
   }


   public static void addConfiguration(JSONObject md, Configuration config) throws Exception {
      PropertySetting setting;
      for (int i = 0; i < config.size(); ++i) {
         setting = config.getSetting(i);
         String key = setting.getDeviceLabel() + "-" + setting.getPropertyName();
         String value = setting.getPropertyValue();
         md.put(key, value);
      }
   }


   public static String getLabel(JSONObject md) {
      try {
         return generateLabel(getChannelIndex(md),
                              getSliceIndex(md),
                              getFrameIndex(md),
                              getPositionIndex(md));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public static String generateLabel(int channel, int slice, int frame, int position) {
      return NumberUtils.intToCoreString(channel) + "_"
             + NumberUtils.intToCoreString(slice) + "_"
             + NumberUtils.intToCoreString(frame) + "_"
             + NumberUtils.intToCoreString(position);
   }

   public static int[] getIndices(String label) {
      try {
         int[] indices = new int[4];
         String[] chunks = label.split("_");
         int i = 0;
         for (String chunk : chunks) {
            indices[i] = NumberUtils.coreStringToInt(chunk);
            ++i;
         }
         return indices;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return null;
      }
   }

   public static String[] getKeys(JSONObject md) {
      int n = md.length();
      String [] keyArray = new String[n];
      Iterator<String> keys = md.keys();
      for (int i=0; i<n; ++i) {
         keyArray[i] = (String) keys.next();
      }
      return keyArray;
   }


   public static JSONArray getJSONArrayMember(JSONObject obj, String key) throws JSONException {
      JSONArray theArray;
      try {
         theArray = obj.getJSONArray(key);
      } catch (JSONException e) {
         theArray = new JSONArray(obj.getString(key));
      }
      return theArray;
   }

   public static String getTime(Date time) {
      return iso8601modified_.format(time);
   }

   public static String getCurrentTime() {
      return getTime(new Date());
   }

}
