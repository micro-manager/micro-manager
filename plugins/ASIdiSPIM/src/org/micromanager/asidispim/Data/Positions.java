///////////////////////////////////////////////////////////////////////////////
//FILE:          Positions.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Data;

import java.awt.geom.Point2D;
import java.util.HashMap;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Holds information about device positions
 * 
 * @author Jon
 */
public class Positions {
   private HashMap<Devices.Keys, Double> oneAxisDrivePositions_;
   private HashMap<Devices.Keys, Point2D.Double> twoAxisDrivePositions_;
   Devices devices_;
   private CMMCore core_;
   
   /**
    * Constructor
    */
   public Positions(Devices devices) {
      devices_ = devices;
      core_ = MMStudioMainFrame.getInstance().getCore();
      oneAxisDrivePositions_ = new HashMap<Devices.Keys, Double>();
      twoAxisDrivePositions_ = new HashMap<Devices.Keys, Point2D.Double>();
      
      
   }// constructor

   /**
    * returns cached position of XY stage or Micro-Mirror, 
    *  or null if device hasn't been defined
    * @param devKey
    * @return 
    */
   public Point2D.Double getTwoAxisStagePosition(Devices.Keys devKey) {
      return twoAxisDrivePositions_.get(devKey);
   }
   
   /**
    * returns cached position of linear stage,
    *  or null if device hasn't been defined
    * @param devKey
    * @return 
    */
   public double getOneAxisStagePosition(Devices.Keys devKey) {
      return oneAxisDrivePositions_.get(devKey);
   }
   
   /**
    * returns cached position of XY stage in string form,
    *  or empty string if device hasn't been defined
    * @param devKey
    * @param dir
    * @return
    */
   public String getXYStagePositionString(Devices.Keys devKey, Joystick.Directions dir) {
      String ret = "";
      Point2D.Double pt = getTwoAxisStagePosition(devKey);
      if (pt != null) {
         if (dir == Joystick.Directions.X) {
            ret = posToDisplayStringUm(pt.x);
         } else if (dir == Joystick.Directions.Y) {
            ret = posToDisplayStringUm(pt.y);
         }
      }
      return ret;
   }
   
   /**
    * returns cached position of micromirror stage in string form,
    *  or empty string if device hasn't been defined
    * @param devKey
    * @param dir
    * @return
    */
   public String getMicromirrorPositionString(Devices.Keys devKey, Joystick.Directions dir) {
      String ret = "";
      Point2D.Double pt = getTwoAxisStagePosition(devKey);
      if (pt != null) {
         if (dir == Joystick.Directions.X) {
            ret = posToDisplayStringDeg(pt.x);
         } else if (dir == Joystick.Directions.Y) {
            ret = posToDisplayStringDeg(pt.y);
         }
      }
      return ret;
   }
   
   /**
    * returns cached position of linear stage in string form,
    *  or empty string if device hasn't been defined
    * @param devKey
    * @param dir
    * @return
    */
   public String getStagePositionString(Devices.Keys devKey) {
      String ret = "";
      Double pt = getOneAxisStagePosition(devKey);
      if (pt != null) {
         ret = posToDisplayStringUm(pt);
      }
      return ret;
   }
   
   
   /**
    * updates the cached stage positions via calls to MMCore
    */
   public final void updateStagePositions() {
      updateOneAxisStagePositions();
      updateTwoAxisStagePositions();
   }
   
   private void updateTwoAxisStagePositions() {
      for (Devices.Keys devKey : Devices.STAGES2D) {
         String mmDevice = devices_.getMMDevice(devKey);
         if (mmDevice==null) {  // skip devices not set in devices tab
            twoAxisDrivePositions_.put(devKey, null);
            continue;
         }
         Point2D.Double pt = null;
         try {
            if (devKey == Devices.Keys.XYSTAGE) {
               pt = core_.getXYStagePosition(mmDevice);
            } else { // must be galvo
               pt = core_.getGalvoPosition(mmDevice);
            }
            twoAxisDrivePositions_.put(devKey, pt);
         } catch (Exception ex) {
            ReportingUtils.logError("Problem getting position of " + mmDevice);
         }
      }
   }
   
   private void updateOneAxisStagePositions() {
      for (Devices.Keys devKey : Devices.STAGES1D) {
         String mmDevice = devices_.getMMDevice(devKey);
         if (mmDevice==null) {  // skip devices not set in devices tab
            oneAxisDrivePositions_.put(devKey, null);
            continue;
         }
         try {
               double pt = core_.getPosition(mmDevice);
               oneAxisDrivePositions_.put(devKey, pt);
         } catch (Exception ex) {
            ReportingUtils.logError("Problem getting position of " + mmDevice);
         }
      }
   }
   
   private static String posToDisplayStringUm(Double pos) {
      if (pos != null) {
         return NumberUtils.doubleToDisplayString(pos)
//         return NumberUtils.doubleToDisplayString(Math.round(1000*pos)/1000)  // rounds to two decimal places 
               + " \u00B5"+"m";
      }
      return "";
   }
   
   private static String posToDisplayStringDeg(Double pos) {
      if (pos != null) {
         return NumberUtils.doubleToDisplayString(pos)
//         return NumberUtils.doubleToDisplayString(Math.round(100*pos)/100)  // rounds to two decimal places
               + " \u00B0"; 
      }
      return "";
   }

   
}
