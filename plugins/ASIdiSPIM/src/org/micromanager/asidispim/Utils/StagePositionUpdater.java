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

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Properties;

/**
 *
 * @author nico
 * @author Jon
 */
public class StagePositionUpdater {
   private final List<ListeningJPanel> panels_;
   private Timer timer_;
   private final Positions positions_;
   private final Properties props_;
   private final AtomicBoolean acqRunning_ = new AtomicBoolean(false);  // flag set externally to indicate that acquisition is happening (and so we should disable updates)
   private final AtomicBoolean updatingPositions_ = new AtomicBoolean(false);  // whether we are actually updating positions currently
   
   /**
    * Utility class for stage position timer.
    * 
    * The timer will be constructed when the start function is called.
    * Panels to be informed of updated stage positions should be added
    * using the addPanel function.
    * 
    * @param positions
    * @param props
    */
   public StagePositionUpdater(Positions positions, Properties props) {
      positions_ = positions;
      props_ = props;
      panels_ = new ArrayList<ListeningJPanel>();
      acqRunning_.set(false);
      updatingPositions_.set(false);
      timer_ = null;
   }
   
   private int getPositionUpdateInterval() {
      // get interval from plugin property stored as preference
      // property/pref value has units of seconds, interval_ has units of milliseconds
      int interval =  (int) (1000*props_.getPropValueFloat(Devices.Keys.PLUGIN, 
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL));
      if (interval == 0) {
         interval = 1000;
      }
      return interval;
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
    * Start the updater at whatever interval is.  Uses its own thread
    * via java.util.Timer.scheduleAtFixedRate()
    */
   public void start() {
      // end any existing updater before starting (anew)
      if (timer_ != null) {
         timer_.cancel();
      }
      timer_ = new Timer(true);
      timer_.scheduleAtFixedRate(new TimerTask() {
            public void run() { oneTimeUpdate(); }
          }, 0, getPositionUpdateInterval());
      updatingPositions_.set(true);
   }
   
   /**
    * stops the timer
    */
   public void stop() {
      if (timer_ != null) {
         timer_.cancel();
      }
      for (ListeningJPanel panel : panels_) {
         panel.stoppedStagePositions();
      }
      updatingPositions_.set(false);
   }
   
   /**
    * check whether position updater is running 
    */
   public boolean isUpdateRunning() {
      return updatingPositions_.get();
   }
   
   /**
    * sets the "acquisition running" flag that disables position updates
    * @param running true if starting acquisition, false if ended
    */
   public void setAcqRunning(boolean running) {
      acqRunning_.set(running);
      if (running & isUpdateRunning()) {
         for (ListeningJPanel panel : panels_) {
            panel.stoppedStagePositions();
         }
      }
   }
   
   /**
    * checks whether "acquisition running" flag is set 
    */
   public boolean isAcqRunning() {
      return acqRunning_.get();
   }
   
   /**
    * Updates the stage positions.  Called whenever the timer "dings", or can be called separately.
    * If acquisition is running then does nothing.
    */
   public void oneTimeUpdate() {
      if (!acqRunning_.get()) {
         // update stage positions in devices
         positions_.refreshStagePositions();
         // notify listeners that positions are updated
         for (ListeningJPanel panel : panels_) {
            panel.updateStagePositions();
         }
      }
   }
   
}
