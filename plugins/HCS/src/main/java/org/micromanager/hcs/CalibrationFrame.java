/*//////////////////////////////////////////////////////////////////////////////
//FILE:           CalibrationFrame.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nico Stuurman
//
//COPYRIGHT:      Regents of the University of California, 2016
//
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
*/

package org.micromanager.hcs;


import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import mmcorej.DeviceType;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * Displays a dialog that lets the user select the well that the objective
 * is currently positioned on.
 * The user can either define the current position as the center of that well
 * or select the 4 edges and let the application calculate the center.
 * 
 * @author Nico Stuurman
 */
public class CalibrationFrame extends JFrame {
   private final String NOTSET = "Not Set Yet";
   private final String CALIBRATIONMETHOD = "CalibrationMethod";
   private final String CALIBRATIONLEGACY = "Legacy";
   private final String CALIBRATIONRECOMMENDED = "Recommended";
   
   public CalibrationFrame(final Studio studio, final SBSPlate plate, 
           final SiteGenerator siteGenerator) {
      
      final JFrame ourFrame = this;
      MutablePropertyMapView settings = studio.profile().getSettings(this.getClass());
      super.setTitle("Calibrate XY Stage");
      
      JPanel contents = new JPanel(
            new MigLayout("align, center, fillx, gap 12"));
      
      JLabel warningLabel1 = new JLabel("Wrong calibrations can result in damaged equipment! ");
      JLabel warningLabel2 = new JLabel("Always check that the calibration is correct!");
      Font warningFont = new Font(warningLabel1.getFont().getName(), Font.PLAIN, 16);
      warningLabel1.setFont(warningFont);
      warningLabel2.setFont(warningFont);
      warningLabel1.setForeground(Color.red);
      warningLabel2.setForeground(Color.red);
      contents.add(warningLabel1, "span 4, center, wrap");
      contents.add(warningLabel2, "span 4, center, wrap");
      contents.add (new JSeparator(), "span 4, grow, wrap");
      
      final List<String> rows = new ArrayList<String>();
      final Map<String, Integer> rowNumbers = new HashMap<String, Integer>();
      for (int i = 1; i < plate.getNumRows() + 1; i++) {
         rows.add(plate.getRowLabel(i));
         rowNumbers.put(plate.getRowLabel(i), i);
      }
      SpinnerModel model = new SpinnerListModel(rows);
      final JSpinner rowSpinner = new JSpinner(model);
      contents.add(new JLabel("row:"), "span 2, split 2, gap 60");
      contents.add(rowSpinner, "width 50, center, pushx, gap 5");
      
      model = new SpinnerNumberModel((int) 1, (int) 1, plate.getNumColumns(), (int) 1);
      final JSpinner columnSpinner = new JSpinner(model);
      contents.add(new JLabel("column:"), "span 2, split 2, gap 60");
      contents.add(columnSpinner, "width 50, center, pushx, gap 5, wrap");
      
      contents.add(new JLabel("Either position the XY stage at the center of " + 
              "the selected well and press OK."), "span 4, wrap");
        
      contents.add(new JSeparator(), "span4, grow, wrap");
      
      contents.add(new JLabel("Or mark the top, right, bottom, and left edge " + 
              "of the selected well and press OK"), "span 4, wrap");
      contents.add(new JLabel("Use live mode and a Pattern Overlay to position " +
              " edges in the center of the image."), "span 4, wrap");
      
      final Point2D.Double[] edges = new Point2D.Double[4];
      for (int i = 0; i < 4; i++) {
         edges[i] = new Point2D.Double();
      }
      
      JLabel topLabel = new JLabel(NOTSET);
      JButton topButton = new JButton("Top");
      topButton.addActionListener(new EdgeListener(studio, topLabel, edges[0]));
     
      JLabel leftLabel = new JLabel(NOTSET);
      JButton leftButton = new JButton("Left");
      leftButton.addActionListener(new EdgeListener(studio, leftLabel, edges[1]));
      
      JLabel rightLabel = new JLabel(NOTSET);
      JButton rightButton = new JButton("Right");
      rightButton.addActionListener(new EdgeListener(studio, rightLabel, edges[2]));
                 
      JLabel bottomLabel = new JLabel(NOTSET);
      JButton bottomButton = new JButton("Bottom");
      bottomButton.addActionListener(new EdgeListener(studio, bottomLabel, edges[3]));
      
      final JLabel[] edgeLabels = {leftLabel, topLabel, rightLabel, bottomLabel};
      
      contents.add(topButton, "span 4, center, wrap");
      contents.add(topLabel, "span 4, center, wrap");
      contents.add(leftButton, "span 2, center");
      contents.add(rightButton, "span 2, center, wrap");
      contents.add(leftLabel, "span 2, center");
      contents.add(rightLabel, "span 2, center, wrap");
      contents.add(bottomLabel, "span 4, center, wrap");
      contents.add(bottomButton, "span 4, center, wrap");
        
      contents.add(new JSeparator(), "span4, grow, wrap");
      
      JLabel methodLabel = new JLabel();
      JPanel methodPanel = new JPanel(new MigLayout("align, center, fillx"));
      JComboBox methodCombo = new JComboBox();
      methodPanel.add(methodLabel, "span 2, wrap");
      methodPanel.add(methodCombo, "span 2");
      methodLabel.setText("Calibration Method");
      methodCombo.setModel(new DefaultComboBoxModel(new String[] 
               {CALIBRATIONLEGACY,CALIBRATIONRECOMMENDED}));
      methodCombo.setToolTipText(
              "<html>"
                      + CALIBRATIONLEGACY + ": The XY stage adapter's coordinates will be reset. <br>"
                      + "Positions saved during this session of MicroManager will not <br>"
                      + "be valid in a new session unless the same calibration is run again. <br>"
                      + "Calibration will need to be performed every time this plugin is used.<br><br>"
                      + CALIBRATIONRECOMMENDED + ": Positions will be saved in terms of the default XY <br>"
                      + "coordinate system. Calibration will be saved and will only need to be <br>"
                      + "repeated if there is a change in XY stage hardware. Users of stages with <br>"
                      + "no homing functionality will need to ensure that their coordinate system <br>"
                      + "is consistent between sessions."
                      + "</html>");
      contents.add(methodPanel, "span 2");
      methodCombo.setSelectedItem(settings.getString(CALIBRATIONMETHOD, CALIBRATIONRECOMMENDED));
      
      JButton cancelButton = new JButton ("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            cleanup (edgeLabels);
            ourFrame.dispose();
         }
      });
      contents.add(cancelButton, "span 4, split 2, tag cancel");
      
      // OK Button, this is where all the action happens
      JButton OKButton = new JButton ("OK");
      OKButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int rowNr = rowNumbers.get( (String) rowSpinner.getValue());
            int colNr = (Integer) columnSpinner.getValue();
            // check for edges and be smart about it
            boolean allEdges = true;
            boolean anyEdge = false;
            for (JLabel edgeLabel : edgeLabels) {
               if (edgeLabel.getText().equals(NOTSET)) {
                  allEdges = false;
               } else {
                  anyEdge = true;
               }
            }
            // if the user sets some but not all edges we have a problem:
            if (anyEdge && !allEdges) {
               // unset all edges and inform the user
               for (JLabel edgeLabel : edgeLabels) {
                  edgeLabel.setText(NOTSET);
               }
               studio.logs().showMessage("Either set all edges or none");
               return;
            }
            // when we have all edges, move the stage to the center
            if (allEdges) {
               double leftX = edges[1].x;
               double rightX = edges[2].x;
               double topY = edges[0].y;
               double bottomY = edges[3].y;
               // Sanity checks:
               if (Math.abs(rightX - leftX) > plate.getWellSpacingX()) {
                  studio.logs().showError("The distance between the right and left edge \n" +
                          "is larger than the the well-to well distance for this plate.  Aborting");
                  return;
               }
               if (Math.abs(topY - bottomY) > plate.getWellSpacingY()) {
                  studio.logs().showError("The distance between the bottom and top edge \n" +
                          "is larger than the the well-to well distance for this plate.  Aborting");
                  return;
               }
               // Dangerous parts: move the stage to the middle, should the user be warned?
               double middleX = (rightX + leftX) / 2.0;
               double middleY = (topY + bottomY) / 2.0;
               try {
                  studio.getCMMCore().setXYPosition(middleX, middleY);
                  studio.getCMMCore().waitForDeviceType(DeviceType.XYStageDevice);
               } catch (Exception ex) {
                  studio.logs().showError(ex, "Failed to reset the stage's coordinates");
                  dispose();
                  return;
               }
            }     
            try {
                Point2D.Double pt = new Point2D.Double(plate.getFirstWellX() + (colNr - 1) * plate.getWellSpacingX(),
                        plate.getFirstWellY() + (rowNr  - 1) * plate.getWellSpacingY());
                Point2D.Double offset;
                Boolean saveCalibration;
                if (CALIBRATIONRECOMMENDED.equals(methodCombo.getSelectedItem())) { 
                    double x = studio.core().getXPosition();
                    double y = studio.core().getYPosition();
                    offset = new Point2D.Double(x - pt.getX(), y - pt.getY());
                    saveCalibration = true;
                   JOptionPane.showMessageDialog(ourFrame, 
                           "Plugin offset set at position: " + offset.x + "," + offset.y);
                } else { //Legacy. Adjust the coordinate system with no offset.
                    studio.getCMMCore().setAdapterOriginXY(pt.getX(), pt.getY());
                    offset = new Point2D.Double(0,0);
                    saveCalibration = false;
                    JOptionPane.showMessageDialog(ourFrame, 
                       "XY Stage set at position: " + pt.x + "," + pt.y);
                }
                settings.putString(CALIBRATIONMETHOD, (String) methodCombo.getSelectedItem());
                siteGenerator.finishCalibration(offset);
            } catch (Exception ex) {
               studio.logs().showError(ex, "Failed to reset the stage's coordinates");
            }
            cleanup (edgeLabels);
            dispose();
         }
      });
      
      contents.add(OKButton, "tag ok, wrap");
      
      super.add(contents);
      super.pack();
      super.setLocationRelativeTo(siteGenerator);
      super.setResizable(false);
      super.setVisible(true);
      
   }
   
   private class EdgeListener implements ActionListener {
      private final Studio studio_;
      private final JLabel label_;
      final private Point2D.Double pos_;
      public EdgeListener(Studio studio, JLabel label, Point2D.Double pos) {
         studio_ = studio;
         label_ = label;
         pos_ = pos;
      }
      @Override
      public void actionPerformed(ActionEvent e) {
         try {
            Point2D.Double xyStagePosition = studio_.getCMMCore().getXYStagePosition();
            pos_.x = xyStagePosition.x;
            pos_.y = xyStagePosition.y;
            label_.setText("" + pos_.x + ", " + pos_.y);

         } catch (Exception ex) {
            studio_.logs().showError(ex, "Failed to get XYStage position");
         }
      }
   }
   
   private void cleanup (JLabel[] edges) {
      for (JLabel edge : edges) {
         edge.setText(NOTSET);
      }
   }
   
}
