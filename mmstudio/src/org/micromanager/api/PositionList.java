///////////////////////////////////////////////////////////////////////////////
//FILE:          PositionList.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Container for the scanning pattern
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
// CVS:          $Id: PositionList.java 10970 2013-05-08 16:58:06Z nico $
//
package org.micromanager.api;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMSerializationException;

/**
 * Navigation list of positions for the XYStage.
 * Used for multi site acquisition support.
 */
public class PositionList implements Serializable {
   private ArrayList<MultiStagePosition> positions_;
   private final static String ID = "Micro-Manager XY-position list";
   private final static String ID_KEY = "ID";
   private final static int VERSION = 3;
   private final static String VERSION_KEY = "VERSION";
   private final static String LABEL_KEY = "LABEL";
   private final static String DEVICE_KEY = "DEVICE";
   private final static String X_KEY = "X";
   private final static String Y_KEY = "Y";
   private final static String Z_KEY = "Z";
   private final static String NUMAXES_KEY = "AXES";
   private final static String POSARRAY_KEY = "POSITIONS";
   private final static String DEVARRAY_KEY = "DEVICES";
   private final static String GRID_ROW_KEY = "GRID_ROW";
   private final static String GRID_COL_KEY = "GRID_COL";
   private final static String PROPERTIES_KEY = "PROPERTIES";
   private final static String DEFAULT_XY_STAGE = "DEFAULT_XY_STAGE";
   private final static String DEFAULT_Z_STAGE = "DEFAULT_Z_STAGE";
   
   public final static String AF_KEY = "AUTOFOCUS";
   public final static String AF_VALUE_FULL = "full";
   public final static String AF_VALUE_INCREMENTAL = "incremental";
   public final static String AF_VALUE_NONE = "none";

   private HashSet<ChangeListener> listeners_ = new HashSet<ChangeListener>();
   
   public PositionList() {
      positions_ = new ArrayList<MultiStagePosition>();
   }
   
   
   public static PositionList newInstance(PositionList aPl) {
      PositionList pl = new PositionList();
      Iterator<MultiStagePosition> it = aPl.positions_.iterator();
      while (it.hasNext())
         pl.addPosition(MultiStagePosition.newInstance(it.next()));
      return pl;
      
   }
   
   public void addChangeListener(ChangeListener listener) {
       listeners_.add(listener);
   }
   
   public void removeChangeListener(ChangeListener listener) {
       listeners_.remove(listener);
   }
   
   public void notifyChangeListeners() {
       for (ChangeListener listener:listeners_) {
           listener.stateChanged(new ChangeEvent(this));
       }
   }

   /**
    * Returns multi-stage position associated with the position index.
    * @param idx - position index
    * @return multi-stage position
    */
   public MultiStagePosition getPosition(int idx) {
      if (idx < 0 || idx >= positions_.size())
         return null;
      
      return positions_.get(idx);

   }
   
   /**
    * Returns a copy of the multi-stage position associated with the position index.
    * @param idx - position index
    * @return multi-stage position
    */
   public MultiStagePosition getPositionCopy(int idx) {
      if (idx < 0 || idx >= positions_.size())
         return null;
      
      return MultiStagePosition.newInstance(positions_.get(idx));
   }
   
   /**
    * Returns position index associated with the position name.
    * @param posLabel - label (name) of the position
    * @return index
    */
   public int getPositionIndex(String posLabel) {
      for (int i=0; i<positions_.size(); i++) {
         if (positions_.get(i).getLabel().compareTo(posLabel) == 0)
            return i;
      }
      return -1;
   }
   
   /**
    * Adds a new position to the list.
    * @param pos - multi-stage position
    */
   public void addPosition(MultiStagePosition pos) {
      String label = pos.getLabel();
      if (!isLabelUnique(label)) {
         pos.setLabel(generateLabel(label));
      }
      positions_.add(pos);
      notifyChangeListeners();
   }

   /**
    * Insert a position into the list.
    * @param pos - multi-stage position
    */
   public void addPosition(int in0, MultiStagePosition pos) {
      String label = pos.getLabel();
      if (!isLabelUnique(label)) {
         pos.setLabel(generateLabel(label));
      }
      positions_.add(in0, pos);
      notifyChangeListeners();
   }
   
   /**
    * Replaces position in the list with the new position
    * @param pos - multi-stage position
    */
   public void replacePosition(int index, MultiStagePosition pos) {
      if (index >= 0 && index < positions_.size()) {
         positions_.set(index, pos);
         notifyChangeListeners();
      }
   }
   
   /**
    * Returns the number of positions contained within the list
    */
   public int getNumberOfPositions() {
      return positions_.size();
   }
   
   /**
    * Empties the list.
    */
   public void clearAllPositions() {
      positions_.clear();
      notifyChangeListeners();
   }
   
   /**
    * Removes a specific position based on the index
    * @param idx - position index
    */
   public void removePosition(int idx) {
      if (idx >= 0 && idx < positions_.size())
         positions_.remove(idx);
         notifyChangeListeners();
   }
   
   /**
    * Initialize the entire array by passing an array of multi-stage positions
    * @param posArray - array of multi-stage positions
    */
   public void setPositions(MultiStagePosition[] posArray) {
      positions_.clear();
      for (int i=0; i<posArray.length; i++) {
         positions_.add(posArray[i]);
      }
      notifyChangeListeners();
   }

   /**
    * Returns an array of positions contained in the list.
    * @return position array
    */
   public MultiStagePosition[] getPositions() {
      MultiStagePosition[] list = new MultiStagePosition[positions_.size()];
      for (int i=0; i<positions_.size(); i++) {
         list[i] = positions_.get(i);
      }
      
      return list;
   }
   
   /**
    * Assigns a label to the position index
    * @param idx - position index
    * @param label - new label (name)
    */
   public void setLabel(int idx, String label) {
      if (idx < 0 || idx >= positions_.size())
         return;
      
      positions_.get(idx).setLabel(label);
      notifyChangeListeners();
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
            mspData.put(GRID_ROW_KEY, msp.getGridRow());
            mspData.put(GRID_COL_KEY, msp.getGridColumn());
            mspData.put(DEFAULT_XY_STAGE, msp.getDefaultXYStage());
            mspData.put(DEFAULT_Z_STAGE, msp.getDefaultZStage());
            JSONArray devicePosData = new JSONArray();            
            // iterate on devices
            for (int j=0; j<msp.size(); j++) {
               StagePosition sp = msp.get(j);
               JSONObject stage = new JSONObject();
               stage.put(X_KEY, sp.x);
               stage.put(Y_KEY, sp.y);
               stage.put(Z_KEY, sp.z);
               stage.put(NUMAXES_KEY, sp.numAxes);
               stage.put(DEVICE_KEY, sp.stageName);
               
               devicePosData.put(j, stage);
            }
            mspData.put(DEVARRAY_KEY, devicePosData);
            
            // insert properties
            JSONObject props = new JSONObject();
            String keys[] = msp.getPropertyNames();
            for (int k=0; k<keys.length; k++) {
               String val = msp.getProperty(keys[k]);
               props.put(keys[k], val);
            }
            
            mspData.put(PROPERTIES_KEY, props);
               
            listOfPositions.put(i, mspData);
         }
         meta.put(POSARRAY_KEY, listOfPositions);
         return meta.toString(3);
      } catch (JSONException e) {
         throw new MMSerializationException("Unable to serialize XY positition data into formatted string.");
      }
   }
   
   /**
    * Restore object data from the JSON encoded stream.
    * @param stream
    * @throws MMSerializationException
    */
   public void restore(String stream) throws MMSerializationException {
      try {
         JSONObject meta = new JSONObject(stream);
         JSONArray posArray = meta.getJSONArray(POSARRAY_KEY);
         int version = meta.getInt(VERSION_KEY);
         positions_.clear();
         
         for (int i=0; i<posArray.length(); i++) {
            JSONObject mspData = posArray.getJSONObject(i);
            MultiStagePosition msp = new MultiStagePosition();
            msp.setLabel(mspData.getString(LABEL_KEY));
            if (version >= 2)
               msp.setGridCoordinates(mspData.getInt(GRID_ROW_KEY), mspData.getInt(GRID_COL_KEY));
            if (version >= 3) {
               msp.setDefaultXYStage(mspData.getString(DEFAULT_XY_STAGE));
               msp.setDefaultZStage(mspData.getString(DEFAULT_Z_STAGE));               
            }

            JSONArray devicePosData = mspData.getJSONArray(DEVARRAY_KEY);
            for (int j=0; j < devicePosData.length(); j++) {
               JSONObject stage = devicePosData.getJSONObject(j);
               StagePosition pos = new StagePosition();
               pos.x = stage.getDouble(X_KEY);
               pos.y = stage.getDouble(Y_KEY);
               pos.z = stage.getDouble(Z_KEY);
               pos.stageName = stage.getString(DEVICE_KEY);
               pos.numAxes = stage.getInt(NUMAXES_KEY);
               msp.add(pos);
            }
            
            // get properties
            JSONObject props = mspData.getJSONObject(PROPERTIES_KEY);
            for (Iterator<String> it = props.keys(); it.hasNext();) {
               String key = it.next();
               msp.setProperty(key, props.getString(key));
            }
            
            positions_.add(msp);
         }
      } catch (JSONException e) {
         throw new MMSerializationException("Invalid or corrupted serialization data.");
      }
      notifyChangeListeners();
   }
   
   /**
    * Helper method to generate unique label when inserting a new position.
    * Not recommended for use - planned to become obsolete.
    * @return Unique label
    */
   public String generateLabel() {
      return generateLabel("Pos");
   }
   
   public String generateLabel(String proposal) {
      String label = proposal + positions_.size();
      
      // verify the uniqueness
      int i = 1;
      while (!isLabelUnique(label)) {
         label = proposal + (positions_.size() + i++);
      }
      
      return label;
   }
           
   
   
   /**
    * Verify that the new label is unique
    * @param label - proposed label
    * @return true if label does not exist
    */
   public boolean isLabelUnique(String label) {
      for (int i=0; i<positions_.size(); i++) {
         if (positions_.get(i).getLabel().compareTo(label) == 0)
            return false;
      }
      return true;
   }
   
   /**
    * Save list to a file.
    * @param path
    * @throws MMException
    */
   public void save(String path) throws MMException {
      File f = new File(path);
      try {
         String serList = serialize();
         FileWriter fw = new FileWriter(f);
         fw.write(serList);
         fw.close();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }
   
   /**
    * Load position list from a file.
    * @param path
    * @throws MMException
    */
   public void load(String path) throws MMException {
      File f = new File(path);
      try {
         StringBuffer contents = new StringBuffer();
         BufferedReader input = new BufferedReader(new FileReader(f));
         String line = null;
         while (( line = input.readLine()) != null){
            contents.append(line);
            contents.append(System.getProperty("line.separator"));
         }
         restore(contents.toString());
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
      notifyChangeListeners();
   }   
}

