///////////////////////////////////////////////////////////////////////////////
//FILE:          StatusSubPanel.java
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

package org.micromanager.asidispim;

import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.StagePositionUpdater;

import net.miginfocom.swing.MigLayout;


/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public final class StatusSubPanel extends ListeningJPanel {
   
   private final Devices devices_;
   private final Properties props_;
   private final Positions positions_;
   private final StagePositionUpdater stagePosUpdater_; 
   
   private final ColorSquare galvoA_;
   private final ColorSquare galvoB_;
   private final ColorSquare piezoA_;
   private final ColorSquare piezoB_;
   
   /**
    * 
    */
   public StatusSubPanel(Devices devices, Properties props, Positions positions,
         StagePositionUpdater stagePosUpdater) {
      super (MyStrings.PanelNames.STATUS_SUBPANEL.toString(),
            new MigLayout(
              "", 
              "[right]5[left]",
              "[]"));
      
      devices_ = devices;
      props_ = props;
      positions_ = positions;
      stagePosUpdater_ = stagePosUpdater;
      
      galvoA_ = new ColorSquare();
      add(galvoA_);
      add(new JLabel("Scan A"), "wrap");
      galvoA_.setToolTipText("Black = blanked; Gray = no device; Green = centered;"
            + " Orange = near center; Red = far from center; Pink = making sheet");
      
      galvoB_ = new ColorSquare();
      add(galvoB_);
      add(new JLabel("Scan B"), "wrap");
      galvoB_.setToolTipText("Black = blanked; Gray = no device; Green = centered;"
            + " Orange = near center; Red = far from center; Pink = making sheet");
      
      piezoA_ = new ColorSquare();
      add(piezoA_);
      add(new JLabel("Piezo A"), "wrap");
      piezoA_.setToolTipText("Gray = no device; Green = centered;"
            + " Orange = near center; Red = far from center");
      
      piezoB_ = new ColorSquare();
      add(piezoB_);
      add(new JLabel("Piezo B"), "wrap");
      piezoB_.setToolTipText("Gray = no device; Green = centered;"
            + " Orange = near center; Red = far from center");
      
      setOpaque(false);
     
   }// constructor
   
   
   /**
    * Private class to draw colored indicator.  Call setColor(Color c) to update.
    * @author Jon
    *
    */
   public class ColorSquare extends JPanel {
      
      public ColorSquare() {
         this.setBackground(Color.LIGHT_GRAY);
      }
      
      public void setColor(Color c) {
         this.setBackground(c);
      }
   }
   
   
   /**
    * Updates specified galvo/scanner indicator.
    * @param devKey
    * @param square
    */
   private void updateGalvoSquare(Devices.Keys devKey, ColorSquare square) {
      final double GALVO_CONSIDERED_CENTER = 0.01d;
      final double GALVO_CLOSE_TO_CENTER = 0.1d;
      
      if (!devices_.isValidMMDevice(devKey)) {
         square.setColor(Color.LIGHT_GRAY);
         return;
      }
      if (props_.getPropValueString(devKey, Properties.Keys.BEAM_ENABLED)
            .equals(Properties.Values.NO.toString())) {
         square.setColor(Color.BLACK);
         return;
      }
      if (props_.getPropValueString(devKey, Properties.Keys.SA_MODE_X)
            .equals(Properties.Values.SAM_ENABLED.toString())) {
         square.setColor(Color.PINK);
         return;
      }
      double displacement = Math.max(
            Math.abs(positions_.getPosition(devKey, Joystick.Directions.X)),
            Math.abs(positions_.getPosition(devKey, Joystick.Directions.Y)));
      if ( displacement < GALVO_CONSIDERED_CENTER) {
         square.setColor(Color.GREEN);
      } else if (displacement < GALVO_CLOSE_TO_CENTER) {
         square.setColor(Color.ORANGE);
      } else {
         square.setColor(Color.RED);
      }
      return;
   }
   
   /**
    * Updates specified piezo indicator
    * @param devKey
    * @param square
    */
   private void updatePiezoSquare(Devices.Keys devKey, ColorSquare square) {
      final double PIEZO_CONSIDERED_CENTER = 1d;
      final double PIEZO_CLOSE_TO_CENTER = 10d;
      
      if (!devices_.isValidMMDevice(devKey)) {
         square.setColor(Color.LIGHT_GRAY);
         return;
      }
      double displacement = Math.abs(positions_.getPosition(devKey, Joystick.Directions.NONE));
      if ( displacement < PIEZO_CONSIDERED_CENTER) {
         square.setColor(Color.GREEN);
      } else if (displacement < PIEZO_CLOSE_TO_CENTER) {
         square.setColor(Color.ORANGE);
      } else {
         square.setColor(Color.RED);
      }
      return;
   }
   

   /**
    * Called whenever position updater has new information for us
    */
   @Override
   public final void updateStagePositions() {
      updateGalvoSquare(Devices.Keys.GALVOA, galvoA_);
      updateGalvoSquare(Devices.Keys.GALVOB, galvoB_);
      updatePiezoSquare(Devices.Keys.PIEZOA, piezoA_);
      updatePiezoSquare(Devices.Keys.PIEZOB, piezoB_);
   }
   
   /**
    * Called whenever position updater stops
    */
   @Override
   public final void stoppedStagePositions() {
      Color c = stagePosUpdater_.isAcqRunning() ? Color.WHITE : Color.LIGHT_GRAY;
      galvoA_.setColor(c);
      galvoB_.setColor(c);
      piezoA_.setColor(c);
      piezoB_.setColor(c);
   }
   
}
