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
package org.micromanager.multiresviewer;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Convenience/standardization for Acq Engine metadata
 */
public class DisplayMetadata {
    
   private static final String CHANNEL_DISPLAY_COLOR = "PreferredChannelDisplayColor";
   private static final String CHANNEL_GROUP = "ChannelGroup";
   private static final String CORE_AUTOFOCUS_DEVICE = "Core-Autofocus";
   private static final String CORE_CAMERA = "Core-Camera";
   private static final String CORE_GALVO = "Core-Galvo";
   private static final String CORE_IMAGE_PROCESSOR = "Core-ImageProcessor";
   private static final String CORE_SLM = "Core-SLM";
   private static final String CORE_SHUTTER = "Core-Shutter";
   private static final String ACQ_TYPE = "AcquisitionType";
   private static final String WIDTH = "Width";
   private static final String HEIGHT = "Height";
   private static final String PIX_SIZE = "PixelSize_um";
   private static final String POS_NAME = "PositionName";
   private static final String POS_INDEX = "PositionIndex";
   private static final String X_UM_INTENDED = "XPosition_umIntended";
   private static final String Y_UM_INTENDED = "YPosition_umIntended";
   private static final String Z_UM_INTENDED = "ZPosition_umIntended";
   private static final String X_UM = "XPosition_um";
   private static final String Y_UM = "YPosition_um";
   private static final String Z_UM = "ZPosition_um";
   private static final String SLICE = "Slice";
   private static final String FRAME = "Frame";
   private static final String CHANNEL_INDEX = "ChannelIndex";
   private static final String Z_INDEX = "ZIndex";
   private static final String T_INDEX = "TIndex";
   private static final String EXPOSURE = "Exposure";
   private static final String CHANNEL_NAME = "Channel";
   private static final String ZC_ORDER = "SlicesFirst";
   private static final String TIME = "Time";
   private static final String DATE_TIME = "DateAndTime";
   private static final String SAVING_PREFIX = "Prefix";
   private static final String INITIAL_POS_LIST = "InitialPositionList";
   private static final String TIMELAPSE_INTERVAL = "Interval_ms";
   private static final String PIX_TYPE = "PixelType";
   private static final String BIT_DEPTH = "BitDepth";
   private static final String ELAPSED_TIME_MS = "ElapsedTime-ms";
   private static final String Z_STEP_UM = "z-step_um";
   private static final String GRID_COL = "GridColumnIndex";
   private static final String GRID_ROW = "GridRowIndex";
   private static final String AFFINE_TRANSFORM = "AffineTransform";
   private static final String IMAGE_CONSTRUCTION_FILTER = "ImageConstruction";
   private static final String PIX_TYPE_GRAY8 = "GRAY8";
   private static final String PIX_TYPE_GRAY16 = "GRAY16";
   private static final String CORE_XYSTAGE = "Core-XYStage";
   private static final String CORE_FOCUS = "Core-Focus";

   protected static String getCurrentDateAndTime() {
      DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      Calendar calobj = Calendar.getInstance();
      return df.format(calobj.getTime());
   }

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
         throw new RuntimeException("couldnt set core xy");
      }
   }

   public static String getCoreXY(JSONObject map) {
      try {
         return map.getString(CORE_XYSTAGE);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing core xy stage tag");
      }
   }

   public static void setCoreFocus(JSONObject map, String zName) {
      try {
         map.put(CORE_FOCUS, zName);
      } catch (JSONException ex) {
         throw new RuntimeException("couldnt set core focus tag");
      }
   }

   public static String getCoreFocus(JSONObject map) {
      try {
         return map.getString(CORE_FOCUS);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing core focus tag");
      }
   }

   public static String getAcqDate(JSONObject map) {
      try {
         return map.getString(DATE_TIME);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing Acq dat time tag");
      }
   }

   public static void setAcqDate(JSONObject map, String dateTime) {
      try {
         map.put(DATE_TIME, dateTime);
      } catch (JSONException ex) {
         throw new RuntimeException("couldnt set core focus tag");
      }
   }

   public static int getPositionIndex(JSONObject map) {
      try {
         return map.getInt(POS_INDEX);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing posotion index tag");
      }
   }

   public static void setPositionIndex(JSONObject map, int positionIndex) {
      try {
         map.put(POS_INDEX, positionIndex);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set position index");
      }
   }

   public static void setBitDepth(JSONObject map, int bitDepth) {
      try {
         map.put(BIT_DEPTH, bitDepth);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set bit depth");
      }
   }

   public static int getBitDepth(JSONObject map) {
      try {
         return map.getInt(BIT_DEPTH);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing bit depth tag");
      }
   }

   public static int getWidth(JSONObject map) {
      try {
         return map.getInt(WIDTH);
      } catch (JSONException ex) {
         throw new RuntimeException("Image width tag missing");
      }
   }

   public static void setWidth(JSONObject map, int width) {
      try {
         map.put(WIDTH, width);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn set image width");
      }
   }

   public static JSONArray getInitialPositionList(JSONObject map) {
      try {
         return map.getJSONArray(INITIAL_POS_LIST);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn get Initial position list");

      }
   }

   public static void setInitialPositionList(JSONObject map, JSONArray initialPositionList) {
      try {
         map.put(INITIAL_POS_LIST, initialPositionList);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn set Initial position list");
      }
   }

   public static String getSavingPrefix(JSONObject map) {
      try {
         return map.getString(SAVING_PREFIX);
      } catch (JSONException ex) {
         throw new RuntimeException("saving prefix tag missing");

      }
   }

   public static void setSavingPrefix(JSONObject map, String prefix) {
      try {
         map.put(SAVING_PREFIX, prefix);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn set saving prefix");
      }
   }

   public static int getHeight(JSONObject map) {
      try {
         return map.getInt(HEIGHT);
      } catch (JSONException ex) {
         throw new RuntimeException("Height missing from image tags");
      }
   }

   public static void setHeight(JSONObject map, int height) {
      try {
         map.put(HEIGHT, height);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set image height");
      }
   }

   public static int getSliceIndex(JSONObject map) {
      try {
         if (map.has(Z_INDEX)) {
            return map.getInt(Z_INDEX);
         } else {
            return map.getInt(SLICE);
         }
      } catch (JSONException e) {
         throw new RuntimeException("Missing slice index tag");
      }
   }

   public static void setZIndex(JSONObject map, int sliceIndex) {
      try {
         map.put(Z_INDEX, sliceIndex);
         map.put(SLICE, sliceIndex);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set slice index");
      }
   }

   public static int getChannelIndex(JSONObject map) {
      try {
         return map.getInt(CHANNEL_INDEX);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing channel index tag");
      }
   }

   public static void setChannelIndex(JSONObject map, int channelIndex) {
      try {
         map.put(CHANNEL_INDEX, channelIndex);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set channel index");
      }
   }

   public static String getChannelName(JSONObject map) {
      try {
         return map.getString(CHANNEL_NAME);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing channel index tag");
      }
   }

   public static void setChannelName(JSONObject map, String channelName) {
      try {
         map.put(CHANNEL_NAME, channelName);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set channel index");
      }
   }

   public static int getFrameIndex(JSONObject map) {
      try {
         if (map.has(FRAME)) {
            return map.getInt(FRAME);
         } else {
            return map.getInt(T_INDEX);
         }
      } catch (Exception e) {
         throw new RuntimeException("Frame index tag missing");
      }
   }

   public static void setTIndex(JSONObject map, int frameIndex) {
      try {
         map.put(FRAME, frameIndex);
         map.put(T_INDEX, frameIndex);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set frame index");
      }
   }

   public static String getPositionName(JSONObject map) {
      try {
         return map.getString(POS_NAME);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing position name tag");
      }
   }

   public static void setPositionName(JSONObject map, String positionName) {
      try {
         map.put(POS_NAME, positionName);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set position name");
      }
   }

   public static String getPixelType(JSONObject map) {
      try {
         if (map != null) {
            return map.getString(PIX_TYPE);
         }
      } catch (JSONException e) {
         throw new RuntimeException(e);
      }
      return "";
   }

   public static void setPixelTypeFromString(JSONObject map, String type) {
      try {
         map.put(PIX_TYPE, type);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set pixel type");

      }
   }

   public static void setPixelTypeFromByteDepth(JSONObject map, int depth) {
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
         throw new RuntimeException("Couldn't set pixel type");

      }
   }

   public static int getBytesPerPixel(JSONObject map) {
      if (isGRAY8(map)) {
         return 1;
      }
      if (isGRAY16(map)) {
         return 2;
      }
//       if (isGRAY32(map)) return 4;
      if (isRGB32(map)) {
         return 4;
      }
//       if (isRGB64(map)) return 8;
      return 0;
   }

   public static int getNumberOfComponents(JSONObject map) {
      String pixelType = getPixelType(map);
      if (pixelType.contentEquals(PIX_TYPE_GRAY8)) {
         return 1;
      } else if (pixelType.contentEquals(PIX_TYPE_GRAY16)) {
         return 1;
      } //      else if (pixelType.contentEquals("GRAY32"))
      //         return 1;
      else if (pixelType.contentEquals("RGB32")) {
         return 3;
      } //      else if (pixelType.contentEquals("RGB64"))
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
   public static boolean isGRAY(JSONObject map) {
      return (isGRAY8(map) || isGRAY16(map));
   }

   public static boolean isRGB(JSONObject map) {
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
      return channel + "_" + slice + "_" + frame + "_" + position;
   }

   public static String[] getKeys(JSONObject md) {
      int n = md.length();
      String[] keyArray = new String[n];
      Iterator<String> keys = md.keys();
      for (int i = 0; i < n; ++i) {
         keyArray[i] = keys.next();
      }
      return keyArray;
   }

   public static JSONArray getJSONArrayMember(JSONObject obj, String key) {
      JSONArray theArray;
      try {
         return obj.getJSONArray(key);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing JSONArray member");

      }
   }

   public static String getImageTime(JSONObject map) {
      try {
         return map.getString(TIME);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing image time tag");

      }
   }

   public static void setImageTime(JSONObject map, String time) {
      try {
         map.put(TIME, time);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set image time");

      }
   }

   public static int getDepth(JSONObject tags) {
      String pixelType = getPixelType(tags);
      if (pixelType.contains(PIX_TYPE_GRAY8)) {
         return 1;
      } else if (pixelType.contains(PIX_TYPE_GRAY16)) {
         return 2;
      } //      else if (pixelType.contains(MMTags.Values.PIX_TYPE_RGB_32))
      //         return 4;
      //      else if (pixelType.contains(MMTags.Values.PIX_TYPE_RGB_64))
      //         return 8;
      else {
         return 0;
      }
   }

   public static double getExposure(JSONObject tags) {
      try {
         return tags.getDouble(EXPOSURE);
      } catch (JSONException ex) {
         throw new RuntimeException("Exposure tag missiing");

      }
   }

   public static void setExposure(JSONObject tags, double exp) {
      try {
         tags.put(EXPOSURE, exp);
      } catch (JSONException ex) {
         throw new RuntimeException("couldnt set exposure");
      }
   }

   public static double getPixelSizeUm(JSONObject map) {
      try {
         return map.getDouble(PIX_SIZE);
      } catch (JSONException ex) {
         throw new RuntimeException("Pixel size missing in metadata");

      }
   }

   public static void setPixelSizeUm(JSONObject map, double val) {
      try {
         map.put(PIX_SIZE, val);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing pixel size tag");

      }
   }

   public static double getZStepUm(JSONObject map) {
      try {
         return map.getDouble(Z_STEP_UM);
      } catch (JSONException ex) {
         throw new RuntimeException("Z step metadta field missing");
      }
   }

   public static void setZStepUm(JSONObject map, double val) {
      try {
         map.put(Z_STEP_UM, val);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set z step tag");

      }
   }

   public static double getZPositionUm(JSONObject map) {
      try {
         return map.getDouble(Z_UM);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing Z position tag");

      }
   }

   public static void setZPositionUm(JSONObject map, double val) {
      try {
         map.put(Z_UM, val);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set z position");

      }
   }

   public static long getElapsedTimeMs(JSONObject map) {
      try {
         return map.getLong(ELAPSED_TIME_MS);
      } catch (JSONException ex) {
         throw new RuntimeException("missing elapsed time tag");

      }
   }

   public static void setElapsedTimeMs(JSONObject map, long val) {
      try {
         map.put(ELAPSED_TIME_MS, val);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set elapsed time");

      }
   }

   public static double getIntervalMs(JSONObject map) {
      try {
         return map.getDouble(TIMELAPSE_INTERVAL);
      } catch (JSONException ex) {
         throw new RuntimeException("Time interval missing from summary metadata");
      }
   }

   public static void setIntervalMs(JSONObject map, double val) {
      try {
         map.put(TIMELAPSE_INTERVAL, val);
      } catch (JSONException ex) {
         throw new RuntimeException("coulndt set time interval metadta field");
      }
   }

   
   public static Color getChannelDisplayColor(JSONObject tags) {
      try {
         return new Color(tags.getInt(CHANNEL_DISPLAY_COLOR));
      } catch (JSONException ex) {
         System.err.println("couldnt find channel preferred display color in tags");
         return Color.white;
      }
   }
   
   public static void setChannelDisplayColor(JSONObject tags, Color c) {
      try {
         tags.put(CHANNEL_DISPLAY_COLOR, c.getRGB());
      } catch (JSONException ex) {
         throw new RuntimeException("Missing ChannelColor");
      }
   }
   
   public static boolean getZCTOrder(JSONObject map) {
      try {
         return map.getBoolean(ZC_ORDER);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing ZCT Tag");

      }
   }

   public static void setZCTOrder(JSONObject map, boolean val) {
      try {
         map.put(ZC_ORDER, val);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set ZCT Order");

      }
   }

   public static void setAffineTransformString(JSONObject summaryMD, String affine) {
      try {
         summaryMD.put(AFFINE_TRANSFORM, affine);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set affine transform");

      }
   }

   public static String getAffineTransformString(JSONObject summaryMD) {
      try {
         return summaryMD.getString(AFFINE_TRANSFORM);
      } catch (JSONException ex) {
         throw new RuntimeException("Affine transform missing from summary metadata");
      }
   }

   public static void setImageConstructionFilter(JSONObject smd, String type) {
      try {
         smd.put(IMAGE_CONSTRUCTION_FILTER, type);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set pixel overlap tag");

      }
   }

   public static void setGridRow(JSONObject smd, long gridRow) {
      try {
         smd.put(GRID_ROW, gridRow);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   public static void setGridCol(JSONObject smd, long gridCol) {
      try {
         smd.put(GRID_COL, gridCol);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   public static long getGridRow(JSONObject smd) {
      try {
         return smd.getLong(GRID_ROW);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   public static long getGridCol(JSONObject smd) {
      try {
         return smd.getLong(GRID_COL);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   public static void setStageXIntended(JSONObject smd, double x) {
      try {
         smd.put(X_UM_INTENDED, x);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set stage x");

      }
   }

   public static void setStageYIntended(JSONObject smd, double y) {
      try {
         smd.put(Y_UM_INTENDED, y);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set stage y");
      }
   }

   public static void setStageZIntended(JSONObject smd, double y) {
      try {
         smd.put(Z_UM_INTENDED, y);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set stage y");
      }
   }

   public static void setStageX(JSONObject smd, double x) {
      try {
         smd.put(X_UM, x);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set stage x");
      }
   }

   public static void setStageY(JSONObject smd, double y) {
      try {
         smd.put(Y_UM, y);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set stage y");
      }
   }

   public static double getStageX(JSONObject smd) {
      try {
         return smd.getDouble(X_UM);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt get stage x");
      }
   }

   public static double getStageY(JSONObject smd) {
      try {
         return smd.getDouble(Y_UM);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt get stage y");

      }
   }

   private static void setChannelGroup(JSONObject summary, String channelGroup) {
      try {
         summary.put(CHANNEL_GROUP, channelGroup);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set cjannel group");
      }
   }

   private static void setCoreAutofocus(JSONObject summary, String autoFocusDevice) {
      try {
         summary.put(CORE_AUTOFOCUS_DEVICE, autoFocusDevice);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set re autofoucs");
      }
   }

   private static void setCoreCamera(JSONObject summary, String cameraDevice) {
      try {
         summary.put(CORE_CAMERA, cameraDevice);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set core camera");
      }
   }

   private static void setCoreGalvo(JSONObject summary, String galvoDevice) {
      try {
         summary.put(CORE_GALVO, galvoDevice);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set core galvo");
      }
   }

   private static void setCoreImageProcessor(JSONObject summary, String imageProcessorDevice) {
      try {
         summary.put(CORE_IMAGE_PROCESSOR, imageProcessorDevice);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set core image processor");
      }
   }

   private static void setCoreSLM(JSONObject summary, String slmDevice) {
      try {
         summary.put(CORE_SLM, slmDevice);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set core slm");
      }
   }

   private static void setCoreShutter(JSONObject summary, String shutterDevice) {
      try {
         summary.put(CORE_SHUTTER, shutterDevice);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set stage y");
      }
   }

}
