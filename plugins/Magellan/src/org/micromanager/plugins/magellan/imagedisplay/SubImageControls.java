///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.plugins.magellan.imagedisplay;

import org.micromanager.plugins.magellan.acq.Acquisition;
import org.micromanager.plugins.magellan.acq.ExploreAcquisition;
import org.micromanager.plugins.magellan.acq.FixedAreaAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.gui.StackWindow;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.plugins.magellan.misc.NumberUtils;

/**
 *
 * @author Henry
 */
public class SubImageControls extends Panel {

   private final static int DEFAULT_FPS = 7;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private EventBus bus_;
   private DisplayPlus display_;
   private ScrollerPanel scrollerPanel_;
   private JPanel sliderPanel_;
   private JScrollBar zTopScrollbar_, zBottomScrollbar_;
   private JTextField zTopTextField_, zBottomTextField_;
   private Acquisition acq_;
   private double zStep_, zOrigin_;
   private int displayHeight_ = -1;
   //thread safe fields for currently displaye dimage
   private volatile int sliceIndex_ = 0, frameIndex_ = 0, channelIndex_ = 0;

   public SubImageControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      zStep_ = acq != null ? acq_.getZStep() : 0;
      try {
         initComponents();
      } catch (Exception e) {
         Log.log("Problem initializing subimage controls");
         Log.log(e);
      }

   }

   /**
    * used for forcing scrollbars to show when opening dataset on disk
    */
   public void makeScrollersAppear(int numChannels, int numSlices, int numFrames) {
      for (AxisScroller s : scrollerPanel_.scrollers_) {
         if (numChannels > 1 && s.getAxis().equals("channel")) {
            s.setVisible(true);
            s.setMaximum(numChannels);
            scrollerPanel_.add(s, "wrap 0px, align center, growx");
         } else if (numFrames > 1 && s.getAxis().equals("time")) {
            s.setVisible(true);
            s.setMaximum(numFrames);
            scrollerPanel_.add(s, "wrap 0px, align center, growx");
         } else if (numSlices > 1 && s.getAxis().equals("z")) {
            s.setVisible(true);
            s.setMaximum(numSlices);
            scrollerPanel_.add(s, "wrap 0px, align center, growx");
         }
      }
      bus_.post(new ScrollerPanel.LayoutChangedEvent());
   }

   public void unlockAllScrollers() {
      scrollerPanel_.unlockAllScrollers();
   }

   public void superLockAllScroller() {
      scrollerPanel_.superlockAllScrollers();
   }

   public void setAnimateFPS(double fps) {
      scrollerPanel_.setFramesPerSecond(fps);
   }

   private void updateZTopAndBottom() {
      //Update the text fields next to the sliders in response to adjustment
      double zBottom = zStep_ * zBottomScrollbar_.getValue() + zOrigin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopScrollbar_.getValue() + zOrigin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      //Update the acquisition 
      ((ExploreAcquisition) acq_).setZLimits(zTop, zBottom);
      //update colored areas on z scrollbar
      //convert to 0 based index based on which slices have been explored
   }

   public void setZLimitSliderValues(int sliceIndex) {
       //first expand scrollbars as needed
       if(zTopScrollbar_.getMaximum() < sliceIndex + 1) {
           zTopScrollbar_.setMaximum(sliceIndex + 2);
       }
       if(zBottomScrollbar_.getMaximum() < sliceIndex + 1) {
           zBottomScrollbar_.setMaximum(sliceIndex + 2);
       }
       if(zTopScrollbar_.getMinimum() > sliceIndex - 1) {
           zTopScrollbar_.setMinimum(sliceIndex - 1);
       }
       if(zBottomScrollbar_.getMinimum() > sliceIndex - 1) {
           zBottomScrollbar_.setMinimum(sliceIndex - 1);
       } 
//       now set sliders to current position 
       zBottomScrollbar_.setValue(sliceIndex);
       zTopScrollbar_.setValue(sliceIndex); 
       this.repaint();

   }
   
   private void expandZLimitsIfNeeded(int topScrollbarIndex, int bottomScrollbarIndex) {
      //extent of 1 needs to be accounted for on top
      if (topScrollbarIndex >= zTopScrollbar_.getMaximum() - 1 || bottomScrollbarIndex >= zBottomScrollbar_.getMaximum() - 1) {
         zTopScrollbar_.setMaximum(Math.max(topScrollbarIndex, bottomScrollbarIndex) + 2);
         zBottomScrollbar_.setMaximum(Math.max(topScrollbarIndex, bottomScrollbarIndex) + 2);
      }
      if (bottomScrollbarIndex <= zBottomScrollbar_.getMinimum() || topScrollbarIndex <= zTopScrollbar_.getMinimum()) {
         zTopScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1);
         zBottomScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1);
      }
      this.repaint();
   }

   private void zTopTextFieldAction() {
         //check if new position is outside bounds of current z range
         //and if so expand sliders as needed
         double val = NumberUtils.parseDouble(zTopTextField_.getText());
         int newSliderindex = (int) Math.round((val - zOrigin_) / zStep_);
         expandZLimitsIfNeeded(newSliderindex, zBottomScrollbar_.getValue());
         //now that scollbar expanded, set value
         zTopScrollbar_.setValue(newSliderindex);
         updateZTopAndBottom();
   }

   private void zBottomTextFieldAction() {
         //check if new position is outside bounds of current z range
         //and if so expand sliders as needed
         double val = NumberUtils.parseDouble(zBottomTextField_.getText());
         int newSliderindex = (int) Math.round((val - zOrigin_) / zStep_);
         expandZLimitsIfNeeded(zTopScrollbar_.getValue(), newSliderindex);
         zBottomScrollbar_.setValue(newSliderindex);
         updateZTopAndBottom();

   }

   private void zTopSliderAdjustment() {
      //Top must be <= to bottom
      if (zTopScrollbar_.getValue() > zBottomScrollbar_.getValue()) {
         zBottomScrollbar_.setValue(zTopScrollbar_.getValue());
      }
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(), zBottomScrollbar_.getValue());
      updateZTopAndBottom();
   }

   private void zBottomSliderAdjustment() {
      //Top must be <= to bottom
      if (zTopScrollbar_.getValue() > zBottomScrollbar_.getValue()) {
         zTopScrollbar_.setValue(zBottomScrollbar_.getValue());
      }
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(), zBottomScrollbar_.getValue());
      updateZTopAndBottom();
   }

   private void initComponents() {
      final JPanel controlsPanel = new JPanel(new MigLayout("insets 0, fillx, align center", "", "[]0[]0[]"));

      makeScrollerPanel();
      controlsPanel.add(scrollerPanel_, "span, growx, wrap");

      if (acq_ instanceof ExploreAcquisition) {
         sliderPanel_ = new JPanel(new MigLayout("insets 0", "[][][grow]", ""));

         CMMCore core = Magellan.getCore();
         String z = core.getFocusDevice();
         try {
            zOrigin_ = core.getPosition(z);
         } catch (Exception ex) {
            Log.log("couldn't get z postition from core", true);
         }
         //Initialize z to current position with space to move one above or below           
         //value, extent, min, max
         //max value of scrollbar is max - extent
         try {
            zTopScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
            zBottomScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
            zTopScrollbar_.setUI(new ColorableScrollbarUI());
            zBottomScrollbar_.setUI(new ColorableScrollbarUI());
         } catch (Exception e) {
            Log.log("problem creating z limit scrollbars",true);
            Log.log(e);
         }
         zTopTextField_ = new JTextField(zOrigin_ + "");
         zTopTextField_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
               zTopTextFieldAction();
            }
         });
            zBottomTextField_ = new JTextField(zOrigin_ + "");
            zBottomTextField_.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent ae) {
                  zBottomTextFieldAction();
               }
            });
            zTopScrollbar_.addAdjustmentListener(new AdjustmentListener() {
               @Override
               public void adjustmentValueChanged(AdjustmentEvent ae) {
                  zTopSliderAdjustment();
               }
            });
            zBottomScrollbar_.addAdjustmentListener(new AdjustmentListener() {
               @Override
               public void adjustmentValueChanged(AdjustmentEvent ae) {
                  zBottomSliderAdjustment();
               }
            });
            //initialize properly
            zTopTextField_.setText(((ExploreAcquisition) acq_).getZTop() + "");
            zBottomTextField_.setText(((ExploreAcquisition) acq_).getZBottom() + "");
            zTopTextField_.getActionListeners()[0].actionPerformed(null);
            zBottomTextField_.getActionListeners()[0].actionPerformed(null);

            sliderPanel_.add(new JLabel("Z limits"), "span 1 2");
            if (JavaUtils.isMac()) {
               sliderPanel_.add(zTopTextField_, "w 80!");
               sliderPanel_.add(zTopScrollbar_, "growx, wrap");
               sliderPanel_.add(zBottomTextField_, "w 80!");
               sliderPanel_.add(zBottomScrollbar_, "growx");
            } else {
               sliderPanel_.add(zTopTextField_, "w 50!");
               sliderPanel_.add(zTopScrollbar_, "growx, wrap");
               sliderPanel_.add(zBottomTextField_, "w 50!");
               sliderPanel_.add(zBottomScrollbar_, "growx");

            }
            controlsPanel.add(sliderPanel_, "span, growx, align center, wrap");

      }

      this.setLayout(new BorderLayout());
      this.add(controlsPanel, BorderLayout.CENTER);

      // Propagate resizing through to our JPanel
      this.addComponentListener(new ComponentAdapter() {

         public void componentResized(ComponentEvent e) {
            Dimension curSize = getSize(); //size of subimage controls
            //expand window when new scrollbars shown for fixed acq
            if (display_.getAcquisition() instanceof FixedAreaAcquisition) {
               if (displayHeight_ == -1) {
                  displayHeight_ = curSize.height;
               } else if (curSize.height != displayHeight_) {
                  //don't expand window bigger that max viewable area on scren
                  int maxHeight = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
                  display_.getHyperImage().getWindow().setSize(new Dimension(display_.getHyperImage().getWindow().getWidth(),
                          Math.min(maxHeight, display_.getHyperImage().getWindow().getHeight() + (curSize.height - displayHeight_))));
                  displayHeight_ = curSize.height;
               }
            }
            SubImageControls.this.getParent().invalidate();
            SubImageControls.this.getParent().validate();
         }
      });
   }

   private void makeScrollerPanel() {
      scrollerPanel_ = new ScrollerPanel(bus_, new String[]{"channel", "time", "z"}, new Integer[]{1, 1, 1}, DEFAULT_FPS) {
         //Override new image event to intercept these events and correct for negative slice indices 

         @Override
         public void onNewImageEvent(NewImageEvent event) {
            // show/expand z scroll bar if needed     
            if (acq_.getNumSlices() > scrollerPanel_.getMaxPosition("z")) {
               for (AxisScroller scroller : scrollers_) {
                  if (scroller.getAxis().equals("z") && scroller.getMaximum() == 1) {
                     scroller.setVisible(true);
                     add(scroller, "wrap 0px, align center, growx");
                     //resize controls to reflect newly shown scroller
                     bus_.post(new ScrollerPanel.LayoutChangedEvent());
                  }
               }
               this.setMaxPosition("z", acq_.getNumSlices());
               //tell the imageplus about new number of slices so everything works properly
               ((IMMImagePlus) display_.getHyperImage()).setNSlicesUnverified(scrollerPanel_.getMaxPosition("z"));
            }

            super.onNewImageEvent(event);
         }
      };
   }

   /**
    * Our ScrollerPanel is informing us that we need to display a different
    * image.
    */
   @Subscribe
   public void onSetImage(ScrollerPanel.SetImageEvent event) {
      int channel = event.getPositionForAxis("channel") + 1;
      int frame = event.getPositionForAxis("time") + 1;
      int slice = event.getPositionForAxis("z") + 1;
      //Make sure hyperimage max dimensions are set properly so image actually shows when requested
      IMMImagePlus immi = (IMMImagePlus) display_.getHyperImage();
      // Ensure proper dimensions are set on the image.
      if (immi.getNFramesUnverified() < frame) {
         immi.setNFramesUnverified(frame);
      }
      if (immi.getNSlicesUnverified() < slice) {
         immi.setNSlicesUnverified(slice);
      }
      if (immi.getNChannelsUnverified() < channel) {
         immi.setNChannelsUnverified(channel);
      }
      //for compisite images, make sure the window knows current number of slices so images display properly
      if (display_.getHyperImage() instanceof MMCompositeImage) {
         StackWindow win = (StackWindow) display_.getHyperImage().getWindow();
         try {
            JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "nSlices", ((MMCompositeImage) display_.getHyperImage()).getNSlicesUnverified());
         } catch (NoSuchFieldException ex) {
            Log.log("Couldn't set number of slices in ImageJ stack window");
         }
      }
      //set the imageJ scrollbar positions here. We don't rely on exactly the same
      //hacky mechanism as MM, I think because dynamic changes required by the explore
      //window. Instead we use the differnet hacky mechanism seen here    
      StackWindow win = (StackWindow) display_.getHyperImage().getWindow();
      try {
         JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "t", frame);
         JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "z", slice);
         JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "c", channel);
      } catch (NoSuchFieldException e) {
         Log.log("Unexpected exception when trying to set image position");
      }

      synchronized (this) {
         channelIndex_ = channel - 1;
         frameIndex_ = frame - 1;
         sliceIndex_ = slice - 1;
         display_.getHyperImage().setPosition(channel, slice, frame);
      }

      display_.drawOverlay();
      if (acq_ instanceof ExploreAcquisition) {
         //convert slice index to explore scrollbar index       
         ((ColorableScrollbarUI) zTopScrollbar_.getUI()).setHighlightedIndices(sliceIndex_ + ((ExploreAcquisition) acq_).getMinSliceIndex(),
                 ((ExploreAcquisition) acq_).getMinSliceIndex(), ((ExploreAcquisition) acq_).getMaxSliceIndex());
         ((ColorableScrollbarUI) zBottomScrollbar_.getUI()).setHighlightedIndices(sliceIndex_ + ((ExploreAcquisition) acq_).getMinSliceIndex(),
                 ((ExploreAcquisition) acq_).getMinSliceIndex(), ((ExploreAcquisition) acq_).getMaxSliceIndex());
         this.repaint();
      }

   }

   public int getDisplayedSlice() {
      return sliceIndex_;
   }

   public int getDisplayedChannel() {
      return channelIndex_;
   }

   public int getDisplayedFrame() {
      return frameIndex_;
   }

   @Subscribe
   public void onLayoutChange(ScrollerPanel.LayoutChangedEvent event) {
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              scrollerPanel_.getPreferredSize().height + (sliderPanel_ != null ? sliderPanel_.getPreferredSize().height : 0)));
      this.invalidate();
      this.validate();
      this.getParent().doLayout();
      SwingUtilities.invokeLater(new Runnable() {

         @Override
         public void run() {
            ((DisplayWindow) display_.getHyperImage().getWindow()).fitExploreCanvasToWindow();
         }
      });
   }

   public void prepareForClose() {
      scrollerPanel_.prepareForClose();
      bus_.unregister(this);
   }
}
