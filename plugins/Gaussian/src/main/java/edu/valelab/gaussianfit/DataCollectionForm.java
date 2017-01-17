/**
 * DataCollectionForm.java
 * 
 * This form hold datasets containing results of gaussian fitting
 * Two types of data sets exists: tracks and "global" spotData
 * 
 * Data structure used internally is contained in "MyRowData".
 * Data are currently stored in RAM, but a caching mechanism could be implemented
 * 
 * The form acts as a "workbench".  Various actions, (such as display, color correction
 * jitter correction) are available, some of which may generate new datasets
 * that are stored in this form
 * 
 *
 * Created on Nov 20, 2010, 8:52:50 AM
 * 
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


package edu.valelab.gaussianfit;

import com.google.common.eventbus.Subscribe;
import edu.valelab.gaussianfit.algorithm.FFTUtils;
import edu.valelab.gaussianfit.datasetdisplay.ImageRenderer;
import edu.valelab.gaussianfit.datasettransformations.SpotDataFilter;
import edu.valelab.gaussianfit.datasettransformations.CoordinateMapper;
import edu.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.valelab.gaussianfit.utils.DisplayUtils;
import edu.valelab.gaussianfit.data.SpotData;
import edu.valelab.gaussianfit.utils.GaussianUtils;
import edu.valelab.gaussianfit.fitting.ZCalibrator;
import edu.valelab.gaussianfit.data.LoadAndSave;
import edu.valelab.gaussianfit.spotoperations.SpotLinker;
import edu.valelab.gaussianfit.data.RowData;
import edu.valelab.gaussianfit.datasetdisplay.ParticlePairLister;
import edu.valelab.gaussianfit.datasettransformations.DriftCorrector;
import edu.valelab.gaussianfit.datasettransformations.PairFilter;
import edu.valelab.gaussianfit.datasettransformations.TrackOperator;
import edu.valelab.gaussianfit.internal.tabledisplay.DataTable;
import edu.valelab.gaussianfit.internal.tabledisplay.DataTableModel;
import edu.valelab.gaussianfit.internal.tabledisplay.DataTableRowSorter;
import edu.valelab.gaussianfit.utils.ListUtils;
import edu.valelab.gaussianfit.utils.ReportingUtils;
import edu.valelab.gaussianfit.utils.NumberUtils;
import edu.valelab.gaussianfit.utils.FileDialogs;
import edu.valelab.gaussianfit.utils.FileDialogs.FileType;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.stat.StatUtils;
import org.jfree.data.xy.XYSeries;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;


/**
 *
 * @author Nico Stuurman
 */
public class DataCollectionForm extends JFrame {
   DataTableModel mainTableModel_;
   private final String[] plotModes_ = {"t-X", "t-Y", "t-dist", "t-Int", "X-Y"};
   private final String[] renderModes_ = {"Points", "Gaussian", "Norm. Gaussian"};
   private final String[] renderSizes_  = {"1x", "2x", "4x", "8x", "16x", "32x", "64x", "128x"};
   public final static String EXTENSION = ".tsf";
   
   // Pref-keys
   private static final String FRAMEXPOS = "DCXPos";
   private static final String FRAMEYPOS = "DCYPos";
   private static final String FRAMEWIDTH = "DCWidth";
   private static final String FRAMEHEIGHT = "DCHeight";
   private static final String USESIGMA = "DCSigma";
   private static final String SIGMAMIN = "DCSigmaMin";
   private static final String SIGMAMAX = "DCSigmaMax";
   private static final String USEINT = "DCIntensity";
   private static final String INTMIN = "DCIntMin";
   private static final String INTMAX = "DCIntMax";
   private static final String LOADTSFDIR = "TSFDir";
   private static final String RENDERMAG = "VisualizationMagnification";
   private static final String PAIRSMAXDISTANCE = "PairsMaxDistance";
   private static final String METHOD2C = "MethodFor2CCorrection";
   private static final String COL0WIDTH = "Col0Width";  
   private static final String COL1WIDTH = "Col1Width";
   private static final String COL2WIDTH = "Col2Width";
   private static final String COL3WIDTH = "Col3Width";
   private static final String COL4WIDTH = "Col4Width";
   private static final String COL5WIDTH = "Col5Width";
   private static final String COL6WIDTH = "Col6Width";
   
   private static final int OK = 0;
   private static final int FAILEDDONOTINFORM = 1;
   private static final int FAILEDDOINFORM = 2;
   
   private static final FileType TSF_FILE = new FileType("TSF File",
           "Tagged Spot Format file",
           "./data.tsf",
           false, new String[]{"txt", "tsf"});
 
   private static CoordinateMapper c2t_;
   private static String loadTSFDir_ = "";   
   private int jitterMethod_ = 1;
   private int jitterMaxSpots_ = 40000; 
   private int jitterMaxFrames_ = 500; 
   private String dir_ = "";   
   public static ZCalibrator zc_ = new ZCalibrator();
   private static Studio studio_;
   
   // GUI elements
   private JLabel IntLabel2;
   private JLabel SigmaLabel2;
   private JLabel SigmaLabel3;
   private JButton SubRange;
   private JButton averageTrackButton_;
   private JButton c2CorrectButton;
   private JButton c2StandardButton;
   private JButton centerTrackButton_;
   private JButton combineButton_;
   private JCheckBox filterIntensityCheckBox_;
   private JCheckBox filterSigmaCheckBox_;
   private JButton infoButton_;
   private JTextField intensityMax_;
   private JTextField intensityMin_;
   private JLabel jLabel1;
   private JLabel jLabel2;
   private JLabel jLabel3;
   private JLabel jLabel4;
   private JLabel jLabel5;
   private JLabel jLabel6;
   private JLabel jLabel7;
   private JPanel jPanel1;
   private JPanel jPanel2;
   private JScrollPane jScrollPane1_;
   private JSeparator jSeparator2;
   private JSeparator jSeparator3;
   private JSeparator jSeparator4;
   private DataTable mainTable_;
   private JButton linkButton_;
   private JButton listButton_1;
   private JButton loadButton;
   private JCheckBox logLogCheckBox_;
   private JButton mathButton_;
   private JComboBox method2CBox_;
   private JTextField pairsMaxDistanceField_;
   private JButton plotButton_;
   private JComboBox plotComboBox_;
   private JCheckBox powerSpectrumCheckBox_;
   private JLabel referenceName_;
   private JButton removeButton;
   private JButton renderButton_;
   private JButton saveButton;
   private JComboBox saveFormatBox_;
   private JButton showButton_;
   private JButton extractTracksButton_;
   private JTextField sigmaMax_;
   private JTextField sigmaMin_;
   private JButton straightenTrackButton_;
   private JButton unjitterButton_;
   private JComboBox visualizationMagnification_;
   private JComboBox visualizationModel_;
   private JButton zCalibrateButton_;
   private JLabel zCalibrationLabel_;
      
   
   /**
    * Method to allow scripts to tune the jitter corrector
    * @param jm 
    */
   public void setJitterMethod(int jm) {
      if (jm == 0 || jm == 1)
         jitterMethod_ = jm;
   }
   /**
    * Method to allow scripts to tune the jitter corrector
    * Sets the maximum number of frames that will be used to produce one 
    * "time-point" in the de-jitter process.  Defaults to 500, set higher if you 
    * have few data-points, lower if you have many spots per frame.
    * @param jm - max number of frames that will be used per de-jitter cycle
    */
   public void setJitterMaxFrames(int jm) {
         jitterMaxFrames_ = jm;
   }
   /**
    * Method to allow scripts to tune the jitter corrector
    * Sets the maximum number of spots that will be used to produce one 
    * "timepoint" in the de-jitter process.  Defaults to 40000.  
    * @param jm 
    */
   public void setJitterMaxSpots(int jm) {
         jitterMaxSpots_ = jm;
   }
  
   /**
    * Method that lets a script gets the Affinetransform calculated by the
    * CoordinateMapper
    * @return  Affine transform object calculated by the Coordinate Mapper
    */
   public AffineTransform getAffineTransform() {
      if (c2t_ == null)
         return null;
      return c2t_.getAffineTransform();
   }
  
   
   private static DataCollectionForm instance_ = null;
    
   //private ArrayList<RowData> rowData_;
   public enum Coordinates {NM, PIXELS};
   public enum PlotMode {X, Y, INT};
   
   
    /**
    * Get spotdata for the given row
    * @param rowNr rowNr (unsorted) for which to return the spotdata
    * @return 
    */
   public RowData getSpotData(int rowNr) {
      return mainTableModel_.getRow(rowNr);
   }
   
   public int getNumberOfSpotData() {
      return mainTableModel_.getRowCount();
   }
   

   /**
    * Implement this class as a singleton
    *
    * @return the form
    */
   public static DataCollectionForm getInstance() {
      if (instance_ == null) {
         instance_ =  new DataCollectionForm(MMStudio.getInstance());
         studio_.events().registerForEvents(instance_);
      }
      return instance_;
   }

   /** 
    * Creates new form DataCollectionForm
    */
   private DataCollectionForm(Studio studio) {

      studio_ = studio;
      
      //rowData_ = new ArrayList<RowData>();

      mainTableModel_ = new DataTableModel();
      
      initComponents();
      referenceName_.setText("  ");
      plotComboBox_.setModel(new DefaultComboBoxModel(plotModes_));
      visualizationModel_.setModel(new DefaultComboBoxModel(renderModes_));
      visualizationMagnification_.setModel(new DefaultComboBoxModel(renderSizes_));
      jScrollPane1_.setName("Gaussian Spot Fitting Data Sets");      
              
      UserProfile up = studio_.getUserProfile();
      Class oc = DataCollectionForm.class;
      int x = up.getInt(oc, FRAMEXPOS, 50);
      int y = up.getInt(oc, FRAMEYPOS, 100);
      int width = up.getInt(oc, FRAMEWIDTH, 800);
      int height = up.getInt(oc, FRAMEHEIGHT, 250);
      super.setBounds(x, y, width, height);
      filterSigmaCheckBox_.setSelected(up.getBoolean(oc, USESIGMA, false));
      sigmaMin_.setText(up.getString(oc, SIGMAMIN, "0.0"));
      sigmaMax_.setText(up.getString(oc, SIGMAMAX, "20.0"));
      filterIntensityCheckBox_.setSelected(up.getBoolean(oc, USEINT, false));
      intensityMin_.setText(up.getString(oc, INTMIN, "0.0"));
      intensityMax_.setText(up.getString(oc, INTMAX, "20000"));
      loadTSFDir_ = up.getString(oc, LOADTSFDIR, "");
      visualizationMagnification_.setSelectedIndex(up.getInt(oc, RENDERMAG, 0));
      pairsMaxDistanceField_.setText(up.getString(oc, PAIRSMAXDISTANCE, "500"));
      method2CBox_.setSelectedItem(up.getString(oc, METHOD2C, "LWM"));
      
      mainTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TableColumnModel cm = mainTable_.getColumnModel();
      cm.getColumn(0).setPreferredWidth(up.getInt(oc, COL0WIDTH, 25));
      cm.getColumn(1).setPreferredWidth(up.getInt(oc, COL1WIDTH, 300));
      cm.getColumn(2).setPreferredWidth(up.getInt(oc, COL2WIDTH, 150));
      cm.getColumn(3).setPreferredWidth(up.getInt(oc, COL3WIDTH, 75));
      cm.getColumn(4).setPreferredWidth(up.getInt(oc, COL4WIDTH, 75));
      cm.getColumn(5).setPreferredWidth(up.getInt(oc, COL5WIDTH, 75));
      cm.getColumn(6).setPreferredWidth(up.getInt(oc, COL6WIDTH, 75));
      
      DataTableRowSorter sorter = 
              new DataTableRowSorter(mainTableModel_);
      //mainTable_.setAutoCreateRowSorter(true);
      mainTable_.setRowSorter(sorter);
       
      // Drag and Drop support for file loading
      super.setTransferHandler(new TransferHandler() {

         @Override
         public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
         }

         @Override
         public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) {
               return false;
            }

            Transferable t = support.getTransferable();
            try {
               @SuppressWarnings("unchecked")
               java.util.List<File> l =
                       (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
               loadFiles((File[]) l.toArray());

            } catch (UnsupportedFlavorException e) {
               return false;
            } catch (IOException e) {
               return false;
            }

            return true;
         }
      });

      super.setVisible(true);
   }
   
   @Subscribe
   public void closeRequested( ShutdownCommencingEvent sce){
      formWindowClosing(null);
   }


   /**
    * Adds a spot data set to the form
    *
    *
    * @param name
    * @param title
    * @param dw
    * @param colCorrRef
    * @param width
    * @param height
    * @param pixelSizeUm
    * @param zStackStepSizeNm
    * @param shape
    * @param halfSize
    * @param nrChannels
    * @param nrFrames
    * @param nrSlices
    * @param nrPositions
    * @param maxNrSpots
    * @param spotList
    * @param timePoints
    * @param isTrack
    * @param coordinate
    * @param hasZ
    * @param minZ
    * @param maxZ
    */
   public void addSpotData(
           String name,
           String title,
           DisplayWindow dw, 
           String colCorrRef,
           int width,
           int height,
           float pixelSizeUm, 
           float zStackStepSizeNm,
           int shape,
           int halfSize,
           int nrChannels,
           int nrFrames,
           int nrSlices,
           int nrPositions,
           int maxNrSpots, 
           List<SpotData> spotList,
           ArrayList<Double> timePoints,
           boolean isTrack, 
           Coordinates coordinate, 
           boolean hasZ, 
           double minZ, 
           double maxZ) {
      RowData newRow = new RowData(name, title, dw, colCorrRef, width, height, 
              pixelSizeUm, zStackStepSizeNm, 
              shape, halfSize, nrChannels, nrFrames, nrSlices, nrPositions, 
              maxNrSpots, spotList, timePoints, isTrack, coordinate, 
              hasZ, minZ, maxZ);
      addSpotData (newRow);
   }
   
   public void fireRowAdded() {
      if (mainTable_.getRowSorter() != null) {
         mainTable_.getRowSorter().allRowsChanged();
      } else {
         mainTableModel_.fireRowInserted();
      }
   }
      
      
   
   public void addSpotData(RowData newRow) {
      mainTableModel_.addRowData(newRow);
      fireRowAdded();
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            formComponentResized(null);
         }
      } );
   }

   /**
    * Return a dataset
    * @param ID with requested ID.
    * @return RowData with selected ID, or null if not found
    */
   public RowData getDataByID(int ID) {
      return mainTableModel_.getDataByID(ID);
   }

   /**
    * This method is called from within the constructor to
    * initialize the form.
    */
   @SuppressWarnings("unchecked")
   private void initComponents() {

      jPanel1 = new JPanel();
      jLabel7 = new JLabel();
      intensityMax_ = new JTextField();
      IntLabel2 = new JLabel();
      SigmaLabel3 = new JLabel();
      sigmaMax_ = new JTextField();
      visualizationMagnification_ = new JComboBox();
      visualizationModel_ = new JComboBox();
      sigmaMin_ = new JTextField();
      intensityMin_ = new JTextField();
      filterIntensityCheckBox_ = new JCheckBox();
      filterSigmaCheckBox_ = new JCheckBox();
      jLabel1 = new JLabel();
      renderButton_ = new JButton();
      jLabel4 = new JLabel();
      zCalibrateButton_ = new JButton();
      zCalibrationLabel_ = new JLabel();
      unjitterButton_ = new JButton();
      linkButton_ = new JButton();
      jSeparator4 = new JSeparator();
      centerTrackButton_ = new JButton();
      straightenTrackButton_ = new JButton();
      logLogCheckBox_ = new JCheckBox();
      plotComboBox_ = new JComboBox();
      SubRange = new JButton();
      mathButton_ = new JButton();
      averageTrackButton_ = new JButton();
      powerSpectrumCheckBox_ = new JCheckBox();
      plotButton_ = new JButton();
      jLabel6 = new JLabel();
      jSeparator2 = new JSeparator();
      SigmaLabel2 = new JLabel();
      pairsMaxDistanceField_ = new JTextField();
      referenceName_ = new JLabel();
      c2CorrectButton = new JButton();
      method2CBox_ = new JComboBox();
      c2StandardButton = new JButton();
      jLabel5 = new JLabel();
      jSeparator3 = new JSeparator();
      infoButton_ = new JButton();
      removeButton = new JButton();
      saveFormatBox_ = new JComboBox();
      saveButton = new JButton();
      loadButton = new JButton();
      showButton_ = new JButton();
      extractTracksButton_ = new JButton();
      jLabel2 = new JLabel();
      jLabel3 = new JLabel();
      combineButton_ = new JButton();
      listButton_1 = new JButton();
      jPanel2 = new JPanel();
      jScrollPane1_ = new JScrollPane();
      mainTable_ = new DataTable();

      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      setTitle("Gaussian tracking data");
      setMinimumSize(new java.awt.Dimension(450, 80));
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            formWindowClosing(evt);
         }
      });
      addComponentListener(new java.awt.event.ComponentAdapter() {
         @Override
         public void componentResized(java.awt.event.ComponentEvent evt) {
            formComponentResized(evt);
         }
      });

      jLabel7.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel7.setText("General");

      intensityMax_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      intensityMax_.setText("0");

      IntLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      IntLabel2.setText("#");

      SigmaLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      SigmaLabel3.setText("nm");

      sigmaMax_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      sigmaMax_.setText("0");

      visualizationMagnification_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      visualizationMagnification_.setModel(new DefaultComboBoxModel(new String[] { "1x", "2x", "4x", "8x", "16x", "32x", "64x", "128x" }));

      visualizationModel_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      visualizationModel_.setModel(new DefaultComboBoxModel(new String[] { "Gaussian" }));

      sigmaMin_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      sigmaMin_.setText("0");

      intensityMin_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      intensityMin_.setText("0");

      filterIntensityCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      filterIntensityCheckBox_.setText("Intensity");

      filterSigmaCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      filterSigmaCheckBox_.setText("Sigma");

      jLabel1.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel1.setText("Filters:");

      renderButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      renderButton_.setText("Render");
      renderButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            renderButton_ActionPerformed(evt);
         }
      });

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel4.setText("Localization Microscopy");

      zCalibrateButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      zCalibrateButton_.setText("Z Calibration");
      zCalibrateButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zCalibrateButton_ActionPerformed(evt);
         }
      });

      zCalibrationLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      zCalibrationLabel_.setText("UnCalibrated");

      unjitterButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      unjitterButton_.setText("Drift Correct");
      unjitterButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            unjitterButton_ActionPerformed(evt);
         }
      });

      linkButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      linkButton_.setText("Link");
      linkButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            linkButton_ActionPerformed(evt);
         }
      });

      jSeparator4.setOrientation(SwingConstants.VERTICAL);

      centerTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      centerTrackButton_.setText("Center");
      centerTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            centerTrackButton_ActionPerformed(evt);
         }
      });

      straightenTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      straightenTrackButton_.setText("Straighten");
      straightenTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            straightenTrackButton_ActionPerformed(evt);
         }
      });

      logLogCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      logLogCheckBox_.setText("log-log");

      plotComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      plotComboBox_.setModel(new DefaultComboBoxModel(new String[] { "t-X", "t-Y", "t-dist.", "t-Int.", "X-Y", " " }));

      SubRange.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      SubRange.setText("SubRange");
      SubRange.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            SubRangeActionPerformed(evt);
         }
      });

      mathButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      mathButton_.setText("Math");
      mathButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mathButton_ActionPerformed(evt);
         }
      });

      averageTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      averageTrackButton_.setText("Average");
      averageTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            averageTrackButton_ActionPerformed(evt);
         }
      });

      powerSpectrumCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      powerSpectrumCheckBox_.setText("PSD");

      plotButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      plotButton_.setText("Plot");
      plotButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            plotButton_ActionPerformed(evt);
         }
      });

      jLabel6.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel6.setText("Tracks");

      jSeparator2.setOrientation(SwingConstants.VERTICAL);

      SigmaLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      SigmaLabel2.setText("nm");

      pairsMaxDistanceField_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      pairsMaxDistanceField_.setText("500");

      referenceName_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      referenceName_.setText("JLabel1");

      c2CorrectButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      c2CorrectButton.setText("2C Correct");
      c2CorrectButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            c2CorrectButtonActionPerformed(evt);
         }
      });

      method2CBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      method2CBox_.setModel(new DefaultComboBoxModel(new String[] { "NR-Similarity", "Affine", "Piecewise-Affine", "LWM" }));
      method2CBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            method2CBox_ActionPerformed(evt);
         }
      });

      c2StandardButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      c2StandardButton.setText("2C Reference");
      c2StandardButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            c2StandardButtonActionPerformed(evt);
         }
      });

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel5.setText("2-Color");

      jSeparator3.setOrientation(SwingConstants.VERTICAL);

      infoButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      infoButton_.setText("Info");
      infoButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            infoButton_ActionPerformed(evt);
         }
      });

      removeButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      removeButton.setText("Remove");
      removeButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeButtonActionPerformed(evt);
         }
      });

      saveFormatBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      saveFormatBox_.setModel(new DefaultComboBoxModel(new String[] { "Binary", "Text" }));

      saveButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      saveButton.setText("Save");
      saveButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveButtonActionPerformed(evt);
         }
      });

      loadButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      loadButton.setText("Load");
      loadButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadButtonActionPerformed(evt);
         }
      });

      showButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      showButton_.setText("Show");
      showButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showButton_ActionPerformed(evt);
         }
      });
      
      extractTracksButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      extractTracksButton_.setText("Extract Tracks");
      extractTracksButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            extractTracksButton_ActionPerformed(evt);
         }
      });
      
      jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel2.setText("< spot <");

      jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel3.setText("< spot <");

      combineButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      combineButton_.setText("Combine");
      combineButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            combineButton_ActionPerformed(evt);
         }
      });

      listButton_1.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      listButton_1.setText("List Pairs");
      listButton_1.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            listButton_1ActionPerformed(evt);
         }
      });

      GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
         jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
         .addGroup(GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
            .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(68, 68, 68)
                        .addComponent(jLabel7))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap(33, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                           .addComponent(saveButton, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)
                           .addComponent(loadButton, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)
                           .addComponent(showButton_, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)
                           .addComponent(extractTracksButton_, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE))
                        .addGap(4, 4, 4)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                           .addComponent(saveFormatBox_, GroupLayout.PREFERRED_SIZE, 90, GroupLayout.PREFERRED_SIZE)
                           .addGroup(jPanel1Layout.createSequentialGroup()
                              .addGap(6, 6, 6)
                              .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                 .addComponent(removeButton, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                                 .addComponent(infoButton_, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE))))))
                  .addGap(7, 7, 7))
               .addGroup(GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                  .addGap(0, 0, Short.MAX_VALUE)
                  .addComponent(combineButton_, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)))
            .addComponent(jSeparator3, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(84, 84, 84)
                  .addComponent(jLabel5))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(14, 14, 14)
                        .addComponent(c2StandardButton, GroupLayout.PREFERRED_SIZE, 82, GroupLayout.PREFERRED_SIZE))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(c2CorrectButton, GroupLayout.PREFERRED_SIZE, 82, GroupLayout.PREFERRED_SIZE)))
                  .addGap(18, 18, 18)
                  .addComponent(referenceName_, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(11, 11, 11)
                  .addComponent(method2CBox_, GroupLayout.PREFERRED_SIZE, 90, GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(pairsMaxDistanceField_, GroupLayout.PREFERRED_SIZE, 44, GroupLayout.PREFERRED_SIZE)
                  .addGap(8, 8, 8)
                  .addComponent(SigmaLabel2, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))
               .addGroup(GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                  .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(listButton_1, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jSeparator2, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(10, 10, 10)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(plotButton_, GroupLayout.PREFERRED_SIZE, 58, GroupLayout.PREFERRED_SIZE)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(powerSpectrumCheckBox_))
                     .addComponent(averageTrackButton_)
                     .addComponent(mathButton_, GroupLayout.PREFERRED_SIZE, 88, GroupLayout.PREFERRED_SIZE)
                     .addComponent(SubRange, GroupLayout.PREFERRED_SIZE, 88, GroupLayout.PREFERRED_SIZE))
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                           .addGap(4, 4, 4)
                           .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                              .addComponent(logLogCheckBox_)
                              .addComponent(plotComboBox_, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE))
                           .addGap(15, 15, 15))
                        .addGroup(GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                           .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                           .addComponent(straightenTrackButton_, GroupLayout.PREFERRED_SIZE, 93, GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(centerTrackButton_, GroupLayout.PREFERRED_SIZE, 93, GroupLayout.PREFERRED_SIZE))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(83, 83, 83)
                  .addComponent(jLabel6)))
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jSeparator4, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(zCalibrateButton_)
                     .addComponent(unjitterButton_, GroupLayout.PREFERRED_SIZE, 107, GroupLayout.PREFERRED_SIZE)
                     .addComponent(linkButton_, GroupLayout.PREFERRED_SIZE, 107, GroupLayout.PREFERRED_SIZE)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(zCalibrationLabel_, GroupLayout.PREFERRED_SIZE, 75, GroupLayout.PREFERRED_SIZE)))
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(7, 7, 7)
                        .addComponent(jLabel1)
                        .addGap(4, 4, 4)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                           .addComponent(filterSigmaCheckBox_)
                           .addGroup(jPanel1Layout.createSequentialGroup()
                              .addGap(1, 1, 1)
                              .addComponent(filterIntensityCheckBox_)))
                        .addGap(1, 1, 1)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                           .addComponent(sigmaMin_, GroupLayout.PREFERRED_SIZE, 47, GroupLayout.PREFERRED_SIZE)
                           .addGroup(jPanel1Layout.createSequentialGroup()
                              .addGap(1, 1, 1)
                              .addComponent(intensityMin_, GroupLayout.PREFERRED_SIZE, 46, GroupLayout.PREFERRED_SIZE)))
                        .addGap(1, 1, 1)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                           .addComponent(jLabel3)
                           .addComponent(jLabel2))
                        .addGap(3, 3, 3)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                           .addComponent(sigmaMax_, GroupLayout.PREFERRED_SIZE, 48, GroupLayout.PREFERRED_SIZE)
                           .addComponent(intensityMax_, GroupLayout.PREFERRED_SIZE, 48, GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(renderButton_)
                        .addGap(4, 4, 4)
                        .addComponent(visualizationModel_, GroupLayout.PREFERRED_SIZE, 110, GroupLayout.PREFERRED_SIZE)
                        .addGap(6, 6, 6)
                        .addComponent(visualizationMagnification_, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)))
                  .addGap(4, 4, 4)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(SigmaLabel3)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addComponent(IntLabel2, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(137, 137, 137)
                  .addComponent(jLabel4))))
      );
      jPanel1Layout.setVerticalGroup(
         jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
         .addGroup(jPanel1Layout.createSequentialGroup()
            .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(jLabel7)
                     .addComponent(jLabel5))
                  .addGap(5, 5, 5))
               .addGroup(GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(jLabel6, GroupLayout.Alignment.TRAILING)
                     .addComponent(jLabel4, GroupLayout.Alignment.TRAILING))
                  .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)))
            .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(3, 3, 3)
                  .addComponent(saveButton, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
                  .addGap(25, 25, 25)
                  .addComponent(loadButton, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
                  .addGap(1, 1, 1)
                  .addComponent(showButton_, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
                  .addGap(1, 1, 1)
                  .addComponent(extractTracksButton_, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addComponent(saveFormatBox_, GroupLayout.PREFERRED_SIZE, 25, GroupLayout.PREFERRED_SIZE)
                  .addGap(21, 21, 21)
                  .addComponent(removeButton, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
                  .addGap(1, 1, 1)
                  .addComponent(infoButton_, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(combineButton_, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addComponent(c2StandardButton, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
                  .addGap(33, 33, 33)
                  .addComponent(c2CorrectButton, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addComponent(referenceName_)
                  .addGap(12, 12, 12)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(pairsMaxDistanceField_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                        .addComponent(method2CBox_, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE))
                     .addComponent(SigmaLabel2, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))
                  .addGap(28, 28, 28)
                  .addComponent(listButton_1, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addComponent(plotButton_, GroupLayout.PREFERRED_SIZE, 18, GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addComponent(powerSpectrumCheckBox_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addComponent(averageTrackButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                     .addComponent(mathButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                     .addComponent(centerTrackButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))
                  .addGap(2, 2, 2)
                  .addComponent(SubRange, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(17, 17, 17)
                        .addComponent(logLogCheckBox_))
                     .addComponent(plotComboBox_, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE))
                  .addGap(2, 2, 2)
                  .addComponent(straightenTrackButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                     .addComponent(zCalibrateButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                     .addComponent(renderButton_, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE))
                  .addGap(7, 7, 7)
                  .addComponent(zCalibrationLabel_))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(visualizationModel_, GroupLayout.PREFERRED_SIZE, 24, GroupLayout.PREFERRED_SIZE)
                     .addComponent(visualizationMagnification_, GroupLayout.PREFERRED_SIZE, 24, GroupLayout.PREFERRED_SIZE))
                  .addGap(16, 16, 16)
                  .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                           .addComponent(jLabel1)
                           .addComponent(unjitterButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(filterSigmaCheckBox_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                           .addComponent(filterIntensityCheckBox_)
                           .addComponent(linkButton_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(sigmaMin_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                           .addComponent(intensityMin_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                           .addComponent(jLabel2)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                           .addComponent(sigmaMax_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
                           .addComponent(jLabel3))
                        .addGap(1, 1, 1)
                        .addComponent(intensityMax_, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(41, 41, 41)
                  .addComponent(SigmaLabel3)
                  .addGap(7, 7, 7)
                  .addComponent(IntLabel2)))
            .addContainerGap())
         .addComponent(jSeparator3)
         .addComponent(jSeparator2)
         .addComponent(jSeparator4)
      );

      jPanel2.setLayout(new java.awt.BorderLayout());

      jScrollPane1_.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
      jScrollPane1_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

      mainTable_.setModel(mainTableModel_);
      mainTable_.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
      jScrollPane1_.setViewportView(mainTable_);

      jPanel2.add(jScrollPane1_, java.awt.BorderLayout.CENTER);

      GroupLayout layout = new GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(GroupLayout.Alignment.LEADING)
         .addComponent(jPanel1, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
         .addComponent(jPanel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jPanel2, GroupLayout.DEFAULT_SIZE, 404, Short.MAX_VALUE)
            .addGap(0, 0, 0))
      );

      pack();
   }

   /**
    * Loads data saved in TSF format (Tagged Spot File Format)
    * Opens awt file select dialog which lets you select only a single file
    * If you want to open multiple files, press the ctrl key while clicking
    * the button.  This will open the swing file opener.
    *
    * @evt
    */
    private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {

       int modifiers = evt.getModifiers();
              
       final File[] selectedFiles;
       if ((modifiers & java.awt.event.InputEvent.META_MASK) > 0) {
          // The Swing fileopener looks ugly but allows for selection of multiple files
          final JFileChooser jfc = new JFileChooser(loadTSFDir_);
          jfc.setMultiSelectionEnabled(true);
          jfc.setDialogTitle("Load Spot Data");
          int ret = jfc.showOpenDialog(this);
          if (ret != JFileChooser.APPROVE_OPTION) {
             return;
          }
          selectedFiles = jfc.getSelectedFiles();
       } else {
          File f = FileDialogs.openFile(this, "Open tsf data set", TSF_FILE);
          selectedFiles = new File[] {f};
       }
       
      if (selectedFiles != null && selectedFiles.length > 0) {
          
         // Thread doing file import
         Runnable loadFile = new Runnable() {

            @Override
            public void run() {
                loadFiles(selectedFiles);
            }
         };

         (new Thread(loadFile)).start();

      }
    }

    /**
     * Given an array of files, tries to import them all 
     * Uses .txt import for text files, and tsf importer for .tsf files.
     * @param selectedFiles - Array of files to be imported
    */
   private void loadFiles(File[] selectedFiles) {
      for (File selectedFile : selectedFiles) {
         loadTSFDir_ = selectedFile.getParent();
         if (selectedFile.getName().endsWith(".txt")) {
            LoadAndSave.loadText(selectedFile, this);
         } else if (selectedFile.getName().endsWith(".tsf")) {
            LoadAndSave.loadTSF(selectedFile, this);
         } else if (selectedFile.getName().endsWith(".bin")) {
            LoadAndSave.loadBin(selectedFile, this);
         } else {
            JOptionPane.showMessageDialog(getInstance(), "Unrecognized file extension");
         }
      }
   }
    
                  
    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {
       int rows[] = mainTable_.getSelectedRowsSorted();
       if (rows.length > 0) {
          for (int i = 0; i < rows.length; i++) {
             if (saveFormatBox_.getSelectedIndex() == 0) {
                if (i == 0)
                   dir_ = LoadAndSave.saveData(mainTableModel_.getRow(i), false, 
                           dir_, this);
                else
                   dir_ = LoadAndSave.saveData(mainTableModel_.getRow(i), true, 
                           dir_, this);
             } else {
                LoadAndSave.saveDataAsText(mainTableModel_.getRow(i), this);
             }
          }
       } else {
          JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to save");
       }
    }

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {
       int rows[] = mainTable_.getSelectedRowsSorted();
       if (rows.length > 0) {
          for (int row = rows.length - 1; row >= 0; row--) {
             mainTableModel_.removeRow(rows[row]);
          }
       } else {
          JOptionPane.showMessageDialog(getInstance(), "No dataset selected");
       }
    }

    private void showButton_ActionPerformed(java.awt.event.ActionEvent evt) {
       int row = mainTable_.getSelectedRowSorted();
       if (row > -1) {
          try {
            showResults(mainTableModel_.getRow(row));
          } catch (OutOfMemoryError ome) {
             JOptionPane.showMessageDialog(getInstance(), "Not enough memory to show data");
          }
       } else {
          JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to show");
       }
    }

   private void extractTracksButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int row = mainTable_.getSelectedRowSorted();
      if (row > -1) {
         Point s = MouseInfo.getPointerInfo().getLocation();
         ExtractTracksDialog extractTracksDialog = 
                 new ExtractTracksDialog(studio_, mainTableModel_.getRow(row), s);
      }
   }
    
   private void formComponentResized(java.awt.event.ComponentEvent evt) {
      Dimension d = getSize();
      d.height -= 155;
      jScrollPane1_.setSize(d);
      jScrollPane1_.getViewport().setViewSize(d);
   }

   /**
    * Use the selected data set as the reference for 2-channel color correction
    * @param evt 
    */
   private void c2StandardButtonActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select one or more datasets as color reference");
      } else {
         
         CoordinateMapper.PointMap points = new CoordinateMapper.PointMap();
         for (int row : rows) {
            
            // Get points from both channels in first frame as ArrayLists        
            ArrayList<Point2D.Double> xyPointsCh1 = new ArrayList<Point2D.Double>();
            ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
            for (SpotData gs : mainTableModel_.getRow(row).spotList_) {
               if (gs.getFrame() == 1) {
                  Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                  if (gs.getChannel() == 1) {
                     xyPointsCh1.add(point);
                  } else if (gs.getChannel() == 2) {
                     xyPointsCh2.add(point);
                  }
               }
            }

            if (xyPointsCh2.isEmpty()) {
               JOptionPane.showMessageDialog(getInstance(), 
                       "No points found in second channel.  Is this a dual channel dataset?");
               return;
            }


            // Find matching points in the two ArrayLists
            Iterator it2 = xyPointsCh1.iterator();
            NearestPoint2D np;
            try {
               np = new NearestPoint2D(xyPointsCh2,
                       NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText()));
            } catch (ParseException ex) {
               ReportingUtils.showError("Problem parsing Pairs max distance number");
               return;
            }

            while (it2.hasNext()) {
               Point2D.Double pCh1 = (Point2D.Double) it2.next();
               Point2D.Double pCh2 = np.findKDWSE(pCh1);
               if (pCh2 != null) {
                  points.put(pCh1, pCh2);
               }
            }
            if (points.size() < 4) {
               ReportingUtils.showError("Fewer than 4 matching points found.  Not enough to set as 2C reference");
               return;
            }
         }


         // we have pairs from all images, construct the coordinate mapper
         try {
            c2t_ = new CoordinateMapper(points, 2, 1);
            
            studio_.alerts().postAlert("2C Reference", DataCollectionForm.class , 
                    "Used " + points.size() + " spot pairs to calculate 2C Reference");
            
            String name = "ID: " + mainTableModel_.getRow(rows[0]).ID_;
            if (rows.length > 1) {
               for (int i = 1; i < rows.length; i++) {
                  name += "," + mainTableModel_.getRow(rows[i]).ID_;
               }
            }
            referenceName_.setText(name);
         } catch (Exception ex) {
            JOptionPane.showMessageDialog(getInstance(), 
               "Error setting color reference.  Did you have enough input points?");
         }
         
      }
   }

  
   public void listPairs(double maxDistance, boolean showPairs, 
           boolean showImage, boolean savePairs, String filePath, 
           boolean showSummary, boolean showGraph) {
      final int[] rows = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select a dataset");
         return;
      }
      if (showPairs || showImage || savePairs || showSummary || showGraph) {
         for (int row : rows) {
            ParticlePairLister.ListParticlePairs(row, maxDistance, showPairs, 
                   showImage, savePairs, filePath, showSummary, showGraph);
         }
      }
   }
   
   public void listPairTracks(double maxDistance, boolean showTrack, 
           boolean showSummary, boolean showOverlay, boolean saveFile, 
           String filePath, boolean p2d) {
      final int[] rows = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select a dataset");
         return;
      }
      if (showTrack || showSummary || showOverlay || saveFile || p2d) {
         ParticlePairLister.listParticlePairTracks(rows, maxDistance, showTrack, 
                 showSummary, showOverlay, saveFile, p2d, filePath);
      }
   }
   
   private void c2CorrectButtonActionPerformed(java.awt.event.ActionEvent evt) {
      int[] rows = mainTable_.getSelectedRowsSorted();
      if (rows.length > 0) {     
         try {
            for (int row : rows) {
               correct2C(mainTableModel_.getRow(row));
            }
         } catch (InterruptedException ex) {
            ReportingUtils.showError(ex);
         }
      } else
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to color correct");
   }

   private void unjitterButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      final int row = mainTable_.getSelectedRowSorted();
      if (row > -1) {
         Runnable doWorkRunnable = new Runnable() {
            @Override
            public void run() {
               if (jitterMethod_ == 0)
                  DriftCorrector.unJitter(mainTableModel_.getRow(row));
               else
                  new DriftCorrector().unJitter2(mainTableModel_.getRow(row), 
                          jitterMaxFrames_, jitterMaxSpots_);
            }
         };
         (new Thread(doWorkRunnable)).start();
      } else {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to unjitter");
      }
   }


   private void formWindowClosing(java.awt.event.WindowEvent evt) {
      UserProfile up = studio_.profile();
      Class oc = DataCollectionForm.class;
      up.setInt(oc, FRAMEXPOS, getX());
      up.setInt(oc, FRAMEYPOS, getY());
      up.setInt(oc, FRAMEWIDTH, getWidth());
      up.setInt(oc, FRAMEHEIGHT, getHeight());
       
      up.setBoolean(oc, USESIGMA, filterSigmaCheckBox_.isSelected());
      up.setString(oc, SIGMAMIN, sigmaMin_.getText());
      up.setString(oc, SIGMAMAX, sigmaMax_.getText());
      up.setBoolean(oc, USEINT, filterIntensityCheckBox_.isSelected());
      up.setString(oc, INTMIN, intensityMin_.getText());
      up.setString(oc, INTMAX, intensityMax_.getText());
      up.setString(oc, LOADTSFDIR, loadTSFDir_);
      up.setInt(oc, RENDERMAG, visualizationMagnification_.getSelectedIndex());
      up.setString(oc, PAIRSMAXDISTANCE, pairsMaxDistanceField_.getText());
       
      TableColumnModel cm = mainTable_.getColumnModel();
      up.setInt(oc, COL0WIDTH, cm.getColumn(0).getWidth());
      up.setInt(oc, COL1WIDTH, cm.getColumn(1).getWidth());
      up.setInt(oc, COL2WIDTH, cm.getColumn(2).getWidth());
      up.setInt(oc, COL3WIDTH, cm.getColumn(3).getWidth());
      up.setInt(oc, COL4WIDTH, cm.getColumn(4).getWidth());
      up.setInt(oc, COL5WIDTH, cm.getColumn(5).getWidth());
      up.setInt(oc, COL6WIDTH, cm.getColumn(6).getWidth());

      studio_.events().unregisterForEvents(this);
      this.dispose();
      instance_ = null;
   }

   /**
    * Present user with summary data of this dataset.
    * 
    * @param evt 
    */
   private void infoButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int row = mainTable_.getSelectedRowSorted();
      if (row > -1) {
          
         
         RowData rowData = mainTableModel_.getRow(row);
         String data = "Name: " + rowData.name_ + "\n" +
                 "Title: " + rowData.title_ + "\n" + 
                 "BoxSize: " + 2*rowData.halfSize_ + "\n" +
                 "Image Height (pixels): " + rowData.height_ + "\n" + 
                 "Image Width (pixels): " + rowData.width_ + "\n" +
                 "Nr. of Spots: " + rowData.maxNrSpots_ + "\n" +
                 "Pixel Size (nm): " + rowData.pixelSizeNm_ + "\n" +
                 "Z Stack Step Size (nm): " + rowData.zStackStepSizeNm_ + "\n" +
                 "Nr. of Channels: " + rowData.nrChannels_ + "\n" +
                 "Nr. of Frames: " + rowData.nrFrames_ + "\n" + 
                 "Nr. of Slices: " + rowData.nrSlices_ + "\n" +
                 "Nr. of Positions: " + rowData.nrPositions_ + "\n" +
                 "Is a Track: " + rowData.isTrack_;
         if (!rowData.isTrack_)
            data += "\nHas Z info: " + rowData.hasZ_;
         if (rowData.hasZ_) {
            data += "\nMinZ: " + String.format("%.2f",rowData.minZ_) + "\n";
            data += "MaxZ: " + String.format("%.2f",rowData.maxZ_);
         }
                    
         if (rowData.isTrack_) {
            ArrayList<Point2D.Double> xyList = ListUtils.spotListToPointList(rowData.spotList_);
            Point2D.Double avg = ListUtils.avgXYList(xyList);
            Point2D.Double stdDev = ListUtils.stdDevXYList(xyList, avg);
            
            data += "\n" + 
                    "Average X: " + avg.x + "\n" +
                    "StdDev X: " + stdDev.x + "\n" + 
                    "Average Y: " + avg.y + "\n" +
                    "StdDev Y: " + stdDev.y;           
         }
         
         TextWindow tw = new TextWindow("Info for " + rowData.name_, data, 300, 300);
         tw.setVisible(true);
       }
       else
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset first");
   }

   /**
    * Renders dataset 
    * 
    * @param evt 
    */
   private void renderButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      final int row = mainTable_.getSelectedRowSorted();
      if (row < 0) {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to render");
      } else {

         Runnable doWorkRunnable = new Runnable() {

            @Override
            public void run() {

               try {
                  int mag = 1 << visualizationMagnification_.getSelectedIndex();
                  SpotDataFilter sf = new SpotDataFilter();
                  if (filterSigmaCheckBox_.isSelected()) {
                     sf.setSigma(true, Double.parseDouble(sigmaMin_.getText()),
                             Double.parseDouble(sigmaMax_.getText()));
                  }
                  if (filterIntensityCheckBox_.isSelected()) {
                     sf.setIntensity(true, Double.parseDouble(intensityMin_.getText()),
                             Double.parseDouble(intensityMax_.getText()));
                  }

                  RowData rowData = mainTableModel_.getRow(row);
                  String fsep = System.getProperty("file.separator");
                  String ttmp = rowData.name_;
                  if (rowData.name_.contains(fsep)) {
                     ttmp = rowData.name_.substring(rowData.name_.lastIndexOf(fsep) + 1);
                  }
                  ttmp += mag + "x";
                  final String title = ttmp;
                  ImagePlus sp;
                  if (rowData.hasZ_) {
                     ImageStack is = ImageRenderer.renderData3D(rowData,
                             visualizationModel_.getSelectedIndex(), mag, null, sf);
                     sp = new ImagePlus(title, is);
                     DisplayUtils.AutoStretch(sp);
                     DisplayUtils.SetCalibration(sp, (rowData.pixelSizeNm_ / mag));                     
                     sp.show();

                  } else {
                     ImageProcessor ip = ImageRenderer.renderData(rowData,
                             visualizationModel_.getSelectedIndex(), mag, null, sf);
                     sp = new ImagePlus(title, ip);

                     GaussCanvas gs = new GaussCanvas(sp, mainTableModel_.getRow(row),
                             visualizationModel_.getSelectedIndex(), mag, sf);
                     DisplayUtils.AutoStretch(sp);
                     DisplayUtils.SetCalibration(sp, (rowData.pixelSizeNm_ / mag));
                     ImageWindow w = new ImageWindow(sp, gs);

                     w.setVisible(true);
                  }
               } catch (OutOfMemoryError ome) {
                  ReportingUtils.showError("Out of Memory");
               }
            }
         };
         
         (new Thread(doWorkRunnable)).start();
      }
   }

   private void plotButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select one or more datasets to plot");
      } else {
         RowData[] myRows = new RowData[rows.length];
         // TODO: check that these are tracks 
         for (int i = 0; i < rows.length; i++)
            myRows[i] = mainTableModel_.getRow(rows[i]);
         plotData(myRows, plotComboBox_.getSelectedIndex());
      }
   }

   
   public class TrackAnalysisData {

         public int frame;
         public int n;
         public double xAvg;
         public double xStdDev;
         public double yAvg;
         public double yStdDev;   
   }
   
   /**
    * Centers all selected tracks (subtracts the average position)
    * and then calculates the average position of all tracks.
    * Can take multiple tracks of varied lengths as input
    * 
    * @param evt 
    */
   private void averageTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select one or more datasets to average");
      } else {
         RowData[] myRows = new RowData[rows.length];
         ArrayList<Point2D.Double> listAvgs = new ArrayList<Point2D.Double>();
         
         for (int i = 0; i < rows.length; i++) {
            myRows[i] = mainTableModel_.getRow(rows[i]);
            ArrayList<Point2D.Double> xyPoints = ListUtils.spotListToPointList(myRows[i].spotList_);
            Point2D.Double listAvg = ListUtils.avgXYList(xyPoints);
            listAvgs.add(listAvg);
         }

         // organize all spots in selected rows in a Hashmap keyed by frame number
         // while doing so, also subtract the average (i.e. center around 0)
         HashMap<Integer, List<SpotData>> allData = 
                 new HashMap<Integer, List<SpotData>>();
         
         for (int i=0; i < myRows.length; i++) {
            for (SpotData spotData : myRows[i].spotList_) {
               SpotData spotCopy = new SpotData(spotData);
               spotCopy.setXCenter(spotData.getXCenter() - listAvgs.get(i).x);
               spotCopy.setYCenter(spotData.getYCenter() - listAvgs.get(i).y);
               int frame = spotData.getFrame();
               if (!allData.containsKey(frame)) {
                  List<SpotData> thisFrame = new ArrayList<SpotData>();
                  thisFrame.add(spotCopy);
                  allData.put(frame, thisFrame);
               } else {
                  allData.get(frame).add(spotCopy);
               }
            }
         }
                          
         List<SpotData> transformedResultList =
                 Collections.synchronizedList(new ArrayList<SpotData>());
         //List<TrackAnalysisData> avgTrackData = new ArrayList<TrackAnalysisData>();
         
         // for each frame in the collection, calculate the average
         for (int i = 1; i <= allData.size(); i++) {
            List<SpotData> frameList = allData.get(i);
            TrackAnalysisData tad = new TrackAnalysisData();
            tad.frame = i;
            tad.n = frameList.size();
            SpotData avgFrame = new SpotData(frameList.get(0));
            
            ArrayList<Point2D.Double> xyPoints = ListUtils.spotListToPointList(frameList);
            Point2D.Double listAvg = ListUtils.avgXYList(xyPoints);
            Point2D.Double stdDev = ListUtils.stdDevXYList(xyPoints, listAvg);
            tad.xAvg = listAvg.x;
            tad.yAvg = listAvg.y;
            tad.xStdDev = stdDev.x;
            tad.yStdDev = stdDev.y;
            //avgTrackData.add(tad);
             
            avgFrame.setXCenter(listAvg.x);
            avgFrame.setYCenter(listAvg.y);
            
            transformedResultList.add(avgFrame);
         }
         

         // Add transformed data to data overview window
         RowData rowData = myRows[0];
         addSpotData(rowData.name_ + " Average", rowData.title_, null, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, rowData.shape_,
                 rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                 rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
                 rowData.timePoints_, true, Coordinates.NM, false, 0.0, 0.0);
/*
         // show resultsTable
         ResultsTable rt = new ResultsTable();
         for (int i = 0; i < avgTrackData.size(); i++) {
            rt.incrementCounter();
            TrackAnalysisData trackData = avgTrackData.get(i);
            rt.addValue("Frame", trackData.frame);
            rt.addValue("n", trackData.n);
            rt.addValue("XAvg", trackData.xAvg);
            rt.addValue("XStdev", trackData.xStdDev);
            rt.addValue("YAvg", trackData.yAvg);
            rt.addValue("YStdev", trackData.yStdDev);
         }
         rt.show("Averaged Tracks");
*/

      }

   }

   public void doMathOnRows(RowData source, RowData operand, int action) {
      // create a copy of the dataset and copy in the corrected data
      List<SpotData> transformedResultList =
              Collections.synchronizedList(new ArrayList<SpotData>());

      ij.IJ.showStatus("Doing math on spot data...");
      
      try {
         
         for (int i = 0; i < source.spotList_.size(); i++) {
            SpotData spotSource = source.spotList_.get(i);
            boolean found = false;
            int j = 0;
            SpotData spotOperand = null;
            while (!found && j < operand.spotList_.size()) {
               spotOperand = operand.spotList_.get(j);
               if (source.isTrack_) {
                  if (spotSource.getChannel() == spotOperand.getChannel()
                          && spotSource.getFrame() == spotOperand.getFrame()
                          && spotSource.getPosition() == spotOperand.getPosition()
                          && spotSource.getSlice() == spotOperand.getSlice()) {
                     found = true;
                  }
               } else { // not a track, b.t.w., I am not sure if slices and frames 
                        // are always swapped in non-track data sets
                  if (spotSource.getChannel() == spotOperand.getChannel()
                          && spotSource.getSlice() == spotOperand.getFrame()
                          && spotSource.getPosition() == spotOperand.getPosition()
                          && spotSource.getFrame() == spotOperand.getSlice()) {
                     found = true;
                  }
               }
               j++;
            }
            if (found && spotOperand != null) {
               double x = 0.0;
               double y = 0.0;
               if (action == 0) {
                  x = spotSource.getXCenter() - spotOperand.getXCenter();
                  y = spotSource.getYCenter() - spotOperand.getYCenter();
               }
               SpotData newSpot = new SpotData(spotSource);
               newSpot.setXCenter(x);
               newSpot.setYCenter(y);
               transformedResultList.add(newSpot);
            }
            ij.IJ.showProgress(i, source.spotList_.size());
         }
         
         ij.IJ.showStatus("Finished doing math...");

         RowData rowData = source;
         
         addSpotData(rowData.name_ + " Subtracted", rowData.title_, rowData.dw_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                 rowData.nrFrames_, rowData.nrSlices_, 1, rowData.maxNrSpots_, 
                 transformedResultList,
                 rowData.timePoints_, rowData.isTrack_, Coordinates.NM, 
                 rowData.hasZ_, rowData.minZ_, rowData.maxZ_);
         
      } catch (IndexOutOfBoundsException iobe) {
         JOptionPane.showMessageDialog(getInstance(), "Data sets differ in Size");
      }

   }

   private void mathButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int[] rows = new int[mainTable_.getRowCount()];

      for (int i = 0; i < rows.length; i++) {
         rows[i] = (Integer) mainTable_.getValueAt(mainTable_.convertRowIndexToModel(i), 0);
      }

      MathForm mf = new MathForm(studio_.getUserProfile(), rows, rows);

      mf.setVisible(true);
   }

   /**
    * Links spots by checking in consecutive frames whether the spot is still present
    * If it is, add it to a list
    * Once a frame has been found in which it is not present, calculate the average spot position
    * and add this averaged spot to the list with linked spots
    * The Frame number of the linked spot list will be 0
    * @param evt - ignored...
    */
   private void linkButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      final int rows[] = mainTable_.getSelectedRowsSorted();

      final double maxDistance;
      try {
         maxDistance = NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText());
      } catch (ParseException ex) {
         ReportingUtils.logError("Error parsing pairs max distance field");
         return;
      }

      Runnable doWorkRunnable;
      doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            for (int row : rows) {
               final RowData rowData = mainTableModel_.getRow(row);
               if (rowData.frameIndexSpotList_ == null) {
                  rowData.index();
               }
               SpotLinker.link(rowData, maxDistance);
            }
         }
      };

      (new Thread(doWorkRunnable)).start();
   }

   private void straightenTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one or more datasets to straighten");
      } else {
         for (int row : rows) {
            RowData r = mainTableModel_.getRow(row);
            /*
            if (evt.getModifiers() > 0) {
               if (r.title_.equals(ij.IJ.getImage().getTitle())) {
                  ImagePlus ip = ij.IJ.getImage();
                  Roi roi = ip.getRoi();
                  if (roi.isLine()) {
                     Polygon pol = roi.getPolygon();

                  }

               }
            }
            */
            TrackOperator.straightenTrack(r);
         }
      }
   }

   private void centerTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one or more datasets to center");
      } else {
         for (int row : rows) {
            TrackOperator.centerTrack(mainTableModel_.getRow(row));
         }
      }
   }


   private void zCalibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length != 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one datasets for Z Calibration");
      } else {
         int result = zCalibrate(rows[0]);
         if (result == OK) {
            zCalibrationLabel_.setText("Calibrated");
         } else if (result == FAILEDDOINFORM) {
            ReportingUtils.showError("Z-Calibration failed");
         }
      }
   }

   private void method2CBox_ActionPerformed(java.awt.event.ActionEvent evt) {
      studio_.profile().setString(DataCollectionForm.class, METHOD2C, 
              (String) method2CBox_.getSelectedItem());
   }

   private String range_ = "";

   private void SubRangeActionPerformed(java.awt.event.ActionEvent evt) {

      final int[] rows = mainTable_.getSelectedRowsSorted();

      if (rows == null || rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one or more datasets for sub ranging");
         return;
      }

      range_ = (String) JOptionPane.showInputDialog(this, "Provide desired subrange\n"
              + "e.g. \"7-50\"", "SubRange", JOptionPane.PLAIN_MESSAGE, null, null, range_);
      ArrayList<Integer> desiredFrameNumbers = new ArrayList<Integer>(
              mainTableModel_.getRow(rows[0]).maxNrSpots_);
      String[] parts = range_.split(",");
      try {
         for (String part : parts) {
            String[] tokens = part.split("-");
            for (int i = Integer.parseInt(tokens[0].trim());
                    i <= Integer.parseInt(tokens[1].trim()); i++) {
               desiredFrameNumbers.add(i);
            }
         }
      } catch (NumberFormatException ex) {
         ReportingUtils.showError(ex, "Could not parse input");
      }

      final ArrayList<Integer> desiredFrameNumbersCopy = desiredFrameNumbers;          
      
      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            if (rows.length > 0) {
               for (int row : rows) {
                  RowData newRow =
                          edu.valelab.gaussianfit.utils.SubRange.subRange(
                          mainTableModel_.getRow(row), desiredFrameNumbersCopy);
                  addSpotData(newRow);
               }

            }
         }
      };
      doWorkRunnable.run();

   }

   private void combineButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      try {
         final int[] rows = mainTable_.getSelectedRowsSorted();
         
         if (rows == null || rows.length < 2) {
            JOptionPane.showMessageDialog(getInstance(), 
                    "Please select two or more datasets to combine");
            return;
         }               
         semaphore_.acquire();

         Runnable doWorkRunnable = new Runnable() {
            @Override
            public void run() {

               List<SpotData> newData =
                       Collections.synchronizedList(new ArrayList<SpotData>());
               for (int i = 0; i < rows.length; i++) {
                  RowData rowData = mainTableModel_.getRow(rows[i]);
                  for (SpotData gs : rowData.spotList_) {
                     newData.add(gs);
                  }
               }

               // Add transformed data to data overview window
               // for now, copy header of first data set
               RowData rowData = mainTableModel_.getRow(rows[0]);
               addSpotData(rowData.name_ + "-Combined",
                       rowData.title_,
                       rowData.dw_,
                       referenceName_.getText(), rowData.width_,
                       rowData.height_, rowData.pixelSizeNm_,
                       rowData.zStackStepSizeNm_, rowData.shape_,
                       rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                       rowData.nrSlices_, 1, rowData.maxNrSpots_, newData,
                       rowData.timePoints_,
                       false, Coordinates.NM, false, 0.0, 0.0);

               semaphore_.release();
            }
         };

         (new Thread(doWorkRunnable)).start();
      } catch (InterruptedException ex) {
         ReportingUtils.showError(ex, "Data set combiner got interupted");
      }
      
   }

   private void listButton_1ActionPerformed(java.awt.event.ActionEvent evt) {
      PairDisplayForm pdf = new PairDisplayForm();
      pdf.setVisible(true);
   }



   /**
    * Shows dataset in ImageJ Results Table
    *
    * @rowData
    */
   private void showResults(RowData rowData) {
      // Copy data to results table

      ResultsTable rt = new ResultsTable();
      rt.reset();
      rt.setPrecision(1);
      int shape = rowData.shape_;
      for (SpotData gd : rowData.spotList_) {
         if (gd != null) {
            rt.incrementCounter();
            rt.addValue(Terms.FRAME, gd.getFrame());
            rt.addValue(Terms.SLICE, gd.getSlice());
            rt.addValue(Terms.CHANNEL, gd.getChannel());
            rt.addValue(Terms.POSITION, gd.getPosition());
            rt.addValue(Terms.INT, gd.getIntensity());
            rt.addValue(Terms.BACKGROUND, gd.getBackground());
            if (rowData.coordinate_ == Coordinates.NM) {
               rt.addValue(Terms.XNM, gd.getXCenter());
               rt.addValue(Terms.YNM, gd.getYCenter());
               if (rowData.hasZ_)
                  rt.addValue(Terms.ZNM, gd.getZCenter());
            } else if (rowData.coordinate_ == Coordinates.PIXELS) {
               rt.addValue(Terms.XFITPIX, gd.getXCenter());
               rt.addValue(Terms.YFITPIX, gd.getYCenter());
            }
            rt.addValue(Terms.SIGMA, gd.getSigma());
            if (shape >= 1) {
               rt.addValue(Terms.WIDTH, gd.getWidth());
            }
            if (shape >= 2) {
               rt.addValue(Terms.A, gd.getA());
            }
            if (shape == 3) {
               rt.addValue(Terms.THETA, gd.getTheta());
            }
            rt.addValue(Terms.XPIX, gd.getX());
            rt.addValue(Terms.YPIX, gd.getY());
            for (String key : gd.getKeys()) {
               rt.addValue(key, gd.getValue(key));
            }
         }
      }
      
      TextPanel tp;
      TextWindow win;
      
      String name = "Spots from: " + rowData.name_;
      rt.show(name);
      ImagePlus siPlus = ij.WindowManager.getImage(rowData.title_);
      // Attach listener to TextPanel
      Frame frame = WindowManager.getFrame(name);
      if (frame != null && frame instanceof TextWindow && siPlus != null) {
         win = (TextWindow) frame;
         tp = win.getTextPanel();

         // TODO: the following does not work, there is some voodoo going on here
         for (MouseListener ms : tp.getMouseListeners()) {
            
            tp.removeMouseListener(ms);
         }
         for (KeyListener ks : tp.getKeyListeners()) {
            tp.removeKeyListener(ks);
         }
         for (KeyListener ks : win.getKeyListeners()) {
            win.removeKeyListener(ks);
         }
         
         ResultsTableListener myk = new ResultsTableListener(studio_, rowData.dw_, siPlus, 
                 rt, win, rowData.halfSize_);
         
         tp.removeKeyListener(IJ.getInstance());
         win.removeKeyListener(IJ.getInstance());
         tp.addKeyListener(myk);
         tp.addMouseListener(myk);
         frame.toFront();
      }
      
   }

   
   public JTable getResultsTable() {
      return mainTable_;
   }

 
   // Used to avoid multiple instances of correct2C at the same time
   private final Semaphore semaphore_ = new Semaphore(1, true);
   
   /**
    * Use the 2Channel calibration to create a new, corrected data set

    *
    * @param rowData
    */
   private void correct2C(final RowData rowData) throws InterruptedException {
      if (rowData.spotList_.size() <= 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to Color correct");
         return;
      }
      if (c2t_ == null) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "No calibration data available.  First Calibrate using 2C Reference");
         return;
      }

      semaphore_.acquire();
      int method = CoordinateMapper.LWM;
      if (method2CBox_.getSelectedItem().equals("Affine")) {
         method = CoordinateMapper.AFFINE;
      }
      if (method2CBox_.getSelectedItem().equals("NR-Similarity")) {
         method = CoordinateMapper.NONRFEFLECTIVESIMILARITY;
      }
      if (method2CBox_.getSelectedItem().equals("Piecewise-Affine")) {
         method = CoordinateMapper.PIECEWISEAFFINE;
      }
      c2t_.setMethod(method);

      ij.IJ.showStatus("Executing color correction");

      Runnable doWorkRunnable = new Runnable() {
         @Override
         public void run() {

            List<SpotData> correctedData =
                    Collections.synchronizedList(new ArrayList<SpotData>());
            Iterator it = rowData.spotList_.iterator();
            int frameNr = 0;
            while (it.hasNext()) {
               SpotData gs = (SpotData) it.next();
               if (gs.getFrame() != frameNr) {
                  frameNr = gs.getFrame();
                  ij.IJ.showStatus("Executing color correction...");
                  ij.IJ.showProgress(frameNr, rowData.nrFrames_);
               }
               if (gs.getChannel() == 1) {
                  Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                  try {
                     Point2D.Double corPoint = c2t_.transform(point);
                     SpotData gsn = new SpotData(gs);
                     if (corPoint != null) {
                        gsn.setXCenter(corPoint.x);
                        gsn.setYCenter(corPoint.y);
                        correctedData.add(gsn);
                     } else {
                        ReportingUtils.logError(
                                "Failed to match spot in channel 1, at " + 
                                gs.getX() + "-" + gs.getY() + ", micron: " +
                                gs.getXCenter() + "-" + gs.getYCenter() );
                     }
                     
                  } catch (Exception ex) {
                     ReportingUtils.logError(ex);
                  }
               } else if (gs.getChannel() == 2) {
                  correctedData.add(gs);
               }

            }

            // Add transformed data to data overview window
            addSpotData(rowData.name_ + "-CC-" + referenceName_.getText() + "-"
                    + method2CBox_.getSelectedItem(),
                    rowData.title_,
                    rowData.dw_,
                    referenceName_.getText(), rowData.width_,
                    rowData.height_, rowData.pixelSizeNm_,
                    rowData.zStackStepSizeNm_, rowData.shape_,
                    rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                    rowData.nrSlices_, 1, rowData.maxNrSpots_, correctedData,
                    rowData.timePoints_,
                    false, Coordinates.NM, false, 0.0, 0.0);

            semaphore_.release();
         }
      };

      (new Thread(doWorkRunnable)).start();
   }

   /**
    * Plots Tracks using JFreeChart
    *
    * @rowData
    * @plotMode - Index of plotMode in array {"t-X", "t-Y", "X-Y", "t-Int"};
    * @plotMode - Index of plotMode in array {"t-X", "t-Y", "t-dist.", "t-Int.",
    * "X-Y"};
    */
   private void plotData(RowData[] rowDatas, int plotMode) {
      String title = plotModes_[plotMode];
      boolean logLog = logLogCheckBox_.isSelected();
      boolean doPSD = powerSpectrumCheckBox_.isSelected();
      boolean useShapes = true;
      if (logLog || doPSD) {
         useShapes = false;
      }
      if (rowDatas.length == 1) {
         title = rowDatas[0].name_ + " " + plotModes_[plotMode];
      }

      XYSeries[] datas = new XYSeries[rowDatas.length];

             
      // Todo: check all rows and throw an error when there is a difference
      boolean useS = useSeconds(rowDatas[0]);
      boolean hasTimeInfo = hasTimeInfo(rowDatas[0]);

      String xAxis = null;

      switch (plotMode) {

         case (0): { // t-X
            if (doPSD) {
               FFTUtils.calculatePSDs(rowDatas, datas, PlotMode.X);
               xAxis = "Freq (Hz)";
               GaussianUtils.plotDataN(title + " PSD", datas, xAxis, "Strength",
                       0, 400, useShapes, logLog);

            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }

               for (int index = 0; index < rowDatas.length; index++) {
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000;
                        }
                        datas[index].add(timePoint, spot.getXCenter());
                     } else {
                        datas[index].add(i, spot.getXCenter());
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time(s)";
                     }
                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, "X(nm)", 0, 400, useShapes, logLog);
            }
         }
         break;

         case (1): { // t-Y
            if (doPSD) {
               FFTUtils.calculatePSDs(rowDatas, datas, PlotMode.Y);
               xAxis = "Freq (Hz)";
               GaussianUtils.plotDataN(title + " PSD", datas, xAxis, "Strength",
                       0, 400, useShapes, logLog);
            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000;
                        }
                        datas[index].add(timePoint, spot.getYCenter());
                     } else {
                        datas[index].add(i, spot.getYCenter());
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time(s)";
                     }
                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, "Y(nm)", 0, 400, useShapes, logLog);
            }
         }
         break;


         case (2): { // t-dist.
            if (doPSD) {
               /*
               FFTUtils.calculatePSDs(rowDatas, datas, PlotMode.Y);
               xAxis = "Freq (Hz)";
               GaussianUtils.plotDataN(title + " PSD", datas, xAxis, "Strength",
                       0, 400, useShapes, logLog);
                       * */
            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
                  SpotData sp = rowDatas[index].spotList_.get(0);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     double distX = (sp.getXCenter() - spot.getXCenter())
                             * (sp.getXCenter() - spot.getXCenter());
                     double distY = (sp.getYCenter() - spot.getYCenter())
                             * (sp.getYCenter() - spot.getYCenter());
                     double dist = Math.sqrt(distX + distY);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000.0;
                        }
                        datas[index].add(timePoint, dist);
                     } else {
                        datas[index].add(i, dist);
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time (s)";
                     }
                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, " distance (nm)", 0, 400, useShapes, logLog);
            }
         }
         break;

         case (3): { // t-Int
            if (doPSD) {
                JOptionPane.showMessageDialog(this, "Function is not implemented");
            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000;
                        }
                        datas[index].add(timePoint, spot.getIntensity());
                     } else {
                        datas[index].add(i, spot.getIntensity());
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time (s)";
                     }

                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, "Intensity (#photons)",
                       0, 400, useShapes, logLog);
            }
         }
         break;

         case (4): { // X-Y
            if (doPSD) {
               JOptionPane.showMessageDialog(this, "Function is not implemented");
            } else {
               double minX = Double.MAX_VALUE; double minY = Double.MAX_VALUE;
               double maxX = Double.MIN_VALUE; double maxY = Double.MIN_VALUE;
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_, false, true);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     datas[index].add(spot.getXCenter(), spot.getYCenter());
                     minX = Math.min(minX, spot.getXCenter());
                     minY = Math.min(minY, spot.getYCenter());
                     maxX = Math.max(maxX, spot.getXCenter());
                     maxY = Math.max(maxY, spot.getYCenter());
                  }
               }
               double xDivisor = 1.0;
               double yDivisor = 1.0;
               String xAxisTitle = "X(nm)";
               String yAxisTitle = "Y(nm)";
               if (maxX - minX > 10000) {
                  xAxisTitle = "X(micron)";
                  xDivisor = 1000;
               }
               if (maxY - minY > 10000) {
                  yAxisTitle = "Y(micron)";
                  yDivisor = 1000;
               } 
               if (xDivisor != 1.0 || yDivisor != 1.0) {  
                  for (int index = 0; index < rowDatas.length; index++) {
                     datas[index] = new XYSeries(rowDatas[index].ID_, false, true);
                     for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                        SpotData spot = rowDatas[index].spotList_.get(i);
                        datas[index].add(spot.getXCenter() / xDivisor, 
                                spot.getYCenter() / yDivisor);
                     }
                  }
               }

               GaussianUtils.plotDataN(title, datas, xAxisTitle, yAxisTitle, 0, 400, useShapes, logLog);
            }
         }
         break;
      }
   }
   
   public boolean useSeconds(RowData row) {
      boolean useS = false;
      if (row.timePoints_ != null) {
         if (row.timePoints_.get(row.timePoints_.size() - 1)
                 - row.timePoints_.get(0) > 10000) {
            useS = true;
         }
      }
      return useS;
   }
   
   private boolean hasTimeInfo(RowData row) {
      boolean hasTimeInfo = false;
      if (row.timePoints_ != null) {
         if (row.timePoints_.get(row.timePoints_.size() - 1)
                 - row.timePoints_.get(0) > 0) {
            hasTimeInfo = true;
         }
      }
      return hasTimeInfo;
   }
   
   /**
    * Performs Z-calibration
    * 
    * 
    * @param rowNr
    * @return 0 indicates success, 
    *          1 indicates failure and calling code should inform user, 
    *          2 indicates failure but calling code should not inform user
    */
   public int zCalibrate(int rowNr) {
      final double widthCutoff = 1000.0;
      final double maxVariance = 100000.0;
      final int minNrSpots = 1;
      
      
      zc_.clearDataPoints();
      
      RowData rd = mainTableModel_.getRow(rowNr);
      if (rd.shape_ < 2) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Use Fit Parameters Dimension 2 or 3 for Z-calibration");
         return FAILEDDONOTINFORM;
      }
      
      List<SpotData> sl = rd.spotList_;
      
      for (SpotData gsd : sl) {
         double xw = gsd.getWidth();
         double xy = xw / gsd.getA();
         if (xw < widthCutoff && xy < widthCutoff && xw > 0 && xy > 0) {
            zc_.addDataPoint(gsd.getWidth(), gsd.getWidth() / gsd.getA(),
               gsd.getSlice() /* * rd.zStackStepSizeNm_*/);
         }
      }
      zc_.plotDataPoints();
       
      zc_.clearDataPoints();
      
      // calculate average and stdev per frame
      if (rd.frameIndexSpotList_ == null) {
         rd.index();
      }  
      
      final int nrImages = rd.nrSlices_;
     
      int frameNr = 0;
      while (frameNr < nrImages) {
         List<SpotData> frameSpots = rd.frameIndexSpotList_.get(frameNr);
         if (frameSpots != null) {
            double[] xws = new double[frameSpots.size()];
            double[] yws = new double[frameSpots.size()];
            int i = 0;
            for (SpotData gsd : frameSpots) {
               xws[i] = gsd.getWidth();
               yws[i] = (gsd.getWidth() / gsd.getA());
               i++;
            }
            double meanX = StatUtils.mean(xws);
            double meanY = StatUtils.mean(yws);
            double varX = StatUtils.variance(xws, meanX);
            double varY = StatUtils.variance(yws, meanY);
            
            if (frameSpots.size() >= minNrSpots && 
                    meanX < widthCutoff &&
                    meanY < widthCutoff &&
                    varX < maxVariance && 
                    varY < maxVariance &&
                    meanX > 0 &&
                    meanY > 0) {
               zc_.addDataPoint(meanX, meanY, frameNr /* * rd.zStackStepSizeNm_ */);
            }
            
         }
         frameNr++;
      }
      if (zc_.nrDataPoints() < 6) {
         ReportingUtils.showError("Not enough particles found for 3D calibration");
         return FAILEDDONOTINFORM;
      }
      
      zc_.plotDataPoints();
      
      try {
         zc_.fitFunction();
      } catch (FunctionEvaluationException ex) {
         ReportingUtils.showError("Error while fitting data");
         return FAILEDDONOTINFORM;
      } catch (OptimizationException ex) {
         ReportingUtils.showError("Error while fitting data");
         return FAILEDDONOTINFORM;
      }

      return OK;
      
   }
   
   public void filterPairs(final double maxDistance, final double deviationMax,
           final int nrQuadrants) {
      final int[] rows = mainTable_.getSelectedRowsSorted();
      
      if (rows == null || rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select a dataset to filter");
         return;
      }
      
      for (int i = 0; i < rows.length; i++) {
         RowData rowData = mainTableModel_.getRow(rows[i]);
         PairFilter.filter(rowData, maxDistance, deviationMax, nrQuadrants);
      }
   }
   
   /**
    * Utility function that runs the selected rows through a SpotDataFilter
    * @param sf SpotDataFilter used to filter the selected dataset
    */
   public void filterSpots(SpotDataFilter sf) {
      final int[] rows = mainTable_.getSelectedRowsSorted();
      for (int i = 0; i < rows.length; i++) {
         RowData rowData = mainTableModel_.getRow(rows[i]);
         List<SpotData> filteredData = new ArrayList<SpotData>();
         for (SpotData spot : rowData.spotList_) {
            if (sf.filter(spot)) {
               filteredData.add(new SpotData(spot));
            }
         }
         // Add transformed data to data overview window
         addSpotData(rowData.name_ + "-Filtered", rowData.title_, rowData.dw_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_,
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_,
                 rowData.nrFrames_, rowData.nrSlices_, 1, filteredData.size(),
                 filteredData, null, false, DataCollectionForm.Coordinates.NM, rowData.hasZ_,
                 rowData.minZ_, rowData.maxZ_);

      }
   }

   public void setPieceWiseAffineParameters(int maxControlPoints, double maxDistance) {
      if (c2t_ != null) {
         c2t_.setPieceWiseAffineMaxControlPoints(maxControlPoints);
         c2t_.setPieceWiseAffineMaxDistance(maxDistance);
      }
   }   

}