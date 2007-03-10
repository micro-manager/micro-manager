///////////////////////////////////////////////////////////////////////////////
//FILE:          XYPositionList.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id$
//
package org.micromanager.navigation;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MMSerializationException;

/**
 * Navigation list of positions for the XYStage.
 * Used for multi-site acquistion support.
 */
public class PositionList {
   private ArrayList<MMStagePosition> positions_;
   private final static String ID = "Micro-Manager XY-position list";
   private final static String ID_KEY = "ID";
   private final static int VERSION = 1;
   private final static String VERSION_KEY = "VERSION";
   private final static String LABEL_KEY = "LABEL";
   private final static String X_KEY = "X";
   private final static String Y_KEY = "Y";
   private final static String POSARRAY_KEY = "POSITIONS";
      
   public PositionList() {
      positions_ = new ArrayList<MMStagePosition>();
   }

   public MMStagePosition getPosition(int idx) {
      if (idx < 0 || idx >= positions_.size())
         return null;
      
      return positions_.get(idx);
   }
   
   public int getPositionIndex(String posLabel) {
      for (int i=0; i<positions_.size(); i++) {
         if (positions_.get(i).label.compareTo(posLabel) == 0)
            return i;
      }
      return -1;
   }
   
   public void addPosition(MMStagePosition pos) {
      positions_.add(pos);
   }
   
   public int getNumberOfPositions() {
      return positions_.size();
   }
   
   public void setPositions(MMStagePosition[] posArray) {
      positions_.clear();
      for (int i=0; i<posArray.length; i++) {
         positions_.add(posArray[i]);
      }
   }

   public MMStagePosition[] getPositions() {
      MMStagePosition[] list = new MMStagePosition[positions_.size()];
      for (int i=0; i<positions_.size(); i++) {
         list[i] = positions_.get(i);
      }
      
      return list;
   }
   
   public void setLabel(int idx, String label) {
      if (idx < 0 || idx >= positions_.size())
         return;
      
      positions_.get(idx).label = label;
   }
   
   /**
    * Serialize object into the JSON encoded stream.
    * @throws MMSerializationException
    */
   public String serialize() throws MMSerializationException {
      JSONObject meta = new JSONObject();
      try {
         meta.put(ID_KEY, ID);
         meta.put(VERSION_KEY, VERSION);
         JSONArray list = new JSONArray();
         for (int i=0; i<positions_.size(); i++) {
            JSONObject pos = new JSONObject();
            pos.put(LABEL_KEY, positions_.get(i).label);
            pos.put(X_KEY, positions_.get(i).x);
            pos.put(Y_KEY, positions_.get(i).y);

            list.put(i, pos);
         }
         meta.put(POSARRAY_KEY, list);
         return meta.toString(3);
      } catch (JSONException e) {
         throw new MMSerializationException("Unable to serialize XY positition data into formatted string.");
      }
   }
   
   /**
    * Restore obejct data from the JSON encoded stream.
    * @param stream
    * @throws MMSerializationException
    */
   public void restore(String stream) throws MMSerializationException {
      try {
         JSONObject meta = new JSONObject(stream);
         JSONArray posArray = meta.getJSONArray(POSARRAY_KEY);
         positions_.clear();
         for (int i=0; i<posArray.length(); i++) {
            JSONObject posSer = posArray.getJSONObject(i);
            MMStagePosition pos = new MMStagePosition();
            pos.label = posSer.getString(LABEL_KEY);
            pos.x = posSer.getDouble(X_KEY);
            pos.y = posSer.getDouble(Y_KEY);
            positions_.add(pos);
         }
      } catch (JSONException e) {
         throw new MMSerializationException("Invalid or corrupted serialization data.");
      }
   }

}

