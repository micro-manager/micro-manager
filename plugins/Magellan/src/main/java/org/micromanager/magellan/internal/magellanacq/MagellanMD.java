/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.magellanacq;

import java.awt.Color;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;

/**
 * Magellan specific metadata
 *
 * @author henrypinkard
 */
public class MagellanMD extends AcqEngMetadata {

   private static final String EXPLORE_ACQ = "MagellanExploreAcquisition";
   private static final String FIXED_SURFACE_POINTS = "DistanceFromFixedSurfacePoints";
   private static final String OVERLAP_X = "GridPixelOverlapX";
   private static final String OVERLAP_Y = "GridPixelOverlapY";
   
   public static final String CHANNEL_AXIS = "c";
   
   public static void setSurfacePoints(JSONObject tags, JSONArray arr) {
      try {
         tags.put(FIXED_SURFACE_POINTS, arr);
      } catch (JSONException ex) {
         throw new RuntimeException("Couldnt add fixed surface interpolation points");
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
