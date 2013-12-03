/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.imaging100x.twophoton;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.micromanager.utils.ReportingUtils;



/**
 *
 * @author Henry
 */
public class SettingsDialog extends JDialog {

   static public final String STAGE_IMAGE_ANGLE_OFFSET = "Theta";
   static public final String REAL_TIME_STITCH = "Realtimestitching";
   static public final String STITCHED_DATA_DIRECTORY = "Stitched data location";
   static public final String FREE_GB__MIN_IN_STITCHED_DATA = "Free GB minimum in stitched data dir";
   static public final String CREATE_IMS_FILE = "Create Imaris file";
   
   
   public SettingsDialog(final Preferences prefs) {
      super();
      this.setModal(true);
      this.setLayout(new BorderLayout());
      JPanel panel = new JPanel();
      this.add(panel, BorderLayout.CENTER);
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      final JSpinner theta = new JSpinner(new SpinnerNumberModel(prefs.getDouble(STAGE_IMAGE_ANGLE_OFFSET, 0),-45,45,0.1));
      row1.add(new JLabel("Stage-Image angle offset"));
      row1.add(theta);
      row1.add(new JLabel("degrees"));
      panel.add(row1);      
      
      JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      final JCheckBox realTimeStitch = new JCheckBox("Activate real time stitching");
      row2.add(realTimeStitch);
      realTimeStitch.setSelected(prefs.getBoolean(REAL_TIME_STITCH, false));
      panel.add(row2);
      realTimeStitch.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ReportingUtils.showMessage("Restart Micro-Manager for changes to take effect");
         }
      });
      
      JPanel rowskis = new JPanel (new FlowLayout(FlowLayout.LEFT));
      final JCheckBox saveIMS = new JCheckBox("Create Imaris file during acquisition");
      rowskis.add(saveIMS);
      saveIMS.setSelected(prefs.getBoolean(CREATE_IMS_FILE, false));
      panel.add(rowskis);
      
      
      JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      JLabel dir = new JLabel("Stitched saving location");
      row3.add(dir);
      final JTextField location = new JTextField(60);
      location.setText(prefs.get(STITCHED_DATA_DIRECTORY, ""));
      row3.add(location);
      panel.add(row3);
      
      JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      final JSpinner freeGig = new JSpinner(new SpinnerNumberModel(prefs.getInt(
              FREE_GB__MIN_IN_STITCHED_DATA,100),10,10000,1));
      row4.add(new JLabel("Minimum number of free GB to maintain in stitched data directory at application startup: "));
      row4.add(freeGig);
      panel.add(row4);
      
      
      JPanel lastRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
      panel.add(lastRow);
      JButton closeButton = new JButton("Close");
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            SettingsDialog.this.setVisible(false);
         }
      });
      lastRow.add(closeButton);
      
      final ActionListener saveSettings = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs.putBoolean(REAL_TIME_STITCH, realTimeStitch.isSelected());
            prefs.put(STITCHED_DATA_DIRECTORY, location.getText());
            prefs.putInt(FREE_GB__MIN_IN_STITCHED_DATA, (Integer) freeGig.getValue());
            prefs.putDouble(STAGE_IMAGE_ANGLE_OFFSET, (Double) theta.getValue());
            prefs.putBoolean(CREATE_IMS_FILE, saveIMS.isSelected());
         }
      };
      realTimeStitch.addActionListener(saveSettings);
      location.getDocument().addDocumentListener(new DocumentListener() {
         @Override 
         public void insertUpdate(DocumentEvent e) {
            saveSettings.actionPerformed(null);
         }
         @Override
         public void removeUpdate(DocumentEvent e) {
            saveSettings.actionPerformed(null);
         }
         @Override
         public void changedUpdate(DocumentEvent e) {
            saveSettings.actionPerformed(null);
         }
      });
      freeGig.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            saveSettings.actionPerformed(null);
         }
      });
      theta.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            saveSettings.actionPerformed(null);
         }
      });
      saveIMS.addActionListener(saveSettings);
      
      this.pack();
      this.setTitle("Settings");
      this.setLocationRelativeTo(null);
      this.setVisible(true);
   }

}
