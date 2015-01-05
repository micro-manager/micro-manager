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

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.utils.NumberUtils;

/**
 * Holds information about device positions
 * 
 * @author Jon
 */
public class Positions {
   private HashMap<Devices.Keys, Double> oneAxisDrivePositions_;
   private HashMap<Devices.Keys, Point2D.Double> twoAxisDrivePositions_;
   Devices devices_;
   private ScriptInterface gui_;
   private CMMCore core_;
   
   /**
    * Constructor
    */
   public Positions(ScriptInterface gui, Devices devices) {
      devices_ = devices;
      gui_ = gui;
      core_ = gui_.getMMCore();
      
      // fill the hashmaps with correct keys, leave values blank for now
      oneAxisDrivePositions_ = new HashMap<Devices.Keys, Double>();
      for (Devices.Keys key : Devices.STAGES1D) {
         oneAxisDrivePositions_.put(key, null);
      }
      twoAxisDrivePositions_ = new HashMap<Devices.Keys, Point2D.Double>();
      for (Devices.Keys key : Devices.STAGES2D) {
         twoAxisDrivePositions_.put(key, null);
      }
      
   }// constructor

   /**
    * returns cached position of XY stage or Micro-Mirror, 
    *  or null if device hasn't been defined
    * @param devKey
    * @return 
    */
   private Point2D.Double getTwoAxisStagePosition(Devices.Keys devKey) {
      return twoAxisDrivePositions_.get(devKey);
   }
   
   /**
    * returns cached position of linear stage,
    *  or null if device hasn't been defined
    * @param devKey
    * @return 
    */
   private Double getOneAxisStagePosition(Devices.Keys devKey) {
      return oneAxisDrivePositions_.get(devKey);
   }
   
   /**
    * Returns the cached position of the specified stage, or 0 if the stage wasn't found or
    * if the cache is uninitialized.
    * @param devKey
    * @param dir
    * @return
    */
   public double getPosition(Devices.Keys devKey, Joystick.Directions dir) {
      if (devices_.is1DStage(devKey)) {
         Double pos = getOneAxisStagePosition(devKey);
         if (pos != null) {
            return pos.doubleValue();
         }
      }
      if (devices_.isGalvo(devKey) || devices_.isXYStage(devKey)) {
         Point2D.Double pos = getTwoAxisStagePosition(devKey);
         if (pos != null) {
            if (dir == Joystick.Directions.X) {
               return pos.x;
            } else if (dir == Joystick.Directions.Y) {
               return pos.y;
            }
         }
      }
      return 0;
   }
   
   /**
    * Returns the current position of the specified stage, or 0 if the stage wasn't found.
    * Updates the cache with the value as well.
    * @param devKey
    * @param dir
    * @return
    */
   public double getUpdatedPosition(Devices.Keys devKey, Joystick.Directions dir) {
      String mmDevice = devices_.getMMDevice(devKey);
      if (mmDevice == null) {
         return 0;
      }
      try {
         if (devices_.is1DStage(devKey)) {
            double pt = core_.getPosition(mmDevice);
            oneAxisDrivePositions_.put(devKey, pt);
            return pt;
         }
         Point2D.Double pt;
         if (devices_.isXYStage(devKey)) {
            pt = core_.getXYStagePosition(mmDevice);
         } else if (devices_.isGalvo(devKey)) {
            pt = core_.getGalvoPosition(mmDevice);
         } else {
            pt = new Point2D.Double();
         }
         twoAxisDrivePositions_.put(devKey, pt);
         if (dir == Joystick.Directions.X) {
            return pt.x;
         } else if (dir == Joystick.Directions.Y) {
            return pt.y;
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex);
      }
      return 0;
   }
   
   /**
    * Returns cached position in string form, or the empty string if there is an error.
    * If dir == Joystick.Directions.NONE then assumes 1D stage
    * @param devKey
    * @param dir
    * @return
    */
   public String getPositionString(Devices.Keys devKey, Joystick.Directions dir) {
      if (devices_.is1DStage(devKey) || dir==Joystick.Directions.NONE) {
         Double pt = getOneAxisStagePosition(devKey);
         if (pt != null) {
            return posToDisplayStringUm(pt.doubleValue());
         }
      }
      if (devices_.isXYStage(devKey)) {
         Point2D.Double pt = getTwoAxisStagePosition(devKey);
         if (pt != null) {
            if (dir == Joystick.Directions.X) {
               return posToDisplayStringUm(pt.x);
            } else if (dir == Joystick.Directions.Y) {
               return  posToDisplayStringUm(pt.y);
            }
         }
      }
      if (devices_.isGalvo(devKey)) {
         Point2D.Double pt = getTwoAxisStagePosition(devKey);
         if (pt != null) {
            if (dir == Joystick.Directions.X) {
               return posToDisplayStringDeg(pt.x);
            } else if (dir == Joystick.Directions.Y) {
               return posToDisplayStringDeg(pt.y);
            }
         }
      }
      // shouldn't get here
      return "";
   }
   
   /**
    * Returns cached position in string form for 1D stage
    * @param devKey
    * @return
    */
   public String getPositionString(Devices.Keys devKey) {
      return getPositionString(devKey, Joystick.Directions.NONE);
   }
   
   /**
    * Sets the position of specified stage to the specified value using appropriate core calls
    * @param devKey
    * @param dir
    * @param pos new position of the stage
    */
   public void setPosition(Devices.Keys devKey, Joystick.Directions dir, double pos) {
      try {
         String mmDevice = devices_.getMMDeviceException(devKey);
         if (devices_.is1DStage(devKey)) {
            core_.setPosition(mmDevice, pos);
         } else  if (devices_.isXYStage(devKey)) {
            if (dir == Joystick.Directions.X) {
               // would prefer setXPosition but it doesn't exist so we stop any Y motion
               double ypos = core_.getYPosition(mmDevice);
               core_.setXYPosition(mmDevice, pos, ypos);
            } else if (dir == Joystick.Directions.Y) {
               double xpos = core_.getXPosition(mmDevice);
               // would prefer setYPosition but it doesn't exist so we stop any X motion
               core_.setXYPosition(mmDevice, xpos, pos);
            }
         } else if (devices_.isGalvo(devKey)) {
            Point2D.Double pos2D = core_.getGalvoPosition(mmDevice);
            if (dir == Joystick.Directions.X) {
               core_.setGalvoPosition(mmDevice, pos, pos2D.y);
            } else if (dir == Joystick.Directions.Y) {
               core_.setGalvoPosition(mmDevice, pos2D.x, pos);
            }
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex);
      }
   }
   
   /**
    * Sets the relative position of specified stage to the specified value using appropriate core calls
    * @param devKey
    * @param dir
    * @param pos new position of the stage
    */
   public void setPositionRelative(Devices.Keys devKey, Joystick.Directions dir, double delta) {
      try {
         String mmDevice = devices_.getMMDeviceException(devKey);
         if (devices_.is1DStage(devKey)) {
            core_.setRelativePosition(mmDevice, delta);
         } else if (devices_.isXYStage(devKey)) {
            if (dir == Joystick.Directions.X) {
               core_.setRelativeXYPosition(mmDevice, delta, 0);
            } else if (dir == Joystick.Directions.Y) {
               core_.setRelativeXYPosition(mmDevice, 0, delta);
            }
         } else if (devices_.isGalvo(devKey)) {
            Point2D.Double pos2D = core_.getGalvoPosition(mmDevice);
            if (dir == Joystick.Directions.X) {
               core_.setGalvoPosition(mmDevice, pos2D.x + delta, pos2D.y);
            } else if (dir == Joystick.Directions.Y) {
               core_.setGalvoPosition(mmDevice, pos2D.x, pos2D.y + delta);
            }
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex);
      }
   }
   
   /**
    * Sets the current position to be the origin.
    * @param devKey
    * @param dir
    * @param pos
    */
   public void setOrigin(Devices.Keys devKey, Joystick.Directions dir) {
      try {
         String mmDevice = devices_.getMMDeviceException(devKey);
         switch (dir) {
         case X:
            if (devices_.isXYStage(devKey)) { 
               double ypos = getUpdatedPosition(devKey, Joystick.Directions.Y);
               core_.setAdapterOriginXY(mmDevice, 0.0, ypos);  // so serial com, since adapter keeps own origin
            }
            break;
         case Y:
            if (devices_.isXYStage(devKey)) { 
               double xpos = getUpdatedPosition(devKey, Joystick.Directions.X);
               core_.setAdapterOriginXY(mmDevice, xpos, 0.0);  // so serial com, since adapter keeps own origin
            }
            break;
         case NONE:
         default:
            if (devices_.is1DStage(devKey)) {
               core_.setOrigin(mmDevice);
            }
            break;
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex);
      }
   }

   
   
   /**
    * updates the cached stage positions via calls to MMCore
    */
   public final void refreshStagePositions() {
      refreshOneAxisStagePositions();
      refreshTwoAxisStagePositions();
   }
   
   private void refreshTwoAxisStagePositions() {
      for (Devices.Keys devKey : Devices.STAGES2D) {
         String mmDevice = devices_.getMMDevice(devKey);
         if (mmDevice==null) {  // skip devices not set in devices tab
            twoAxisDrivePositions_.put(devKey, null);
            continue;
         }
         Point2D.Double pt;
         try {
            if (devices_.isXYStage(devKey)) {
               pt = core_.getXYStagePosition(mmDevice);
            } else if (devices_.isGalvo(devKey)) {
               pt = core_.getGalvoPosition(mmDevice);
            } else {
               pt = new Point2D.Double();
            }
            twoAxisDrivePositions_.put(devKey, pt);
         } catch (Exception ex) {
            gui_.logError("Problem getting position of " + mmDevice);
         }
      }
   }
   
   private void refreshOneAxisStagePositions() {
      for (Devices.Keys devKey : Devices.STAGES1D) {
         String mmDevice = devices_.getMMDevice(devKey);
         if (mmDevice==null) {  // skip devices not set in devices tab
            oneAxisDrivePositions_.put(devKey, null);
            continue;
         }
         try {
            if (devices_.is1DStage(devKey)) {
               double pt = core_.getPosition(mmDevice);
               oneAxisDrivePositions_.put(devKey, pt);
            }
         } catch (Exception ex) {
            gui_.logError("Problem getting position of " + mmDevice);
         }
      }
   }
   
   public static String posToDisplayStringUm(Double pos) {
      if (pos != null) {
         return NumberUtils.doubleToDisplayString(pos)
               + " \u00B5"+"m";
      }
      return "";
   }
   
   public static String posToDisplayStringDeg(Double pos) {
      if (pos != null) {
         return NumberUtils.doubleToDisplayString(pos)
               + " \u00B0"; 
      }
      return "";
   }

   
}
