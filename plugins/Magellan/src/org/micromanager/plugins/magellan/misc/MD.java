///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.plugins.magellan.misc;

import ij.ImagePlus;
import java.util.Iterator;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONException;
import org.micromanager.plugins.magellan.json.JSONObject;



/**
 * List of metadata tags
 */
public class MD {
   
   private static final String WIDTH = "Width";
   private static final String HEIGHT = "Height";
   private static final String PIX_SIZE = "PixelSize_um";
   private static final String POS_NAME = "PositionName";
   private static final String POS_INDEX = "PositionIndex";
   private static final String XUM = "XPositionUm";
   private static final String YUM = "YPositionUm";
   private static final String ZUM = "ZPositionUm";
   private static final String SLICE = "Slice";
   private static final String FRAME = "Frame";
   private static final String CHANNEL_INDEX = "ChannelIndex";
   private static final String SLICE_INDEX = "SliceIndex";
   private static final String FRAME_INDEX = "FrameIndex";
   private static final String NUM_FRAMES = "Frames";
   private static final String NUM_SLICES = "Slices";
   private static final String NUM_CHANNELS = "Channels";
   private static final String EXPOSURE = "Exposure";
   private static final String CHANNEL_NAME = "Channel";
   private static final String CHANNEL_NAMES = "ChNames";
   private static final String CHANNEL_COLORS = "ChColors";
   private static final String ZC_ORDER = "SlicesFirst";
   private static final String TIME = "Time";
   private static final String SAVING_PREFIX = "Prefix";
   private static final String INITIAL_POS_LIST = "InitialPositionList";
   private static final String TIMELAPSE_INTERVAL = "Interval_ms";
   private static final String PIX_TYPE = "PixelType";
   private static final String BIT_DEPTH = "BitDepth";
   private static final String ELAPSED_TIME_MS = "ElapsedTime-ms";
   private static final String Z_STEP_UM = "z-step_um";
   private static final String OVERLAP_X = "GridPixelOverlapX";
   private static final String OVERLAP_Y = "GridPixelOverlapY";
   private static final String GRID_COL = "GridColumnIndex";
   private static final String GRID_ROW = "GridRowIndex";
   private static final String AFFINE_TRANSFORM = "AffineTransform";
   private static final String EXPLORE_ACQ = "MagellanExploreAcquisition";
   private static final String IMAGE_CONSTRUCTION_FILTER = "ImageConstruction";
   private static final String RANK_FILTER_RANK = "RankFilterRank";
   private static final String PIX_TYPE_GRAY8 = "GRAY8";
   private static final String PIX_TYPE_GRAY16 = "GRAY16";
   private static final String IJ_TYPE = "IJType";
   private static final String CORE_XYSTAGE = "Core-XYStage";
   private static final String CORE_FOCUS = "Core-Focus";
   private static final String FIXED_SURFACE_POINTS = "DistanceFromFixedSurfacePoints";
   
   
   
   public static int[] getIndices(String imageLabel) {
      int[] ind = new int[4];
      String[] s = imageLabel.split("_");
      for (int i = 0; i < 4; i++) {
         ind[i] = Integer.parseInt(s[i]);
      }
      return ind;
   }

   public static JSONObject copy(JSONObject map) {
      try {
         return new JSONObject(map.toString());
      } catch (JSONException e) {
         return null;
      }
   }
   
   public static void setCoreXY(JSONObject map, String xyName) {
      try {
         map.put(CORE_XYSTAGE, xyName);
      } catch (JSONException ex) {
         Log.log("couldnt set core xy");
         throw new RuntimeException();
      }
   }

   public static String getCoreXY(JSONObject map) {
      try {
         return map.getString(CORE_XYSTAGE);
      } catch (JSONException ex) {
         Log.log("Missing core xy stage tag");
         throw new RuntimeException();
      }
   }

   public static void setCoreFocus(JSONObject map, String zName) {
      try {
         map.put(CORE_FOCUS, zName);
      } catch (JSONException ex) {
         Log.log("couldnt set core focus tag");
         throw new RuntimeException();
      }
   }

   public static String getCoreFocus(JSONObject map) {
      try {
         return map.getString(CORE_FOCUS);
      } catch (JSONException ex) {
         Log.log("Missing core focus tag");
         throw new RuntimeException();
      }
   }

   public static int getPositionIndex(JSONObject map) {
      try {
         return map.getInt(POS_INDEX);
      } catch (JSONException ex) {
         Log.log("Missing posotion index tag");
         throw new RuntimeException();
      }
   }

   public static void setPositionIndex(JSONObject map, int positionIndex)  {
      try {
         map.put(POS_INDEX, positionIndex);
      } catch (JSONException ex) {
         Log.log("Couldn't set position index");
         throw new RuntimeException();
      }
   }
   
      public static void setBitDepth(JSONObject map, int bitDepth)  {
      try {
         map.put(BIT_DEPTH, bitDepth);
      } catch (JSONException ex) {
         Log.log("Couldn't set bit depth");
         throw new RuntimeException();
      }
   }

   public static int getBitDepth(JSONObject map) {
      try {
         return map.getInt(BIT_DEPTH);
      } catch (JSONException ex) {
         Log.log("Missing bit depth tag");
         throw new RuntimeException();
      }
   }

   public static int getWidth(JSONObject map) {
      try {
         return map.getInt(WIDTH);
      } catch (JSONException ex) {
         Log.log("Image width tag missing");
         throw new RuntimeException();
      }
   }

   public static void setWidth(JSONObject map, int width) {
      try {
         map.put(WIDTH, width);
      } catch (JSONException ex) {
         Log.log("Couldn set image width");
      }
   }
       
   public static JSONArray getInitialPositionList(JSONObject map) {
      try {
         return map.getJSONArray(INITIAL_POS_LIST);
      } catch (JSONException ex) {
         Log.log("Couldn get Initial position list");
         throw new RuntimeException();
      }
   }
   
   public static void setInitialPositionList(JSONObject map, JSONArray initialPositionList) {
      try {
         map.put(INITIAL_POS_LIST, initialPositionList);
      } catch (JSONException ex) {
         Log.log("Couldn set Initial position list");
      }
   }
   
    public static String getSavingPrefix(JSONObject map) {
      try {
         return map.getString(SAVING_PREFIX);
      } catch (JSONException ex) {
         Log.log("saving prefix tag missing");
         throw new RuntimeException();
      }
   }

   public static void setSavingPrefix(JSONObject map, String prefix) {
      try {
         map.put(SAVING_PREFIX, prefix);
      } catch (JSONException ex) {
         Log.log("Couldn set saving prefix");
      }
   }

   public static int getHeight(JSONObject map)  {
      try {
         return map.getInt(HEIGHT);
      } catch (JSONException ex) {
         Log.log("Height missing from image tags");
         throw new RuntimeException();
      }
   }

   public static void setHeight(JSONObject map, int height)   {
      try {
         map.put(HEIGHT, height);
      } catch (JSONException ex) {
         Log.log("Couldnt set image height");
         throw new RuntimeException();
      }
   }

   public static int getSliceIndex(JSONObject map) {
      try {
         if (map.has(SLICE_INDEX)) {
            return map.getInt(SLICE_INDEX);
         } else {
            return map.getInt(SLICE);
         }
      } catch (JSONException e) {
         Log.log("Missing slice index tag");
         throw new RuntimeException();
      }
   }

   public static void setSliceIndex(JSONObject map, int sliceIndex)  {
      try {
         map.put(SLICE_INDEX, sliceIndex);
         map.put(SLICE, sliceIndex);
      } catch (JSONException ex) {
                 Log.log("Couldn't set slice index");
         throw new RuntimeException();
      }
   }
   

   public static int getChannelIndex(JSONObject map)  {
      try {
         return map.getInt(CHANNEL_INDEX);
      } catch (JSONException ex) {
                  Log.log("Missing channel index tag");
         throw new RuntimeException();
      }
   }

   public static void setChannelIndex(JSONObject map, int channelIndex)  {
      try {
         map.put(CHANNEL_INDEX, channelIndex);
      } catch (JSONException ex) {
                  Log.log("Couldn't set channel index");
         throw new RuntimeException();
      }
   }

   public static int getFrameIndex(JSONObject map) {
      try {
         if (map.has(FRAME)) {
            return map.getInt(FRAME);
         } else {
            return map.getInt(FRAME_INDEX);
         }
      } catch (Exception e) {
         Log.log("Frame index tag missing");
         throw new RuntimeException();
      }
   }

   public static void setFrameIndex(JSONObject map, int frameIndex)  {
      try {
         map.put(FRAME, frameIndex);
         map.put(FRAME_INDEX, frameIndex);
      } catch (JSONException ex) {
                  Log.log("Couldn't set frame index");
         throw new RuntimeException();
      }
   }
   
   public static String getPositionName(JSONObject map) {
      try {
         return map.getString(POS_NAME);
      } catch (JSONException ex) {
         Log.log("Missing position name tag");
         throw new RuntimeException();
      }
   }

   public static void setPositionName(JSONObject map, String positionName) {
      try {
         map.put(POS_NAME, positionName);
      } catch (JSONException ex) {
                  Log.log("Couldn't set position name");
         throw new RuntimeException();
      }
   }

   public static int getIJType(JSONObject map)  {
      try {
         return map.getInt(IJ_TYPE);
      } catch (JSONException e) {
         try {
            String pixelType = map.getString(PIX_TYPE);
            if (pixelType.contentEquals(PIX_TYPE_GRAY8)) {
               return ImagePlus.GRAY8;
            } else if (pixelType.contentEquals(PIX_TYPE_GRAY16)) {
               return ImagePlus.GRAY16;
//            } else if (pixelType.contentEquals("GRAY32")) {
//               return ImagePlus.GRAY32;
            } else if (pixelType.contentEquals("RGB32")) {
               return ImagePlus.GRAY8;
            } else {
               throw new RuntimeException();
            }
         } catch (JSONException e2) {
            throw new  RuntimeException();
         }
      }
   }

   public static String getPixelType(JSONObject map)   {
      try {
         if (map != null)
            return map.getString(PIX_TYPE);
      } catch (JSONException e) {
         try {
            int ijType = map.getInt(IJ_TYPE);
            if (ijType == ImagePlus.GRAY8) {
               return PIX_TYPE_GRAY8;
            }
            else if (ijType == ImagePlus.GRAY16) {
               return PIX_TYPE_GRAY16;
            }
//            else if (ijType == ImagePlus.GRAY32) {
//               return "GRAY32";
//            }
//            else if (ijType == ImagePlus.COLOR_RGB) {
//               return "RGB32";
//            }
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

   public static void setPixelTypeFromString(JSONObject map, String type)  {
      try {
         map.put(PIX_TYPE, type);
      } catch (JSONException ex) {
                  Log.log("Couldn't set pixel type");
         throw new RuntimeException();
      }
   }

      public static void setPixelTypeFromByteDepth(JSONObject map, int depth)  {
         try {
         switch (depth) {
         case 1:
            map.put(PIX_TYPE, PIX_TYPE_GRAY8);
         break;
         case 2:
            map.put(PIX_TYPE, PIX_TYPE_GRAY16);
         break;
         case 4:
            map.put(PIX_TYPE, "RGB32");
         break;
//         case 8:
//            map.put(PIX_TYPE, "RGB64");
//         break;
      }
         } catch (JSONException e) {
                     Log.log("Couldn't set pixel type");
         throw new RuntimeException();
         }
   }
      
   public static int getBytesPerPixel(JSONObject map)  {
       if (isGRAY8(map)) return 1;
       if (isGRAY16(map)) return 2;
//       if (isGRAY32(map)) return 4;
       if (isRGB32(map)) return 4;
//       if (isRGB64(map)) return 8;
       return 0;
   }

   public static int getNumberOfComponents(JSONObject map)  {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals(PIX_TYPE_GRAY8))
           return 1;
      else if (pixelType.contentEquals(PIX_TYPE_GRAY16))
           return 1;
//      else if (pixelType.contentEquals("GRAY32"))
//         return 1;
      else if (pixelType.contentEquals("RGB32"))
           return 3;
//      else if (pixelType.contentEquals("RGB64"))
//           return 3;
      else {
         throw new RuntimeException();
      }
   }

   public static boolean isGRAY8(JSONObject map) {
         return getPixelType(map).contentEquals(PIX_TYPE_GRAY8);
   }

   public static boolean isGRAY16(JSONObject map) {
         return getPixelType(map).contentEquals(PIX_TYPE_GRAY16);
   }
    
//   public static boolean isGRAY32(JSONObject map)  {
//      return getPixelType(map).contentEquals("GRAY32");
//   }
//
   public static boolean isRGB32(JSONObject map) {
      return getPixelType(map).contentEquals("RGB32");
   }

//   public static boolean isRGB64(JSONObject map)  {
//      return getPixelType(map).contentEquals("RGB64");
//   }

   public static boolean isGRAY(JSONObject map)  {
      return (isGRAY8(map) || isGRAY16(map) );
   }

   public static boolean isRGB(JSONObject map)   {
      return (isRGB32(map));
//              || isRGB64(map));
   }

   public static String getLabel(JSONObject md) {
      return generateLabel(getChannelIndex(md),
              getSliceIndex(md),
              getFrameIndex(md),
              getPositionIndex(md));
   }

   public static String generateLabel(int channel, int slice, int frame, int position) {
      return channel +"_"+slice+"_"+frame+"_"+position;
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

   public static JSONArray getJSONArrayMember(JSONObject obj, String key) {
      JSONArray theArray;
      try {
         return obj.getJSONArray(key);
      } catch (JSONException ex) {
         Log.log("Missing JSONArray member");
         throw new RuntimeException();
      }
   }

   public static String getImageTime(JSONObject map)  {
      try {
         return map.getString(TIME);
      } catch (JSONException ex) {
                  Log.log("Missing image time tag");
         throw new RuntimeException();
      }
   }

   public static void setImageTime(JSONObject map, String time) {
      try {
         map.put(TIME, time);
      } catch (JSONException ex) {
         Log.log("Couldn't set image time");
         throw new RuntimeException();
      }
   }

   public static int getDepth(JSONObject tags) {
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

   public static int getNumFrames(JSONObject tags) {
      try {
         return Math.max(1, tags.getInt(NUM_FRAMES));
      } catch (JSONException ex) {
         Log.log("Missing numframes tag");
         throw new RuntimeException();
      }
   }
   
   public static void setNumFrames(JSONObject tags, int numFrames) {
      try {
         tags.put(NUM_FRAMES, numFrames);
      } catch (JSONException ex) {
         Log.log("couldnt set numFrames");
         throw new RuntimeException();
      }
   }

   public static int getNumSlices(JSONObject tags) {
      try {
         return Math.max(1, tags.getInt(NUM_SLICES));
      } catch (JSONException ex) {
         Log.log("Missing numslices tag");
         throw new RuntimeException();
      }
   }

   public static void setNumSlices(JSONObject tags, int numSlices) {
      try {
         tags.put(NUM_SLICES, numSlices);
      } catch (JSONException ex) {
         Log.log("couldnt set numSlices");
         throw new RuntimeException();
      }
   }

   public static int getNumChannels(JSONObject tags)   {
      try {
         return Math.max(1, tags.getInt(NUM_CHANNELS));
      } catch (JSONException ex) {
        Log.log("Num channels tag missiing");
        throw new RuntimeException();
      }
   }
   
   public static void setNumChannels(JSONObject tags, int numC) {
      try {
         tags.put(NUM_CHANNELS, numC);
      } catch (JSONException ex) {
         Log.log("couldnt set numChannels");
      }
   }

      public static double getExposure(JSONObject tags)   {
      try {
         return  tags.getDouble(EXPOSURE);
      } catch (JSONException ex) {
        Log.log("Exposure tag missiing");
        throw new RuntimeException();
      }
   }
   
   public static void setExposure(JSONObject tags, double exp) {
      try {
         tags.put(EXPOSURE, exp);
      } catch (JSONException ex) {
         Log.log("couldnt set exposure");
      }
   }
   
     public static void setSurfacePoints(JSONObject tags, JSONArray arr) {
      try {
         tags.put(FIXED_SURFACE_POINTS, arr);
      } catch (JSONException ex) {
         Log.log("Couldnt add fixed surface interpolation points");
         throw new RuntimeException();
      }
   }
   
   public static double getPixelSizeUm(JSONObject map)  {
      try {
         return map.getDouble(PIX_SIZE);
      } catch (JSONException ex) {
         Log.log("Pixel size missing in metadata");
         throw new RuntimeException();
      }
   }

   public static void setPixelSizeUm(JSONObject map, double val) {
      try {
         map.put(PIX_SIZE, val);
      } catch (JSONException ex) {
         Log.log("Missing pixel size tag");
         throw new RuntimeException();
      }
   }

   public static double getZStepUm(JSONObject map)  {
      try {
         return map.getDouble(Z_STEP_UM);
      } catch (JSONException ex) {
         Log.log("Z step metadta field missing");
         return 0;
      }
   }
   
   public static void setZStepUm(JSONObject map, double val)  {
      try {
         map.put(Z_STEP_UM, val);
      } catch (JSONException ex) {
                  Log.log("Couldn't set z step tag");
         throw new RuntimeException();
      }
   }

   public static double getZPositionUm(JSONObject map) {
      try {
         return map.getDouble(ZUM);
      } catch (JSONException ex) {
         Log.log("Missing Z position tag");
         throw new RuntimeException();
      }
   }
   
   public static void setZPositionUm(JSONObject map, double val) {
      try {
         map.put(ZUM, val);
      } catch (JSONException ex) {
         Log.log("Couldn't set z position");
         throw new RuntimeException();
      }
   }

   public static long getElapsedTimeMs(JSONObject map) {
      try {
         return map.getLong(ELAPSED_TIME_MS);
      } catch (JSONException ex) {
                  Log.log("missing elapsed time tag");
         throw new RuntimeException();
      }
   }
   public static void setElapsedTimeMs(JSONObject map, long val) {
      try {
         map.put(ELAPSED_TIME_MS, val);
      } catch (JSONException ex) {
                 Log.log("Couldn't set elapsed time");
         throw new RuntimeException();
      }
   }
   
   public static double getIntervalMs(JSONObject map) {
      try {
         return map.getDouble(TIMELAPSE_INTERVAL);
      } catch (JSONException ex) {
         Log.log("Time interval missing from summary metadata");
         return 0;
      }
   }
   public static void setIntervalMs(JSONObject map, double val) {
      try {
         map.put(TIMELAPSE_INTERVAL, val);
      } catch (JSONException ex) {
         Log.log("coulndt set time interval metadta field");
      }
   }

   public static boolean getZCTOrder(JSONObject map)  {
      try {
         return map.getBoolean(ZC_ORDER);
      } catch (JSONException ex) {
                  Log.log("Missing ZCT Tag");
         throw new RuntimeException();
      }
   }
   public static void setZCTOrder(JSONObject map, boolean val)  {
      try {
         map.put(ZC_ORDER, val);
      } catch (JSONException ex) {
                  Log.log("Couldn't set ZCT Order");
         throw new RuntimeException();
      }
   }

   public static void setAffineTransformString(JSONObject summaryMD, String affine) {
      try {
         summaryMD.put(AFFINE_TRANSFORM, affine);
      } catch (JSONException ex) {
        Log.log("Couldn't set affine transform");
        throw new RuntimeException();
      }
   }
   
   public static String getAffineTransformString(JSONObject summaryMD) {
      try {
         return summaryMD.getString(AFFINE_TRANSFORM);
      } catch (JSONException ex) {
        Log.log("Affine transform missing from summary metadata");
        return "";
      }
   }

   public static boolean isExploreAcq(JSONObject smd) {
      try {
         return smd.getBoolean(EXPLORE_ACQ);
      } catch (JSONException ex) {
         Log.log("find exploreAcq tag");
         throw new RuntimeException();
      }
   }
   
   public static void setExploreAcq(JSONObject smd, boolean explore) {
      try {
         smd.put(EXPLORE_ACQ, explore);
      } catch (JSONException ex) {
         Log.log("Couldnt set pixel overlap tag");
         throw new RuntimeException();
      }
   }
   
   public static void setImageConstructionFilter(JSONObject smd, String type) {
      try {
         smd.put(IMAGE_CONSTRUCTION_FILTER, type);
      } catch (JSONException ex) {
         Log.log("Couldnt set pixel overlap tag");
         throw new RuntimeException();
      }
   }
   
    public static void setRankFilterRank(JSONObject smd, double rank) {
      try {
         smd.put(RANK_FILTER_RANK, rank);
      } catch (JSONException ex) {
         Log.log("Couldnt set pixel overlap tag");
         throw new RuntimeException();
      }
   }

   public static void setPixelOverlapX(JSONObject smd, int overlap) {
      try {
         smd.put(OVERLAP_X, overlap);
      } catch (JSONException ex) {
         Log.log("Couldnt set pixel overlap tag");
         throw new RuntimeException();
      }
   }

   public static void setPixelOverlapY(JSONObject smd, int overlap) {
      try {
         smd.put(OVERLAP_Y, overlap);
      } catch (JSONException ex) {
         Log.log("Couldnt set pixel overlap tag");
         throw new RuntimeException();
      }
   }
   
   public static int getPixelOverlapX(JSONObject summaryMD) {
      try {
         return summaryMD.getInt(OVERLAP_X);
      } catch (JSONException ex) {
         Log.log("Couldnt find pixel overlap in image tags");
         return 0;
      }
   }
   
   public static int getPixelOverlapY(JSONObject summaryMD) {
      try {
         return summaryMD.getInt(OVERLAP_Y);
      } catch (JSONException ex) {
         Log.log("Couldnt find pixel overlap in image tags");
         return 0;
      }
   }
   
   public static void setChannelNames(JSONObject smd, JSONArray channelNames) {
      try {
         smd.put(CHANNEL_NAMES, channelNames);
      } catch (JSONException ex) {
         Log.log("Couldnt set channel names");
         throw new RuntimeException();
      }
   }
   
   public static void setChannelColors(JSONObject smd, JSONArray channelColors) {
      try {
         smd.put(CHANNEL_COLORS, channelColors);
      } catch (JSONException ex) {
         Log.log("Couldnt set channel colors");
         throw new RuntimeException();
      }
   }
   
   public static void setGridRow(JSONObject smd, long gridRow) {
      try {
         smd.put(GRID_ROW, gridRow);
      } catch (JSONException ex) {
         Log.log("Couldnt set grid row");
         throw new RuntimeException();
      }
   }

   public static void setGridCol(JSONObject smd, long gridCol) {
      try {
         smd.put(GRID_COL, gridCol);
      } catch (JSONException ex) {
         Log.log("Couldnt set grid row");
         throw new RuntimeException();
      }
   }
   
   public static long getGridRow(JSONObject smd) {
      try {
         return smd.getLong(GRID_ROW);
      } catch (JSONException ex) {
         Log.log("Couldnt set grid row");
         throw new RuntimeException();
      }
   }

   public static long getGridCol(JSONObject smd) {
      try {
         return smd.getLong(GRID_COL);
      } catch (JSONException ex) {
         Log.log("Couldnt set grid row");
         throw new RuntimeException();
      }
   }
   
   public static void setStageX(JSONObject smd, double x) {
      try {
         smd.put(XUM, x);
      } catch (JSONException ex) {
         Log.log("Couldnt set stage x");
         throw new RuntimeException();
      }
   }

   public static void setStageY(JSONObject smd, double y) {
      try {
         smd.put(YUM, y);
      } catch (JSONException ex) {
         Log.log("Couldnt set stage y");
         throw new RuntimeException();
      }
   }
      
   public static double getStageX(JSONObject smd) {
      try {
         return smd.getDouble(XUM);
      } catch (JSONException ex) {
         Log.log("Couldnt get stage x");
         throw new RuntimeException();
      }
   }

   public static double getStageY(JSONObject smd) {
      try {
         return smd.getDouble(YUM);
      } catch (JSONException ex) {
         Log.log("Couldnt get stage y");
         throw new RuntimeException();
      }
   }
}
