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
import edu.ucsf.valelab.gaussianfit.utils.GUFrame;
import edu.ucsf.valelab.gaussianfit.utils.FileDialogs;
import edu.ucsf.valelab.gaussianfit.utils.NumberUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;

import net.miginfocom.swing.MigLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Dimension;
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
import org.micromanager.Studio;
import org.micromanager.UserProfile;

/**
 *
 * @author nico
 */
public class PairDisplayForm extends GUFrame{
   private static final String MAXDISTANCEPREF = "maxdistance";
   private static final String SHOWPAIRLISTPREF = "showpairlist";
   private static final String SHOWHISTOGRAMPREF = "showhistogram";
   private static final String SHOWTRACKSUMMARYPREF = "showtracksummary";
   private static final String SHOWOVERLAYPREF = "showoverlay";
   private static final String SHOWXYHISTOGRAMPREF = "showXYHistogram";
   private static final String P2DPREF = "p2d";
   private static final String USEGAUSSIAN = "useGaussianOfVectDistances";
   private static final String P2DUSEVECTDISTANCE = "p2dUseVectDistances";
   private static final String P2DFIXEDPREF = "p2dFixedSigma";
   private static final String P2DERRORESTIMATE = "p2dEstimateError";
   private static final String SIGMAPREF = "sigma";
   private static final String SIGMAINPUT = "SigmaInput";
   private static final String SIGMAINPUTUSER = "SigmaInputUser";
   private static final String SIGMAINPUTDATAAVG = "SigmaInputDataAvg";
   private static final String SIGMAINPUTDATAIND = "SigmaInputDataIdividual";
   public static FileDialogs.FileType PAIR_DATA 
           = new FileDialogs.FileType("PAIR_DATA",
                 "Export to Location",
                 System.getProperty("user.home") + "/Untitled",
                 false, (String[]) null);
   final UserProfile up_;
   
   public PairDisplayForm(final Studio studio) {
      super(studio, PairDisplayForm.class);
      final GUFrame myFrame = this;
      up_ = studio.profile();
      super.loadPosition(100, 100, 250, 75);
      JPanel panel = new JPanel(new MigLayout(
              "ins 5", 
              "[][grow]", 
              "[][grow][]"));
      super.setTitle("Pair display options");
      
      // input box with max distance for a pair to be a pair
      panel.add(new JLabel("Maximum distance:" ), "split 2, span 2");
      final JTextField distanceTextField = new JTextField();
      distanceTextField.setMinimumSize(new Dimension(60, 20));
      distanceTextField.setText(up_.getString(this.getClass(), MAXDISTANCEPREF, "50.0"));
      distanceTextField.getDocument().addDocumentListener(
              makeDocumentListener(MAXDISTANCEPREF, distanceTextField));
      panel.add (distanceTextField, "wrap");
      
      
      // pair list
      final JCheckBox showPairList = 
              makeCheckBox ("Show Pair list", SHOWPAIRLISTPREF);
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
              makeCheckBox("Show X-Y distance histogram", SHOWXYHISTOGRAMPREF);
      panel.add(showXYHistogram, "wrap");
      
      // Calculate Gaussian fit of vector distances (calculate distance from average x position and average y position)
      final JCheckBox gaussianEstimate = 
              makeCheckBox("Use Gaussian fit of Vector distances", USEGAUSSIAN);
      
      // Distance estimate
      final JCheckBox p2dDistanceEstimate =
              makeCheckBox("Estimate average distance (P2D)", P2DPREF);
      
      // Use vector distances (calculate distance from average x position and average y position) in p2d
      final JCheckBox p2dUseVectDistance = 
              makeCheckBox("Use Vector distances", P2DUSEVECTDISTANCE);
      
      // Distance estimate with fixed sigma
      final JCheckBox distanceEstimateFixedSigma =
              makeCheckBox("P2D with fixed sigma: ", P2DFIXEDPREF);
      
      // Sigma to use when doing P2D fit with fixed sigma
      final JTextField sigmaTextField = new JTextField();
      sigmaTextField.setMinimumSize(new Dimension(60, 20));
      sigmaTextField.setText(up_.getString(this.getClass(), SIGMAPREF, "10.0"));
      
      // Select fixed sigma
      final JRadioButton useUserSigmaValue = new JRadioButton("");
            
      // Use Sigma from individual data
      final JRadioButton useIndividualSigma = new JRadioButton("from data (individual)");
 
           // Whether or not to estimae the SEM of the P2D
      final JCheckBox estimateP2DError = 
              makeCheckBox("Estimate Error (slow!)", P2DERRORESTIMATE);
      
      final JCheckBox showHistogram =
              makeCheckBox("Show histogram", SHOWHISTOGRAMPREF);
     
      ButtonGroup group = new ButtonGroup();
      group.add(useUserSigmaValue);
      // group.add(estimateSigmaValue);  
      group.add(useIndividualSigma);
  
      String buttonSelection = up_.getString(PairDisplayForm.class, SIGMAINPUT, SIGMAINPUTDATAAVG);
      useUserSigmaValue.setSelected(buttonSelection.equals(SIGMAINPUTUSER));
      // estimateSigmaValue.setSelected(buttonSelection.equals(SIGMAINPUTDATAAVG));
      useIndividualSigma.setSelected(buttonSelection.equals(SIGMAINPUTDATAIND));
      useUserSigmaValue.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            if (useUserSigmaValue.isSelected()) {
               up_.setString(PairDisplayForm.class, SIGMAINPUT, SIGMAINPUTUSER);
            }
         }
      });
      useIndividualSigma.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent ae) {
                 if (useIndividualSigma.isSelected()) {
                    up_.setString(PairDisplayForm.class, SIGMAINPUT, SIGMAINPUTDATAIND);
                 }
              }
      });
      
      sigmaTextField.getDocument().addDocumentListener(
              makeDocumentListener(SIGMAPREF, sigmaTextField));
      
      p2dUseVectDistance.setEnabled(p2dDistanceEstimate.isSelected());
      estimateP2DError.setEnabled(p2dDistanceEstimate.isSelected());
      showHistogram.setEnabled(p2dDistanceEstimate.isSelected() || 
              gaussianEstimate.isSelected());
      gaussianEstimate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            showHistogram.setEnabled(p2dDistanceEstimate.isSelected() || 
                     gaussianEstimate.isSelected());
         }
      });
      p2dDistanceEstimate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            p2dUseVectDistance.setEnabled(p2dDistanceEstimate.isSelected());
            distanceEstimateFixedSigma.setEnabled(p2dDistanceEstimate.isSelected() &&
                    !p2dUseVectDistance.isSelected() );
            sigmaTextField.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected() );
            useUserSigmaValue.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected());
            useIndividualSigma.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected() );
            estimateP2DError.setEnabled(p2dDistanceEstimate.isSelected());
            showHistogram.setEnabled(p2dDistanceEstimate.isSelected() || 
                     gaussianEstimate.isSelected());
         }
      });
      distanceEstimateFixedSigma.addActionListener(new ActionListener(){
         @Override
         public void actionPerformed(ActionEvent ae){
            useUserSigmaValue.setEnabled(distanceEstimateFixedSigma.isSelected());
            sigmaTextField.setEnabled(distanceEstimateFixedSigma.isSelected()); 
            useIndividualSigma.setEnabled(distanceEstimateFixedSigma.isSelected());
         }
      });
      p2dUseVectDistance.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            distanceEstimateFixedSigma.setEnabled(p2dDistanceEstimate.isSelected() &&
                    !p2dUseVectDistance.isSelected() );
            sigmaTextField.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected() );
            useUserSigmaValue.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected());
            useIndividualSigma.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected() );
         }
      });
      distanceEstimateFixedSigma.setEnabled(p2dDistanceEstimate.isSelected() && 
                    !p2dUseVectDistance.isSelected());
      sigmaTextField.setEnabled(p2dDistanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected());
      useUserSigmaValue.setEnabled(p2dDistanceEstimate.isSelected()&& 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected());
      useIndividualSigma.setEnabled(p2dDistanceEstimate.isSelected()&& 
                    distanceEstimateFixedSigma.isSelected() && 
                    !p2dUseVectDistance.isSelected());
      panel.add(gaussianEstimate, "wrap");
      panel.add(p2dDistanceEstimate);
      panel.add(p2dUseVectDistance, "wrap");
      panel.add(distanceEstimateFixedSigma, "gapleft 60");
      panel.add(useUserSigmaValue, "split 2");
      panel.add(sigmaTextField, "wrap");
      panel.add(useIndividualSigma, "skip 1, wrap");
      panel.add(estimateP2DError, "gapleft 60, wrap");
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
            final double sigmaValue;
            if (distanceEstimateFixedSigma.isSelected()) {
               try {
                  sigmaValue = NumberUtils.displayStringToDouble(sigmaTextField.getText());
               } catch (ParseException ex) {
                  ReportingUtils.showError("Maximum distance should be a number");
                  return;
               }
            } else {
               sigmaValue = -1.0;
            }
            
            ParticlePairLister.Builder ppb = new ParticlePairLister.Builder();
            ppb.maxDistanceNm(maxDistance).
                    showPairs(showPairList.isSelected()).
                    showSummary(showPairTrackSummary.isSelected()).
                    showOverlay(showOverlay.isSelected()).
                    doGaussianEstimate(gaussianEstimate.isSelected()).
                    p2d(p2dDistanceEstimate.isSelected()).
                    useVectorDistances(p2dUseVectDistance.isSelected()).
                    fitSigma(!distanceEstimateFixedSigma.isSelected()).
                    showXYHistogram(showXYHistogram.isSelected()).
                    useSigmaEstimate(useUserSigmaValue.isSelected()).
                    useIndividualSigmas(useIndividualSigma.isSelected()).
                    sigmaEstimate(sigmaValue).
                    showHistogram(showHistogram.isSelected()).
                    estimateP2DError(estimateP2DError.isSelected());
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
      final JCheckBox jcb =  new JCheckBox(text);
      jcb.setSelected(up_.getBoolean(PairDisplayForm.class, prefName, false));
      jcb.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            up_.setBoolean(PairDisplayForm.class, prefName, jcb.isSelected());
         }
      });
      return jcb;
   }
   
   private DocumentListener makeDocumentListener(final String prefName, 
           final JTextField textField) {
      return new DocumentListener() {
         private void savePref() {
            up_.setString(PairDisplayForm.class, prefName, textField.getText());
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