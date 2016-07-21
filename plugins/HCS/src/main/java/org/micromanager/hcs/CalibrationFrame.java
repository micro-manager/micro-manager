/*//////////////////////////////////////////////////////////////////////////////
//FILE:           CalibrationFrame.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nico Stuurman
//
//COPYRIGHT:      Regents of the University of California, 2014-2016
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


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;


/**
 *
 * @author Nico Stuurman
 */
public class CalibrationFrame extends JFrame {
   private final String NOTSET = "Not Set";
   
   public CalibrationFrame(final Studio studio, final SBSPlate plate, 
           final SiteGenerator siteGenerator) {
      
      super.setTitle("Calibrate XY Stage");
      
      JPanel contents = new JPanel(
            new MigLayout("align, center, fillx, gap 10"));
      
      final List<String> rows = new ArrayList<String>();
      final Map<String, Integer> rowNumbers = new HashMap<String, Integer>();
      for (int i = 1; i < plate.getNumRows() + 1; i++) {
         rows.add(plate.getRowLabel(i));
         rowNumbers.put(plate.getRowLabel(i), i);
      }
      SpinnerModel model = new SpinnerListModel(rows);
      final JSpinner rowSpinner = new JSpinner(model);
      contents.add(new JLabel("row:"), "grow, gap 40");
      contents.add(rowSpinner, "width 50");
      
      model = new SpinnerNumberModel((int) 1, (int) 1, plate.getNumColumns(), (int) 1);
      final JSpinner columnSpinner = new JSpinner(model);
      contents.add(new JLabel("column"), "grow, gap 40");
      contents.add(columnSpinner, "width 50, wrap");
      
      contents.add(new JLabel("Either position the XY stage at the center of " + 
              "the selected well and press OK"), "span 4, wrap");
        
      contents.add(new JSeparator(), "span4, grow, wrap");
      
      contents.add(new JLabel("Or mark the top, right, bottom, and left edge " + 
              "of the selected well and press OK"), "span 4, wrap");
      
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
      
      contents.add(topButton, "span 4, center, wrap");
      contents.add(topLabel, "span 4, center, wrap");
      contents.add(leftButton, "center");
      contents.add(rightButton, "center, skip 2, wrap");
      contents.add(leftLabel, "center");
      contents.add(rightLabel, "center, skip 2, wrap");
      contents.add(bottomLabel, "span 4, center, wrap");
      contents.add(bottomButton, "span 4, center, wrap");
        
      contents.add(new JSeparator(), "span4, grow, wrap");
      
      JButton cancelButton = new JButton ("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      contents.add(cancelButton, "span 4, split 2, tag cancel");
      
      JButton OKButton = new JButton ("OK");
      OKButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int rowNr = rowNumbers.get( (String) rowSpinner.getValue());
            int colNr = (Integer) columnSpinner.getValue();
            // TODO: check for edges and be smart about it
            try {
               studio.getCMMCore().setAdapterOriginXY(
                       plate.getFirstWellX() + (rowNr - 1) * plate.getWellSpacingX(), 
                       plate.getFirstWellY() + (colNr - 1) * plate.getWellSpacingY() );
               siteGenerator.regenerate();
            } catch (Exception ex) {
               studio.logs().showError(ex, "Failed to reset the stage's coordinates");
            }
            dispose();
         }
      });
      contents.add(OKButton, "tag ok, wrap");
            
      super.add(contents);
      super.pack();
      super.setLocationRelativeTo(siteGenerator);
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
            label_.setText("" + xyStagePosition.x + ", " + xyStagePosition.y);
            pos_.x = xyStagePosition.x;
            pos_.y = xyStagePosition.y;
         } catch (Exception ex) {
            studio_.logs().showError(ex, "Failed to get XYStage position");
         }
      }
      
   }
   
}
