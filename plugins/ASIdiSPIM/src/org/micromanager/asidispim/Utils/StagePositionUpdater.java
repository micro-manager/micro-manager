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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Timer;

import org.micromanager.api.ScriptInterface;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Properties;

/**
 *
 * @author nico
 * @author Jon
 */
public class StagePositionUpdater {
   private int interval_;
   private final List<ListeningJPanel> panels_;
   private Timer timer_;
   private final Positions positions_;
   private final Properties props_;
   private final ScriptInterface gui_;
   private final AtomicBoolean acqRunning_ = new AtomicBoolean(false);
   
   /**
    * Utility class for stage position timer.
    * 
    * The timer will be constructed when the start function is called.
    * Panels to be informed of updated stage positions should be added
    * using the addPanel function.
    * 
    * @param gui - Micro-Manager ScriptInterface api implementation
    * @param positions
    * @param props
    */
   public StagePositionUpdater(ScriptInterface gui, 
           Positions positions, Properties props) {
      gui_ = gui;
      positions_ = positions;
      props_ = props;
      panels_ = new ArrayList<ListeningJPanel>();
      updateInterval();
   }
   
   private void updateInterval() {
      // get interval from properties (note this is special plugin property
      //   which gets stored as preference)
      interval_ =  (int) (1000*props_.getPropValueFloat(Devices.Keys.PLUGIN, 
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL));
      if (interval_ == 0) {
         interval_ = 1000;
      }
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
      if (timer_ != null) {
         timer_.stop();
      }
      updateInterval();
      timer_ = new Timer(interval_, new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            oneTimeUpdate();
         }
      });
      timer_.start();
   }
   
   /**
    * stops the timer
    */
   public void stop() {
      if (timer_ != null) {
         timer_.stop();
      }
   }
   
   public boolean isRunning() {
      if (timer_ != null) {
         return timer_.isRunning();
      }
      return false;
   }
   
   public void setAcqRunning(boolean r) {
      acqRunning_.set(r);
   }
   
   /**
    * Updates the stage positions.  Called whenever the timer "dings", or can be called separately.
    * If acquisition is running then does nothing.
    */
   public void oneTimeUpdate() {
      if (!gui_.isAcquisitionRunning() ) {  // 
         // update stage positions in devices
         positions_.refreshStagePositions();
         // notify listeners that positions are updated
         for (ListeningJPanel panel : panels_) {
            panel.updateStagePositions();
         }
      }
   }
   
}
