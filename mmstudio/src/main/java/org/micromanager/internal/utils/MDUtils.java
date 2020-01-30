package org.micromanager.internal.utils;

import ij.ImagePlus;
import java.awt.Rectangle;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
 * This class is intended to standardize interactions with the tags in 
 * TaggedImages and the image summary metadata. Ideally all tags that have any
 * effect on program flow would only be accessed by way of this module; the
 * eventual goal being to promote those tags to being proper member fields and
 * deprecate the corresponding bits of JSON.
 *
 * By using this module, type safety is enforced, redundant tags can be 
 * identified (e.g. "Frame" vs. "FrameIndex"), and it becomes much easier to
 * track which bits of code are relying on which tags.
 *
 * @deprecated Don't use this in new code!
 */
@Deprecated
public final class MDUtils {
   private final static SimpleDateFormat IMAGEDATEFORMAT =
           new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

   /**
    * Helper function to test if a given key exists and has a non-null value.
    */
   private static boolean isValid(JSONObject map, String key) {
      return (map.has(key) && !map.isNull(key));
   }

   public static JSONObject copy(JSONObject map) {
      try {
         return new JSONObject(map.toString());
      } catch (JSONException e) {
         return null;
      }
   }
   
   public static boolean hasPositionIndex(JSONObject map) {
      return map.has("PositionIndex");
   }

   public static int getPositionIndex(JSONObject map) throws JSONException {
      return map.getInt("PositionIndex");
   }

   public static void setPositionIndex(JSONObject map, int positionIndex) throws JSONException {
      map.put("PositionIndex", positionIndex);
   }

   public static boolean hasBitDepth(JSONObject map) {
      return isValid(map, "BitDepth");
   }

   public static int getBitDepth(JSONObject map) throws JSONException {
      if (map.has("Summary"))
         return map.getJSONObject("Summary").getInt("BitDepth");
      return map.getInt("BitDepth");
   }

   public static void setBitDepth(JSONObject map, int depth) throws JSONException {
      map.put("BitDepth", depth);
   }

   public static boolean hasWidth(JSONObject map) {
      return map.has("Width");
   }

   public static int getWidth(JSONObject map) throws JSONException {
      return map.getInt("Width");
   }

   public static void setWidth(JSONObject map, int width) throws JSONException {
      map.put("Width", width);
   }

   public static boolean hasHeight(JSONObject map) {
      return map.has("Height");
   }

   public static int getHeight(JSONObject map) throws JSONException {
      return map.getInt("Height");
   }

   public static void setHeight(JSONObject map, int height) throws JSONException {
      map.put("Height", height);
   }

   public static int getBinning(JSONObject map) throws JSONException {
      // Some cameras return binning in the form "2", whereas others return 
      // it as "2x2".  Micro-Manager only works with equal binning in x and y.
      // Try to translate both into a single value.
      try {
         return map.getInt("Binning");
      } catch (JSONException jex) { 
         // either there is no binning key or it was not an int }
      }
      String val = map.getString("Binning");
      if (val.contains("x")) {
         // HACK: assume the binning parameter is e.g. "1x1" or "2x2" and
         // just take the first number.
         try {
            return Integer.parseInt(val.split("x", 2)[0]);
         } catch (NumberFormatException e) {
            // we only throw JSONExceptions:
            throw new JSONException("Failed to deduce binning");
         }
      } else {
         throw new JSONException("Unrecognized binning String");
      }
      
   }

   public static void setBinning(JSONObject map, int binning) throws JSONException {
      map.put("Binning", binning);
   }

   public static long getSequenceNumber(JSONObject map) throws JSONException {
      return map.getLong("ImageNumber");
   }

   public static void setSequenceNumber(JSONObject map, long num) throws JSONException {
      map.put("ImageNumber", num);
   }

   public static boolean hasSliceIndex(JSONObject map) {
      return map.has("SliceIndex") || map.has("Slice");
   }

   public static int getSliceIndex(JSONObject map) throws JSONException {
      if (map.has("SliceIndex")) {
         return map.getInt("SliceIndex");
      } else {
         return map.getInt("Slice");
      }
   }

   public static void setSliceIndex(JSONObject map, int sliceIndex) throws JSONException {
      map.put("SliceIndex", sliceIndex);
      map.put("Slice", sliceIndex);
   }
   
   public static boolean hasChannelIndex(JSONObject map) {
      return map.has("ChannelIndex");
   }

   public static int getChannelIndex(JSONObject map) throws JSONException {
      return map.getInt("ChannelIndex");
   }

   public static void setChannelIndex(JSONObject map, int channelIndex) throws JSONException {
      map.put("ChannelIndex", channelIndex);
   }

   public static boolean hasFrameIndex(JSONObject map) {
      return map.has("Frame") || map.has("FrameIndex");
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
      map.put("FrameIndex", frameIndex);
   }
   
   public static int getNumPositions(JSONObject map) throws JSONException {
      if (map.has("Positions"))
         return map.getInt("Positions");
      throw new JSONException("Positions tag not found in summary metadata");
   }

   public static void setNumPositions(JSONObject map, int numPositions) throws JSONException {
      map.put("Positions", numPositions);
   }

   public static boolean hasPositionName(JSONObject map) {
      return isValid(map, "PositionName");
   }
   public static String getPositionName(JSONObject map) throws JSONException {
      if (isValid(map, "PositionName")) {
         return map.getString("PositionName");
      } else {
         return null;
      }
   }

   public static void setPositionName(JSONObject map, String positionName) throws JSONException {
      map.put("PositionName", positionName);
   }

   public static String getChannelName(JSONObject map) throws JSONException {
      if (isValid(map, "Channel")) {
         return map.getString("Channel");
      } else {
         return "";
      }
   }

   public static void setChannelName(JSONObject map, String channel) throws JSONException {
      map.put("Channel", channel);
   }

   public static boolean hasChannelColor(JSONObject map) {
      return map.has("ChColor");
   }

   public static int getChannelColor(JSONObject map) throws JSONException {
      if (isValid(map, "ChColor")) {
         return map.getInt("ChColor");
      } else {
         return -1;
      }
   }

   public static void setChannelColor(JSONObject map, int color) throws JSONException {
      map.put("ChColor", color);
   }

   public static String getFileName(JSONObject map) throws JSONException {
      if (map.has("FileName")) {
         return map.getString("FileName");
      } else {
         return null;
      }
   }

   public static void setFileName(JSONObject map, String filename) throws JSONException {
      map.put("FileName", filename);
   }

   public static int getIJType(JSONObject map) throws JSONException, IllegalArgumentException {
      try {
         return map.getInt("IJType");
      } catch (JSONException e) {
         try {
            String pixelType = map.getString("PixelType");
            if (pixelType.contentEquals("GRAY8")) {
               return ImagePlus.GRAY8;
            } else if (pixelType.contentEquals("GRAY16")) {
               return ImagePlus.GRAY16;
            } else if (pixelType.contentEquals("GRAY32")) {
               return ImagePlus.GRAY32;
            } else if (pixelType.contentEquals("RGB32")) {
               return ImagePlus.COLOR_RGB;
            } else {
               throw new IllegalArgumentException("Can't figure out IJ type.");
            }
         } catch (JSONException e2) {
            throw new IllegalArgumentException("Can't figure out IJ type");
         }
      }
   }

   public static boolean hasPixelType(JSONObject map) {
      return (map.has("PixelType") || map.has("IJType"));
   }

   public static String getPixelType(JSONObject map)  throws JSONException, IllegalArgumentException {
      try {
         if (map != null)
            return map.getString("PixelType");
      } catch (JSONException e) {
         try {
            int ijType = map.getInt("IJType");
            if (ijType == ImagePlus.GRAY8) {
               return "GRAY8";
            }
            else if (ijType == ImagePlus.GRAY16) {
               return "GRAY16";
            }
            else if (ijType == ImagePlus.GRAY32) {
               return "GRAY32";
            }
            else if (ijType == ImagePlus.COLOR_RGB) {
               return "RGB32";
            }
            else {
               throw new IllegalArgumentException("Can't figure out pixel type");
            }
            // There is no IJType for RGB64.
         }
         catch (JSONException e2) {
            throw new IllegalArgumentException("Can't figure out pixel type");
         }
      }
      return "";
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

   public static void setUUID(JSONObject map, UUID uuid) throws JSONException {
      map.put("UUID", uuid);
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

   public static void setPixelTypeFromString(JSONObject map, String type) throws JSONException {
      map.put("PixelType", type);
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
      
   public static int getBytesPerPixel(JSONObject map) throws JSONException, IllegalArgumentException {
       if (isGRAY8(map)) return 1;
       if (isGRAY16(map)) return 2;
       if (isGRAY32(map)) return 4;
       if (isRGB32(map)) return 4;
       if (isRGB64(map)) return 8;
       return 0;
   }


   public static int getSingleChannelType(JSONObject map) throws JSONException, IllegalArgumentException {
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
         throw new IllegalArgumentException("Can't figure out channel type.");
      }
   }

   public static int getNumberOfComponents(JSONObject map) throws IllegalArgumentException, JSONException {
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
         throw new IllegalArgumentException("Pixel type \"" + pixelType + "\"not recognized!");
      }
   }

   public static boolean isGRAY8(JSONObject map) throws JSONException, IllegalArgumentException {
      return getPixelType(map).contentEquals("GRAY8");
   }

   public static boolean isGRAY16(JSONObject map) throws JSONException, IllegalArgumentException {
      return getPixelType(map).contentEquals("GRAY16");
   }
   
   public static boolean isGRAY32(JSONObject map) throws JSONException, IllegalArgumentException {
      return getPixelType(map).contentEquals("GRAY32");
   }

   public static boolean isRGB32(JSONObject map) throws JSONException, IllegalArgumentException {
      return getPixelType(map).contentEquals("RGB32");
   }

   public static boolean isRGB64(JSONObject map) throws JSONException, IllegalArgumentException {
      return getPixelType(map).contentEquals("RGB64");
   }

   public static boolean isGRAY8(TaggedImage img) throws JSONException, IllegalArgumentException {
      return isGRAY8(img.tags);
   }

   public static boolean isGRAY16(TaggedImage img) throws JSONException, IllegalArgumentException {
      return isGRAY16(img.tags);
   }

   public static boolean isRGB32(TaggedImage img) throws JSONException, IllegalArgumentException {
      return isRGB32(img.tags);
   }

   public static boolean isRGB64(TaggedImage img) throws JSONException, IllegalArgumentException {
      return isRGB64(img.tags);
   }

   public static boolean isGRAY(JSONObject map) throws JSONException, IllegalArgumentException {
      return (isGRAY8(map) || isGRAY16(map) || isGRAY32(map));
   }

   public static boolean isRGB(JSONObject map) throws JSONException, IllegalArgumentException {
      return (isRGB32(map) || isRGB64(map));
   }

   public static boolean isGRAY(TaggedImage img) throws JSONException, IllegalArgumentException {
      return isGRAY(img.tags);
   }

   public static boolean isRGB(TaggedImage img) throws JSONException, IllegalArgumentException {
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
      ArrayList<String> result = new ArrayList<String>();
      String[] keyArray = new String[n];
      Iterator<String> keys = md.keys();
      for (int i=0; i<n; ++i) {
         String key = keys.next();
         // Can't provide a key that points to null, as actually trying to
         // use it will provoke a JSONException!
         if (!md.isNull(key)) {
            result.add(key);
         }
      }
      return result.toArray(new String[] {});
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
      return IMAGEDATEFORMAT.format(time);
   }

   public static String getCurrentTime() {
      return getTime(new Date());
   }

   public static boolean hasImageTime(JSONObject map) {
      return isValid(map, "Time");
   }
   public static String getImageTime(JSONObject map) throws JSONException {
      return map.getString("Time");
   }
   public static void setImageTime(JSONObject map, String time) throws JSONException {
      map.put("Time", time);
   }

   public static Rectangle getROI(JSONObject tags)
      throws IllegalArgumentException, JSONException {
      String roiString = tags.getString("ROI");

      // roiString is a '-'-separated sequence of 4 integers (an unfortunate
      // choice when the integers are negative). Although all 4 parameters are
      // positive, negative numbers have been observed in the wild, and the
      // best thing to do here is to preserve their value.

      // First change the separater to comma while preserving minus sign
      roiString = roiString.replaceAll("-", "_");
      roiString = roiString.replaceAll("__", ",-");
      roiString = roiString.replaceFirst("^_", "-");
      roiString = roiString.replaceAll("_", ",");

      String[] xywh = roiString.split(",");
      if (xywh.length != 4) {
         throw new IllegalArgumentException("Invalid ROI tag");
      }
      int x, y, w, h;
      x = Integer.parseInt(xywh[0]);
      y = Integer.parseInt(xywh[1]);
      w = Integer.parseInt(xywh[2]);
      h = Integer.parseInt(xywh[3]);
      return new Rectangle(x, y, w, h);
   }

   public static void setROI(JSONObject tags, Rectangle r) throws JSONException {
      String roiString = String.format("%d-%d-%d-%d", 
            (int) r.getX(), (int) r.getY(), 
            (int) r.getWidth(), (int) r.getHeight());
      tags.put("ROI", roiString);
   }

   public static int getNumFrames(JSONObject tags) throws JSONException {
      if (tags.has("Summary")) {
         JSONObject summary = tags.getJSONObject("Summary");
         if (summary.has("Frames"))
            return Math.max(1,summary.getInt("Frames"));
      }
      if (tags.has("Frames"))
         return Math.max(1,tags.getInt("Frames"));
      return 1;
   }

   public static void setNumFrames(JSONObject tags, int numFrames) throws JSONException {
      tags.put("Frames", numFrames);
   }
   
   public static int getNumSlices(JSONObject tags) throws JSONException {
      if (tags.has("Summary")) {
         JSONObject summary = tags.getJSONObject("Summary");
         if (summary.has("Slices"))
            return Math.max(1, summary.getInt("Slices"));
      }
      if (tags.has("Slices"))
         return Math.max(1, tags.getInt("Slices"));
      return 1;
   }

   public static void setNumSlices(JSONObject tags, int numSlices) throws JSONException {
      tags.put("Slices", numSlices);
   }
   
   public static int getNumChannels(JSONObject tags) throws JSONException {
      if (tags.has("Summary")) {
         JSONObject summary = tags.getJSONObject("Summary");
         if (summary.has("Channels"))
            return Math.max(1, summary.getInt("Channels"));
      }
      if (tags.has("Channels"))
         return Math.max(1, tags.getInt("Channels"));
      return 1;
   }

   public static void setNumChannels(JSONObject tags, int numChannels) throws JSONException {
      tags.put("Channels", numChannels);
   }

   public static boolean hasPixelSizeUm(JSONObject map) {
      return (isValid(map, "PixelSize_um") || isValid(map, "PixelSizeUm"));
   }
   public static double getPixelSizeUm(JSONObject map) throws JSONException {
      if (isValid(map, "PixelSize_um")) {
         return map.getDouble("PixelSize_um");
      }
      return map.getDouble("PixelSizeUm");
   }
   public static void setPixelSizeUm(JSONObject map, double val) throws JSONException {
      map.put("PixelSize_um", val);
   }
   
   public static boolean hasZStepUm(JSONObject map) {
      return (isValid(map, "z-step_um"));
   }
   public static double getZStepUm(JSONObject map) throws JSONException {
      return map.getDouble("z-step_um");
   }
   public static void setZStepUm(JSONObject map, double val) throws JSONException {
      map.put("z-step_um", val);
   }
   
   public static boolean hasExposureMs(JSONObject map) {
      return (isValid(map, "Exposure-ms"));
   }
   public static double getExposureMs(JSONObject map) throws JSONException {
      return map.getDouble("Exposure-ms");
   }
   public static void setExposureMs(JSONObject map, double val) throws JSONException {
      map.put("Exposure-ms", val);
   }

   public static boolean hasXPositionUm(JSONObject map) {
      return (isValid(map, "XPositionUm"));
   }
   public static double getXPositionUm(JSONObject map) throws JSONException {
      return map.getDouble("XPositionUm");
   }
   public static void setXPositionUm(JSONObject map, double val) throws JSONException {
      map.put("XPositionUm", val);
   }

   public static boolean hasYPositionUm(JSONObject map) {
      return (isValid(map, "YPositionUm"));
   }
   public static double getYPositionUm(JSONObject map) throws JSONException {
      return map.getDouble("YPositionUm");
   }
   public static void setYPositionUm(JSONObject map, double val) throws JSONException {
      map.put("YPositionUm", val);
   }

   public static boolean hasZPositionUm(JSONObject map) {
      return (isValid(map, "ZPositionUm"));
   }
   public static double getZPositionUm(JSONObject map) throws JSONException {
      return map.getDouble("ZPositionUm");
   }
   public static void setZPositionUm(JSONObject map, double val) throws JSONException {
      map.put("ZPositionUm", val);
   }

   public static boolean hasElapsedTimeMs(JSONObject map) {
      return (isValid(map, "ElapsedTime-ms"));
   }
   public static double getElapsedTimeMs(JSONObject map) throws JSONException {
      return map.getDouble("ElapsedTime-ms");
   }
   public static void setElapsedTimeMs(JSONObject map, double val) throws JSONException {
      map.put("ElapsedTime-ms", val);
   }

   public static boolean hasCoreCamera(JSONObject map) {
      return (isValid(map, "Core-Camera"));
   }
   public static String getCoreCamera(JSONObject map) throws JSONException {
      return map.getString("Core-Camera");
   }
   public static void setCoreCamera(JSONObject map, String val) throws JSONException {
      map.put("Core-Camera", val);
   }

   public static double getIntervalMs(JSONObject map) throws JSONException {
      return map.getDouble("Interval_ms");
   }
   public static void setIntervalMs(JSONObject map, double val) throws JSONException {
      map.put("Interval_ms", val);
   }
   public static boolean hasIntervalMs(JSONObject map) throws JSONException {
      return (isValid(map, "Interval_ms"));
   }

   public static String getChannelGroup(JSONObject map) throws JSONException {
      return map.getString("Core-ChannelGroup");
   }

   public static boolean hasSlicesFirst(JSONObject map) {
      return (isValid(map, "SlicesFirst"));
   }
   public static boolean getSlicesFirst(JSONObject map) throws JSONException {
      return map.getBoolean("SlicesFirst");
   }
   public static void setSlicesFirst(JSONObject map, boolean val) throws JSONException {
      map.put("SlicesFirst", val);
   }

   public static boolean hasTimeFirst(JSONObject map) {
      return (isValid(map, "TimeFirst"));
   }
   public static boolean getTimeFirst(JSONObject map) throws JSONException {
      return map.getBoolean("TimeFirst");
   }
   public static void setTimeFirst(JSONObject map, boolean val) throws JSONException {
      map.put("TimeFirst", val);
   }

   public static JSONObject getSummary(JSONObject map) throws JSONException {
      return map.getJSONObject("Summary");
   }
   public static void setSummary(JSONObject map, JSONObject summary) throws JSONException {
      map.put("Summary", summary);
   }

   public static boolean hasComments(JSONObject map) {
      return map.has("Comment");
   }

   public static String getComments(JSONObject map) throws JSONException {
      return map.getString("Comment");
   }
   public static void setComments(JSONObject map, String comments) throws JSONException {
      map.put("Comment", comments);
   }
}