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
import org.micromanager.asidispim.Data.Positions;

/**
 *
 * @author nico
 */
public class StagePositionUpdater {
   int interval_ = 1000;
   List<ListeningJPanel> panels_;
   Timer stagePosUpdater_;
   Positions positions_;
   
   /**
    * Utility class for stage position timer.
    * 
    * The timer will be constructed when the start function is called.
    * Panels to be informed of updated stage positions should be added
    * using the addPanel function.
    * 
    * 
    * @param positions
    */
   public StagePositionUpdater(Positions positions) {
      positions_ = positions;
      panels_ = new ArrayList<ListeningJPanel>();
   }
   
   public void addPanel(ListeningJPanel panel) {
      panels_.add(panel);
      if (stagePosUpdater_ != null && stagePosUpdater_.isRunning()) {
         start(interval_);
      }
   }
   
   public void start (int interval) {
      interval_ = interval;
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
   
   public void stop() {
      if (stagePosUpdater_ != null) {
         stagePosUpdater_.stop();
      }
   }
   
   public void oneTimeUpdate() {
      // update stage positions in devices
      positions_.updateStagePositions();
      // notify listeners that positions are updated
      for (ListeningJPanel panel : panels_) {
         panel.updateStagePositions();
      }
   }
   
}
