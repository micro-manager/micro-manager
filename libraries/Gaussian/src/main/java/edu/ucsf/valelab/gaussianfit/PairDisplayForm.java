/*
 * Copyright (c) 2015-2017, Regents of the University of California, San Francisco
 * Author: Nico Stuurman, nico.stuurman@ucsf.edu

 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package edu.ucsf.valelab.gaussianfit;

import edu.ucsf.valelab.gaussianfit.datasetdisplay.ParticlePairLister;
import edu.ucsf.valelab.gaussianfit.utils.FileDialogs;
import edu.ucsf.valelab.gaussianfit.utils.GUFrame;
import edu.ucsf.valelab.gaussianfit.utils.NumberUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * @author nico
 */
public class PairDisplayForm extends GUFrame {

   private static final String MAXDISTANCEPREF = "maxdistance";
   private static final String SHOWPAIRLISTPREF = "showpairlist";
   private static final String SHOWHISTOGRAMPREF = "showhistogram";
   private static final String SHOWTRACKSUMMARYPREF = "showtracksummary";
   private static final String SHOWOVERLAYPREF = "showoverlay";
   private static final String SHOWXYHISTOGRAMPREF = "showXYHistogram";
   private static final String P2DPREF = "p2d";
   private static final String P2DFRAMES = "p2dframes";
   private static final String P2DSINGLE = "p2dsingle";
   private static final String P2DMULTIPLE = "p2dmultiple";
   private static final String SIGMAPREF = "sigma";
   private static final String USEBOOTSTRAPPING = "useBootsTrapping";

   public static FileDialogs.FileType PAIR_DATA
         = new FileDialogs.FileType("PAIR_DATA",
         "Export to Location",
         System.getProperty("user.home") + "/Untitled",
         false, (String[]) null);
   final MutablePropertyMapView settings_;

   public PairDisplayForm(final Studio studio) {
      super(studio, PairDisplayForm.class);
      final GUFrame myFrame = this;
      settings_ = studio.profile().getSettings(this.getClass());
      super.loadPosition(100, 100, 250, 75);
      JPanel panel = new JPanel(new MigLayout(
            "ins 5",
            "[][grow]",
            "[][grow][]"));
      super.setTitle("Pair display options");

      // input box with max distance for a pair to be a pair
      panel.add(new JLabel("Maximum distance:"), "split 2, span 2");
      final JTextField distanceTextField = new JTextField();
      distanceTextField.setMinimumSize(new Dimension(60, 20));
      distanceTextField.setText(settings_.getString(MAXDISTANCEPREF, "50.0"));
      distanceTextField.getDocument().addDocumentListener(
            makeDocumentListener(MAXDISTANCEPREF, distanceTextField));
      panel.add(distanceTextField, "wrap");

      // pair list
      final JCheckBox showPairList =
            makeCheckBox("Show Pair list", SHOWPAIRLISTPREF);
      panel.add(showPairList, "wrap");

      final JCheckBox showPairTrackSummary =
            makeCheckBox("Show Pair Track Summary", SHOWTRACKSUMMARYPREF);
      panel.add(showPairTrackSummary, "wrap");

      // arrow overlay
      final JCheckBox showOverlay =
            makeCheckBox("Show Pair Track Arrow overlay", SHOWOVERLAYPREF);
      panel.add(showOverlay, "wrap");

      // 2 histograms, one with X and other with Y distance of pair members
      final JCheckBox showXYHistogram =
            makeCheckBox("Show X-Y distance histogram (registration error)", SHOWXYHISTOGRAMPREF);
      panel.add(showXYHistogram, "span 2, wrap");

      // Calculate Gaussian fit of vector distances (calculate distance from average x position and average y position)
      // final JCheckBox gaussianEstimate = 
      //        makeCheckBox("Use Gaussian fit of Vector distances", USEGAUSSIAN);

      // 2 histograms, one with X and other with Y distance of pair members
      final JCheckBox bootstrap =
            makeCheckBox("Error using bootstrapping (slow)", USEBOOTSTRAPPING);
      bootstrap.setEnabled(false);

      // Distance estimate
      final JCheckBox p2dDistanceEstimate =
            makeCheckBox("Calculate distance (P2D)", P2DPREF);

      final JRadioButton p2dSingle = new JRadioButton("from single frames");
      final JRadioButton p2dMultiple = new JRadioButton("from multiple frames");
      ButtonGroup group = new ButtonGroup();
      group.add(p2dSingle);
      group.add(p2dMultiple);

      // Registration error Sigma to use when doing P2D fit with fixed sigma
      final JLabel registrationLabel = new JLabel("Registration error: ");
      final JTextField registrationErrorTextField = new JTextField();
      registrationErrorTextField.setMinimumSize(new Dimension(60, 20));
      registrationErrorTextField.setText(settings_.getString(SIGMAPREF, "10.0"));
      registrationErrorTextField.getDocument().addDocumentListener(
            makeDocumentListener(SIGMAPREF, registrationErrorTextField));

      final JCheckBox showHistogram =
            makeCheckBox("Show histogram", SHOWHISTOGRAMPREF);

      String buttonSelection = settings_.getString(P2DFRAMES, P2DSINGLE);
      p2dSingle.setSelected(buttonSelection.equals(P2DSINGLE));
      p2dMultiple.setSelected(buttonSelection.equals(P2DMULTIPLE));
      bootstrap.setEnabled(p2dMultiple.isSelected());
      registrationErrorTextField.setEnabled(p2dSingle.isSelected());
      registrationLabel.setEnabled(p2dSingle.isSelected());

      p2dSingle.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            if (p2dSingle.isSelected()) {
               settings_.putString(P2DFRAMES, P2DSINGLE);
               registrationErrorTextField.setEnabled(p2dSingle.isSelected());
               registrationLabel.setEnabled(p2dSingle.isSelected());
               bootstrap.setEnabled(false);
            }
         }
      });
      p2dMultiple.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            if (p2dMultiple.isSelected()) {
               settings_.putString(P2DFRAMES, P2DMULTIPLE);
               registrationErrorTextField.setEnabled(!p2dMultiple.isSelected());
               registrationLabel.setEnabled(!p2dMultiple.isSelected());
               bootstrap.setEnabled(true);
            }
         }
      });

      showHistogram.setEnabled(p2dDistanceEstimate.isSelected());

      p2dDistanceEstimate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            p2dSingle.setEnabled(p2dDistanceEstimate.isSelected());
            p2dMultiple.setEnabled(p2dDistanceEstimate.isSelected());
            registrationErrorTextField.setEnabled(
                  p2dDistanceEstimate.isSelected() && p2dSingle.isSelected());
            bootstrap.setEnabled(
                  p2dDistanceEstimate.isSelected() && p2dMultiple.isSelected());
            showHistogram.setEnabled(p2dDistanceEstimate.isSelected());
         }
      });

      registrationErrorTextField.setEnabled(
            p2dDistanceEstimate.isSelected() && p2dSingle.isSelected());

      panel.add(p2dDistanceEstimate, "wrap");
      panel.add(p2dSingle, "gapleft 30, wrap");
      panel.add(registrationLabel, "split 2, gapleft 55");
      panel.add(registrationErrorTextField, "wrap");
      panel.add(p2dMultiple, "gapleft 30, wrap");
      panel.add(bootstrap, "gapleft 50, wrap");
      panel.add(showHistogram, "gapleft 30, wrap");

      // OK/Cancel buttons
      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {

            final double maxDistance;
            try {
               maxDistance = NumberUtils.displayStringToDouble(distanceTextField.getText());
            } catch (ParseException ex) {
               ReportingUtils.showError("Maximum distance should be a number");
               return;
            }
            final double registrationError;
            if (p2dDistanceEstimate.isSelected()) {
               try {
                  registrationError = NumberUtils
                        .displayStringToDouble(registrationErrorTextField.getText());
               } catch (ParseException ex) {
                  ReportingUtils.showError("Maximum distance should be a number");
                  return;
               }
            } else {
               registrationError = -1.0;
            }

            ParticlePairLister.Builder ppb = new ParticlePairLister.Builder();
            ppb.maxDistanceNm(maxDistance).
                  showPairs(showPairList.isSelected()).
                  showSummary(showPairTrackSummary.isSelected()).
                  showOverlay(showOverlay.isSelected()).
                  p2d(p2dDistanceEstimate.isSelected()).
                  showXYHistogram(showXYHistogram.isSelected()).
                  p2dSingleFrames(p2dSingle.isSelected()).
                  registrationError(registrationError).
                  showHistogram(showHistogram.isSelected()).
                  bootstrap(bootstrap.isSelected());
            DataCollectionForm.getInstance().listPairTracks(ppb);

            myFrame.dispose();
         }
      });
      panel.add(okButton, "span, split 3, tag ok");
      super.getRootPane().setDefaultButton(okButton);
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {

            myFrame.dispose();
         }
      });
      panel.add(cancelButton, "tag cancel");

      super.add(panel);
      super.pack();
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent ev) {
            myFrame.dispose();
         }
      });
   }

   @Override
   public void dispose() {
      super.dispose();
   }

   private JCheckBox makeCheckBox(final String text, final String prefName) {
      final JCheckBox jcb = new JCheckBox(text);
      jcb.setSelected(settings_.getBoolean(prefName, false));
      jcb.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            settings_.putBoolean(prefName, jcb.isSelected());
         }
      });
      return jcb;
   }

   private DocumentListener makeDocumentListener(final String prefName,
         final JTextField textField) {
      return new DocumentListener() {
         private void savePref() {
            settings_.putString(prefName, textField.getText());
         }

         @Override
         public void insertUpdate(DocumentEvent e) {
            savePref();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            savePref();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            savePref();
         }
      };
   }

}