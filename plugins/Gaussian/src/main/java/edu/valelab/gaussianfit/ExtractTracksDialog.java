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
   private static final String MINFRAMES = "minframes";
   private static final String MAXMISSING = "maxmissing";
   private static final String MAXDISTANCE = "maxdistance";
   
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
              up.getInt(us, MINFRAMES, 10),1, null, 1));
      jp.add(minFramesSp, w + ", wrap");
      
      JLabel label2 = new JLabel("Max # missing Frames:");
      label.setFont(gFont);
      jp.add(label2);
      
      final JSpinner maxMissingSp = new JSpinner();
      maxMissingSp.setFont(gFont);
      maxMissingSp.setModel(new SpinnerNumberModel(
              up.getInt(us, MAXMISSING, 0), 0, null, 1));
      jp.add(maxMissingSp, w + ", wrap");
      
      JLabel label3 = new JLabel("Max. distance (nm)");
      label3.setFont(gFont);
      jp.add(label3);
      
      final JTextField distanceTF = new JTextField(
              NumberUtils.doubleToDisplayString(up.getDouble(us, MAXDISTANCE,90.0)));
      distanceTF.setFont(gFont);
      jp.add(distanceTF, w + ", wrap");
      
      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int minFrames = (Integer) minFramesSp.getValue();
            up.setInt(us, MINFRAMES, minFrames);
            int maxMissing = (Integer) maxMissingSp.getValue();
            up.setInt(us, MAXMISSING, maxMissing);
            double distance = Double.parseDouble(distanceTF.getText());
            up.setDouble(us, MAXDISTANCE, distance);
            SpotLinker.extractTracks(rowData, minFrames, maxMissing, distance);
            jf.dispose();
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
      jp.setPreferredSize(jp.getMinimumSize();
      jp.revalidate();
      
      jf.add(jp);
      jf.pack();
      
      jf.setResizable(false);
      jf.setLocation(p);
      jf.setVisible(true);

   }
   
}
