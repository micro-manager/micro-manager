/**
 * MainForm.java
 *
 * Form showing the UI controlling tracking of single molecules using
 * Gaussian Fitting
 *
 * The real work is done in class GaussianTrackThread
 *
 * Created on Sep 15, 2010, 9:29:05 PM
 */

package edu.valelab.gaussianfit;

import edu.valelab.gaussianfit.data.GaussianInfo;
import edu.valelab.gaussianfit.utils.MMWindowAbstraction;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import java.awt.Color;
import java.awt.Polygon;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.json.JSONException;
import org.json.JSONObject;
import edu.valelab.gaussianfit.utils.NumberUtils;
import edu.valelab.gaussianfit.utils.ReportingUtils;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicBoolean;



/**
 *
 * @author nico
 */
public class MainForm extends javax.swing.JFrame implements ij.ImageListener{
   private static final String NOISETOLERANCE = "NoiseTolerance";
   private static final String PCF = "PhotonConversionFactor";
   private static final String GAIN = "Gain";
   private static final String PIXELSIZE = "PixelSize";
   private static final String TIMEINTERVALMS = "TimeIntervalMs";
   private static final String ZSTEPSIZE = "ZStepSize";
   private static final String BACKGROUNDLEVEL = "BackgroundLevel";
   private static final String SIGMAMAX = "SigmaMax";
   private static final String SIGMAMIN = "SigmaMin";
   private static final String USEFILTER = "UseFilter";
   private static final String NRPHOTONSMIN = "NrPhotonsMin";
   private static final String NRPHOTONSMAX = "NrPhotonsMax";
   private static final String USENRPHOTONSFILTER = "UseNrPhotonsFilter";
   private static final String MAXITERATIONS = "MaxIterations";
   private static final String BOXSIZE = "BoxSize";
   private static final String FRAMEXPOS = "XPos";
   private static final String FRAMEYPOS = "YPos";
   private static final String FRAMEWIDTH = "Width";
   private static final String FRAMEHEIGHT = "Height";
   private static final String FITMODE = "FitMode";
   private static final String ENDTRACKBOOL = "EndTrackBoolean";
   private static final String ENDTRACKINT = "EndTrackAfterN";
   private static final String PREFILTER = "PreFilterType";

   // we are a singleton with only one window
   public static boolean WINDOWOPEN = false;

   Preferences prefs_;

   // Store values of dropdown menus:
   private int shape_ = 1;
   private final int fitMode_ = 2;
   private FindLocalMaxima.FilterType preFilterType_ = FindLocalMaxima.FilterType.NONE;

   private FitAllThread ft_;
   
   public AtomicBoolean aStop_ = new AtomicBoolean(false);

   private int lastFrame_ = -1;
   
   // to keep track of front most window
   ImagePlus ip_ = null;

    /**
     * Creates new form MainForm
     * 
     */
    public MainForm() {
       initComponents();

       if (prefs_ == null)
            prefs_ = Preferences.userNodeForPackage(this.getClass());
       noiseToleranceTextField_.setText(Integer.toString(prefs_.getInt(NOISETOLERANCE,100)));
       photonConversionTextField.setText(Double.toString(prefs_.getDouble(PCF, 10.41)));
       emGainTextField_.setText(Double.toString(prefs_.getDouble(GAIN, 50)));
       pixelSizeTextField_.setText(Double.toString(prefs_.getDouble(PIXELSIZE, 107.0)));
       baseLevelTextField.setText(Double.toString(prefs_.getDouble(BACKGROUNDLEVEL, 100)));
       timeIntervalTextField_.setText(Double.toString(prefs_.getDouble(TIMEINTERVALMS, 1)));
       zStepTextField_.setText(Double.toString(prefs_.getDouble(ZSTEPSIZE, 50)));                   
       pixelSizeTextField_.getDocument().addDocumentListener(new BackgroundCleaner(pixelSizeTextField_));
       emGainTextField_.getDocument().addDocumentListener(new BackgroundCleaner(emGainTextField_));      
       timeIntervalTextField_.getDocument().addDocumentListener(new BackgroundCleaner(timeIntervalTextField_));
       
       minSigmaTextField.setText(Double.toString(prefs_.getDouble(SIGMAMIN, 100)));
       maxSigmaTextField.setText(Double.toString(prefs_.getDouble(SIGMAMAX, 200)));
       minNrPhotonsTextField.setText(Double.toString(prefs_.getDouble(NRPHOTONSMIN, 500)));
       maxNrPhotonsTextField.setText(Double.toString(prefs_.getDouble(NRPHOTONSMAX, 50000)));
       filterDataCheckBoxNrPhotons.setSelected(prefs_.getBoolean(USENRPHOTONSFILTER, false));
       fitMethodComboBox1.setSelectedIndex(prefs_.getInt(FITMODE, 0));
       maxIterationsTextField.setText(Integer.toString(prefs_.getInt(MAXITERATIONS, 250)));
       boxSizeTextField.setText(Integer.toString(prefs_.getInt(BOXSIZE, 16)));
       filterDataCheckBoxWidth.setSelected(prefs_.getBoolean(USEFILTER, false));
       preFilterComboBox_.setSelectedIndex(prefs_.getInt(PREFILTER, 0));
       endTrackCheckBox_.setSelected(prefs_.getBoolean(ENDTRACKBOOL, false));
       endTrackSpinner_.setValue(prefs_.getInt(ENDTRACKINT, 0));
             
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

       noiseToleranceTextField_.getDocument().addDocumentListener(updateNoiseOverlay);
       boxSizeTextField.getDocument().addDocumentListener(updateNoiseOverlay);
          

       setTitle("Localization Microscopy");
       
       // wdith on Mac should be 250, Windows 270
       setBounds(prefs_.getInt(FRAMEXPOS, 100), prefs_.getInt(FRAMEYPOS, 100), 270, 625);
       ImagePlus.addImageListener(this);
       setVisible(true);
    }
    
    
   private class BackgroundCleaner implements DocumentListener {

      JTextField field_;

      public BackgroundCleaner(JTextField field) {
         field_ = field;
      }

      private void updateBackground() {
         field_.setBackground(Color.white);
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
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jButton1 = new javax.swing.JButton();
      jLabel1 = new javax.swing.JLabel();
      jLabel3 = new javax.swing.JLabel();
      jLabel4 = new javax.swing.JLabel();
      filterDataCheckBoxWidth = new javax.swing.JCheckBox();
      jLabel5 = new javax.swing.JLabel();
      jLabel6 = new javax.swing.JLabel();
      photonConversionTextField = new javax.swing.JTextField();
      emGainTextField_ = new javax.swing.JTextField();
      baseLevelTextField = new javax.swing.JTextField();
      jLabel7 = new javax.swing.JLabel();
      jLabel8 = new javax.swing.JLabel();
      jLabel9 = new javax.swing.JLabel();
      jSeparator4 = new javax.swing.JSeparator();
      minSigmaTextField = new javax.swing.JTextField();
      trackButton = new javax.swing.JButton();
      jSeparator1 = new javax.swing.JSeparator();
      jSeparator2 = new javax.swing.JSeparator();
      jSeparator3 = new javax.swing.JSeparator();
      noiseToleranceTextField_ = new javax.swing.JTextField();
      pixelSizeTextField_ = new javax.swing.JTextField();
      jLabel13 = new javax.swing.JLabel();
      fitAllButton_ = new javax.swing.JButton();
      jLabel10 = new javax.swing.JLabel();
      jSeparator5 = new javax.swing.JSeparator();
      jLabel11 = new javax.swing.JLabel();
      jLabel12 = new javax.swing.JLabel();
      jLabel14 = new javax.swing.JLabel();
      preFilterComboBox_ = new javax.swing.JComboBox();
      fitDimensionsComboBox1 = new javax.swing.JComboBox();
      timeIntervalTextField_ = new javax.swing.JTextField();
      jLabel15 = new javax.swing.JLabel();
      jLabel17 = new javax.swing.JLabel();
      maxIterationsTextField = new javax.swing.JTextField();
      maxSigmaTextField = new javax.swing.JTextField();
      jLabel18 = new javax.swing.JLabel();
      jLabel2 = new javax.swing.JLabel();
      boxSizeTextField = new javax.swing.JTextField();
      stopButton = new javax.swing.JButton();
      filterDataCheckBoxNrPhotons = new javax.swing.JCheckBox();
      minNrPhotonsTextField = new javax.swing.JTextField();
      jLabel16 = new javax.swing.JLabel();
      maxNrPhotonsTextField = new javax.swing.JTextField();
      showButton = new javax.swing.JButton();
      endTrackCheckBox_ = new javax.swing.JCheckBox();
      endTrackSpinner_ = new javax.swing.JSpinner();
      jLabel19 = new javax.swing.JLabel();
      readParmsButton_ = new javax.swing.JToggleButton();
      jLabel20 = new javax.swing.JLabel();
      fitMethodComboBox1 = new javax.swing.JComboBox();
      showOverlay_ = new javax.swing.JToggleButton();
      mTrackButton_ = new javax.swing.JButton();
      jLabel21 = new javax.swing.JLabel();
      zStepTextField_ = new javax.swing.JTextField();
      labelNPoints_ = new javax.swing.JLabel();
      jSeparator6 = new javax.swing.JSeparator();
      positionsLabel_ = new javax.swing.JLabel();
      allPosButton_ = new javax.swing.JButton();
      currentPosButton_ = new javax.swing.JButton();
      posTextField_ = new javax.swing.JTextField();

      jButton1.setText("jButton1");

      setBounds(new java.awt.Rectangle(0, 22, 250, 550));
      setMinimumSize(new java.awt.Dimension(250, 550));
      setResizable(false);
      addWindowListener(new java.awt.event.WindowAdapter() {
         public void windowClosing(java.awt.event.WindowEvent evt) {
            formWindowClosing(evt);
         }
         public void windowClosed(java.awt.event.WindowEvent evt) {
            formWindowClosed(evt);
         }
      });
      getContentPane().setLayout(null);

      jLabel1.setText("Fit Parameters...");
      getContentPane().add(jLabel1);
      jLabel1.setBounds(20, 250, 101, 16);

      jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel3.setText("Fitter");
      getContentPane().add(jLabel3);
      jLabel3.setBounds(50, 290, 26, 13);

      jLabel4.setText("Filter Data...");
      getContentPane().add(jLabel4);
      jLabel4.setBounds(20, 360, 87, 20);

      filterDataCheckBoxWidth.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            filterDataCheckBoxWidthActionPerformed(evt);
         }
      });
      getContentPane().add(filterDataCheckBoxWidth);
      filterDataCheckBoxWidth.setBounds(20, 380, 28, 20);
      getContentPane().add(jLabel5);
      jLabel5.setBounds(0, 0, 0, 0);

      jLabel6.setText("Imaging parameters...");
      getContentPane().add(jLabel6);
      jLabel6.setBounds(20, 10, 142, 16);

      photonConversionTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      photonConversionTextField.setText("10.41");
      getContentPane().add(photonConversionTextField);
      photonConversionTextField.setBounds(170, 50, 67, 20);

      emGainTextField_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      emGainTextField_.setText("50");
      getContentPane().add(emGainTextField_);
      emGainTextField_.setBounds(170, 70, 67, 19);

      baseLevelTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      baseLevelTextField.setText("100");
      baseLevelTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            baseLevelTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(baseLevelTextField);
      baseLevelTextField.setBounds(170, 150, 67, 20);

      jLabel7.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel7.setText("Photon Conversion factor");
      getContentPane().add(jLabel7);
      jLabel7.setBounds(40, 50, 123, 13);

      jLabel8.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel8.setText("Linear (EM) Gain");
      getContentPane().add(jLabel8);
      jLabel8.setBounds(40, 70, 78, 13);

      jLabel9.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel9.setText("Camera Offset (counts)");
      getContentPane().add(jLabel9);
      jLabel9.setBounds(40, 150, 122, 13);
      getContentPane().add(jSeparator4);
      jSeparator4.setBounds(496, 191, 1, 9);

      minSigmaTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      minSigmaTextField.setText("100");
      minSigmaTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            minSigmaTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(minSigmaTextField);
      minSigmaTextField.setBounds(50, 380, 40, 30);

      trackButton.setText("Track");
      trackButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            trackButtonActionPerformed(evt);
         }
      });
      getContentPane().add(trackButton);
      trackButton.setBounds(90, 530, 75, 29);
      getContentPane().add(jSeparator1);
      jSeparator1.setBounds(20, 240, 220, 10);
      getContentPane().add(jSeparator2);
      jSeparator2.setBounds(20, 170, 220, 10);
      getContentPane().add(jSeparator3);
      jSeparator3.setBounds(20, 350, 220, 10);

      noiseToleranceTextField_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      noiseToleranceTextField_.setText("2000");
      noiseToleranceTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            noiseToleranceTextField_ActionPerformed(evt);
         }
      });
      noiseToleranceTextField_.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            noiseToleranceTextField_FocusLost(evt);
         }
      });
      noiseToleranceTextField_.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
         public void propertyChange(java.beans.PropertyChangeEvent evt) {
            noiseToleranceTextField_PropertyChange(evt);
         }
      });
      noiseToleranceTextField_.addKeyListener(new java.awt.event.KeyAdapter() {
         public void keyTyped(java.awt.event.KeyEvent evt) {
            noiseToleranceTextField_KeyTyped(evt);
         }
      });
      getContentPane().add(noiseToleranceTextField_);
      noiseToleranceTextField_.setBounds(180, 220, 60, 20);

      pixelSizeTextField_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      pixelSizeTextField_.setText("0.8");
      pixelSizeTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pixelSizeTextField_ActionPerformed(evt);
         }
      });
      getContentPane().add(pixelSizeTextField_);
      pixelSizeTextField_.setBounds(170, 90, 67, 20);

      jLabel13.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel13.setText("PixelSize (nm)");
      getContentPane().add(jLabel13);
      jLabel13.setBounds(40, 90, 122, 13);

      fitAllButton_.setText("Fit");
      fitAllButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fitAllButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(fitAllButton_);
      fitAllButton_.setBounds(10, 530, 80, 30);

      jLabel10.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel10.setText("nm < Width <");
      getContentPane().add(jLabel10);
      jLabel10.setBounds(90, 390, 80, 10);
      getContentPane().add(jSeparator5);
      jSeparator5.setBounds(20, 470, 220, 10);

      jLabel11.setText("Find Maxima Settings...");
      getContentPane().add(jLabel11);
      jLabel11.setBounds(20, 180, 147, 16);

      jLabel12.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel12.setText("Pre-Filter");
      getContentPane().add(jLabel12);
      jLabel12.setBounds(90, 200, 60, 13);

      jLabel14.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel14.setText("Dimensions");
      getContentPane().add(jLabel14);
      jLabel14.setBounds(50, 270, 56, 13);

      preFilterComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      preFilterComboBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "None", "Gaussian1-5" }));
      preFilterComboBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            preFilterComboBox_ActionPerformed(evt);
         }
      });
      getContentPane().add(preFilterComboBox_);
      preFilterComboBox_.setBounds(150, 200, 90, 20);

      fitDimensionsComboBox1.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      fitDimensionsComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3" }));
      fitDimensionsComboBox1.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fitDimensionsComboBox1ActionPerformed(evt);
         }
      });
      getContentPane().add(fitDimensionsComboBox1);
      fitDimensionsComboBox1.setBounds(150, 260, 90, 27);

      timeIntervalTextField_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      timeIntervalTextField_.setText("0.8");
      getContentPane().add(timeIntervalTextField_);
      timeIntervalTextField_.setBounds(170, 110, 67, 20);

      jLabel15.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel15.setText("Time Interval (ms)");
      getContentPane().add(jLabel15);
      jLabel15.setBounds(40, 110, 122, 13);

      jLabel17.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel17.setText("Max Iterations");
      getContentPane().add(jLabel17);
      jLabel17.setBounds(50, 310, 90, 13);

      maxIterationsTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      maxIterationsTextField.setText("250");
      getContentPane().add(maxIterationsTextField);
      maxIterationsTextField.setBounds(170, 310, 70, 20);

      maxSigmaTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      maxSigmaTextField.setText("200");
      maxSigmaTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            maxSigmaTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(maxSigmaTextField);
      maxSigmaTextField.setBounds(160, 380, 50, 30);

      jLabel18.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel18.setText("nm");
      getContentPane().add(jLabel18);
      jLabel18.setBounds(210, 390, 20, 10);

      jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel2.setText("Box Size (pixels)");
      getContentPane().add(jLabel2);
      jLabel2.setBounds(50, 330, 90, 10);

      boxSizeTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      boxSizeTextField.setText("16");
      getContentPane().add(boxSizeTextField);
      boxSizeTextField.setBounds(170, 330, 70, 25);

      stopButton.setText("Stop");
      stopButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            stopButtonActionPerformed(evt);
         }
      });
      getContentPane().add(stopButton);
      stopButton.setBounds(130, 560, 80, 30);

      filterDataCheckBoxNrPhotons.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            filterDataCheckBoxNrPhotonsActionPerformed(evt);
         }
      });
      getContentPane().add(filterDataCheckBoxNrPhotons);
      filterDataCheckBoxNrPhotons.setBounds(20, 410, 28, 20);

      minNrPhotonsTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      minNrPhotonsTextField.setText("100");
      minNrPhotonsTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            minNrPhotonsTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(minNrPhotonsTextField);
      minNrPhotonsTextField.setBounds(50, 410, 50, 30);

      jLabel16.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel16.setText("< # photons <");
      getContentPane().add(jLabel16);
      jLabel16.setBounds(100, 420, 80, 10);

      maxNrPhotonsTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      maxNrPhotonsTextField.setText("200");
      maxNrPhotonsTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            maxNrPhotonsTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(maxNrPhotonsTextField);
      maxNrPhotonsTextField.setBounds(180, 410, 60, 30);

      showButton.setText("Data");
      showButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showButtonActionPerformed(evt);
         }
      });
      getContentPane().add(showButton);
      showButton.setBounds(40, 560, 80, 30);

      endTrackCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      endTrackCheckBox_.setText("End track when missing");
      endTrackCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            endTrackCheckBox_ActionPerformed(evt);
         }
      });
      getContentPane().add(endTrackCheckBox_);
      endTrackCheckBox_.setBounds(20, 440, 150, 23);

      endTrackSpinner_.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
      getContentPane().add(endTrackSpinner_);
      endTrackSpinner_.setBounds(160, 440, 50, 28);

      jLabel19.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel19.setText("frames");
      getContentPane().add(jLabel19);
      jLabel19.setBounds(210, 440, 40, 30);

      readParmsButton_.setText("read");
      readParmsButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            readParmsButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(readParmsButton_);
      readParmsButton_.setBounds(170, 20, 60, 20);

      jLabel20.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel20.setText("Noise tolerance");
      getContentPane().add(jLabel20);
      jLabel20.setBounds(90, 220, 76, 20);

      fitMethodComboBox1.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      fitMethodComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Simplex", "Levenberg-Marq", "Simplex-MLE", "Levenberg-Marq-Weighted" }));
      fitMethodComboBox1.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fitMethodComboBox1ActionPerformed(evt);
         }
      });
      getContentPane().add(fitMethodComboBox1);
      fitMethodComboBox1.setBounds(150, 280, 90, 27);

      showOverlay_.setText("show");
      showOverlay_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showOverlay_ActionPerformed(evt);
         }
      });
      getContentPane().add(showOverlay_);
      showOverlay_.setBounds(20, 200, 60, 20);

      mTrackButton_.setText("MTrack");
      mTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mTrackButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(mTrackButton_);
      mTrackButton_.setBounds(170, 530, 80, 29);

      jLabel21.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel21.setText("Z-step (nm)");
      getContentPane().add(jLabel21);
      jLabel21.setBounds(40, 130, 70, 13);

      zStepTextField_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      zStepTextField_.setText("50");
      getContentPane().add(zStepTextField_);
      zStepTextField_.setBounds(170, 130, 67, 20);

      labelNPoints_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      labelNPoints_.setText("n:");
      getContentPane().add(labelNPoints_);
      labelNPoints_.setBounds(20, 220, 70, 20);
      getContentPane().add(jSeparator6);
      jSeparator6.setBounds(20, 520, 220, 10);

      positionsLabel_.setText("Positions...");
      getContentPane().add(positionsLabel_);
      positionsLabel_.setBounds(20, 480, 80, 16);

      allPosButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      allPosButton_.setText("All");
      allPosButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            allPosButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(allPosButton_);
      allPosButton_.setBounds(30, 500, 60, 20);

      currentPosButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      currentPosButton_.setText("Current");
      currentPosButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            currentPosButton_ActionPerformed(evt);
         }
      });
      getContentPane().add(currentPosButton_);
      currentPosButton_.setBounds(100, 500, 70, 20);

      posTextField_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      posTextField_.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
      posTextField_.setText("1");
      getContentPane().add(posTextField_);
      posTextField_.setBounds(170, 500, 80, 20);

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void filterDataCheckBoxWidthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterDataCheckBoxWidthActionPerformed

    }//GEN-LAST:event_filterDataCheckBoxWidthActionPerformed

    private void noiseToleranceTextField_PropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_noiseToleranceTextField_PropertyChange
       
       
    }//GEN-LAST:event_noiseToleranceTextField_PropertyChange

    private void trackButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackButtonActionPerformed
       GaussianTrackThread tT = new GaussianTrackThread(IJ.getImage(), 
               FindLocalMaxima.FilterType.NONE);
       updateValues(tT);
       
       // Execute on another thread,
       // use tT.trackGaussians to run it on the same thread
       tT.init();
       System.out.println("started thread");
    }//GEN-LAST:event_trackButtonActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
       WINDOWOPEN = false;
    }//GEN-LAST:event_formWindowClosed

    private void fitAllButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fitAllButton_ActionPerformed
       
       if (ft_ == null || !ft_.isRunning()) {
          ft_ = new FitAllThread(shape_, fitMode_, preFilterType_, 
                  posTextField_.getText());
          updateValues(ft_);
          ft_.init();
       } else {
          JOptionPane.showMessageDialog(null, "Already running fitting analysis");
       }
    }//GEN-LAST:event_fitAllButton_ActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
       try {
       prefs_.put(NOISETOLERANCE, noiseToleranceTextField_.getText());
       prefs_.putDouble(PCF, NumberUtils.displayStringToDouble(photonConversionTextField.getText()));
       prefs_.putDouble(GAIN, NumberUtils.displayStringToDouble(emGainTextField_.getText()));
       prefs_.putDouble(PIXELSIZE, NumberUtils.displayStringToDouble(pixelSizeTextField_.getText()));      
       prefs_.putDouble(TIMEINTERVALMS, NumberUtils.displayStringToDouble(timeIntervalTextField_.getText()));
       prefs_.putDouble(ZSTEPSIZE, NumberUtils.displayStringToDouble(zStepTextField_.getText()));
       prefs_.putDouble(BACKGROUNDLEVEL, NumberUtils.displayStringToDouble(baseLevelTextField.getText()));
       prefs_.putBoolean(USEFILTER, filterDataCheckBoxWidth.isSelected());
       prefs_.putDouble(SIGMAMIN, NumberUtils.displayStringToDouble(minSigmaTextField.getText()));
       prefs_.putDouble(SIGMAMAX, NumberUtils.displayStringToDouble(maxSigmaTextField.getText()));
       prefs_.putBoolean(USENRPHOTONSFILTER, filterDataCheckBoxNrPhotons.isSelected());
       prefs_.putDouble(NRPHOTONSMIN, NumberUtils.displayStringToDouble(minNrPhotonsTextField.getText()));
       prefs_.putDouble(NRPHOTONSMAX, NumberUtils.displayStringToDouble(maxNrPhotonsTextField.getText()));
       prefs_.putInt(MAXITERATIONS, NumberUtils.displayStringToInt(maxIterationsTextField.getText()));
       prefs_.putInt(BOXSIZE, NumberUtils.displayStringToInt(boxSizeTextField.getText()));
       prefs_.putInt(PREFILTER, preFilterComboBox_.getSelectedIndex());
       prefs_.putInt(FRAMEXPOS, getX());
       prefs_.putInt(FRAMEYPOS, getY());
       prefs_.putInt(FRAMEWIDTH, getWidth());
       prefs_.putInt(FRAMEHEIGHT, this.getHeight());
       prefs_.putBoolean(ENDTRACKBOOL, endTrackCheckBox_.isSelected() );
       prefs_.putInt(ENDTRACKINT, (Integer) endTrackSpinner_.getValue() );
       prefs_.putInt(FITMODE, fitMethodComboBox1.getSelectedIndex());
       } catch (ParseException ex) {
          ReportingUtils.logError(ex, "Error while closing Localization Microscopy plugin");
       }
       
       WINDOWOPEN = false;
       
       this.setVisible(false);
    }//GEN-LAST:event_formWindowClosing

    public void formWindowOpened() {
       WINDOWOPEN = true;
    }
    
   @Override
    public void dispose() {
       formWindowClosing(null);
    }

    private void preFilterComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preFilterComboBox_ActionPerformed
       String item = (String) preFilterComboBox_.getSelectedItem();
       if (item.equals("None"))
          preFilterType_ = FindLocalMaxima.FilterType.NONE;
       if (item.equals("Gaussian1-5"))
          preFilterType_ = FindLocalMaxima.FilterType.GAUSSIAN1_5;
       if (showOverlay_.isSelected())
         showNoiseTolerance();
    }//GEN-LAST:event_preFilterComboBox_ActionPerformed

    private void fitDimensionsComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fitDimensionsComboBox1ActionPerformed
       shape_ = fitDimensionsComboBox1.getSelectedIndex() + 1;
    }//GEN-LAST:event_fitDimensionsComboBox1ActionPerformed

    private void baseLevelTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_baseLevelTextFieldActionPerformed
    }//GEN-LAST:event_baseLevelTextFieldActionPerformed

    private void maxSigmaTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_maxSigmaTextFieldActionPerformed
       if (Double.parseDouble(maxSigmaTextField.getText()) <=
               Double.parseDouble(minSigmaTextField.getText() ))
          minSigmaTextField.setText( Double.toString
                  (Double.parseDouble(maxSigmaTextField.getText()) - 1));
    }//GEN-LAST:event_maxSigmaTextFieldActionPerformed

    private void minSigmaTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minSigmaTextFieldActionPerformed
       if (Double.parseDouble(minSigmaTextField.getText()) >=
               Double.parseDouble(maxSigmaTextField.getText() ))
          maxSigmaTextField.setText( Double.toString
                  (Double.parseDouble(minSigmaTextField.getText()) + 1));
    }//GEN-LAST:event_minSigmaTextFieldActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
       if (ft_ != null && ft_.isRunning())
          ft_.stop();
       aStop_.set(true);   
    }//GEN-LAST:event_stopButtonActionPerformed

    private void filterDataCheckBoxNrPhotonsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterDataCheckBoxNrPhotonsActionPerformed
       // TODO add your handling code here:
    }//GEN-LAST:event_filterDataCheckBoxNrPhotonsActionPerformed

    private void minNrPhotonsTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minNrPhotonsTextFieldActionPerformed
        if (Double.parseDouble(minNrPhotonsTextField.getText()) >=
               Double.parseDouble(maxNrPhotonsTextField.getText() ))
          minNrPhotonsTextField.setText( Double.toString
                  (Double.parseDouble(maxNrPhotonsTextField.getText()) - 1));
    }//GEN-LAST:event_minNrPhotonsTextFieldActionPerformed

    private void maxNrPhotonsTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_maxNrPhotonsTextFieldActionPerformed
        if (Double.parseDouble(maxNrPhotonsTextField.getText()) <=
           Double.parseDouble(minNrPhotonsTextField.getText() ))
        maxNrPhotonsTextField.setText( Double.toString
           (Double.parseDouble(minNrPhotonsTextField.getText()) + 1));
    }//GEN-LAST:event_maxNrPhotonsTextFieldActionPerformed

    private void showButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showButtonActionPerformed
       DataCollectionForm dcForm = DataCollectionForm.getInstance();
       dcForm.setVisible(true);
    }//GEN-LAST:event_showButtonActionPerformed

   private void endTrackCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_endTrackCheckBox_ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_endTrackCheckBox_ActionPerformed

   private void noiseToleranceTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noiseToleranceTextField_ActionPerformed
      //if (showOverlay_.isSelected())
      //   showNoiseTolerance();
   }//GEN-LAST:event_noiseToleranceTextField_ActionPerformed

   private void showNoiseTolerance() {
       ImagePlus siPlus;
       try {
          siPlus = IJ.getImage();
       } catch (Exception e) {
          return;
       }
       if (ip_ != siPlus)
          ip_ = siPlus;

       // Roi originalRoi = siPlus.getRoi();
       // Find maximum in Roi, might not be needed....
      try {
         int val = Integer.parseInt(noiseToleranceTextField_.getText());
         int halfSize = Integer.parseInt(boxSizeTextField.getText()) / 2;
         Polygon pol = FindLocalMaxima.FindMax(siPlus, 2* halfSize, val, preFilterType_);
         // pol = FindLocalMaxima.noiseFilter(siPlus.getProcessor(), pol, val);
         Overlay ov = new Overlay();
         for (int i = 0; i < pol.npoints; i++) {
            int x = pol.xpoints[i];
            int y = pol.ypoints[i];
            ov.add(new Roi(x - halfSize, y - halfSize, 2 * halfSize, 2 * halfSize));
         }
         labelNPoints_.setText("n: " + pol.npoints);
         siPlus.setOverlay(ov);
         siPlus.setHideOverlay(false);
      } catch (NumberFormatException nfEx) {
         // nothing to do
      }
   }

   private void noiseToleranceTextField_FocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_noiseToleranceTextField_FocusLost
   }//GEN-LAST:event_noiseToleranceTextField_FocusLost

   private void readParmsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_readParmsButton_ActionPerformed
      // should not have made this a push button...
      readParmsButton_.setSelected(false);
      // take the active ImageJ image
      ImagePlus siPlus;
      try {
         siPlus = IJ.getImage();
      } catch (Exception ex) {
         return;
      }
      if (ip_ != siPlus) {
         ip_ = siPlus;
      }
      try {
         Class<?> mmWin = Class.forName("org.micromanager.api.MMWindow");
         Constructor[] aCTors = mmWin.getDeclaredConstructors();
         aCTors[0].setAccessible(true);
         Object mw = aCTors[0].newInstance(siPlus);
         Method[] allMethods = mmWin.getDeclaredMethods();
         
         // assemble all methods we need
         Method mIsMMWindow = null;
         Method mGetSummaryMetaData = null;
         Method mGetImageMetaData = null;
         for (Method m : allMethods) {
            String mname = m.getName();
            if (mname.startsWith("isMMWindow")
                    && m.getGenericReturnType() == boolean.class) {
               mIsMMWindow = m;
               mIsMMWindow.setAccessible(true);
            }
            if (mname.startsWith("getSummaryMetaData")
                    && m.getGenericReturnType() == JSONObject.class) {
               mGetSummaryMetaData = m;
               mGetSummaryMetaData.setAccessible(true);
            }
            if (mname.startsWith("getImageMetadata")
                    && m.getGenericReturnType() == JSONObject.class) {
               mGetImageMetaData = m;
               mGetImageMetaData.setAccessible(true);
            }

         }

         if (mIsMMWindow != null && (Boolean) mIsMMWindow.invoke(mw)) {
            JSONObject summary = null;
            int lastFrame = 0;
            if (mGetSummaryMetaData != null) {
               summary = (JSONObject) mGetSummaryMetaData.invoke(mw);
               try {
                  lastFrame = summary.getInt("Frames");
               } catch (JSONException ex) {
               }               
            }
            JSONObject im = null;
            JSONObject imLast = null;
            if (mGetImageMetaData != null) {
               im = (JSONObject) mGetImageMetaData.invoke(mw, 0, 0, 0, 0);
               if (lastFrame > 0)
                  imLast = (JSONObject) 
                          mGetImageMetaData.invoke(mw, 0, 0, lastFrame - 1, 0);              
            }
                                  
            if (summary != null && im != null) {

               // it may be better to read the timestamp of the first and last frame and deduce interval from there
               if (summary.has("Interval_ms")) {
                  try {
                     timeIntervalTextField_.setText(NumberUtils.doubleToDisplayString(summary.getDouble("Interval_ms")));
                     timeIntervalTextField_.setBackground(Color.lightGray);
                  } catch (JSONException jex) {
                     // nothing to do
                  }
               }
               if (summary.has("PixelSize_um")) {
                  try {
                     pixelSizeTextField_.setText(NumberUtils.doubleToDisplayString(summary.getDouble("PixelSize_um") * 1000.0));
                     pixelSizeTextField_.setBackground(Color.lightGray);
                  } catch (JSONException jex) {
                     System.out.println("Error");
                  }

               }
               double emGain = -1.0;
               boolean conventionalGain = false;


               // find amplifier for Andor camera
               try {
                  String camera = im.getString("Core-Camera");
                  if (im.getString(camera + "-Output_Amplifier").equals("Conventional")) {
                     conventionalGain = true;
                  }
                  // TODO: find amplifier for other cameras

                  // find gain for Andor:
                  try {
                     emGain = im.getDouble(camera + "-Gain");
                  } catch (JSONException jex) {
                     try {
                        emGain = im.getDouble(camera + "-EMGain");
                     } catch (JSONException jex2) {
                        // key not found, nothing to do
                     }
                  }

               } catch (JSONException ex) {
                  // tag not found...
               }


               if (conventionalGain) {
                  emGain = 1;
               }
               if (emGain > 0) {
                  emGainTextField_.setText(NumberUtils.doubleToDisplayString(emGain));
                  emGainTextField_.setBackground(Color.lightGray);
               }

               // Get time stamp from first and last frame
               try {
                  double firstTimeMs = im.getDouble("ElapsedTime-ms");
                  double lastTimeMs = imLast.getDouble("ElapsedTime-ms");
                  double intervalMs = (lastTimeMs - firstTimeMs) / lastFrame;
                  timeIntervalTextField_.setText(
                          NumberUtils.doubleToDisplayString(intervalMs));
                  timeIntervalTextField_.setBackground(Color.lightGray);
               } catch (JSONException jex) {
               }

            }
         }

      } catch (ClassNotFoundException ex) {
      } catch (InstantiationException ex) {
         //Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IllegalAccessException ex) {
         //Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IllegalArgumentException ex) {
         //Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvocationTargetException ex) {
         //Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, ex);
      }

   }//GEN-LAST:event_readParmsButton_ActionPerformed

   private void noiseToleranceTextField_KeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_noiseToleranceTextField_KeyTyped
   }//GEN-LAST:event_noiseToleranceTextField_KeyTyped

   private void fitMethodComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fitMethodComboBox1ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_fitMethodComboBox1ActionPerformed

   private void showOverlay_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showOverlay_ActionPerformed
      if (showOverlay_.isSelected()) {
         showNoiseTolerance();
         showOverlay_.setText("hide");
      } else {
         ImagePlus siPlus;
         try {
            siPlus = IJ.getImage();
         } catch (Exception e) {
            return;
         }
         siPlus.setHideOverlay(true);
         showOverlay_.setText("show");
      }
   }//GEN-LAST:event_showOverlay_ActionPerformed

   private void pixelSizeTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pixelSizeTextField_ActionPerformed
      // delete
   }//GEN-LAST:event_pixelSizeTextField_ActionPerformed

   private void mTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mTrackButton_ActionPerformed

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
            aStop_ .set(false);
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
                  Polygon pol = FindLocalMaxima.FindMax(siPlus, halfSize, val, preFilterType_);
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
               Polygon pol = FindLocalMaxima.FindMax(siPlus, halfSize, val, preFilterType_);
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

   }//GEN-LAST:event_mTrackButton_ActionPerformed

   private void allPosButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allPosButton_ActionPerformed
      ImagePlus siPlus;
      try {
          siPlus = IJ.getImage();
      } catch (Exception e) {
          return;
      }
      int nrPos = MMWindowAbstraction.getNumberOfPositions(siPlus);
      if (nrPos > 1) {
         posTextField_.setText("1-" + nrPos);
      } else {
         posTextField_.setText("1");
      }
      

   }//GEN-LAST:event_allPosButton_ActionPerformed

   private void currentPosButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_currentPosButton_ActionPerformed
      ImagePlus siPlus;
      try {
          siPlus = IJ.getImage();
      } catch (Exception e) {
          return;
      }
      int pos = MMWindowAbstraction.getPosition(siPlus);
      posTextField_.setText("" + pos);
   }//GEN-LAST:event_currentPosButton_ActionPerformed

   public void updateValues(GaussianInfo tT) {
      try {
         tT.setNoiseTolerance(Integer.parseInt(noiseToleranceTextField_.getText()));
         tT.setPhotonConversionFactor(NumberUtils.displayStringToDouble(photonConversionTextField.getText()));
         tT.setGain(NumberUtils.displayStringToDouble(emGainTextField_.getText()));
         tT.setPixelSize((float) NumberUtils.displayStringToDouble(pixelSizeTextField_.getText()));
         tT.setZStackStepSize((float) NumberUtils.displayStringToDouble(zStepTextField_.getText()));
         tT.setTimeIntervalMs(NumberUtils.displayStringToDouble(timeIntervalTextField_.getText()));
         tT.setBaseLevel(NumberUtils.displayStringToDouble(baseLevelTextField.getText()));
         tT.setUseWidthFilter(filterDataCheckBoxWidth.isSelected());
         tT.setSigmaMin(NumberUtils.displayStringToDouble(minSigmaTextField.getText()));
         tT.setSigmaMax(NumberUtils.displayStringToDouble(maxSigmaTextField.getText()));
         tT.setUseNrPhotonsFilter(filterDataCheckBoxNrPhotons.isSelected());
         tT.setNrPhotonsMin(NumberUtils.displayStringToDouble(minNrPhotonsTextField.getText()));
         tT.setNrPhotonsMax(NumberUtils.displayStringToDouble(maxNrPhotonsTextField.getText()));
         tT.setMaxIterations(Integer.parseInt(maxIterationsTextField.getText()));
         tT.setBoxSize(Integer.parseInt(boxSizeTextField.getText()));
         tT.setShape(fitDimensionsComboBox1.getSelectedIndex() + 1);
         tT.setFitMode(fitMethodComboBox1.getSelectedIndex() + 1);
         tT.setEndTrackBool(endTrackCheckBox_.isSelected());
         tT.setEndTrackAfterNFrames((Integer) endTrackSpinner_.getValue());
      } catch (NumberFormatException ex) {
         JOptionPane.showMessageDialog(null, "Error interpreting input: " + ex.getMessage());
      } catch (ParseException ex) {
         JOptionPane.showMessageDialog(null, "Error interpreting input: " + ex.getMessage());
      }
   }
   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton allPosButton_;
   private javax.swing.JTextField baseLevelTextField;
   private javax.swing.JTextField boxSizeTextField;
   private javax.swing.JButton currentPosButton_;
   private javax.swing.JTextField emGainTextField_;
   private javax.swing.JCheckBox endTrackCheckBox_;
   private javax.swing.JSpinner endTrackSpinner_;
   private javax.swing.JCheckBox filterDataCheckBoxNrPhotons;
   private javax.swing.JCheckBox filterDataCheckBoxWidth;
   private javax.swing.JButton fitAllButton_;
   private javax.swing.JComboBox fitDimensionsComboBox1;
   private javax.swing.JComboBox fitMethodComboBox1;
   private javax.swing.JButton jButton1;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel10;
   private javax.swing.JLabel jLabel11;
   private javax.swing.JLabel jLabel12;
   private javax.swing.JLabel jLabel13;
   private javax.swing.JLabel jLabel14;
   private javax.swing.JLabel jLabel15;
   private javax.swing.JLabel jLabel16;
   private javax.swing.JLabel jLabel17;
   private javax.swing.JLabel jLabel18;
   private javax.swing.JLabel jLabel19;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel20;
   private javax.swing.JLabel jLabel21;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel6;
   private javax.swing.JLabel jLabel7;
   private javax.swing.JLabel jLabel8;
   private javax.swing.JLabel jLabel9;
   private javax.swing.JSeparator jSeparator1;
   private javax.swing.JSeparator jSeparator2;
   private javax.swing.JSeparator jSeparator3;
   private javax.swing.JSeparator jSeparator4;
   private javax.swing.JSeparator jSeparator5;
   private javax.swing.JSeparator jSeparator6;
   private javax.swing.JLabel labelNPoints_;
   private javax.swing.JButton mTrackButton_;
   private javax.swing.JTextField maxIterationsTextField;
   private javax.swing.JTextField maxNrPhotonsTextField;
   private javax.swing.JTextField maxSigmaTextField;
   private javax.swing.JTextField minNrPhotonsTextField;
   private javax.swing.JTextField minSigmaTextField;
   private javax.swing.JTextField noiseToleranceTextField_;
   private javax.swing.JTextField photonConversionTextField;
   private javax.swing.JTextField pixelSizeTextField_;
   private javax.swing.JTextField posTextField_;
   private javax.swing.JLabel positionsLabel_;
   private javax.swing.JComboBox preFilterComboBox_;
   private javax.swing.JToggleButton readParmsButton_;
   private javax.swing.JButton showButton;
   private javax.swing.JToggleButton showOverlay_;
   private javax.swing.JButton stopButton;
   private javax.swing.JTextField timeIntervalTextField_;
   private javax.swing.JButton trackButton;
   private javax.swing.JTextField zStepTextField_;
   // End of variables declaration//GEN-END:variables

   @Override
   public void imageOpened(ImagePlus ip) {
      imageUpdated(ip);
   }

   @Override
   public void imageClosed(ImagePlus ip) {
         //   System.out.println("Closed");
   }

   @Override
   public void imageUpdated(ImagePlus ip) {
      if (!WINDOWOPEN) {
         return;
      }
      if (ip != ip_) {
         pixelSizeTextField_.setBackground(Color.white);
         emGainTextField_.setBackground(Color.white);      
         timeIntervalTextField_.setBackground(Color.white);
    
         if (ip_ != null) {
            ip_.setOverlay(null);
            ip_.setHideOverlay(true);
         }
         ip_ = ip;
      }
         
      if (showOverlay_.isSelected()) {
         
         // note that there is confusion about frames versus slices
         int frame = 1;
         if (ip.getNFrames() > 1)
            frame = ip.getFrame();
         else if (ip.getNSlices() > 1)
            frame = ip.getSlice();
         
         if (lastFrame_ != frame) {
            lastFrame_ = frame;
            showNoiseTolerance();
         }
      }
   }

}
