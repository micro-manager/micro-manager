/**
 * StageDisplay plugin
 * 
 * Author: Jon Daniels (ASI)
 * 
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 **/


package org.micromanager.stagedisplay;

import net.miginfocom.swing.MigLayout;

import java.util.Timer;
import java.util.TimerTask;

import mmcorej.CMMCore;
import java.util.prefs.Preferences;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Point2D;

import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMFrame;

/**
 * @author jon
 */
@SuppressWarnings("serial")
public class StageDisplayFrame extends MMFrame implements MMListenerInterface {
   private final ScriptInterface gui_;
   private final CMMCore core_;
   private Preferences prefs_;

   private final DecimalFormat df_;
   private final JLabel xPositionLabel_;
   private final JLabel yPositionLabel_;
   private final JLabel zPositionLabel_;
   private final JCheckBox enableRefreshCB_;
   private Timer timer_ = null;
   
   private static final String ENABLEUPDATES = "ENABLEUPDATES";
   
   /**
    * Creates new StageDisplayFrame
    */
   public StageDisplayFrame(ScriptInterface gui) {
      
      this.setLayout(new MigLayout(
            "",
            "[left]4[right]10[left]4[right]",
            "[]6[]"));
      gui_ = gui;
      core_ = gui_.getMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      NumberFormat nf = NumberFormat.getInstance();
      df_ = (DecimalFormat)nf;
      df_.applyPattern("###,###,##0.0");

      final int positionMaxWidth = 60;
      add(new JLabel("X: "));
      xPositionLabel_ = new JLabel("");
      xPositionLabel_.setMaximumSize(new Dimension(positionMaxWidth, 20));
      xPositionLabel_.setMinimumSize(new Dimension(positionMaxWidth, 20));
      add(xPositionLabel_);
      
      add(new JLabel("Z: "));
      zPositionLabel_ = new JLabel("");
      zPositionLabel_.setMaximumSize(new Dimension(positionMaxWidth, 20));
      zPositionLabel_.setMinimumSize(new Dimension(positionMaxWidth, 20));
      add(zPositionLabel_, "wrap");
      
      add(new JLabel("Y: "));
      yPositionLabel_ = new JLabel("");
      yPositionLabel_.setMaximumSize(new Dimension(positionMaxWidth, 20));
      yPositionLabel_.setMinimumSize(new Dimension(positionMaxWidth, 20));
      add(yPositionLabel_);
      
      // checkbox to turn updates on and off
      enableRefreshCB_ = new JCheckBox("Enable updates", prefs_.getBoolean(ENABLEUPDATES, true));
      enableRefreshCB_.addItemListener(new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent arg0) {
            prefs_.putBoolean(ENABLEUPDATES, enableRefreshCB_.isSelected());
            refreshTimer();
         }
      });
      add(enableRefreshCB_, "span 2");
      
      // we want to stop the timer when closed so actually dispose instead of just hiding
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      
      refreshTimer();

      setTitle(StageDisplay.menuName); 
      loadAndRestorePosition(300, 300);  // put frame back where it was last time
      pack();           // shrinks the window as much as it can
      setResizable(false);
   }
   
   /**
    * This method intended to be a public API method if ever needed.
    */
   public void enableUpdates(boolean enabled) {
      enableRefreshCB_.setSelected(enabled);
   }
   
   /**
    * Starts the timer if updates are enabled, or stops it otherwise.
    */
   public void refreshTimer() {
      if (enableRefreshCB_.isSelected()) {
         startTimer();
      } else {
         stopTimer();
      }
   }
   
   /**
    * Unconditionally starts the timer.
    */
   private void startTimer() {
      // end any existing updater before starting (anew)
      stopTimer();
      timer_ = new Timer(true);
      timer_.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
           // update positions if we aren't already doing it or paused
           // this prevents building up task queue if something slows down
           updateStagePositions();
        }
      }, 0, 1000);  // 1 sec interval
   }
   
   /**
    * Unconditionally stops the timer.
    */
   private void stopTimer() {
      if (timer_ != null) {
         timer_.cancel();
      }
   }
   
   /**
    * Reads the positions from the default XY and Z stages and displays them.
    * This is called on a regular interval whenever the timer "dings".
    */
   private final void updateStagePositions() {
      Point2D.Double pt = new Point2D.Double();
      try {
         pt = core_.getXYStagePosition();
         xPositionLabel_.setText(df_.format(pt.x) + " \u00B5m");
         yPositionLabel_.setText(df_.format(pt.y) + " \u00B5m");
      } catch (Exception e) {
         xPositionLabel_.setText("None");
         yPositionLabel_.setText("None");
      }
      double zpos = 0;
      try {
         zpos = core_.getPosition();
         zPositionLabel_.setText(df_.format(zpos) + " \u00B5m"); 
      } catch (Exception e) {
         zPositionLabel_.setText("None"); 
      }
   }

   /**
    * This is called when the window is closed, make sure to stop the timer here.
    */
   public final void dispose() {
      stopTimer();
      super.dispose();
   }
   
   
@Override
public void configGroupChangedAlert(String arg0, String arg1) {
}

@Override
public void exposureChanged(String arg0, double arg1) {
}


@Override
public void pixelSizeChangedAlert(double arg0) {
}


@Override
public void propertiesChangedAlert() {
}


@Override
public void propertyChangedAlert(String arg0, String arg1, String arg2) {
}


@Override
public void slmExposureChanged(String arg0, double arg1) {
}


@Override
public void stagePositionChangedAlert(String arg0, double arg1) {
}


@Override
public void systemConfigurationLoaded() {
}


@Override
public void xyStagePositionChanged(String arg0, double arg1, double arg2) {
}
   
}

