///////////////////////////////////////////////////////////////////////////////
//FILE:          XYZGridPanel.java
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.BorderFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.micromanager.acquisition.ComponentTitledBorder;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Joystick.Directions;
import org.micromanager.asidispim.Utils.DeviceUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.MyNumberUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class XYZGridPanel extends ListeningJPanel {  // use some of the ListeningJPanel functionality but not other because not a tab in plugin
   
   private final ScriptInterface gui_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   private final StagePositionUpdater stagePosUpdater_;
   private final Positions positions_;
   private final CMMCore core_;
   
   private final JFormattedTextField planarSlopeXField_;
   private final JFormattedTextField planarSlopeYField_;
   private final JFormattedTextField planarOffsetZField_;
   private final JPanel gridXPanel_;
   private final JCheckBox useXGridCB_;
   private final JFormattedTextField gridXStartField_;
   private final JFormattedTextField gridXStopField_;
   private final JFormattedTextField gridXDeltaField_;
   private final JLabel gridXCount_;
   private final JPanel gridYPanel_;
   private final JCheckBox useYGridCB_;
   private final JFormattedTextField gridYStartField_;
   private final JFormattedTextField gridYStopField_;
   private final JFormattedTextField gridYDeltaField_;
   private final JLabel gridYCount_;
   private final JPanel gridZPanel_;
   private final JCheckBox useZGridCB_;
   private final JFormattedTextField gridZStartField_;
   private final JFormattedTextField gridZStopField_;
   private final JFormattedTextField gridZDeltaField_;
   private final JLabel gridZCount_;
   private final JCheckBox clearYZGridCB_;
   private final JButton limitsButton_;
   private final MMFrame limitsFrame_;
   private final JPanel limitsPanel_;
   
   /**
    * 
    * @param gui Micro-Manager api
    * @param devices the (single) instance of the Devices class
    * @param props Plugin-wide properties
    * @param prefs Plugin-wide preferences
    * @param stagePosUpdater Can query the controller for stage positionns
    */
   public XYZGridPanel(final ScriptInterface gui, Devices devices, Properties props, 
         Prefs prefs, StagePositionUpdater stagePosUpdater, Positions positions) {
      super (MyStrings.PanelNames.SETTINGS.toString(), // TODO we are lying here about the panel name, should probably change this but everyone will loose their prefs if we change
            new MigLayout(
              "", 
              "[right]10[center]10[center]",
              "[]0[]"));
     
      gui_ = gui;
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      stagePosUpdater_ = stagePosUpdater;
      positions_ = positions;
      core_ = gui.getMMCore();
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);

      // start XYZ grid frame
      // visibility of this frame is controlled from XYZ grid button
      // this frame is separate from main plugin window
      
      gridXPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));

      useXGridCB_ = pu.makeCheckBox("Slices from stage coordinates",
            Properties.Keys.PREFS_USE_X_GRID, panelName_, true);
      useXGridCB_.setEnabled(true);
      useXGridCB_.setFocusPainted(false); 
      ComponentTitledBorder componentBorder = 
            new ComponentTitledBorder(useXGridCB_, gridXPanel_, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      gridXPanel_.setBorder(componentBorder);
      
      // enable/disable panel elements depending on checkbox state
      useXGridCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            PanelUtils.componentsSetEnabled(gridXPanel_, useXGridCB_.isSelected());
         }
      });
            
      gridXPanel_.add(new JLabel("X start [um]:"));
      gridXStartField_ = pu.makeFloatEntryField(panelName_, "Grid_X_Start", -400, 5);
      gridXStartField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridXCount();
         }
      });
      gridXPanel_.add(gridXStartField_);
      JButton tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
         gridXStartField_.setValue(positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.X));
         updateGridXCount();
      }
      });
      gridXPanel_.add(tmp_but, "wrap");
      
      gridXPanel_.add(new JLabel("X stop [um]:"));
      gridXStopField_ = pu.makeFloatEntryField(panelName_, "Grid_X_Stop", 400, 5);
      gridXStopField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridXCount();
         }
      });
      gridXPanel_.add(gridXStopField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
           gridXStopField_.setValue(positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.X));
           updateGridXCount();
        }
      });
      gridXPanel_.add(tmp_but, "wrap");

      gridXPanel_.add(new JLabel("X delta [um]:"));
      gridXDeltaField_ = pu.makeFloatEntryField(panelName_, "Grid_X_Delta", 3, 5);
      gridXDeltaField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridXCount();
         }
      });
      gridXPanel_.add(gridXDeltaField_, "wrap");
      
      gridXPanel_.add(new JLabel("Slice count:"));
      gridXCount_ = new JLabel("");
      gridXPanel_.add(gridXCount_, "wrap");
      updateGridXCount();
      PanelUtils.componentsSetEnabled(gridXPanel_, useXGridCB_.isSelected());  // initialize
      
      gridYPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));

      useYGridCB_ = pu.makeCheckBox("Grid in Y",
            Properties.Keys.PREFS_USE_Y_GRID, panelName_, true);
      useYGridCB_.setEnabled(true);
      useYGridCB_.setFocusPainted(false); 
      componentBorder = 
            new ComponentTitledBorder(useYGridCB_, gridYPanel_, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      gridYPanel_.setBorder(componentBorder);
      
      // enable/disable panel elements depending on checkbox state
      useYGridCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            PanelUtils.componentsSetEnabled(gridYPanel_, useYGridCB_.isSelected());
         }
      });
            
      gridYPanel_.add(new JLabel("Y start [um]:"));
      gridYStartField_ = pu.makeFloatEntryField(panelName_, "Grid_Y_Start", -1200, 5);
      gridYStartField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridYCount();
         }
      });
      gridYPanel_.add(gridYStartField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
         gridYStartField_.setValue(positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.Y));
         updateGridYCount();
      }
      });
      gridYPanel_.add(tmp_but, "wrap");
      
      gridYPanel_.add(new JLabel("Y stop [um]:"));
      gridYStopField_ = pu.makeFloatEntryField(panelName_, "Grid_Y_Stop", 1200, 5);
      gridYStopField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridYCount();
         }
      });
      gridYPanel_.add(gridYStopField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
           gridYStopField_.setValue(positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.Y));
           updateGridYCount();
        }
      });
      gridYPanel_.add(tmp_but, "wrap");

      gridYPanel_.add(new JLabel("Y delta [um]:"));
      gridYDeltaField_ = pu.makeFloatEntryField(panelName_, "Grid_Y_Delta", 700, 5);
      gridYDeltaField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridYCount();
         }
      });
      gridYPanel_.add(gridYDeltaField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
           Devices.Keys camKey = ASIdiSPIM.getFrame().getAcquisitionPanel().isFirstSideA() ? Devices.Keys.CAMERAA : Devices.Keys.CAMERAB;
           int height;
           try {
              height = core_.getROI(devices_.getMMDevice(camKey)).height;
           } catch (Exception e) {
              height = 1;
           }
           float pixelSize = (float) core_.getPixelSizeUm();
           double delta = height*pixelSize;
           double overlap = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_GRID_OVERLAP_PERCENT); 
           delta *= (1-overlap/100);
           // sanity checks, would be better handled with exceptions or more formal checks
           if (height > 4100 || height < 4 || pixelSize < 1e-6) {
              return;
           }
           gridYDeltaField_.setValue((double)Math.round(delta));
           updateGridYCount();
        }
      });
      gridYPanel_.add(tmp_but, "wrap");
      
      gridYPanel_.add(new JLabel("Y count:"));
      gridYCount_ = new JLabel("");
      gridYPanel_.add(gridYCount_, "wrap");
      updateGridYCount();
      PanelUtils.componentsSetEnabled(gridYPanel_, useYGridCB_.isSelected());  // initialize


      gridZPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));

      useZGridCB_ = pu.makeCheckBox("Grid in Z",
            Properties.Keys.PREFS_USE_Z_GRID, panelName_, true);
      useZGridCB_.setEnabled(true);
      useZGridCB_.setFocusPainted(false); 
      componentBorder = 
            new ComponentTitledBorder(useZGridCB_, gridZPanel_, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      gridZPanel_.setBorder(componentBorder);
      
      // enable/disable panel elements depending on checkbox state
      useZGridCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            PanelUtils.componentsSetEnabled(gridZPanel_, useZGridCB_.isSelected());
         }
      });
      
      gridZPanel_.add(new JLabel("Z start [um]:"));
      gridZStartField_ = pu.makeFloatEntryField(panelName_, "Grid_Z_Start", 0, 5);
      gridZStartField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridZCount();
         }
      });
      gridZPanel_.add(gridZStartField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
         gridZStartField_.setValue(positions_.getUpdatedPosition(Devices.Keys.UPPERZDRIVE));
         updateGridZCount();
      }
      });
      gridZPanel_.add(tmp_but, "wrap");
      
      gridZPanel_.add(new JLabel("Z stop [um]:"));
      gridZStopField_ = pu.makeFloatEntryField(panelName_, "Grid_Z_Stop", -800, 5);
      gridZStopField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridZCount();
         }
      });
      gridZPanel_.add(gridZStopField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
           gridZStopField_.setValue(positions_.getUpdatedPosition(Devices.Keys.UPPERZDRIVE));
           updateGridZCount();
        }
      });
      gridZPanel_.add(tmp_but, "wrap");

      gridZPanel_.add(new JLabel("Z delta [um]:"));
      gridZDeltaField_ = pu.makeFloatEntryField(panelName_, "Grid_Z_Delta", 400, 5);
      gridZDeltaField_.addPropertyChangeListener("value", new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            updateGridZCount();
         }
      });
      gridZPanel_.add(gridZDeltaField_);
      tmp_but = new JButton("Set");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
           Devices.Keys camKey = ASIdiSPIM.getFrame().getAcquisitionPanel().isFirstSideA() ? Devices.Keys.CAMERAA : Devices.Keys.CAMERAB;
           int width;
           try {
              width = core_.getROI(devices_.getMMDevice(camKey)).width;
           } catch (Exception e) {
              width = 1;
           }
           float pixelSize = (float) core_.getPixelSizeUm();
           // sanity checks, would be better handled with exceptions or more formal checks
           if (width > 4100 || width < 4 || pixelSize < 1e-6) {
              return;
           }
           DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
           double delta = width*pixelSize/du.getStageGeometricSpeedFactor(true);  // base stage delta on PathA 
           double overlap = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_GRID_OVERLAP_PERCENT); 
           delta *= (1-overlap/100);
           gridZDeltaField_.setValue((double)Math.round(delta));
           updateGridZCount();
        }
      });
      gridZPanel_.add(tmp_but, "wrap");
      
      gridZPanel_.add(new JLabel("Z count:"));
      gridZCount_ = new JLabel("");
      gridZPanel_.add(gridZCount_, "wrap");
      updateGridZCount();
      PanelUtils.componentsSetEnabled(gridZPanel_, useZGridCB_.isSelected());  // initialize
      
      JPanel gridSettingsPanel = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));

      gridSettingsPanel.setBorder(PanelUtils.makeTitledBorder("Grid settings"));
      gridSettingsPanel.add(new JLabel("Overlap (Y and Z) [%]:"));
      JSpinner tileOverlapPercent = pu.makeSpinnerFloat(0, 100, 1,
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_GRID_OVERLAP_PERCENT, 10);
      gridSettingsPanel.add(tileOverlapPercent, "wrap");
      clearYZGridCB_ = pu.makeCheckBox("Clear position list if YZ unused",
              Properties.Keys.PREFS_CLEAR_YZ_GRID, panelName_, true);
      gridSettingsPanel.add(clearYZGridCB_, "span 2");
      
      JButton computeGridButton = new JButton("Compute grid");
      computeGridButton.setBackground(Color.green);
      computeGridButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            computeGrid(true, true);
         }
      });
      
      final JButton editPositionListButton2 = new JButton("Edit position list...");
      editPositionListButton2.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            gui_.showXYPositionList();
         }
      });
      
      
      JPanel planarCorrectionPanel = new JPanel(new MigLayout(
            "",
            "[right]10[center]10[center]",
            "[]8[]"));
      planarCorrectionPanel.setBorder(PanelUtils.makeTitledBorder("Planar Correction"));
      
      JCheckBox enablePlanarCorrectionCB = pu.makeCheckBox("Enable planar correction",
            Properties.Keys.PLUGIN_PLANAR_ENABLED, panelName_, false);
      planarCorrectionPanel.add(enablePlanarCorrectionCB, "left, span 3, wrap");
      // always start with planar correction off to avoid unwanted Z movement
      if (enablePlanarCorrectionCB.isSelected()) {
         enablePlanarCorrectionCB.doClick();
      }
      
      planarCorrectionPanel.add(new JLabel("X slope [\u00B5m/mm]"));
      planarSlopeXField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_PLANAR_SLOPE_X.toString(), 0, 5);
      planarCorrectionPanel.add(planarSlopeXField_);
      JButton zeroXSlope = new JButton("Set 0");
      zeroXSlope.setBackground(Color.red);
      zeroXSlope.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            planarSlopeXField_.setValue((Double)0.0);
         }
      });
      planarCorrectionPanel.add(zeroXSlope, "growx, wrap");
      
      planarCorrectionPanel.add(new JLabel("Y slope [\u00B5m/mm]"));
      planarSlopeYField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_PLANAR_SLOPE_Y.toString(), 0, 5);
      planarCorrectionPanel.add(planarSlopeYField_);
      JButton zeroYSlope = new JButton("Set 0");
      zeroYSlope.setBackground(Color.red);
      zeroYSlope.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            planarSlopeYField_.setValue((Double)0.0);
         }
      });
      planarCorrectionPanel.add(zeroYSlope, "growx, wrap");
      
      planarCorrectionPanel.add(new JLabel("Z offset [\u00B5m/mm]"));
      planarOffsetZField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_PLANAR_OFFSET_Z.toString(), 0, 5);
      planarCorrectionPanel.add(planarOffsetZField_);
      JButton setOffsetButton = new JButton("Set here");
      setOffsetButton.setBackground(Color.red);
      setOffsetButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            planarOffsetZField_.setValue((Double)positions_.getUpdatedPosition(Devices.Keys.UPPERZDRIVE));
         }
      });
      planarCorrectionPanel.add(setOffsetButton, "wrap");
      
      JButton computePlanarCorrection = new JButton("Compute correction from position list");
      computePlanarCorrection.setBackground(Color.green);
      computePlanarCorrection.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            final PositionList positionList;
            try {
               positionList = gui_.getPositionList();
               final int nrPositions = positionList.getNumberOfPositions();
               if (nrPositions < 3) {
                  ReportingUtils.showError("Must have at least 3 positions to compute planar correction");
                  return;
               }
               // compute best-fit plane by using matrix pseudo-inverse as described at https://math.stackexchange.com/a/2306029
               org.apache.commons.math.linear.Array2DRowRealMatrix matA = new org.apache.commons.math.linear.Array2DRowRealMatrix(nrPositions, 3);
               org.apache.commons.math.linear.Array2DRowRealMatrix matB = new org.apache.commons.math.linear.Array2DRowRealMatrix(nrPositions, 1);
               // compute center position to subtract off, this supposedly helps avoid singularities/numerical inaccuracies in matrix math
               double xTotal = 0.0;
               double yTotal = 0.0;
               double zTotal = 0.0;
               for (int row = 0; row < nrPositions; ++row) {
                  MultiStagePosition pos = positionList.getPosition(row);
                  xTotal += pos.getX();
                  yTotal += pos.getY();
                  zTotal += pos.getZ();
               }
               final double xCenter = xTotal/nrPositions;
               final double yCenter = yTotal/nrPositions;
               final double zCenter = zTotal/nrPositions;
               // form the matrixes
               for (int row = 0; row < nrPositions; ++row) {
                  MultiStagePosition pos = positionList.getPosition(row);
                  matA.setRow(row, new double[]{pos.getX()-xCenter, pos.getY()-yCenter, 1.0});
                  matB.setRow(row, new double[]{pos.getZ()-zCenter});
               }
               // compute the pseudoinverse and multiply by Z vector to give coefficients [a, b, c]
               //  such that a*x(n) + b*y(n) + c = z(n) is the best possible fix to the provided points
               // pseudoinverse computed as ((A*trans(A))^-1)*trans(A) per https://math.stackexchange.com/a/2306029
               org.apache.commons.math.linear.RealMatrix matA_trans = matA.transpose();
               org.apache.commons.math.linear.RealMatrix matA_square = matA_trans.multiply(matA);
               org.apache.commons.math.linear.RealMatrix matA_square_inv = new org.apache.commons.math.linear.LUDecompositionImpl(matA_square).getSolver().getInverse();
               org.apache.commons.math.linear.RealMatrix matA_pseudo_inv = matA_square_inv.multiply(matA_trans);
               org.apache.commons.math.linear.RealMatrix matX = matA_pseudo_inv.multiply(matB);
               double a = matX.getEntry(0, 0);
               double b = matX.getEntry(1, 0);
               // now correct the offset c for the center position that we subtracted off
               double c = matX.getEntry(2, 0) + zCenter - a*xCenter - b*yCenter;
               planarSlopeXField_.setValue((Double)(1000*a));
               planarSlopeYField_.setValue((Double)(1000*b));
               planarOffsetZField_.setValue((Double)c);
            } catch (Exception ex) {
               ReportingUtils.showError("Could not compute planar correction from position list.  Make sure the points specify a plane.");
               ReportingUtils.logError(ex.getStackTrace().toString());
            }
         }
      });
      planarCorrectionPanel.add(computePlanarCorrection, "span 3, growx");
      
      JPanel overviewAcquisitionPanel = new JPanel(new MigLayout(
            "",
            "[center]",
            "[]8[]"));
      overviewAcquisitionPanel.setBorder(PanelUtils.makeTitledBorder("Overview Acquisition"));
      
      JButton buttonOverviewAcq = new JButton("Run overview acquisition!");
      buttonOverviewAcq.setBackground(Color.green);
      buttonOverviewAcq.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ASIdiSPIM.getFrame().getAcquisitionPanel().runOverviewAcquisition();
         }
      });
      overviewAcquisitionPanel.add(buttonOverviewAcq, "growx, growy, wrap");
      overviewAcquisitionPanel.add(new JLabel("Corresponding settings on Data Analysis tab"));
      
      limitsButton_ = new JButton("XYZ stage limits...");
      limitsFrame_ = new MMFrame("diSPIM_XYZ_limits");
      limitsFrame_.setTitle("XYZ Limits");
      limitsFrame_.loadPosition(100, 100);
      limitsPanel_ = new LimitsPanel(prefs_, positions_);
      limitsFrame_.add(limitsPanel_);
      limitsFrame_.pack();
      limitsFrame_.setResizable(false);
      
      class LimitsFrameAdapter extends WindowAdapter {
         @Override
         public void windowClosing(WindowEvent e) {
            ((ListeningJPanel) limitsPanel_).windowClosing();
         }
      }
      limitsFrame_.addWindowListener(new LimitsFrameAdapter());
      
      limitsButton_.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
             limitsButton_.setSelected(true);
             limitsFrame_.setVisible(true);
          }
      });
      
      JPanel leftCol = new JPanel(new MigLayout(
            "flowy",
            "",
            "0[]8[]0"));
      leftCol.add(gridYPanel_);
      leftCol.add(gridXPanel_);
      
      JPanel midCol = new JPanel(new MigLayout(
            "flowy",
            "",
            "0[]8[]8[]8[]0"));
      midCol.add(gridZPanel_, "growx");
      midCol.add(gridSettingsPanel, "growx");
      midCol.add(computeGridButton, "growx, growy");
      midCol.add(editPositionListButton2, "growx");
      
      JPanel rightCol = new JPanel(new MigLayout(
            "flowy",
            "",
            "[]8[]"));
      rightCol.add(overviewAcquisitionPanel, "growx");
      rightCol.add(planarCorrectionPanel, "growx");
      rightCol.add(limitsButton_, "growx");
      
      add(leftCol);
      add(midCol);
      add(rightCol);

      // end XYZ grid frame
   }
   
   @Override
   public void windowClosing() {
      limitsFrame_.savePosition();
      ((ListeningJPanel) limitsPanel_).windowClosing();
      limitsFrame_.dispose();
   }
   
   /**
    * Called whenever position updater has refreshed positions
    * (this panel isn't registered as listener but AcquisitionPanel listener calls this)
    */
   @Override
   public final void updateStagePositions() {
      ((ListeningJPanel) limitsPanel_).updateStagePositions();
   }
   
   private int updateGridXCount() {
      double range = ((Double)gridXStartField_.getValue()) - ((Double)gridXStopField_.getValue());
      double delta = ((Double)gridXDeltaField_.getValue());
      if (Math.signum(range) != Math.signum(delta) && 
            !MyNumberUtils.floatsEqual(Math.abs(range), 0.0)) {
         delta *= -1;
         gridXDeltaField_.setValue(delta);
      }
      Integer count = (Integer)((int)Math.ceil(range/delta)) + 1;
      gridXCount_.setText(count.toString());
      return count;
   }
   
   private int updateGridYCount() {
      double range = ((Double)gridYStartField_.getValue()) - ((Double)gridYStopField_.getValue());
      double delta = ((Double)gridYDeltaField_.getValue());
      if (Math.signum(range) != Math.signum(delta) && 
            !MyNumberUtils.floatsEqual(Math.abs(range), 0.0)) {
         delta *= -1;
         gridYDeltaField_.setValue(delta);
      }
      Integer count = (Integer)((int)Math.ceil(range/delta)) + 1;
      gridYCount_.setText(count.toString());
      return count;
   }

   private int updateGridZCount() {
      double range = ((Double)gridZStartField_.getValue()) - ((Double)gridZStopField_.getValue());
      double delta = ((Double)gridZDeltaField_.getValue());
      if (Math.signum(range) != Math.signum(delta) &&
            !MyNumberUtils.floatsEqual(Math.abs(range), 0.0)) {
         delta *= -1;
         gridZDeltaField_.setValue(delta);
      }
      Integer count = (Integer)((int)Math.ceil(range/delta)) + 1;
      gridZCount_.setText(count.toString());
      return count;
   }

   public double getStartY() {
      return (Double)gridYStartField_.getValue();
   }

   public double getStopY() {
      return (Double)gridYStopField_.getValue();
   }

   public double getDeltaY() {
      return (Double)gridYDeltaField_.getValue();
   }

   public boolean getEnableY() {
      return useYGridCB_.isSelected();
   }

   public double getStartX() {
      return (Double)gridXStartField_.getValue();
   }

   public double getStopX() {
      return (Double)gridXStopField_.getValue();
   }

   public double getDeltaX() {
      return (Double)gridXDeltaField_.getValue();
   }

   public boolean getEnableX() {
      return useXGridCB_.isSelected();
   }

   public double getStartZ() {
      return (Double)gridZStartField_.getValue();
   }

   public double getStopZ() {
      return (Double)gridZStopField_.getValue();
   }

   public double getDeltaZ() {
      return (Double)gridZDeltaField_.getValue();
   }

   public boolean getEnableZ() {
      return useZGridCB_.isSelected();
   }

   public void setStartY(double startY) {
      gridYStartField_.setValue((Double)startY);
   }

   public void setStopY(double stopY) {
      gridYStopField_.setValue((Double)stopY);
   }

   public void setDeltaY(double deltaY) {
      gridYDeltaField_.setValue((Double)deltaY);
   }

   public void setEnableY(boolean enableY) {
      useYGridCB_.setSelected(!enableY);
      useYGridCB_.doClick();
   }

   public void setStartX(double startX) {
      gridXStartField_.setValue((Double)startX);
   }

   public void setStopX(double stopX) {
      gridXStopField_.setValue((Double)stopX);
   }

   public void setDeltaX(double deltaX) {
      gridXDeltaField_.setValue((Double)deltaX);
   }

   public void setEnableX(boolean enableX) {
      useXGridCB_.setSelected(!enableX);
      useXGridCB_.doClick();
   }

   public void setStartZ(double startZ) {
      gridZStartField_.setValue((Double)startZ);
   }

   public void setStopZ(double stopZ) {
      gridZStopField_.setValue((Double)stopZ);
   }

   public void setDeltaZ(double deltaZ) {
      gridZDeltaField_.setValue((Double)deltaZ);
   }

   public void setEnableZ(boolean enableZ) {
      useZGridCB_.setSelected(!enableZ);
      useZGridCB_.doClick();
   }
   
   /**
    * Computes grid (position list as well as slices/spacing) based on current settings
    * @param promptOnOverwrite raise dialog box before overwriting position list
    * @param doZgrid
    * @throws Exception
    */
   public void computeGrid(boolean promptOnOverwrite, boolean doZgrid) {
      final boolean useX = useXGridCB_.isSelected();
      final boolean useY = useYGridCB_.isSelected();
      final boolean useZ = useZGridCB_.isSelected() && doZgrid;
      final int numX = useX ? updateGridXCount() : 1;
      final int numY = useY ? updateGridYCount() : 1;
      final int numZ = useZ ? updateGridZCount() : 1;
      double centerX = (((Double)gridXStartField_.getValue()) + ((Double)gridXStopField_.getValue()))/2;
      double centerY = (((Double)gridYStartField_.getValue()) + ((Double)gridYStopField_.getValue()))/2;
      double centerZ = (((Double)gridZStartField_.getValue()) + ((Double)gridZStopField_.getValue()))/2;
      double deltaX = (Double)gridXDeltaField_.getValue();
      double deltaY = (Double)gridYDeltaField_.getValue();
      double deltaZ = (Double)gridZDeltaField_.getValue();
      double startY = centerY - deltaY*(numY-1)/2;
      double startZ = centerZ - deltaZ*(numZ-1)/2;
      String xy_device = devices_.getMMDevice(Devices.Keys.XYSTAGE);
      String z_device = devices_.getMMDevice(Devices.Keys.UPPERZDRIVE);

      if (useX) {
         try {
            DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
            ASIdiSPIM.getFrame().getAcquisitionPanel().setVolumeSliceStepSize(Math.abs(deltaX)/du.getStageGeometricSpeedFactor(true));
            ASIdiSPIM.getFrame().getAcquisitionPanel().setVolumeSlicesPerVolume(numX);
            if (!useY && !useZ) {
               // move to X center if we aren't generating position list with it
               positions_.setPosition(Devices.Keys.XYSTAGE, Directions.X, centerX);
               core_.waitForDevice(devices_.getMMDevice(Devices.Keys.XYSTAGE));
            }
         } catch (Exception ex) {
            // not sure what to do in case of error so ignore
         }
      } else {
         // use current X value as center; this was original behavior
         centerX = positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.X);
      }

      // if we aren't using one axis, use the current position instead of GUI position
      if (useY && !useZ) {
         startZ =  positions_.getUpdatedPosition(Devices.Keys.UPPERZDRIVE);
      }
      if (useZ && !useY) {
         startY =  positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.Y);
      }

      if (!useY && !useZ && !clearYZGridCB_.isSelected()) {
         return;
      }

      PositionList pl;
      try {
         pl = gui_.getPositionList();
      } catch (MMScriptException e) {
         pl = new PositionList();
      }
      boolean isPositionListEmpty = pl.getNumberOfPositions() == 0;
      if (!isPositionListEmpty && promptOnOverwrite) {
         boolean overwrite = MyDialogUtils.getConfirmDialogResult(
               "Do you really want to overwrite the existing position list?",
               JOptionPane.YES_NO_OPTION);
         if (!overwrite) {
            return;  // nothing to do
         }
      }
      pl = new PositionList();
      if (useY || useZ) {
         for (int iZ=0; iZ<numZ; ++iZ) {
            for (int iY=0; iY<numY; ++iY) {
               MultiStagePosition msp = new MultiStagePosition();
               if (useY) {
                  StagePosition s = new StagePosition();
                  s.stageName = xy_device;
                  s.numAxes = 2;
                  s.x = centerX;
                  s.y = startY + iY * deltaY;
                  msp.add(s);
               }
               if (useZ) {
                  StagePosition s2 = new StagePosition();
                  s2.stageName = z_device;
                  s2.x = startZ + iZ * deltaZ;
                  msp.add(s2);
               }
               msp.setLabel("Pos_" + iZ + "_" + iY);
               pl.addPosition(msp);
            }        
         }
      }
      try {
         gui_.setPositionList(pl);
      } catch (MMScriptException ex) {
         MyDialogUtils.showError(ex, "Couldn't overwrite position list with generated YZ grid");
      }
   }

   
   
}
