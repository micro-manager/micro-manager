/*
 * Simple dialog to collect information needed to extract tracks from 
 * a datasets with spot posiions
 *
 * Author: Nico Stuurman 2016


Copyright (c) 2016, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */
package edu.ucsf.valelab.gaussianfit;

import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.spotoperations.SpotLinker;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
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
   private static final String COMBINE_CHANNELS = "combine_channels";
   private static final String MAX_CHANNEL_DISTANCE = "max_channel_distance";
   
   ExtractTracksDialog(final Studio studio, final RowData rowData, final Point p) {
      final JFrame jf = new JFrame();
      jf.setTitle("Extract Tracks");

      jf.getContentPane().setLayout(new MigLayout("insets 20", "[fill]5[]", ""));
      
      Font gFont = new Font("Lucida Grande", 0, 10);
      final MutablePropertyMapView settings = 
              studio.profile().getSettings(ExtractTracksDialog.class);
      
      JLabel label = new JLabel("Minimum # of Frames:");
      label.setFont(gFont);
      jf.getContentPane().add(label);
      final JSpinner minFramesSp = new JSpinner();
      minFramesSp.setFont(gFont);
      String w = "width 60:60:60";
      minFramesSp.setModel(new SpinnerNumberModel(
              settings.getInteger(MINIMUM_NUMBER_OF_FRAMES, 10),1, null, 1));
      jf.getContentPane().add(minFramesSp, w + ", wrap");
      
      JLabel label2 = new JLabel("Max # missing Frames:");
      label2.setFont(gFont);
      jf.getContentPane().add(label2);
      final JSpinner maxMissingSp = new JSpinner();
      maxMissingSp.setFont(gFont);
      maxMissingSp.setModel(new SpinnerNumberModel(
              settings.getInteger(MAXIMUM_NUMBER_OF_MISSING_FRAMES, 0), 0, null, 1));
      jf.getContentPane().add(maxMissingSp, w + ", wrap");
      
      JLabel label3 = new JLabel("Max. distance (nm)");
      label3.setFont(gFont);
      jf.getContentPane().add(label3);
      final JTextField distanceTF = new JTextField(
              NumberUtils.doubleToDisplayString(settings.getDouble(
                      MAXIMUM_DISTANCE_BETWEEN_FRAMES, 90.0)));
      distanceTF.setFont(gFont);
      jf.getContentPane().add(distanceTF, w + ", wrap");
      
      JLabel label4 = new JLabel("Min. total distance (nm)");
      label4.setFont(gFont);
      jf.getContentPane().add(label4);
      final JTextField minTotalDistanceTF = new JTextField(
               NumberUtils.doubleToDisplayString(settings.getDouble(
                       MINIMUM_TOTAL_DISTANCE, 300.0)) );
      minTotalDistanceTF.setFont(gFont);
      jf.getContentPane().add(minTotalDistanceTF, w + ", wrap");
      
      final JLabel maxPairLabel = new JLabel("Max. pair distance (nm)");
      final JTextField maxPairDistance = new JTextField(
              NumberUtils.doubleToDisplayString(settings.getDouble(
                      MAX_CHANNEL_DISTANCE, 100.0)));
      
      final JCheckBox combineChannels = new JCheckBox("Combine tracks from all channels");
      combineChannels.setFont(gFont);
      combineChannels.setSelected(settings.getBoolean(COMBINE_CHANNELS, false));
      maxPairLabel.setEnabled(combineChannels.isSelected());
      maxPairDistance.setEnabled(combineChannels.isSelected());
      combineChannels.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            maxPairLabel.setEnabled(combineChannels.isSelected());
            maxPairDistance.setEnabled(combineChannels.isSelected());
         }
      });
      jf.getContentPane().add(combineChannels, "span 2, wrap");
      
       jf.getContentPane().add(maxPairLabel);
       jf.getContentPane().add(maxPairDistance, "wrap");
      
      
      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               int start = DataCollectionForm.getInstance().getNumberOfSpotData();
               int minFrames = (Integer) minFramesSp.getValue();
               settings.putInteger(MINIMUM_NUMBER_OF_FRAMES, minFrames);
               int maxMissing = (Integer) maxMissingSp.getValue();
               settings.putInteger(MAXIMUM_NUMBER_OF_MISSING_FRAMES, maxMissing);
               double distance = NumberUtils.displayStringToDouble(
                       distanceTF.getText());
               settings.putDouble(MAXIMUM_DISTANCE_BETWEEN_FRAMES, distance);
               double minTotalDistance = NumberUtils.displayStringToDouble(
                       minTotalDistanceTF.getText());
               settings.putDouble(MINIMUM_TOTAL_DISTANCE, minTotalDistance);
               boolean multiChannel = combineChannels.isSelected();
               settings.putBoolean(COMBINE_CHANNELS, multiChannel);
               double maxPairDistanceD = NumberUtils.displayStringToDouble(
                       maxPairDistance.getText());
               settings.putDouble(MAX_CHANNEL_DISTANCE, maxPairDistanceD);
               int nrExtractedTracks = SpotLinker.extractTracks(rowData, 
                       minFrames, maxMissing, distance, minTotalDistance,
                       multiChannel, maxPairDistanceD);
               if (nrExtractedTracks == 0) {
                  studio.logs().showMessage("No tracks found with current settings");
               } else {
                  int end = DataCollectionForm.getInstance().getNumberOfSpotData();
                  DataCollectionForm.getInstance().setSelectedRows(start, end - 1);
               }
               jf.dispose();
            } catch (ParseException pe) {
               studio.logs().showError("Failed to parse numeric input");
            }
         }
      });
      jf.getContentPane().add(okButton, "span, split 2, tag ok");
      
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            jf.dispose();
         }
      });
      jf.getContentPane().add(cancelButton, "tag cancel, wrap");
      jf.validate();
      // On Windows, the window ends up too wide.  Thi seems to be OS
      // specific.  When resizable is true, the window can not be made less wide.
      // Not sure how to override this.
      jf.pack();
      
      jf.setResizable(false);
      jf.setLocation(p);
      jf.setVisible(true);

   }
   
}
