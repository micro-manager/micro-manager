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
package edu.valelab.gaussianfit;

import edu.valelab.gaussianfit.datasetdisplay.ParticlePairLister;
import edu.valelab.gaussianfit.utils.GUFrame;
import edu.valelab.gaussianfit.utils.FileDialogs;
import edu.valelab.gaussianfit.utils.NumberUtils;
import edu.valelab.gaussianfit.utils.ReportingUtils;

import net.miginfocom.swing.MigLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Dimension;
import java.io.File;
import java.text.ParseException;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
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
   private static final String SHOWIMAGEPREF = "showimage";
   private static final String SAVEPAIRTEXTFILEPREF = "savetextfile";
   private static final String FILEPATHPREF = "filepath";
   private static final String SHOWSUMMARYPREF = "showsummary";
   private static final String SHOWGRAPHPREF = "showgraph";
   private static final String SHOWTRACKSPREF = "showtrack";
   private static final String SHOWTRACKSUMMARYPREF = "showtracksummary";
   private static final String SAVETRACKSUMMARYFILEPREF = "savetracksummaryfile";
   private static final String SHOWOVERLAYPREF = "showoverlay";
   private static final String P2DPREF = "p2d";
   private static final String P2DFIXEDPREF = "p2dFixedSigma";
   private static final String SIGMAPREF = "sigma";
   private static final String SIGMAINPUTFROMDATA = "SigmaInputFromData";
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
      
      final JCheckBox showImage = 
              makeCheckBox("Show Pair distances in an image", SHOWIMAGEPREF);
      panel.add(showImage, "wrap");
      
      // save pair list as text file (checkbox)
      final JCheckBox savePairTextFile = 
              makeCheckBox ("Save Pair List as Text File", SAVEPAIRTEXTFILEPREF);
      final JCheckBox saveTrackSummaryFile = makeCheckBox(
              "Save Pair Track List as Text File", SAVETRACKSUMMARYFILEPREF);      
      final JLabel filePathLabel = new JLabel("FilePath:");
      final JTextField filePath = new JTextField();
      filePath.setText(up_.getString(this.getClass(), FILEPATHPREF, ""));
      final JButton dirButton = new JButton("...");
      final JComponent[] fileComps = {filePathLabel, filePath, dirButton};
      setEnabled(savePairTextFile.isSelected(), fileComps);
      ActionListener fileSavingActionListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setEnabled (savePairTextFile.isSelected() || 
                    saveTrackSummaryFile.isSelected() , fileComps);
         }
      };
      savePairTextFile.addActionListener(fileSavingActionListener);
      panel.add(savePairTextFile, "wrap");
      
      // summary per frame
      final JCheckBox showSummary = 
              makeCheckBox("Show per Frame summary", SHOWSUMMARYPREF);
      panel.add(showSummary, "wrap");
      
      // graph of average distance in each frame
      final JCheckBox showGraph = 
              makeCheckBox("Show Graph with per Frame errors", SHOWGRAPHPREF);
      panel.add(showGraph, "wrap");
      
      // list with tracks found
      final JCheckBox showTracks = 
              makeCheckBox("Show Pair Track list", SHOWTRACKSPREF);
      panel.add(showTracks, "wrap");
      
      panel.add(saveTrackSummaryFile, "wrap");
      saveTrackSummaryFile.addActionListener(fileSavingActionListener);
     
      final JCheckBox showTrackSummary =
              makeCheckBox("Show Pair Track Summary", SHOWTRACKSUMMARYPREF);
      panel.add(showTrackSummary, "wrap");
           
      // arrow overlay
      final JCheckBox showOverlay = 
              makeCheckBox("Show Pair Track Arrow overlay", SHOWOVERLAYPREF);
      panel.add(showOverlay, "wrap");
      
      // Distance estimate
      final JCheckBox distanceEstimate =
              makeCheckBox("Estimate average distance (P2D)", P2DPREF);
      
      // Distance estimate with fixed sigma
      final JCheckBox distanceEstimateFixedSigma =
              makeCheckBox("P2D with fixed sigma: ", P2DFIXEDPREF);
      
      final JTextField sigmaTextField = new JTextField();
      sigmaTextField.setMinimumSize(new Dimension(60, 20));
      sigmaTextField.setText(up_.getString(this.getClass(), SIGMAPREF, "10.0"));
      final JRadioButton useSigmaValue = new JRadioButton("");
      final JRadioButton estimateSigmaValue = new JRadioButton("from data");
      ButtonGroup group = new ButtonGroup();
      group.add(useSigmaValue);
      group.add(estimateSigmaValue);
      useSigmaValue.setSelected(up_.getBoolean(PairDisplayForm.class, SIGMAINPUTFROMDATA, false) == false);
      estimateSigmaValue.setSelected(up_.getBoolean(PairDisplayForm.class, SIGMAINPUTFROMDATA, false));
      useSigmaValue.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            up_.setBoolean(PairDisplayForm.class, SIGMAINPUTFROMDATA, !useSigmaValue.isSelected());
         }
      });
      estimateSigmaValue.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            up_.setBoolean(PairDisplayForm.class, SIGMAINPUTFROMDATA, estimateSigmaValue.isSelected());
         }
      });
      
      sigmaTextField.getDocument().addDocumentListener(
              makeDocumentListener(SIGMAPREF, sigmaTextField));
      distanceEstimate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            distanceEstimateFixedSigma.setEnabled(distanceEstimate.isSelected());
            sigmaTextField.setEnabled(distanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected());
            useSigmaValue.setEnabled(distanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected());
            estimateSigmaValue.setEnabled(distanceEstimate.isSelected()&& 
                    distanceEstimateFixedSigma.isSelected());
         }
      });
      distanceEstimateFixedSigma.addActionListener(new ActionListener(){
         @Override
         public void actionPerformed(ActionEvent ae){
            useSigmaValue.setEnabled(distanceEstimateFixedSigma.isSelected());
            estimateSigmaValue.setEnabled(distanceEstimateFixedSigma.isSelected());
         }
      });
      distanceEstimateFixedSigma.setEnabled(distanceEstimate.isSelected());
      sigmaTextField.setEnabled(distanceEstimate.isSelected() && 
                    distanceEstimateFixedSigma.isSelected());
      useSigmaValue.setEnabled(distanceEstimate.isSelected()&& 
                    distanceEstimateFixedSigma.isSelected());
      estimateSigmaValue.setEnabled(distanceEstimate.isSelected()&& 
                    distanceEstimateFixedSigma.isSelected());
      
      panel.add(distanceEstimate, "wrap");
      panel.add(distanceEstimateFixedSigma, "gapleft 60");
      panel.add(useSigmaValue, "split 2");
      panel.add(sigmaTextField, "wrap");
      panel.add(estimateSigmaValue, "skip 1, wrap");
      
      
      // basepath for the text file
      panel.add(filePathLabel, "split 3, span 3");
      filePath.setMinimumSize(new Dimension(250, 20));
      filePath.getDocument().addDocumentListener(
              makeDocumentListener(FILEPATHPREF, filePath));
      panel.add(filePath);
      dirButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setSaveDestinationDirectory(filePath);
            up_.setString(PairDisplayForm.class, FILEPATHPREF, filePath.getText());
         }
      });
      panel.add(dirButton, "wrap");
      
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
            DataCollectionForm.getInstance().listPairs(maxDistance, 
                    showPairList.isSelected(), showImage.isSelected(),
                    savePairTextFile.isSelected(), filePath.getText(),
                    showSummary.isSelected(), showGraph.isSelected() );
            
            ParticlePairLister.Builder ppb = new ParticlePairLister.Builder();
            ppb.maxDistanceNm(maxDistance).
                    showTrack(showTracks.isSelected()).
                    showSummary(showTrackSummary.isSelected()).
                    showOverlay(showOverlay.isSelected()).
                    saveFile(saveTrackSummaryFile.isSelected()).
                    filePath(filePath.getText()).
                    p2d(distanceEstimate.isSelected()).
                    sigmaEstimate(sigmaValue);
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
   
   private void setEnabled(boolean state, JComponent[] comps) {
      for (JComponent comp : comps) {
         comp.setEnabled(state);
      }
   }
   
   private void setSaveDestinationDirectory(JTextField rootField) {
      File result = FileDialogs.openDir(null,
              "Please choose a directory to save pair data",
              PAIR_DATA);
      if (result != null) {
         rootField.setText(result.getAbsolutePath());
      }
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