/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import ij.ImagePlus;
import java.awt.Rectangle;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * List of metadata tags
 */
public class MD {
   
   public static final String WIDTH = "Width";
   public static final String HEIGHT = "Height";
   public static final String PIX_SIZE = "PixelSize_um";
   public static final String POS_NAME = "PositionName";
   public static final String POS_INDEX = "PositionIndex";
   public static final String XUM = "XPositionUm";
   public static final String YUM = "YPositionUm";
   public static final String ZUM = "ZPositionUm";
   public static final String SLICE = "Slice";
   public static final String FRAME = "Frame";
   public static final String CHANNEL_INDEX = "ChannelIndex";
   public static final String SLICE_INDEX = "SliceIndex";
   public static final String FRAME_INDEX = "FrameIndex";
   public static final String NUM_CHANNELS = "Channels";
   public static final String CHANNEL_NAME = "Channel";
   public static final String CHANNEL_NAMES = "ChNames";
   public static final String CHANNEL_COLORS = "ChColors";
   public static final String ZC_ORDER = "SlicesFirst";
   public static final String TIME = "Time";
   public static final String SAVING_PREFIX = "Prefix";
   public static final String INITIAL_POS_LIST = "InitialPositionList";
   public static final String TIMELAPSE_INTERVAL = "Interval_ms";
   public static final String PIX_TYPE = "PixelType";
   public static final String BIT_DEPTH = "BitDepth";
   public static final String ELAPSED_TIME_MS = "ElapsedTime-ms";
   public static final String Z_STEP_UM = "z-step_um";
   public static final String OVERLAP_X = "GridPixelOverlapX";
   public static final String OVERLAP_Y = "GridPixelOverlapY";
   public static final String AFFINE_TRANSFORM = "AffineTransform";
   public static final String EXPLORE_ACQ = "MagellanExploreAcquisition";
   public static final String PIX_TYPE_GRAY8 = "GRAY8";
   public static final String PIX_TYPE_GRAY16 = "GRAY16";
   
   

   public static int[] getIndices(String imageLabel) {
      int[] ind = new int[4];
      String[] s = imageLabel.split("_");
      for (int i = 0; i < 4; i++) {
         ind[i] = Integer.parseInt(s[i]);
      }
      return ind;
   }

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
      map.put("FrameIndex", frameIndex);
   }
   
   public static int getNumPositions(JSONObject map) throws JSONException {
      if (map.has("Positions"))
         return map.getInt("Positions");
      throw new JSONException("Positions tag not found in summary metadata");
   }

   public static boolean hasPositionName(JSONObject map) {
      return isValid(map, "PositionName");
   }
   public static String getPositionName(JSONObject map) throws JSONException {
      if (isValid(map, "PositionName")) {
         return map.getString("PositionName");
      } else if (map.has("PositionIndex")) {
         return "Pos" + map.getString("PositionIndex");
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

   public static int getIJType(JSONObject map) throws JSONException {
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
               throw new RuntimeException();
            }
         } catch (JSONException e2) {
            throw new  RuntimeException();
         }
      }
   }

   public static String getPixelType(JSONObject map)  throws JSONException {
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
               throw new RuntimeException();
            }
            // There is no IJType for RGB64.
         }
         catch (JSONException e2) {
            throw new RuntimeException();
         }
      }
      return "";
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
      
   public static int getBytesPerPixel(JSONObject map) throws JSONException {
       if (isGRAY8(map)) return 1;
       if (isGRAY16(map)) return 2;
       if (isGRAY32(map)) return 4;
       if (isRGB32(map)) return 4;
       if (isRGB64(map)) return 8;
       return 0;
   }

   public static int getNumberOfComponents(JSONObject map) throws JSONException {
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
         throw new RuntimeException();
      }
   }

   public static boolean isGRAY8(JSONObject map) throws JSONException {
      return getPixelType(map).contentEquals("GRAY8");
   }

   public static boolean isGRAY16(JSONObject map) throws JSONException {
      return getPixelType(map).contentEquals("GRAY16");
   }
   
   public static boolean isGRAY32(JSONObject map) throws JSONException {
      return getPixelType(map).contentEquals("GRAY32");
   }

   public static boolean isRGB32(JSONObject map) throws JSONException {
      return getPixelType(map).contentEquals("RGB32");
   }

   public static boolean isRGB64(JSONObject map) throws JSONException {
      return getPixelType(map).contentEquals("RGB64");
   }

   public static boolean isGRAY(JSONObject map) throws JSONException {
      return (isGRAY8(map) || isGRAY16(map) || isGRAY32(map));
   }

   public static boolean isRGB(JSONObject map) throws JSONException {
      return (isRGB32(map) || isRGB64(map));
   }

   public static String getLabel(JSONObject md) {
      try {
         return generateLabel(getChannelIndex(md),
                              getSliceIndex(md),
                              getFrameIndex(md),
                              getPositionIndex(md));
      } catch (JSONException ex) {
         Log.log(ex);
         return null;
      }
   }

   public static String generateLabel(int channel, int slice, int frame, int position) {
      return channel +"-"+slice+"-"+frame+"-"+position;
   }

   public static String[] getKeys(JSONObject md) {
      int n = md.length();
      String [] keyArray = new String[n];
      Iterator<String> keys = md.keys();
      for (int i=0; i<n; ++i) {
         keyArray[i] = keys.next();
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

   public static boolean hasImageTime(JSONObject map) {
      return isValid(map, "Time");
   }
   public static String getImageTime(JSONObject map) throws JSONException {
      return map.getString("Time");
   }
   public static void setImageTime(JSONObject map, String time) throws JSONException {
      map.put("Time", time);
   }

   public static int getDepth(JSONObject tags) throws JSONException {
      String pixelType = getPixelType(tags);
      if (pixelType.contains(PIX_TYPE_GRAY8))
         return 1;
      else if (pixelType.contains(PIX_TYPE_GRAY16))
         return 2;
//      else if (pixelType.contains(MMTags.Values.PIX_TYPE_RGB_32))
//         return 4;
//      else if (pixelType.contains(MMTags.Values.PIX_TYPE_RGB_64))
//         return 8;
      else
         return 0;
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
}
