/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import acq.Acquisition;
import acq.CustomAcqEngine;
import acq.ExploreAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import javax.swing.JButton;
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
import org.micromanager.imagedisplay.NewImageEvent;
import org.micromanager.imagedisplay.ScrollerPanel;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class DisplayPlusControls extends DisplayControls {

   private final static int DEFAULT_FPS = 10;
   private static final int SCROLLBAR_INCREMENTS = 10000;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private static final DecimalFormat THREE_DECIMAL_FORMAT = new DecimalFormat("0.000");
   private final static String GRID_PANEL = "Grid controls", INFO_PANEL = "Info panel",
           SURFACE_PANEL = "Create surface";
   
   
   private EventBus bus_;
   private DisplayPlus display_;
   private ScrollerPanel scrollerPanel_;
   private Timer nextFrameTimer_;
   private JButton pauseButton_, abortButton_, showFolderButton_;
   private JTextField fpsField_;
   private JLabel zPosLabel_, timeStampLabel_, nextFrameLabel_, posNameLabel_;
   private JButton newGridButton_, createSurfaceButton_, zoomInButton_, zoomOutButton_;
   private JToggleButton exploreButton_, gotoButton_;
   private JScrollBar zTopSlider_, zBottomSlider_;
   private JTextField zTopTextField_, zBottomTextField_;
   private Acquisition acq_;
   private double zMin_, zMax_;
   private CardLayout changingPanelLayout_;
   private JPanel changingPanel_;
   private JSpinner gridRowSpinner_, gridColSpinner_, gridOverlapXSpinner_, gridOverlapYSpinner_;
   
   
   private JLabel pixelInfoLabel_, countdownLabel_, imageInfoLabel_;

   public DisplayPlusControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      initComponents();
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
//          zMax_ = (int) core.getPropertyUpperLimit(z, "Position");
            //TODO: remove this, which is only for testing
            zMax_ = 50;
            /////////
            int sliderPos = (int) (zPos / ((double) Math.abs(zMax_ - zMin_)) * SCROLLBAR_INCREMENTS);
            zTopSlider_ = new JScrollBar(JScrollBar.HORIZONTAL,sliderPos,10,0,SCROLLBAR_INCREMENTS);
            zBottomSlider_ = new JScrollBar(JScrollBar.HORIZONTAL,sliderPos,10,0,SCROLLBAR_INCREMENTS);
            zTopTextField_ = new JTextField(zPos + "");
            zTopTextField_.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent ae) {
                  double val = Double.parseDouble(zTopTextField_.getText());
                  int sliderPos = (int) (val / ((double) Math.abs(zMax_ - zMin_)) * SCROLLBAR_INCREMENTS);
                  zTopSlider_.setValue(sliderPos);
                  updateZTopAndBottom();
               }
            });
            zBottomTextField_ = new JTextField(zPos + "");
            zBottomTextField_.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent ae) {
                  double val = Double.parseDouble(zBottomTextField_.getText());
                  int sliderPos = (int) (val / ((double) Math.abs(zMax_ - zMin_)) * SCROLLBAR_INCREMENTS);                  
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
            sliderPanel.add(new JLabel("Z limits"), "span 1 2");
            sliderPanel.add(zTopTextField_, "w 50!");
            sliderPanel.add(zTopSlider_, "growx, wrap");
            sliderPanel.add(zBottomTextField_, "w 50!");
            sliderPanel.add(zBottomSlider_, "growx");
            controlsPanel.add(sliderPanel, "span, growx, align center, wrap");
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't create z sliders");
         }
      } else {
         JPanel labelsPanel = new JPanel(new MigLayout("insets 0"));
         pixelInfoLabel_ = new JLabel("                                         ");
         pixelInfoLabel_.setMinimumSize(new Dimension(150, 10));
         pixelInfoLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
         labelsPanel.add(pixelInfoLabel_);

         imageInfoLabel_ = new JLabel("                                         ");
         imageInfoLabel_.setMinimumSize(new Dimension(150, 10));
         imageInfoLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
         labelsPanel.add(imageInfoLabel_);

         countdownLabel_ = new JLabel("                                         ");
         countdownLabel_.setMinimumSize(new Dimension(150, 10));
         countdownLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
         labelsPanel.add(countdownLabel_);

         controlsPanel.add(labelsPanel, "span, growx, align center, wrap");
         //      nextFrameTimer_ = new Timer(1000, new ActionListener() {
//
//         @Override
//         public void actionPerformed(ActionEvent e) {
//            long nextImageTime = 0;
//            try {
//               nextImageTime = display_.getNextWakeTime();
//            } catch (NullPointerException ex) {
//               nextFrameTimer_.stop();
//            }
//            if (!display_.acquisitionIsRunning()) {
//               nextFrameTimer_.stop();
//            }
//            double timeRemainingS = (nextImageTime - System.nanoTime() / 1000000) / 1000;
//            if (timeRemainingS > 0 && display_.acquisitionIsRunning()) {
//               nextFrameLabel_.setText("Next frame: " + NumberUtils.doubleToDisplayString(1 + timeRemainingS) + " s");
//               nextFrameTimer_.setDelay(100);
//            } else {
//               nextFrameTimer_.setDelay(1000);
//               nextFrameLabel_.setText("");
//            }
//
//         }
//      });
//      nextFrameTimer_.start();
      }


      JPanel buttonPanel = new JPanel(new MigLayout());

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
      
      newGridButton_ = new JButton("Create grid");
      newGridButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            //show grid making controls           
            changingPanelLayout_.show(changingPanel_, GRID_PANEL);
            display_.activateNewGridMode(true);
            gridSizeChanged();
         }
      });

      if (acq_ instanceof ExploreAcquisition) {
         exploreButton_ = new JToggleButton("Explore");
         exploreButton_.setSelected(true);
         exploreButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               display_.activateExploreMode(exploreButton_.isSelected());
            }
         });
      }
      
      zoomInButton_ = new JButton("Zoom in");
      zoomInButton_.setToolTipText("Keyboard shortcut: \"+\" key");
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display_.zoom(true);
         }
      });
      
      zoomOutButton_ = new JButton("Zoom out");
      zoomOutButton_.setToolTipText("Keyboard shortcut: \"-\" key");
      zoomOutButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display_.zoom(false);
         }
      });
      
//      gotoButton_ = new JToggleButton("Goto");
//      gotoButton_.addActionListener(null);
      
      
      createSurfaceButton_ = new JButton("Mark surface");
      createSurfaceButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            //show surface creation controls           
            changingPanelLayout_.show(changingPanel_, SURFACE_PANEL);
            display_.activateNewSurfaceMode(true);
         }
      });


      buttonPanel.add(showFolderButton_);
      buttonPanel.add(abortButton_);
      buttonPanel.add(pauseButton_);
      buttonPanel.add(newGridButton_);
      buttonPanel.add(exploreButton_);
      buttonPanel.add(zoomInButton_);
      buttonPanel.add(zoomOutButton_);
      buttonPanel.add(createSurfaceButton_);
     
      
      
      JPanel newGridControlPanel = makeNewGridControlPanel();
      JPanel newSurfaceControlPanel = makeSurfaceControlPanel();
      
      JPanel statusPanel = new JPanel(new MigLayout());
      statusPanel.add(new JLabel("Statuzzzzzzzzz"));
      changingPanelLayout_ = new CardLayout();
      changingPanel_ = new JPanel(changingPanelLayout_);
      changingPanel_.add(newGridControlPanel, GRID_PANEL);
      changingPanel_.add(newSurfaceControlPanel, SURFACE_PANEL);
      changingPanel_.add(statusPanel, INFO_PANEL);
      changingPanelLayout_.show(changingPanel_, INFO_PANEL);
      
      controlsPanel.add(buttonPanel, "wrap");
      controlsPanel.add(changingPanel_);
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
       
       JToggleButton markPointsButton = new JToggleButton("nothing yet");
       markPointsButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            
         }
      });
       
       newSurfaceControlPanel.add(markPointsButton);
       return newSurfaceControlPanel;
   }
   
   private JPanel makeNewGridControlPanel() {
      JPanel newGridControlPanel = new JPanel(new MigLayout());

      gridRowSpinner_ = new JSpinner();
      gridRowSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000, 1));
      gridColSpinner_ = new JSpinner();
      gridColSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000, 1));
      ChangeListener gridCL = new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            gridSizeChanged();
         }
      };
      gridRowSpinner_.addChangeListener(gridCL);
      gridColSpinner_.addChangeListener(gridCL);

      final JButton createGridButton = new JButton("Create");
      createGridButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            display_.createGrid();
         }
      });

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            changingPanelLayout_.show(changingPanel_, INFO_PANEL);
            display_.activateNewGridMode(false);
         }
      });

      gridOverlapXSpinner_ = new JSpinner();
      gridOverlapXSpinner_.setModel(new SpinnerNumberModel(0, 0, 1000, 1));
      gridOverlapYSpinner_ = new JSpinner();
      gridOverlapYSpinner_.setModel(new SpinnerNumberModel(0, 0, 1000, 1));
      gridOverlapXSpinner_.addChangeListener(gridCL);
      gridOverlapYSpinner_.addChangeListener(gridCL);

      newGridControlPanel.add(cancelButton);
      newGridControlPanel.add(new JLabel("Rows: "));
      newGridControlPanel.add(gridRowSpinner_);
      newGridControlPanel.add(new JLabel("Cols: "));
      newGridControlPanel.add(gridColSpinner_);
      newGridControlPanel.add(new JLabel("Pixel overlap x: "));
      newGridControlPanel.add(gridOverlapXSpinner_);
      newGridControlPanel.add(new JLabel(" y: "));
      newGridControlPanel.add(gridOverlapYSpinner_);
      newGridControlPanel.add(createGridButton);
      return newGridControlPanel;
   }

   private void makeScrollerPanel() {
      scrollerPanel_ = new ScrollerPanel(bus_, new String[]{"channel", "position", "time", "z"}, new Integer[]{1, 1, 1, 1}, DEFAULT_FPS) {
         //Override new image event to intercept these events and correct for negative slice indices 
         @Override
         public void onNewImageEvent(NewImageEvent event) {
            if (acq_ instanceof ExploreAcquisition) {
               //intercept event and edit slice index
               int channel = event.getPositionForAxis("channel");
               int z = event.getPositionForAxis("z");
               //make slice index >= 0 for viewer   
               z -= ((ExploreAcquisition) acq_).getLowestSliceIndex();
               // show/expand z scroll bar if needed
               if (((ExploreAcquisition) acq_).getHighestSliceIndex() - ((ExploreAcquisition) acq_).getLowestSliceIndex() + 1 
                       > scrollerPanel_.getMaxPosition("z")) {
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
                  HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
                  axisToPosition.put("channel", channel);
                  axisToPosition.put("z", z);
               }
               super.onNewImageEvent(event);
            }
         }
      };
   }

   private void makeStatusLabels() {
       

      //text area
//      zPosLabel_ = new JLabel("Z:") {
//
//         @Override
//         public void setText(String s) {
//            DisplayPlusControls.this.invalidate();
//            super.setText(s);
//            DisplayPlusControls.this.validate();
//         }
//      };
//      timeStampLabel_ = new JLabel("Elapsed time:") {
//
//         @Override
//         public void setText(String s) {
//            DisplayPlusControls.this.invalidate();
//            super.setText(s);
//            DisplayPlusControls.this.validate();
//         }
//      };
//      nextFrameLabel_ = new JLabel("Next frame: ") {
//
//         @Override
//         public void setText(String s) {
//            DisplayPlusControls.this.invalidate();
//            super.setText(s);
//            DisplayPlusControls.this.validate();
//         }
//      };
//      fpsField_ = new JTextField();
//      fpsField_.setText("7");
//      fpsField_.setToolTipText("Set the speed at which the acquisition is played back.");
//      fpsField_.setPreferredSize(new Dimension(25, 18));
//      fpsField_.addFocusListener(new java.awt.event.FocusAdapter() {
//
//         public void focusLost(java.awt.event.FocusEvent evt) {
//            updateFPS();
//         }
//      });
//      fpsField_.addKeyListener(new java.awt.event.KeyAdapter() {
//
//         public void keyReleased(java.awt.event.KeyEvent evt) {
//            updateFPS();
//         }
//      });
//      JLabel fpsLabel = new JLabel("Animation playback FPS: ");
   }
   
   private void gridSizeChanged() {
      int numRows = (Integer) gridRowSpinner_.getValue();
      int numCols = (Integer) gridColSpinner_.getValue();
      int xOverlap = (Integer) gridOverlapXSpinner_.getValue();
      int yOverlap = (Integer) gridOverlapYSpinner_.getValue();
      display_.gridSizeChanged(numRows, numCols, xOverlap, yOverlap);
   }

   private void updateZTopAndBottom() {
      double zBottom = (zBottomSlider_.getValue() / (double) SCROLLBAR_INCREMENTS)
              * Math.abs(zMax_ - zMin_) + zMin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = (zTopSlider_.getValue() / (double) SCROLLBAR_INCREMENTS)
              * Math.abs(zMax_ - zMin_) + zMin_;
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
      display_.updatePosition(position);
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
