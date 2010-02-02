///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiStagePosition.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Generalized mechanical position implementation - using multiple
//               stages.
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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import org.micromanager.utils.MMException;

import mmcorej.CMMCore;

public class MultiStagePosition {
   private ArrayList<StagePosition> stagePosList_;
   private String label_;
   private String defaultZStage_;
   private String defaultXYStage_;
   private int gridRow_ = 0;
   private int gridCol_ = 0;
   private Hashtable<String, String> properties_;
   
   /**
    * Default constructor.
    */
   public MultiStagePosition() {
      stagePosList_ = new ArrayList<StagePosition>();
      label_ = new String("Undefined");
      defaultZStage_ = new String("");
      defaultXYStage_ = new String("");
      properties_ = new Hashtable<String, String>();
   }
   
   /**
    * Convenient constructor where the motion system consists of one XY stage and one focus stage.
    * @param xyStage name
    * @param x - coordinate in microns
    * @param y - coordinate in microns
    * @param zStage name
    * @param z - focus position in microns
    */
   public MultiStagePosition(String xyStage, double x, double y, String zStage, double z) {
      this();
      
      // create and add xy position
      StagePosition xyPos = new StagePosition();
      xyPos.numAxes = 2;
      xyPos.stageName = xyStage;
      xyPos.x = x;
      xyPos.y = y;
      defaultXYStage_ = xyStage; 
      add(xyPos);
      
      // create and add z position
      StagePosition zPos = new StagePosition();
      zPos.numAxes = 1;
      zPos.stageName = zStage;
      zPos.x = z;
      defaultZStage_ = zStage;
      add(zPos);
   }
   
   /**
    * Copy constructor.
    * @param aMps - another instance of the MultiStagePoswition class
    * @return
    */
   public static MultiStagePosition newInstance(MultiStagePosition aMps) {
      MultiStagePosition mps = new MultiStagePosition();
      mps.label_ = new String(aMps.label_);
      mps.defaultXYStage_ = new String(aMps.defaultXYStage_);
      mps.defaultZStage_ = new String(aMps.defaultZStage_);
      for (Enumeration<String> e = aMps.properties_.keys(); e.hasMoreElements();) {
         String key = e.nextElement();
         String val = new String(aMps.properties_.get(key));
         mps.properties_.put(key, val);
      }
      
      Iterator<StagePosition> it = aMps.stagePosList_.iterator();
      while(it.hasNext()) {
         mps.add(StagePosition.newInstance(it.next()));
      }  
      return mps;
   }
   
   /**
    * Add one stage position point
    */
   public void add(StagePosition sp) {
      stagePosList_.add(sp);
   }
   
   /**
    * Number of stages.
    */
   public int size() {
      return stagePosList_.size();
   }
   
   /**
    * Return stage position based on index
    * @param idx - position index
    * @return
    */
   public StagePosition get(int idx) {
      return stagePosList_.get(idx);
   }
   
   /**
    * Add a generalized property-value par to the position.
    */
   public void setProperty(String key, String value) {
      properties_.put(key, value);
   }

   /**
    * Return the array of property keys (names) associated with this position
    */
   public String[] getPropertyNames() {
      String keys[] = new String[properties_.size()];
      int i=0;
      for (Enumeration<String> e = properties_.keys(); e.hasMoreElements();)
         keys[i++] = e.nextElement();
      return keys;
   }
   
   /**
    * Checks if the position has a particular property
    */
   public boolean hasProperty(String key) {
      return properties_.containsKey(key);
   }
   
   /**
    * Returns property value for a given key (name) 
    */
   public String getProperty(String key) {
      if (properties_.containsKey(key))
         return properties_.get(key);
      else
         return null;
   }
   
   /**
    * Returns position for a specific stage.
    */
   public StagePosition get(String stageName) {
      for (int i=0; i<stagePosList_.size(); i++) {
         StagePosition sp = stagePosList_.get(i);
         if (sp.stageName.compareTo(stageName) == 0)
            return sp;
      }
      return null;
   }
   
   /**
    * Returns position label.
    */
   public String getLabel() {
      return label_;
   }

   /**
    * Sets position label (such as well name, etc.)
    */
   public void setLabel(String lab) {
      label_ = lab;
   }
   
   /**
    * Defines which stage serves as focus control
    */
   public void setDefaultZStage(String stage) {
      defaultZStage_ = stage;
   }
   
   public String getDefaultZStage() {
      return defaultZStage_;
   }
   
   public String getDefaultXYStage() {
      return defaultXYStage_;
   }
   
   /**
    * Defines which stage serves as the XY motion control device
    */
   public void setDefaultXYStage(String stage) {
      defaultXYStage_ = stage;
   }

   /**
    * Moves all stages to the specified positions.
    * @param core_ - microscope API
    * @throws Exception
    */
   public static void goToPosition(MultiStagePosition msp, CMMCore core_) throws Exception {
      for (int i=0; i<msp.size(); i++) {
    	  try{
    		  
    	  
         StagePosition sp = msp.get(i);
         if (sp.numAxes == 1) {
            core_.setPosition(sp.stageName, sp.x);
         } else if (sp.numAxes == 2) {
            core_.setXYPosition(sp.stageName, sp.x, sp.y);
         }
         
         // wait for one device at the time
         // TODO: this should not be here
         core_.waitForDevice(sp.stageName);
    	  }
    	  catch(Exception e)
    	  {
    		  throw new Exception("XY stage error");
    	  }
      }
      
   }

   /**
    * Returns "X" coordinate of the position.
    */
   public double getX() {
      // TODO: implement more efficient position calculation
      for (int i=0; i<stagePosList_.size(); i++) {
         StagePosition sp = stagePosList_.get(i);
         if (sp.numAxes == 2 && sp.stageName.compareTo(defaultXYStage_) == 0) {
            return sp.x;
         }
      }
      return 0.0;
   }
   
   /**
    * Returns "Y" coordinate of the position.
    */
  public double getY() {
      for (int i=0; i<stagePosList_.size(); i++) {
         StagePosition sp = stagePosList_.get(i);
         if (sp.numAxes == 2 && sp.stageName.compareTo(defaultXYStage_) == 0) {
            return sp.y;
         }
      }
      return 0.0;
   }
   
  /**
   * Returns "Z" - focus coordinate of the position.
   */
   public double getZ() {
      for (int i=0; i<stagePosList_.size(); i++) {
         StagePosition sp = stagePosList_.get(i);
         if (sp.numAxes == 1 && sp.stageName.compareTo(defaultZStage_) == 0) {
            return sp.x;
         }
      }
      return 0.0;
   }
   
   /**
    * Sets grid parameters for the rectangular grid
    * @param row
    * @param col
    */
   public void setGridCoordinates(int row, int col) {
      gridRow_ = row;
      gridCol_ = col;
   }
   
   /**
    * Returns rectangular grid row.
    * @return row
    */
   public int getGridRow() {
      return gridRow_;
   }
   
   /**
    * Returns rectangular grid column.
    * @return column
    */
   public int getGridColumn() {
      return gridCol_;
   }
}
