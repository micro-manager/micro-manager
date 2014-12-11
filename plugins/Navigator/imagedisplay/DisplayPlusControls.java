/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagedisplay;

import imagedisplay.DisplayPlus;
import acq.Acquisition;
import acq.ExploreAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import gui.SettingsDialog;
import ij.gui.StackWindow;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.imagedisplay.AxisScroller;
import org.micromanager.imagedisplay.DisplayWindow;
import org.micromanager.imagedisplay.IMMImagePlus;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.NewImageEvent;
import org.micromanager.imagedisplay.ScrollerPanel;
import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.MultiPosRegion;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceManager;

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
   private JButton zoomInButton_, zoomOutButton_;
   private JToggleButton exploreButton_, gotoButton_;
   private JScrollBar zTopSlider_, zBottomSlider_;
   private JTextField zTopTextField_, zBottomTextField_;
   private Acquisition acq_;
   private double zMin_, zMax_, zStep_;
   private int numZSteps_;
   private CardLayout changingPanelLayout_;
   private JPanel changingPanel_;
   private JSpinner gridRowSpinner_, gridColSpinner_;
   private RegionManager regionManager_;
   private SurfaceManager surfaceManager_;
   private JLabel pixelInfoLabel_, countdownLabel_, imageInfoLabel_;
   private JComboBox surfacesCombo_, regionsCombo_;
   
   public DisplayPlusControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      zStep_ = acq_.getZStep();
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

      makeScrollerPanel();
      controlsPanel.add(scrollerPanel_, "span, growx, wrap");

      if (acq_ instanceof ExploreAcquisition) {
         JPanel sliderPanel = new JPanel(new MigLayout("insets 0", "[][][grow]", ""));
         //get slider min and max
         //TODO: what if no z device enabled?
         try {
            CMMCore core = MMStudio.getInstance().getCore();
            String z = core.getFocusDevice();
            double zPos = core.getPosition(z);
            zMin_ = (int) core.getPropertyLowerLimit(z, "Position");
            zMax_ = (int) core.getPropertyUpperLimit(z, "Position");
            if (SettingsDialog.getDemoMode()) {
               zMin_ = 0;
               zMax_ = 399;
            }    
            //Always initialize so current position falls exactly on a step, 
            //so don't have to auto move z when launching explore
            int stepsAbove = (int) Math.floor((zPos - zMin_) / zStep_);
            int stepsBelow = (int) Math.floor((zMax_ - zPos) / zStep_);
            //change min and max to reflect stepped positions
            zMin_ = zPos - stepsAbove * zStep_;
            zMax_ = zPos + stepsBelow * zStep_;
            numZSteps_ = stepsBelow + stepsAbove + 1;
            zTopSlider_ = new JScrollBar(JScrollBar.HORIZONTAL,stepsAbove,1,0,numZSteps_);
            zBottomSlider_ = new JScrollBar(JScrollBar.HORIZONTAL,stepsAbove,1,0,numZSteps_);
            zTopTextField_ = new JTextField(zPos + "");
            zTopTextField_.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent ae) {
                  double val = Double.parseDouble(zTopTextField_.getText());
                  int sliderPos = (int) (val / ((double) Math.abs(zMax_ - zMin_)) * numZSteps_);
                  zTopSlider_.setValue(sliderPos);
                  updateZTopAndBottom();
               }
            });
            zBottomTextField_ = new JTextField(zPos + "");
            zBottomTextField_.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent ae) {
                  double val = Double.parseDouble(zBottomTextField_.getText());
                  int sliderPos = (int) (val / ((double) Math.abs(zMax_ - zMin_)) * numZSteps_);                  
                  zBottomSlider_.setValue(sliderPos);
                  updateZTopAndBottom();
               }
            });
            zTopSlider_.addAdjustmentListener(new AdjustmentListener() {
               @Override
               public void adjustmentValueChanged(AdjustmentEvent ae) {

                  if (zTopSlider_.getValue() > zBottomSlider_.getValue()) {
                     zBottomSlider_.setValue(zTopSlider_.getValue());
                  }
                  updateZTopAndBottom();
               }
            });

            zBottomSlider_.addAdjustmentListener(new AdjustmentListener() {
               @Override
               public void adjustmentValueChanged(AdjustmentEvent ae) {
                  if (zTopSlider_.getValue() > zBottomSlider_.getValue()) {
                     zTopSlider_.setValue(zBottomSlider_.getValue());
                  }                                              
                  updateZTopAndBottom();                  
               }
            });
            //initialize properly
            zTopTextField_.setText(((ExploreAcquisition) acq_).getZTop() + "");
            zBottomTextField_.setText(((ExploreAcquisition) acq_).getZBottom() + "");
            zTopTextField_.getActionListeners()[0].actionPerformed(null);
            zBottomTextField_.getActionListeners()[0].actionPerformed(null);


            sliderPanel.add(new JLabel("Z limits"), "span 1 2");
            sliderPanel.add(zTopTextField_, "w 50!");
            sliderPanel.add(zTopSlider_, "growx, wrap");
            sliderPanel.add(zBottomTextField_, "w 50!");
            sliderPanel.add(zBottomSlider_, "growx");
            controlsPanel.add(sliderPanel, "span, growx, align center, wrap");
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't create z sliders");
         }
      }


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
      this.addComponentListener(new ComponentAdapter() {
         public void componentResized(ComponentEvent e) {
            Dimension curSize = getSize();
            controlsPanel.setPreferredSize(new Dimension(curSize.width, curSize.height));
            invalidate();
            validate();
         }
      });
   }

   private JPanel makeSurfaceControlPanel() {
      JPanel newSurfaceControlPanel = new JPanel(new MigLayout());
      
      JLabel currentSurfLabel = new JLabel("Surface: ");
      surfacesCombo_ = new JComboBox(surfaceManager_.createSurfaceComboBoxModel());
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
      
      regionsCombo_ = new JComboBox(regionManager_.createGridComboBoxModel());
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

   private void makeScrollerPanel() {
      scrollerPanel_ = new ScrollerPanel(bus_, new String[]{"channel", "position", "time", "z"}, new Integer[]{1, 1, 1, 1}, DEFAULT_FPS) {
         //Override new image event to intercept these events and correct for negative slice indices 

         @Override
         public void onNewImageEvent(NewImageEvent event) {                   
            //duplicate new image event and edit as needed
            HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
            axisToPosition.put("channel", event.getPositionForAxis("channel"));
            axisToPosition.put("position", 0);
            axisToPosition.put("time", event.getPositionForAxis("time"));
            axisToPosition.put("z", event.getPositionForAxis("z"));
            if (acq_ instanceof ExploreAcquisition) {
               //intercept event and edit slice index
               int z = event.getPositionForAxis("z");           
               //make slice index >= 0 for viewer   
               z -= ((ExploreAcquisition) acq_).getLowestSliceIndex();
               // show/expand z scroll bar if needed
               if (((ExploreAcquisition) acq_).getHighestSliceIndex() - ((ExploreAcquisition) acq_).getLowestSliceIndex() + 1 > scrollerPanel_.getMaxPosition("z")) {
                  for (AxisScroller scroller : scrollers_) {
                     if (scroller.getAxis().equals("z") && scroller.getMaximum() == 1) {
                        scroller.setVisible(true);
                        add(scroller, "wrap 0px, align center, growx");
                        //resize controls to reflect newly shown scroller
                        bus_.post(new LayoutChangedEvent());
                     }
                  }
                  this.setMaxPosition("z", ((ExploreAcquisition) acq_).getHighestSliceIndex() - ((ExploreAcquisition) acq_).getLowestSliceIndex() + 1);
                  //tell the imageplus about new number of slices so everything works properly
                  ((IMMImagePlus) display_.getHyperImage()).setNSlicesUnverified(scrollerPanel_.getMaxPosition("z"));
               }
               axisToPosition.put("z", z);
            }
            //pass along the edited new image event
            super.onNewImageEvent(new NewImageEvent(axisToPosition));
         }
      };
   }
  
   private MultiPosRegion createNewGrid() {
      int imageWidth = display_.getImagePlus().getWidth();
      int imageHeight = display_.getImagePlus().getHeight();
      ZoomableVirtualStack stack = (ZoomableVirtualStack) display_.getImagePlus().getStack();
      Point center = stack.getAbsoluteFullResPixelCoordinate(imageWidth / 2, imageHeight / 2);
      
      return new MultiPosRegion(regionManager_,(Integer) gridRowSpinner_.getValue(), (Integer) gridColSpinner_.getValue(),center.x, center.y);
   }
   
   private void createGrid() {
//      try {
//         //get displacements of center of rectangle from center of stitched image
//         double rectCenterXDisp = this.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterX()
//                 - this.getImagePlus().getWidth() / 2;
//         double rectCenterYDisp = this.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterY()
//                 - this.getImagePlus().getHeight() / 2;

//         Point2D.Double stagePos = Util.stagePositionFromPixelPosition(rectCenterXDisp, rectCenterYDisp);
//
////         int xOverlap = SettingsDialog.getXOverlap(), yOverlap = SettingsDialog.getYOverlap();
//         int xOverlap = 0, yOverlap = 0;
//         Util.createGrid(stagePos.x, stagePos.y,
//                 (Integer) gridXSpinner_.getValue(), (Integer) gridYSpinner_.getValue(),
//                 xOverlap, yOverlap);
//
//      } catch (Exception e) {
//         ReportingUtils.showError("Couldnt create grid");
//      }
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

   private void updateZTopAndBottom() {
      double zBottom = zStep_ * zBottomSlider_.getValue() + zMin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopSlider_.getValue() + zMin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      ((ExploreAcquisition) acq_).setZLimits(zTop, zBottom);   
   }

   public void toggleExploreMode() {
      exploreButton_.setSelected(!exploreButton_.isSelected());
      exploreButton_.getActionListeners()[0].actionPerformed(null);
   }
   
   public ScrollerPanel getScrollerPanel() {
      return scrollerPanel_;
   }

   /**
    * Our ScrollerPanel is informing us that we need to display a different
    * image.
    */
   @Subscribe
   public void onSetImage(ScrollerPanel.SetImageEvent event) {
      int position = event.getPositionForAxis("position");
      display_.updatePosition(position); //TODO: get rid of this because no positions???
      // Positions for ImageJ are 1-indexed but positions from the event are 
      // 0-indexed.
      int channel = event.getPositionForAxis("channel") + 1;
      int frame = event.getPositionForAxis("time") + 1;
      int slice = event.getPositionForAxis("z") + 1;
      //Make sure hyperimage max dimensions are set properly so image actually shows when requested
      IMMImagePlus immi = (IMMImagePlus) display_.getHyperImage();
      // Ensure proper dimensions are set on the image.
      if (immi.getNFramesUnverified() < frame ) {
         immi.setNFramesUnverified(frame);
      }
      if (immi.getNSlicesUnverified() < slice ) {
         immi.setNSlicesUnverified(slice );
      }
      if (immi.getNChannelsUnverified() < channel ) {
         immi.setNChannelsUnverified(channel);
      }
      //for compisite images, make sure the window knows current number of slices so images display properly
      if (display_.getHyperImage() instanceof MMCompositeImage) {
         StackWindow win = (StackWindow) display_.getHyperImage().getWindow();
         try {
            JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "nSlices", ((MMCompositeImage) display_.getHyperImage()).getNSlicesUnverified());
            //also set z position since it doesn't automatically work due to null z scrollbat              
            JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "z", slice);
         } catch (NoSuchFieldException ex) {
           ReportingUtils.showError("Couldn't set number of slices in ImageJ stack window"); 
         }
      }

      display_.getHyperImage().setPosition(channel, slice, frame);
      display_.drawOverlay(true);
   }

   @Subscribe
   public void onLayoutChange(ScrollerPanel.LayoutChangedEvent event) {
      int width = ((DisplayWindow) this.getParent()).getWidth();
//      scrollerPanel_.setPreferredSize(new Dimension(width,scrollerPanel_.getPreferredSize().height));
//      this.setPreferredSize( new Dimension(width, CONTROLS_HEIGHT + event.getPreferredSize().height));
      invalidate();
      validate();
//      ((DisplayWindow) this.getParent()).pack();
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
