/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagedisplay;

import acq.Acquisition;
import acq.ExploreAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import gui.SettingsDialog;
import ij.gui.StackWindow;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import mmcloneclasses.imagedisplay.*;
import mmcloneclasses.internalinterfaces.DisplayControls;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class SubImageControls extends DisplayControls {

   private final static int DEFAULT_FPS = 10;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private EventBus bus_;
   private DisplayPlus display_;
   private ScrollerPanel scrollerPanel_;
   private JScrollBar zTopSlider_, zBottomSlider_;
   private JTextField zTopTextField_, zBottomTextField_;
   private Acquisition acq_;
   private double zMin_, zMax_, zStep_;
   private int numZSteps_;

   public SubImageControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      zStep_ = acq_.getZStep();
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
            zTopSlider_ = new JScrollBar(JScrollBar.HORIZONTAL, stepsAbove, 1, 0, numZSteps_);
            zBottomSlider_ = new JScrollBar(JScrollBar.HORIZONTAL, stepsAbove, 1, 0, numZSteps_);
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
            if (JavaUtils.isMac()) {
               sliderPanel.add(zTopTextField_, "w 80!");
               sliderPanel.add(zTopSlider_, "growx, wrap");
               sliderPanel.add(zBottomTextField_, "w 80!");
               sliderPanel.add(zBottomSlider_, "growx");
            } else {
               sliderPanel.add(zTopTextField_, "w 50!");
               sliderPanel.add(zTopSlider_, "growx, wrap");
               sliderPanel.add(zBottomTextField_, "w 50!");
               sliderPanel.add(zBottomSlider_, "growx");

            }
            controlsPanel.add(sliderPanel, "span, growx, align center, wrap");
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't create z sliders");
         }
      }

      this.setLayout(new BorderLayout());
      this.add(controlsPanel, BorderLayout.CENTER);



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
                        bus_.post(new ScrollerPanel.LayoutChangedEvent());
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

   private void updateZTopAndBottom() {
      double zBottom = zStep_ * zBottomSlider_.getValue() + zMin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopSlider_.getValue() + zMin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      ((ExploreAcquisition) acq_).setZLimits(zTop, zBottom);
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

   @Override
   public void prepareForClose() {
      scrollerPanel_.prepareForClose();
      bus_.unregister(this);
   }

   //Don't need these for now
   @Override
   public void imagesOnDiskUpdate(boolean onDisk) {
   }

   @Override
   public void acquiringImagesUpdate(boolean acquiring) {
   }

   @Override
   public void setImageInfoLabel(String text) {
   }

   @Override
   public void newImageUpdate(JSONObject tags) {
   }
}
