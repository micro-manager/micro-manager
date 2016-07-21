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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;
import org.micromanager.internal.utils.MMFrame;


/**
 *
 * @author Nico Stuurman
 */
public class CalibrationFrame extends MMFrame {
   public CalibrationFrame(final Studio studio, final SBSPlate plate, 
           final SiteGenerator siteGenerator) {
      super.loadAndRestorePosition(100, 100);
      super.setTitle("Calibrate XY Stage");
      
      JPanel contents = new JPanel(
            new MigLayout("align, center, fillx"));
      contents.add(new JLabel("Manually position the XY stage at the center of " + 
              "the selected well and press OK"), "span 4, wrap");
      
      final List<String> rows = new ArrayList<String>();
      final Map<String, Integer> rowNumbers = new HashMap<String, Integer>();
      for (int i = 1; i < plate.getNumRows() + 1; i++) {
         rows.add(plate.getRowLabel(i));
         rowNumbers.put(plate.getRowLabel(i), i);
      }
      SpinnerModel model = new SpinnerListModel(rows);
      final JSpinner rowSpinner = new JSpinner(model);
      contents.add(new JLabel("row:"), "grow");
      contents.add(rowSpinner, "width 60");
      
      model = new SpinnerNumberModel((int) 1, (int) 1, plate.getNumColumns(), (int) 1);
      final JSpinner columnSpinner = new JSpinner(model);
      contents.add(new JLabel("column"), "grow");
      contents.add(columnSpinner, "width 60, wrap");
      
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
      super.setVisible(true);
      
   }
}
