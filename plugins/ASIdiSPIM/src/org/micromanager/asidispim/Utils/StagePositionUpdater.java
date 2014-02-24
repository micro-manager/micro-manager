///////////////////////////////////////////////////////////////////////////////
//FILE:          StagePositionUpdater.java
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.asidispim.Data.Positions;

/**
 *
 * @author nico
 * @author Jon
 */
public class StagePositionUpdater {
   private int interval_ = 1000;
   private List<ListeningJPanel> panels_;
   private Timer stagePosUpdater_;
   private Positions positions_;
   private final AcquisitionEngine acqEngine_;
   
   /**
    * Utility class for stage position timer.
    * 
    * The timer will be constructed when the start function is called.
    * Panels to be informed of updated stage positions should be added
    * using the addPanel function.
    * 
    * Defaults to 1 sec interval between updates.  Can be set 
    * 
    * @param positions
    */
   public StagePositionUpdater(Positions positions) {
      positions_ = positions;
      panels_ = new ArrayList<ListeningJPanel>();
      acqEngine_ = MMStudioMainFrame.getInstance().getAcquisitionEngine();
   }
   
   /**
    * Add panel to list of ListeningJPanels that get notified whenever
    * we have refreshed positions.
    * @param panel
    */
   public void addPanel(ListeningJPanel panel) {
      panels_.add(panel);
   }
   
   /**
    * Start the updater at whatever interval is
    */
   public void start() {
      if (stagePosUpdater_ != null) {
         stagePosUpdater_.stop();
      }
      stagePosUpdater_ = new Timer(interval_, new ActionListener() {
         public void actionPerformed(ActionEvent ae) {
            oneTimeUpdate();
         }
      });
      stagePosUpdater_.start();
   }
   
   /**
    * Starts the timer with a new interval.
    * @param interval_ms new interval in milliseconds
    */
   public void start(int interval_ms) {
      interval_ = interval_ms;
      start();
   }
   
   /**
    * stops the timer
    */
   public void stop() {
      if (stagePosUpdater_ != null) {
         stagePosUpdater_.stop();
      }
   }
   
   public boolean isRunning() {
      if (stagePosUpdater_ != null) {
         return stagePosUpdater_.isRunning();
      }
      return false;
   }

   public int getInterval() {
      return interval_;
   }
   
   /**
    * Updates the stage positions.  Called whenever the timer "dings", or can be called separately.
    * If acquisition is running then does nothing.
    */
   public void oneTimeUpdate() {
      if (!acqEngine_.isAcquisitionRunning()) {  // 
         // update stage positions in devices
         positions_.refreshStagePositions();
         // notify listeners that positions are updated
         for (ListeningJPanel panel : panels_) {
            panel.updateStagePositions();
         }
      }
   }
   
}
