/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagedisplay;

import acq.Acquisition;
import acq.ExploreAcquisition;
import com.google.common.eventbus.EventBus;
import gui.GUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import mmcloneclasses.internalinterfaces.DisplayControls;
import net.miginfocom.swing.MigLayout;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import surfacesandregions.MultiPosGrid;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceManager;
import surfacesandregions.SurfaceRegionComboBoxModel;

/**
 *
 * @author Henry
 */
public class DisplayPlusControls extends DisplayControls {

   private final static int DEFAULT_FPS = 10;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private static final DecimalFormat THREE_DECIMAL_FORMAT = new DecimalFormat("0.000");
   private final static String GRID_PANEL = "Grid controls", EXPLORE_PANEL = "Explore panel",
           SURFACE_PANEL = "Create surface", DEFAULT_PANEL = "Default panel";
   
   
   private EventBus bus_;
   private DisplayPlus display_;
   private ScrollerPanel scrollerPanel_;
   private Timer nextFrameTimer_;
   private JButton pauseButton_, abortButton_, showFolderButton_;
   private JTextField fpsField_;
   private JLabel helpLabel_, nextFrameLabel_, zPosLabel_, timeStampLabel_;
   private JPanel labelsPanel_;
   private JToggleButton gridButton_, surfaceButton_;
   private JToggleButton exploreButton_, gotoButton_;
   private Acquisition acq_;
   private CardLayout changingPanelLayout_;
   private JPanel changingPanel_;
   private JSpinner gridRowSpinner_, gridColSpinner_;
   private RegionManager regionManager_;
   private SurfaceManager surfaceManager_;
   private JComboBox surfacesCombo_, regionsCombo_;
   
   public DisplayPlusControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      surfaceManager_ = SurfaceManager.getInstance();
      regionManager_ = RegionManager.getInstance();
      initComponents();
   }
   
   private void resizeLabels() {
      labelsPanel_.invalidate();
      labelsPanel_.validate();
   }

   private void newSurfaceButtonAction() {
      if (surfaceButton_.isSelected()) { 
         //deselect others
         if (acq_ instanceof ExploreAcquisition) {
            exploreButton_.setSelected(false);
         }
         gridButton_.setSelected(false);
         //make a surface if none exists
         if (surfaceManager_.getNumberOfSurfaces() == 0) {
            surfaceManager_.addNewSurface();
            surfacesCombo_.setSelectedIndex(0);
         }
         //show surface creation controls           
         changingPanelLayout_.show(changingPanel_, SURFACE_PANEL);
         helpLabel_.setText("Left click to add points, right click to remove points, right click and drag to pan");
         display_.setMode(DisplayPlus.NEWSURFACE);
      } else {
         //return to none mode
         display_.setMode(DisplayPlus.NONE);
         changingPanelLayout_.show(changingPanel_, DEFAULT_PANEL);
         helpLabel_.setText("Right click and drag to pan, use + and - keys to zoom");
      }
      resizeLabels();
   }

   private void newGridButtonAction() {
      if (gridButton_.isSelected()) { 
         //deselect others
         if (acq_ instanceof ExploreAcquisition) {
            exploreButton_.setSelected(false);
         }
         surfaceButton_.setSelected(false);
         //make a grid if none exists"
         if (regionManager_.getNumberOfRegions() == 0) {
            regionManager_.addNewRegion(createNewGrid());
            regionsCombo_.setSelectedIndex(0);
         }

         //show grid making controls           
         changingPanelLayout_.show(changingPanel_, GRID_PANEL);
         helpLabel_.setText("Left click and drag to move grid, right click and drag to pan");
         display_.setMode(DisplayPlus.NEWGRID);
      } else {
         //return to none mode
         display_.setMode(DisplayPlus.NONE);
         changingPanelLayout_.show(changingPanel_, DEFAULT_PANEL);            
         helpLabel_.setText("Right click and drag to pan, use + and - keys to zoom");
      }
      resizeLabels();
   }

   private void exploreButtonAction() {
      if (exploreButton_.isSelected()) {
         //deselect others
         surfaceButton_.setSelected(false);
         gridButton_.setSelected(false);
         changingPanelLayout_.show(changingPanel_, EXPLORE_PANEL);
         display_.setMode(exploreButton_.isSelected() ? DisplayPlus.EXPLORE : DisplayPlus.NONE);
      } else {
         //return to none mode
         display_.setMode(DisplayPlus.NONE);
         changingPanelLayout_.show(changingPanel_, DEFAULT_PANEL);
         helpLabel_.setText("Right click and drag to pan, use + and - keys to zoom");
      }
      resizeLabels();
   }

   private void initComponents() {
      final JPanel controlsPanel = new JPanel(new MigLayout("insets 0, fillx, align center", "", "[]0[]0[]"));

      labelsPanel_ = new JPanel(new MigLayout(""));
      helpLabel_ = new JLabel("Right click and drag to pan, use + and - keys to zoom");
      labelsPanel_.add(helpLabel_, "growx");
      makeStatusLabels();
      labelsPanel_.add(zPosLabel_);
      if (!(acq_ instanceof ExploreAcquisition))  {
         labelsPanel_.add(timeStampLabel_);
         labelsPanel_.add(zPosLabel_);
      }

      controlsPanel.add(labelsPanel_, "span, growx, align center, wrap");
      if (!(acq_ instanceof ExploreAcquisition))  {
         labelsPanel_.add(nextFrameLabel_);
         nextFrameTimer_ = new Timer(1000, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
               long nextImageTime = 0;
               try {
                  nextImageTime = display_.getNextWakeTime();
               } catch (NullPointerException ex) {
                  nextFrameTimer_.stop();
               }
               if (display_.acquisitionFinished()) {
                  nextFrameTimer_.stop();
               }
               double timeRemainingS = (nextImageTime - System.currentTimeMillis()) / 1000;
               if (timeRemainingS > 0 && !display_.acquisitionFinished()) {
                  nextFrameLabel_.setText("Next frame: " + NumberUtils.doubleToDisplayString(1 + timeRemainingS) + " s");
                  nextFrameTimer_.setDelay(100);
               } else {
                  nextFrameTimer_.setDelay(1000);
                  nextFrameLabel_.setText("");
               }

            }
         });
         nextFrameTimer_.start();
      }

      JPanel buttonPanel = new JPanel(new MigLayout("insets 0"));

      showFolderButton_ = new JButton();
      showFolderButton_.setBackground(new java.awt.Color(255, 255, 255));
      showFolderButton_.setIcon(
              new javax.swing.ImageIcon(
              getClass().getResource("/org/micromanager/icons/folder.png")));
      showFolderButton_.setToolTipText("Show containing folder");
      showFolderButton_.setFocusable(false);
      showFolderButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      showFolderButton_.setMaximumSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setMinimumSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setPreferredSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setVerticalTextPosition(
              javax.swing.SwingConstants.BOTTOM);
      showFolderButton_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showFolderButtonActionPerformed(evt);
         }
      });

      //button area
      abortButton_ = new JButton();
      abortButton_.setBackground(new java.awt.Color(255, 255, 255));
      abortButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/cancel.png"))); // NOI18N
      abortButton_.setToolTipText("Abort acquisition");
      abortButton_.setFocusable(false);
      abortButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      abortButton_.setMaximumSize(new java.awt.Dimension(25, 25));
      abortButton_.setMinimumSize(new java.awt.Dimension(25, 25));
      abortButton_.setPreferredSize(new java.awt.Dimension(25, 25));
      abortButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
      abortButton_.addActionListener(new ActionListener() {

         public void actionPerformed(ActionEvent e) {
            display_.abort();

         }
      });

      pauseButton_ = new JButton();
      pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
      pauseButton_.setToolTipText("Pause acquisition");
      pauseButton_.setFocusable(false);
      pauseButton_.setMargin(new java.awt.Insets(0, 0, 0, 0));
      pauseButton_.setMaximumSize(new java.awt.Dimension(25, 25));
      pauseButton_.setMinimumSize(new java.awt.Dimension(25, 25));
      pauseButton_.setPreferredSize(new java.awt.Dimension(25, 25));
      pauseButton_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
//            display_.pause();
//               if (acqEng_.isPaused()) {
//                  pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/resultset_next.png"))); // NOI18N
//               } else {
//                  pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
//               }
         }
      });
      
      gridButton_ = new JToggleButton("Grid");
      gridButton_.setToolTipText("Create, view, and edit rectangular grids of XY stage positions");
      gridButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            newGridButtonAction();
         }
      });

      if (acq_ instanceof ExploreAcquisition) {
         exploreButton_ = new JToggleButton("Explore");
         exploreButton_.setToolTipText("Activate explore mode");
         exploreButton_.setSelected(true);
         exploreButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               exploreButtonAction();
            }
         });
      }
      
//      zoomInButton_ = new JButton("Zoom in");
//      zoomInButton_.setToolTipText("Keyboard shortcut: \"+\" key");
//      zoomInButton_.addActionListener(new ActionListener() {
//         @Override
//         public void actionPerformed(ActionEvent e) {
//            display_.zoom(true);
//         }
//      });
//      
//      zoomOutButton_ = new JButton("Zoom out");
//      zoomOutButton_.setToolTipText("Keyboard shortcut: \"-\" key");
//      zoomOutButton_.addActionListener(new ActionListener() {
//         @Override
//         public void actionPerformed(ActionEvent e) {
//            display_.zoom(false);
//         }
//      });
      
//      gotoButton_ = new JToggleButton("Goto");
//      gotoButton_.addActionListener(null);
      
      
      surfaceButton_ = new JToggleButton("Surface");
      surfaceButton_.setToolTipText("Create, view, and edit 3D interpolated surfaces"); 
      surfaceButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
           newSurfaceButtonAction();
         }
      });
      
      buttonPanel.add(showFolderButton_);
      buttonPanel.add(abortButton_);
      buttonPanel.add(pauseButton_);
      if (acq_ instanceof ExploreAcquisition) {
         buttonPanel.add(exploreButton_);
      }
      buttonPanel.add(gridButton_);
      buttonPanel.add(surfaceButton_);
      
      JPanel newGridControlPanel = makeNewGridControlPanel();
      JPanel newSurfaceControlPanel = makeSurfaceControlPanel();
      JPanel defaultPanel = new JPanel(new MigLayout());
//      TODO: change
      defaultPanel.add(new JLabel("Default"));
      JPanel explorePanel = new JPanel(new MigLayout());
      explorePanel.add(new JLabel("Left click or click and drag to acquire tiles, right click to pan, +/- keys to zoom"));

      
      changingPanelLayout_ = new CardLayout();
      changingPanel_ = new JPanel(changingPanelLayout_);
      changingPanel_.add(newGridControlPanel, GRID_PANEL);
      changingPanel_.add(newSurfaceControlPanel, SURFACE_PANEL);
      changingPanel_.add(explorePanel, EXPLORE_PANEL);
      changingPanel_.add(defaultPanel,DEFAULT_PANEL);
      changingPanelLayout_.show(changingPanel_, EXPLORE_PANEL);
      
      controlsPanel.add(buttonPanel, "wrap");
      controlsPanel.add(changingPanel_);
      
//      JPanel test = new JPanel(new MigLayout());
//      test.add(new JLabel("Henry"));
//      controlsPanel.add(test, "wrap");
      
      
      this.setLayout(new BorderLayout());
      this.add(controlsPanel,BorderLayout.CENTER);
     
      
      // Propagate resizing through to our JPanel
//      this.addComponentListener(new ComponentAdapter() {
//         public void componentResized(ComponentEvent e) {
//            Dimension curSize = getSize();
//            controlsPanel.setPreferredSize(new Dimension(curSize.width, curSize.height));
//            invalidate();
//            validate();
//         }
//      });
   }

   private JPanel makeSurfaceControlPanel() {
      JPanel newSurfaceControlPanel = new JPanel(new MigLayout());
      
      JLabel currentSurfLabel = new JLabel("Surface: ");
      surfacesCombo_ = new JComboBox(GUI.createSurfaceAndRegionComboBoxModel(true, false));
      surfacesCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display_.setCurrentSurface(surfaceManager_.getSurface(surfacesCombo_.getSelectedIndex()));
         }
      });
      JToggleButton newSurfaceButton = new JToggleButton("New");
      newSurfaceButton.addActionListener(new ActionListener() {
         
         @Override
         public void actionPerformed(ActionEvent e) {            
            surfaceManager_.addNewSurface();
            surfacesCombo_.setSelectedIndex(surfacesCombo_.getItemCount() - 1);
            display_.setCurrentSurface(surfaceManager_.getSurface(surfacesCombo_.getSelectedIndex()));
            surfacesCombo_.repaint();          
         }
      });
         
      
      JPanel showPanel = new JPanel(new MigLayout());
      showPanel.add(new JLabel("Show: "));
      final JCheckBox footprintCheckbox = new JCheckBox("Footprint");
      final JCheckBox stagePosCheckbox = new JCheckBox("Stage positions");
      final JCheckBox surfaceCheckbox = new JCheckBox("Surface");
      footprintCheckbox.setSelected(true);
      stagePosCheckbox.setSelected(true);
      surfaceCheckbox.setSelected(true);
      showPanel.add(footprintCheckbox);
      showPanel.add(stagePosCheckbox);
      showPanel.add(surfaceCheckbox);
      ActionListener showActionListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display_.setSurfaceDisplaySettings(footprintCheckbox.isSelected(), stagePosCheckbox.isSelected(), surfaceCheckbox.isSelected());
         }
      };
      footprintCheckbox.addActionListener(showActionListener);
      stagePosCheckbox.addActionListener(showActionListener);
      surfaceCheckbox.addActionListener(showActionListener);
      
      newSurfaceControlPanel.add(newSurfaceButton);
      newSurfaceControlPanel.add(currentSurfLabel);
      newSurfaceControlPanel.add(surfacesCombo_, "w 100!");
      newSurfaceControlPanel.add(showPanel);
      return newSurfaceControlPanel;
   }
   
   private JPanel makeNewGridControlPanel() {
      JPanel newGridControlPanel = new JPanel(new MigLayout());
      
      regionsCombo_ = new JComboBox(GUI.createSurfaceAndRegionComboBoxModel(false, true));
      regionsCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display_.setCurrentRegion(regionManager_.getRegion(regionsCombo_.getSelectedIndex()));
            
            if (regionsCombo_.getSelectedItem() != null) {
               gridRowSpinner_.setValue(regionManager_.getRegion(regionsCombo_.getSelectedIndex()).numRows());
               gridColSpinner_.setValue(regionManager_.getRegion(regionsCombo_.getSelectedIndex()).numCols());
            }
         }  
      });
//      regionManager_.addListDataListener(gridsCombo);


      gridRowSpinner_ = new JSpinner();
      gridRowSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000, 1));
      gridColSpinner_ = new JSpinner();
      gridColSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000, 1));
      ChangeListener gridCL = new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) { 
            regionManager_.getRegion(regionsCombo_.getSelectedIndex()).updateParams((Integer) gridRowSpinner_.getValue(), 
                    (Integer) gridColSpinner_.getValue());
         }
      };
      gridRowSpinner_.addChangeListener(gridCL);
      gridColSpinner_.addChangeListener(gridCL);

      final JButton newGridButton = new JButton("New grid");
      newGridButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            regionManager_.addNewRegion(createNewGrid());
            regionsCombo_.setSelectedIndex(regionsCombo_.getItemCount() - 1);
            display_.setCurrentRegion(regionManager_.getRegion(regionsCombo_.getSelectedIndex()));
            regionsCombo_.repaint();
         }
      });

   
      newGridControlPanel.add(newGridButton);
      newGridControlPanel.add(new JLabel("Current grid: "));
      newGridControlPanel.add(regionsCombo_, "w 100!");
      newGridControlPanel.add(new JLabel("Rows: "));
      newGridControlPanel.add(gridRowSpinner_, "w 40!");
      newGridControlPanel.add(new JLabel("Cols: "));
      newGridControlPanel.add(gridColSpinner_, "w 40!");
      return newGridControlPanel;
   }
 
   private MultiPosGrid createNewGrid() {
      int imageWidth = display_.getImagePlus().getWidth();
      int imageHeight = display_.getImagePlus().getHeight();
      return new MultiPosGrid(regionManager_,(Integer) gridRowSpinner_.getValue(), (Integer) gridColSpinner_.getValue(), 
              display_.stageCoordFromImageCoords(imageWidth / 2, imageHeight / 2));
   }

   private void makeStatusLabels() {
      //text area
      zPosLabel_ = new JLabel("Z:") {

         @Override
         public void setText(String s) {
            DisplayPlusControls.this.invalidate();
            super.setText(s);
            DisplayPlusControls.this.validate();
         }
      };
      timeStampLabel_ = new JLabel("Elapsed time:") {

         @Override
         public void setText(String s) {
            DisplayPlusControls.this.invalidate();
            super.setText(s);
            DisplayPlusControls.this.validate();
         }
      };
      nextFrameLabel_ = new JLabel("Next frame: ") {

         @Override
         public void setText(String s) {
            DisplayPlusControls.this.invalidate();
            super.setText(s);
            DisplayPlusControls.this.validate();
         }
      };
      fpsField_ = new JTextField();
      fpsField_.setText("7");
      fpsField_.setToolTipText("Set the speed at which the acquisition is played back.");
      fpsField_.setPreferredSize(new Dimension(25, 18));
      fpsField_.addFocusListener(new java.awt.event.FocusAdapter() {

         public void focusLost(java.awt.event.FocusEvent evt) {
//            updateFPS();
//            TODO: this
         }
      });
      fpsField_.addKeyListener(new java.awt.event.KeyAdapter() {

         public void keyReleased(java.awt.event.KeyEvent evt) {
//            updateFPS();
         }
      });
      JLabel fpsLabel = new JLabel("Animation playback FPS: ");
   }

   public void toggleExploreMode() {
      exploreButton_.setSelected(!exploreButton_.isSelected());
      exploreButton_.getActionListeners()[0].actionPerformed(null);
   }

   private void showFolderButtonActionPerformed(java.awt.event.ActionEvent evt) {
      display_.showFolder();
   }

   @Override
   public void imagesOnDiskUpdate(boolean enabled) {
      showFolderButton_.setEnabled(enabled);
   }

   @Override
   public void acquiringImagesUpdate(boolean acquiring) {
      abortButton_.setEnabled(acquiring);
      pauseButton_.setEnabled(acquiring);
   }

   @Override
   public void newImageUpdate(JSONObject tags) {
      if (tags == null) {
         return;
      }
      updateLabels(tags);
   }

   @Override
   public void prepareForClose() {
      scrollerPanel_.prepareForClose();
      bus_.unregister(this);
      surfaceManager_.removeFromModelList( (SurfaceRegionComboBoxModel) surfacesCombo_.getModel());
      regionManager_.removeFromModelList( (SurfaceRegionComboBoxModel) regionsCombo_.getModel());      
   }

   private void updateLabels(JSONObject tags) {
      //Z position label
      String zPosition = "";
      try {
         zPosition = NumberUtils.doubleToDisplayString(MDUtils.getZPositionUm(tags));
      } catch (Exception e) {
         // Do nothing...
      }
//      zPosLabel_.setText("Z Position: " + zPosition + " um ");

      //time label
      try {
         int ms = (int) tags.getDouble("ElapsedTime-ms");
         int s = ms / 1000;
         int min = s / 60;
         int h = min / 60;

         String time = TWO_DECIMAL_FORMAT.format(h) + ":" + TWO_DECIMAL_FORMAT.format(min % 60)
                 + ":" + TWO_DECIMAL_FORMAT.format(s % 60) + "." + THREE_DECIMAL_FORMAT.format(ms % 1000);
//         timeStampLabel_.setText("Elapsed time: " + time + " ");
      } catch (JSONException ex) {
//            ReportingUtils.logError("MetaData did not contain ElapsedTime-ms field");
      }
   }

   @Override
   public void setImageInfoLabel(String text) {
      //Don't have one of these...
   }

}
