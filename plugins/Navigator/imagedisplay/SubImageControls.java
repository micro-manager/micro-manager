/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagedisplay;

import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.FixedAreaAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import gui.SettingsDialog;
import ij.gui.StackWindow;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
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
public class SubImageControls extends Panel {

   private final static int DEFAULT_FPS = 10;
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

   public SubImageControls(DisplayPlus disp, EventBus bus, Acquisition acq) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      display_ = disp;
      bus_.register(this);
      acq_ = acq;
      zStep_ = acq_.getZStep();
      initComponents();
   }

   private void updateZTopAndBottom() {
      //Update the text fields next to the sliders in response to adjustment
      double zBottom = zStep_ * zBottomScrollbar_.getValue() + zOrigin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopScrollbar_.getValue() + zOrigin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      //Update the acquisition 
      ((ExploreAcquisition) acq_).setZLimits(zTop, zBottom);
   }
   
   private void expandZLimitsIfNeeded(int topScrollbarIndex, int bottomScrollbarIndex) {
      //extent of 1 needs to be accounted for on top
      if (topScrollbarIndex >= zTopScrollbar_.getMaximum() - 1 || bottomScrollbarIndex >= zBottomScrollbar_.getMaximum() - 1) {         
         zTopScrollbar_.setMaximum(Math.max(topScrollbarIndex,bottomScrollbarIndex) + 2); 
         zBottomScrollbar_.setMaximum(Math.max(topScrollbarIndex,bottomScrollbarIndex) + 2); 
      }      
      if (bottomScrollbarIndex <= zBottomScrollbar_.getMinimum() || topScrollbarIndex <= zTopScrollbar_.getMinimum()) {
         zTopScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1); 
         zBottomScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1); 
      }     
   }
   
   private void zTopTextFieldAction() {
      //check if new position is outside bounds of current z range
      //and if so expand sliders as needed
      double val = Double.parseDouble(zTopTextField_.getText());
      int newSliderindex =  (int) Math.round((val  - zOrigin_) / zStep_);
      expandZLimitsIfNeeded(newSliderindex, zBottomScrollbar_.getValue());
      //now that scollbar expanded, set value
      zTopScrollbar_.setValue(newSliderindex);
      updateZTopAndBottom();
   }

   private void zBottomTextFieldAction() {
      //check if new position is outside bounds of current z range
      //and if so expand sliders as needed
      double val = Double.parseDouble(zBottomTextField_.getText());
      int newSliderindex =  (int) Math.round((val  - zOrigin_) / zStep_);
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(),newSliderindex);
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
         //TODO: what if no z device enabled?
         try {
            CMMCore core = MMStudio.getInstance().getCore();
            String z = core.getFocusDevice();
            zOrigin_ = core.getPosition(z);
            //Initialize z to current position with space to move one above or below           
            //value, extent, min, max
            //max value of scrollbar is max - extent
            zTopScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
            zBottomScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
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
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't create z sliders");
         }
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
            //duplicate new image event and edit as needed
            HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
            axisToPosition.put("channel", event.getPositionForAxis("channel"));
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
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              scrollerPanel_.getPreferredSize().height + (sliderPanel_ != null ? sliderPanel_.getPreferredSize().height : 0)));
      this.invalidate();
      this.validate();
      this.getParent().doLayout();
//      ((DisplayWindow) display_.getHyperImage().getWindow()).resizeCanvas();
   }

   public void prepareForClose() {
      scrollerPanel_.prepareForClose();
      bus_.unregister(this);
   }
}
