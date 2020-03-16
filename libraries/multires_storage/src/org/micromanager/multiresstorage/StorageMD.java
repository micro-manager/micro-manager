/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.multiresstorage;

import java.util.HashMap;
import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author henrypinkard
 */
class StorageMD {

   private static final String SUPER_CHANNEL_NAME = "StorageSuperChannelName";
   private static final String AXES = "ImagrStorageAxesPositions";
   private static final String GRID_COL = "GridColumnIndex";
   private static final String GRID_ROW = "GridRowIndex";
   private static final String OVERLAP_X = "GridPixelOverlapX";
   private static final String OVERLAP_Y = "GridPixelOverlapY";

   static String generateLabel(int channel, int slice, int frame, int position) {
      return channel + "_" + slice + "_" + frame + "_" + position;
   }

   public static int[] getIndices(String imageLabel) {
      int[] ind = new int[4];
      String[] s = imageLabel.split("_");
      for (int i = 0; i < 4; i++) {
         ind[i] = Integer.parseInt(s[i]);
      }
      return ind;
   }

   static void setGridRow(JSONObject smd, long gridRow) {
      try {
         smd.put(GRID_ROW, gridRow);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   static void setGridCol(JSONObject smd, long gridCol) {
      try {
         smd.put(GRID_COL, gridCol);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   static long getGridRow(JSONObject smd) {
      try {
         return smd.getLong(GRID_ROW);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   static long getGridCol(JSONObject smd) {
      try {
         return smd.getLong(GRID_COL);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set grid row");

      }
   }

   public static String getChannelName(JSONObject map) {
      try {
         return map.getString(SUPER_CHANNEL_NAME);
      } catch (JSONException ex) {
         throw new RuntimeException("Missing channel index tag");
      }
   }

   public static void setSuperChannelName(JSONObject map, String channelName) {
      try {
         map.put(SUPER_CHANNEL_NAME, channelName);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldn't set channel index");
      }
   }

   static HashMap<String, Integer> getAxes(JSONObject tags) {
      try {
         JSONObject axes = tags.getJSONObject(AXES);
         Iterator<String> iter = axes.keys();
         HashMap<String, Integer> axesMap = new HashMap<String, Integer>();
         while (iter.hasNext()) {
            String key = iter.next();
            axesMap.put(key, axes.getInt(key));
         }
         return axesMap;
      } catch (JSONException ex) {
         throw new RuntimeException("couldnt create axes");
      }
   }
   
      public static void createAxes(JSONObject tags) {
      try {
         tags.put(AXES, new JSONObject());
      } catch (JSONException ex) {
         throw new RuntimeException("couldnt create axes");
      }
   }

   public static void setAxisPosition(JSONObject tags, String axis, int position) {
      try {
         tags.getJSONObject(AXES).put(axis, position);
      } catch (JSONException ex) {
         throw new RuntimeException("couldnt create axes");
      }
   }

   public static void setPixelOverlapX(JSONObject smd, int overlap) {
      try {
         smd.put(OVERLAP_X, overlap);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set pixel overlap tag");

      }
   }

   public static void setPixelOverlapY(JSONObject smd, int overlap) {
      try {
         smd.put(OVERLAP_Y, overlap);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set pixel overlap tag");

      }
   }

   public static int getPixelOverlapX(JSONObject summaryMD) {
      try {
         return summaryMD.getInt(OVERLAP_X);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt find pixel overlap in image tags");
      }
   }

   public static int getPixelOverlapY(JSONObject summaryMD) {
      try {
         return summaryMD.getInt(OVERLAP_Y);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt find pixel overlap in image tags");
      }
   }

}
