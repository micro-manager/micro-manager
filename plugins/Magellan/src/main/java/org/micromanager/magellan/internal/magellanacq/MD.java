/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.magellanacq;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Magellan specific metadata
 *
 * @author henrypinkard
 */
public class MD {

   private static final String EXPLORE_ACQ = "MagellanExploreAcquisition";
   private static final String FIXED_SURFACE_POINTS = "DistanceFromFixedSurfacePoints";
   private static final String CHANNEL_NAMES = "ChNames";
   private static final String CHANNEL_COLORS = "ChColors";

   public static void setSurfacePoints(JSONObject tags, JSONArray arr) {
      try {
         tags.put(FIXED_SURFACE_POINTS, arr);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt add fixed surface interpolation points");

      }
   }

   public static void setChannelNames(JSONObject smd, JSONArray channelNames) {
      try {
         smd.put(CHANNEL_NAMES, channelNames);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set channel names");

      }
   }

   public static void setChannelColors(JSONObject smd, JSONArray channelColors) {
      try {
         smd.put(CHANNEL_COLORS, channelColors);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set channel colors");

      }
   }

   public static boolean isExploreAcq(JSONObject smd) {
      try {
         return smd.getBoolean(EXPLORE_ACQ);
      } catch (JSONException ex) {
         throw new RuntimeException("find exploreAcq tag");

      }
   }

   public static void setExploreAcq(JSONObject smd, boolean explore) {
      try {
         smd.put(EXPLORE_ACQ, explore);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt set pixel overlap tag");

      }
   }

}
