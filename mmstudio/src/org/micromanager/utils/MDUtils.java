/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import ij.ImagePlus;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.TaggedImage;

/**
 *
 * @author arthur
 */
public class MDUtils {
   public static Map<String,String> copy(Map<String,String> map) {
      return new HashMap(map);
   }
   
   public static int getInt(Map<String,String> map, String key) throws Exception {
      try {
         return NumberUtils.coreStringToInt(map.get(key));
      } catch (ParseException ex) {
         throw new Exception("Can't find a " + key + " property.");
      }
   }
   
   public static long getLong(Map<String,String> map, String key) throws Exception {
      try {
         return NumberUtils.coreStringToLong(map.get(key));
      } catch (ParseException ex) {
         throw new Exception("Can't find a " + key + " property.");
      }
   }

   public static void putInt(Map<String,String> map, String key, int val) throws Exception {
      map.put(key, NumberUtils.intToDisplayString(val));
   }

   public static int getPositionIndex(Map<String,String> map) throws Exception {
      return getInt(map, "Acquisition-PositionIndex");
   }

   public static int getWidth(Map<String,String> map) throws Exception {
      return getInt(map, "Image-Width");
   }

   public static int getHeight(Map<String,String> map) throws Exception {
      return getInt(map, "Image-Height");
   }

   public static void setWidth(Map<String,String> map, int width) throws Exception {
      putInt(map, "Image-Width", width);
   }

   public static void setHeight(Map<String,String> map, int height) throws Exception {
      putInt(map, "Image-Height", height);
   }

   public static int getSliceIndex(Map<String,String> map) throws Exception {
      return getInt(map, "Acquisition-SliceIndex");
   }

   public static int getChannelIndex(Map<String,String> map) throws Exception {
      return getInt(map, "Acquisition-ChannelIndex");
   }

   public static int getFrameIndex(Map<String,String> map) throws Exception {
      return getInt(map, "Acquisition-FrameIndex");
   }

   public static String getPositionName(Map<String,String> map) throws Exception {
      return map.get("Acquisition-PositionName");
   }

   public static String getChannelName(Map<String,String> map) throws Exception {
      return map.get("Acquisition-ChannelName");
   }

   public static String getFileName(Map<String, String> map) {
      return map.get("Acquisition-FileName");
   }

   public static void setFileName(Map<String, String> map, String filename) {
      map.put("Acquisition-FileName", filename);
   }

   public static String getPixelType(Map<String, String> map)  throws Exception {
      return map.get("Image-PixelType");
   }

   public static void put(Map<String, String> map, String key, long value) {
      map.put(key, NumberUtils.longToCoreString(value));
   }

   public static void put(Map<String, String> map, String key, int value) {
      map.put(key, NumberUtils.intToCoreString(value));
   }

   public static void put(Map<String, String> map, String key, String value) {
      map.put(key, value);
   }

   public static void put(Map<String, String> map, String key, double value) {
      map.put(key, NumberUtils.doubleToCoreString(value));
   }

   public static void addRandomUUID(Map<String,String> map) throws Exception {
      UUID uuid = UUID.randomUUID();
      map.put("UUID", uuid.toString());
   }

   public static UUID getUUID(Map<String,String> map) {
      if (map.containsKey("UUID"))
         return UUID.fromString(map.get("UUID"));
      else
         return null;
   }

   public static void setImageType(Map<String, String> map, int type) throws Exception {
      switch (type) {
         case ImagePlus.GRAY8:
            map.put("Image-PixelType", "GRAY8");
         break;
         case ImagePlus.GRAY16:
            map.put("Image-PixelType", "GRAY16");
         break;
         case ImagePlus.COLOR_RGB:
            map.put("Image-PixelType", "RGB32");
         break;
         case 64:
            map.put("Image-PixelType", "RGB64");
         break;
      }
   }

   public static int getSingleChannelType(Map<String, String> map) throws Exception {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals("GRAY8"))
           return ImagePlus.GRAY8;
      else if (pixelType.contentEquals("GRAY16"))
           return ImagePlus.GRAY16;
      else if (pixelType.contentEquals("RGB32"))
           return ImagePlus.GRAY8;
      else if (pixelType.contentEquals("RGB64"))
           return ImagePlus.GRAY16;
      else {
         throw new Exception("Pixel type not recognized!");
      }
   }

   public static int getNumberOfComponents(Map<String, String> map) throws Exception {
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

   public static boolean isGRAY8(Map<String,String> map) throws Exception {
      return getPixelType(map).contentEquals("GRAY8");
   }

   public static boolean isGRAY16(Map<String,String> map) throws Exception {
      return getPixelType(map).contentEquals("GRAY16");
   }

   public static boolean isRGB32(Map<String,String> map) throws Exception {
      return getPixelType(map).contentEquals("RGB32");
   }

   public static boolean isRGB64(Map<String,String> map) throws Exception {
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

   public static boolean isGRAY(Map<String,String> map) throws Exception {
      return (isGRAY8(map) || isGRAY16(map));
   }

   public static boolean isRGB(Map<String,String> map) throws Exception {
      return (isRGB32(map) || isRGB64(map));
   }

   public static boolean isGRAY(TaggedImage img) throws Exception {
      return isGRAY(img.tags);
   }

   public static boolean isRGB(TaggedImage img) throws Exception {
      return isRGB(img.tags);
   }

   public static void addConfiguration(Map<String, String> md, Configuration config) throws Exception {
      PropertySetting setting;
      for (int i = 0; i < config.size(); ++i) {
         setting = config.getSetting(i);
         String key = setting.getDeviceLabel() + "-" + setting.getPropertyName();
         String value = setting.getPropertyValue();
         MDUtils.put(md, key, value);
      }
   }

   public static String getLabel(Map<String, String> md) {
      if (md.containsKey("Acquisition-Label"))
         return md.get("Acquisition-Label");
      else
         return generateLabel(md);
   }

   public static String generateLabel(Map<String, String> md) {
      try {
         String label =
                 String.format("%09d", getFrameIndex(md))
                 + "_"
                 + MDUtils.getChannelName(md)
                 + "_"
                 + String.format("%03d", getSliceIndex(md));
         MDUtils.setLabel(md, label);
         return label;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return null;
      }
   }

   public static String setLabel(Map<String, String> md, String label) {
      return md.put("Acquisition-Label", label);
   }

}
