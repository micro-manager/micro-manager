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
 */

package edu.valelab.GaussianFit;

import edu.valelab.GaussianFit.spotOperations.NearestPointGsSpotPair;
import edu.valelab.GaussianFit.spotOperations.NearestPoint2D;
import edu.valelab.GaussianFit.utils.DisplayUtils;
import edu.valelab.GaussianFit.data.GsSpotPair;
import edu.valelab.GaussianFit.data.SpotData;
import edu.valelab.GaussianFit.utils.GaussianUtils;
import edu.valelab.GaussianFit.fitting.ZCalibrator;
import edu.valelab.GaussianFit.data.LoadAndSave;
import edu.valelab.GaussianFit.spotOperations.SpotLinker;
import edu.valelab.GaussianFit.data.RowData;
import edu.valelab.GaussianFit.utils.ListUtils;
import edu.valelab.GaussianFit.utils.ReportingUtils;
import edu.valelab.GaussianFit.utils.NumberUtils;
import edu.valelab.GaussianFit.utils.FileDialogs;
import edu.valelab.GaussianFit.utils.FileDialogs.FileType;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageWindow;
import ij.gui.MessageDialog;
import ij.gui.Roi;
import ij.gui.StackWindow;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
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
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.stat.StatUtils;
import org.jfree.data.xy.XYSeries;


/**
 *
 * @author Nico Stuurman
 */
public class DataCollectionForm extends javax.swing.JFrame {
   AbstractTableModel myTableModel_;
   private final String[] columnNames_ = {"ID", "Image", "Nr of spots", 
      "2C Reference", "stdX", "stdY", "nrPhotons"};
   private final String[] plotModes_ = {"t-X", "t-Y", "t-dist", "t-Int", "X-Y"};
   private final String[] renderModes_ = {"Points", "Gaussian", "Norm. Gaussian"};
   private final String[] renderSizes_  = {"1x", "2x", "4x", "8x", "16x", "32x", "64x", "128x"};
   public final static String EXTENSION = ".tsf";
   
   // Prefs
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
   private static final String COL0Width = "Col0Width";  
   private static final String COL1Width = "Col1Width";
   private static final String COL2Width = "Col2Width";
   private static final String COL3Width = "Col3Width";
   private static final String COL4Width = "Col4Width";
   private static final String COL5Width = "Col5Width";
   private static final String COL6Width = "Col6Width";
   
   private static final int OK = 0;
   private static final int FAILEDDONOTINFORM = 1;
   private static final int FAILEDDOINFORM = 2;
   
   private Preferences prefs_;
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
    * @return  Affine transform object calcualed by the Coordinate Mapper
    */
   public AffineTransform getAffineTransform() {
      if (c2t_ == null)
         return null;
      return c2t_.getAffineTransform();
   }
  
   
   public static DataCollectionForm instance_ = null;
   
   // public since it is used in MathForm.  
   // TODO: make this private
   public ArrayList<RowData> rowData_;
   
   public enum Coordinates {NM, PIXELS};
   public enum PlotMode {X, Y, INT};
   

   /**
    * Implement this class as a singleton
    *
    * @return the form
    */
   public static DataCollectionForm getInstance() {
      if (instance_ == null) {
         instance_ =  new DataCollectionForm();
         // MMStudio.getInstance().addMMBackgroundListener(instance_);
      }
      return instance_;
   }

   /** 
    * Creates new form DataCollectionForm
    */
   private DataCollectionForm() {

      rowData_ = new ArrayList<RowData>();

      myTableModel_ = new AbstractTableModel() {
             @Override
          public String getColumnName(int col) {
              return columnNames_[col];
          }
             @Override
          public int getRowCount() {
             if (rowData_ == null)
                return 0;
             return rowData_.size();
          }
            @Override
          public int getColumnCount() { 
             return columnNames_.length;
          }
            @Override
          public Object getValueAt(int row, int col) {
             if (col == 0 && rowData_ != null)
                return rowData_.get(row).ID_;
             else if (col == 1 && rowData_ != null)
                return rowData_.get(row).name_;
             else if (col == 2)
                return rowData_.get(row).spotList_.size();
             else if (col == 3)
                return rowData_.get(row).colCorrRef_;
             else if (col == 4)
                if (rowData_.get(row).isTrack_)
                  return String.format("%.2f", rowData_.get(row).stdX_);
                else return null;
             else if (col == 5)
                if (rowData_.get(row).isTrack_)
                  return String.format("%.2f", rowData_.get(row).stdY_);
                else 
                   return null;
             else if (col == 6)
                if (rowData_.get(row).isTrack_)
                  return String.format("%.2f", rowData_.get(row).totalNrPhotons_);
                else 
                   return null;
             else 
                return getColumnName(col);
             
          }
            @Override
          public boolean isCellEditable(int row, int col) {
            return col == 1;
          }
             @Override
          public void setValueAt(Object value, int row, int col) {
             if (col == 1)
                rowData_.get(row).name_ = (String) value;
             fireTableCellUpdated(row, col);
          }
       };

       initComponents();
       referenceName_.setText("  ");
       plotComboBox_.setModel(new javax.swing.DefaultComboBoxModel(plotModes_));
       visualizationModel_.setModel(new javax.swing.DefaultComboBoxModel(renderModes_));
       visualizationMagnification_.setModel(new javax.swing.DefaultComboBoxModel(renderSizes_));
       jScrollPane1_.setName("Gaussian Spot Fitting Data Sets");      
              
       if (prefs_ == null)
          prefs_ = Preferences.userNodeForPackage(this.getClass());
       setBounds(prefs_.getInt(FRAMEXPOS, 50), prefs_.getInt(FRAMEYPOS, 100),
             prefs_.getInt(FRAMEWIDTH, 800), prefs_.getInt(FRAMEHEIGHT, 250));
       filterSigmaCheckBox_.setSelected(prefs_.getBoolean(USESIGMA, false));
       sigmaMin_.setText(prefs_.get(SIGMAMIN, "0.0"));
       sigmaMax_.setText(prefs_.get(SIGMAMAX, "20.0"));
       filterIntensityCheckBox_.setSelected(prefs_.getBoolean(USEINT, false));
       intensityMin_.setText(prefs_.get(INTMIN, "0.0"));
       intensityMax_.setText(prefs_.get(INTMAX, "20000"));
       loadTSFDir_ = prefs_.get(LOADTSFDIR, "");
       visualizationMagnification_.setSelectedIndex(prefs_.getInt(RENDERMAG, 0));
       pairsMaxDistanceField_.setText(prefs_.get(PAIRSMAXDISTANCE, "500"));
       method2CBox_.setSelectedItem(prefs_.get(METHOD2C, "LWM"));
       
       jTable1_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
       TableColumnModel cm = jTable1_.getColumnModel();
       cm.getColumn(0).setPreferredWidth(prefs_.getInt(COL0Width, 25));
       cm.getColumn(1).setPreferredWidth(prefs_.getInt(COL1Width, 300));
       cm.getColumn(2).setPreferredWidth(prefs_.getInt(COL2Width, 150));
       cm.getColumn(3).setPreferredWidth(prefs_.getInt(COL3Width, 75));
       cm.getColumn(4).setPreferredWidth(prefs_.getInt(COL4Width, 75));
       cm.getColumn(5).setPreferredWidth(prefs_.getInt(COL5Width, 75));
       cm.getColumn(6).setPreferredWidth(prefs_.getInt(COL6Width, 75));
       
       // Drag and Drop support for file loading
       this.setTransferHandler(new TransferHandler() {

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

      setVisible(true);
   }


   /**
    * Adds a spot data set to the form
    *
    *
    * @param name
    * @param title
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
      RowData newRow = new RowData(name, title, colCorrRef, width, height, 
              pixelSizeUm, zStackStepSizeNm, 
              shape, halfSize, nrChannels, nrFrames, nrSlices, nrPositions, 
              maxNrSpots, spotList, timePoints, isTrack, coordinate, 
              hasZ, minZ, maxZ);
      addSpotData (newRow);
   }
   
   public void addSpotData(RowData newRow) {
      rowData_.add(newRow);
      myTableModel_.fireTableRowsInserted(rowData_.size()-1, rowData_.size());
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
   public RowData getDataSet(int ID) {
      int i=0;
      while (i < rowData_.size()) {
         if (rowData_.get(i).ID_ == ID)
            return rowData_.get(i);
      }

      return null;
   }

   /**
    * This method is called from within the constructor to
    * initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is
    * always regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jPanel1 = new javax.swing.JPanel();
      jLabel7 = new javax.swing.JLabel();
      intensityMax_ = new javax.swing.JTextField();
      IntLabel2 = new javax.swing.JLabel();
      SigmaLabel3 = new javax.swing.JLabel();
      sigmaMax_ = new javax.swing.JTextField();
      visualizationMagnification_ = new javax.swing.JComboBox();
      visualizationModel_ = new javax.swing.JComboBox();
      sigmaMin_ = new javax.swing.JTextField();
      intensityMin_ = new javax.swing.JTextField();
      filterIntensityCheckBox_ = new javax.swing.JCheckBox();
      filterSigmaCheckBox_ = new javax.swing.JCheckBox();
      jLabel1 = new javax.swing.JLabel();
      renderButton_ = new javax.swing.JButton();
      jLabel4 = new javax.swing.JLabel();
      zCalibrateButton_ = new javax.swing.JButton();
      zCalibrationLabel_ = new javax.swing.JLabel();
      unjitterButton_ = new javax.swing.JButton();
      linkButton_ = new javax.swing.JButton();
      jSeparator4 = new javax.swing.JSeparator();
      centerTrackButton_ = new javax.swing.JButton();
      straightenTrackButton_ = new javax.swing.JButton();
      logLogCheckBox_ = new javax.swing.JCheckBox();
      plotComboBox_ = new javax.swing.JComboBox();
      SubRange = new javax.swing.JButton();
      mathButton_ = new javax.swing.JButton();
      averageTrackButton_ = new javax.swing.JButton();
      powerSpectrumCheckBox_ = new javax.swing.JCheckBox();
      plotButton_ = new javax.swing.JButton();
      jLabel6 = new javax.swing.JLabel();
      jSeparator2 = new javax.swing.JSeparator();
      SigmaLabel2 = new javax.swing.JLabel();
      pairsMaxDistanceField_ = new javax.swing.JTextField();
      referenceName_ = new javax.swing.JLabel();
      pairsButton = new javax.swing.JButton();
      listButton_ = new javax.swing.JButton();
      c2CorrectButton = new javax.swing.JButton();
      method2CBox_ = new javax.swing.JComboBox();
      c2StandardButton = new javax.swing.JButton();
      jLabel5 = new javax.swing.JLabel();
      jSeparator3 = new javax.swing.JSeparator();
      infoButton_ = new javax.swing.JButton();
      removeButton = new javax.swing.JButton();
      saveFormatBox_ = new javax.swing.JComboBox();
      saveButton = new javax.swing.JButton();
      loadButton = new javax.swing.JButton();
      showButton_ = new javax.swing.JButton();
      jLabel2 = new javax.swing.JLabel();
      jLabel3 = new javax.swing.JLabel();
      combineButton_ = new javax.swing.JButton();
      jPanel2 = new javax.swing.JPanel();
      jScrollPane1_ = new javax.swing.JScrollPane();
      jTable1_ = new javax.swing.JTable();

      setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
      setTitle("Gaussian tracking data");
      setMinimumSize(new java.awt.Dimension(450, 80));
      addWindowListener(new java.awt.event.WindowAdapter() {
         public void windowClosing(java.awt.event.WindowEvent evt) {
            formWindowClosing(evt);
         }
      });
      addComponentListener(new java.awt.event.ComponentAdapter() {
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
      visualizationMagnification_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1x", "2x", "4x", "8x", "16x", "32x", "64x", "128x" }));

      visualizationModel_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      visualizationModel_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Gaussian" }));

      sigmaMin_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      sigmaMin_.setText("0");

      intensityMin_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      intensityMin_.setText("0");

      filterIntensityCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      filterIntensityCheckBox_.setText("Intensity");
      filterIntensityCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            filterIntensityCheckBox_ActionPerformed(evt);
         }
      });

      filterSigmaCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      filterSigmaCheckBox_.setText("Sigma");
      filterSigmaCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            filterSigmaCheckBox_ActionPerformed(evt);
         }
      });

      jLabel1.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel1.setText("Filters:");

      renderButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      renderButton_.setText("Render");
      renderButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            renderButton_ActionPerformed(evt);
         }
      });

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel4.setText("Localization Microscopy");

      zCalibrateButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      zCalibrateButton_.setText("Z Calibration");
      zCalibrateButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zCalibrateButton_ActionPerformed(evt);
         }
      });

      zCalibrationLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      zCalibrationLabel_.setText("UnCalibrated");

      unjitterButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      unjitterButton_.setText("Drift Correct");
      unjitterButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            unjitterButton_ActionPerformed(evt);
         }
      });

      linkButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      linkButton_.setText("Link");
      linkButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            linkButton_ActionPerformed(evt);
         }
      });

      jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);

      centerTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      centerTrackButton_.setText("Center");
      centerTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            centerTrackButton_ActionPerformed(evt);
         }
      });

      straightenTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      straightenTrackButton_.setText("Straighten");
      straightenTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            straightenTrackButton_ActionPerformed(evt);
         }
      });

      logLogCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      logLogCheckBox_.setText("log-log");
      logLogCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            logLogCheckBox_ActionPerformed(evt);
         }
      });

      plotComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      plotComboBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "t-X", "t-Y", "t-dist.", "t-Int.", "X-Y", " " }));

      SubRange.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      SubRange.setText("SubRange");
      SubRange.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            SubRangeActionPerformed(evt);
         }
      });

      mathButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      mathButton_.setText("Math");
      mathButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mathButton_ActionPerformed(evt);
         }
      });

      averageTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      averageTrackButton_.setText("Average");
      averageTrackButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            averageTrackButton_ActionPerformed(evt);
         }
      });

      powerSpectrumCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      powerSpectrumCheckBox_.setText("PSD");
      powerSpectrumCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            powerSpectrumCheckBox_ActionPerformed(evt);
         }
      });

      plotButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      plotButton_.setText("Plot");
      plotButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            plotButton_ActionPerformed(evt);
         }
      });

      jLabel6.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel6.setText("Tracks");

      jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

      SigmaLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      SigmaLabel2.setText("nm");

      pairsMaxDistanceField_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      pairsMaxDistanceField_.setText("500");

      referenceName_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      referenceName_.setText("JLabel1");

      pairsButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      pairsButton.setText("Pairs");
      pairsButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pairsButtonActionPerformed(evt);
         }
      });

      listButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      listButton_.setText("List");
      listButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            listButton_ActionPerformed(evt);
         }
      });

      c2CorrectButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      c2CorrectButton.setText("2C Correct");
      c2CorrectButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            c2CorrectButtonActionPerformed(evt);
         }
      });

      method2CBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      method2CBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "NR-Similarity", "Affine", "LWM" }));
      method2CBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            method2CBox_ActionPerformed(evt);
         }
      });

      c2StandardButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      c2StandardButton.setText("2C Reference");
      c2StandardButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            c2StandardButtonActionPerformed(evt);
         }
      });

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel5.setText("2-Color");

      jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);

      infoButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      infoButton_.setText("Info");
      infoButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            infoButton_ActionPerformed(evt);
         }
      });

      removeButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      removeButton.setText("Remove");
      removeButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeButtonActionPerformed(evt);
         }
      });

      saveFormatBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      saveFormatBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Binary", "Text" }));

      saveButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      saveButton.setText("Save");
      saveButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveButtonActionPerformed(evt);
         }
      });

      loadButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      loadButton.setText("Load");
      loadButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadButtonActionPerformed(evt);
         }
      });

      showButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      showButton_.setText("Show");
      showButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showButton_ActionPerformed(evt);
         }
      });

      jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel2.setText("< spot <");

      jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      jLabel3.setText("< spot <");

      combineButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      combineButton_.setText("Combine");
      combineButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            combineButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
         jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(68, 68, 68)
                  .addComponent(jLabel7))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addContainerGap(33, Short.MAX_VALUE)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(saveButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(loadButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(showButton_, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                           .addComponent(saveFormatBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                           .addGroup(jPanel1Layout.createSequentialGroup()
                              .addGap(6, 6, 6)
                              .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                 .addComponent(removeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                                 .addComponent(infoButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(combineButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)))))
            .addGap(7, 7, 7)
            .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(84, 84, 84)
                  .addComponent(jLabel5))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(14, 14, 14)
                        .addComponent(c2StandardButton, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(c2CorrectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)))
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(referenceName_, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                           .addComponent(pairsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                           .addComponent(listButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(11, 11, 11)
                  .addComponent(method2CBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(pairsMaxDistanceField_, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(8, 8, 8)
                  .addComponent(SigmaLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(10, 10, 10)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(plotButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(powerSpectrumCheckBox_))
                     .addComponent(averageTrackButton_)
                     .addComponent(mathButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(SubRange, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                           .addGap(4, 4, 4)
                           .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                              .addComponent(logLogCheckBox_)
                              .addComponent(plotComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                           .addGap(15, 15, 15))
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                           .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                           .addComponent(straightenTrackButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(centerTrackButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(83, 83, 83)
                  .addComponent(jLabel6)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(zCalibrateButton_)
                     .addComponent(unjitterButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(linkButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(zCalibrationLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)))
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(7, 7, 7)
                        .addComponent(jLabel1)
                        .addGap(4, 4, 4)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                           .addComponent(filterSigmaCheckBox_)
                           .addGroup(jPanel1Layout.createSequentialGroup()
                              .addGap(1, 1, 1)
                              .addComponent(filterIntensityCheckBox_)))
                        .addGap(1, 1, 1)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                           .addComponent(sigmaMin_, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                           .addGroup(jPanel1Layout.createSequentialGroup()
                              .addGap(1, 1, 1)
                              .addComponent(intensityMin_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(1, 1, 1)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                           .addComponent(jLabel3)
                           .addComponent(jLabel2))
                        .addGap(3, 3, 3)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                           .addComponent(sigmaMax_, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                           .addComponent(intensityMax_, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(renderButton_)
                        .addGap(4, 4, 4)
                        .addComponent(visualizationModel_, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(6, 6, 6)
                        .addComponent(visualizationMagnification_, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)))
                  .addGap(4, 4, 4)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(SigmaLabel3)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addComponent(IntLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(137, 137, 137)
                  .addComponent(jLabel4))))
      );
      jPanel1Layout.setVerticalGroup(
         jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(jPanel1Layout.createSequentialGroup()
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(jLabel7)
                     .addComponent(jLabel5))
                  .addGap(5, 5, 5))
               .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(jLabel6, javax.swing.GroupLayout.Alignment.TRAILING)
                     .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.TRAILING))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(3, 3, 3)
                  .addComponent(saveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(25, 25, 25)
                  .addComponent(loadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(1, 1, 1)
                  .addComponent(showButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addComponent(saveFormatBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(21, 21, 21)
                  .addComponent(removeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(1, 1, 1)
                  .addComponent(infoButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                  .addComponent(combineButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(8, 8, 8))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addComponent(c2StandardButton, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(33, 33, 33)
                  .addComponent(c2CorrectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addComponent(referenceName_)
                  .addGap(12, 12, 12)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(pairsMaxDistanceField_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(method2CBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addComponent(SigmaLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGap(3, 3, 3)
                  .addComponent(pairsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addComponent(listButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addComponent(plotButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addComponent(powerSpectrumCheckBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addComponent(averageTrackButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(2, 2, 2)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(mathButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(centerTrackButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGap(2, 2, 2)
                  .addComponent(SubRange, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(17, 17, 17)
                        .addComponent(logLogCheckBox_))
                     .addComponent(plotComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGap(2, 2, 2)
                  .addComponent(straightenTrackButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(zCalibrateButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(renderButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(zCalibrationLabel_)
                  .addGap(71, 71, 71))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(1, 1, 1)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(visualizationModel_, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(visualizationMagnification_, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGap(16, 16, 16)
                  .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                           .addComponent(jLabel1)
                           .addComponent(unjitterButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(filterSigmaCheckBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                           .addComponent(filterIntensityCheckBox_)
                           .addComponent(linkButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(sigmaMin_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                           .addComponent(intensityMin_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                           .addComponent(jLabel2)))
                     .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                           .addComponent(sigmaMax_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                           .addComponent(jLabel3))
                        .addGap(1, 1, 1)
                        .addComponent(intensityMax_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addGap(41, 41, 41)
                  .addComponent(SigmaLabel3)
                  .addGap(7, 7, 7)
                  .addComponent(IntLabel2))))
         .addComponent(jSeparator3)
         .addComponent(jSeparator2)
         .addComponent(jSeparator4)
      );

      jPanel2.setLayout(new java.awt.BorderLayout());

      jScrollPane1_.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
      jScrollPane1_.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

      jTable1_.setModel(myTableModel_);
      jTable1_.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
      jScrollPane1_.setViewportView(jTable1_);

      jPanel2.add(jScrollPane1_, java.awt.BorderLayout.CENTER);

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
         .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, 404, Short.MAX_VALUE)
            .addGap(0, 0, 0))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

   /**
    * Loads data saved in TSF format (Tagged Spot File Format)
    * Opens awt file select dialog which lets you select only a single file
    * If you want to open multiple files, press the ctrl key while clicking
    * the button.  This will open the swing file opener.
    *
    * @evt
    */
    private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed

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
    }//GEN-LAST:event_loadButtonActionPerformed

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
    
                  
    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
       int rows[] = jTable1_.getSelectedRows();
       if (rows.length > 0) {
          for (int i = 0; i < rows.length; i++) {
             if (saveFormatBox_.getSelectedIndex() == 0) {
                if (i == 0)
                   dir_ = LoadAndSave.saveData(rowData_.get(rows[i]), false, 
                           dir_, this);
                else
                   dir_ = LoadAndSave.saveData(rowData_.get(rows[i]), true, 
                           dir_, this);
             } else {
                LoadAndSave.saveDataAsText(rowData_.get(rows[i]), this);
             }
          }
       } else {
          JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to save");
       }
    }//GEN-LAST:event_saveButtonActionPerformed

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed
       int rows[] = jTable1_.getSelectedRows();
       if (rows.length > 0) {
          for (int row = rows.length - 1; row >= 0; row--) {
             rowData_.remove(rows[row]);
             myTableModel_.fireTableRowsDeleted(rows[row], rows[row]);
          }
       } else {
          JOptionPane.showMessageDialog(getInstance(), "No dataset selected");
       }
    }//GEN-LAST:event_removeButtonActionPerformed

    private void showButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showButton_ActionPerformed
       int row = jTable1_.getSelectedRow();
       if (row > -1) {
          try {
          showResults(rowData_.get(row));
          } catch (OutOfMemoryError ome) {
             JOptionPane.showMessageDialog(getInstance(), "Not enough memory to show data");
          }
       } else {
          JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to show");
       }
    }//GEN-LAST:event_showButton_ActionPerformed

   private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
      //jScrollPane1.setSize(this.getSize());
      Dimension d = getSize();
      d.height -= 155;
      jScrollPane1_.setSize(d);
      jScrollPane1_.getViewport().setViewSize(d);
   }//GEN-LAST:event_formComponentResized

   /**
    * Use the selected data set as the reference for 2-channel color correction
    * @param evt 
    */
   private void c2StandardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_c2StandardButtonActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select one or more datasets as color reference");
      } else {
         
         CoordinateMapper.PointMap points = new CoordinateMapper.PointMap();
         for (int row : rows) {
            
            // Get points from both channels in first frame as ArrayLists        
            ArrayList<Point2D.Double> xyPointsCh1 = new ArrayList<Point2D.Double>();
            ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
            for (SpotData gs : rowData_.get(row).spotList_) {
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
            boolean continueQualityCheck = true;
            int nrOfRemovedSpots = 0;

            while (continueQualityCheck && points.size() > 4) {
               // quality control on our new coordinate mapper.  
               // Apply an affine transform on our data and check distribution 
               int method = CoordinateMapper.AFFINE;
               c2t_.setMethod(method);
               CoordinateMapper.PointMap corPoints = new CoordinateMapper.PointMap();
               List<Double> distances = new ArrayList<Double>();
               double maxDistance = 0.0;
               Point2D.Double maxPairKey = null;
               for (Map.Entry pair : points.entrySet()) {
                  Point2D.Double uPt = (Point2D.Double) pair.getValue();
                  Point2D.Double otherPt = (Point2D.Double) pair.getKey();
                  Point2D.Double corPt = c2t_.transform(otherPt);
                  corPoints.put(uPt, corPt);
                  double distance = Math.sqrt(NearestPoint2D.distance2(uPt, corPt));
                  if (distance > maxDistance) {
                     maxDistance = distance;
                     maxPairKey = otherPt;
                  }
                  distances.add(distance);
               }
               Double avg = listAvg(distances);
               Double stdDev = listStdDev(distances, avg);

               // Quality control check
               if (2 * stdDev > avg) {
                  nrOfRemovedSpots+=1;
                  points.remove(maxPairKey);
                  c2t_ = new CoordinateMapper(points, 2, 1);
               } else {
                  continueQualityCheck = false;
                  ij.IJ.log("Removed " + nrOfRemovedSpots + " pairs, " + ", avg. distance: " +
                    avg + ", std. dev: " + stdDev);
               }
            }
            
            //ij.IJ.showMessage("Corrected data have average of: " + avg + ",  std. dev. of: " + stdDev);
            
            String name = "ID: " + rowData_.get(rows[0]).ID_;
            if (rows.length > 1) {
               for (int i = 1; i < rows.length; i++) {
                  name += "," + rowData_.get(rows[i]).ID_;
               }
            }
            referenceName_.setText(name);
         } catch (Exception ex) {
            JOptionPane.showMessageDialog(getInstance(), 
               "Error setting color reference.  Did you have enough input points?");
         }

         
      
         
         
         
      }
   }//GEN-LAST:event_c2StandardButtonActionPerformed

   /**
    * Cycles through the spots of the selected data set and finds the most nearby 
    * spot in channel 2.  It will list this as a pair if the two spots are within
    * MAXMATCHDISTANCE nm of each other.  
    * In addition, it will list the  average distance, and average distance
    * in x and y for each frame.
    * 
    * spots in channel 2
    * that are within MAXMATCHDISTANCE of 
    * 
    * @param evt 
    */
   private void pairsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pairsButtonActionPerformed
      final int row = jTable1_.getSelectedRow();
      if (row < 0) {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset for the Pair function");

         return;
      }

      if (row > -1) {

         Runnable doWorkRunnable = new Runnable() {

            @Override
            public void run() {
               ResultsTable rt = new ResultsTable();
               rt.reset();
               rt.setPrecision(2);
               ResultsTable rt2 = new ResultsTable();
               rt2.reset();
               rt2.setPrecision(2);
               int width = rowData_.get(row).width_;
               int height = rowData_.get(row).height_;
               double factor = rowData_.get(row).pixelSizeNm_;
               boolean useS = useSeconds(rowData_.get(row));
               ij.ImageStack stack = new ij.ImageStack(width, height);

               ImagePlus sp = new ImagePlus("Errors in pairs");

               XYSeries xData = new XYSeries("XError");
               XYSeries yData = new XYSeries("YError");


               ij.IJ.showStatus("Creating Pairs...");


               for (int frame = 1; frame <= rowData_.get(row).nrFrames_; frame++) {
                  ij.IJ.showProgress(frame, rowData_.get(row).nrFrames_);
                  ImageProcessor ip = new ShortProcessor(width, height);
                  short pixels[] = new short[width * height];
                  ip.setPixels(pixels);

                  // Get points from both channels in each frame as ArrayLists        
                  ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
                  ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                  for (SpotData gs : rowData_.get(row).spotList_) {
                     if (gs.getFrame() == frame) {
                        if (gs.getChannel() == 1) {
                           gsCh1.add(gs);
                        } else if (gs.getChannel() == 2) {
                           Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                           xyPointsCh2.add(point);
                        }
                     }
                  }

                  if (xyPointsCh2.isEmpty()) {
                     ReportingUtils.logError("Pairs function in Localization plugin: no points found in second channel in frame " + frame);
                     continue;
                  }

                  // Find matching points in the two ArrayLists
                  Iterator it2 = gsCh1.iterator();
                  try {
                     NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                             NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText()));

                     ArrayList<Double> distances = new ArrayList<Double>();
                     ArrayList<Double> errorX = new ArrayList<Double>();
                     ArrayList<Double> errorY = new ArrayList<Double>();

                     while (it2.hasNext()) {
                        SpotData gs = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
                        if (pCh2 != null) {
                           rt.incrementCounter();
                           rt.addValue(Terms.POSITION, gs.getPosition());
                           rt.addValue(Terms.FRAME, gs.getFrame());
                           rt.addValue(Terms.SLICE, gs.getSlice());
                           rt.addValue(Terms.CHANNEL, gs.getSlice());
                           rt.addValue(Terms.XPIX, gs.getX());
                           rt.addValue(Terms.YPIX, gs.getY());
                           rt.addValue("X1", pCh1.getX());
                           rt.addValue("Y1", pCh1.getY());
                           rt.addValue("X2", pCh2.getX());
                           rt.addValue("Y2", pCh2.getY());
                           double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                           double d = Math.sqrt(d2);
                           rt.addValue("Distance", d);
                           rt.addValue("Orientation (sine)",
                                   NearestPoint2D.orientation(pCh1, pCh2));
                           distances.add(d);

                           ip.putPixel((int) (pCh1.x / factor), (int) (pCh1.y / factor), (int) d);

                           double ex = pCh2.getX() - pCh1.getX();
                           //double ex = (pCh1.getX() - pCh2.getX()) * (pCh1.getX() - pCh2.getX());
                           //ex = Math.sqrt(ex);
                           errorX.add(ex);
                           //double ey = (pCh1.getY() - pCh2.getY()) * (pCh1.getY() - pCh2.getY());
                           //ey = Math.sqrt(ey);
                           double ey = pCh2.getY() - pCh1.getY();
                           errorY.add(ey);

                        }
                     }

                     Double avg = listAvg(distances);
                     Double stdDev = listStdDev(distances, avg);

                     Double avgX = listAvg(errorX);
                     Double stdDevX = listStdDev(errorX, avgX);
                     Double avgY = listAvg(errorY);
                     Double stdDevY = listStdDev(errorY, avgY);

                     rt2.incrementCounter();
                     rt2.addValue("Frame Nr.", frame);
                     rt2.addValue("Avg. distance", avg);
                     rt2.addValue("StdDev distance", stdDev);
                     rt2.addValue("X", avgX);
                     rt2.addValue("StdDev X", stdDevX);
                     rt2.addValue("Y", avgY);
                     rt2.addValue("StdDevY", stdDevY);

                     stack.addSlice("frame: " + frame, ip);

                     double timePoint = frame;
                     if (rowData_.get(row).timePoints_ != null) {
                        timePoint = rowData_.get(row).timePoints_.get(frame);
                        if (useS) {
                           timePoint /= 1000;
                        }
                     }
                     xData.add(timePoint, avgX);
                     yData.add(timePoint, avgY);

                  } catch (ParseException ex) {
                     JOptionPane.showMessageDialog(getInstance(), "Error in Pairs input");
                     return;
                  }

               }

               if (rt.getCounter() == 0) {
                  MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                          "No Pairs found", "No Pairs found");
                  return;
               }

               // show summary in resultstable
               rt2.show("Summary of Pairs found in " + rowData_.get(row).name_);


               //  show Pairs panel and attach listener
               TextPanel tp;
               TextWindow win;

               String rtName = "Pairs found in " + rowData_.get(row).name_;
               rt.show(rtName);
               ImagePlus siPlus = ij.WindowManager.getImage(rowData_.get(row).title_);
               Frame frame = WindowManager.getFrame(rtName);
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

                  ResultsTableListener myk = new ResultsTableListener(siPlus, rt, win, rowData_.get(row).halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }




               String xAxis = "Time (frameNr)";
               if (rowData_.get(row).timePoints_ != null) {
                  xAxis = "Time (ms)";
                  if (useS) {
                     xAxis = "Time (s)";
                  }
               }
               GaussianUtils.plotData2("Error in " + rowData_.get(row).name_, xData, yData, xAxis, "Error(nm)", 0, 400);

               ij.IJ.showStatus("");

               sp.setOpenAsHyperStack(true);
               sp.setStack(stack, 1, 1, rowData_.get(row).nrFrames_);
               sp.setDisplayRange(0, 20);
               sp.setTitle(rowData_.get(row).title_);

               ImageWindow w = new StackWindow(sp);
               w.setTitle("Error in " + rowData_.get(row).name_);

               w.setImage(sp);
               w.setVisible(true);

            }
         };

         (new Thread(doWorkRunnable)).start();

      }
   }//GEN-LAST:event_pairsButtonActionPerformed

   /**
    * Helper function for function listParticels
    * Finds a spot within MAXMatchDistance in the frame following the frame
    * of the given spot.
    * Only looks at Channel 1
    * 
    * @param input - look for a spot close to this one
    * @param spotPairs - List with spotPairs
    * @return spotPair found or null if none
    */
   private GsSpotPair findNextSpotPair(GsSpotPair input,
           ArrayList<ArrayList<GsSpotPair>> spotPairsByFrame,
           NearestPointGsSpotPair npsp, int frame) {
      final double maxDistance;
      try {
         maxDistance = NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText());
      } catch (ParseException ex) {
         ReportingUtils.logError("Error parsing pairs max distance field");
         return null;
      }
      final double maxDistance2 = maxDistance * maxDistance;

      Iterator<GsSpotPair> it = (spotPairsByFrame.get(frame - 1)).iterator();

      while (it.hasNext()) {
         GsSpotPair nextSpot = it.next();
         if (nextSpot.getGSD().getFrame() == frame) {
            if (NearestPoint2D.distance2(input.getfp(), nextSpot.getfp())
                    < maxDistance2) {
               return nextSpot;
            }
         }
         // optimization that is only valid if the ArrayList is properly sorted
         if (nextSpot.getGSD().getFrame() > frame) {
            return null;
         }
      }

      return null;
   }

   /**
    * Cycles through the spots of the selected data set and finds the most nearby 
    * spot in channel 2.  It will list this as a pair if the two spots are within
    * MAXMATCHDISTANCE nm of each other.  
    * 
    * Once all pairs are found, it will go through all frames and try to build up
    * tracks.  If the spot is within MAXMATCHDISTANCE between frames, the code
    * will consider the particle to be identical.
    * 
    * All "tracks" of particles will be listed
    * 
    * In addition, it will list the  average distance, and average distance
    * in x and y for each frame.
    * 
    * spots in channel 2
    * that are within MAXMATCHDISTANCE of 
    * 
    * @param evt 
    */
   public void listParticles(java.awt.event.ActionEvent evt) {

      final int[] rows = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select a dataset for the List Particles function");

         return;
      }

      // if (row > -1) {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {


            // Show Particle List as linked Results Table
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            // Show Particle Summary as Linked Results Table
            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(1);

            final double maxDistance;
            try {
               maxDistance = NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText());
            } catch (ParseException ex) {
               ReportingUtils.logError("Error parsing pairs max distance field");
               return;
            }

            for (int row : rows) {
               ArrayList<ArrayList<GsSpotPair>> spotPairsByFrame =
                       new ArrayList<ArrayList<GsSpotPair>>();

               ij.IJ.showStatus("Creating Pairs...");

               // First go through all frames to find all pairs
               int nrSpotPairsInFrame1 = 0;
               for (int frame = 1; frame <= rowData_.get(row).nrFrames_; frame++) {
                  ij.IJ.showProgress(frame, rowData_.get(row).nrFrames_);
                  spotPairsByFrame.add(new ArrayList<GsSpotPair>());

                  // Get points from both channels in first frame as ArrayLists        
                  ArrayList<Point2D.Double> xyPointsCh1 = new ArrayList<Point2D.Double>();
                  ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                  for (SpotData gs : rowData_.get(row).spotList_) {
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
                     ReportingUtils.logError(
                             "Pairs function in Localization plugin: no points found in second channel in frame " 
                             + frame);
                     continue;
                  }

                  // Find matching points in the two ArrayLists
                  Iterator it2 = xyPointsCh1.iterator();
                  try {
                     NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                             NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText()));

                     while (it2.hasNext()) {
                        SpotData gs = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
                        if (pCh2 != null) {
                           GsSpotPair pair = new GsSpotPair(gs, pCh1, pCh2);
                           //spotPairs.add(pair);
                           spotPairsByFrame.get(frame - 1).add(pair);
                        }
                     }

                  } catch (ParseException ex) {
                     JOptionPane.showMessageDialog(getInstance(), "Error in Pairs input");
                     return;
                  }
               }


               // We have all pairs, assemble in tracks
               ij.IJ.showStatus("Assembling tracks...");

               // prepare NearestPoint objects to speed up finding closest pair 
               ArrayList<NearestPointGsSpotPair> npsp = new ArrayList<NearestPointGsSpotPair>();
               for (int frame = 1; frame <= rowData_.get(row).nrFrames_; frame++) {
                  npsp.add(new NearestPointGsSpotPair(spotPairsByFrame.get(frame - 1), maxDistance));
               }

               ArrayList<ArrayList<GsSpotPair>> tracks = new ArrayList<ArrayList<GsSpotPair>>();

               Iterator<GsSpotPair> iSpotPairs = spotPairsByFrame.get(0).iterator();
               int i = 0;
               while (iSpotPairs.hasNext()) {
                  ij.IJ.showProgress(i++, nrSpotPairsInFrame1);
                  GsSpotPair spotPair = iSpotPairs.next();
                  // for now, we only start tracks at frame number 1
                  if (spotPair.getGSD().getFrame() == 1) {
                     ArrayList<GsSpotPair> track = new ArrayList<GsSpotPair>();
                     track.add(spotPair);
                     int frame = 2;
                     while (frame <= rowData_.get(row).nrFrames_) {

                        GsSpotPair newSpotPair = npsp.get(frame - 1).findKDWSE(
                                new Point2D.Double(spotPair.getfp().getX(), spotPair.getfp().getY()));
                        if (newSpotPair != null) {
                           spotPair = newSpotPair;
                           track.add(spotPair);
                        }
                        frame++;
                     }
                     tracks.add(track);
                  }
               }

               if (tracks.isEmpty()) {
                  MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                          "No Pairs found", "No Pairs found");
                  continue;
               } 

               Iterator<ArrayList<GsSpotPair>> itTracks = tracks.iterator();
               int spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  Iterator<GsSpotPair> itTrack = track.iterator();
                  while (itTrack.hasNext()) {
                     GsSpotPair spot = itTrack.next();
                     rt.incrementCounter();
                     rt.addValue("Spot ID", spotId);
                     rt.addValue(Terms.FRAME, spot.getGSD().getFrame());
                     rt.addValue(Terms.SLICE, spot.getGSD().getSlice());
                     rt.addValue(Terms.CHANNEL, spot.getGSD().getSlice());
                     rt.addValue(Terms.XPIX, spot.getGSD().getX());
                     rt.addValue(Terms.YPIX, spot.getGSD().getY());
                     rt.addValue("Distance", Math.sqrt(
                             NearestPoint2D.distance2(spot.getfp(), spot.getsp())));
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(spot.getfp(), spot.getsp()));
                  }
                  spotId++;
               }
               TextPanel tp;
               TextWindow win;

               String rtName = rowData_.get(row).name_ + " Particle List";
               rt.show(rtName);
               ImagePlus siPlus = ij.WindowManager.getImage(rowData_.get(row).title_);
               Frame frame = WindowManager.getFrame(rtName);
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

                  ResultsTableListener myk = new ResultsTableListener(siPlus, rt, win, rowData_.get(row).halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }

               siPlus = ij.WindowManager.getImage(rowData_.get(row).title_);
               if (siPlus != null && siPlus.getOverlay() != null) {
                  siPlus.getOverlay().clear();
               }
               Arrow.setDefaultWidth(0.5);

               itTracks = tracks.iterator();
               spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  ArrayList<Double> distances = new ArrayList<Double>();
                  ArrayList<Double> orientations = new ArrayList<Double>();
                  ArrayList<Double> xDiff = new ArrayList<Double>();
                  ArrayList<Double> yDiff = new ArrayList<Double>();
                  for (GsSpotPair pair : track) {
                     distances.add(Math.sqrt(
                             NearestPoint2D.distance2(pair.getfp(), pair.getsp())));
                     orientations.add(NearestPoint2D.orientation(pair.getfp(),
                             pair.getsp()));
                     xDiff.add(pair.getfp().getX() - pair.getsp().getX());
                     yDiff.add(pair.getfp().getY() - pair.getsp().getY());
                  }
                  GsSpotPair pair = track.get(0);
                  rt2.incrementCounter();
                  rt2.addValue("Row ID", rowData_.get(row).ID_);
                  rt2.addValue("Spot ID", spotId);
                  rt2.addValue(Terms.FRAME, pair.getGSD().getFrame());
                  rt2.addValue(Terms.SLICE, pair.getGSD().getSlice());
                  rt2.addValue(Terms.CHANNEL, pair.getGSD().getSlice());
                  rt2.addValue(Terms.XPIX, pair.getGSD().getX());
                  rt2.addValue(Terms.YPIX, pair.getGSD().getY());
                  rt2.addValue("n", track.size());

                  double avg = ListUtils.avgList(distances);
                  rt2.addValue("Distance-Avg", avg);
                  rt2.addValue("Distance-StdDev", ListUtils.stdDevList(distances, avg));
                  double oAvg = ListUtils.avgList(orientations);
                  rt2.addValue("Orientation-Avg", oAvg);
                  rt2.addValue("Orientation-StdDev",
                          ListUtils.stdDevList(orientations, oAvg));

                  double xDiffAvg = ListUtils.avgList(xDiff);
                  double yDiffAvg = ListUtils.avgList(yDiff);
                  double xDiffAvgStdDev = ListUtils.stdDevList(xDiff, xDiffAvg);
                  double yDiffAvgStdDev = ListUtils.stdDevList(yDiff, yDiffAvg);
                  rt2.addValue("Dist.Vect.Avg", Math.sqrt(
                          (xDiffAvg * xDiffAvg) + (yDiffAvg * yDiffAvg)));
                  rt2.addValue("Dist.Vect.StdDev", Math.sqrt(
                          (xDiffAvgStdDev * xDiffAvgStdDev)
                          + (yDiffAvgStdDev * yDiffAvgStdDev)));


                  /* draw arrows in overlay */
                  double mag = 100.0;  // factor that sets magnification of the arrow
                  double factor = mag * 1 / rowData_.get(row).pixelSizeNm_;  // factor relating mad and pixelSize
                  int xStart = track.get(0).getGSD().getX();
                  int yStart = track.get(0).getGSD().getY();


                  Arrow arrow = new Arrow(xStart, yStart,
                          xStart + (factor * xDiffAvg),
                          yStart + (factor * yDiffAvg));
                  arrow.setHeadSize(3);
                  arrow.setOutline(false);
                  if (siPlus != null && siPlus.getOverlay() == null) {
                     siPlus.setOverlay(arrow, Color.yellow, 1, Color.yellow);
                  } else if (siPlus != null && siPlus.getOverlay() != null) {
                     siPlus.getOverlay().add(arrow);
                  }

                  spotId++;
               }
               if (siPlus != null) {
                  siPlus.setHideOverlay(false);
               }

               rtName = rowData_.get(row).name_ + " Particle Summary";
               rt2.show(rtName);
               siPlus = ij.WindowManager.getImage(rowData_.get(row).title_);
               frame = WindowManager.getFrame(rtName);
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

                  ResultsTableListener myk = new ResultsTableListener(siPlus, rt2, win, rowData_.get(row).halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }

               ij.IJ.showStatus("");

            }
         }
      };

      (new Thread(doWorkRunnable)).start();

   }                                         

   
   
   /**
    * Calculates the average of a list of doubles
    * 
    * @param list
    * @return average
    */
   private static double listAvg (List<Double> list) {
      double total = 0.0;
      Iterator it = list.iterator();
      while (it.hasNext()) {
         total += (Double) it.next();
      }
      
      return total / list.size();      
   }
   
   
   /**
    * Returns the Standard Deviation as sqrt( 1/(n-1) sum( square(value - avg)) )
    * Feeding in parameter avg is just increase performance
    * 
    * @param list ArrayList<Double> 
    * @param avg average of the list
    * @return standard deviation as defined above
    */
   private static double listStdDev (List<Double> list, double avg) {
      
      double errorsSquared = 0;
      Iterator it = list.iterator();
      while (it.hasNext()) {
         double error = (Double) it.next() - avg;
         errorsSquared += (error * error);
      }
      return Math.sqrt(errorsSquared / (list.size() - 1) ) ;
   }
   
   
   /**
    * Utility function to calculate Standard Deviation
    * @param list
    * @return 
    */
   private static double listStdDev (ArrayList<Double> list) {
      double avg = listAvg(list);
      
      return listStdDev(list, avg);

   }
   
   
   private void c2CorrectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_c2CorrectButtonActionPerformed
      int[] rows = jTable1_.getSelectedRows();
      if (rows.length > 0) {     
         try {
            for (int row : rows) {
               correct2C(rowData_.get(row));
            }
         } catch (InterruptedException ex) {
            ReportingUtils.showError(ex);
         }
      } else
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to color correct");
   }//GEN-LAST:event_c2CorrectButtonActionPerformed

   private void unjitterButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unjitterButton_ActionPerformed
      final int row = jTable1_.getSelectedRow();
      if (row > -1) {
         Runnable doWorkRunnable = new Runnable() {
            @Override
            public void run() {
               if (jitterMethod_ == 0)
                  unJitter(rowData_.get(row));
               else
                  new DriftCorrector().unJitter(rowData_.get(row), jitterMaxFrames_, jitterMaxSpots_);
            }
         };
         (new Thread(doWorkRunnable)).start();
      } else {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to unjitter");
      }
   }//GEN-LAST:event_unjitterButton_ActionPerformed

   private void filterSigmaCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterSigmaCheckBox_ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_filterSigmaCheckBox_ActionPerformed

   private void filterIntensityCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterIntensityCheckBox_ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_filterIntensityCheckBox_ActionPerformed

   private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
       prefs_.putInt(FRAMEXPOS, getX());
       prefs_.putInt(FRAMEYPOS, getY());
       prefs_.putInt(FRAMEWIDTH, getWidth());
       prefs_.putInt(FRAMEHEIGHT, getHeight());
       
       prefs_.putBoolean(USESIGMA, filterSigmaCheckBox_.isSelected());
       prefs_.put(SIGMAMIN, sigmaMin_.getText());
       prefs_.put(SIGMAMAX, sigmaMax_.getText());
       prefs_.putBoolean(USEINT, filterIntensityCheckBox_.isSelected());
       prefs_.put(INTMIN, intensityMin_.getText());
       prefs_.put(INTMAX, intensityMax_.getText());
       prefs_.put(LOADTSFDIR, loadTSFDir_);
       prefs_.putInt(RENDERMAG, visualizationMagnification_.getSelectedIndex());
       prefs_.put(PAIRSMAXDISTANCE, pairsMaxDistanceField_.getText());
       
       TableColumnModel cm = jTable1_.getColumnModel();
       prefs_.putInt(COL0Width, cm.getColumn(0).getWidth());
       prefs_.putInt(COL1Width, cm.getColumn(1).getWidth());
       prefs_.putInt(COL2Width, cm.getColumn(2).getWidth());
       prefs_.putInt(COL3Width, cm.getColumn(3).getWidth());
       prefs_.putInt(COL4Width, cm.getColumn(4).getWidth());
       prefs_.putInt(COL5Width, cm.getColumn(5).getWidth());
       prefs_.putInt(COL6Width, cm.getColumn(6).getWidth());
       
       setVisible(false);
   }//GEN-LAST:event_formWindowClosing

   /**
    * Present user with summary data of this dataset.
    * 
    * @param evt 
    */
   private void infoButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_infoButton_ActionPerformed
      int row = jTable1_.getSelectedRow();
      if (row > -1) {
          
         
         RowData rowData = rowData_.get(row);
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
   }//GEN-LAST:event_infoButton_ActionPerformed

   /**
    * Renders dataset 
    * 
    * @param evt 
    */
   private void renderButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renderButton_ActionPerformed
      final int row = jTable1_.getSelectedRow();
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

                  RowData rowData = rowData_.get(row);
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

                     GaussCanvas gs = new GaussCanvas(sp, rowData_.get(row),
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
   }//GEN-LAST:event_renderButton_ActionPerformed

   private void plotButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select one or more datasets to plot");
      } else {
         RowData[] myRows = new RowData[rows.length];
         // TODO: check that these are tracks 
         for (int i = 0; i < rows.length; i++)
            myRows[i] = rowData_.get(rows[i]);
         plotData(myRows, plotComboBox_.getSelectedIndex());
      }
   }//GEN-LAST:event_plotButton_ActionPerformed

   
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
   private void averageTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_averageTrackButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select one or more datasets to average");
      } else {
         RowData[] myRows = new RowData[rows.length];
         ArrayList<Point2D.Double> listAvgs = new ArrayList<Point2D.Double>();
         
         for (int i = 0; i < rows.length; i++) {
            myRows[i] = rowData_.get(rows[i]);
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
         addSpotData(rowData.name_ + " Average", rowData.title_, "", rowData.width_,
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

   }//GEN-LAST:event_averageTrackButton_ActionPerformed

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
         
         addSpotData(rowData.name_ + " Subtracted", rowData.title_, "", rowData.width_,
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

   private void mathButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mathButton_ActionPerformed
      int[] rows = new int[jTable1_.getRowCount()];

      for (int i = 0; i < rows.length; i++) {
         rows[i] = (Integer) jTable1_.getValueAt(i, 0);
      }

      MathForm mf = new MathForm(rows, rows);

      // mf.setBackground(MMStudio.getInstance().getBackgroundColor());
      // MMStudio.getInstance().addMMBackgroundListener(mf);

      mf.setVisible(true);
   }//GEN-LAST:event_mathButton_ActionPerformed

   /**
    * Links spots by checking in consecutive frames whether the spot is still present
    * If it is, add it to a list
    * Once a frame has been found in which it is not present, calculate the average spot position
    * and add this averaged spot to the list with linked spots
    * The Frame number of the linked spot list will be 0
    * @param evt - ignored...
    */
   private void linkButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkButton_ActionPerformed
      final int row = jTable1_.getSelectedRow();

      final RowData rowData = rowData_.get(row);
      if (rowData.frameIndexSpotList_ == null) {
         rowData.index();
      }
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
            SpotLinker.link(rowData, maxDistance);
         }
      };

      (new Thread(doWorkRunnable)).start();
   }//GEN-LAST:event_linkButton_ActionPerformed

   private void straightenTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_straightenTrackButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one or more datasets to straighten");
      } else {
         for (int row : rows) {
            RowData r = rowData_.get(row);
            if (evt.getModifiers() > 0) {
               if (r.title_.equals(ij.IJ.getImage().getTitle())) {
                  ImagePlus ip = ij.IJ.getImage();
                  Roi roi = ip.getRoi();
                  if (roi.isLine()) {
                     Polygon pol = roi.getPolygon();

                  }

               }
            }
            straightenTrack(r);
         }
      }
   }//GEN-LAST:event_straightenTrackButton_ActionPerformed

   private void centerTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centerTrackButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one or more datasets to center");
      } else {
         for (int row : rows) {
            centerTrack(rowData_.get(row));
         }
      }
   }//GEN-LAST:event_centerTrackButton_ActionPerformed

   private void powerSpectrumCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_powerSpectrumCheckBox_ActionPerformed

   }//GEN-LAST:event_powerSpectrumCheckBox_ActionPerformed

   private void logLogCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logLogCheckBox_ActionPerformed

   }//GEN-LAST:event_logLogCheckBox_ActionPerformed

   private void zCalibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zCalibrateButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
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
   }//GEN-LAST:event_zCalibrateButton_ActionPerformed

   private void listButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_listButton_ActionPerformed
      listParticles(evt);
   }//GEN-LAST:event_listButton_ActionPerformed

   private void method2CBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_method2CBox_ActionPerformed
      prefs_.put(METHOD2C, (String) method2CBox_.getSelectedItem());
   }//GEN-LAST:event_method2CBox_ActionPerformed

   private String range_ = "";

   private void SubRangeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SubRangeActionPerformed

      final int[] rows = jTable1_.getSelectedRows();

      if (rows == null || rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(),
                 "Please select one or more datasets for sub ranging");
         return;
      }

      range_ = (String) JOptionPane.showInputDialog(this, "Provide desired subrange\n"
              + "e.g. \"7-50\"", "SubRange", JOptionPane.PLAIN_MESSAGE, null, null, range_);
      ArrayList<Integer> desiredFrameNumbers = new ArrayList<Integer>(
              rowData_.get(rows[0]).maxNrSpots_);
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
                          edu.valelab.GaussianFit.utils.SubRange.subRange(
                          rowData_.get(row), desiredFrameNumbersCopy);
                  addSpotData(newRow);
               }

            }
         }
      };
      doWorkRunnable.run();

   }//GEN-LAST:event_SubRangeActionPerformed

   private void combineButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_combineButton_ActionPerformed
      try {
         final int[] rows = jTable1_.getSelectedRows();
         
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
                  RowData rowData = rowData_.get(rows[i]);
                  for (SpotData gs : rowData.spotList_) {
                     newData.add(gs);
                  }
               }

               // Add transformed data to data overview window
               // for now, copy header of first data set
               RowData rowData = rowData_.get(rows[0]);
               addSpotData(rowData.name_ + "-Combined",
                       rowData.title_,
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
      
   }//GEN-LAST:event_combineButton_ActionPerformed

   
   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JLabel IntLabel2;
   private javax.swing.JLabel SigmaLabel2;
   private javax.swing.JLabel SigmaLabel3;
   private javax.swing.JButton SubRange;
   private javax.swing.JButton averageTrackButton_;
   private javax.swing.JButton c2CorrectButton;
   private javax.swing.JButton c2StandardButton;
   private javax.swing.JButton centerTrackButton_;
   private javax.swing.JButton combineButton_;
   private javax.swing.JCheckBox filterIntensityCheckBox_;
   private javax.swing.JCheckBox filterSigmaCheckBox_;
   private javax.swing.JButton infoButton_;
   private javax.swing.JTextField intensityMax_;
   private javax.swing.JTextField intensityMin_;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel6;
   private javax.swing.JLabel jLabel7;
   private javax.swing.JPanel jPanel1;
   private javax.swing.JPanel jPanel2;
   private javax.swing.JScrollPane jScrollPane1_;
   private javax.swing.JSeparator jSeparator2;
   private javax.swing.JSeparator jSeparator3;
   private javax.swing.JSeparator jSeparator4;
   private javax.swing.JTable jTable1_;
   private javax.swing.JButton linkButton_;
   private javax.swing.JButton listButton_;
   private javax.swing.JButton loadButton;
   private javax.swing.JCheckBox logLogCheckBox_;
   private javax.swing.JButton mathButton_;
   private javax.swing.JComboBox method2CBox_;
   private javax.swing.JButton pairsButton;
   private javax.swing.JTextField pairsMaxDistanceField_;
   private javax.swing.JButton plotButton_;
   private javax.swing.JComboBox plotComboBox_;
   private javax.swing.JCheckBox powerSpectrumCheckBox_;
   private javax.swing.JLabel referenceName_;
   private javax.swing.JButton removeButton;
   private javax.swing.JButton renderButton_;
   private javax.swing.JButton saveButton;
   private javax.swing.JComboBox saveFormatBox_;
   private javax.swing.JButton showButton_;
   private javax.swing.JTextField sigmaMax_;
   private javax.swing.JTextField sigmaMin_;
   private javax.swing.JButton straightenTrackButton_;
   private javax.swing.JButton unjitterButton_;
   private javax.swing.JComboBox visualizationMagnification_;
   private javax.swing.JComboBox visualizationModel_;
   private javax.swing.JButton zCalibrateButton_;
   private javax.swing.JLabel zCalibrationLabel_;
   // End of variables declaration//GEN-END:variables


   /**
    * Renders button with appropriate names
    */
   class ButtonRenderer extends JButton implements TableCellRenderer {

      public ButtonRenderer() {
         setOpaque(true);
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
          boolean isSelected, boolean hasFocus, int row, int column) {

         setForeground(table.getForeground());
         setBackground(UIManager.getColor("Button.background"));

         if (rowData_.get(row).isTrack_) {
            if (column == 4)
               setText((value == null ? "" : "Center"));
            else {
               if (column == 5)
                  setText((value == null ? "" : "Straighten"));
               else
                  setText((value == null ? "" : value.toString()));
            }
         } else {
            return null;
            //if (column == 4)
            //   setText((value == null ? "" : "Render"));
            //if (column == 5)
            //   return null;     
         }
             
         return this;
      }
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
         
         ResultsTableListener myk = new ResultsTableListener(siPlus, rt, win, rowData.halfSize_);
         tp.addKeyListener(myk);
         tp.addMouseListener(myk);
         frame.toFront();
      }
      
   }

   
   public JTable getResultsTable() {
      return jTable1_;
   }

   /**
    * Calculates the axis of motion of a given dataset and normalizes the data
    * to that axis.
    *
    * @rowData
    */
   private void straightenTrack(RowData rowData) {
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      for (SpotData gs : rowData.spotList_) {
         Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
         xyPoints.add(point);
      }

      // Calculate direction of travel and transform data set along this axis
      ArrayList<Point2D.Double> xyCorrPoints = GaussianUtils.pcaRotate(xyPoints);
      List<SpotData> transformedResultList =
              Collections.synchronizedList(new ArrayList<SpotData>());
      
      for (int i = 0; i < xyPoints.size(); i++) {
         SpotData oriSpot = rowData.spotList_.get(i);
         SpotData spot = new SpotData(oriSpot);
         spot.setData(oriSpot.getIntensity(), oriSpot.getBackground(),
                 xyCorrPoints.get(i).getX(), xyCorrPoints.get(i).getY(), 0.0, oriSpot.getWidth(),
                 oriSpot.getA(), oriSpot.getTheta(), oriSpot.getSigma());
         transformedResultList.add(spot);
      }

      // Add transformed data to data overview window
      addSpotData(rowData.name_ + "Straightened", rowData.title_, "", rowData.width_,
              rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
              rowData.shape_, rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
              rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
              rowData.timePoints_, true, Coordinates.NM, false, 0.0, 0.0);
   }
   
   
   /**
    * Creates a new dataset that is centered around the average of the X and Y data.
    * In other words, the average of both X and Y is calculated and subtracted from each datapoint
    *
    * @rowData
    */
   private void centerTrack(RowData rowData) {
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
      ArrayList<Point2D.Double> xyPoints = ListUtils.spotListToPointList(rowData.spotList_);
      Point2D.Double avgPoint = ListUtils.avgXYList(xyPoints);
          
      /*ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      Iterator it = rowData.spotList_.iterator();
      double totalX = 0.0;
      double totalY = 0.0;
      while (it.hasNext()) {
         SpotData gs = (SpotData) it.next();
         Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
         totalX += gs.getXCenter();
         totalY += gs.getYCenter();
         xyPoints.add(point);
      }
      
      double avgX = totalX / rowData.spotList_.size();
      double avgY = totalY / rowData.spotList_.size();
             * 
       */
      for (Point2D.Double xy : xyPoints) {
         xy.x = xy.x - avgPoint.x;
         xy.y = xy.y - avgPoint.y;
      }


      // create a copy of the dataset and copy in the corrected data
      List<SpotData> transformedResultList =
              Collections.synchronizedList(new ArrayList<SpotData>());
      
      for (int i = 0; i < xyPoints.size(); i++) {
         SpotData oriSpot = rowData.spotList_.get(i);
         SpotData spot = new SpotData(oriSpot);
         spot.setData(oriSpot.getIntensity(), oriSpot.getBackground(),
                 xyPoints.get(i).getX(), xyPoints.get(i).getY(), 0.0, oriSpot.getWidth(),
                 oriSpot.getA(), oriSpot.getTheta(), oriSpot.getSigma());
         transformedResultList.add(spot);
      }

      // Add transformed data to data overview window
      addSpotData(rowData.name_ + " Centered", rowData.title_, "", rowData.width_,
              rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
              rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
              rowData.nrFrames_, rowData.nrSlices_, 1, rowData.maxNrSpots_, 
              transformedResultList, rowData.timePoints_, true, Coordinates.NM, 
              false, 0.0, 0.0);
   }

   /**
    * Creates a new data set that is corrected for motion blur
    * Correction is performed by projecting a number of images onto a 
    * 2D scattergram and using cross-correlation between them to find
    * the displacement
    * 
    * @param rowData 
    */
   private void unJitter(final RowData rowData) {

      // TODO: instead of a fixed number of frames, go for a certain number of spots
      // Number of frames could be limited as well
      final int framesToCombine = 200;
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
           
      ij.IJ.showStatus("Executing jitter correction");
      
      Runnable doWorkRunnable = new Runnable() {
         
         @Override
         public void run() {
            
            int mag = (int) (rowData.pixelSizeNm_ / 40.0);
            while (mag % 2 != 0)
               mag += 1;
                        
            int width = mag * rowData.width_;
            int height = mag * rowData.height_;                        
            
            int size = width * height;
            
            
             // TODO: add 0 padding to deal with aberrant image sizes
            if ( (width != height) || ( (width & (width - 1)) != 0) ) {
               JOptionPane.showMessageDialog(getInstance(), 
                 "Magnified image is not a square with a size that is a power of 2");
               ij.IJ.showStatus(" ");
               return;
            }

            // TODO: what if we should go through nrSlices instead of nrFrames?
            boolean useSlices = false;
            int nrOfTests = rowData.nrFrames_ / framesToCombine;
            if (nrOfTests == 0) {
               useSlices = true;
               nrOfTests = rowData.nrSlices_ / framesToCombine;
               if (rowData.nrSlices_ % framesToCombine > 0) {
                  nrOfTests++;
               }
            } else {
               if (rowData.nrFrames_ % framesToCombine > 0) {
                  nrOfTests++;
               }
            }

            // storage of stage movement data
            class StageMovementData {
               
               Point2D.Double pos_;
               Point frameRange_;
               
               StageMovementData(Point2D.Double pos, Point frameRange) {
                  pos_ = pos;
                  frameRange_ = frameRange;
               }
            }
            ArrayList<StageMovementData> stagePos = new ArrayList<StageMovementData>();
            
            try {
               // make imageprocessors for all the images that we will generate
               ImageProcessor[] ip = new ImageProcessor[nrOfTests];
               byte[][] pixels = new byte[nrOfTests][width * height];
               
               for (int i = 0; i < nrOfTests; i++) {
                  ip[i] = new ByteProcessor(width, height);
                  ip[i].setPixels(pixels[i]);
               }
               
               double factor = (double) mag / rowData.pixelSizeNm_;

               // make 2D scattergrams of all pixelData
               for (SpotData spot : rowData.spotList_) {
                  int j;
                  if (useSlices) {
                     j = (spot.getSlice() - 1) / framesToCombine;
                  } else {
                     j = (spot.getFrame() - 1) / framesToCombine;
                  }
                  int x = (int) (factor * spot.getXCenter());
                  int y = (int) (factor * spot.getYCenter());
                  int index = (y * width) + x;
                  if (index < size && index > 0) {
                     if (pixels[j][index] != -1) {
                        pixels[j][index] += 1;
                     }
                  }
                  
               }
               
               JitterDetector jd = new JitterDetector(ip[0]);
               
               Point2D.Double fp = new Point2D.Double(0.0, 0.0);
               Point2D.Double com = new Point2D.Double(0.0, 0.0);
               
               jd.getJitter(ip[0], fp);
               
               for (int i = 1; i < ip.length; i++) {
                  ij.IJ.showStatus("Executing jitter correction..." + i);
                  ij.IJ.showProgress(i, ip.length);
                  int spotCount = 0;
                  for (int j=0; j < ip[i].getPixelCount(); j++) 
                     spotCount += ip[i].get(j);
                  
                  jd.getJitter(ip[i], com);
                  double x = (fp.x - com.x) / factor;
                  double y = (fp.y - com.y) / factor;
                  if (rowData.timePoints_ != null) {
                     rowData.timePoints_.get(i);
                  }
                  stagePos.add(new StageMovementData(new Point2D.Double(x, y),
                          new Point(i * framesToCombine, ((i + 1) * framesToCombine - 1))));
                  System.out.println("i: " + i + " nSpots: " + spotCount + " X: " + x + " Y: " + y);
               }
               
            } catch (OutOfMemoryError ex) {
               // not enough memory to allocate all images in one go
               // we need to cycle through all gaussian spots cycle by cycle

               double factor = (double) mag / rowData.pixelSizeNm_;
               
               ImageProcessor ipRef = new ByteProcessor(width, height);
               byte[] pixelsRef = new byte[width * height];
               ipRef.setPixels(pixelsRef);


               // take the first image as reference
               for (SpotData spot : rowData.spotList_) {
                  int j;
                  if (useSlices) {
                     j = (spot.getSlice() - 1) / framesToCombine;
                  } else {
                     j = (spot.getFrame() - 1) / framesToCombine;
                  }
                  if (j == 0) {
                     int x = (int) (factor * spot.getXCenter());
                     int y = (int) (factor * spot.getYCenter());
                     int index = (y * width) + x;
                     if (index < size && index > 0) {
                        if (pixelsRef[index] != -1) {
                           pixelsRef[index] += 1;
                        }
                     }
                  }
               }
               
               JitterDetector jd = new JitterDetector(ipRef);
               
               Point2D.Double fp = new Point2D.Double(0.0, 0.0);
               jd.getJitter(ipRef, fp);
               
               Point2D.Double com = new Point2D.Double(0.0, 0.0);
               ImageProcessor ipTest = new ByteProcessor(width, height);
               byte[] pixelsTest = new byte[width * height];
               ipTest.setPixels(pixelsTest);
               
               for (int i = 1; i < nrOfTests; i++) {
                  ij.IJ.showStatus("Executing jitter correction..." + i);
                  ij.IJ.showProgress(i, nrOfTests);
                  for (int p = 0; p < size; p++) {
                     ipTest.set(p, 0);
                  }
                  
                  for (SpotData spot : rowData.spotList_) {
                     int j;
                     if (useSlices) {
                        j = (spot.getSlice() - 1) / framesToCombine;
                     } else {
                        j = (spot.getFrame() - 1) / framesToCombine;
                     }
                     if (j == i) {
                        int x = (int) (factor * spot.getXCenter());
                        int y = (int) (factor * spot.getYCenter());
                        int index = (y * width) + x;
                        if (index < size && index > 0) {
                           if (pixelsTest[index] != -1) {
                              pixelsTest[index] += 1;
                           }
                        }
                     }
                  }
                  
                  jd.getJitter(ipTest, com);
                  double x = (fp.x - com.x) / factor;
                  double y = (fp.y - com.y) / factor;
                  double timePoint = i;
                  if (rowData.timePoints_ != null) {
                     rowData.timePoints_.get(i);
                  }
                  stagePos.add(new StageMovementData(new Point2D.Double(x, y),
                          new Point(i * framesToCombine, ((i + 1) * framesToCombine - 1))));
                  System.out.println("X: " + x + " Y: " + y);
               }
               
            }
            
            try {
               // Assemble stage movement data into a track
               List<SpotData> stageMovementData = new ArrayList<SpotData>();
               SpotData sm = new SpotData(null, 1, 1, 1, 1, 1, 1, 1);
               sm.setData(0, 0, 0, 0, 0.0, 0, 0, 0, 0);
               stageMovementData.add(sm);
               
               // calculate moving average for stageposition
               ArrayList<StageMovementData> stagePosMA = new ArrayList<StageMovementData>();
               int windowSize = 5;
               for (int i = 0; i < stagePos.size() - windowSize; i++) {
                  Point2D.Double avg = new Point2D.Double(0.0, 0.0);
                  for (int j = 0; j < windowSize; j++) {
                     avg.x += stagePos.get(i + j).pos_.x;
                     avg.y += stagePos.get(i + j).pos_.y;
                  }
                  avg.x /= windowSize;
                  avg.y /= windowSize;
                  
                  stagePosMA.add(new StageMovementData(avg, stagePos.get(i).frameRange_));
               }
               
               
               for (int i = 0; i < stagePosMA.size(); i++) {
                  StageMovementData smd = stagePosMA.get(i);
                  SpotData s =
                          new SpotData(null, 1, 1, i + 2, 1, 1, 1, 1);
                  s.setData(0, 0, smd.pos_.x, smd.pos_.y, 0.0, 0, 0, 0, 0);                  
                  stageMovementData.add(s);
               }

               // Add stage movement data to overview window
               // First try to copy the time points
               ArrayList<Double> timePoints = null;
               if (rowData.timePoints_ != null) {
                  timePoints = new ArrayList<Double>();
                  int tp = framesToCombine;
                  while (tp < rowData.timePoints_.size()) {
                     timePoints.add(rowData.timePoints_.get(tp));
                     tp += framesToCombine;
                  }
               }
               
               RowData newRow = new RowData(rowData.name_ + "-Jitter", 
                       rowData.title_, "", rowData.width_,rowData.height_, 
                       rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                       rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                       stageMovementData.size(),1, 1, stageMovementData.size(), 
                       stageMovementData, timePoints, true, Coordinates.NM, 
                       false, 0.0, 0.0);
               
               rowData_.add(newRow);
               
               myTableModel_.fireTableRowsInserted(rowData_.size() - 1, rowData_.size());
               
                                           
               ij.IJ.showStatus("Assembling jitter corrected dataset...");
               ij.IJ.showProgress(1);
               
               List<SpotData> correctedData = new ArrayList<SpotData>();
               Iterator it = rowData.spotList_.iterator();
               
               int testNr = 0;
               StageMovementData smd = stagePosMA.get(0);
               int counter = 0;
               while (it.hasNext()) {
                  counter++;
                  SpotData gs = (SpotData) it.next();
                  int test;
                  if (useSlices) {
                     test = gs.getSlice();
                  } else {
                     test = gs.getFrame();
                  }
                  if (test != testNr) {
                     testNr = test - 1;
                  }
                  boolean found = false;
                  if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                     found = true;
                  }
                  if (!found) {
                     for (int i = 0; i < stagePosMA.size() && !found; i++) {
                        smd = stagePosMA.get(i);
                        if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                           found = true;
                        }
                     }
                  }
                  if (found) {
                     Point2D.Double point = new Point2D.Double(gs.getXCenter() - smd.pos_.x,
                             gs.getYCenter() - smd.pos_.y);
                     SpotData gsn = new SpotData(gs);
                     gsn.setXCenter(point.x);
                     gsn.setYCenter(point.y);
                     correctedData.add(gsn);
                  } else {
                     correctedData.add(gs);
                  }
                  
                  
               }

               // Add transformed data to data overview window
               addSpotData(rowData.name_ + "-Jitter-Correct", rowData.title_, "", 
                       rowData.width_, rowData.height_, rowData.pixelSizeNm_, 
                       rowData.zStackStepSizeNm_, rowData.shape_, 
                       rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                       rowData.nrSlices_, 1, rowData.maxNrSpots_, correctedData,
                       null, false, Coordinates.NM, false, 0.0, 0.0);
               
               ij.IJ.showStatus("Finished jitter correction");
            } catch (OutOfMemoryError oom) {
              System.gc();
              ij.IJ.error("Out of Memory");
            }
         }
      };

      (new Thread(doWorkRunnable)).start();
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
                     gsn.setXCenter(corPoint.x);
                     gsn.setYCenter(corPoint.y);
                     correctedData.add(gsn);
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
   
   private boolean useSeconds(RowData row) {
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
    * @return 0 indicates success, 1 indicates failure and calling code should inform user, 2 indicates failure but calling code should not inform user
    */
   public int zCalibrate(int rowNr) {
      final double widthCutoff = 1000.0;
      final double maxVariance = 100000.0;
      final int minNrSpots = 1;
      
      
      zc_.clearDataPoints();
      
      RowData rd = rowData_.get(rowNr);
      if (rd.shape_ < 2) {
         JOptionPane.showMessageDialog(getInstance(), "Use Fit Parameters Dimension 2 or 3 for Z-calibration");
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
            
            //System.out.println("Frame: " + frameNr + ", X: " + (int) meanX + ", " + (int) varX + 
            //        ", Y: " + (int) meanY + ", " + (int) varY);
            
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


}
