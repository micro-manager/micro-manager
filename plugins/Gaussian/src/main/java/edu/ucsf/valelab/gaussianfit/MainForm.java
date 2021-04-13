/**
 * MainForm.java
 *
 * Form showing the UI controlling tracking of single molecules using
 * Gaussian Fitting
 *
 * The real work is done in class GaussianTrackThread
 *
 * Created on Sep 15, 2010, 9:29:05 PM

* Copyright (c) 2010-2017, Regents of the University of California
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

import edu.ucsf.valelab.gaussianfit.fitmanagement.GaussianTrackThread;
import edu.ucsf.valelab.gaussianfit.fitmanagement.FitAllThread;
import com.google.common.eventbus.Subscribe;
import edu.ucsf.valelab.gaussianfit.algorithm.FindLocalMaxima;
import edu.ucsf.valelab.gaussianfit.data.GaussianInfo;
import edu.ucsf.valelab.gaussianfit.datasetdisplay.SpotOverlay;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import java.awt.Color;
import java.awt.Polygon;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import edu.ucsf.valelab.gaussianfit.utils.NumberUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import ij.process.ImageProcessor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayPositionChangedEvent;
import org.micromanager.display.DisplayWindow;



/**
 *
 * @author nico
 */
public class MainForm extends JFrame {
   private static final String NOISETOLERANCE = "NoiseTolerance";
   private static final String PCF = "PhotonConversionFactor";
   private static final String GAIN = "Gain";
   private static final String PIXELSIZE = "PixelSize";
   private static final String TIMEINTERVALMS = "TimeIntervalMs";
   private static final String ZSTEPSIZE = "ZStepSize";
   private static final String BACKGROUNDLEVEL = "BackgroundLevel";
   private static final String READNOISE = "ReadNoise";
   private static final String SIGMAMAX = "SigmaMax";
   private static final String SIGMAMIN = "SigmaMin";
   private static final String USEFILTER = "UseFilter";
   private static final String NRPHOTONSMIN = "NrPhotonsMin";
   private static final String NRPHOTONSMAX = "NrPhotonsMax";
   private static final String USENRPHOTONSFILTER = "UseNrPhotonsFilter";
   private static final String MAXITERATIONS = "MaxIterations";
   private static final String BOXSIZE = "BoxSize";
   private static final String USEFIXEDWIDTH = "UseFixedWidth";
   private static final String FIXEDWIDTH = "FixedWidth";
   private static final String FRAMEXPOS = "XPos";
   private static final String FRAMEYPOS = "YPos";
   private static final String FITMODE = "FitMode";
   private static final String FITSHAPE = "FitShape";
   private static final String ENDTRACKBOOL = "EndTrackBoolean";
   private static final String ENDTRACKINT = "EndTrackAfterN";
   private static final String PREFILTER = "PreFilterType";
   private static final String SKIPCHANNELS = "SkipChannels";
   private static final String CHANNELSKIPSTRING = "ChannelsToSkip";

   // we are a singleton with only one window
   public static boolean WINDOWOPEN = false;

   private final Studio studio_;
   
   // Store values of dropdown menus:
   private FindLocalMaxima.FilterType preFilterType_ = FindLocalMaxima.FilterType.NONE;

   private FitAllThread ft_;
   
   public AtomicBoolean aStop_ = new AtomicBoolean(false);
   
   // to keep track of front most window
   ImagePlus ip_ = null;
   
   // GUI elements
   private JToggleButton readParmsButton_;
   private JTextField photonConversionTextField_;
   private JTextField emGainTextField_;
   private JTextField pixelSizeTextField_;
   private JTextField timeIntervalTextField_;
   private JTextField zStepTextField_;
   private JTextField baseLevelTextField_;
   private JTextField readNoiseTextField_;
   
   private JToggleButton showOverlay_;
   private JComboBox preFilterComboBox_;
   private JTextField noiseToleranceTextField_;
   
   private JComboBox fitDimensionsComboBox1_;
   private JComboBox fitMethodComboBox1_;
   private JTextField boxSizeTextField;
   private JTextField maxIterationsTextField_;
   private JCheckBox useFixedWidthInFit_;
   private JTextField fixedWidthInFit_;
           
   private JLabel labelNPoints_;
   
   private JCheckBox filterDataCheckBoxWidth_;
   private JTextField minSigmaTextField_;
   private JTextField maxSigmaTextField_;
   private JCheckBox filterDataCheckBoxNrPhotons_;
   private JTextField maxNrPhotonsTextField_;
   private JTextField minNrPhotonsTextField_;
   private JCheckBox endTrackCheckBox_;
   private JSpinner endTrackSpinner_;
   
   private JTextField posTextField_;
   
   private JCheckBox skipChannelsCheckBox_;
   private JTextField channelsToSkip_;
   private JLabel widthLabel_;
   
   private JButton fitAllButton_;
   private JButton mTrackButton_;
   
   private final int nrThreads_;
   private final ExecutorService threadPool_;
   
   private final SpotOverlay spotOverlay_;


    /**
     * Creates new form MainForm
     * 
     * @param studio Instance of the Micro-Manager 2.0 api
     */
   public MainForm(Studio studio) {
 
      studio_ = studio;
      int nrThreads = ij.Prefs.getThreads();
      if (nrThreads > 8) {
         nrThreads = 8;
      }
      nrThreads_ = nrThreads;
      threadPool_ = Executors.newFixedThreadPool(nrThreads_);

      initComponents();

      spotOverlay_ = new SpotOverlay();

      UserProfile up = studio_.getUserProfile();
      Class oc = MainForm.class;
      noiseToleranceTextField_.setText((up.getString(oc, NOISETOLERANCE, "100")));
      photonConversionTextField_.setText(Double.toString(up.getDouble(oc, PCF, 10.41)));
      emGainTextField_.setText(Double.toString(up.getDouble(oc, GAIN, 50.0)));
      pixelSizeTextField_.setText(Double.toString(up.getDouble(oc, PIXELSIZE, 107.0)));
      baseLevelTextField_.setText(Double.toString(up.getDouble(oc, BACKGROUNDLEVEL, 100.0)));
      readNoiseTextField_.setText(Double.toString(up.getDouble(oc, READNOISE, 0.0)));
      timeIntervalTextField_.setText(Double.toString(up.getDouble(oc, TIMEINTERVALMS, 1.0)));
      zStepTextField_.setText(Double.toString(up.getDouble(oc, ZSTEPSIZE, 50.0)));

      pixelSizeTextField_.getDocument().addDocumentListener(new BackgroundCleaner(pixelSizeTextField_));
      emGainTextField_.getDocument().addDocumentListener(new BackgroundCleaner(emGainTextField_));
      timeIntervalTextField_.getDocument().addDocumentListener(new BackgroundCleaner(timeIntervalTextField_));

      minSigmaTextField_.setText(Double.toString(up.getDouble(oc, SIGMAMIN, 100.0)));
      maxSigmaTextField_.setText(Double.toString(up.getDouble(oc, SIGMAMAX, 200.0)));
      minNrPhotonsTextField_.setText(Double.toString(up.getDouble(oc, NRPHOTONSMIN, 500.0)));
      maxNrPhotonsTextField_.setText(Double.toString(up.getDouble(oc, NRPHOTONSMAX, 50000.0)));
      filterDataCheckBoxNrPhotons_.setSelected(up.getBoolean(oc, USENRPHOTONSFILTER, false));
      fitDimensionsComboBox1_.setSelectedIndex(up.getInt(oc, FITSHAPE, 1) - 1);
      fitMethodComboBox1_.setSelectedIndex(up.getInt(oc, FITMODE, 0));
      maxIterationsTextField_.setText(Integer.toString(up.getInt(oc, MAXITERATIONS, 250)));
      boxSizeTextField.setText(Integer.toString(up.getInt(oc, BOXSIZE, 8)));
      useFixedWidthInFit_.setSelected(up.getBoolean(oc, USEFIXEDWIDTH, false));
      fixedWidthInFit_.setText(Double.toString(up.getDouble(oc, FIXEDWIDTH, 250.0)));
      fixedWidthInFit_.setEnabled(useFixedWidthInFit_.isSelected());
      filterDataCheckBoxWidth_.setSelected(up.getBoolean(oc, USEFILTER, false));
      preFilterComboBox_.setSelectedIndex(up.getInt(oc, PREFILTER, 0));
      endTrackCheckBox_.setSelected(up.getBoolean(oc, ENDTRACKBOOL, false));
      endTrackSpinner_.setValue(up.getInt(oc, ENDTRACKINT, 0));
      skipChannelsCheckBox_.setSelected(up.getBoolean(oc, SKIPCHANNELS, false));
      channelsToSkip_.setText(up.getString(oc, CHANNELSKIPSTRING, ""));

      DocumentListener updateNoiseOverlay = new DocumentListener() {

         @Override
         public void changedUpdate(DocumentEvent documentEvent) {
            updateDisplay();
         }

         @Override
         public void insertUpdate(DocumentEvent documentEvent) {
            updateDisplay();
         }

         @Override
         public void removeUpdate(DocumentEvent documentEvent) {
            updateDisplay();
         }

         private void updateDisplay() {
            if (WINDOWOPEN && showOverlay_.isSelected()) {
               showNoiseTolerance();
            }
         }
      };

      updateWidthDisplay();
      updateNrPhotonsDisplay();
      updateEndTrack();
      noiseToleranceTextField_.getDocument().addDocumentListener(updateNoiseOverlay);
      boxSizeTextField.getDocument().addDocumentListener(updateNoiseOverlay);

      super.getRootPane().setDefaultButton(fitAllButton_);

      super.setTitle("Localization Microscopy");

      super.setLocation(up.getInt(oc, FRAMEXPOS, 100), up.getInt(oc, FRAMEYPOS, 100));

      super.setVisible(true);
   }

   private class BackgroundCleaner implements DocumentListener {

      JTextField field_;

      public BackgroundCleaner(JTextField field) {
         field_ = field;
      }

      private void updateBackground() {
         field_.setBackground(Color.DARK_GRAY);
      }

      @Override
      public void changedUpdate(DocumentEvent documentEvent) {
         updateBackground();
      }

      @Override
      public void insertUpdate(DocumentEvent documentEvent) {
         updateBackground();
      }

      @Override
      public void removeUpdate(DocumentEvent documentEvent) {
         updateBackground();
      }
   };
    

    /** This method is called from within the constructor to
     * initialize the form.

     */
    @SuppressWarnings("unchecked")
   private void initComponents() {

      filterDataCheckBoxWidth_ = new JCheckBox();
      
      photonConversionTextField_ = new JTextField();
      emGainTextField_ = new JTextField();
      baseLevelTextField_ = new JTextField();
      readNoiseTextField_ = new JTextField();
      minSigmaTextField_ = new JTextField();
      noiseToleranceTextField_ = new JTextField();
      pixelSizeTextField_ = new JTextField();
      preFilterComboBox_ = new JComboBox();
      fitDimensionsComboBox1_ = new JComboBox();
      timeIntervalTextField_ = new JTextField();
      maxIterationsTextField_ = new JTextField();
      maxSigmaTextField_ = new JTextField();
      boxSizeTextField = new JTextField();
      fitMethodComboBox1_ = new JComboBox();
      useFixedWidthInFit_ = new JCheckBox();
      fixedWidthInFit_ = new JTextField();
           
      filterDataCheckBoxNrPhotons_ = new JCheckBox();
      minNrPhotonsTextField_ = new JTextField();
      maxNrPhotonsTextField_ = new JTextField();
      endTrackCheckBox_ = new JCheckBox();
      endTrackSpinner_ = new JSpinner();
      readParmsButton_ = new JToggleButton();
      showOverlay_ = new JToggleButton();
      mTrackButton_ = new JButton();
      zStepTextField_ = new JTextField();
      labelNPoints_ = new JLabel();
      
      posTextField_ = new JTextField();
      channelsToSkip_ = new JTextField();
      
      fitAllButton_ = new JButton();
      
      Font gFont = new Font("Lucida Grande", 0, 10);
      Dimension textFieldDim = new Dimension(57,20);
      Dimension dropDownSize = new Dimension(90, 20);
      Dimension dropDownSizeMax = new Dimension(120, 20);
      String indent = "gapleft 20px";


      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            formWindowClosing(evt);
         }
         @Override
         public void windowClosed(java.awt.event.WindowEvent evt) {
            formWindowClosed(evt);
         }
      });
      
      getContentPane().setLayout(new MigLayout("insets 8", "", "[13]0[13]"));
      
      
/*-----------  Imaging Parameters  -----------*/
      getContentPane().add(new JLabel("Imaging parameters..."));
      
      readParmsButton_.setText("read");
      readParmsButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            readParmsButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(readParmsButton_, "wrap");

      JLabel jLabel = new JLabel("Photon Conversion factor");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);

      photonConversionTextField_.setFont(gFont); 
      photonConversionTextField_.setText("10.41");
      photonConversionTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(photonConversionTextField_, "wrap");

      jLabel = new JLabel("Linear (EM) Gain");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);
      
      emGainTextField_.setFont(gFont); 
      emGainTextField_.setText("50");
      emGainTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(emGainTextField_, "wrap");
  
      jLabel = new JLabel("PixelSize (nm)");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);
      
      pixelSizeTextField_.setFont(gFont); 
      pixelSizeTextField_.setText("0.8");
      pixelSizeTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(pixelSizeTextField_, "wrap");

      jLabel = new JLabel("Time Interval (ms)");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);
      
      timeIntervalTextField_.setFont(gFont); 
      timeIntervalTextField_.setText("0.8");
      timeIntervalTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(timeIntervalTextField_, "wrap");
 
      jLabel = new JLabel("Z-step (nm)");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);

      zStepTextField_.setFont(gFont); 
      zStepTextField_.setText("50");
      zStepTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(zStepTextField_, "wrap");
      
      jLabel = new JLabel("Camera Offset (counts)");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);
            
      baseLevelTextField_.setFont(gFont); 
      baseLevelTextField_.setText("100");
      baseLevelTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(baseLevelTextField_, "wrap");
      
      jLabel = new JLabel("Read Noise (e)");
      jLabel.setFont(gFont);
      getContentPane().add(jLabel, indent);
      
      readNoiseTextField_.setFont(gFont);
      readNoiseTextField_.setText("0.0");
      readNoiseTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(readNoiseTextField_, "wrap");
      
      getContentPane().add(new JSeparator(), "span, grow, wrap");
      
/*-----------  Find Maxima  -----------*/      
      jLabel = new JLabel("Find Maxima...");
      getContentPane().add(jLabel, "grow");

      showOverlay_.setText("show");
      showOverlay_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showOverlay_ActionPerformed(evt);
         }
      });
      getContentPane().add(showOverlay_, "wrap");
      
      jLabel = new JLabel("Pre-Filter");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);

      preFilterComboBox_.setFont(gFont); 
      preFilterComboBox_.setModel(new DefaultComboBoxModel(new String[] { "None", "Gaussian1-5" }));
      preFilterComboBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            preFilterComboBox_ActionPerformed(evt);
         }
      });
      preFilterComboBox_.setMinimumSize(dropDownSize);
      preFilterComboBox_.setMaximumSize(dropDownSizeMax);
      getContentPane().add(preFilterComboBox_, "wrap");

      labelNPoints_.setFont(gFont); 
      labelNPoints_.setText("n:       ");
      getContentPane().add(labelNPoints_, indent + ", split 2");

      jLabel = new JLabel("Noise tolerance");   
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, "right");
           
      noiseToleranceTextField_.setFont(gFont); 
      noiseToleranceTextField_.setText("2000");
      noiseToleranceTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(noiseToleranceTextField_, "wrap");
    
      getContentPane().add(new JSeparator(), "span, grow, wrap");
      
/*-----------  Fit Parameters  -----------*/
      jLabel = new JLabel("Fit Parameters...");
      getContentPane().add(jLabel, "left, wrap");
 
      jLabel = new JLabel("Dimensions");            
      jLabel.setFont(gFont);
      getContentPane().add(jLabel, indent);
         
      fitDimensionsComboBox1_.setFont(gFont); 
      fitDimensionsComboBox1_.setModel(new DefaultComboBoxModel(
              new String[] { "1", "2", "3" }));
      fitDimensionsComboBox1_.setMinimumSize(dropDownSize);
      fitDimensionsComboBox1_.setMaximumSize(dropDownSizeMax);
      fitDimensionsComboBox1_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (fitDimensionsComboBox1_.getSelectedIndex() != 0) {
               useFixedWidthInFit_.setSelected(false);
               fixedWidthInFit_.setEnabled(false);
            }
         }
      });
      getContentPane().add(fitDimensionsComboBox1_, "wrap");
 
      jLabel = new JLabel("Fitter");      
      jLabel.setFont(gFont);
      getContentPane().add(jLabel, indent);
      
      fitMethodComboBox1_.setFont(gFont); 
      fitMethodComboBox1_.setModel(new DefaultComboBoxModel(
              new String[] { "Simplex", "Levenberg-Marq", "Simplex-MLE", "LM-Weighted" }));
      fitMethodComboBox1_.setMinimumSize(dropDownSize);    
      fitMethodComboBox1_.setMaximumSize(dropDownSize);
      getContentPane().add(fitMethodComboBox1_, "gapright push, wrap");

      jLabel = new JLabel("Max Iterations");      
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);

      maxIterationsTextField_.setFont(gFont); 
      maxIterationsTextField_.setText("250");
      maxIterationsTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(maxIterationsTextField_, "wrap");

      jLabel = new JLabel("Box Size (pixels)");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, indent);

      boxSizeTextField.setFont(gFont); 
      boxSizeTextField.setText("16");
      boxSizeTextField.setMinimumSize(textFieldDim);
      getContentPane().add(boxSizeTextField, "wrap");
      
      useFixedWidthInFit_.setFont(gFont);
      useFixedWidthInFit_.setText("Fix Width");
      useFixedWidthInFit_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            fixedWidthInFit_.setEnabled(useFixedWidthInFit_.isSelected());
            fitDimensionsComboBox1_.setSelectedItem("1");
         }
      });
      getContentPane().add(indent, useFixedWidthInFit_);
      fixedWidthInFit_.setFont(gFont);
      fixedWidthInFit_.setText("250");
      fixedWidthInFit_.setMinimumSize(textFieldDim);
      getContentPane().add(fixedWidthInFit_, "split 2");
      jLabel = new JLabel("nm");
      jLabel.setFont(gFont);
      getContentPane().add(jLabel, "wrap");
      
      // HACK: not re-assigning jLabel as done below causes the last 
      // jLabel to not show. No idea why, but this works around the problem
      jLabel = new JLabel("Hack");
      getContentPane().add(new JSeparator(), "span 3, grow, wrap");
      
/*-----------  Filter Data  -----------*/
      getContentPane().add(new JLabel("Filter Data..."), "wrap");
      
      widthLabel_ = new JLabel(" nm < Width < "); 
      
      filterDataCheckBoxWidth_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateWidthDisplay();
         }
      });
      getContentPane().add(filterDataCheckBoxWidth_, indent + ",span 3, split 5");  

      minSigmaTextField_.setFont(gFont); 
      minSigmaTextField_.setText("100");
      minSigmaTextField_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            minSigmaTextFieldActionPerformed(evt);
         }
      });
      minSigmaTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(minSigmaTextField_);
          
      widthLabel_.setFont(gFont);
      getContentPane().add(widthLabel_);
      
      maxSigmaTextField_.setFont(gFont); 
      maxSigmaTextField_.setText("200");
      maxSigmaTextField_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            maxSigmaTextFieldActionPerformed(evt);
         }
      });
      maxSigmaTextField_.setMinimumSize(textFieldDim);
      getContentPane().add(maxSigmaTextField_);

      JLabel jLabel3 = new JLabel("nm");
      jLabel3.setFont(gFont); 
      getContentPane().add(jLabel3, "wrap");

      filterDataCheckBoxNrPhotons_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateNrPhotonsDisplay();
         }
      });
      getContentPane().add(filterDataCheckBoxNrPhotons_, indent + ", span, split 4");

      minNrPhotonsTextField_.setFont(gFont); 
      minNrPhotonsTextField_.setText("100");
      minNrPhotonsTextField_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            minNrPhotonsTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(minNrPhotonsTextField_);

      JLabel jLabel2 = new JLabel(" < # photons < ");
      jLabel2.setFont(gFont); 
      getContentPane().add(jLabel2);

      maxNrPhotonsTextField_.setFont(gFont); 
      maxNrPhotonsTextField_.setText("200");
      maxNrPhotonsTextField_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            maxNrPhotonsTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(maxNrPhotonsTextField_, "wrap");
 
      endTrackCheckBox_.setFont(gFont); 
      endTrackCheckBox_.setText("End track when missing");
      endTrackCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateEndTrack();
         }
      });
      getContentPane().add(endTrackCheckBox_, indent + ", span 3, split 3");

      endTrackSpinner_.setModel(new SpinnerNumberModel(1, 1, null, 1));
      getContentPane().add(endTrackSpinner_, "width 40");

      jLabel.setText(" frames");
      jLabel.setFont(gFont); 
      getContentPane().add(jLabel, "wrap");

      getContentPane().add(new JSeparator(), "span, grow, wrap");

      
/*-----------  Positions  -----------*/
      getContentPane().add(new JLabel("Positions..."), "wrap");

      JButton allPosButton = new JButton ("All");
      allPosButton.setFont(gFont); 
      allPosButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            allPosButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(allPosButton, indent + ",span, split 3");

      
      final JButton currentPosButton = new JButton();
      currentPosButton.setFont(gFont); 
      currentPosButton.setText("Current");
      currentPosButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            currentPosButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(currentPosButton);

      posTextField_.setFont(gFont); 
      posTextField_.setHorizontalAlignment(JTextField.RIGHT);
      posTextField_.setMinimumSize(textFieldDim);
      posTextField_.setText("1");
      getContentPane().add(posTextField_, "wrap");

      getContentPane().add(new JSeparator(), "span, grow, wrap");

/*-----------  Channels  -----------*/
      getContentPane().add(new JLabel("Skip Channels..."), "wrap");
      
      skipChannelsCheckBox_ = new JCheckBox();
      skipChannelsCheckBox_.setFont(gFont); 
      skipChannelsCheckBox_.setText("Skip channel(s):");
      getContentPane().add(skipChannelsCheckBox_, indent );
      
      channelsToSkip_.setMinimumSize(textFieldDim);
      getContentPane().add(channelsToSkip_, "wrap");
      
      
      getContentPane().add(new JSeparator(), "span, grow, wrap");
/*-----------  Buttons  -----------*/
 

      fitAllButton_.setText("Fit");
      fitAllButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fitAllButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(fitAllButton_, "span, split 3, align center");
      //fitAllButton_.setBounds(10, 530, 80, 30);


      JButton trackButton = new JButton("Track");
      trackButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            trackButtonActionPerformed(evt);
         }
      });
      getContentPane().add(trackButton);
      
      mTrackButton_.setText("MTrack");
      mTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mTrackButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(mTrackButton_, "wrap");

          
      JButton showButton = new JButton("Data");
      showButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showButtonActionPerformed(evt);
         }
      });
      getContentPane().add(showButton, "span, split 2, align center");

      JButton stopButton = new JButton("Stop");
      stopButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            stopButtonActionPerformed(evt);
         }
      });
      getContentPane().add(stopButton, "wrap");
    

      pack();
      
      setResizable(false);
   }

  
    private void trackButtonActionPerformed(java.awt.event.ActionEvent evt) {
       GaussianTrackThread tT = new GaussianTrackThread(IJ.getImage(), 
               FindLocalMaxima.FilterType.NONE);
       updateValues(tT);
       
       // Execute on another thread,
       // use tT.trackGaussians to run it on the same thread
       tT.init();
       System.out.println("started thread");
    }

    private void formWindowClosed(java.awt.event.WindowEvent evt) {
       WINDOWOPEN = false;
    }

    private void fitAllButton_ActionPerformed(java.awt.event.ActionEvent evt) {
       if (ft_ == null || !ft_.isRunning()) {
          ft_ = new FitAllThread(studio_, 
                  nrThreads_,
                  threadPool_,
                  preFilterType_, 
                  posTextField_.getText());
          updateValues(ft_);
          ft_.init();
       } else {
          JOptionPane.showMessageDialog(null, "Already running fitting analysis");
       }
    }

   private void updateWidthDisplay() {
      boolean selected = filterDataCheckBoxWidth_.isSelected();
      minSigmaTextField_.setEnabled(selected);
      widthLabel_.setEnabled(selected);
      maxSigmaTextField_.setEditable(selected);
    }
   
   private void updateNrPhotonsDisplay() {
      boolean selected = filterDataCheckBoxNrPhotons_.isSelected();
      minNrPhotonsTextField_.setEnabled(selected);
      maxNrPhotonsTextField_.setEnabled(selected);
   }
   
   private void updateEndTrack() {
      boolean selected = endTrackCheckBox_.isSelected();
      endTrackSpinner_.setEnabled(selected);
   }
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {
      try {
         UserProfile up = studio_.getUserProfile();
         Class oc = MainForm.class;
         up.setString(oc, NOISETOLERANCE, noiseToleranceTextField_.getText());
         up.setDouble(oc, PCF, NumberUtils.displayStringToDouble(photonConversionTextField_.getText()));
         up.setDouble(oc, GAIN, NumberUtils.displayStringToDouble(emGainTextField_.getText()));
         up.setDouble(oc, PIXELSIZE, NumberUtils.displayStringToDouble(pixelSizeTextField_.getText()));
         up.setDouble(oc, TIMEINTERVALMS, NumberUtils.displayStringToDouble(timeIntervalTextField_.getText()));
         up.setDouble(oc, ZSTEPSIZE, NumberUtils.displayStringToDouble(zStepTextField_.getText()));
         up.setDouble(oc, BACKGROUNDLEVEL, NumberUtils.displayStringToDouble(baseLevelTextField_.getText()));
         up.setDouble(oc, READNOISE, NumberUtils.displayStringToDouble(readNoiseTextField_.getText()));
         up.setBoolean(oc, USEFILTER, filterDataCheckBoxWidth_.isSelected());
         up.setDouble(oc, SIGMAMIN, NumberUtils.displayStringToDouble(minSigmaTextField_.getText()));
         up.setDouble(oc, SIGMAMAX, NumberUtils.displayStringToDouble(maxSigmaTextField_.getText()));
         up.setBoolean(oc, USENRPHOTONSFILTER, filterDataCheckBoxNrPhotons_.isSelected());
         up.setDouble(oc, NRPHOTONSMIN, NumberUtils.displayStringToDouble(minNrPhotonsTextField_.getText()));
         up.setDouble(oc, NRPHOTONSMAX, NumberUtils.displayStringToDouble(maxNrPhotonsTextField_.getText()));
         up.setInt(oc, MAXITERATIONS, NumberUtils.displayStringToInt(maxIterationsTextField_.getText()));
         up.setInt(oc, BOXSIZE, NumberUtils.displayStringToInt(boxSizeTextField.getText()));
         up.setBoolean(oc, USEFIXEDWIDTH, useFixedWidthInFit_.isSelected());
         up.setDouble(oc, FIXEDWIDTH, NumberUtils.displayStringToDouble(fixedWidthInFit_.getText()));
         up.setInt(oc, PREFILTER, preFilterComboBox_.getSelectedIndex());
         up.setInt(oc, FRAMEXPOS, getX());
         up.setInt(oc, FRAMEYPOS, getY());
         up.setBoolean(oc, ENDTRACKBOOL, endTrackCheckBox_.isSelected());
         up.setInt(oc, ENDTRACKINT, (Integer) endTrackSpinner_.getValue());
         up.setInt(oc, FITMODE, fitMethodComboBox1_.getSelectedIndex());
         up.setInt(oc, FITSHAPE, fitDimensionsComboBox1_.getSelectedIndex() + 1);
         up.setBoolean(oc, SKIPCHANNELS, skipChannelsCheckBox_.isSelected());
         up.setString(oc, CHANNELSKIPSTRING, channelsToSkip_.getText());
      } catch (ParseException ex) {
         ReportingUtils.logError(ex, "Error while closing Localization Microscopy plugin");
      }
      
      threadPool_.shutdownNow();

      WINDOWOPEN = false;

      this.setVisible(false);
   }

    public void formWindowOpened() {
       WINDOWOPEN = true;
    }
    
   @Override
   public void dispose() {
      formWindowClosing(null);
   }

   private void preFilterComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {
      String item = (String) preFilterComboBox_.getSelectedItem();
      if (item.equals("None")) {
         preFilterType_ = FindLocalMaxima.FilterType.NONE;
      }
      if (item.equals("Gaussian1-5")) {
         preFilterType_ = FindLocalMaxima.FilterType.GAUSSIAN1_5;
      }
      if (showOverlay_.isSelected()) {
         showNoiseTolerance();
      }
   }

   private void maxSigmaTextFieldActionPerformed(java.awt.event.ActionEvent evt) {
      if (Double.parseDouble(maxSigmaTextField_.getText())
              <= Double.parseDouble(minSigmaTextField_.getText())) {
         minSigmaTextField_.setText(Double.toString(Double.parseDouble(maxSigmaTextField_.getText()) - 1));
      }
   }

   private void minSigmaTextFieldActionPerformed(java.awt.event.ActionEvent evt) {
      if (Double.parseDouble(minSigmaTextField_.getText())
              >= Double.parseDouble(maxSigmaTextField_.getText())) {
         maxSigmaTextField_.setText(Double.toString(Double.parseDouble(minSigmaTextField_.getText()) + 1));
      }
   }

   private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {
      if (ft_ != null && ft_.isRunning()) {
         ft_.stop();
      }
      aStop_.set(true);
   }

   private void minNrPhotonsTextFieldActionPerformed(java.awt.event.ActionEvent evt) {
      if (Double.parseDouble(minNrPhotonsTextField_.getText())
              >= Double.parseDouble(maxNrPhotonsTextField_.getText())) {
         minNrPhotonsTextField_.setText(Double.toString(Double.parseDouble(maxNrPhotonsTextField_.getText()) - 1));
      }
   }

   private void maxNrPhotonsTextFieldActionPerformed(java.awt.event.ActionEvent evt) {
      if (Double.parseDouble(maxNrPhotonsTextField_.getText())
              <= Double.parseDouble(minNrPhotonsTextField_.getText())) {
         maxNrPhotonsTextField_.setText(Double.toString(Double.parseDouble(minNrPhotonsTextField_.getText()) + 1));
      }
   }

   private void showButtonActionPerformed(java.awt.event.ActionEvent evt) {
      DataCollectionForm dcForm = DataCollectionForm.getInstance();
      dcForm.setVisible(true);
   }


   private boolean showNoiseTolerance() {
      DataViewer dv = studio_.displays().getActiveDataViewer();

      // Roi originalRoi = siPlus.getRoi();
      // Find maximum in Roi, might not be needed....
      if (dv != null) {
         spotOverlay_.clearSquares();
         try {
            ImageProcessor iProc = studio_.data().ij().
                    createProcessor(dv.getDisplayedImages().get(0));
            ImagePlus siPlus = new ImagePlus("tmp", iProc);
            int val = Integer.parseInt(noiseToleranceTextField_.getText());
            int halfSize = Integer.parseInt(boxSizeTextField.getText()) / 2;
            Polygon pol = FindLocalMaxima.FindMax(siPlus, 2 * halfSize, val, preFilterType_);
            for (int i = 0; i < pol.npoints; i++) {
               int x = pol.xpoints[i];
               int y = pol.ypoints[i];
               spotOverlay_.addSquare(x, y, 2 * halfSize);
            }
            labelNPoints_.setText("n: " + pol.npoints);
         } catch (NumberFormatException nfEx) {
            // nothing to do
         } catch (IOException ex) {
            Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
      return true;
   }

   private void readParmsButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      // should not have made this a push button...
      readParmsButton_.setSelected(false);

      DisplayWindow currentWindow = studio_.displays().getCurrentWindow();
      if (currentWindow == null) {
         return;
      }
      Datastore dataStore = currentWindow.getDatastore();
      if (dataStore == null) {
         return;
      }
      SummaryMetadata summaryMetadata = dataStore.getSummaryMetadata();
      if (summaryMetadata.getWaitInterval() != null) {
         timeIntervalTextField_.setText(NumberUtils.doubleToDisplayString(
                 summaryMetadata.getWaitInterval()));
      }
      try {
         Image img = dataStore.getAnyImage();
         Metadata imgMetadata = img.getMetadata();
         double pixelSize = imgMetadata.getPixelSizeUm();
         pixelSizeTextField_.setText(NumberUtils.doubleToDisplayString(pixelSize * 1000.0));

         String camera = imgMetadata.getCamera();
         String key = camera + "-Output_Amplifier";
         double emGain = 1.0;
         if (imgMetadata.getScopeData().containsKey(key)) {
            if (!imgMetadata.getScopeData().getString(key).equals("Conventional")) {
               key = camera + "-EMGain";
               String gain = imgMetadata.getScopeData().getString(key);
               try {
                  emGain = NumberUtils.displayStringToDouble(gain);
               } catch (ParseException ex) {
                  studio_.logs().logError(ex, "Error parsing EM Gain");
               }
            }
         }
         emGainTextField_.setText(NumberUtils.doubleToDisplayString(emGain));

         int nrFrames = dataStore.getNextIndex(Coords.T);
         Image img0 = dataStore.getImage(img.getCoords().copyBuilder().channel(0).t(0).build());
         Image imgLast = dataStore.getImage(img.getCoords().copyBuilder().channel(0).t(nrFrames - 1).build());
         double startTimeMs = img0.getMetadata().getElapsedTimeMs(0.0);
         double endTimeMs = imgLast.getMetadata().getElapsedTimeMs(0.0);
         double msPerFrame = (endTimeMs - startTimeMs) / nrFrames;
         timeIntervalTextField_.setText(NumberUtils.doubleToDisplayString(msPerFrame));
      } catch (IOException ioe) {
         ReportingUtils.logError(ioe, "In Gaussian plugin show noise tolerance");
      }

   }

   @Subscribe
   public void OnImageChanged(DisplayPositionChangedEvent pe) {
      if (spotOverlay_.isVisible()) {
         showNoiseTolerance();
      }
   }

   private void showOverlay_ActionPerformed(java.awt.event.ActionEvent evt) {
      DataViewer viewer = studio_.displays().getActiveDataViewer(); 
      DisplayWindow dw = studio_.displays().getCurrentWindow();

      if (showOverlay_.isSelected()) {
         if (showNoiseTolerance()) {
            dw.addOverlay(spotOverlay_);
            spotOverlay_.setVisible(true);
            showOverlay_.setText("hide");
            if (viewer != null) {
               viewer.registerForEvents(this);
            }
         }
      } else {
         if (viewer != null) {
            viewer.unregisterForEvents(this);
         }
         spotOverlay_.setVisible(false);
         showOverlay_.setText("show");
         dw.removeOverlay(spotOverlay_);
      }
   }

   private void mTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {

      // Poor way of tracking multiple spots by running sequential tracks
      // TODO: optimize
      final ImagePlus siPlus;
      try {
         siPlus = IJ.getImage();
      } catch (Exception e) {
         return;
      }
      if (ip_ != siPlus) {
         ip_ = siPlus;
      }

      Runnable mTracker = new Runnable() {
         @Override
         public void run() {
            aStop_.set(false);
            int val = Integer.parseInt(noiseToleranceTextField_.getText());
            int halfSize = Integer.parseInt(boxSizeTextField.getText()) / 2;

            // If ROI manager is used, use RoiManager Rois
            //  may be dangerous if the user is not aware
            RoiManager roiM = RoiManager.getInstance();
            Roi[] rois = null;
            if (roiM != null) {
               rois = roiM.getSelectedRoisAsArray();
            }
            if (rois != null && rois.length > 0) {
               for (Roi roi : rois) {
                  siPlus.setRoi(roi, false);
                  Polygon pol = FindLocalMaxima.FindMax(siPlus, 2 *  halfSize, val, preFilterType_);
                  for (int i = 0; i < pol.npoints && !aStop_.get(); i++) {
                     int x = pol.xpoints[i];
                     int y = pol.ypoints[i];
                     siPlus.setRoi(x - 2 * halfSize, y - 2 * halfSize, 4 * halfSize, 4 * halfSize);
                     GaussianTrackThread tT = new GaussianTrackThread(siPlus,
                             FindLocalMaxima.FilterType.NONE);
                     updateValues(tT);
                     tT.trackGaussians(true);
                  }
               }
            } else {  // no Rois in RoiManager
               Polygon pol = FindLocalMaxima.FindMax(siPlus, 2 * halfSize, val, preFilterType_);
               for (int i = 0; i < pol.npoints && !aStop_.get(); i++) {
                  int x = pol.xpoints[i];
                  int y = pol.ypoints[i];
                  siPlus.setRoi(x - 2 * halfSize, y - 2 * halfSize, 4 * halfSize, 4 * halfSize);
                  GaussianTrackThread tT = new GaussianTrackThread(siPlus,
                          FindLocalMaxima.FilterType.NONE);
                  updateValues(tT);
                  tT.trackGaussians(true);
               }
            }
         }
      };

      (new Thread(mTracker)).start();

   }

   private void allPosButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      DataViewer dw = studio_.displays().getActiveDataViewer();

      int nrPos = 1;
      if (dw != null) {
         nrPos = dw.getDataProvider().getNextIndex(Coords.STAGE_POSITION);
      }
      if (nrPos > 1) {
         posTextField_.setText("1-" + nrPos);
      }

   }

   private void currentPosButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      DataViewer dw = studio_.displays().getActiveDataViewer();

      try {
         int pos = 1;
         if (dw != null ) {
            pos = dw.getDisplayedImages().get(0).getCoords().getStagePosition() + 1;
         }
         posTextField_.setText("" + pos);
      } catch (IOException ioe) {
      }
   }

   public void updateValues(GaussianInfo tT) {
      try {
         tT.setNoiseTolerance(Integer.parseInt(noiseToleranceTextField_.getText()));
         tT.setPhotonConversionFactor(NumberUtils.displayStringToDouble(photonConversionTextField_.getText()));
         tT.setGain(NumberUtils.displayStringToDouble(emGainTextField_.getText()));
         tT.setPixelSize((float) NumberUtils.displayStringToDouble(pixelSizeTextField_.getText()));
         tT.setZStackStepSize((float) NumberUtils.displayStringToDouble(zStepTextField_.getText()));
         tT.setTimeIntervalMs(NumberUtils.displayStringToDouble(timeIntervalTextField_.getText()));
         tT.setBaseLevel(NumberUtils.displayStringToDouble(baseLevelTextField_.getText()));
         tT.setReadNoise(NumberUtils.displayStringToDouble(readNoiseTextField_.getText()));
         tT.setUseWidthFilter(filterDataCheckBoxWidth_.isSelected());
         tT.setSigmaMin(NumberUtils.displayStringToDouble(minSigmaTextField_.getText()));
         tT.setSigmaMax(NumberUtils.displayStringToDouble(maxSigmaTextField_.getText()));
         tT.setUseNrPhotonsFilter(filterDataCheckBoxNrPhotons_.isSelected());
         tT.setNrPhotonsMin(NumberUtils.displayStringToDouble(minNrPhotonsTextField_.getText()));
         tT.setNrPhotonsMax(NumberUtils.displayStringToDouble(maxNrPhotonsTextField_.getText()));
         tT.setMaxIterations(Integer.parseInt(maxIterationsTextField_.getText()));
         tT.setHalfBoxSize((Integer.parseInt(boxSizeTextField.getText())) / 2);
         tT.setShape(fitDimensionsComboBox1_.getSelectedIndex() + 1);
         tT.setFitMode(fitMethodComboBox1_.getSelectedIndex() + 1);
         tT.setUseFixedWidth(useFixedWidthInFit_.isSelected());
         tT.setFixedWidthNm(NumberUtils.displayStringToDouble(fixedWidthInFit_.getText()));
         tT.setEndTrackBool(endTrackCheckBox_.isSelected());
         tT.setEndTrackAfterNFrames((Integer) endTrackSpinner_.getValue());
         tT.setSkipChannels(skipChannelsCheckBox_.isSelected());
         if (skipChannelsCheckBox_.isSelected()) {
            try {
               if (skipChannelsCheckBox_.isSelected()) {
                  String[] parts = channelsToSkip_.getText().split(",");
                  int[] result = new int[parts.length];
                  for (int i = 0; i < parts.length; i++) {
                     result[i] = NumberUtils.displayStringToInt(parts[i]);
                  }
                  tT.setChannelsToSkip(result);
               }
            } catch (NumberFormatException ex) {
               JOptionPane.showMessageDialog(null, "Error channels to skip : " + ex.getMessage());
            }
         }
      } catch (NumberFormatException ex) {
         JOptionPane.showMessageDialog(null, "Error interpreting input: " + ex.getMessage());
      } catch (ParseException ex) {
         JOptionPane.showMessageDialog(null, "Error interpreting input: " + ex.getMessage());
      }
   }

}