package org.micromanager.magellan.internal.magellanacq;

import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.main.AcqEngMetadata;

/**
 * Magellan specific metadata.
 *
 * @author henrypinkard
 */
public class MagellanMD extends AcqEngMetadata {

   private static final String EXPLORE_ACQ = "MagellanExploreAcquisition";
   private static final String FIXED_SURFACE_POINTS = "DistanceFromFixedSurfacePoints";
   
   
   public static final String SAVING_NAME = "SavingPrefix";
   public static final String SAVING_DIR = "SavingDir";
   
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
         return false;

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
