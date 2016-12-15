/*
 * Simple dialog to collect information needed to extract tracks from 
 * a datasets with spot posiions
 *
 * Author: Nico Stuurman 2016
 * Copyright: Regents of the Univeristy of California
 * License: BSD
 */
package edu.valelab.gaussianfit;

import edu.valelab.gaussianfit.data.RowData;
import edu.valelab.gaussianfit.spotoperations.SpotLinker;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.internal.utils.NumberUtils;

/**
 *
 * @author Nico
 */
public class ExtractTracksDialog  {
   private static final String MINIMUM_NUMBER_OF_FRAMES = "minframes";
   private static final String MAXIMUM_NUMBER_OF_MISSING_FRAMES = "maxmissing";
   private static final String MAXIMUM_DISTANCE_BETWEEN_FRAMES = "maxdistance";
   private static final String MINIMUM_TOTAL_DISTANCE = "minimum_total_distance";
   
   ExtractTracksDialog(final Studio studio, final RowData rowData, final Point p) {
      final JFrame jf = new JFrame();
      jf.setTitle("Extract Tracks");
      
      JPanel jp = new JPanel();
      jp.setLayout(new MigLayout(""));
      
      Font gFont = new Font("Lucida Grande", 0, 10);
      final UserProfile up = studio.profile();
      final Class us = ExtractTracksDialog.class;
      
      JLabel label = new JLabel("Minimum # of Frames:");
      label.setFont(gFont);
      jp.add(label);
      final JSpinner minFramesSp = new JSpinner();
      minFramesSp.setFont(gFont);
      String w = "width 60:60:60";
      minFramesSp.setModel(new SpinnerNumberModel(
              up.getInt(us, MINIMUM_NUMBER_OF_FRAMES, 10),1, null, 1));
      jp.add(minFramesSp, w + ", wrap");
      
      JLabel label2 = new JLabel("Max # missing Frames:");
      label.setFont(gFont);
      jp.add(label2);
      final JSpinner maxMissingSp = new JSpinner();
      maxMissingSp.setFont(gFont);
      maxMissingSp.setModel(new SpinnerNumberModel(
              up.getInt(us, MAXIMUM_NUMBER_OF_MISSING_FRAMES, 0), 0, null, 1));
      jp.add(maxMissingSp, w + ", wrap");
      
      JLabel label3 = new JLabel("Max. distance (nm)");
      label3.setFont(gFont);
      jp.add(label3);
      final JTextField distanceTF = new JTextField(
              NumberUtils.doubleToDisplayString(up.getDouble(us, 
                      MAXIMUM_DISTANCE_BETWEEN_FRAMES, 90.0)));
      distanceTF.setFont(gFont);
      jp.add(distanceTF, w + ", wrap");
      
      JLabel label4 = new JLabel("Min. total distance (nm)");
      label4.setFont(gFont);
      jp.add(label4);
      final JTextField minTotalDistanceTF = new JTextField(
               NumberUtils.doubleToDisplayString(up.getDouble(us, 
                       MINIMUM_TOTAL_DISTANCE, 300.0)) );
      minTotalDistanceTF.setFont(gFont);
      jp.add(minTotalDistanceTF, w + ", wrap");
      
      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               int minFrames = (Integer) minFramesSp.getValue();
               up.setInt(us, MINIMUM_NUMBER_OF_FRAMES, minFrames);
               int maxMissing = (Integer) maxMissingSp.getValue();
               up.setInt(us, MAXIMUM_NUMBER_OF_MISSING_FRAMES, maxMissing);
               double distance = NumberUtils.displayStringToDouble(
                       distanceTF.getText());
               up.setDouble(us, MAXIMUM_DISTANCE_BETWEEN_FRAMES, distance);
               double minTotalDistance = NumberUtils.displayStringToDouble(
                       minTotalDistanceTF.getText());
               up.setDouble(us, MINIMUM_TOTAL_DISTANCE, minTotalDistance);
               int nrExtractedTracks = SpotLinker.extractTracks(rowData, minFrames, maxMissing, distance,
                       minTotalDistance);
               if (nrExtractedTracks == 0) {
                  studio.logs().showMessage("No tracks found with current settings");
               }
               jf.dispose();
            } catch (ParseException pe) {
               studio.logs().showError("Failed to parse numeric input");
            }
         }
      });
      jp.add(okButton, "span, split 2, tag ok");
      
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            jf.dispose();
         }
      });
      jp.add(cancelButton, "tag cancel, wrap");
      jp.setPreferredSize(jp.getMinimumSize());
      jp.revalidate();
      
      jf.add(jp);
      jf.pack();
      
      jf.setResizable(false);
      jf.setLocation(p);
      jf.setVisible(true);

   }
   
}
