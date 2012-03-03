/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import ij.ImagePlus;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.UUID;
import mmcorej.CMMCore;
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
   
   public static int getPositionIndex(JSONObject map) throws JSONException {
      return map.getInt("PositionIndex");
   }

   public static void setPositionIndex(JSONObject map, int positionIndex) throws JSONException {
      map.put("PositionIndex", positionIndex);
   }

   public static int getBitDepth(JSONObject map) throws JSONException {
      if (map.has("Summary"))
         return map.getJSONObject("Summary").getInt("BitDepth");
      return map.getInt("BitDepth");
   }

   public static int getWidth(JSONObject map) throws JSONException {
      return map.getInt("Width");
   }

   public static void setWidth(JSONObject map, int width) throws JSONException {
      map.put("Width", width);
   }

   public static int getHeight(JSONObject map) throws JSONException {
      return map.getInt("Height");
   }

   public static void setHeight(JSONObject map, int height) throws JSONException {
      map.put("Height", height);
   }

   public static int getBinning(JSONObject map) throws JSONException {
      return map.getInt("Binning");
   }

   public static void setBinning(JSONObject map, int binning) throws JSONException {
      map.put("Binning", binning);
   }

   public static int getSliceIndex(JSONObject map) throws JSONException {
      return map.getInt("SliceIndex");
   }

   public static void setSliceIndex(JSONObject map, int sliceIndex) throws JSONException {
      map.put("SliceIndex", sliceIndex);
   }

   public static int getChannelIndex(JSONObject map) throws JSONException {
      return map.getInt("ChannelIndex");
   }

   public static void setChannelIndex(JSONObject map, int channelIndex) throws JSONException {
      map.put("ChannelIndex", channelIndex);
   }

   public static int getFrameIndex(JSONObject map) throws JSONException {
      if (map.has("Frame")) {
         return map.getInt("Frame");
      } else {
         return map.getInt("FrameIndex");
      }
   }

   public static void setFrameIndex(JSONObject map, int frameIndex) throws JSONException {
      map.put("Frame", frameIndex);
   }

   public static String getPositionName(JSONObject map) throws JSONException {
      if (map.has("PositionName") && !map.isNull("PositionName")) {
         return map.getString("PositionName");
      } else {
         return null;
      }
   }

   public static void setPositionName(JSONObject map, String positionName) throws JSONException {
      map.put("PositionName", positionName);
   }

   public static String getChannelName(JSONObject map) throws JSONException {
      if (map.has("Channel") && !map.isNull("Channel")) {
         return map.getString("Channel");
      } else {
         return "";
      }
   }

   public static int getChannelColor(JSONObject map) throws JSONException {
      if (map.has("ChColor") && !map.isNull("ChColor")) {
         return map.getInt("ChColor");
      } else {
         return -1;
      }
   }

   public static String getFileName(JSONObject map) throws JSONException {
      return map.getString("FileName");
   }

   public static void setFileName(JSONObject map, String filename) throws JSONException {
      map.put("FileName", filename);
   }

   public static String getPixelType(JSONObject map)  throws JSONException, MMScriptException {
      try {
         return map.getString("PixelType");
      } catch (JSONException e) {
         try {
            int ijType = map.getInt("IJType");
            if (ijType == ImagePlus.GRAY8)
               return "GRAY8";
            else if (ijType == ImagePlus.GRAY16)
               return "GRAY16";
            else if (ijType == ImagePlus.GRAY32)
               return "GRAY32";
            else if (ijType == ImagePlus.COLOR_RGB)
               return "RGB32";
            else throw new MMScriptException("Can't figure out pixel type");
            // There is no IJType for RGB64.
         } catch (MMScriptException e2) {
            throw new MMScriptException ("Can't figure out pixel type");
         }
      }
   }

   public static void addRandomUUID(JSONObject map) throws JSONException {
      UUID uuid = UUID.randomUUID();
      map.put("UUID", uuid.toString());
   }

   public static UUID getUUID(JSONObject map) throws JSONException {
      if (map.has("UUID"))
         return UUID.fromString(map.getString("UUID"));
      else
         return null;
   }

   public static void setPixelType(JSONObject map, int type) throws JSONException {
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

      public static void setPixelTypeFromByteDepth(JSONObject map, int depth) throws JSONException {
      switch (depth) {
         case 1:
            map.put("PixelType", "GRAY8");
         break;
         case 2:
            map.put("PixelType", "GRAY16");
         break;
         case 4:
            map.put("PixelType", "RGB32");
         break;
         case 8:
            map.put("PixelType", "RGB64");
         break;
      }
   }


   public static int getSingleChannelType(JSONObject map) throws JSONException, MMScriptException {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals("GRAY8")) {
         return ImagePlus.GRAY8;
      } else if (pixelType.contentEquals("GRAY16")) {
         return ImagePlus.GRAY16;
      } else if (pixelType.contentEquals("GRAY32")) {          
         return ImagePlus.GRAY32;
      } else if (pixelType.contentEquals("RGB32")) {
         return ImagePlus.GRAY8;
      } else if (pixelType.contentEquals("RGB64")) {
         return ImagePlus.GRAY16;
      } else {
         throw new MMScriptException("Can't figure out channel type.");
      }
   }

   public static int getNumberOfComponents(JSONObject map) throws MMScriptException, JSONException {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals("GRAY8"))
           return 1;
      else if (pixelType.contentEquals("GRAY16"))
           return 1;
      else if (pixelType.contentEquals("GRAY32"))
         return 1;
      else if (pixelType.contentEquals("RGB32"))
           return 3;
      else if (pixelType.contentEquals("RGB64"))
           return 3;
      else {
         throw new MMScriptException("Pixel type not recognized!");
      }
   }

   public static boolean isGRAY8(JSONObject map) throws JSONException, MMScriptException {
      return getPixelType(map).contentEquals("GRAY8");
   }

   public static boolean isGRAY16(JSONObject map) throws JSONException, MMScriptException {
      return getPixelType(map).contentEquals("GRAY16");
   }
   
   public static boolean isGRAY32(JSONObject map) throws JSONException, MMScriptException {
      return getPixelType(map).contentEquals("GRAY32");
   }

   public static boolean isRGB32(JSONObject map) throws JSONException, MMScriptException {
      return getPixelType(map).contentEquals("RGB32");
   }

   public static boolean isRGB64(JSONObject map) throws JSONException, MMScriptException {
      return getPixelType(map).contentEquals("RGB64");
   }

   public static boolean isGRAY8(TaggedImage img) throws JSONException, MMScriptException {
      return isGRAY8(img.tags);
   }

   public static boolean isGRAY16(TaggedImage img) throws JSONException, MMScriptException {
      return isGRAY16(img.tags);
   }

   public static boolean isRGB32(TaggedImage img) throws JSONException, MMScriptException {
      return isRGB32(img.tags);
   }

   public static boolean isRGB64(TaggedImage img) throws JSONException, MMScriptException {
      return isRGB64(img.tags);
   }

   public static boolean isGRAY(JSONObject map) throws JSONException, MMScriptException {
      return (isGRAY8(map) || isGRAY16(map) || isGRAY32(map));
   }

   public static boolean isRGB(JSONObject map) throws JSONException, MMScriptException {
      return (isRGB32(map) || isRGB64(map));
   }

   public static boolean isGRAY(TaggedImage img) throws JSONException, MMScriptException {
      return isGRAY(img.tags);
   }

   public static boolean isRGB(TaggedImage img) throws JSONException, MMScriptException {
      return isRGB(img.tags);
   }


   public static void addConfiguration(JSONObject md, Configuration config) {
      PropertySetting setting;
      for (int i = 0; i < config.size(); ++i) {
         try {
            setting = config.getSetting(i);
               String key = setting.getDeviceLabel() + "-" + setting.getPropertyName();
         String value = setting.getPropertyValue();
            md.put(key, value);
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      }
   }


   public static String getLabel(JSONObject md) {
      try {
         return generateLabel(getChannelIndex(md),
                              getSliceIndex(md),
                              getFrameIndex(md),
                              getPositionIndex(md));
      } catch (JSONException ex) {
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
      } catch (ParseException ex) {
         ReportingUtils.logError(ex);
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

   public static String getROI (CMMCore core) {
      String roi = "";
      int [] x = new int[1];
      int [] y = new int[1];
      int [] xSize = new int[1];
      int [] ySize = new int[1];
      try {
         core.getROI(x, y, xSize, ySize);
         roi += x[0] + "-" + y[0] + "-" + xSize[0] + "-" + ySize[0];
      } catch (Exception ex) {
         ReportingUtils.logError(ex, "Error in MDUtils::getROI");
      }
      return roi;
   }

   public static int getDepth(JSONObject tags) throws MMScriptException, JSONException {
      String pixelType = getPixelType(tags);
      if (pixelType.contains("GRAY8"))
         return 1;
      else if (pixelType.contains("GRAY16"))
         return 2;
      else if (pixelType.contains("RGB32"))
         return 4;
      else if (pixelType.contains("RGB64"))
         return 8;
      else
         return 0;
   }
   
   public static int getNumChannels(JSONObject tags) throws MMScriptException, JSONException {
      if (tags.has("Summary")) {
         JSONObject summary = tags.getJSONObject("Summary");
         if (summary.has("Channels"))
            return summary.getInt("Channels");
      }
      if (tags.has("Channels"))
         return tags.getInt("Channels");
      return 1;
      
   }

}
