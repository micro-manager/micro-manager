package org.micromanager.positionlist;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.utils.MMDialog;

/**
 * This class provides a dialog that allows the user to apply an offset to the
 * selected stage positions. Ultimately it calls
 * PositionListDlg.offsetSelectedSites to adjust positions.
 */
class OffsetPositionsDialog extends MMDialog {
   
   public OffsetPositionsDialog(final PositionListDlg parent) {
      super();
      
      setSize(new Dimension(320, 300));
      setTitle("Add offset to stage positions");
      setResizable(false);
      setModal(true);
      setLayout(new MigLayout("flowy"));

      JLabel label = new JLabel("<html><p>This dialog allows you to add offsets to the currently-selected stage positions.</p></html>");
      label.setPreferredSize(new Dimension(300, 20));
      add(label, "align center");

      // Create text inputs for each of the three axes. 
      final ArrayList<JTextField> fields = new ArrayList<JTextField>();
      for (String axisLabel : new String[]{"X", "Y", "Z"}) {
         JLabel prompt = new JLabel(String.format("%s offset: ", axisLabel));
         add(prompt, "split 2, gaptop 30, flowx");
         JTextField field = new JTextField(6);
         field.setText("0");
         add(field);
         fields.add(field);
      }

      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            // Parse the user's inputs and then call 
            // PositionListDlg.offsetSelectedSites.
            float[] offsets = new float[3];
            for (int i = 0; i < 3; ++i) {
               try {
                  offsets[i] = Float.parseFloat(fields.get(i).getText());
               }
               catch (java.lang.NumberFormatException e) {
                  // Assume no valid number was provided; skip it.
                  offsets[i] = 0;
               }
            }
            parent.offsetSelectedSites(offsets);
            dispose();
         }
      });
      add(okButton, "split 2, flowx");

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
            public void actionPerformed(ActionEvent event) {
               dispose();
            }
      });
      add(cancelButton);

      setVisible(true);
   }
}
