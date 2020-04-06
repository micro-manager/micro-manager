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


package edu.ucsf.valelab.gaussianfit;

import com.google.common.eventbus.Subscribe;
import edu.ucsf.valelab.gaussianfit.datasetdisplay.ImageRenderer;
import edu.ucsf.valelab.gaussianfit.datasettransformations.SpotDataFilter;
import edu.ucsf.valelab.gaussianfit.datasettransformations.CoordinateMapper;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.ucsf.valelab.gaussianfit.utils.DisplayUtils;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.fitting.ZCalibrator;
import edu.ucsf.valelab.gaussianfit.data.LoadAndSave;
import edu.ucsf.valelab.gaussianfit.spotoperations.SpotLinker;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.datasetdisplay.ParticlePairLister;
import edu.ucsf.valelab.gaussianfit.datasetdisplay.TrackPlotter;
import edu.ucsf.valelab.gaussianfit.datasettransformations.DriftCorrector;
import edu.ucsf.valelab.gaussianfit.datasettransformations.PairFilter;
import edu.ucsf.valelab.gaussianfit.datasettransformations.TrackOperator;
import edu.ucsf.valelab.gaussianfit.internal.tabledisplay.DataTable;
import edu.ucsf.valelab.gaussianfit.internal.tabledisplay.DataTableModel;
import edu.ucsf.valelab.gaussianfit.internal.tabledisplay.DataTableRowSorter;
import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import edu.ucsf.valelab.gaussianfit.utils.NumberUtils;
import edu.ucsf.valelab.gaussianfit.utils.FileDialogs;
import edu.ucsf.valelab.gaussianfit.utils.FileDialogs.FileType;
import edu.ucsf.valelab.gaussianfit.utils.MapUtils;
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
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.stat.StatUtils;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.MMStudio;


/**
 *
 * @author Nico Stuurman
 */
public class DataCollectionForm extends JFrame {
   private DataTableModel mainTableModel_;
   private final String[] renderModes_ = {"Points", "Gaussian", "Norm. Gaussian"};
   private final String[] renderSizes_  = 
               {"1x", "2x", "4x", "8x", "16x", "32x", "64x", "128x"};
   private final String[] c2CorrectAlgorithms_ =  
               { "NR-Similarity", "Affine", "Piecewise-Affine", "LWM" };
   private final String[] fileFormats_ = { "Binary", "Text" };
   
   public final static String EXTENSION = ".tsf";
   
   // Preference instance
   private final MutablePropertyMapView settings_;
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
   private static final String PAIRMAXDIST = "DCPairMaxDist";
   private static final String PAIRMAXSIGMA = "DCPairSigma";
   private static final String PAIRNRQUADRANTS = "DCPairNrQuadrants";
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
   private DataTable mainTable_;
   private JComboBox saveFormatBox_;
   private JTextField pairsMaxDistanceField_;
   private JComboBox method2CBox_;
   private JLabel reference2CName_;
   private JCheckBox logLogCheckBox_;
   private JComboBox plotComboBox_;
   private JCheckBox powerSpectrumCheckBox_;
   private JComboBox visualizationMagnification_;
   private JComboBox visualizationModel_;
   private JLabel zCalibrationLabel_;
   private JCheckBox filterIntensityCheckBox_;
   private JTextField intensityMax_;
   private JTextField intensityMin_;
   private JCheckBox filterSigmaCheckBox_;
   private JTextField sigmaMax_;
   private JTextField sigmaMin_;
   private JTextField pairMaxDist_;
   private JTextField pairMaxSigma_;
   private JTextField pairNrQuadrants_;
       
   
   private static DataCollectionForm instance_ = null;

   public enum Coordinates {NM, PIXELS};
   public enum PlotMode {X, Y, INT};
      
   
   /**
    * Method to allow scripts to tune the jitter corrector
    * @param jm 
    */
   public void setJitterMethod(int jm) {
      if (jm == 0 || jm == 1) {
         jitterMethod_ = jm;
      }
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
      if (c2t_ == null) {
         return null;
      }
      return c2t_.getAffineTransform();
   }

  
   
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
   
   public void setSelectedRows(int start, int end) {
      mainTable_.setRowSelectionInterval(start, end);
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

      mainTableModel_ = new DataTableModel();
      
      initComponents();
              
      // Read UI values bakc form Profile
      UserProfile up = studio_.getUserProfile();
      Class oc = DataCollectionForm.class;
      settings_ = up.getSettings(oc);
      super.setBounds(
            settings_.getInteger(FRAMEXPOS, 50),
            settings_.getInteger(FRAMEYPOS, 100),
            settings_.getInteger(FRAMEWIDTH, 800),
            settings_.getInteger(FRAMEHEIGHT, 250) );
      filterSigmaCheckBox_.setSelected(settings_.getBoolean(USESIGMA, false));
      sigmaMin_.setText(settings_.getString(SIGMAMIN, "0.0"));
      sigmaMax_.setText(settings_.getString(SIGMAMAX, "20.0"));
      filterIntensityCheckBox_.setSelected(settings_.getBoolean(USEINT, false));
      intensityMin_.setText(settings_.getString(INTMIN, "0.0"));
      intensityMax_.setText(settings_.getString(INTMAX, "20000"));
      pairMaxDist_.setText(settings_.getString(PAIRMAXDIST, "100.0"));
      pairMaxSigma_.setText(settings_.getString(PAIRMAXSIGMA, "2.0"));
      pairNrQuadrants_.setText(settings_.getString(PAIRNRQUADRANTS, "36"));
      loadTSFDir_ = settings_.getString(LOADTSFDIR, "");
      visualizationMagnification_.setSelectedIndex(up.getInt(oc, RENDERMAG, 0));
      pairsMaxDistanceField_.setText(settings_.getString(PAIRSMAXDISTANCE, "500"));
      method2CBox_.setSelectedItem(settings_.getString(METHOD2C, "LWM"));
      
      mainTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TableColumnModel cm = mainTable_.getColumnModel();
      cm.getColumn(0).setPreferredWidth(settings_.getInteger(COL0WIDTH, 25));
      cm.getColumn(1).setPreferredWidth(settings_.getInteger(COL1WIDTH, 300));
      cm.getColumn(2).setPreferredWidth(settings_.getInteger(COL2WIDTH, 150));
      cm.getColumn(3).setPreferredWidth(settings_.getInteger(COL3WIDTH, 75));
      cm.getColumn(4).setPreferredWidth(settings_.getInteger(COL4WIDTH, 75));
      cm.getColumn(5).setPreferredWidth(settings_.getInteger(COL5WIDTH, 75));
      cm.getColumn(6).setPreferredWidth(settings_.getInteger(COL6WIDTH, 75));
      
      DataTableRowSorter sorter = 
              new DataTableRowSorter(mainTableModel_);
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
    * Adds a dataset to the data table.  
    * Data is provided as a builder that is build in this function
    * @param builder RowData builder
    */
   public void addSpotData(RowData.Builder builder) {
      RowData newRow = builder.build();
      mainTableModel_.addRowData(newRow);
      fireRowAdded();
      // attemp to make the newly added row visible.  
      final Rectangle cellRect = mainTable_.getCellRect(mainTableModel_.getRowCount(), 0, false); 
      mainTable_.scrollRectToVisible(cellRect);
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            formComponentResized(null);
         }
      } );
   }
      
   public void fireRowAdded() {
      if (mainTable_.getRowSorter() != null) {
         mainTable_.getRowSorter().allRowsChanged();
      } else {
         mainTableModel_.fireRowInserted();
      }
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

      intensityMax_ = new JTextField();
      sigmaMax_ = new JTextField();
      visualizationMagnification_ = new JComboBox();
      visualizationModel_ = new JComboBox();
      sigmaMin_ = new JTextField();
      intensityMin_ = new JTextField();
      filterIntensityCheckBox_ = new JCheckBox();
      filterSigmaCheckBox_ = new JCheckBox();
      zCalibrationLabel_ = new JLabel();
      logLogCheckBox_ = new JCheckBox();
      plotComboBox_ = new JComboBox();
      powerSpectrumCheckBox_ = new JCheckBox();
      pairsMaxDistanceField_ = new JTextField();
      reference2CName_ = new JLabel("  ");
      method2CBox_ = new JComboBox();
      saveFormatBox_ = new JComboBox();
      mainTable_ = new DataTable();
      
      
      final String insets = "insets 3";
      final Font gFont = new Font("Lucida Grande", 0, 10);
      final Font hFont = new Font("Lucida Grande", 0, 12);
      final Dimension buttonSize = new Dimension(100, 20); 
      final Dimension textFieldSize = new Dimension(50,20);
      final Dimension dropDownSize = new Dimension(70, 20);
      final Dimension dropDownSizeMax = new Dimension(100, 20);

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

/***************************    General tab  **********************************/
      JPanel generalPanel = new JPanel();
      generalPanel.setLayout(new MigLayout(insets, "[fill]3[fill]", 
              "[]4[]3[]3[]3[]"));
      
      JLabel generalLabel = new JLabel("General");
      generalLabel.setFont(hFont); 
      generalPanel.add(generalLabel, "span 2, gapleft 80, wrap");
      
      JButton saveButton = new JButton("Save");
      saveButton.setFont(gFont); 
      saveButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveButtonActionPerformed(evt);
         }
      });
      saveButton.setMaximumSize(buttonSize);
      generalPanel.add(saveButton);

      saveFormatBox_.setFont(gFont); 
      saveFormatBox_.setModel(new DefaultComboBoxModel(fileFormats_));
      saveFormatBox_.setPreferredSize(dropDownSize);
      saveFormatBox_.setMaximumSize(dropDownSizeMax);
      generalPanel.add(saveFormatBox_, "wrap");
      
      JButton loadButton = new JButton("Load");
      loadButton.setFont(gFont); 
      loadButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadButtonActionPerformed(evt);
         }
      });
      loadButton.setMaximumSize(buttonSize);
      generalPanel.add(loadButton);

      JButton removeButton = new JButton("Remove");
      removeButton.setFont(gFont); 
      removeButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeButtonActionPerformed(evt);
         }
      });
      removeButton.setMaximumSize(buttonSize);
      generalPanel.add(removeButton, "wrap");

      JButton showButton = new JButton("Show");
      showButton.setFont(gFont); 
      showButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showButton_ActionPerformed(evt);
         }
      }); 
      showButton.setMaximumSize(buttonSize);
      generalPanel.add(showButton);
      
      JButton infoButton = new JButton("Info");
      infoButton.setFont(gFont); 
      infoButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            infoButton_ActionPerformed(evt);
         }
      });
      infoButton.setMaximumSize(buttonSize);
      generalPanel.add(infoButton, "wrap");
         
      JButton extractTracksButton = new JButton("Extract Tracks");
      extractTracksButton.setFont(gFont); 
      extractTracksButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            extractTracksButton_ActionPerformed(evt);
         }
      });
      extractTracksButton.setMaximumSize(buttonSize);
      generalPanel.add(extractTracksButton);
           
      JButton combineButton = new JButton("Combine");
      combineButton.setFont(gFont); 
      combineButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            combine(true);
         }
      });
      combineButton.setMaximumSize(buttonSize);
      generalPanel.add(combineButton, "wrap");
      
      JButton combineTracksButton = new JButton("Combine Tracks");
      combineTracksButton.setFont(gFont); 
      combineTracksButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            combine(false);
         }
      });
      combineTracksButton.setMaximumSize(buttonSize);
      generalPanel.add(combineTracksButton);

      
/**********************  2-Color tab    ***************************************/
      JPanel c2Panel = new JPanel(new MigLayout(insets, "[fill]3[fill]", 
               "[]4[]3[]3[]3[]"));
      
      JLabel c2olorLabel = new JLabel("2-Color");
      c2olorLabel.setFont(hFont);
      c2Panel.add(c2olorLabel, "span 2, gapleft 60, wrap");
      
      JButton c2StandardButton = new JButton("2C Reference");
      c2StandardButton.setFont(gFont);
      c2StandardButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            c2StandardButtonActionPerformed(evt);
         }
      });
      c2StandardButton.setMaximumSize(buttonSize);
      c2Panel.add(c2StandardButton, "width 90:90:95");
           
      reference2CName_.setFont(gFont);
      c2Panel.add(reference2CName_, "width 50:50:60, wrap");
          
      method2CBox_.setFont(gFont); 
      method2CBox_.setModel(new DefaultComboBoxModel(c2CorrectAlgorithms_));
      method2CBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            method2CBox_ActionPerformed(evt);
         }
      });
      method2CBox_.setPreferredSize(dropDownSize);
      method2CBox_.setMaximumSize(dropDownSizeMax);
      c2Panel.add(method2CBox_);
           
      pairsMaxDistanceField_.setFont(gFont); 
      pairsMaxDistanceField_.setText("500");
      pairsMaxDistanceField_.setPreferredSize(textFieldSize);
      pairsMaxDistanceField_.setMinimumSize(textFieldSize);
      c2Panel.add(pairsMaxDistanceField_, "split 2");
      
      JLabel nmLabel = new JLabel("nm");
      nmLabel.setFont(gFont); 
      c2Panel.add(nmLabel, "wrap");
      
      JButton c2CorrectButton = new JButton("2C Correct");
      c2CorrectButton.setFont(gFont);
      c2CorrectButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            c2CorrectButtonActionPerformed(evt);
         }
      });
      c2CorrectButton.setMaximumSize(buttonSize);
      c2Panel.add(c2CorrectButton, "span 2, gapleft 40, gapright 40, wrap");

      JButton listPairsButton = new JButton("List Pairs");
      listPairsButton.setFont(gFont); 
      listPairsButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            listButton_1ActionPerformed(evt);
         }
      });
      listPairsButton.setMaximumSize(buttonSize);
      c2Panel.add(listPairsButton, "span 2, gapleft 40, gapright 40, wrap");

/************************* Tracks ***************************/      
      JPanel tracksPanel = new JPanel(new MigLayout(insets, "[fill]3[fill]", 
               "[]4[]3[]3[]3[]"));
      
      JLabel trackLabel = new JLabel("Tracks");
      trackLabel.setFont(hFont); 
      tracksPanel.add(trackLabel, "span 2, gapleft 60, wrap");

      JButton plotButton = new JButton("Plot");
      plotButton.setFont(gFont); 
      plotButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            plotButton_ActionPerformed(evt);
         }
      });
      plotButton.setMaximumSize(buttonSize);
      tracksPanel.add(plotButton);
      
      plotComboBox_.setFont(gFont); 
      plotComboBox_.setModel(new DefaultComboBoxModel(TrackPlotter.PLOTMODES));
      plotComboBox_.setMaximumSize(dropDownSizeMax);
      tracksPanel.add(plotComboBox_, "wrap");

      powerSpectrumCheckBox_.setFont(gFont); 
      powerSpectrumCheckBox_.setText("PSD");
      powerSpectrumCheckBox_.setMaximumSize(buttonSize);
      tracksPanel.add(powerSpectrumCheckBox_);
      
      logLogCheckBox_.setFont(gFont); 
      logLogCheckBox_.setText("log-log");
      logLogCheckBox_.setMaximumSize(buttonSize);
      tracksPanel.add(logLogCheckBox_, "wrap");

      JButton averageTrackButton = new JButton("Average");
      averageTrackButton.setFont(gFont);
      averageTrackButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            averageTrackButton_ActionPerformed(evt);
         }
      });
      averageTrackButton.setMaximumSize(buttonSize);
      tracksPanel.add(averageTrackButton);
      
      JButton straightenTrackButton = new JButton("Straighten");
      straightenTrackButton.setFont(gFont); 
      straightenTrackButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            straightenTrackButton_ActionPerformed(evt);
         }
      });
      straightenTrackButton.setMaximumSize(buttonSize);
      tracksPanel.add(straightenTrackButton, "wrap");

      JButton mathButton = new JButton("Math");
      mathButton.setFont(gFont); 
      mathButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mathButton_ActionPerformed(evt);
         }
      });
      mathButton.setMaximumSize(buttonSize);
      tracksPanel.add(mathButton);
      
      JButton centerTrackButton = new JButton("Center");
      centerTrackButton.setFont(gFont);
      centerTrackButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            centerTrackButton_ActionPerformed(evt);
         }
      });
      centerTrackButton.setMaximumSize(buttonSize);
      tracksPanel.add(centerTrackButton, "wrap");
      
      JButton subRangeButton = new JButton("SubRange");
      subRangeButton.setFont(gFont); 
      subRangeButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            SubRangeActionPerformed(evt);
         }
      });
      subRangeButton.setMaximumSize(buttonSize);
      tracksPanel.add(subRangeButton);

/************************** Filters ***************************/    
      JPanel filterPanel = new JPanel(new MigLayout(insets, 
              "[fill]0[fill]0[fill]0[fill]0[fill]", 
              "[]3[]0[]0[]0[]"));
      
      JLabel filterLabel = new JLabel("Spot Filters:");
      filterLabel.setFont(hFont); 
      filterPanel.add(filterLabel, "span 5, gapleft 30, split 2, center");
      
      JButton filterNowButton = new JButton("Filter Now");
            filterNowButton.setFont(gFont); 
      filterNowButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            filterNow_ActionPerformed();
         }
      });
      filterNowButton.setMaximumSize(buttonSize);
      filterPanel.add(filterNowButton, "center, wrap");
      
           
      filterIntensityCheckBox_.setFont(gFont);
      filterIntensityCheckBox_.setText("Intensity");
      filterPanel.add(filterIntensityCheckBox_);
      
      intensityMin_.setFont(gFont);
      intensityMin_.setText("0");
      intensityMin_.setPreferredSize(textFieldSize);
      intensityMin_.setMinimumSize(textFieldSize);
      filterPanel.add(intensityMin_);
      
      JLabel spotCompLabel1 = new JLabel("< spot <");
      spotCompLabel1.setFont(gFont); 
      filterPanel.add(spotCompLabel1);
      
      intensityMax_.setFont(gFont);
      intensityMax_.setText("0");
      filterPanel.add(intensityMax_);
      
      JLabel intensityUnitLabel = new JLabel("#");
      intensityUnitLabel.setFont(gFont); 
      filterPanel.add(intensityUnitLabel, "wrap");
      
      filterSigmaCheckBox_.setFont(gFont); 
      filterSigmaCheckBox_.setText("Sigma");     
      filterPanel.add(filterSigmaCheckBox_);
              
      sigmaMin_.setFont(gFont);
      sigmaMin_.setText("0");
      sigmaMin_.setMinimumSize(textFieldSize);
      filterPanel.add(sigmaMin_);
              
      JLabel spotCompLabel2 = new JLabel("< spot <");
      spotCompLabel2.setFont(gFont);
      filterPanel.add(spotCompLabel2);
              
      sigmaMax_.setFont(gFont);
      sigmaMax_.setText("0");
      sigmaMax_.setMinimumSize(textFieldSize);
      filterPanel.add(sigmaMax_);
              
      JLabel sigmaUnitLabel = new JLabel("nm");
      sigmaUnitLabel.setFont(gFont);
      filterPanel.add(sigmaUnitLabel, "wrap");
      
      JSeparator hLine = new JSeparator();
      hLine.setOrientation(SwingConstants.HORIZONTAL);
      hLine.setMinimumSize(new Dimension (60, 2));
      filterPanel.add(hLine, "growx, span 5, wrap");
      
      JLabel pairFilterLabel = new JLabel("Pair Filters:");
      pairFilterLabel.setFont(hFont); 
      filterPanel.add(pairFilterLabel, "span 5, gapleft 30, split 2, center");
      
      JButton pairFilterButton = new JButton("Filter Now");
      pairFilterButton.setFont(gFont); 
      pairFilterButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pairFilterNow_ActionPerformed();
         }
      });
      pairFilterButton.setMaximumSize(buttonSize);
      filterPanel.add(pairFilterButton, "center, wrap");

      // filterPairs(50, 2.0, 36);
      JLabel maxPairDistLabel = new JLabel("Max dist.(nm)");
      maxPairDistLabel.setFont(gFont);
      filterPanel.add(maxPairDistLabel);
      
      pairMaxDist_ = new JTextField("100.0");
      pairMaxDist_.setFont(gFont);
      pairMaxDist_.setMinimumSize(textFieldSize);
      filterPanel.add(pairMaxDist_);
      
      JLabel sigmaDistLabel = new JLabel("Max sigma");
      sigmaDistLabel.setFont(gFont);
      filterPanel.add(sigmaDistLabel, "span 3, split 2");
      
      pairMaxSigma_ = new JTextField("2.0");
      pairMaxSigma_.setFont(gFont);
      pairMaxSigma_.setMinimumSize(textFieldSize);
      filterPanel.add(pairMaxSigma_, "wrap");
      
      JLabel pairNrQuadLabel = new JLabel("# Quadrants");
      pairNrQuadLabel.setFont(gFont);
      filterPanel.add(pairNrQuadLabel);
      
      pairNrQuadrants_ = new JTextField("36");
      pairNrQuadrants_.setFont(gFont);
      pairNrQuadrants_.setMinimumSize(textFieldSize);
      filterPanel.add(pairNrQuadrants_);
      
      
      
/************************* Localization Microscopy *******************/  
      JPanel visualizationPanel = new JPanel(new MigLayout(insets, 
              "[fill]3[fill]3[fill]", 
              "[]4[]3[]3[]3[]"));
      
      JLabel lmLabel = new JLabel("Localization Microscopy");
      lmLabel.setFont(hFont);
      visualizationPanel.add(lmLabel, "span 3, gapleft 70, wrap");

      JButton renderButton = new JButton("Render");
      renderButton.setFont(gFont); 
      renderButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            renderButton_ActionPerformed(evt);
         }
      });
      renderButton.setMaximumSize(buttonSize);
      visualizationPanel.add(renderButton);
      
      visualizationModel_.setFont(gFont);
      visualizationModel_.setModel(new DefaultComboBoxModel(renderModes_));
      visualizationModel_.setPreferredSize(dropDownSize);
      visualizationModel_.setMaximumSize(dropDownSizeMax);
      visualizationPanel.add(visualizationModel_);

      visualizationMagnification_.setFont(gFont); 
      visualizationMagnification_.setModel(new DefaultComboBoxModel(renderSizes_));
      visualizationMagnification_.setPreferredSize(dropDownSize);
      visualizationMagnification_.setMaximumSize(dropDownSizeMax);
      visualizationPanel.add(visualizationMagnification_, "wrap");
    
      
      JButton zCalibrateButton = new JButton("Z Calibration");
      zCalibrateButton.setFont(gFont); 
      zCalibrateButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zCalibrateButton_ActionPerformed(evt);
         }
      });
      zCalibrateButton.setMaximumSize(buttonSize);
      visualizationPanel.add(zCalibrateButton);

      JButton unjitterButton = new JButton("Drift Correct");
      unjitterButton.setFont(gFont); 
      unjitterButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            unjitterButton_ActionPerformed(evt);
         }
      });
      unjitterButton.setMaximumSize(buttonSize);
      visualizationPanel.add(unjitterButton);

      JButton linkButton = new JButton("Link");
      linkButton.setFont(gFont);
      linkButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            linkButton_ActionPerformed(evt);
         }
      });
      linkButton.setMaximumSize(buttonSize);
      visualizationPanel.add(linkButton, "wrap");
      
      zCalibrationLabel_.setFont(gFont); 
      zCalibrationLabel_.setText("UnCalibrated");
      visualizationPanel.add(zCalibrationLabel_, "gapleft 10");

   
/************************* Assemble the complete window  *******************/      
      Dimension vLineMinSize = new Dimension(6, 60);

      getContentPane().setLayout(new MigLayout(insets, 
              "[fill]0[]0[fill]2[]2[fill]2[]2[fill]", "[top]2[fill, grow 1]"));
      getContentPane().add(generalPanel);
      getContentPane().add(getVLine(vLineMinSize), "growy");
      getContentPane().add(c2Panel);
      getContentPane().add(getVLine(vLineMinSize), "growy");
      getContentPane().add(tracksPanel);
      getContentPane().add(getVLine(vLineMinSize), "growy");
      getContentPane().add(filterPanel);
      getContentPane().add(getVLine(vLineMinSize), "growy");
      getContentPane().add(visualizationPanel, "wrap");
      
      JScrollPane tableScrollPane = new JScrollPane();      
      tableScrollPane.setHorizontalScrollBarPolicy(
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      tableScrollPane.setVerticalScrollBarPolicy(
              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      mainTable_.setModel(mainTableModel_);
      mainTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      mainTable_.setPreferredScrollableViewportSize(new Dimension(10000, 10000));
      mainTable_.addMouseListener(new PopClickListener());
      tableScrollPane.setViewportView(mainTable_);

      getContentPane().add(tableScrollPane, "span 9, wrap");


      pack();
   }
   
   /**
    * Class to create a popupmenu for shortcuts to esoteric functions
    */
   
   private class PopupMenu extends JPopupMenu {

      public PopupMenu() {
         JMenuItem copyTracksItem = new JMenuItem(new AbstractAction("Copy Tracks") {
            @Override
            public void actionPerformed(ActionEvent ae) {
               String txt1 = getSelectedTracks(true);
               String txt2 = getSelectedTracks(false);
               Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
               clpbrd.setContents(new StringSelection(txt1 + txt2), null);
            }
         });
         super.add(copyTracksItem);
         
         JMenuItem copySummaryItem = new JMenuItem(new AbstractAction("Copy Summary") {
            @Override
            public void actionPerformed(ActionEvent ae) {
               String txt1 = getSummaryOfSelectedTracks(false);
               String txt2 = getSummarizedTrackData(false);
               Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
               clpbrd.setContents(new StringSelection(txt1 + "\t" + txt2), null);
            }
         });
         super.add(copySummaryItem);
         
         JMenuItem copyHeadersItem = new JMenuItem(new AbstractAction("Copy Headers") {
            @Override
            public void actionPerformed(ActionEvent ae) {
               String txt1 = getSummaryOfSelectedTracks(true);
               String txt2 = getSummarizedTrackData(true);
               Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
               clpbrd.setContents(new StringSelection(txt1 + "\t" + txt2), null);
            }
         });
         super.add(copyHeadersItem);
         
         JMenuItem getInterTrackDistancesItem = new JMenuItem(new AbstractAction("Inter Track Distances") {
            @Override
            public void actionPerformed(ActionEvent ae) {
               IJ.showStatus("Calculating Inter Track distances...");
               
               // Distance calculations take a long time so need their own thread
               Runnable calculateDistances = new Runnable() {
                  @Override
                  public void run() {
                     String header = getInterTrackDistances(true);
                     String interTrackDistances = getInterTrackDistances(false);
                     Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                     clpbrd.setContents(new StringSelection(header + "\n" + interTrackDistances), null);
                  }
               };

               (new Thread(calculateDistances, "Calculate distances")).start();
               
            }
         });
         super.add(getInterTrackDistancesItem);
         
         JMenuItem selectAllRowsItem = new JMenuItem(new AbstractAction("Select all") {
            @Override
            public void actionPerformed(ActionEvent ae) {
               mainTable_.selectAll();
            }
         });
         super.add(selectAllRowsItem);
      }
      
   }
   
   private class PopClickListener extends MouseAdapter {

      @Override
      public void mousePressed(MouseEvent e) {
         if (e.isPopupTrigger()) {
            doPop(e);
         }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
         if (e.isPopupTrigger()) {
            doPop(e);
         }
      }

      private void doPop(MouseEvent e) {
         PopupMenu menu = new PopupMenu();
         menu.show(e.getComponent(), e.getX(), e.getY());
      }
   }


   /**
    * Helper function to facilitate the UI
    * @param minSize minimumSize of the Separator
    * @return vertical separator with the desired minimum size
    */
   private JSeparator getVLine(Dimension minSize) {
      JSeparator vLine = new JSeparator();
      vLine.setOrientation(SwingConstants.VERTICAL);
      vLine.setMinimumSize(minSize); 
      return vLine;
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
          final JFileChooser jfc = new JFileChooser(settings_.getString(LOADTSFDIR, ""));
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
         settings_.putString(LOADTSFDIR, selectedFile.getParent());
         if (selectedFile.getName().endsWith(".txt")) {
            LoadAndSave.loadText(selectedFile, this);
         } else if (selectedFile.getName().endsWith(".tsf")) {
            LoadAndSave.loadTSF(selectedFile, this);
         } else if (selectedFile.getName().endsWith(".bin")) {
            LoadAndSave.loadBin(selectedFile, this);
         } else {
            JOptionPane.showMessageDialog(this, "Unrecognized file extension");
         }
      }
   }
    
                  
    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {
       int rows[] = mainTable_.getSelectedRowsSorted();
       if (rows.length > 0) {
          RowData[] rowData = new RowData[rows.length];
          for (int i = 0; i < rows.length; i++) {
             rowData[i] = mainTableModel_.getRow(rows[i]);
          }
             if (saveFormatBox_.getSelectedIndex() == 0) {
                dir_ = LoadAndSave.saveData(rowData, false, dir_, this);
             } else {
                dir_ = LoadAndSave.saveDataAsText(rowData, dir_, this);
             }
          
       } else {
          JOptionPane.showMessageDialog(this, "Please select a dataset to save");
       }
    }

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {
       int rows[] = mainTable_.getSelectedRowsSorted();
       if (rows.length > 0) {
             mainTableModel_.removeRows(rows);
       } else {
          JOptionPane.showMessageDialog(this, "No dataset selected");
       }
    }

    private void showButton_ActionPerformed(java.awt.event.ActionEvent evt) {
       int row = mainTable_.getSelectedRowSorted();
       if (row > -1) {
          try {
            showResults(mainTableModel_.getRow(row));
          } catch (OutOfMemoryError ome) {
             JOptionPane.showMessageDialog(this, "Not enough memory to show data");
          }
       } else {
          JOptionPane.showMessageDialog(this, "Please select a dataset to show");
       }
    }

   private void extractTracksButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int row = mainTable_.getSelectedRowSorted();
      if (row > -1) {
         Point s = MouseInfo.getPointerInfo().getLocation();
         ExtractTracksDialog extractTracksDialog = 
                 new ExtractTracksDialog(studio_, mainTableModel_.getRow(row), s);
      } else {
         JOptionPane.showMessageDialog(this, "No Data Rows selected");
      }
   }
    
   private void formComponentResized(java.awt.event.ComponentEvent evt) {
      mainTable_.update();
      super.repaint();
   }

   /**
    * Use the selected data set as the reference for 2-channel color correction
    * @param evt 
    */
   private void c2StandardButtonActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(this, "Please select one or more datasets as color reference");
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
               JOptionPane.showMessageDialog(this, 
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
            reference2CName_.setText(name);
         } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, 
               "Error setting color reference.  Did you have enough input points?");
         }
         
      }
   }

   
   public void listPairTracks(ParticlePairLister.Builder builder) {
      final int[] rows = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(this, 
                 "Please select a dataset");
         return;
      }
      ParticlePairLister ppl = builder.rows(rows).build();
      ppl.listParticlePairTracks();
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
      } else {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select a dataset to color correct");
      }
   }

   private void unjitterButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      final int row = mainTable_.getSelectedRowSorted();
      if (row > -1) {
         Runnable doWorkRunnable = new Runnable() {
            @Override
            public void run() {
               if (jitterMethod_ == 0) {
                  DriftCorrector.unJitter(mainTableModel_.getRow(row));
               } else {
                  new DriftCorrector().unJitter2(mainTableModel_.getRow(row),
                          jitterMaxFrames_, jitterMaxSpots_);
               }
            }
         };
         (new Thread(doWorkRunnable)).start();
      } else {
         JOptionPane.showMessageDialog(this, "Please select a dataset to unjitter");
      }
   }


   private void formWindowClosing(java.awt.event.WindowEvent evt) {
      settings_.putInteger(FRAMEXPOS, getX());
      settings_.putInteger(FRAMEYPOS, getY());
      settings_.putInteger(FRAMEWIDTH, getWidth());
      settings_.putInteger(FRAMEHEIGHT, getHeight());
       
      settings_.putBoolean(USESIGMA, filterSigmaCheckBox_.isSelected());
      settings_.putString(SIGMAMIN, sigmaMin_.getText());
      settings_.putString(SIGMAMAX, sigmaMax_.getText());
      settings_.putBoolean(USEINT, filterIntensityCheckBox_.isSelected());
      settings_.putString(INTMIN, intensityMin_.getText());
      settings_.putString(INTMAX, intensityMax_.getText());
      settings_.putString(PAIRMAXDIST, pairMaxDist_.getText());
      settings_.putString(PAIRMAXSIGMA, pairMaxSigma_.getText());
      settings_.putString(PAIRNRQUADRANTS, pairNrQuadrants_.getText());
      settings_.putString(LOADTSFDIR, loadTSFDir_);
      settings_.putInteger(RENDERMAG, 
              visualizationMagnification_.getSelectedIndex());
      settings_.putString(PAIRSMAXDISTANCE, pairsMaxDistanceField_.getText());
       
      TableColumnModel cm = mainTable_.getColumnModel();
      settings_.putInteger(COL0WIDTH, cm.getColumn(0).getWidth());
      settings_.putInteger(COL1WIDTH, cm.getColumn(1).getWidth());
      settings_.putInteger(COL2WIDTH, cm.getColumn(2).getWidth());
      settings_.putInteger(COL3WIDTH, cm.getColumn(3).getWidth());
      settings_.putInteger(COL4WIDTH, cm.getColumn(4).getWidth());
      settings_.putInteger(COL5WIDTH, cm.getColumn(5).getWidth());
      settings_.putInteger(COL6WIDTH, cm.getColumn(6).getWidth());

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
         String data = "Name: " + rowData.getName() + "\n" +
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
         if (!rowData.isTrack_) {
            data += "\nHas Z info: " + rowData.hasZ_;
         }
         if (rowData.hasZ_) {
            data += "\nMinZ: " + String.format("%.2f",rowData.minZ_) + "\n";
            data += "MaxZ: " + String.format("%.2f",rowData.maxZ_);
         }
                    
         if (rowData.isTrack_) {
            ArrayList<Point2D.Double> xyList = ListUtils.spotListToPointList(rowData.spotList_);
            Point2D.Double avg = ListUtils.avgXYList(xyList);
            Point2D.Double stdDev = ListUtils.stdDevsXYList(xyList, avg);
            
            data += "\n" + 
                    "Average X: " + avg.x + "\n" +
                    "StdDev X: " + stdDev.x + "\n" + 
                    "Average Y: " + avg.y + "\n" +
                    "StdDev Y: " + stdDev.y + "\n" +
                    "StdDev: " + rowData.std_;           
         }
         
         TextWindow tw = new TextWindow("Info for " + rowData.getName(), data, 300, 300);
         tw.setVisible(true);
       }
       else {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select a dataset first");
      }
   }

   /**
    * Renders dataset 
    * 
    * @param evt 
    */
   private void renderButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      final int row = mainTable_.getSelectedRowSorted();
      if (row < 0) {
         JOptionPane.showMessageDialog(this, "Please select a dataset to render");
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
                  String ttmp = rowData.getName();
                  if (rowData.getName().contains(fsep)) {
                     ttmp = rowData.getName().substring(rowData.getName().lastIndexOf(fsep) + 1);
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
         JOptionPane.showMessageDialog(this, "Please select one or more datasets to plot");
      } else {
         RowData[] myRows = new RowData[rows.length];
         // TODO: check that these are tracks 
         for (int i = 0; i < rows.length; i++) {
            myRows[i] = mainTableModel_.getRow(rows[i]);
         }
         TrackPlotter.plotData(myRows, 
                 plotComboBox_.getSelectedIndex(), 
                 logLogCheckBox_.isSelected(), 
                 powerSpectrumCheckBox_.isSelected(),
                 this);
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
         JOptionPane.showMessageDialog(this, 
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
         
         // for each frame in the collection, calculate the average
         for (int i : allData.keySet()) {
            List<SpotData> frameList = allData.get(i);
            TrackAnalysisData tad = new TrackAnalysisData();
            tad.frame = i;
            tad.n = frameList.size();
            SpotData avgFrame = new SpotData(frameList.get(0));
            
            ArrayList<Point2D.Double> xyPoints = ListUtils.spotListToPointList(frameList);
            Point2D.Double listAvg = ListUtils.avgXYList(xyPoints);
            Point2D.Double stdDev = ListUtils.stdDevsXYList(xyPoints, listAvg);
            tad.xAvg = listAvg.x;
            tad.yAvg = listAvg.y;
            tad.xStdDev = stdDev.x;
            tad.yStdDev = stdDev.y;
             
            avgFrame.setXCenter(listAvg.x);
            avgFrame.setYCenter(listAvg.y);
            
            transformedResultList.add(avgFrame);
         }

         // Add transformed data to data overview window
         RowData rowData = myRows[0];
         RowData.Builder builder = rowData.copy();
         builder.setName(rowData.getName() + " Average").setDisplayWindow(null).
                 setColColorRef("").setSpotList(transformedResultList).
                 setIsTrack(true).setHasZ(false);
         addSpotData(builder);
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

         RowData.Builder builder = source.copy();
         builder.setName(source.getName() + " Subtracted").setNrPositions(1).
                 setSpotList(transformedResultList);
         addSpotData(builder);
         
      } catch (IndexOutOfBoundsException iobe) {
         JOptionPane.showMessageDialog(this, "Data sets differ in Size");
      }

   }

   private void mathButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int[] rows = new int[mainTable_.getRowCount()];

      for (int i = 0; i < rows.length; i++) {
         Integer j =  (Integer) mainTable_.getValueAt(mainTable_.convertRowIndexToModel(i), 0);
         rows[i] = j;
      }

      MathForm mf = new MathForm(studio_.getUserProfile(), rows, rows);

      mf.setVisible(true);
   }
   
   public String getSummaryOfSelectedTracks(boolean header) {
      if (header) {
         return "name\tn\tNr of Spots\tCh.\tstd\tnrPhotons";
      }
      
      class Data {
         int n = 0;
         long numberOfSpots = 0;;
         Integer channelNr = 0;
         Double nrPhotons = 0.0;
         Double std = 0.0;
      }
      
      final int rows[] = mainTable_.getSelectedRowsSorted();
      Data data = new Data();
      for (int row : rows) {
         final RowData rowData = mainTableModel_.getRow(row);
         if (rowData.isTrack_) {
            data.n += 1;
            data.numberOfSpots += rowData.maxNrSpots_;
            data.channelNr += rowData.spotList_.get(0).getChannel();
            data.std += rowData.std_;
            data.nrPhotons += rowData.totalNrPhotons_;
         } 
      }
      Data result = new Data();
      result.n = data.n;
      result.numberOfSpots = data.numberOfSpots / data.n;
      result.channelNr = data.channelNr / data.n;
      result.std = data.std / data.n;
      result.nrPhotons = data.nrPhotons / data.n;
      
      String output = mainTableModel_.getRow(rows[0]).getName() + "\t" +
              result.n + "\t" + result.numberOfSpots + "\t" + result.channelNr +
              "\t" + result.std + "\t" +  result.nrPhotons;
      
      return output;

   }
   
   public String getSelectedTracks(boolean header) {
      if (header) {
         return "name\tNr of Spots\tCh.\tstd\tNr of Photons\tAvg nr Photons\tStdDev Nr Photons\n";
      }
      
      String output = "";
      final int rows[] = mainTable_.getSelectedRowsSorted();
      for (int row : rows) {
         final RowData rowData = mainTableModel_.getRow(row);
         if (rowData.isTrack_) {
            List<Double> photonNrs = new ArrayList<Double>();
            for (SpotData spot : rowData.spotList_) {
               photonNrs.add(spot.getIntensity());
            }
            double avgNrPhotons = ListUtils.listAvg(photonNrs);
            output += rowData.getName() + "\t" + 
                    rowData.maxNrSpots_ + "\t" +
                    rowData.spotList_.get(0).getChannel() + "\t" +
                    rowData.std_ + "\t" + 
                    rowData.totalNrPhotons_ + "\t" + 
                    avgNrPhotons + "\t" +
                    ListUtils.listStdDev(photonNrs, avgNrPhotons) + "\n";
         } 
      }
      
      return output;

   }
   
   /**
    * For the selected tracks, average the various stdDev estimates
    * and place on the clipboard
    * @param header when true only return the headers (explaining the data)
    * @return Header or values
    */
   public String getSummarizedTrackData(boolean header) {
      if (header) {
         return "Avg.Sigma\tAvg.Mortenson Sigmas\tAvg.integral Sigma\tAvg.Intensity\t" +
                 "Avg.AptIntensity\tAvg.Background\tAvg.AptBackground\tAvg.Width";
      }
      final int rows[] = mainTable_.getSelectedRowsSorted();
      List<Double> sigmas = new ArrayList<Double>();
      List<Double> mSigmas = new ArrayList<Double>();
      List<Double> iSigmas = new ArrayList<Double>();
      List<Double> intensities = new ArrayList<Double>();
      List<Double> aptIntensities = new ArrayList<Double>();
      List<Double> backgrounds = new ArrayList<Double>();
      List<Double> aptBackgrounds = new ArrayList<Double>();
      List<Double> widths = new ArrayList<Double>();
      for (int row : rows) {
         final RowData rowData = mainTableModel_.getRow(row);
         for (SpotData spotData : rowData.spotList_) {
            sigmas.add(spotData.getSigma());
            if (spotData.getValue(SpotData.Keys.MSIGMA) != null && 
                  !spotData.getValue(SpotData.Keys.MSIGMA).equals(Double.NaN) ) {
               mSigmas.add(spotData.getValue(SpotData.Keys.MSIGMA));
            }
            if (spotData.getValue(SpotData.Keys.INTEGRALSIGMA) != null &&
                  !spotData.getValue(SpotData.Keys.INTEGRALSIGMA).equals(Double.NaN) ) {
               iSigmas.add(spotData.getValue(SpotData.Keys.INTEGRALSIGMA));
            }
            intensities.add(spotData.getIntensity());
            if (spotData.getValue(SpotData.Keys.APERTUREINTENSITY) != null &&
                    !spotData.getValue(SpotData.Keys.APERTUREINTENSITY).equals(Double.NaN) ) {
               aptIntensities.add(spotData.getValue(SpotData.Keys.APERTUREINTENSITY));
            }
            backgrounds.add(spotData.getBackground());
            Double tmp = spotData.getValue(SpotData.Keys.APERTUREBACKGROUND);
            if (tmp != null && !tmp.equals(Double.NaN)) {
               aptBackgrounds.add(tmp);
            }
            widths.add(spotData.getWidth());
         }
      }
      
      String output = ListUtils.listAvg(sigmas) + 
              "\t" + ListUtils.listAvg(mSigmas) +
              "\t" + ListUtils.listAvg(iSigmas) + 
              "\t" + ListUtils.listAvg(intensities) + 
              "\t" + ListUtils.listAvg(aptIntensities) +
              "\t" + ListUtils.listAvg(backgrounds) +
              "\t" + ListUtils.listAvg(aptBackgrounds) + 
              "\t" + ListUtils.listAvg(widths);
      
      return output;
   }
   
   /**
    * Returns a data describing the distances between spots in input tracks
    * This is used to determine the experimental sigma of the fitted positions
    * The tracks should be of stationary spots, the function determines the 
    * distance between a spot and the corresponding spots in all other tracks.
    * 
    * @param header
    * @return 
    */
   public String getInterTrackDistances(boolean header) {
      
      if (header) {
         return "Spot ID 1\tSpot ID 2\tMeasured Dist. Std. Dev\t" +
                 "Predicted Dist. Std Dev. (Integral Aperture)\t" +
                 "Measured Dist. Std. Dev. (direct)";
      }
      
      final double cutoffPercentage = 0.75;
      
      final double firstHalfWaitInProgressBar = 0.5;
         
      final int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 2) {
         ReportingUtils.showError("Need at least 2 tracks to calculate inter track distance statistics");
         return "";
      }
      
      // First go through all tracks and calculate the variance in the distances
      // to all other spots.  Then throw out the top cutoffPercentage, since we have
      // noticed that there are usually badly behaving spots.
      // Go through the cleaned up list and report the average measured 
      // and predicted sigmas
      
      Map<Integer, Double> variancesMap = new HashMap<Integer, Double>();
      final AtomicInteger progressCounter = new AtomicInteger(1);
      for (int row : rows) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               IJ.showProgress((double) progressCounter.getAndIncrement() / 
                       (double) rows.length * firstHalfWaitInProgressBar);
         }
         });
         final RowData rowData = mainTableModel_.getRow(row);
         List<Double> variances = new ArrayList<Double>();
         for (int sRow : rows) {
            if (row != sRow) {
               // calculate the distance averaged over all frames 
               // between between our track and this particular one
               final List<Double> distsSqr = new ArrayList<Double>();
               final RowData sRowData = mainTableModel_.getRow(sRow);
               Map<Integer, List<SpotData>> sSpotListByFrame = sRowData.getSpotListIndexedByFrame();
               for (SpotData spotData : rowData.spotList_) {
                  SpotData sSpotData = null;
                  if (sSpotListByFrame.get(spotData.getFrame()) != null) {
                     sSpotData = (SpotData) sSpotListByFrame.get(spotData.getFrame()).get(0);
                  }
                  if (sSpotData != null) {
                     double xDistance = spotData.getXCenter() - sSpotData.getXCenter();
                     double yDistance = spotData.getYCenter() - sSpotData.getYCenter();
                     distsSqr.add(xDistance * xDistance + yDistance * yDistance);
                  }
               }
               variances.add(ListUtils.listStdDev(distsSqr));
            }
         }
         // Calculate the std. Dev. of all measured distances.
         variancesMap.put(row, ListUtils.listAvg(variances) );
      }
      Map<Integer, Double> sortedStdDevMap = MapUtils.sortByValue(variancesMap);
      final List<Integer> cleanedRows = new ArrayList<Integer>();
      Set<Map.Entry<Integer, Double>> entrySet = sortedStdDevMap.entrySet();
      Iterator<Map.Entry<Integer, Double>> eIterator = entrySet.iterator();
      int cutOff = (int) (cutoffPercentage * (double) sortedStdDevMap.size());
      while (eIterator.hasNext() && cleanedRows.size() < cutOff) {
         Map.Entry<Integer, Double> next = eIterator.next();
         cleanedRows.add(next.getKey());
      }
      
      
      StringBuilder  output = new StringBuilder(10000);
      List<Double> measuredSigmas = new ArrayList<Double>();
      List<Double> predictedSigmas = new ArrayList<Double>();
      
      progressCounter.set(1);
      for (int row : cleanedRows) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               IJ.showProgress(firstHalfWaitInProgressBar + 
                       (double) progressCounter.getAndIncrement() / 
                               (double)cleanedRows.size() * ( 1 - firstHalfWaitInProgressBar) );
         }
         });
         final RowData rowData = mainTableModel_.getRow(row);
         for (int sRow : cleanedRows) {
            if (row != sRow) {
               List<Double> xDists = new ArrayList<Double>();
               List<Double> yDists = new ArrayList<Double>();
               List<Double> predictedDistStdDevs = new ArrayList<Double>();
               final RowData sRowData = mainTableModel_.getRow(sRow);
               Map<Integer, List<SpotData>> sSpotListByFrame = sRowData.getSpotListIndexedByFrame();
               for (SpotData spotData : rowData.spotList_) {
                  SpotData sSpotData = null;
                  if (sSpotListByFrame.get(spotData.getFrame()) != null) {
                     sSpotData = (SpotData) sSpotListByFrame.get(spotData.getFrame()).get(0);
                  }
                  if (sSpotData != null) {
                     double xDist = spotData.getXCenter() - sSpotData.getXCenter();
                     xDists.add(xDist);
                     double yDist = spotData.getYCenter() - sSpotData.getYCenter();
                     yDists.add(yDist);
                     double pStdDevA = spotData.getValue(SpotData.Keys.INTEGRALAPERTURESIGMA);
                     double pStdDevB = sSpotData.getValue(SpotData.Keys.INTEGRALAPERTURESIGMA);
                     predictedDistStdDevs.add(Math.sqrt(pStdDevA * pStdDevA + pStdDevB * pStdDevB));
                  }
               }
               // Propagate errors in x and y
               double xAvg = ListUtils.listAvg(xDists);
               double yAvg = ListUtils.listAvg(yDists);
               double xStdDev = ListUtils.listStdDev(xDists, xAvg);
               double yStdDev = ListUtils.listStdDev(yDists, yAvg);
               double measuredSigma = 1 / Math.sqrt(xAvg * xAvg + yAvg * yAvg) *
                       Math.sqrt(xStdDev * xStdDev * xAvg * xAvg + 
                               yStdDev * yStdDev * yAvg * yAvg);
               measuredSigmas.add(measuredSigma);
               double predictedDistStdDev = ListUtils.listAvg(predictedDistStdDevs);
               predictedSigmas.add(predictedDistStdDev);
            
               output.append(rowData.ID_).append("\t").append(sRowData.ID_).
                       append("\t").append( measuredSigma ). 
                       append("\t").append(predictedDistStdDev).
                       append("\n");
            }
         }

      }
      
      StringBuilder finalOutput = new StringBuilder(output.length() + 1000);
      double measuredSigmaAverage = ListUtils.listAvg(measuredSigmas);
      double predictedSigmaAverage = ListUtils.listAvg(predictedSigmas);
      finalOutput.append("\tAverage:\t").append(measuredSigmaAverage).
              append("\t").append(predictedSigmaAverage).append("\n");
      finalOutput.append("\tStd. Dev.:\t").
              append(ListUtils.listStdDev(measuredSigmas, measuredSigmaAverage)).
              append("\t").
              append(ListUtils.listStdDev(predictedSigmas, predictedSigmaAverage)).
              append("\n");
      finalOutput.append(output);
      
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            IJ.showStatus("Copied Inter Track distance data");
         }
      });

      return finalOutput.toString();
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
               SpotLinker.link(rowData, maxDistance);
            }
         }
      };

      (new Thread(doWorkRunnable)).start();
   }

   private void straightenTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(this,
                 "Please select one or more datasets to straighten");
      } else {
         for (int row : rows) {
            RowData r = mainTableModel_.getRow(row);
            TrackOperator.straightenTrack(r);
         }
      }
   }

   private void centerTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(this,
                 "Please select one or more datasets to center");
      } else {
         int start = mainTable_.getRowCount();
         for (int row : rows) {
            TrackOperator.centerTrack(mainTableModel_.getRow(row));
         }
         int end = mainTable_.getRowCount();
         mainTable_.setRowSelectionInterval(start, end - 1);
      }
   }


   private void zCalibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      int rows[] = mainTable_.getSelectedRowsSorted();
      if (rows.length != 1) {
         JOptionPane.showMessageDialog(this,
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
         JOptionPane.showMessageDialog(this,
                 "Please select one or more datasets for sub-ranging");
         return;
      }

      range_ = (String) JOptionPane.showInputDialog(this, "Provide desired range of Frame numbers\n"
              + "e.g. \"7-50\"", "SubRange", JOptionPane.PLAIN_MESSAGE, null, null, range_);
      ArrayList<Long> desiredFrameNumbers = new ArrayList<Long>(
              (int) mainTableModel_.getRow(rows[0]).maxNrSpots_);
      String[] parts = range_.split(",");
      try {
         for (String part : parts) {
            String[] tokens = part.split("-");
            for (long i = Integer.parseInt(tokens[0].trim());
                    i <= Integer.parseInt(tokens[1].trim()); i++) {
               desiredFrameNumbers.add(i);
            }
         }
      } catch (NumberFormatException ex) {
         ReportingUtils.showError(ex, "Could not parse input");
      }

      final ArrayList<Long> desiredFrameNumbersCopy = desiredFrameNumbers;          
      
      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            if (rows.length > 0) {
               for (int row : rows) {

                  RowData.Builder output = mainTableModel_.getRow(row).copy();
                  List<SpotData> outList = new ArrayList<SpotData>();
                  output.setSpotList(outList);

                  List<SpotData> spots = mainTableModel_.getRow(row).spotList_;

                  Collections.sort(desiredFrameNumbersCopy);
                  for (SpotData spot : mainTableModel_.getRow(row).spotList_) {
                     if (desiredFrameNumbersCopy.contains((long) spot.getFrame())) {
                        outList.add(new SpotData(spot));
                     }
                  }
                  output.setMaxNrSpots(outList.size());
                  addSpotData(output);
               }

            }
         }
      };
      doWorkRunnable.run();

   }

   private void combine(final boolean modifyPositions) {
      try {
         final int[] rows = mainTable_.getSelectedRowsSorted();
         
         if (rows == null || rows.length < 2) {
            JOptionPane.showMessageDialog(this, 
                    "Please select two or more datasets to combine");
            return;
         }               
         semaphore_.acquire();

         Runnable doWorkRunnable = new Runnable() {
            @Override
            public void run() {

               List<SpotData> newData =
                       Collections.synchronizedList(new ArrayList<SpotData>());
               int positionOffset = 0;
               for (int i = 0; i < rows.length; i++) {
                  RowData rowData = mainTableModel_.getRow(rows[i]);
                  for (SpotData gs : rowData.spotList_) {
                     if (modifyPositions) {
                        SpotData newGs = new SpotData(gs);
                        newGs.setPosition(gs.getPosition() + positionOffset);
                        newData.add(newGs);
                     } else {
                        newData.add(gs);
                     }
                     
                  }
                  positionOffset += rowData.nrPositions_;
               }

               // Add transformed data to data overview window
               // for now, copy header of first data set
               RowData rowData = mainTableModel_.getRow(rows[0]);
               RowData.Builder builder = rowData.copy();
               builder.setName(rowData.getName() + "-Combined").
                       setColColorRef(reference2CName_.getText()).
                       setSpotList(newData).setIsTrack(false).
                       setHasZ(false).setMinZ(0.0).setMaxZ(0.0).
                       setNrPositions(positionOffset);
               addSpotData(builder);

               semaphore_.release();
            }
         };

         (new Thread(doWorkRunnable)).start();
      } catch (InterruptedException ex) {
         ReportingUtils.showError(ex, "Data set combiner got interupted");
      }
      
   }

   private void listButton_1ActionPerformed(java.awt.event.ActionEvent evt) {
      PairDisplayForm pdf = new PairDisplayForm(studio_);
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
            rt.addValue(Terms.FRAME, ResultsTable.d2s(gd.getFrame(), 0));
            rt.addValue(Terms.SLICE, ResultsTable.d2s(gd.getSlice(), 0));
            rt.addValue(Terms.CHANNEL, ResultsTable.d2s(gd.getChannel(), 0));
            rt.addValue(Terms.POSITION, ResultsTable.d2s(gd.getPosition(), 0));
            rt.addValue(Terms.XPIX, ResultsTable.d2s(gd.getX(), 0));
            rt.addValue(Terms.YPIX, ResultsTable.d2s(gd.getY(), 0));
            if (rowData.coordinate_ == Coordinates.NM) {
               rt.addValue(Terms.XNM, gd.getXCenter());
               rt.addValue(Terms.YNM, gd.getYCenter());
               if (rowData.hasZ_) {
                  rt.addValue(Terms.ZNM, gd.getZCenter());
               }
            } else if (rowData.coordinate_ == Coordinates.PIXELS) {
               rt.addValue(Terms.XFITPIX, gd.getXCenter());
               rt.addValue(Terms.YFITPIX, gd.getYCenter());
            }
            //rt.addValue(Terms.INT, gd.getIntensity());
            //rt.addValue(Terms.BACKGROUND, gd.getBackground());
            if (shape >= 1) {
               rt.addValue(Terms.WIDTH, gd.getWidth());
            }
            if (shape >= 2) {
               rt.addValue(Terms.A, gd.getA());
            }
            if (shape == 3) {
               rt.addValue(Terms.THETA, gd.getTheta());
            }
            //rt.addValue(Terms.SIGMA, ResultsTable.d2s(gd.getSigma(), 2));
            String[] gdKeys = {SpotData.Keys.APERTUREINTENSITY,
               SpotData.Keys.APERTUREBACKGROUND};
            for (String key : gdKeys) {
               if (gd.hasKey(key)) {
                  rt.addValue(key, gd.getValue(key));
               }
            }
            if (gd.hasKey(SpotData.Keys.INTEGRALAPERTURESIGMA)) {
               String key = SpotData.Keys.INTEGRALAPERTURESIGMA;
               rt.addValue(key, ResultsTable.d2s(gd.getValue(key), 2));
            }
         }
      }
      
      TextPanel tp;
      
      String name = "Spots from: " + rowData.getName();
      rt.show(name);
      ImagePlus siPlus = ij.WindowManager.getImage(rowData.title_);
      // Attach listener to TextPanel
      Frame frame = WindowManager.getFrame(name);
      if (frame != null && frame instanceof TextWindow && siPlus != null) {
         final TextWindow win = (TextWindow) frame;
         win.setSize(studio_.profile().getInt(DataCollectionForm.class, "ResWidth", 550),
                 studio_.profile().getInt(DataCollectionForm.class, "ResHeight", 300));
         win.setLocation(studio_.profile().getInt(DataCollectionForm.class, "ResXPos", 100),
                 studio_.profile().getInt(DataCollectionForm.class, "ResYPos", 100));
         win.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent we) {
               }

            @Override
            public void windowClosing(WindowEvent we) {
               studio_.profile().setInt(DataCollectionForm.class, "ResWidth", win.getWidth());
               studio_.profile().setInt(DataCollectionForm.class, "ResHeight", win.getHeight());
               studio_.profile().setInt(DataCollectionForm.class, "ResXPos", win.getX());
               studio_.profile().setInt(DataCollectionForm.class, "ResYPos", win.getY());
            }

            @Override
            public void windowClosed(WindowEvent we) {
               }

            @Override
            public void windowIconified(WindowEvent we) {
               }

            @Override
            public void windowDeiconified(WindowEvent we) {
               }

            @Override
            public void windowActivated(WindowEvent we) {
               }

            @Override
            public void windowDeactivated(WindowEvent we) {
               }
         });
         
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
         
         ResultsTableListener myk = new ResultsTableListener(rowData.dw_, siPlus, 
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
         JOptionPane.showMessageDialog(this, "Please select a dataset to Color correct");
         return;
      }
      if (c2t_ == null) {
         JOptionPane.showMessageDialog(this, 
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
            RowData.Builder builder = rowData.copy();
            builder.setName(rowData.getName() + "-CC-" + reference2CName_.getText() + "-"
                    + method2CBox_.getSelectedItem()).
                    setColColorRef(reference2CName_.getText()).
                    setSpotList(correctedData);
            addSpotData(builder);
  
            semaphore_.release();
         }
      };

      (new Thread(doWorkRunnable)).start();
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
         JOptionPane.showMessageDialog(this, 
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
            
      Map<Integer, List<SpotData>> frameIndexSpotList = 
              rd.getSpotListIndexedByFrame();
      
      final int nrImages = rd.nrSlices_;
     
      int frameNr = 0;
      while (frameNr < nrImages) {
         List<SpotData> frameSpots = frameIndexSpotList.get(frameNr);
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
         JOptionPane.showMessageDialog(this,
                 "Please select a dataset to filter");
         return;
      }
      
      for (int i = 0; i < rows.length; i++) {
         RowData rowData = mainTableModel_.getRow(rows[i]);
         PairFilter.filter(rowData, maxDistance, deviationMax, nrQuadrants);
      }
   }
   
   public void filterNow_ActionPerformed() {
            
      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            
            SpotDataFilter sdf = new SpotDataFilter();  
            try {
               if (filterIntensityCheckBox_.isSelected()) {
                  sdf.setIntensity(true,
                       NumberUtils.displayStringToDouble(intensityMin_.getText()),
                       NumberUtils.displayStringToDouble(intensityMax_.getText()) );
               }
               if (filterSigmaCheckBox_.isSelected()) {
                  sdf.setSigma(true, 
                       NumberUtils.displayStringToDouble(sigmaMin_.getText()), 
                       NumberUtils.displayStringToDouble(sigmaMax_.getText()) );
               }
               filterSpots(sdf);
            } catch (ParseException ex) {
              ReportingUtils.showError("Filter inputs are not all numeric");
            }
         }
      };
      doWorkRunnable.run();
   }
   
    public void pairFilterNow_ActionPerformed() {
            
      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            try {
               double maxDist = NumberUtils.displayStringToDouble(
                       pairMaxDist_.getText());
               double maxStd = NumberUtils.displayStringToDouble(
                        pairMaxSigma_.getText());
               int nrQuadrants = (int) Math.round(NumberUtils.displayStringToDouble(
                        pairNrQuadrants_.getText()));
               filterPairs(maxDist, maxStd, nrQuadrants);
            } catch (ParseException ex) {
              ReportingUtils.showError("Filter inputs are not all numeric");
            }
         }
      };
      doWorkRunnable.run();
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
         RowData.Builder builder = rowData.copy();
         builder.setName(rowData.getName() + "-Filtered").setMaxNrSpots(filteredData.size()).
                 setSpotList(filteredData);
         addSpotData(builder);
      }
   }

   public void setPieceWiseAffineParameters(int maxControlPoints, double maxDistance) {
      if (c2t_ != null) {
         c2t_.setPieceWiseAffineMaxControlPoints(maxControlPoints);
         c2t_.setPieceWiseAffineMaxDistance(maxDistance);
      }
   }   

}