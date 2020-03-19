/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay;

import com.google.common.eventbus.Subscribe;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.DefaultCellEditor;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import org.json.JSONObject;
import org.micromanager.display.DisplaySettingsChangedEvent;
import org.micromanager.magellan.internal.channels.MagellanChannelSpec;
import org.micromanager.magellan.internal.coordinates.NoPositionsDefinedYetException;
import org.micromanager.magellan.internal.gui.SimpleChannelTableModel;
import org.micromanager.magellan.internal.gui.ExactlyOneRowSelectionModel;
import org.micromanager.magellan.internal.misc.MD;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;

/**
 *
 * @author henrypinkard
 */
class DisplayWindowControlsNew extends javax.swing.JPanel implements SurfaceGridListener {

   private MagellanDisplayController display_;
   private ListSelectionListener surfaceTableListSelectionListener_;
   private volatile int selectedSurfaceGridIndex_ = -1;
   private ContrastPanel cpMagellan_;
   MagellanChannelSpec channels_;

   /**
    * Creates new form DisplayWindowControls
    */
   public DisplayWindowControlsNew(MagellanDisplayController disp, MagellanChannelSpec channels) {

      display_ = disp;
      channels_ = channels;
      display_.registerForEvents(this);
      initComponents();
      metadataPanelMagellan_.setSummaryMetadata(disp.getSummaryMD());

      this.setFocusable(false); //think this is good 

      if (disp.isExploreAcquisiton()) {
         //left justified editor
         JTextField tf = new JTextField();
         tf.setHorizontalAlignment(SwingConstants.LEFT);
         DefaultCellEditor ed = new DefaultCellEditor(tf);
         channelsTable_.getColumnModel().getColumn(2).setCellEditor(ed);
         //and renderer
         DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
         renderer.setHorizontalAlignment(SwingConstants.LEFT); // left justify
         channelsTable_.getColumnModel().getColumn(2).setCellRenderer(renderer);
         channelsTable_.getColumnModel().getColumn(0).setMaxWidth(30); //Acitve checkbox column

         //start in explore
         tabbedPane_.setSelectedIndex(0);
      }

      if (!(disp.isExploreAcquisiton()) || channels == null) {
         Component c = tabbedPane_.getComponentAt(3);
         tabbedPane_.remove(3); //remove explore tab
         //remove listeners and stuff

         acquireAtCurrentButton_.setVisible(false);
         tabbedPane_.setSelectedIndex(0); //statr on contrst
      }

      //exactly one surface or grid selected at all times
      surfaceGridTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
      surfaceTableListSelectionListener_ = new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
               return;
               //action occurs second time this method is called, after the table gains focus
            }
            updateSurfaceGridSelection();
         }
      };
      surfaceGridTable_.getSelectionModel().addListSelectionListener(surfaceTableListSelectionListener_);
      //Table column widths
      surfaceGridTable_.getColumnModel().getColumn(0).setMaxWidth(40); //show column
      surfaceGridTable_.getColumnModel().getColumn(1).setMaxWidth(120); //type column
      //So it is initialized correctly when surfaces are already present
      updateSurfaceGridSelection();
      updateMode();

      if (!disp.isExploreAcquisiton()) {
         exploreButton_.setVisible(false);
         acquireAtCurrentButton_.setVisible(false);
         for (ActionListener l : exploreButton_.getActionListeners()) {
            exploreButton_.removeActionListener(l);
         }
         for (ActionListener l : acquireAtCurrentButton_.getActionListeners()) {
            acquireAtCurrentButton_.removeActionListener(l);
         }
      }
      //knitially disable surfaces and grids
      tabbedPane_.setEnabledAt(display_.isExploreAcquisiton() ? 1 : 0, false);

   }
   

   public void onDisplayClose() {
      display_.unregisterForEvents(this);
      if (display_.isExploreAcquisiton()) {
         ((SimpleChannelTableModel) channelsTable_.getModel()).shutdown();
      }
      surfaceGridTable_.getSelectionModel().removeListSelectionListener(surfaceTableListSelectionListener_);
   }

   private void updateSurfaceGridSelection() {
      selectedSurfaceGridIndex_ = surfaceGridTable_.getSelectedRow();
      //if last in list is removed, update the selected index
      if (selectedSurfaceGridIndex_ == surfaceGridTable_.getModel().getRowCount()) {
         surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_ - 1, selectedSurfaceGridIndex_ - 1);
      }
      XYFootprint current = getCurrentSurfaceOrGrid();
      if (current != null) {

         CardLayout card1 = (CardLayout) surfaceGridSpecificControlsPanel_.getLayout();
         if (current instanceof SurfaceInterpolator) {
            card1.show(surfaceGridSpecificControlsPanel_, "surface");
         } else {
            card1.show(surfaceGridSpecificControlsPanel_, "grid");
            int numRows = ((MultiPosGrid) current).numRows();
            int numCols = ((MultiPosGrid) current).numCols();
            gridRowsSpinner_.setValue(numRows);
            gridColsSpinner_.setValue(numCols);
         }
      }
      display_.redrawOverlay();
   }

   private void updateMode() {
      if (display_.isActiveExploreAcquisiton() && exploreButton_.isSelected()) {
         display_.setOverlayMode(MagellanDataViewCoords.EXPLORE);
         return;
      }
      if (tabbedPane_.getSelectedIndex() == 1) {
         display_.setOverlayMode(MagellanDataViewCoords.SURFACE_AND_GRID);
      } else {
         display_.setOverlayMode(MagellanDataViewCoords.NONE);
      }
   }

   void updateHistogramData(HashMap<Integer, int[]> hists, HashMap<Integer, Integer> mins, HashMap<Integer, Integer> maxs) {
      cpMagellan_.updateHistogramData(hists, mins, maxs);
   }

   public void addContrastControls(int channelIndex, String channelName) {
      cpMagellan_.addContrastControls(channelIndex, channelName);
   }

   public ArrayList<XYFootprint> getSurfacesAndGridsForDisplay() {
      ArrayList<XYFootprint> list = new ArrayList<XYFootprint>();
      for (int i = 0; i < SurfaceGridManager.getInstance().getNumberOfGrids() + SurfaceGridManager.getInstance().getNumberOfSurfaces(); i++) {
         try {
            if (((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).isSurfaceOrGridVisible(i)) {
               list.add(SurfaceGridManager.getInstance().getSurfaceOrGrid(i));
            }
         } catch (NullPointerException e) {
            //this comes up when making a bunch of surfaces then making a grid, unclear why vut it seems to be debnign
         }
      }
      return list;
   }

   public MetadataPanel getMetadataPanelMagellan() {
      return metadataPanelMagellan_;
   }

   XYFootprint getCurrentSurfaceOrGrid() {
      if (selectedSurfaceGridIndex_ == -1) {
         return null;
      }
      return SurfaceGridManager.getInstance().getSurfaceOrGrid(selectedSurfaceGridIndex_);
   }

   public void onNewImage() {
      //once there's an image, surfaces and grids are game
      tabbedPane_.setEnabledAt(display_.isExploreAcquisiton() ? 1 : 0, true);
   }
   
   void displaySettingsChanged() {
      cpMagellan_.displaySettingsChanged();
   }

   
   void setImageMetadata(JSONObject imageMD) {
      metadataPanelMagellan_.updateImageMetadata(imageMD);
      updateStatusLabel(imageMD);
   }

   private void updateStatusLabel(JSONObject metadata) {
      if (metadata == null) {
         return;
      }
      long elapsed = MD.getElapsedTimeMs(metadata);
      long days = elapsed / (60 * 60 * 24 * 1000), hours = elapsed / 60 / 60 / 1000, minutes = elapsed / 60 / 1000, seconds = elapsed / 1000;

      hours = hours % 24;
      minutes = minutes % 60;
      seconds = seconds % 60;
      String h = ("0" + hours).substring(("0" + hours).length() - 2);
      String m = ("0" + (minutes)).substring(("0" + minutes).length() - 2);
      String s = ("0" + (seconds)).substring(("0" + seconds).length() - 2);
      String label = days + ":" + h + ":" + m + ":" + s + " (D:H:M:S)";

      elapsedTimeLabel_.setText("Elapsed time: " + label);
      zPosLabel_.setText("Display Z position: " + MD.getZPositionUm(metadata) + "um");
   }

   public boolean isCurrentlyEditableSurfaceGridVisible() {
      if (selectedSurfaceGridIndex_ == -1) {
         return false;
      }
      return (Boolean) surfaceGridTable_.getValueAt(selectedSurfaceGridIndex_, 0);
   }

   @Override
   public void SurfaceOrGridChanged(XYFootprint f) {

   }

   @Override
   public void SurfaceOrGridDeleted(XYFootprint f) {
      updateSurfaceGridSelection();
   }

   @Override
   public void SurfaceOrGridCreated(XYFootprint f) {
      updateSurfaceGridSelection();
   }

   @Override
   public void SurfaceOrGridRenamed(XYFootprint f) {

   }

   @Override
   public void SurfaceInterpolationUpdated(SurfaceInterpolator s) {

   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      tabbedPane_ = new javax.swing.JTabbedPane();
      contrastPanelPanel_ = new javax.swing.JPanel();
      surfaceGridPanel_ = new javax.swing.JPanel();
      jScrollPane2 = new javax.swing.JScrollPane();
      surfaceGridTable_ = new javax.swing.JTable();
      surfaceGridSpecificControlsPanel_ = new javax.swing.JPanel();
      gridControlPanel_ = new javax.swing.JPanel();
      gridRowsLabel_ = new javax.swing.JLabel();
      gridRowsSpinner_ = new javax.swing.JSpinner();
      gridColsLabel_ = new javax.swing.JLabel();
      gridColsSpinner_ = new javax.swing.JSpinner();
      jLabel1 = new javax.swing.JLabel();
      surfaceControlPanel_ = new javax.swing.JPanel();
      showStagePositionsCheckBox_ = new javax.swing.JCheckBox();
      showInterpCheckBox_ = new javax.swing.JCheckBox();
      jLabel2 = new javax.swing.JLabel();
      newGridButton_ = new javax.swing.JButton();
      newSurfaceButton_ = new javax.swing.JButton();
      metadataPanel_ = new javax.swing.JPanel();
      metadataPanelMagellan_ = new org.micromanager.magellan.internal.imagedisplay.MetadataPanel();
      explorePanel_ = new javax.swing.JPanel();
      jScrollPane1 = new javax.swing.JScrollPane();
      channelsTable_ = new javax.swing.JTable();
      selectUseAllButton_ = new javax.swing.JButton();
      syncExposuresButton_ = new javax.swing.JButton();
      topControlPanel_ = new javax.swing.JPanel();
      showInFolderButton_ = new javax.swing.JButton();
      abortButton_ = new javax.swing.JButton();
      pauseButton_ = new javax.swing.JButton();
      fpsLabel_ = new javax.swing.JLabel();
      animationFPSSpinner_ = new javax.swing.JSpinner();
      lockScrollbarsCheckBox_ = new javax.swing.JCheckBox();
      elapsedTimeLabel_ = new javax.swing.JLabel();
      zPosLabel_ = new javax.swing.JLabel();
      acquireAtCurrentButton_ = new javax.swing.JButton();
      exploreButton_ = new javax.swing.JToggleButton();
      scaleBarCheckBox_ = new javax.swing.JCheckBox();

      tabbedPane_.setToolTipText("");
      tabbedPane_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            tabbedPane_StateChanged(evt);
         }
      });

      contrastPanelPanel_.setLayout(new java.awt.BorderLayout());

      cpMagellan_ = new ContrastPanel(display_);
      contrastPanelPanel_.add(cpMagellan_);

      tabbedPane_.addTab("Contrast", contrastPanelPanel_);

      surfaceGridTable_.setModel(new DisplayWindowSurfaceGridTableModel(display_)
      );
      jScrollPane2.setViewportView(surfaceGridTable_);

      surfaceGridSpecificControlsPanel_.setLayout(new java.awt.CardLayout());

      gridRowsLabel_.setText("Rows:");

      gridRowsSpinner_.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
      gridRowsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            gridRowsSpinner_StateChanged(evt);
         }
      });

      gridColsLabel_.setText("Columns:");

      gridColsSpinner_.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
      gridColsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            gridColsSpinner_StateChanged(evt);
         }
      });

      jLabel1.setText("Current Grid: ");

      javax.swing.GroupLayout gridControlPanel_Layout = new javax.swing.GroupLayout(gridControlPanel_);
      gridControlPanel_.setLayout(gridControlPanel_Layout);
      gridControlPanel_Layout.setHorizontalGroup(
         gridControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, gridControlPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(jLabel1)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(gridRowsLabel_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridRowsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridColsLabel_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridColsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      gridControlPanel_Layout.setVerticalGroup(
         gridControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(gridControlPanel_Layout.createSequentialGroup()
            .addGroup(gridControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(gridRowsLabel_)
               .addComponent(gridRowsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(gridColsLabel_)
               .addComponent(gridColsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(jLabel1))
            .addGap(0, 0, Short.MAX_VALUE))
      );

      surfaceGridSpecificControlsPanel_.add(gridControlPanel_, "grid");

      showStagePositionsCheckBox_.setSelected(true);
      showStagePositionsCheckBox_.setText("XY Footprint postions");
      showStagePositionsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showStagePositionsCheckBox_ActionPerformed(evt);
         }
      });

      showInterpCheckBox_.setSelected(true);
      showInterpCheckBox_.setText("Interpolation");
      showInterpCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showInterpCheckBox_ActionPerformed(evt);
         }
      });

      jLabel2.setText("Show:");

      javax.swing.GroupLayout surfaceControlPanel_Layout = new javax.swing.GroupLayout(surfaceControlPanel_);
      surfaceControlPanel_.setLayout(surfaceControlPanel_Layout);
      surfaceControlPanel_Layout.setHorizontalGroup(
         surfaceControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceControlPanel_Layout.createSequentialGroup()
            .addComponent(jLabel2)
            .addGap(21, 21, 21)
            .addComponent(showInterpCheckBox_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(showStagePositionsCheckBox_)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      surfaceControlPanel_Layout.setVerticalGroup(
         surfaceControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceControlPanel_Layout.createSequentialGroup()
            .addGroup(surfaceControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(showStagePositionsCheckBox_)
               .addComponent(showInterpCheckBox_)
               .addComponent(jLabel2))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      surfaceGridSpecificControlsPanel_.add(surfaceControlPanel_, "surface");

      newGridButton_.setText("New Grid");
      newGridButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newGridButton_ActionPerformed(evt);
         }
      });

      newSurfaceButton_.setText("New Surface");
      newSurfaceButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newSurfaceButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout surfaceGridPanel_Layout = new javax.swing.GroupLayout(surfaceGridPanel_);
      surfaceGridPanel_.setLayout(surfaceGridPanel_Layout);
      surfaceGridPanel_Layout.setHorizontalGroup(
         surfaceGridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceGridPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(newGridButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(newSurfaceButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, Short.MAX_VALUE)
            .addComponent(surfaceGridSpecificControlsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, 424, javax.swing.GroupLayout.PREFERRED_SIZE))
         .addComponent(jScrollPane2)
      );
      surfaceGridPanel_Layout.setVerticalGroup(
         surfaceGridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceGridPanel_Layout.createSequentialGroup()
            .addGroup(surfaceGridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(surfaceGridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                  .addComponent(newGridButton_)
                  .addComponent(newSurfaceButton_))
               .addGroup(surfaceGridPanel_Layout.createSequentialGroup()
                  .addContainerGap()
                  .addComponent(surfaceGridSpecificControlsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 403, Short.MAX_VALUE))
      );

      tabbedPane_.addTab("Surfaces and Grids", surfaceGridPanel_);

      javax.swing.GroupLayout metadataPanel_Layout = new javax.swing.GroupLayout(metadataPanel_);
      metadataPanel_.setLayout(metadataPanel_Layout);
      metadataPanel_Layout.setHorizontalGroup(
         metadataPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(metadataPanelMagellan_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      );
      metadataPanel_Layout.setVerticalGroup(
         metadataPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(metadataPanelMagellan_, javax.swing.GroupLayout.DEFAULT_SIZE, 459, Short.MAX_VALUE)
      );

      tabbedPane_.addTab("Metadata", metadataPanel_);

      explorePanel_.setToolTipText("<html>Left click or click and drag to select tiles <br>Left click again to confirm <br>Right click and drag to pan<br>+/- keys or mouse wheel to zoom in/out</html>");

      channelsTable_.setModel(new SimpleChannelTableModel(channels_,false));
      jScrollPane1.setViewportView(channelsTable_);

      selectUseAllButton_.setText("Select all");
      selectUseAllButton_.setToolTipText("Select or deselect all channels");
      selectUseAllButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            selectUseAllButton_ActionPerformed(evt);
         }
      });

      syncExposuresButton_.setText("Sync exposures");
      syncExposuresButton_.setToolTipText("Make all exposures equal to the top channel exposures");
      syncExposuresButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            syncExposuresButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout explorePanel_Layout = new javax.swing.GroupLayout(explorePanel_);
      explorePanel_.setLayout(explorePanel_Layout);
      explorePanel_Layout.setHorizontalGroup(
         explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane1)
         .addGroup(explorePanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(selectUseAllButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(syncExposuresButton_)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      explorePanel_Layout.setVerticalGroup(
         explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, explorePanel_Layout.createSequentialGroup()
            .addGroup(explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(selectUseAllButton_)
               .addComponent(syncExposuresButton_))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 421, Short.MAX_VALUE))
      );

      tabbedPane_.addTab("Channels", explorePanel_);

      showInFolderButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/folder.png"))); // NOI18N
      showInFolderButton_.setToolTipText("Show in folder");
      showInFolderButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showInFolderButton_ActionPerformed(evt);
         }
      });

      abortButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/abort.png"))); // NOI18N
      abortButton_.setToolTipText("Abort acquisition");
      abortButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            abortButton_ActionPerformed(evt);
         }
      });

      pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/pause.png"))); // NOI18N
      pauseButton_.setToolTipText("Pause/resume acquisition");
      pauseButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pauseButton_ActionPerformed(evt);
         }
      });

      fpsLabel_.setText("Animate FPS:");

      animationFPSSpinner_.setModel(new javax.swing.SpinnerNumberModel(7.0d, 1.0d, 1000.0d, 1.0d));
      animationFPSSpinner_.setToolTipText("Speed of the scrollbar animation button playback");
      animationFPSSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            animationFPSSpinner_StateChanged(evt);
         }
      });

      lockScrollbarsCheckBox_.setText("Lock scrollbars");
      lockScrollbarsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            lockScrollbarsCheckBox_ActionPerformed(evt);
         }
      });

      elapsedTimeLabel_.setText("Elapsed time: ");

      zPosLabel_.setText("Display Z position: ");

      acquireAtCurrentButton_.setText("Acquire tile here");
      acquireAtCurrentButton_.setToolTipText("Acquire an image at the current hardware position");
      acquireAtCurrentButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            acquireAtCurrentButton_ActionPerformed(evt);
         }
      });

      exploreButton_.setSelected(true);
      exploreButton_.setText("Explore");
      exploreButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            exploreButton_ActionPerformed(evt);
         }
      });

      scaleBarCheckBox_.setText("Scale Bar");
      scaleBarCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            scaleBarCheckBox_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout topControlPanel_Layout = new javax.swing.GroupLayout(topControlPanel_);
      topControlPanel_.setLayout(topControlPanel_Layout);
      topControlPanel_Layout.setHorizontalGroup(
         topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, topControlPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(topControlPanel_Layout.createSequentialGroup()
                  .addComponent(zPosLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
               .addGroup(topControlPanel_Layout.createSequentialGroup()
                  .addComponent(elapsedTimeLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addGap(286, 286, 286)
            .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(acquireAtCurrentButton_)
               .addComponent(exploreButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGap(101, 101, 101))
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, topControlPanel_Layout.createSequentialGroup()
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(fpsLabel_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(animationFPSSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(lockScrollbarsCheckBox_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(scaleBarCheckBox_)
            .addGap(41, 41, 41))
         .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(topControlPanel_Layout.createSequentialGroup()
               .addContainerGap()
               .addComponent(showInFolderButton_)
               .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
               .addComponent(abortButton_)
               .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
               .addComponent(pauseButton_)
               .addContainerGap(500, Short.MAX_VALUE)))
      );
      topControlPanel_Layout.setVerticalGroup(
         topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(topControlPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                  .addComponent(animationFPSSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addComponent(fpsLabel_))
               .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                  .addComponent(lockScrollbarsCheckBox_)
                  .addComponent(scaleBarCheckBox_)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 13, Short.MAX_VALUE)
            .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(exploreButton_)
               .addComponent(elapsedTimeLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(acquireAtCurrentButton_)
               .addComponent(zPosLabel_)))
         .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(topControlPanel_Layout.createSequentialGroup()
               .addContainerGap()
               .addGroup(topControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                  .addComponent(showInFolderButton_)
                  .addComponent(abortButton_)
                  .addComponent(pauseButton_))
               .addContainerGap(77, Short.MAX_VALUE)))
      );

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addComponent(tabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
         .addComponent(topControlPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addComponent(topControlPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(tabbedPane_))
      );

      tabbedPane_.getAccessibleContext().setAccessibleName("Contrast");
   }// </editor-fold>//GEN-END:initComponents

   private void gridRowsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridRowsSpinner_StateChanged
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue());
      }
      display_.redrawOverlay();
   }//GEN-LAST:event_gridRowsSpinner_StateChanged

   private void gridColsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridColsSpinner_StateChanged
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue());
      }
      display_.redrawOverlay();
   }//GEN-LAST:event_gridColsSpinner_StateChanged

   private void showStagePositionsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showStagePositionsCheckBox_ActionPerformed
      display_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected());
      display_.redrawOverlay();
   }//GEN-LAST:event_showStagePositionsCheckBox_ActionPerformed

   private void showInterpCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInterpCheckBox_ActionPerformed
      display_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected());
      display_.redrawOverlay();
   }//GEN-LAST:event_showInterpCheckBox_ActionPerformed

   private void newGridButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newGridButton_ActionPerformed
      try {
         Point2D.Double coord = display_.getStageCoordinateOfViewCenter();
         MultiPosGrid r = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).newGrid(
                 (Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue(), coord);
         selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(r);
         surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
      } catch (NoPositionsDefinedYetException e) {
         JOptionPane.showMessageDialog(this, "Explore a tile first before adding a position");
         return;
      }
   }//GEN-LAST:event_newGridButton_ActionPerformed

   private void newSurfaceButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSurfaceButton_ActionPerformed
      SurfaceInterpolator s = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).addNewSurface();
      selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(s);
      surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
   }//GEN-LAST:event_newSurfaceButton_ActionPerformed

   private void selectUseAllButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectUseAllButton_ActionPerformed
      ((SimpleChannelTableModel) channelsTable_.getModel()).selectAllChannels();
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();
   }//GEN-LAST:event_selectUseAllButton_ActionPerformed

   private void syncExposuresButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncExposuresButton_ActionPerformed
      ((SimpleChannelTableModel) channelsTable_.getModel()).synchronizeExposures();
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();
   }//GEN-LAST:event_syncExposuresButton_ActionPerformed

   private void tabbedPane_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_tabbedPane_StateChanged

      if (display_.isExploreAcquisiton() && exploreButton_.isSelected() && tabbedPane_.getSelectedIndex() == 1) {
         exploreButton_.setSelected(false);
      } else if (display_.isExploreAcquisiton() && !exploreButton_.isSelected() && tabbedPane_.getSelectedIndex() != 1) {
         exploreButton_.setSelected(true);
      }
      updateMode();
   }//GEN-LAST:event_tabbedPane_StateChanged

   private void showInFolderButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInFolderButton_ActionPerformed
      display_.showFolder();
   }//GEN-LAST:event_showInFolderButton_ActionPerformed

   private void abortButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_abortButton_ActionPerformed
      display_.abortAcquisition();
   }//GEN-LAST:event_abortButton_ActionPerformed

   private void pauseButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButton_ActionPerformed
      display_.togglePauseAcquisition();
      pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource(
              display_.isAcquisitionPaused() ? "/org/micromanager/magellan/play.png" : "/org/micromanager/magellan/pause.png")));
      repaint();
   }//GEN-LAST:event_pauseButton_ActionPerformed

   private void animationFPSSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_animationFPSSpinner_StateChanged
      display_.setAnimateFPS(((Number) animationFPSSpinner_.getValue()).doubleValue());
   }//GEN-LAST:event_animationFPSSpinner_StateChanged

   private void lockScrollbarsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lockScrollbarsCheckBox_ActionPerformed
      if (!lockScrollbarsCheckBox_.isSelected()) {
         display_.unlockAllScroller();
      } else {
         display_.superlockAllScrollers();
      }
   }//GEN-LAST:event_lockScrollbarsCheckBox_ActionPerformed

   private void acquireAtCurrentButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acquireAtCurrentButton_ActionPerformed
      display_.acquireTileAtCurrentPosition();
   }//GEN-LAST:event_acquireAtCurrentButton_ActionPerformed

   private void exploreButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreButton_ActionPerformed
      if (display_.isExploreAcquisiton() && exploreButton_.isSelected() && tabbedPane_.getSelectedIndex() == 1) {
         tabbedPane_.setSelectedIndex(0); //switch away from surface grid mode when explorign activated
      }
      updateMode();
   }//GEN-LAST:event_exploreButton_ActionPerformed

   private void scaleBarCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scaleBarCheckBox_ActionPerformed
      display_.showScaleBar(scaleBarCheckBox_.isSelected());
      display_.redrawOverlay();
   }//GEN-LAST:event_scaleBarCheckBox_ActionPerformed


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton abortButton_;
   private javax.swing.JButton acquireAtCurrentButton_;
   private javax.swing.JSpinner animationFPSSpinner_;
   private javax.swing.JTable channelsTable_;
   private javax.swing.JPanel contrastPanelPanel_;
   private javax.swing.JLabel elapsedTimeLabel_;
   private javax.swing.JToggleButton exploreButton_;
   private javax.swing.JPanel explorePanel_;
   private javax.swing.JLabel fpsLabel_;
   private javax.swing.JLabel gridColsLabel_;
   private javax.swing.JSpinner gridColsSpinner_;
   private javax.swing.JPanel gridControlPanel_;
   private javax.swing.JLabel gridRowsLabel_;
   private javax.swing.JSpinner gridRowsSpinner_;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JScrollPane jScrollPane2;
   private javax.swing.JCheckBox lockScrollbarsCheckBox_;
   private org.micromanager.magellan.internal.imagedisplay.MetadataPanel metadataPanelMagellan_;
   private javax.swing.JPanel metadataPanel_;
   private javax.swing.JButton newGridButton_;
   private javax.swing.JButton newSurfaceButton_;
   private javax.swing.JButton pauseButton_;
   private javax.swing.JCheckBox scaleBarCheckBox_;
   private javax.swing.JButton selectUseAllButton_;
   private javax.swing.JButton showInFolderButton_;
   private javax.swing.JCheckBox showInterpCheckBox_;
   private javax.swing.JCheckBox showStagePositionsCheckBox_;
   private javax.swing.JPanel surfaceControlPanel_;
   private javax.swing.JPanel surfaceGridPanel_;
   private javax.swing.JPanel surfaceGridSpecificControlsPanel_;
   private javax.swing.JTable surfaceGridTable_;
   private javax.swing.JButton syncExposuresButton_;
   private javax.swing.JTabbedPane tabbedPane_;
   private javax.swing.JPanel topControlPanel_;
   private javax.swing.JLabel zPosLabel_;
   // End of variables declaration//GEN-END:variables

}
