///////////////////////////////////////////////////////////////////////////////
//FILE:          PiezoSleepPreventer.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2014
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

package org.micromanager.asidispim.Utils;

import java.util.Timer;
import java.util.TimerTask;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Properties;

import mmcorej.CMMCore;

/**
 *
 * @author Jon
 */
public class PiezoSleepPreventer {
   private final CMMCore core_;
   private final Devices devices_;
   private final Properties props_;
   private Timer timer_;
   
   private final int unsleepPeriodSeconds = 40;  // run every 40 seconds
   
   /**
    * Utility class to periodically "move" piezos to prevent them from sleeping.
    *  (move is of distance 0 so nothing happens in practice)
    *  
    * The timer is intended to be started when live mode is enabled and stopped when
    *   live mode is disabled.
    * 
    * @param positions
    * @param props
    */
   public PiezoSleepPreventer(ScriptInterface gui, Devices devices, Properties props) {
      core_ = gui.getMMCore();
      devices_ = devices;
      props_ = props;
      timer_ = null;
   }
   
   /**
    * Start the timer on own thread via java.util.Timer.scheduleAtFixedRate().
    * Call stop() to stop.
    */
   public void start() {
      // end any existing updater before starting (anew)
      if (timer_ != null) {
         timer_.cancel();
      }
      timer_ = new Timer(true);
      timer_.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
               unsleepPiezos();
            }
          }, 0, unsleepPeriodSeconds*1000);
      // will run immediately after start
   }
   
   /**
    * Stops the timer.
    */
   public void stop() {
      if (timer_ != null) {
         timer_.cancel();
      }
   }
   
   /**
    * Moves the piezos by 0 to reset their sleep timer.
    */
   public void unsleepPiezos() {
      try {
         for (Devices.Keys piezoKey : Devices.PIEZOS) {
            if (devices_.isValidMMDevice(piezoKey)) {
               if (props_.getPropValueInteger(piezoKey, Properties.Keys.AUTO_SLEEP_DELAY) > 0) {
                  core_.setRelativePosition(devices_.getMMDevice(piezoKey), 0);
               }
            }
         }
      } catch (Exception e) {
         MyDialogUtils.showError("Could not reset piezo's positions");
      }
   }
   
}
