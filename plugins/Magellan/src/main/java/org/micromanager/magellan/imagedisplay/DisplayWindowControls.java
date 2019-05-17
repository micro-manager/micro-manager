/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template ind the editor.
 */
package org.micromanager.magellan.imagedisplay;

import org.micromanager.magellan.acq.Acquisition;
import org.micromanager.magellan.acq.ExploreAcquisition;
import org.micromanager.magellan.gui.SimpleChannelTableModel;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Panel;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.DefaultCellEditor;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.micromanager.magellan.coordinates.NoPositionsDefinedYetException;
import org.micromanager.magellan.json.JSONObject;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.ExactlyOneRowSelectionModel;
import org.micromanager.magellan.misc.MD;
import org.micromanager.magellan.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.surfacesandregions.XYFootprint;

/**
 *
 * @author henrypinkard
 */
public class DisplayWindowControls extends Panel implements SurfaceGridListener {

   private static final Color LIGHT_BLUE = new Color(200, 200, 255);

   private EventBus bus_;
   private DisplayPlus display_;
   private SurfaceGridManager manager_ = SurfaceGridManager.getInstance();
   private Acquisition acq_;
   private volatile int selectedSurfaceGridIndex_ = -1;

   /**
    * Creates new form DisplayWindowControls
    */
   public DisplayWindowControls(DisplayPlus disp, EventBus bus, Acquisition acq) {

      bus_ = bus;
      display_ = disp;
      disp.registerControls(this);
      bus_.register(this);
      acq_ = acq;
      initComponents();
      metadataPanelMagellan_.setSummaryMetadata(disp.getSummaryMetadata());

      this.setFocusable(false); //think this is good 

      if (acq_ instanceof ExploreAcquisition) {
         //left justified editor
         JTextField tf = new JTextField();
         tf.setHorizontalAlignment(SwingConstants.LEFT);
         DefaultCellEditor ed = new DefaultCellEditor(tf);
         channelsTable_.getColumnModel().getColumn(2).setCellEditor(ed);
         //and renderer
         DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
         renderer.setHorizontalAlignment(SwingConstants.LEFT); // left justify
         channelsTable_.getColumnModel().getColumn(2).setCellRenderer(renderer);
         //start in explore
         tabbedPane_.setSelectedIndex(0);
      } else {
         tabbedPane_.remove(0); //remove explore tab
         acquireAtCurrentButton_.setVisible(false);
         tabbedPane_.setSelectedIndex(1); //statr on contrst
      }

      //exactly one surface or grid selected at all times
      surfaceGridTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
      surfaceGridTable_.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
               return;
               //action occurs second time this method is called, after the table gains focus
            }
            updateSurfaceGridSelection();
         }
      });
      //Table column widths
      surfaceGridTable_.getColumnModel().getColumn(0).setMaxWidth(40); //show column
      surfaceGridTable_.getColumnModel().getColumn(1).setMaxWidth(120); //type column
      //So it is initialized correctly when surfaces are already present
      updateSurfaceGridSelection();
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
      display_.drawOverlay();
   }

   /**
    * Called just before image redrawn
    */
   public void imageChangedUpdate(JSONObject metadata) {
      if (cpMagellan_ != null) {
         cpMagellan_.imageChangedUpdate();
      }
      if (metadataPanel_ != null) {
         metadataPanelMagellan_.imageChangedUpdate(metadata);
      }
      updateStatusLabel(metadata);
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

   public ContrastPanelMagellanAdapter getContrastPanelMagellan() {
      return cpMagellan_;
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

   @Subscribe
   public void onNewImageEvent(NewImageEvent e) {
      //once there's an image, surfaces and grids are game
//      tabbedPane_.setEnabledAt(acq_ instanceof ExploreAcquisition ? 1 : 0, true);
   }

   @Subscribe
   public void onSetImageEvent(ScrollerPanel.SetImageEvent event) {
      if (display_.isClosing()) {
         return;
      }
      JSONObject tags = display_.getCurrentMetadata();
      if (tags == null) {
         return;
      }
      updateStatusLabel(tags);
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

   public void prepareForClose() {
      bus_.unregister(this);
      ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).shutdown();
      if (acq_ instanceof ExploreAcquisition) {
         ((SimpleChannelTableModel) channelsTable_.getModel()).shutdown();
      }
   }

   private MultiPosGrid createNewGrid() {
      int imageWidth = display_.getImagePlus().getWidth();
      int imageHeight = display_.getImagePlus().getHeight();
      return new MultiPosGrid(manager_, Magellan.getCore().getXYStageDevice(),
              (Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue(),
              display_.stageCoordFromImageCoords(imageWidth / 2, imageHeight / 2));
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
      explorePanel_ = new javax.swing.JPanel();
      jScrollPane1 = new javax.swing.JScrollPane();
      channelsTable_ = new javax.swing.JTable();
      acquireAtCurrentButton_ = new javax.swing.JButton();
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
      contrastPanelPanel_ = new javax.swing.JPanel();
      cpMagellan_ = new org.micromanager.magellan.imagedisplay.ContrastPanelMagellanAdapter();
      metadataPanel_ = new javax.swing.JPanel();
      metadataPanelMagellan_ = new org.micromanager.magellan.imagedisplay.MetadataPanel();
      showInFolderButton_ = new javax.swing.JButton();
      abortButton_ = new javax.swing.JButton();
      pauseButton_ = new javax.swing.JButton();
      fpsLabel_ = new javax.swing.JLabel();
      animationFPSSpinner_ = new javax.swing.JSpinner();
      showNewImagesCheckBox_ = new javax.swing.JCheckBox();
      elapsedTimeLabel_ = new javax.swing.JLabel();
      zPosLabel_ = new javax.swing.JLabel();

      tabbedPane_.setToolTipText("");
      tabbedPane_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            tabbedPane_StateChanged(evt);
         }
      });

      explorePanel_.setToolTipText("<html>Left click or click and drag to select tiles <br>Left click again to confirm <br>Right click and drag to pan<br>+/- keys or mouse wheel to zoom in/out</html>");

      channelsTable_.setModel(acq_ != null ? new SimpleChannelTableModel(acq_.getChannels(),false) : new DefaultTableModel());
      jScrollPane1.setViewportView(channelsTable_);

      acquireAtCurrentButton_.setText("Acquire image at current hardware position");
      acquireAtCurrentButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            acquireAtCurrentButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout explorePanel_Layout = new javax.swing.GroupLayout(explorePanel_);
      explorePanel_.setLayout(explorePanel_Layout);
      explorePanel_Layout.setHorizontalGroup(
         explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane1)
         .addGroup(explorePanel_Layout.createSequentialGroup()
            .addGap(159, 159, 159)
            .addComponent(acquireAtCurrentButton_)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      explorePanel_Layout.setVerticalGroup(
         explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, explorePanel_Layout.createSequentialGroup()
            .addComponent(acquireAtCurrentButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 339, Short.MAX_VALUE))
      );

      tabbedPane_.addTab("Explore", explorePanel_);

      surfaceGridTable_.setModel(new DisplayWindowSurfaceGridTableModel(display_)
      );
      surfaceGridTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
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
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(surfaceGridSpecificControlsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, 424, javax.swing.GroupLayout.PREFERRED_SIZE))
         .addGroup(surfaceGridPanel_Layout.createSequentialGroup()
            .addComponent(jScrollPane2)
            .addContainerGap())
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
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE))
      );

      tabbedPane_.addTab("Surfaces and Grids", surfaceGridPanel_);

      javax.swing.GroupLayout contrastPanelPanel_Layout = new javax.swing.GroupLayout(contrastPanelPanel_);
      contrastPanelPanel_.setLayout(contrastPanelPanel_Layout);
      contrastPanelPanel_Layout.setHorizontalGroup(
         contrastPanelPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(cpMagellan_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      );
      contrastPanelPanel_Layout.setVerticalGroup(
         contrastPanelPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(cpMagellan_, javax.swing.GroupLayout.DEFAULT_SIZE, 374, Short.MAX_VALUE)
      );

      tabbedPane_.addTab("Contrast", contrastPanelPanel_);

      javax.swing.GroupLayout metadataPanel_Layout = new javax.swing.GroupLayout(metadataPanel_);
      metadataPanel_.setLayout(metadataPanel_Layout);
      metadataPanel_Layout.setHorizontalGroup(
         metadataPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(metadataPanelMagellan_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      );
      metadataPanel_Layout.setVerticalGroup(
         metadataPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(metadataPanelMagellan_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      );

      tabbedPane_.addTab("Metadata", metadataPanel_);

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

      showNewImagesCheckBox_.setSelected(true);
      showNewImagesCheckBox_.setText("Move scrollbars on new image");
      showNewImagesCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showNewImagesCheckBox_ActionPerformed(evt);
         }
      });

      elapsedTimeLabel_.setText("Elapsed time: ");

      zPosLabel_.setText("Display Z position: ");

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(tabbedPane_)
               .addGroup(layout.createSequentialGroup()
                  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(layout.createSequentialGroup()
                        .addComponent(showInFolderButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(abortButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pauseButton_)
                        .addGap(18, 18, 18)
                        .addComponent(fpsLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(animationFPSSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(showNewImagesCheckBox_))
                     .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(zPosLabel_)
                        .addGap(71, 71, 71)
                        .addComponent(elapsedTimeLabel_)))
                  .addGap(0, 0, Short.MAX_VALUE)))
            .addContainerGap())
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                  .addComponent(showInFolderButton_)
                  .addComponent(abortButton_)
                  .addComponent(pauseButton_))
               .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                  .addComponent(fpsLabel_)
                  .addComponent(animationFPSSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addComponent(showNewImagesCheckBox_)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(zPosLabel_)
               .addComponent(elapsedTimeLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(tabbedPane_)
            .addContainerGap())
      );

      tabbedPane_.getAccessibleContext().setAccessibleName("Status");
   }// </editor-fold>//GEN-END:initComponents

   private void newSurfaceButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSurfaceButton_ActionPerformed
      SurfaceInterpolator s = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).addNewSurface();
      selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(s);
      surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
   }//GEN-LAST:event_newSurfaceButton_ActionPerformed

   private void showInterpCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInterpCheckBox_ActionPerformed
      display_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected());
      display_.drawOverlay();
   }//GEN-LAST:event_showInterpCheckBox_ActionPerformed

   private void showStagePositionsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showStagePositionsCheckBox_ActionPerformed
      display_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected());
      display_.drawOverlay();
   }//GEN-LAST:event_showStagePositionsCheckBox_ActionPerformed

   private void showNewImagesCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showNewImagesCheckBox_ActionPerformed
      if (showNewImagesCheckBox_.isSelected()) {
         display_.unlockAllScroller();
      } else {
         display_.superlockAllScrollers();
      }
   }//GEN-LAST:event_showNewImagesCheckBox_ActionPerformed

   private void tabbedPane_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_tabbedPane_StateChanged
      if (acq_ instanceof ExploreAcquisition) {
         if (tabbedPane_.getSelectedIndex() == 0) {
            display_.setMode(DisplayPlus.EXPLORE);
         } else if (tabbedPane_.getSelectedIndex() == 1) {
            display_.setMode(DisplayPlus.SURFACE_AND_GRID);
         } else if (tabbedPane_.getSelectedIndex() == 2) {
            display_.setMode(DisplayPlus.NONE);
         } else {
            display_.setMode(DisplayPlus.NONE);
         }
      } else if (tabbedPane_.getSelectedIndex() == 0) {
         display_.setMode(DisplayPlus.SURFACE_AND_GRID);
      } else if (tabbedPane_.getSelectedIndex() == 1) {
         display_.setMode(DisplayPlus.NONE);
      } else {
         display_.setMode(DisplayPlus.NONE);
      }
   }//GEN-LAST:event_tabbedPane_StateChanged

   private void gridRowsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridRowsSpinner_StateChanged
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue());
      }
   }//GEN-LAST:event_gridRowsSpinner_StateChanged

   private void newGridButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newGridButton_ActionPerformed
      try {
         Point2D.Double coord = display_.getCurrentDisplayedCoordinate();
         MultiPosGrid r = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).newGrid(
                 (Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue(), coord);
         selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(r);
         surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
      } catch (NoPositionsDefinedYetException e) {
         JOptionPane.showMessageDialog(this, "Explore a tile first before adding a position");
         return;
      }
   }//GEN-LAST:event_newGridButton_ActionPerformed

   private void showInFolderButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInFolderButton_ActionPerformed
      display_.showFolder();
   }//GEN-LAST:event_showInFolderButton_ActionPerformed

   private void animationFPSSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_animationFPSSpinner_StateChanged
      display_.setAnimateFPS(((Number) animationFPSSpinner_.getValue()).doubleValue());
   }//GEN-LAST:event_animationFPSSpinner_StateChanged

    private void pauseButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButton_ActionPerformed
       acq_.togglePaused();
       pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource(
               acq_.isPaused() ? "/org/micromanager/magellan/play.png" : "/org/micromanager/magellan/pause.png")));
      repaint();
    }//GEN-LAST:event_pauseButton_ActionPerformed

    private void abortButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_abortButton_ActionPerformed
       acq_.abort();
    }//GEN-LAST:event_abortButton_ActionPerformed

   private void gridColsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridColsSpinner_StateChanged
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue());
      }
   }//GEN-LAST:event_gridColsSpinner_StateChanged

    private void acquireAtCurrentButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acquireAtCurrentButton_ActionPerformed
       ((ExploreAcquisition) acq_).acquireTileAtCurrentLocation(((DisplayWindow) display_.getImagePlus().getWindow()).getSubImageControls());
    }//GEN-LAST:event_acquireAtCurrentButton_ActionPerformed

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton abortButton_;
   private javax.swing.JButton acquireAtCurrentButton_;
   private javax.swing.JSpinner animationFPSSpinner_;
   private javax.swing.JTable channelsTable_;
   private javax.swing.JPanel contrastPanelPanel_;
   private org.micromanager.magellan.imagedisplay.ContrastPanelMagellanAdapter cpMagellan_;
   private javax.swing.JLabel elapsedTimeLabel_;
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
   private org.micromanager.magellan.imagedisplay.MetadataPanel metadataPanelMagellan_;
   private javax.swing.JPanel metadataPanel_;
   private javax.swing.JButton newGridButton_;
   private javax.swing.JButton newSurfaceButton_;
   private javax.swing.JButton pauseButton_;
   private javax.swing.JButton showInFolderButton_;
   private javax.swing.JCheckBox showInterpCheckBox_;
   private javax.swing.JCheckBox showNewImagesCheckBox_;
   private javax.swing.JCheckBox showStagePositionsCheckBox_;
   private javax.swing.JPanel surfaceControlPanel_;
   private javax.swing.JPanel surfaceGridPanel_;
   private javax.swing.JPanel surfaceGridSpecificControlsPanel_;
   private javax.swing.JTable surfaceGridTable_;
   private javax.swing.JTabbedPane tabbedPane_;
   private javax.swing.JLabel zPosLabel_;
   // End of variables declaration//GEN-END:variables

}
