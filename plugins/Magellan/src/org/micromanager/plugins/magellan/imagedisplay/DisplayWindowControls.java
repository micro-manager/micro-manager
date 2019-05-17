/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.plugins.magellan.imagedisplay;

import org.micromanager.plugins.magellan.acq.Acquisition;
import org.micromanager.plugins.magellan.acq.ExploreAcquisition;
import org.micromanager.plugins.magellan.channels.SimpleChannelTableModel;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.micromanager.plugins.magellan.gui.GUI;
import java.awt.Color;
import java.awt.Panel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.MD;
import org.micromanager.plugins.magellan.surfacesandregions.MultiPosRegion;
import org.micromanager.plugins.magellan.surfacesandregions.RegionManager;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceManager;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceRegionComboBoxModel;

/**
 *
 * @author henrypinkard
 */
public class DisplayWindowControls extends Panel {

   private static final Color LIGHT_GREEN = new Color(200, 255, 200);
   
   private EventBus bus_;
   private DisplayPlus display_;
   private RegionManager regionManager_;
   private SurfaceManager surfaceManager_;
   private Acquisition acq_;
   private Popup instructionsPopup_;
   private boolean updateGridParams_ = true;

   /**
    * Creates new form DisplayWindowControls
    */
   public DisplayWindowControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
       bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      surfaceManager_ = SurfaceManager.getInstance();
      regionManager_ = RegionManager.getInstance();   
      initComponents();
      //initially disable surface and grid
      tabbedPane_.setEnabledAt(1, false);
      tabbedPane_.setEnabledAt(2, false);      
      
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
         //start showing explore index
         tabbedPane_.setSelectedIndex(3); 
      } else {
         tabbedPane_.remove(3); //remove explore tab
      }
   }
   
   public void hideInstructionsPopup() {
      if (instructionsPopup_ != null ) {
         instructionsPopup_.hide();
         instructionsPopup_ = null;
         DisplayWindowControls.this.repaint();
      }
   }

   private void setupPopupHints() {
      //make custom instruction popups disappear
      tabbedPane_.addMouseMotionListener(new MouseMotionAdapter() {
         @Override
         public void mouseMoved(MouseEvent e) {
            hideInstructionsPopup();
         }        
      });
      
   }
   
   public void showStartupHints() {
      if (acq_ instanceof ExploreAcquisition) {
         showInstructionLabel("<html>Left click or click and drag to select tiles <br>"
                 + "Left click again to confirm <br>Right click and drag to pan<br>+/- keys or mouse wheel to zoom in/out</html>");
      } else {
         showInstructionLabel("<html>Right click and drag to pan<br>+/- keys or mouse wheel to zoom in/out</html>");
      }
      setupPopupHints();
   }
   
   @Subscribe
   public void onNewImageEvent(NewImageEvent e) {
      //once there's an image, surfaces and grids are game
      tabbedPane_.setEnabledAt(1, true);
      tabbedPane_.setEnabledAt(2, true);
   }

   @Subscribe
   public void onSetImageEvent(ScrollerPanel.SetImageEvent event) {
       if(display_.isClosing()) {
           return;
       }
       JSONObject tags = display_.getCurrentMetadata();
      if (tags == null) {
         return;
      }

      //update status panel
//      long sizeBytes = acq_.getStorage().getDataSetSize();
//      if (sizeBytes < 1024) {
//         datasetSizeLabel_.setText(sizeBytes + "  Bytes");
//      } else if (sizeBytes < 1024 * 1024) {
//         datasetSizeLabel_.setText(sizeBytes / 1024 + "  KB");
//      } else if (sizeBytes < 1024l * 1024 * 1024) {
//         datasetSizeLabel_.setText(sizeBytes / 1024 / 1024 + "  MB");
//      } else if (sizeBytes < 1024l * 1024 * 1024 * 1024) {
//         datasetSizeLabel_.setText(sizeBytes / 1024 / 1024 / 1024 + "  GB");
//      } else {
//         datasetSizeLabel_.setText(sizeBytes / 1024 / 1024 / 1024 / 1024 + "  TB");
//      }
      long elapsed = MD.getElapsedTimeMs(tags);
      long days = elapsed / (60 * 60 * 24 * 1000), hours = elapsed / 60 / 60 / 1000, minutes = elapsed / 60 / 1000, seconds = elapsed / 1000;

      hours = hours % 24;
      minutes = minutes % 60;
      seconds = seconds % 60;
      String h = ("0" + hours).substring(("0" + hours).length() - 2);
      String m = ("0" + (minutes)).substring(("0" + minutes).length() - 2);
      String s = ("0" + (seconds)).substring(("0" + seconds).length() - 2);
      String label = days + ":" + h + ":" + m + ":" + s + " (D:H:M:S)";

      elapsedTimeLabel_.setText(label);
      zPosLabel_.setText(MD.getZPositionUm(tags) + "um");
   }

   public void prepareForClose() {
      bus_.unregister(this);
      surfaceManager_.removeFromModelList((SurfaceRegionComboBoxModel) currentSufaceCombo_.getModel());
      regionManager_.removeFromModelList((SurfaceRegionComboBoxModel) currentGridCombo_.getModel());
      if (acq_ instanceof ExploreAcquisition) {
         ((SimpleChannelTableModel)channelsTable_.getModel()).shutdown();
      }
   }

   private MultiPosRegion createNewGrid() {
      int imageWidth = display_.getImagePlus().getWidth();
      int imageHeight = display_.getImagePlus().getHeight();
      return new MultiPosRegion(regionManager_, Magellan.getCore().getXYStageDevice(),
              (Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue(),
              display_.stageCoordFromImageCoords(imageWidth / 2, imageHeight / 2));
   }
   
   private void showInstructionLabel(String text) {
      if (!tabbedPane_.getSelectedComponent().isShowing()) {
         return;
      }

      if (instructionsPopup_ != null) {
         instructionsPopup_.hide();
      }     
      PopupFactory popupFactory = PopupFactory.getSharedInstance();
      int x = tabbedPane_.getSelectedComponent().getLocationOnScreen().x;
      int y = tabbedPane_.getSelectedComponent().getLocationOnScreen().y;

      JPanel background = new JPanel();
      background.setBorder(BorderFactory.createLineBorder(Color.black));
      background.setBackground(LIGHT_GREEN); //light green
      JLabel message = new JLabel(text);
      background.add(message);
      x += tabbedPane_.getSelectedComponent().getWidth() / 2 - background.getPreferredSize().width / 2;
      y += tabbedPane_.getSelectedComponent().getHeight() / 2 - background.getPreferredSize().height / 2;
      instructionsPopup_ = popupFactory.getPopup(tabbedPane_.getSelectedComponent(), background, x, y);
      instructionsPopup_.show();
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
        statusPanel_ = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        elapsedTimeLabel_ = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        zPosLabel_ = new javax.swing.JLabel();
        gridPanel_ = new javax.swing.JPanel();
        newGridButton_ = new javax.swing.JButton();
        currentGridsLabel_ = new javax.swing.JLabel();
        currentGridCombo_ = new javax.swing.JComboBox();
        gridRowsLabel_ = new javax.swing.JLabel();
        gridRowsSpinner_ = new javax.swing.JSpinner();
        gridColsLabel_ = new javax.swing.JLabel();
        gridColsSpinner_ = new javax.swing.JSpinner();
        surfacePanel_ = new javax.swing.JPanel();
        newSurfaceButton_ = new javax.swing.JButton();
        currentSurfaceLabel_ = new javax.swing.JLabel();
        currentSufaceCombo_ = new javax.swing.JComboBox();
        showFootprintCheckBox_ = new javax.swing.JCheckBox();
        showInterpCheckBox_ = new javax.swing.JCheckBox();
        showStagePositionsCheckBox_ = new javax.swing.JCheckBox();
        aboveBelowSurfaceCombo_ = new javax.swing.JComboBox();
        explorePanel_ = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        channelsTable_ = new javax.swing.JTable();
        acquireAtCurrentButton_ = new javax.swing.JButton();
        showInFolderButton_ = new javax.swing.JButton();
        abortButton_ = new javax.swing.JButton();
        pauseButton_ = new javax.swing.JButton();
        fpsLabel_ = new javax.swing.JLabel();
        animationFPSSpinner_ = new javax.swing.JSpinner();
        showNewImagesCheckBox_ = new javax.swing.JCheckBox();

        tabbedPane_.setToolTipText("");
        tabbedPane_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                tabbedPane_StateChanged(evt);
            }
        });

        jLabel2.setText("Elapsed time: ");

        jLabel3.setText("Z position: ");

        javax.swing.GroupLayout statusPanel_Layout = new javax.swing.GroupLayout(statusPanel_);
        statusPanel_.setLayout(statusPanel_Layout);
        statusPanel_Layout.setHorizontalGroup(
            statusPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(statusPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(statusPanel_Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(elapsedTimeLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 286, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(statusPanel_Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zPosLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 219, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(473, Short.MAX_VALUE))
        );
        statusPanel_Layout.setVerticalGroup(
            statusPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(statusPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(elapsedTimeLabel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(zPosLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addGap(44, 44, 44))
        );

        tabbedPane_.addTab("Status", statusPanel_);

        gridPanel_.setToolTipText("Left click and drag to move grid");

        newGridButton_.setText("New Grid");
        newGridButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newGridButton_ActionPerformed(evt);
            }
        });

        currentGridsLabel_.setText("Current grid:");

        currentGridCombo_.setModel(GUI.createSurfaceAndRegionComboBoxModel(false, true));
        currentGridCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                currentGridCombo_ActionPerformed(evt);
            }
        });

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

        javax.swing.GroupLayout gridPanel_Layout = new javax.swing.GroupLayout(gridPanel_);
        gridPanel_.setLayout(gridPanel_Layout);
        gridPanel_Layout.setHorizontalGroup(
            gridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gridPanel_Layout.createSequentialGroup()
                .addComponent(newGridButton_)
                .addGap(45, 45, 45)
                .addGroup(gridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(gridPanel_Layout.createSequentialGroup()
                        .addComponent(currentGridsLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(currentGridCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(gridPanel_Layout.createSequentialGroup()
                        .addComponent(gridRowsLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(gridRowsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(gridColsLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(gridColsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(122, 348, Short.MAX_VALUE))
        );
        gridPanel_Layout.setVerticalGroup(
            gridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gridPanel_Layout.createSequentialGroup()
                .addGroup(gridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(gridPanel_Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(gridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(currentGridsLabel_)
                            .addComponent(currentGridCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(gridPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(gridRowsLabel_)
                            .addComponent(gridRowsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(gridColsLabel_)
                            .addComponent(gridColsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(gridPanel_Layout.createSequentialGroup()
                        .addGap(24, 24, 24)
                        .addComponent(newGridButton_)))
                .addContainerGap(49, Short.MAX_VALUE))
        );

        tabbedPane_.addTab("Grid", gridPanel_);

        surfacePanel_.setToolTipText("<html>Left click to add interpolation points<br>\nRight click to remove points<br>\nShift + right click to remove all points at Z slice</html>");

        newSurfaceButton_.setText("New Surface");
        newSurfaceButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newSurfaceButton_ActionPerformed(evt);
            }
        });

        currentSurfaceLabel_.setText("Current Surface:");

        currentSufaceCombo_.setModel(GUI.createSurfaceAndRegionComboBoxModel(true, false));
        currentSufaceCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                currentSufaceCombo_ActionPerformed(evt);
            }
        });

        showFootprintCheckBox_.setSelected(true);
        showFootprintCheckBox_.setText("Footprint");
        showFootprintCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showFootprintCheckBox_ActionPerformed(evt);
            }
        });

        showInterpCheckBox_.setSelected(true);
        showInterpCheckBox_.setText("Interpolation");
        showInterpCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInterpCheckBox_ActionPerformed(evt);
            }
        });

        showStagePositionsCheckBox_.setSelected(true);
        showStagePositionsCheckBox_.setText("Tiles");
        showStagePositionsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showStagePositionsCheckBox_ActionPerformed(evt);
            }
        });

        aboveBelowSurfaceCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Above surface", "Below surface" }));
        aboveBelowSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboveBelowSurfaceCombo_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout surfacePanel_Layout = new javax.swing.GroupLayout(surfacePanel_);
        surfacePanel_.setLayout(surfacePanel_Layout);
        surfacePanel_Layout.setHorizontalGroup(
            surfacePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacePanel_Layout.createSequentialGroup()
                .addGroup(surfacePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(surfacePanel_Layout.createSequentialGroup()
                        .addComponent(newSurfaceButton_)
                        .addGap(18, 18, 18)
                        .addComponent(showFootprintCheckBox_)
                        .addGap(18, 18, 18)
                        .addComponent(showInterpCheckBox_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(showStagePositionsCheckBox_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(aboveBelowSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(surfacePanel_Layout.createSequentialGroup()
                        .addGap(163, 163, 163)
                        .addComponent(currentSurfaceLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(currentSufaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(280, Short.MAX_VALUE))
        );
        surfacePanel_Layout.setVerticalGroup(
            surfacePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacePanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(surfacePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(currentSurfaceLabel_)
                    .addComponent(currentSufaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(2, 2, 2)
                .addGroup(surfacePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(newSurfaceButton_)
                    .addComponent(showFootprintCheckBox_)
                    .addComponent(showInterpCheckBox_)
                    .addComponent(showStagePositionsCheckBox_)
                    .addComponent(aboveBelowSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(50, Short.MAX_VALUE))
        );

        tabbedPane_.addTab("Surface", surfacePanel_);

        explorePanel_.setToolTipText("<html>Left click or click and drag to select tiles <br>Left click again to confirm <br>Right click and drag to pan<br>+/- keys or mouse wheel to zoom in/out</html>");

        channelsTable_.setModel(acq_ != null ? new SimpleChannelTableModel(acq_.getChannels()) : new DefaultTableModel());
        jScrollPane1.setViewportView(channelsTable_);

        acquireAtCurrentButton_.setText("Acquire tile at current position");
        acquireAtCurrentButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                acquireAtCurrentButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout explorePanel_Layout = new javax.swing.GroupLayout(explorePanel_);
        explorePanel_.setLayout(explorePanel_Layout);
        explorePanel_Layout.setHorizontalGroup(
            explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 842, Short.MAX_VALUE)
            .addGroup(explorePanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(acquireAtCurrentButton_)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        explorePanel_Layout.setVerticalGroup(
            explorePanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, explorePanel_Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(acquireAtCurrentButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        tabbedPane_.addTab("Explore", explorePanel_);

        showInFolderButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/plugins/magellan/icons/folder.png"))); // NOI18N
        showInFolderButton_.setToolTipText("Show in folder");
        showInFolderButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInFolderButton_ActionPerformed(evt);
            }
        });

        abortButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/plugins/magellan/icons/abort.png"))); // NOI18N
        abortButton_.setToolTipText("Abort acquisition");
        abortButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                abortButton_ActionPerformed(evt);
            }
        });

        pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/plugins/magellan/icons/pause.png"))); // NOI18N
        pauseButton_.setToolTipText("Pause/resume acquisition");
        pauseButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButton_ActionPerformed(evt);
            }
        });

        fpsLabel_.setText("Animation FPS:");

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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(showInFolderButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(abortButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pauseButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fpsLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(animationFPSSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(showNewImagesCheckBox_)
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(tabbedPane_)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addComponent(tabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        tabbedPane_.getAccessibleContext().setAccessibleName("Status");
    }// </editor-fold>//GEN-END:initComponents

   private void newSurfaceButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSurfaceButton_ActionPerformed
      surfaceManager_.addNewSurface();
      currentSufaceCombo_.setSelectedIndex(currentSufaceCombo_.getItemCount() - 1);
      display_.setCurrentSurface(surfaceManager_.getSurface(currentSufaceCombo_.getSelectedIndex()));
      currentSufaceCombo_.repaint();
   }//GEN-LAST:event_newSurfaceButton_ActionPerformed

   private void showInterpCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInterpCheckBox_ActionPerformed
            display_.setSurfaceDisplaySettings(showFootprintCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected() && 
              aboveBelowSurfaceCombo_.getSelectedIndex() == 0, showStagePositionsCheckBox_.isSelected() && 
              aboveBelowSurfaceCombo_.getSelectedIndex() == 1, showInterpCheckBox_.isSelected());
   }//GEN-LAST:event_showInterpCheckBox_ActionPerformed

   private void showStagePositionsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showStagePositionsCheckBox_ActionPerformed
          display_.setSurfaceDisplaySettings(showFootprintCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected() && 
              aboveBelowSurfaceCombo_.getSelectedIndex() == 0, showStagePositionsCheckBox_.isSelected() && 
              aboveBelowSurfaceCombo_.getSelectedIndex() == 1, showInterpCheckBox_.isSelected());
   }//GEN-LAST:event_showStagePositionsCheckBox_ActionPerformed

   private void showNewImagesCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showNewImagesCheckBox_ActionPerformed
      if (showNewImagesCheckBox_.isSelected()) {
         display_.unlockAllScroller();
      } else {
         display_.superlockAllScrollers();
      }
   }//GEN-LAST:event_showNewImagesCheckBox_ActionPerformed

   private void tabbedPane_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_tabbedPane_StateChanged
      if (tabbedPane_.getSelectedIndex() == 0) { //status
         display_.setMode(DisplayPlus.NONE);
      }   else if (tabbedPane_.getSelectedIndex() == 1) { // grid
         //make a grid if none exists"
         if (regionManager_.getNumberOfRegions() == 0) {
            regionManager_.addNewRegion(createNewGrid());
            currentGridCombo_.setSelectedIndex(0);
         }    
         display_.setMode(DisplayPlus.NEWGRID);
         showInstructionLabel(((JPanel)tabbedPane_.getComponentAt(1)).getToolTipText());
      } else if (tabbedPane_.getSelectedIndex() == 2) { //surface
         //make a surface if none exists
         if (surfaceManager_.getNumberOfSurfaces() == 0) {
            surfaceManager_.addNewSurface();
            currentSufaceCombo_.setSelectedIndex(0);
         }
         //show surface creation controls           
         display_.setMode(DisplayPlus.SURFACE);
         //show tooltip
         showInstructionLabel(((JPanel) tabbedPane_.getComponentAt(2)).getToolTipText());
      } else if (tabbedPane_.getSelectedIndex() == 3) { //explore
         display_.setMode(DisplayPlus.EXPLORE);
         showInstructionLabel(((JPanel) tabbedPane_.getComponentAt(3)).getToolTipText());
      }
   }//GEN-LAST:event_tabbedPane_StateChanged

   private void gridRowsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridRowsSpinner_StateChanged
      int index = currentGridCombo_.getSelectedIndex();
      if (index != -1 && updateGridParams_) {
         MultiPosRegion r = regionManager_.getRegion(index);
         r.updateParams((Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue());
      }
   }//GEN-LAST:event_gridRowsSpinner_StateChanged

   private void currentGridCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_currentGridCombo_ActionPerformed
      display_.setCurrentRegion(regionManager_.getRegion(currentGridCombo_.getSelectedIndex()));
      if (currentGridCombo_.getSelectedItem() != null) {
         updateGridParams_ = false; //so that grid/col info doesnt change while switching to new
         gridRowsSpinner_.setValue(regionManager_.getRegion(currentGridCombo_.getSelectedIndex()).numRows());
         gridColsSpinner_.setValue(regionManager_.getRegion(currentGridCombo_.getSelectedIndex()).numCols());
         updateGridParams_ = true;
      }
   }//GEN-LAST:event_currentGridCombo_ActionPerformed

   private void newGridButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newGridButton_ActionPerformed
      regionManager_.addNewRegion(createNewGrid());
      currentGridCombo_.setSelectedIndex(currentGridCombo_.getItemCount() - 1);
      display_.setCurrentRegion(regionManager_.getRegion(currentGridCombo_.getSelectedIndex()));
      currentGridCombo_.repaint();
   }//GEN-LAST:event_newGridButton_ActionPerformed

   private void showInFolderButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInFolderButton_ActionPerformed
      display_.showFolder();
   }//GEN-LAST:event_showInFolderButton_ActionPerformed

   private void showFootprintCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showFootprintCheckBox_ActionPerformed
      display_.setSurfaceDisplaySettings(showFootprintCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected()
              && aboveBelowSurfaceCombo_.getSelectedIndex() == 0, showStagePositionsCheckBox_.isSelected()
              && aboveBelowSurfaceCombo_.getSelectedIndex() == 1, showInterpCheckBox_.isSelected());
   }//GEN-LAST:event_showFootprintCheckBox_ActionPerformed

   private void aboveBelowSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboveBelowSurfaceCombo_ActionPerformed
      display_.setSurfaceDisplaySettings(showFootprintCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected()
              && aboveBelowSurfaceCombo_.getSelectedIndex() == 0, showStagePositionsCheckBox_.isSelected()
              && aboveBelowSurfaceCombo_.getSelectedIndex() == 1, showInterpCheckBox_.isSelected());
   }//GEN-LAST:event_aboveBelowSurfaceCombo_ActionPerformed

   private void currentSufaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_currentSufaceCombo_ActionPerformed
      display_.setCurrentSurface(surfaceManager_.getSurface(currentSufaceCombo_.getSelectedIndex()));
   }//GEN-LAST:event_currentSufaceCombo_ActionPerformed

   private void animationFPSSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_animationFPSSpinner_StateChanged
      display_.setAnimateFPS( ((Number)animationFPSSpinner_.getValue()).doubleValue());
   }//GEN-LAST:event_animationFPSSpinner_StateChanged

    private void pauseButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButton_ActionPerformed
        acq_.togglePaused();
        pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource(
                acq_.isPaused() ? "/org/micromanager/plugins/magellan/icons/play.png" : "/org/micromanager/plugins/magellan/icons/pause.png")));
        repaint();
    }//GEN-LAST:event_pauseButton_ActionPerformed

    private void abortButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_abortButton_ActionPerformed
        acq_.abort();
    }//GEN-LAST:event_abortButton_ActionPerformed

   private void gridColsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridColsSpinner_StateChanged
      int index = currentGridCombo_.getSelectedIndex();
      if (index != -1 && updateGridParams_) {
         MultiPosRegion r = regionManager_.getRegion(index);
         r.updateParams((Integer) gridRowsSpinner_.getValue(), (Integer) gridColsSpinner_.getValue());
      }
   }//GEN-LAST:event_gridColsSpinner_StateChanged

    private void acquireAtCurrentButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acquireAtCurrentButton_ActionPerformed
        ((ExploreAcquisition) acq_).acquireTileAtCurrentLocation( ((DisplayWindow)display_.getImagePlus().getWindow()).getSubImageControls()); 
    }//GEN-LAST:event_acquireAtCurrentButton_ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton abortButton_;
    private javax.swing.JComboBox aboveBelowSurfaceCombo_;
    private javax.swing.JButton acquireAtCurrentButton_;
    private javax.swing.JSpinner animationFPSSpinner_;
    private javax.swing.JTable channelsTable_;
    private javax.swing.JComboBox currentGridCombo_;
    private javax.swing.JLabel currentGridsLabel_;
    private javax.swing.JComboBox currentSufaceCombo_;
    private javax.swing.JLabel currentSurfaceLabel_;
    private javax.swing.JLabel elapsedTimeLabel_;
    private javax.swing.JPanel explorePanel_;
    private javax.swing.JLabel fpsLabel_;
    private javax.swing.JLabel gridColsLabel_;
    private javax.swing.JSpinner gridColsSpinner_;
    private javax.swing.JPanel gridPanel_;
    private javax.swing.JLabel gridRowsLabel_;
    private javax.swing.JSpinner gridRowsSpinner_;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton newGridButton_;
    private javax.swing.JButton newSurfaceButton_;
    private javax.swing.JButton pauseButton_;
    private javax.swing.JCheckBox showFootprintCheckBox_;
    private javax.swing.JButton showInFolderButton_;
    private javax.swing.JCheckBox showInterpCheckBox_;
    private javax.swing.JCheckBox showNewImagesCheckBox_;
    private javax.swing.JCheckBox showStagePositionsCheckBox_;
    private javax.swing.JPanel statusPanel_;
    private javax.swing.JPanel surfacePanel_;
    private javax.swing.JTabbedPane tabbedPane_;
    private javax.swing.JLabel zPosLabel_;
    // End of variables declaration//GEN-END:variables
}
