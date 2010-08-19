/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import ij.ImagePlus;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
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

   public static int getPositionIndex(Map<String,String> map) throws Exception {
      return getInt(map, "Position");
   }

   public static int getWidth(Map<String,String> map) throws Exception {
      return getInt(map, "Width");
   }

   public static int getHeight(Map<String,String> map) throws Exception {
      return getInt(map, "Height");
   }

   public static int getSlice(Map<String,String> map) throws Exception {
      return getInt(map, "Slice");
   }

   public static int getChannelIndex(Map<String,String> map) throws Exception {
      return getInt(map, "ChannelIndex");
   }

   public static int getFrame(Map<String,String> map) throws Exception {
      return getInt(map, "Frame");
   }

   public static String getPositionName(Map<String,String> map) throws Exception {
      return map.get("PositionName");
   }

   public static String getChannelName(Map<String,String> map) throws Exception {
      return map.get("Channel");
   }

   public static String getFileName(Map<String, String> map) {
      return map.get("FileName");
   }

   public static void setFileName(Map<String, String> map, String filename) {
      map.put("FileName", filename);
   }

   public static String getPixelType(Map<String, String> map)  throws Exception {
      return map.get("PixelType");
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

   public static void setImageType(Map<String, String> map, int type) throws Exception {
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

}
