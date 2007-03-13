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
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MMSerializationException;

/**
 * Navigation list of positions for the XYStage.
 * Used for multi-site acquistion support.
 */
public class PositionList {
   private ArrayList<MultiStagePosition> positions_;
   private final static String ID = "Micro-Manager XY-position list";
   private final static String ID_KEY = "ID";
   private final static int VERSION = 1;
   private final static String VERSION_KEY = "VERSION";
   private final static String LABEL_KEY = "LABEL";
   private final static String DEVICE_KEY = "DEVICE";
   private final static String X_KEY = "X";
   private final static String Y_KEY = "Y";
   private final static String Z_KEY = "Z";
   private final static String POSARRAY_KEY = "POSITIONS";
   private final static String DEVARRAY_KEY = "DEVICES";
      
   public PositionList() {
      positions_ = new ArrayList<MultiStagePosition>();
   }

   public MultiStagePosition getPosition(int idx) {
      if (idx < 0 || idx >= positions_.size())
         return null;
      
      return positions_.get(idx);
   }
   
   public int getPositionIndex(String posLabel) {
      for (int i=0; i<positions_.size(); i++) {
         if (positions_.get(i).getLabel().compareTo(posLabel) == 0)
            return i;
      }
      return -1;
   }
   
   public void addPosition(MultiStagePosition pos) {
      positions_.add(pos);
   }
   
   public int getNumberOfPositions() {
      return positions_.size();
   }
   
   public void setPositions(MultiStagePosition[] posArray) {
      positions_.clear();
      for (int i=0; i<posArray.length; i++) {
         positions_.add(posArray[i]);
      }
   }

   public MultiStagePosition[] getPositions() {
      MultiStagePosition[] list = new MultiStagePosition[positions_.size()];
      for (int i=0; i<positions_.size(); i++) {
         list[i] = positions_.get(i);
      }
      
      return list;
   }
   
   public void setLabel(int idx, String label) {
      if (idx < 0 || idx >= positions_.size())
         return;
      
      positions_.get(idx).setLabel(label);
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
         JSONArray listOfPositions = new JSONArray();
         // iterate on positions
         for (int i=0; i<positions_.size(); i++) {
            MultiStagePosition msp = positions_.get(i);

            JSONObject mspData = new JSONObject();
            // annotate position with label
            mspData.put(LABEL_KEY, positions_.get(i).getLabel());

            JSONArray devicePosData = new JSONArray();            
            // iterate on devices
            for (int j=0; j<msp.size(); j++) {
               StagePosition sp = msp.get(j);
               JSONObject stage = new JSONObject();
               stage.put(X_KEY, sp.x);
               stage.put(Y_KEY, sp.y);
               stage.put(Z_KEY, sp.z);
               stage.put(DEVICE_KEY, sp.stageName);
               
               devicePosData.put(j, stage);
            }
            mspData.put(DEVARRAY_KEY, devicePosData);

            listOfPositions.put(i, mspData);
         }
         meta.put(POSARRAY_KEY, listOfPositions);
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
            JSONObject mspData = posArray.getJSONObject(i);
            MultiStagePosition msp = new MultiStagePosition();
            msp.setLabel(mspData.getString(LABEL_KEY));
            JSONArray devicePosData = mspData.getJSONArray(DEVARRAY_KEY);
            for (int j=0; j < devicePosData.length(); j++) {
               JSONObject stage = devicePosData.getJSONObject(j);
               StagePosition pos = new StagePosition();
               pos.x = stage.getDouble(X_KEY);
               pos.y = stage.getDouble(Y_KEY);
               pos.z = stage.getDouble(Z_KEY);
               pos.stageName = stage.getString(DEVICE_KEY);
               msp.add(pos);
            }
            positions_.add(msp);
         }
      } catch (JSONException e) {
         throw new MMSerializationException("Invalid or corrupted serialization data.");
      }
   }

}

