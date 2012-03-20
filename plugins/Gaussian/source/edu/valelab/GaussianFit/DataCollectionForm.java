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

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import edu.ucsf.tsf.TaggedSpotsProtos.IntensityUnits;
import edu.ucsf.tsf.TaggedSpotsProtos.LocationUnits;
import edu.ucsf.tsf.TaggedSpotsProtos.FitMode;
import edu.ucsf.tsf.TaggedSpotsProtos.SpotList;
import edu.ucsf.tsf.TaggedSpotsProtos.Spot;

import ij.gui.StackWindow;
import ij.gui.YesNoCancelDialog;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.geom.Point2D;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;

import org.jfree.data.xy.XYSeries;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.NumberUtils;
import valelab.LocalWeightedMean;

/**
 *
 * @author Nico Stuurman
 */
public class DataCollectionForm extends javax.swing.JFrame {
   AbstractTableModel myTableModel_;
   private final String[] columnNames_ = {"ID", "Image", "Nr of spots", "2C Reference", 
      "Action1", "Action2"};
   private final String[] plotModes_ = {"t-X", "t-Y", "X-Y", "t-Int"};
   private final String[] renderModes_ = {"Points", "Gaussian"};
   private final String[] renderSizes_  = {"1x", "2x", "4x", "8x"};
   public final static String extension_ = ".tsf";
   // TODO: make this user-settable
   private final double MAXMATCHDISTANCE = 1000.0;
   
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
   
   ArrayList<MyRowData> rowData_;
   double[][][] colorCorrection_; // 2D array (one for each pixel) containing xy coordinates of
                                 // first image (0 and 1) and correction to second image (2 and 3)
   public static DataCollectionForm instance_ = null;
   private static LocalWeightedMean lwm_;
   
   Preferences prefs_;
   
   
   private static int rowDataID_ = 1;


   /**
    * Data structure for spotlists
    */
   public class MyRowData {
      public final List<GaussianSpotData> spotList_;
      public final ArrayList<Double> timePoints_;
      public final String name_;
      public final String title_;
      public final String colCorrRef_;
      public final int width_;
      public final int height_;
      public final float pixelSizeNm_;
      public final int shape_;
      public final int halfSize_;
      public final int nrChannels_;
      public final int nrFrames_;
      public final int nrSlices_;
      public final int nrPositions_;
      public final int maxNrSpots_;
      public final boolean isTrack_;
      public final int ID_;


      public MyRowData(String name,
              String title,
              String colCorrRef,
              int width,
              int height,
              float pixelSizeUm,
              int shape,
              int halfSize, 
              int nrChannels,
              int nrFrames,
              int nrSlices,
              int nrPositions,
              int maxNrSpots, 
              List<GaussianSpotData> spotList,
              ArrayList<Double> timePoints,
              boolean isTrack) {
         name_ = name;
         title_ = title;
         colCorrRef_ = colCorrRef;
         width_ = width;
         height_ = height;
         pixelSizeNm_ = pixelSizeUm;
         spotList_ = spotList;
         shape_ = shape;
         halfSize_ = halfSize;
         nrChannels_ = nrChannels;
         nrFrames_ = nrFrames;
         nrSlices_ = nrSlices;
         nrPositions_ = nrPositions;
         maxNrSpots_ = maxNrSpots;
         timePoints_ = timePoints;
         isTrack_ = isTrack;
         ID_ = rowDataID_;
         rowDataID_++;
      }
   }

   /**
    * Implement this class as a singleton
    *
    * @return the form
    */
   public static DataCollectionForm getInstance() {
      if (instance_ == null)
         instance_ =  new DataCollectionForm();
      return instance_;
   }

    /** 
     * Creates new form DataCollectionForm
     */
   private DataCollectionForm() {

      rowData_ = new ArrayList<MyRowData>();

      myTableModel_ = new AbstractTableModel() {
         @Override
          public String getColumnName(int col) {
              return columnNames_[col].toString();
          }
          public int getRowCount() {
             if (rowData_ == null)
                return 0;
             return rowData_.size();
          }
          public int getColumnCount() { 
             return columnNames_.length;
          }
          public Object getValueAt(int row, int col) {
             if (col == 0 && rowData_ != null)
                return rowData_.get(row).ID_;
             else if (col == 1 && rowData_ != null)
                return rowData_.get(row).name_;
             else if (col == 2)
                return rowData_.get(row).spotList_.size();
             else if (col == 3)
                return rowData_.get(row).colCorrRef_;
             else
                return getColumnName(col);
          }
         @Override
          public boolean isCellEditable(int row, int col) {
            return false;
          }
         @Override
          public void setValueAt(Object value, int row, int col) {
              fireTableCellUpdated(row, col);
          }
       };

       initComponents();
       referenceName_.setText("");
       jTable1_.addMouseListener(new LocalMouseListener());
       jTable1_.getColumn("Action1").setCellRenderer(new ButtonRenderer());
       jTable1_.getColumn("Action2").setCellRenderer(new ButtonRenderer());
       plotComboBox_.setModel(new javax.swing.DefaultComboBoxModel(plotModes_));
       visualizationModel_.setModel(new javax.swing.DefaultComboBoxModel(renderModes_));
       visualizationMagnification_.setModel(new javax.swing.DefaultComboBoxModel(renderSizes_));
       jScrollPane1.setName("Gaussian Spot Fitting Data Sets");
       
       setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
       MMStudioMainFrame.getInstance().addMMBackgroundListener(this);
       
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
             
       setVisible(true);
   }

   /**
    * Use a Mouse listener instead of buttons, since buttons in a table are a pain
    */
   private class LocalMouseListener extends MouseAdapter {
      @Override
      public void mouseClicked(MouseEvent me) {
         Point click = new Point(me.getX(), me.getY());
         int column = jTable1_.columnAtPoint(click);
         int row = jTable1_.rowAtPoint(click);
         
         if (column == 4) {
            if (rowData_.get(row).isTrack_) {
               centerTrack(rowData_.get(row));
               //MyRowData[] myRD = new MyRowData[1];
               //myRD[0] = rowData_.get(row);
               //plotData(myRD, plotComboBox_.getSelectedIndex());
            }
            else {
               /*
               int mag = 1 << visualizationMagnification_.getSelectedIndex();
               SpotDataFilter sf = new SpotDataFilter();
               if (filterSigmaCheckBox_.isSelected())
                  sf.setSigma(true, Double.parseDouble(sigmaMin_.getText()), 
                          Double.parseDouble(sigmaMax_.getText()) );
               if (filterIntensityCheckBox_.isSelected())
                  sf.setIntensity(true, Double.parseDouble(intensityMin_.getText()), 
                          Double.parseDouble(intensityMax_.getText()) );
               ImageRenderer.renderData(null, rowData_.get(row), visualizationModel_.getSelectedIndex(),
                       mag, null, sf);
                * 
                */
            }
         }
         else if (column == 5) {
            if (rowData_.get(row).isTrack_)
               straightenTrack(rowData_.get(row));
         }

      }
   }


   /**
    * Adds a spot data set to the form
    *
    *
    * @param name
    * @param title
    * @param width
    * @param height
    * @param pixelSizeUm
    * @param shape
    * @param halfSize
    * @param nrChannels
    * @param nrFrames
    * @param nrSlices
    * @param nrPositions
    * @param maxNrSpots
    * @param spotList
    * @param isTrack
    */
   public void addSpotData(
           String name,
           String title,
           String colCorrRef,
           int width,
           int height,
           float pixelSizeUm,
           int shape,
           int halfSize,
           int nrChannels,
           int nrFrames,
           int nrSlices,
           int nrPositions,
           int maxNrSpots, 
           List<GaussianSpotData> spotList,
           ArrayList<Double> timePoints,
           boolean isTrack) {
      MyRowData newRow = new MyRowData(name, title, colCorrRef, width, height, pixelSizeUm, 
              shape, halfSize, nrChannels, nrFrames, nrSlices, nrPositions, 
              maxNrSpots, spotList, timePoints, isTrack);
      rowData_.add(newRow);
      myTableModel_.fireTableRowsInserted(rowData_.size()-1, rowData_.size());
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

        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1_ = new javax.swing.JTable();
        loadButton = new javax.swing.JButton();
        plotComboBox_ = new javax.swing.JComboBox();
        visualizationMagnification_ = new javax.swing.JComboBox();
        visualizationModel_ = new javax.swing.JComboBox();
        saveButton = new javax.swing.JButton();
        removeButton = new javax.swing.JButton();
        showButton_ = new javax.swing.JButton();
        c2StandardButton = new javax.swing.JButton();
        pairsButton = new javax.swing.JButton();
        c2CorrectButton = new javax.swing.JButton();
        referenceName_ = new javax.swing.JLabel();
        unjitterButton_ = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        filterSigmaCheckBox_ = new javax.swing.JCheckBox();
        filterIntensityCheckBox_ = new javax.swing.JCheckBox();
        sigmaMin_ = new javax.swing.JTextField();
        intensityMin_ = new javax.swing.JTextField();
        sigmaMax_ = new javax.swing.JTextField();
        intensityMax_ = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        SigmaLabel2 = new javax.swing.JLabel();
        IntLabel2 = new javax.swing.JLabel();
        infoButton_ = new javax.swing.JButton();
        plotButton_ = new javax.swing.JButton();
        renderButton_ = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        jSeparator3 = new javax.swing.JSeparator();
        jComboBox1 = new javax.swing.JComboBox();
        jSeparator4 = new javax.swing.JSeparator();
        averageTrackButton_ = new javax.swing.JButton();
        mathButton_ = new javax.swing.JButton();
        pairsMaxDistanceField_ = new javax.swing.JTextField();
        SigmaLabel3 = new javax.swing.JLabel();

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
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jTable1_.setModel(myTableModel_);
        jScrollPane1.setViewportView(jTable1_);

        getContentPane().add(jScrollPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 84, 940, 680));

        loadButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        loadButton.setText("Load");
        loadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadButtonActionPerformed(evt);
            }
        });
        getContentPane().add(loadButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 10, 80, 19));

        plotComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        plotComboBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "t-X", "t-Y", "X-Y", "t-Int.", " " }));
        getContentPane().add(plotComboBox_, new org.netbeans.lib.awtextra.AbsoluteConstraints(460, 30, 67, -1));

        visualizationMagnification_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        visualizationMagnification_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1x", "2x", "4x", "8x" }));
        getContentPane().add(visualizationMagnification_, new org.netbeans.lib.awtextra.AbsoluteConstraints(710, 10, 90, 20));

        visualizationModel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        visualizationModel_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Gaussian" }));
        getContentPane().add(visualizationModel_, new org.netbeans.lib.awtextra.AbsoluteConstraints(800, 10, -1, 20));

        saveButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        saveButton.setText("Save");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });
        getContentPane().add(saveButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 10, -1, 19));

        removeButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        removeButton.setText("Remove");
        removeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeButtonActionPerformed(evt);
            }
        });
        getContentPane().add(removeButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 50, 70, 19));

        showButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        showButton_.setText("Show");
        showButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showButton_ActionPerformed(evt);
            }
        });
        getContentPane().add(showButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 30, 80, 19));

        c2StandardButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        c2StandardButton.setText("2C Reference");
        c2StandardButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                c2StandardButtonActionPerformed(evt);
            }
        });
        getContentPane().add(c2StandardButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(270, 10, 82, 19));

        pairsButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        pairsButton.setText("Pairs");
        pairsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pairsButtonActionPerformed(evt);
            }
        });
        getContentPane().add(pairsButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(360, 30, -1, 19));

        c2CorrectButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        c2CorrectButton.setText("2C Correct");
        c2CorrectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                c2CorrectButtonActionPerformed(evt);
            }
        });
        getContentPane().add(c2CorrectButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(270, 30, 82, 19));

        referenceName_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        referenceName_.setText("jLabel1");
        getContentPane().add(referenceName_, new org.netbeans.lib.awtextra.AbsoluteConstraints(360, 10, 60, -1));

        unjitterButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        unjitterButton_.setText("Drift Correct");
        unjitterButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unjitterButton_ActionPerformed(evt);
            }
        });
        getContentPane().add(unjitterButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 50, 80, 19));

        jLabel1.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        jLabel1.setText("Filters:");
        getContentPane().add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(620, 40, -1, -1));

        filterSigmaCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        filterSigmaCheckBox_.setText("Sigma");
        filterSigmaCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterSigmaCheckBox_ActionPerformed(evt);
            }
        });
        getContentPane().add(filterSigmaCheckBox_, new org.netbeans.lib.awtextra.AbsoluteConstraints(660, 40, -1, 20));

        filterIntensityCheckBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        filterIntensityCheckBox_.setText("Intensity");
        filterIntensityCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterIntensityCheckBox_ActionPerformed(evt);
            }
        });
        getContentPane().add(filterIntensityCheckBox_, new org.netbeans.lib.awtextra.AbsoluteConstraints(660, 60, -1, -1));

        sigmaMin_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        sigmaMin_.setText("0");
        getContentPane().add(sigmaMin_, new org.netbeans.lib.awtextra.AbsoluteConstraints(740, 40, 47, 20));

        intensityMin_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        intensityMin_.setText("0");
        getContentPane().add(intensityMin_, new org.netbeans.lib.awtextra.AbsoluteConstraints(740, 58, 46, 20));

        sigmaMax_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        sigmaMax_.setText("0");
        getContentPane().add(sigmaMax_, new org.netbeans.lib.awtextra.AbsoluteConstraints(840, 40, 57, 17));

        intensityMax_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        intensityMax_.setText("0");
        getContentPane().add(intensityMax_, new org.netbeans.lib.awtextra.AbsoluteConstraints(840, 60, 59, 17));

        jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        jLabel2.setText("< spot <");
        getContentPane().add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(790, 60, -1, -1));

        jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        jLabel3.setText("< spot <");
        getContentPane().add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(790, 40, -1, -1));

        SigmaLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        SigmaLabel2.setText("nm");
        getContentPane().add(SigmaLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(420, 50, -1, 20));

        IntLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        IntLabel2.setText("#");
        getContentPane().add(IntLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(900, 60, 10, -1));

        infoButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        infoButton_.setText("Info");
        infoButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                infoButton_ActionPerformed(evt);
            }
        });
        getContentPane().add(infoButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 30, -1, 19));

        plotButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        plotButton_.setText("Plot");
        plotButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotButton_ActionPerformed(evt);
            }
        });
        getContentPane().add(plotButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(460, 10, 67, 18));

        renderButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        renderButton_.setText("Render");
        renderButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renderButton_ActionPerformed(evt);
            }
        });
        getContentPane().add(renderButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(620, 10, -1, 19));

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
        getContentPane().add(jSeparator2, new org.netbeans.lib.awtextra.AbsoluteConstraints(450, 0, -1, 84));

        jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);
        getContentPane().add(jSeparator3, new org.netbeans.lib.awtextra.AbsoluteConstraints(260, 0, -1, 84));

        jComboBox1.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Binary", "Text" }));
        getContentPane().add(jComboBox1, new org.netbeans.lib.awtextra.AbsoluteConstraints(170, 10, 88, 18));

        jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);
        getContentPane().add(jSeparator4, new org.netbeans.lib.awtextra.AbsoluteConstraints(600, 0, -1, 84));

        averageTrackButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        averageTrackButton_.setText("Average");
        averageTrackButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                averageTrackButton_ActionPerformed(evt);
            }
        });
        getContentPane().add(averageTrackButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(530, 10, 60, 20));

        mathButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        mathButton_.setText("Math");
        getContentPane().add(mathButton_, new org.netbeans.lib.awtextra.AbsoluteConstraints(530, 30, 60, 20));

        pairsMaxDistanceField_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
        pairsMaxDistanceField_.setText("100");
        getContentPane().add(pairsMaxDistanceField_, new org.netbeans.lib.awtextra.AbsoluteConstraints(360, 50, 60, 20));

        SigmaLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        SigmaLabel3.setText("nm");
        getContentPane().add(SigmaLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(907, 42, -1, -1));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Loads data saved in TSF format (Tagged Spot File Format)
     *
     * @evt
     */
    private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed
      
      FileDialog fd = new FileDialog(this, "Load Spot Data", FileDialog.LOAD);
      fd.setVisible(true);
      String selectedItem = fd.getFile();
      if (selectedItem == null) {
         return;
      } else {
         final File selectedFile = new File( fd.getDirectory() + File.separator +
		        fd.getFile());
         
          Runnable doWorkRunnable = new Runnable() {

             public void run() {

                SpotList psl = null;
                try {
                   
                   ij.IJ.showStatus("Loading data..");

                   setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                   FileInputStream fi = new FileInputStream(selectedFile);
                   DataInputStream di = new DataInputStream(fi);
                   
                   // the new file format has an initial 0, then the offset (in long)
                   // to the position of spotList
                   int magic = di.readInt();
                   if (magic != 0) {
                      // reset and mark do not seem to work on my computer
                      fi.close();
                      fi = new FileInputStream(selectedFile);
                      psl = SpotList.parseDelimitedFrom(fi);
                   } else {
                      // TODO: evaluate after creating code writing this formt
                      long offset = di.readLong();
                      fi.skip(offset);
                      psl = SpotList.parseDelimitedFrom(fi);
                      fi.close();
                      fi = new FileInputStream(selectedFile);
                      fi.skip(12); // size of int + size of long
                   }
                   
                   
                   String name = psl.getName();
                   String title = psl.getName();
                   int width = psl.getNrPixelsX();
                   int height = psl.getNrPixelsY();
                   float pixelSizeUm = psl.getPixelSize();
                   int shape = 1;
                   if (psl.getFitMode() == FitMode.TWOAXIS) {
                      shape = 2;
                   } else if (psl.getFitMode() == FitMode.TWOAXISANDTHETA) {
                      shape = 3;
                   }
                   int halfSize = psl.getBoxSize() / 2;
                   int nrChannels = psl.getNrChannels();
                   int nrFrames = psl.getNrFrames();
                   int nrSlices = psl.getNrSlices();
                   int nrPositions = psl.getNrPos();
                   boolean isTrack = psl.getIsTrack();
                   long expectedSpots = psl.getNrSpots();
                   long esf = expectedSpots / 100;
                   long maxNrSpots = 0;


                   ArrayList<GaussianSpotData> spotList = new ArrayList<GaussianSpotData>();
                   Spot pSpot;
                   while (fi.available() > 0 && (expectedSpots == 0 || maxNrSpots < expectedSpots) ) {
                      
                      pSpot = Spot.parseDelimitedFrom(fi);

                      GaussianSpotData gSpot = new GaussianSpotData((ImageProcessor) null, pSpot.getChannel(),
                              pSpot.getSlice(), pSpot.getFrame(), pSpot.getPos(),
                              pSpot.getMolecule(), pSpot.getXPosition(), pSpot.getYPosition());
                      gSpot.setData(pSpot.getIntensity(), pSpot.getBackground(), pSpot.getX(),
                              pSpot.getY(), pSpot.getWidth(), pSpot.getA(), pSpot.getTheta(),
                              pSpot.getXPrecision());
                      maxNrSpots++;
                      if ( (esf > 0) &&  ( ( maxNrSpots % esf) == 0) )
                         ij.IJ.showProgress((double)maxNrSpots / (double) expectedSpots);

                      spotList.add(gSpot);
                   }

                   addSpotData(name, title, "", width, height, pixelSizeUm, shape, halfSize,
                           nrChannels, nrFrames, nrSlices, nrPositions, (int) maxNrSpots,
                           spotList, null, isTrack);

                } catch (FileNotFoundException ex) {
                   ij.IJ.error("File not found");
                } catch (IOException ex) {
                   ij.IJ.error("Error while reading file");
                } finally {
                   setCursor(Cursor.getDefaultCursor());
                   ij.IJ.showStatus("");
                   ij.IJ.showProgress(1.0);
                }
             }
          };
          
          (new Thread(doWorkRunnable)).start();
       }
          
    }//GEN-LAST:event_loadButtonActionPerformed

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
       int row = jTable1_.getSelectedRow();
       if (row > -1)
         saveData(rowData_.get(row));
       else
          JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to save");
    }//GEN-LAST:event_saveButtonActionPerformed

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed
       int rows[] = jTable1_.getSelectedRows();
       if (rows.length > 0) {
          for (int row = rows.length -1; row >= 0; row--) {
             rowData_.remove(rows[row]);
             myTableModel_.fireTableRowsDeleted(rows[row], rows[row]);
          }
       } else
         JOptionPane.showMessageDialog(getInstance(), "No dataset selected");
    }//GEN-LAST:event_removeButtonActionPerformed

    private void showButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showButton_ActionPerformed
       int row = jTable1_.getSelectedRow();
       if (row > -1)
         showResults(rowData_.get(row));
       else
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to show");
    }//GEN-LAST:event_showButton_ActionPerformed

   private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
      //jScrollPane1.setSize(this.getSize());
      Dimension d = getSize();
      d.height -= 60;
      jScrollPane1.setSize(d);
      jScrollPane1.getViewport().setViewSize(d);
   }//GEN-LAST:event_formComponentResized

   /**
    * Use the selected data set as the reference for 2-channel color correction
    * @param evt 
    */
   private void c2StandardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_c2StandardButtonActionPerformed
      int row = jTable1_.getSelectedRow();
      if (row < 0)
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset as color reference");
      else {
         // Get points from both channels in first frame as ArrayLists        
         ArrayList<Point2D.Double> xyPointsCh1 = new ArrayList<Point2D.Double>();
         ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
         Iterator it = rowData_.get(row).spotList_.iterator();
         while (it.hasNext()) {
            GaussianSpotData gs = (GaussianSpotData) it.next();
            if (gs.getFrame() == 1) {
               Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
               if (gs.getChannel() == 1)
                  xyPointsCh1.add(point);
               else if (gs.getChannel() == 2)
                  xyPointsCh2.add(point);
            }
         }
         
         if (xyPointsCh2.size() == 0) {
            JOptionPane.showMessageDialog(getInstance(), "No points found in second channel.  Is this a dual channel dataset?");
            return;
         }
         
         
         // Find matching points in the two ArrayLists
         Iterator it2 = xyPointsCh1.iterator();
         Map points = new HashMap<Point2D.Double,Point2D.Double>();
         NearestPoint2D np = new NearestPoint2D(xyPointsCh2, MAXMATCHDISTANCE);
         
         while (it2.hasNext()) {
            Point2D.Double pCh1 = (Point2D.Double) it2.next();
            Point2D.Double pCh2 = np.findKDWSE(pCh1);
            if (pCh2 != null) {
               points.put(pCh1, pCh2);
            }
         }
         try {
            lwm_ = new LocalWeightedMean(2, points);
            referenceName_.setText("ID: " + rowData_.get(row).ID_);
         } catch (Exception ex) {
            JOptionPane.showMessageDialog(getInstance(), "Error setting color reference.  Did you have enough input points?");
            return;
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
               ij.ImageStack  stack = new ij.ImageStack(width, height); 
               
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
                  ArrayList<Point2D.Double> xyPointsCh1 = new ArrayList<Point2D.Double>();
                  ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                  Iterator it = rowData_.get(row).spotList_.iterator();
                  while (it.hasNext()) {
                     GaussianSpotData gs = (GaussianSpotData) it.next();
                     if (gs.getFrame() == frame) {
                        Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        if (gs.getChannel() == 1) {
                           xyPointsCh1.add(point);
                        } else if (gs.getChannel() == 2) {
                           xyPointsCh2.add(point);
                        }
                     }
                  }
                  
                  if (xyPointsCh2.size() == 0) {
                     JOptionPane.showMessageDialog(getInstance(), "No points found in second channel.  Is this a 2-channel dataset?");
                     return;
                  }
                     

                  // Find matching points in the two ArrayLists
                  Iterator it2 = xyPointsCh1.iterator();
                  try {
                     NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                             NumberUtils.displayStringToDouble(pairsMaxDistanceField_.getText()));


                     ArrayList<Double> distances = new ArrayList<Double>();
                     ArrayList<Double> errorX = new ArrayList<Double>();
                     ArrayList<Double> errorY = new ArrayList<Double>();

                     while (it2.hasNext()) {
                        Point2D.Double pCh1 = (Point2D.Double) it2.next();
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
                        if (pCh2 != null) {
                           rt.incrementCounter();
                           rt.addValue("X1", pCh1.getX());
                           rt.addValue("Y1", pCh1.getY());
                           rt.addValue("X2", pCh2.getX());
                           rt.addValue("Y2", pCh2.getY());
                           double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                           double d = Math.sqrt(d2);
                           rt.addValue("Distance", d);
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
                     }
                     xData.add(timePoint, avgX);
                     yData.add(timePoint, avgY);

                  } catch (ParseException ex) {
                     JOptionPane.showMessageDialog(getInstance(), "Error in Pairs input");
                     return;
                  }

               }

               rt2.show("Summary of Pairs found in " + rowData_.get(row).name_);
               rt.show("Pairs found in " + rowData_.get(row).name_);

               String yAxis = "Time (frameNr)";
               if (rowData_.get(row).timePoints_ != null) {
                  yAxis = "Time (s)";
               }
               GaussianUtils.plotData2("Error", xData, yData, yAxis, "Error(nm)", 0, 400);

               ij.IJ.showStatus("");

               sp.setOpenAsHyperStack(true);
               sp.setStack(stack, 1, 1, rowData_.get(row).nrFrames_);
               sp.setDisplayRange(0, 20);
               //sp.setSlice(1);
               //sp.resetStack();

               ImageWindow w = new StackWindow(sp);

               w.setImage(sp);
               w.setVisible(true);

            }
         };

         (new Thread(doWorkRunnable)).start();

      }
   }//GEN-LAST:event_pairsButtonActionPerformed

   private static double listAvg (ArrayList<Double> list) {
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
   private static double listStdDev (ArrayList<Double> list, double avg) {
      
      double errorsSquared = 0;
      Iterator it = list.iterator();
      while (it.hasNext()) {
         double error = (Double) it.next() - avg;
         errorsSquared += (error * error);
      }
      return Math.sqrt(errorsSquared / (list.size() - 1) ) ;
   }
   
   
   /**
    * Utility function to calculate 
    * @param list
    * @return 
    */
   private static double listStdDev (ArrayList<Double> list) {
      double avg = listAvg(list);
      
      return listStdDev(list, avg);

   }
   
   
   private void c2CorrectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_c2CorrectButtonActionPerformed
      int row = jTable1_.getSelectedRow();
      if (row > -1) {     
         correct2C(rowData_.get(row));
      } else
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to color correct");
   }//GEN-LAST:event_c2CorrectButtonActionPerformed

   private void unjitterButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unjitterButton_ActionPerformed
      int row = jTable1_.getSelectedRow();
      if (row > -1) {     
         unJitter(rowData_.get(row));
      } else
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to unjitter");
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
          
         
         MyRowData rowData = rowData_.get(row);
         String data = "Name: " + rowData.name_ + "\n" +
                 "Title: " + rowData.title_ + "\n" + 
                 "BoxSize: " + 2*rowData.halfSize_ + "\n" +
                 "Image Height (pixels): " + rowData.height_ + "\n" + 
                 "Image Width (pixels): " + rowData.width_ + "\n" +
                 "Nr. of Spots: " + rowData.maxNrSpots_ + "\n" +
                 "Pixel Size (nm): " + rowData.pixelSizeNm_ + "\n" +
                 "Nr. of Channels: " + rowData.nrChannels_ + "\n" +
                 "Nr. of Frames: " + rowData.nrFrames_ + "\n" + 
                 "Nr. of Slices: " + rowData.nrSlices_ + "\n" +
                 "Nr. of Positions: " + rowData.nrPositions_ + "\n" +
                 "Is a Track: " + rowData.isTrack_;
         
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
      int row = jTable1_.getSelectedRow();
      if (row < 0) {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to render");
      } else {
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
         ImageRenderer.renderData(null, rowData_.get(row), visualizationModel_.getSelectedIndex(),
                 mag, null, sf);
      }
   }//GEN-LAST:event_renderButton_ActionPerformed

   private void plotButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select one or more datasets to plot");
      } else {
         MyRowData[] myRows = new MyRowData[rows.length];
         // TODO: check that these are tracks 
         for (int i = 0; i < rows.length; i++)
            myRows[i] = rowData_.get(rows[i]);
         plotData(myRows, plotComboBox_.getSelectedIndex());
      }
   }//GEN-LAST:event_plotButton_ActionPerformed

   private ArrayList<Point2D.Double> rowToList(MyRowData myRow){
      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      Iterator it = myRow.spotList_.iterator();
      while (it.hasNext()) {
         GaussianSpotData gs = (GaussianSpotData) it.next();
         Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
         xyPoints.add(point);
      }
      return xyPoints;
   }
   
   private Point2D.Double avgXYList(ArrayList<Point2D.Double> xyPoints) {
      Point2D.Double myAvg = new Point2D.Double(0.0, 0.0);
      for (Point2D.Double point : xyPoints) {
         myAvg.x += point.x;
         myAvg.y += point.y;
      }
      
      myAvg.x = myAvg.x / xyPoints.size();
      myAvg.y = myAvg.y / xyPoints.size();
      
      return myAvg;
   }
   
   
   private void averageTrackButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_averageTrackButton_ActionPerformed
      int rows[] = jTable1_.getSelectedRows();
      if (rows.length < 1) {
         JOptionPane.showMessageDialog(getInstance(), 
                 "Please select one or more datasets to average");
      } else {
         MyRowData[] myRows = new MyRowData[rows.length];
         ArrayList<Point2D.Double>[] xyPoints = (ArrayList<Point2D.Double>[]) new ArrayList[rows.length];
         
         for (int i = 0; i < rows.length; i++) {
            myRows[i] = rowData_.get(rows[i]);
            xyPoints[i] = rowToList(myRows[i]);
            Point2D.Double listAvg = avgXYList(xyPoints[i]);
            for (Point2D.Double xy : xyPoints[i]) {
               xy.x = xy.x - listAvg.x;
               xy.y = xy.y - listAvg.y;
            }
         }
         
         // we have all tracks centered, now average them
         
         // Make sure that all tracks are the same length (we could deal with 
         // tracks of different lengths, but it will complicate the code)
         int trackLength = xyPoints[0].size();
         for (int i = 1; i < xyPoints.length; i++) {
            if (xyPoints[i].size() != trackLength) {
               JOptionPane.showMessageDialog(getInstance(), "Tracks need to be of same length");
            }             
         }
         
         ArrayList<Point2D.Double> avgPoints = new ArrayList<Point2D.Double>();
         ArrayList<Point2D.Double> stdDevPoints = new ArrayList<Point2D.Double>();
         
         for (int i = 0; i < xyPoints[0].size(); i++) {
            ArrayList<Double> xp = new ArrayList<Double>();
            ArrayList<Double> yp = new ArrayList<Double>();
            for (int j = 0; j < xyPoints.length; j++) {
               xp.add(xyPoints[j].get(i).x);
               yp.add(xyPoints[j].get(i).y);
            }
            avgPoints.add(new Point2D.Double(listAvg(xp), listAvg(yp)) );
            stdDevPoints.add(new Point2D.Double(listStdDev(xp), listStdDev(yp)));
         }

         // create a copy of the dataset and copy in the corrected data
         List<GaussianSpotData> transformedResultList =
                 Collections.synchronizedList(new ArrayList<GaussianSpotData>());

         for (int i = 0; i < avgPoints.size(); i++) {
            GaussianSpotData oriSpot = myRows[0].spotList_.get(i);
            GaussianSpotData spot = new GaussianSpotData(oriSpot);
            spot.setData(0.0, 0.0, avgPoints.get(i).getX(), 
                    avgPoints.get(i).getY(), 0.0, 0.0, 0.0, 0.0 );
            transformedResultList.add(spot);
         }

         // Add transformed data to data overview window
         MyRowData rowData = myRows[0];
         addSpotData(rowData.name_ + " Average", rowData.title_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.shape_,
                 rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                 rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
                 rowData.timePoints_, true);

         // Since there is no place for stdev, also show a resultsTable
         ResultsTable rt = new ResultsTable();
         for (int i = 0; i < avgPoints.size(); i++) {
                        rt.incrementCounter();
            rt.addValue("XAvg", avgPoints.get(i).x);
            rt.addValue("XStdev", stdDevPoints.get(i).x);
            rt.addValue("YAvg", avgPoints.get(i).y);
            rt.addValue("YStdev", stdDevPoints.get(i).y);

         }
         rt.show("Averaged Tracks");
         

      }

   }//GEN-LAST:event_averageTrackButton_ActionPerformed

   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel IntLabel2;
    private javax.swing.JLabel SigmaLabel2;
    private javax.swing.JLabel SigmaLabel3;
    private javax.swing.JButton averageTrackButton_;
    private javax.swing.JButton c2CorrectButton;
    private javax.swing.JButton c2StandardButton;
    private javax.swing.JCheckBox filterIntensityCheckBox_;
    private javax.swing.JCheckBox filterSigmaCheckBox_;
    private javax.swing.JButton infoButton_;
    private javax.swing.JTextField intensityMax_;
    private javax.swing.JTextField intensityMin_;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JTable jTable1_;
    private javax.swing.JButton loadButton;
    private javax.swing.JButton mathButton_;
    private javax.swing.JButton pairsButton;
    private javax.swing.JTextField pairsMaxDistanceField_;
    private javax.swing.JButton plotButton_;
    private javax.swing.JComboBox plotComboBox_;
    private javax.swing.JLabel referenceName_;
    private javax.swing.JButton removeButton;
    private javax.swing.JButton renderButton_;
    private javax.swing.JButton saveButton;
    private javax.swing.JButton showButton_;
    private javax.swing.JTextField sigmaMax_;
    private javax.swing.JTextField sigmaMin_;
    private javax.swing.JButton unjitterButton_;
    private javax.swing.JComboBox visualizationMagnification_;
    private javax.swing.JComboBox visualizationModel_;
    // End of variables declaration//GEN-END:variables


   /**
    * Renders button with appropriate names
    */
   class ButtonRenderer extends JButton implements TableCellRenderer {

      public ButtonRenderer() {
         setOpaque(true);
      }

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
   private void showResults(MyRowData rowData) {
      // Copy data to results table

      ResultsTable rt = new ResultsTable();
      rt.reset();
      rt.setPrecision(1);
      int shape = rowData.shape_;
      for (GaussianSpotData gd : rowData.spotList_) {
         if (gd != null) {
            rt.incrementCounter();
            rt.addValue(Terms.FRAME, gd.getFrame());
            rt.addValue(Terms.SLICE, gd.getSlice());
            rt.addValue(Terms.CHANNEL, gd.getChannel());
            rt.addValue(Terms.POSITION, gd.getPosition());
            rt.addValue(Terms.INT, gd.getIntensity());
            rt.addValue(Terms.BACKGROUND, gd.getBackground());
            rt.addValue(Terms.XNM, gd.getXCenter());
            rt.addValue(Terms.YNM, gd.getYCenter());
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

   /**
    * Save data set in TSF (Tagged Spot File) format
    *
    * @rowData - row with spot data to be saved
    */
   private void saveData(final MyRowData rowData) {
      FileDialog fd = new FileDialog(this, "Save Spot Data", FileDialog.SAVE);
      fd.setVisible(true);
      String selectedItem = fd.getFile();
      if (selectedItem == null) {
         return;
      } else {
         String fn = fd.getFile();
         if (!fn.contains(".")) {
            fn = fn + extension_;
         }
         final File selectedFile = new File(fd.getDirectory() + File.separator + fn);
         if (selectedFile.exists()) {
            // this may be superfluous
            YesNoCancelDialog y = new YesNoCancelDialog(this, "File " + fn + "Exists...", "File exists.  Overwrite?");
            if (y.cancelPressed()) {
               return;
            }
            if (!y.yesPressed()) {
               saveData(rowData);
               return;
            }
         }
         
         
         
         Runnable doWorkRunnable = new Runnable() {
            
            public void run() {
               
               SpotList.Builder tspBuilder = SpotList.newBuilder();
               tspBuilder.setApplicationId(1).
                       setName(rowData.name_).
                       setFilepath(rowData.title_).
                       setNrPixelsX(rowData.width_).
                       setNrPixelsY(rowData.height_).
                       setNrSpots(rowData.spotList_.size()).
                       setPixelSize(160).
                       setBoxSize(rowData.halfSize_ * 2).
                       setNrChannels(rowData.nrChannels_).
                       setNrSlices(rowData.nrSlices_).
                       setIsTrack(rowData.isTrack_).
                       setNrPos(rowData.nrPositions_).
                       setNrFrames(rowData.nrFrames_).
                       setLocationUnits(LocationUnits.NM).
                       setIntensityUnits(IntensityUnits.PHOTONS).
                       setNrSpots(rowData.maxNrSpots_);
               switch (rowData.shape_) {
                  case (1):
                     tspBuilder.setFitMode(FitMode.ONEAXIS);
                     break;
                  case (2):
                     tspBuilder.setFitMode(FitMode.TWOAXIS);
                     break;
                  case (3):
                     tspBuilder.setFitMode(FitMode.TWOAXISANDTHETA);
                     break;
               }
               
               
               SpotList spotList = tspBuilder.build();
               try {
                  setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                  
                  FileOutputStream fo = new FileOutputStream(selectedFile);
                  // write space for magic nr and offset to spotList
                  for (int i = 0; i < 12; i++) {
                     fo.write(0);
                  }
                  
                  
                  
                  int counter = 0;
                  for (GaussianSpotData gd : rowData.spotList_) {
                     
                     if ((counter % 1000) == 0) {                        
                        ij.IJ.showStatus("Saving spotData...");
                        ij.IJ.showProgress(counter, rowData.spotList_.size());
                     }
                     
                     if (gd != null) {
                        Spot.Builder spotBuilder = Spot.newBuilder();
                        // TODO: precede all these calls with check for presence of member
                        // or be OK with default values?
                        spotBuilder.setMolecule(counter).
                                setFrame(gd.getFrame()).
                                setChannel(gd.getChannel()).
                                setPos(gd.getPosition()).
                                setSlice(gd.getSlice()).
                                setX((float) gd.getXCenter()).
                                setY((float) gd.getYCenter()).
                                setIntensity((float) gd.getIntensity()).
                                setBackground((float) gd.getBackground()).
                                setXPosition(gd.getX()).
                                setYPosition(gd.getY()).
                                setWidth((float) gd.getWidth()).
                                setA((float) gd.getA()).
                                setTheta((float) gd.getTheta()).
                                setXPrecision((float) gd.getSigma());
                        
                        double width = gd.getWidth();
                        double xPrec = gd.getSigma();
                        
                        Spot spot = spotBuilder.build();
                        // write message size and message
                        spot.writeDelimitedTo(fo);
                        counter++;
                     }
                  }
                  
                  FileChannel fc = fo.getChannel();
                  long offset = fc.position();
                  spotList.writeDelimitedTo(fo);

                  // now go back to write offset to the stream
                  fc.position(4);
                  DataOutputStream dos = new DataOutputStream(fo);
                  dos.writeLong(offset - 12);
                  
                  fo.close();
                  
                  ij.IJ.showProgress(1);
                  ij.IJ.showStatus("Finished saving spotData...");
               } catch (IOException ex) {
                  Logger.getLogger(DataCollectionForm.class.getName()).log(Level.SEVERE, null, ex);
               } finally {
                  setCursor(Cursor.getDefaultCursor());
               }
            }
         };
         
         (new Thread(doWorkRunnable)).start();
         
      }
      
   }

   /**
    * Calculates the axis of motion of a given dataset and normalizes the data
    * to that axis.
    *
    * @rowData
    */
   private void straightenTrack(MyRowData rowData) {
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      Iterator it = rowData.spotList_.iterator();
      while (it.hasNext()) {
         GaussianSpotData gs = (GaussianSpotData) it.next();
         Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
         xyPoints.add(point);
      }

      // Calculate direction of travel and transform data set along this axis
      ArrayList<Point2D.Double> xyCorrPoints = GaussianUtils.pcaRotate(xyPoints);
      List<GaussianSpotData> transformedResultList =
              Collections.synchronizedList(new ArrayList<GaussianSpotData>());
      
      for (int i = 0; i < xyPoints.size(); i++) {
         GaussianSpotData oriSpot = rowData.spotList_.get(i);
         GaussianSpotData spot = new GaussianSpotData(oriSpot);
         spot.setData(oriSpot.getIntensity(), oriSpot.getBackground(),
                 xyCorrPoints.get(i).getX(), xyCorrPoints.get(i).getY(), oriSpot.getWidth(),
                 oriSpot.getA(), oriSpot.getTheta(), oriSpot.getSigma());
         transformedResultList.add(spot);
      }

      // Add transformed data to data overview window
      addSpotData(rowData.name_ + "Straightened", rowData.title_, "", rowData.width_,
              rowData.height_, rowData.pixelSizeNm_, rowData.shape_,
              rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
              rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
              rowData.timePoints_, true);
   }
   
   
   /**
    * Creates a new dataset that is centered around the average of the X and Y data.
    * In other words, the average of both X and Y is calculated and subtracted from each datapoint
    *
    * @rowData
    */
   private void centerTrack(MyRowData rowData) {
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
      ArrayList<Point2D.Double> xyPoints = rowToList(rowData);
      Point2D.Double avgPoint = avgXYList(xyPoints);
          
      /*ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      Iterator it = rowData.spotList_.iterator();
      double totalX = 0.0;
      double totalY = 0.0;
      while (it.hasNext()) {
         GaussianSpotData gs = (GaussianSpotData) it.next();
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
      List<GaussianSpotData> transformedResultList =
              Collections.synchronizedList(new ArrayList<GaussianSpotData>());
      
      for (int i = 0; i < xyPoints.size(); i++) {
         GaussianSpotData oriSpot = rowData.spotList_.get(i);
         GaussianSpotData spot = new GaussianSpotData(oriSpot);
         spot.setData(oriSpot.getIntensity(), oriSpot.getBackground(),
                 xyPoints.get(i).getX(), xyPoints.get(i).getY(), oriSpot.getWidth(),
                 oriSpot.getA(), oriSpot.getTheta(), oriSpot.getSigma());
         transformedResultList.add(spot);
      }

      // Add transformed data to data overview window
      addSpotData(rowData.name_ + " Centered", rowData.title_, "", rowData.width_,
              rowData.height_, rowData.pixelSizeNm_, rowData.shape_,
              rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
              rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
              rowData.timePoints_, true);
   }

   /**
    * Creates a new data set that is corrected for motion blur
    * Correction is performed by projecting a number of images onto a 
    * 2D scattergram and using cross-correlation between them to find
    * the displacement
    * 
    * @param rowData 
    */
   private void unJitter(final MyRowData rowData) {
      // TODO: instead of a fixed number of frames, go for a certain number of spots
      // Number of frames could be limited as well
      final int framesToCombine = 100;
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
      ij.IJ.showStatus("Executing jitter correction");
      
      Runnable doWorkRunnable = new Runnable() {
         
         public void run() {
            
            int mag = 1 << visualizationMagnification_.getSelectedIndex();
            
            
            int width = mag * rowData.width_;
            int height = mag * rowData.height_;
            int size = width * height;

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
               for (GaussianSpotData spot : rowData.spotList_) {
                  int j = 0;
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
                  jd.getJitter(ip[i], com);
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
               
            } catch (OutOfMemoryError ex) {
               // not enough memory to allocate all images in one go
               // we need to cycle through all gaussian spots cycle by cycle

               double factor = (double) mag / rowData.pixelSizeNm_;
               
               ImageProcessor ipRef = new ByteProcessor(width, height);
               byte[] pixelsRef = new byte[width * height];
               ipRef.setPixels(pixelsRef);


               // take the first image as reference
               for (GaussianSpotData spot : rowData.spotList_) {
                  int j = 0;
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
                  
                  for (GaussianSpotData spot : rowData.spotList_) {
                     int j = 0;
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
               List<GaussianSpotData> stageMovementData = new ArrayList<GaussianSpotData>();
               GaussianSpotData sm = new GaussianSpotData(null, 1, 1, 1, 1, 1, 1, 1);
               sm.setData(0, 0, 0, 0, 0, 0, 0, 0);
               stageMovementData.add(sm);
               for (int i = 0; i < stagePos.size(); i++) {
                  StageMovementData smd = stagePos.get(i);
                  GaussianSpotData s =
                          new GaussianSpotData(null, 1, 1, i + 2, 1, 1, 1, 1);
                  s.setData(0, 0, smd.pos_.x, smd.pos_.y, 0, 0, 0, 0);                  
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
               
               MyRowData newRow = new MyRowData(rowData.name_ + "-Jitter", rowData.title_,
                       "", rowData.width_,
                       rowData.height_, rowData.pixelSizeNm_, rowData.shape_,
                       rowData.halfSize_, rowData.nrChannels_, stageMovementData.size(),
                       1, 1, stageMovementData.size(), stageMovementData,
                       timePoints, true);
               rowData_.add(newRow);
               
               myTableModel_.fireTableRowsInserted(rowData_.size() - 1, rowData_.size());
               
               
               
               
               ij.IJ.showStatus("Assembling jitter corrected dataset...");
               ij.IJ.showProgress(1);
               
               List<GaussianSpotData> correctedData = new ArrayList<GaussianSpotData>();
               Iterator it = rowData.spotList_.iterator();
               
               int testNr = 0;
               StageMovementData smd = stagePos.get(0);
               int counter = 0;
               while (it.hasNext()) {
                  counter++;
                  GaussianSpotData gs = (GaussianSpotData) it.next();
                  int test = 0;
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
                     for (int i = 0; i < stagePos.size() && !found; i++) {
                        smd = stagePos.get(i);
                        if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                           found = true;
                        }
                     }
                  }
                  if (found) {
                     Point2D.Double point = new Point2D.Double(gs.getXCenter() - smd.pos_.x,
                             gs.getYCenter() - smd.pos_.y);
                     GaussianSpotData gsn = new GaussianSpotData(gs);
                     gsn.setXCenter(point.x);
                     gsn.setYCenter(point.y);
                     correctedData.add(gsn);
                  } else {
                     correctedData.add(gs);
                  }
                  
                  
               }

               // Add transformed data to data overview window
               addSpotData(rowData.name_ + "-Jitter-Correct", rowData.title_, "", rowData.width_,
                       rowData.height_, rowData.pixelSizeNm_, rowData.shape_,
                       rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                       rowData.nrSlices_, 1, rowData.maxNrSpots_, correctedData,
                       null, false);
               ij.IJ.showStatus("Finished jitter correction");
            } catch (OutOfMemoryError oom) {
              System.gc();
              ij.IJ.error("Out of Memory");
            }
         }
      };

      (new Thread(doWorkRunnable)).start();
   }
   
   
   /**
    * Use the 2Channel calibration to create a new, corrected data set
    * 
    * @param rowData 
    */
   private void correct2C(final MyRowData rowData)
   {
      if (rowData.spotList_.size() <= 1) {
         JOptionPane.showMessageDialog(getInstance(), "Please select a dataset to Color correct");
         return;
      }
      if (lwm_ == null) {
         JOptionPane.showMessageDialog(getInstance(), "No calibration data available.  First Calibrate using 2C Reference");
         return;
      }
      
      
      ij.IJ.showStatus("Executing color correction");
      
      Runnable doWorkRunnable = new Runnable() {

         public void run() {

            List<GaussianSpotData> correctedData =
                    Collections.synchronizedList(new ArrayList<GaussianSpotData>());
            Iterator it = rowData.spotList_.iterator();
            int frameNr = 0;
            while (it.hasNext()) {
               GaussianSpotData gs = (GaussianSpotData) it.next();
               if (gs.getFrame() != frameNr) {
                  frameNr = gs.getFrame();
                  ij.IJ.showStatus("Executing color correction...");
                  ij.IJ.showProgress(frameNr, rowData.nrFrames_);
               }
               if (gs.getChannel() == 1) {
                  Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                  try {
                     Point2D.Double corPoint = lwm_.transform(point);
                     GaussianSpotData gsn = new GaussianSpotData(gs);
                     gsn.setXCenter(corPoint.x);
                     gsn.setYCenter(corPoint.y);
                     correctedData.add(gsn);
                  } catch (Exception ex) {
                     ex.printStackTrace();
                  }
               } else if (gs.getChannel() == 2) {
                  correctedData.add(gs);
               }

            }

            // Add transformed data to data overview window
            addSpotData(rowData.name_ + "Channel-Correct", rowData.title_, 
                    referenceName_.getText(), rowData.width_,
                    rowData.height_, rowData.pixelSizeNm_, rowData.shape_,
                    rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                    rowData.nrSlices_, 1, rowData.maxNrSpots_, correctedData,
                    null, 
                    false);
         }
      };

      (new Thread(doWorkRunnable)).start();
   }


   /**
    * Plots Tracks using JFreeChart
    *
    * @rowData
    * @plotMode - Index of plotMode in array {"t-X", "t-Y", "X-Y", "t-Int"};
    */
   private void plotData(MyRowData[] rowDatas, int plotMode) {
      String title = plotModes_[plotMode];
      if (rowDatas.length == 1)
         title = rowDatas[0].name_ + " " + plotModes_[plotMode];
      
      XYSeries[] datas = new XYSeries[rowDatas.length];

      String xAxis = null;
      
      switch (plotMode) {

         case (0): { // t-X
            for (int index = 0; index < rowDatas.length; index++) {
               datas[index] = new XYSeries(rowDatas[index].ID_);
               for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                  GaussianSpotData spot = rowDatas[index].spotList_.get(i);
                  if (rowDatas[index].timePoints_ != null) {
                     double timePoint = rowDatas[index].timePoints_.get(i);
                     datas[index].add(timePoint, spot.getXCenter());
                  } else {
                     datas[index].add(i, spot.getXCenter());
                  }
               }
               xAxis = "Time (frameNr)";
               if (rowDatas[index].timePoints_ != null) {
                  xAxis = "Time (s)";
               }
            }
            GaussianUtils.plotDataN(title, datas, xAxis, "X(nm)", 0, 400);
         }
         break;
         
         case (1): { // t-Y
            for (int index = 0; index < rowDatas.length; index++) {
               datas[index] = new XYSeries(rowDatas[index].ID_);
               for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                  GaussianSpotData spot = rowDatas[index].spotList_.get(i);
                  if (rowDatas[index].timePoints_ != null) {
                     double timePoint = rowDatas[index].timePoints_.get(i);
                     datas[index].add(timePoint, spot.getYCenter());
                  } else {
                     datas[index].add(i, spot.getYCenter());
                  }
               }
               xAxis = "Time (frameNr)";
               if (rowDatas[index].timePoints_ != null) {
                  xAxis = "Time (s)";
               }
            }
            GaussianUtils.plotDataN(title, datas, xAxis, "Y(nm)", 0, 400);
         }
         break;
         
         case (2): { // X-Y
            for (int index = 0; index < rowDatas.length; index++) {
               datas[index] = new XYSeries(rowDatas[index].ID_, false, true);
               for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                  GaussianSpotData spot = rowDatas[index].spotList_.get(i);
                  datas[index].add(spot.getXCenter(), spot.getYCenter());
               }
            }
            GaussianUtils.plotDataN(title, datas, "X(nm)", "Y(nm)", 0, 400);
         }
         break;


         case (3): {
            for (int index = 0; index < rowDatas.length; index++) {
               datas[index] = new XYSeries(rowDatas[index].ID_);
               for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                  GaussianSpotData spot = rowDatas[index].spotList_.get(i);
                  if (rowDatas[index].timePoints_ != null) {
                     double timePoint = rowDatas[index].timePoints_.get(i);
                     datas[index].add(timePoint, spot.getIntensity());
                  } else {
                     datas[index].add(i, spot.getIntensity());
                  }
               }
               xAxis = "Time (frameNr)";
               if (rowDatas[index].timePoints_ != null) {
                  xAxis = "Time (s)";
               }
            }
            GaussianUtils.plotDataN(title, datas, xAxis, "Intensity (#photons)", 0, 400);
         }
         break;
      }
      
   }
      


   private boolean test(double[][][] imageSpotList, int source, int target, double cutoff) {
      double xtest = imageSpotList[0][source][2] - imageSpotList[1][target][2];
      if (xtest > cutoff || xtest < -cutoff)
         return false;
      double ytest = imageSpotList[0][source][3] - imageSpotList[1][target][3];
      if (ytest > cutoff || ytest < -cutoff)
         return false;
      return true;
   }

   private class SpotSortComparator implements Comparator {

      // Return the result of comparing the two row arrays
      public int compare(Object o1, Object o2) {
         double[] p1 = (double[]) o1;
         double[] p2 = (double[]) o2;
         if (p1[0] < p2[0]) {
            return -1;
         }
         if (p1[0] > p2[0]) {
            return 1;
         }
         if (p1[0] == p2[0]) {
            if (p1[1] < p2[1]) {
               return -1;
            }
            if (p1[1] > p2[1]) {
               return 1;
            }
         }
         return 0;
      }
   }

}
