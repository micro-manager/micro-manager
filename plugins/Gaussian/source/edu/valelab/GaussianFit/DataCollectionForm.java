/**
 * DataCollectionForm.java
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

import ij.gui.YesNoCancelDialog;
import ij.process.ShortProcessor;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.geom.Point2D;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.JOptionPane;
import org.jfree.data.xy.XYSeries;

import valelab.LocalWeightedMean;

/**
 *
 * @author nico
 */
public class DataCollectionForm extends javax.swing.JFrame {
   AbstractTableModel myTableModel_;
   private final String[] columnNames_ = {"Image", "Nr of spots", "Plot/Render", "Action"};
   private final String[] plotModes_ = {"t-X", "t-Y", "X-Y"};
   private final String[] renderModes_ = {"Points", "Gaussian"};
   private final String[] renderSizes_  = {"1x", "2x", "4x", "8x"};
   public final static String extension_ = ".tsf";
   ArrayList<MyRowData> rowData_;
   double[][][] colorCorrection_; // 2D array (one for each pixel) containing xy coordinates of
                                 // first image (0 and 1) and correction to second image (2 and 3)
   public static DataCollectionForm instance_ = null;
   private static LocalWeightedMean lwm_;


   /**
    * Data structure for spotlists
    */
   private class MyRowData {
      public final List<GaussianSpotData> spotList_;
      public final String name_;
      public final String title_;
      public final int width_;
      public final int height_;
      public final float pixelSizeUm_;
      public final int shape_;
      public final int halfSize_;
      public final int nrChannels_;
      public final int nrFrames_;
      public final int nrSlices_;
      public final int nrPositions_;
      public final int maxNrSpots_;
      public final boolean isTrack_;


      public MyRowData(String name,
              String title,
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
              boolean isTrack) {
         name_ = name;
         title_ = title;
         width_ = width;
         height_ = height;
         pixelSizeUm_ = pixelSizeUm;
         spotList_ = spotList;
         shape_ = shape;
         halfSize_ = halfSize;
         nrChannels_ = nrChannels;
         nrFrames_ = nrFrames;
         nrSlices_ = nrSlices;
         nrPositions_ = nrPositions;
         maxNrSpots_ = maxNrSpots;
         isTrack_ = isTrack;
      }
   }

   /**
    * Implement this class as a singleton
    *
    * @return
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
                return rowData_.get(row).name_;
             else if (col == 1)
                return rowData_.get(row).spotList_.size();
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
       jTable1.addMouseListener(new LocalMouseListener());
       jTable1.getColumn("Plot/Render").setCellRenderer(new ButtonRenderer());
       jTable1.getColumn("Action").setCellRenderer(new ButtonRenderer());
       plotComboBox_.setModel(new javax.swing.DefaultComboBoxModel(plotModes_));
       visualizationModel_.setModel(new javax.swing.DefaultComboBoxModel(renderModes_));
       visualizationMagnification_.setModel(new javax.swing.DefaultComboBoxModel(renderSizes_));
       jScrollPane1.setName("Gaussian Spot Fitting Data Sets");
       setVisible(true);
   }

   /**
    * Use a Mouse listener instead of buttons, since buttons in a table are a pain
    */
   private class LocalMouseListener extends MouseAdapter {
      @Override
      public void mouseClicked(MouseEvent me) {
         Point click = new Point(me.getX(), me.getY());
         int column = jTable1.columnAtPoint(click);
         int row = jTable1.rowAtPoint(click);
         
         if (column == 2) {
            if (rowData_.get(row).isTrack_)
               plotData(rowData_.get(row), plotComboBox_.getSelectedIndex());
            else
               renderData(rowData_.get(row), visualizationModel_.getSelectedIndex(),
                       visualizationMagnification_.getSelectedIndex());
         }
         else if (column == 3) {
            if (rowData_.get(row).isTrack_)
               straightenTrack(rowData_.get(row));
            else 
               correct2C(rowData_.get(row));
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
           boolean isTrack) {
      rowData_.add(new MyRowData(name, title, width, height, pixelSizeUm, shape, halfSize,
              nrChannels, nrFrames, nrSlices, nrPositions, maxNrSpots, spotList,
              isTrack));
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
        jTable1 = new javax.swing.JTable();
        loadButton = new javax.swing.JButton();
        plotComboBox_ = new javax.swing.JComboBox();
        visualizationMagnification_ = new javax.swing.JComboBox();
        visualizationModel_ = new javax.swing.JComboBox();
        saveButton = new javax.swing.JButton();
        removeButton = new javax.swing.JButton();
        showButton = new javax.swing.JButton();
        c2StandardButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Gaussian tracking data");
        setMinimumSize(new java.awt.Dimension(450, 80));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });

        jTable1.setModel(myTableModel_);
        jScrollPane1.setViewportView(jTable1);

        loadButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        loadButton.setText("Load");
        loadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadButtonActionPerformed(evt);
            }
        });

        plotComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        plotComboBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "X-T", "Y-T", "X-Y" }));

        visualizationMagnification_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        visualizationMagnification_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1x", "2x", "4x", "8x" }));

        visualizationModel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        visualizationModel_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Gaussian" }));

        saveButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        saveButton.setText("Save");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        removeButton.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        removeButton.setText("Remove");
        removeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeButtonActionPerformed(evt);
            }
        });

        showButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        showButton.setText("Show");
        showButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showButtonActionPerformed(evt);
            }
        });

        c2StandardButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        c2StandardButton.setText("2C Standard");
        c2StandardButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                c2StandardButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(loadButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(saveButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(removeButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(showButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(c2StandardButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 89, Short.MAX_VALUE)
                .add(18, 18, 18)
                .add(visualizationModel_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 90, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(visualizationMagnification_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 90, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 90, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 716, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(loadButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(plotComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(visualizationMagnification_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(visualizationModel_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(saveButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(removeButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(showButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(c2StandardButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 446, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Loads data saved in TSF format (Tagged Spot File Format)
     *
     * @evt
     */
    private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed
      File selectedFile;
      FileDialog fd = new FileDialog(this, "Load Spot Data", FileDialog.LOAD);
      fd.setVisible(true);
      String selectedItem = fd.getFile();
      if (selectedItem == null) {
         return;
      } else {
         selectedFile = new File( fd.getDirectory() + File.separator +
		        fd.getFile());

         SpotList psl = null;
         try {
            
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            
            FileInputStream fi = new FileInputStream(selectedFile);

            psl = SpotList.parseDelimitedFrom(fi);
       
            String name = psl.getName();
            String title = psl.getName();
            int width = psl.getNrPixelsX();
            int height = psl.getNrPixelsY();
            float pixelSizeUm = psl.getPixelSize();
            int shape = 1;
            if (psl.getFitMode() == FitMode.TWOAXIS)
               shape = 2;
            else if (psl.getFitMode() == FitMode.TWOAXISANDTHETA)
               shape = 3;
            int halfSize = psl.getBoxSize() / 2;
            int nrChannels = psl.getNrChannels();
            int nrFrames = psl.getNrFrames();
            int nrSlices = psl.getNrSlices();
            int nrPositions = psl.getNrPos();
            boolean isTrack = psl.getIsTrack();
            int maxNrSpots = 0;

            
            
            ArrayList<GaussianSpotData> spotList = new ArrayList<GaussianSpotData>();
            Spot pSpot;
            while (fi.available() > 0) {
               pSpot = Spot.parseDelimitedFrom(fi);

               GaussianSpotData gSpot = new GaussianSpotData((ImageProcessor) null, pSpot.getChannel(),
                       pSpot.getSlice(), pSpot.getFrame(), pSpot.getPos(),
                       pSpot.getMolecule(), pSpot.getXPosition(), pSpot.getYPosition());
               gSpot.setData(pSpot.getIntensity(), pSpot.getBackground(), pSpot.getX(),
                       pSpot.getY(), pSpot.getWidth(), pSpot.getA(), pSpot.getTheta(),
                       pSpot.getXPrecision());
               maxNrSpots++;

               spotList.add(gSpot);
            }

            addSpotData(name, title, width, height, pixelSizeUm, shape, halfSize,
                    nrChannels, nrFrames, nrSlices, nrPositions, maxNrSpots,
                    spotList, isTrack);

         } catch (FileNotFoundException ex) {
            ex.printStackTrace();
         } catch (IOException ex) {
            ex.printStackTrace();
            return;
         } finally {
            setCursor(Cursor.getDefaultCursor());
         }
      }

    }//GEN-LAST:event_loadButtonActionPerformed

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
       int row = jTable1.getSelectedRow();
       if (row > -1)
         saveData(rowData_.get(row));
       else
          JOptionPane.showMessageDialog(null, "Please select a dataset to save");
    }//GEN-LAST:event_saveButtonActionPerformed

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed
       int rows[] = jTable1.getSelectedRows();
       if (rows.length > 0) {
          for (int row = rows.length -1; row >= 0; row--) {
             rowData_.remove(rows[row]);
             myTableModel_.fireTableRowsDeleted(rows[row], rows[row]);
          }
       }
    }//GEN-LAST:event_removeButtonActionPerformed

    private void showButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showButtonActionPerformed
       int row = jTable1.getSelectedRow();
       if (row > -1)
         showResults(rowData_.get(row));
    }//GEN-LAST:event_showButtonActionPerformed

   private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
      //jScrollPane1.setSize(this.getSize());
      Dimension d = getSize();
      d.height -= 60;
      jScrollPane1.setSize(d);
      jScrollPane1.getViewport().setViewSize(d);
   }//GEN-LAST:event_formComponentResized

   private void c2StandardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_c2StandardButtonActionPerformed
      int row = jTable1.getSelectedRow();
      if (row > -1) {
         // TODO: calculate transform
         ArrayList points = new ArrayList();
         
         ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
         Iterator it = rowData_.get(row).spotList_.iterator();
         while (it.hasNext()) {
            GaussianSpotData gs = (GaussianSpotData) it.next();
            Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
            xyPoints.add(point);
         }
         
      }
         showResults(rowData_.get(row));
   }//GEN-LAST:event_c2StandardButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton c2StandardButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JButton loadButton;
    private javax.swing.JComboBox plotComboBox_;
    private javax.swing.JButton removeButton;
    private javax.swing.JButton saveButton;
    private javax.swing.JButton showButton;
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
            if (column == 2)
               setText((value == null ? "" : "Plot"));
            else {
               if (column == 3)
                  setText((value == null ? "" : "Straighten"));
               else
                  setText((value == null ? "" : value.toString()));
            }
         } else {
            if (column == 2)
               setText((value == null ? "" : "Render"));
            else {
               if (column == 3)
                  setText((value == null ? "" : "2C Correct"));
               else
                  setText((value == null ? "" : value.toString()));
            }
            //return null;
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
      if (frame!=null && frame instanceof TextWindow && siPlus != null) {
         win = (TextWindow)frame;
         tp = win.getTextPanel();

         // TODO: the following does not work, there is some voodoo going on here
         for (MouseListener ms : tp.getMouseListeners()) {
            tp.removeMouseListener(ms);
         }
         for (KeyListener ks : tp.getKeyListeners()) {
            tp.removeKeyListener(ks);
         }

         MyK myk = new MyK(siPlus, rt, win, rowData.halfSize_);
         tp.addKeyListener(myk);
         tp.addMouseListener(myk);
         frame.toFront();
      }

   }

   /**
    * Save data set in TSF (Tagged Spot File) format
    *
    * @rowData
    */
   private void saveData(MyRowData rowData) {
      File selectedFile;
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
         selectedFile = new File(fd.getDirectory() + File.separator + fn);
         if (selectedFile.exists()) {
            //JOptionPane.showOptionDialog(this,"File exists.  Overwrite?", "File Exists...", JOptionPane.YES_NO_CANCEL_OPTION);
            YesNoCancelDialog y = new YesNoCancelDialog(this, "File " + fn + "Exists...", "File exists.  Overwrite?");
            if (y.cancelPressed()) {
               return;
            }
            if (!y.yesPressed()) {
               saveData(rowData);
               return;
            }
         }


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
            spotList.writeDelimitedTo(fo);

            int counter = 0;
            for (GaussianSpotData gd : rowData.spotList_) {
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

            fo.close();
         } catch (IOException ex) {
            Logger.getLogger(DataCollectionForm.class.getName()).log(Level.SEVERE, null, ex);
         } finally {
            setCursor(Cursor.getDefaultCursor());
         }
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
      addSpotData(rowData.name_ + "Straightened", rowData.title_, rowData.width_,
              rowData.height_, rowData.pixelSizeUm_, rowData.shape_,
              rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
              rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
              true);
   }
   
   /**
    * Use the 2Channel calibration to create a new, corrected data set
    * 
    * @param rowData 
    */
   private void correct2C(MyRowData rowData)
   {
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
      
   }

   /**
    * Renders spotdata using various renderModes
    * 
    * @param rowData
    * @param renderMode - 
    * @param renderSize 
    */
   private void renderData(MyRowData rowData,int renderMode, int renderSize) {
      String fsep = System.getProperty("file.separator");
      String title = rowData.name_;
      if (rowData.name_.contains(fsep))
         title = rowData.name_.substring(rowData.name_.lastIndexOf(fsep) + 1);
      title += renderSizes_[renderSize];
      
      int mag = 1 << renderSize;
      int width = mag * rowData.width_;
      int height = mag * rowData.height_;
      ImageProcessor ip = new ShortProcessor(width, height);
      int size = width * height;
      short pixels[] = new short[size];
      ip.setPixels(pixels);
      double factor = (double) mag / rowData.pixelSizeUm_;

      if (renderMode == 0) {
         for (GaussianSpotData spot : rowData.spotList_) {
            int x = (int) (factor * spot.getXCenter());
            int y = (int) (factor * spot.getYCenter());
            int index = (y * width) + x;
            if (index < size && index > 0)
               if (pixels[index] != -1)
                  pixels[index] += 1;
         }
      } else if (renderMode == 2) {  // Gaussian

      }

      ImagePlus sp = new ImagePlus(title, ip);
      sp.setDisplayRange(0, 1);
      ImageWindow w = new ImageWindow(sp);
      w.setVisible(true);

   }

   /**
    * Plots Tracks using JFreeChart
    *
    * @rowData
    * @plotMode - Index of plotMode in array {"t-X", "t-Y", "X-Y"};
    */
   private void plotData(MyRowData rowData, int plotMode) {
      String title = rowData.name_ + " " + plotModes_[plotMode];
      if (plotMode == 0) {
         XYSeries data = new XYSeries("");
         for (int i = 0; i < rowData.spotList_.size(); i++) {
            GaussianSpotData spot = rowData.spotList_.get(i);
            data.add(i, spot.getXCenter());
         }
         GaussianUtils.plotData(title, data, "Time", "X(nm)", 0, 400);
      }
      else if (plotComboBox_.getSelectedIndex() == 1) {
         XYSeries data = new XYSeries("");
         for (int i = 0; i < rowData.spotList_.size(); i++) {
            GaussianSpotData spot = rowData.spotList_.get(i);
            data.add(i, spot.getYCenter());
         }
         GaussianUtils.plotData(title, data, "Time", "Y(nm)", 0, 400);
      }
      else if (plotComboBox_.getSelectedIndex() == 2) {
         XYSeries data = new XYSeries("", false, true);
         for (int i = 0; i < rowData.spotList_.size(); i++) {
            GaussianSpotData spot = rowData.spotList_.get(i);
            data.add(spot.getXCenter(), spot.getYCenter());
         }
         GaussianUtils.plotData(title, data, "X(nm)", "Y(nm)", 0, 400);
      }
   }

   private int findMatch(double[][][] imageSpotList, int nr, double cutoff) {
      int j = 0;
      while ( (nr - j) >= 0 || (nr + j) < imageSpotList[0].length ) {
         if (nr - j >= 0) {
            if (test(imageSpotList, nr, nr - j, cutoff))
               return nr - j;
         }
         if ( (j != 0) && nr + j < imageSpotList[0].length) {
            if (test(imageSpotList, nr, nr + j, cutoff))
               return nr + j;
         }
         j++;
      }
      return -1;
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

   /*
   private void displayColorCorrection (String axis) {
       if (colorCorrection_ == null)
          // TODO: error message
          return;

       int arrayOffset = 2;
       if (axis.equals("Y"))
          arrayOffset = 3;

       // pixels to be shown
       short pixels[] = new short[width_ * height_];
       // scale the difference values to the maximum allowed in a short
       double max = 0;
       double min = 0;
       for (int x = 0; x < xSize_; x++) {
          for (int y=0; y < ySize_; y++) {
             if (colorCorrection_[x][y][arrayOffset] > max)
                max = colorCorrection_[x][y][arrayOffset];
             if (colorCorrection_[x][y][arrayOffset] < min)
                min = colorCorrection_[x][y][arrayOffset];
          }
       }

       // scale the image so that 1 gray scale value corresponds to 1 nm
       int factor = (int) (max - min);
       if (factor > 65535)
          factor = 65535;

       // populate pixels with scaled values
       for (int x = 0; x < xSize_; x++) {
          for (int y=0; y < ySize_; y++) {
             pixels[(y * xSize_) + x] = (short) (factor * (
                     (colorCorrection_[x][y][arrayOffset] - min) / (max - min)));
          }
       }

       ImageProcessor ip = new ShortProcessor(xSize_, ySize_);
       ip.setPixels(pixels);

       ImagePlus sp = new ImagePlus("Color Correction " + axis + " Offsets", ip);
       ImageWindow w = new ImageWindow(sp);
       w.setVisible(true);
   }

    */
}