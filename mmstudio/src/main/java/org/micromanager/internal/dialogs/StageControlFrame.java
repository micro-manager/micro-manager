/**
 * StageControlFrame.java
 *
 * Created on Aug 19, 2010, 10:04:49 PM
 * Nico Stuurman, copyright UCSF, 2010
 *
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.internal.dialogs;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JFormattedTextField;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author nico
 * @author Jon
 */
public final class StageControlFrame extends MMFrame {
   private final Studio studio_;
   private final CMMCore core_;
   
   private final MutablePropertyMapView settings_;

   private static final int MAX_NUM_Z_PANELS = 5;

   private static final int FRAME_X_DEFAULT_POS = 100;
   private static final int FRAME_Y_DEFAULT_POS = 100;
   
   private final ExecutorService stageMotionExecutor_;

   private static final String[] XY_MOVEMENTS = new String[] {
      "SMALLMOVEMENT", "MEDIUMMOVEMENT", "LARGEMOVEMENT"
   };
   private static final String SMALLMOVEMENTZ = "SMALLMOVEMENTZ";
   private static final String MEDIUMMOVEMENTZ = "MEDIUMMOVEMENTZ";
   private static final String CURRENTZDRIVE = "CURRENTZDRIVE";
   private static final String REFRESH = "REFRESH";
   private static final String NRZPANELS = "NRZPANELS";

   private static StageControlFrame staticFrame_;

   private JPanel errorPanel_;
   private JPanel xyPanel_;
   private JLabel xyPositionLabel_;
   private JPanel zPanel_[] = new JPanel[MAX_NUM_Z_PANELS];
   private JComboBox<String>[] zDriveSelect_ = new JComboBox[MAX_NUM_Z_PANELS];
   private JLabel zPositionLabel_[] = new JLabel[MAX_NUM_Z_PANELS];
   private JPanel settingsPanel_;
   private JCheckBox enableRefreshCB_;
   private Timer timer_ = null;
   // Ordered small, medium, large.
   private JFormattedTextField[] xyStepTexts_ = new JFormattedTextField[] {
      new JFormattedTextField(NumberFormat.getNumberInstance()),
      new JFormattedTextField(NumberFormat.getNumberInstance()),
      new JFormattedTextField(NumberFormat.getNumberInstance())
   };
   private JFormattedTextField[] zStepTextsSmall_ = new JFormattedTextField[MAX_NUM_Z_PANELS];
   private JFormattedTextField[] zStepTextsMedium_ = new JFormattedTextField[MAX_NUM_Z_PANELS];
   private JButton[] plusButtons_ = new JButton[MAX_NUM_Z_PANELS];
   private JButton[] minusButtons_ = new JButton[MAX_NUM_Z_PANELS];

   public static void showStageControl() {
      Studio studio = org.micromanager.internal.MMStudio.getInstance();
      if (staticFrame_ == null) {
         staticFrame_ = new StageControlFrame(studio);
         studio.events().registerForEvents(staticFrame_);
      }
      staticFrame_.initialize();
      staticFrame_.setVisible(true);
   }


   /**
    * Creates new form StageControlFrame
    * @param gui the MM api
    */
   public StageControlFrame(Studio gui) {
      studio_ = gui;
      core_ = studio_.getCMMCore();
      settings_ = studio_.profile().getSettings(StageControlFrame.class);
      stageMotionExecutor_ = Executors.newFixedThreadPool(2);

      initComponents();

      super.loadAndRestorePosition(FRAME_X_DEFAULT_POS, FRAME_Y_DEFAULT_POS);
   }

   /**
    * Initialized GUI components based on current hardware configuration
    * Can be called at any time to adjust display (for instance after hardware
    * configuration change). Also called when user requests window to be shown.
    */
   public final void initialize() {
      stopTimer();
      double[] xyStepSizes = new double[] {1.0, 10.0, 100.0};
      double pixelSize = core_.getPixelSizeUm();
      long nrPixelsX = core_.getImageWidth();
      if (pixelSize != 0) {
         xyStepSizes[0] = pixelSize;
         xyStepSizes[1] = pixelSize * nrPixelsX * 0.1;
         xyStepSizes[2] = pixelSize * nrPixelsX;
      }
      // Read XY stepsizes from profile
      for (int i = 0; i < 3; ++i) {
         xyStepSizes[i] = settings_.getDouble(XY_MOVEMENTS[i], xyStepSizes[i]);
         xyStepTexts_[i].setText(
                 NumberUtils.doubleToDisplayString(xyStepSizes[i]) );
      }

      StrVector zDrives = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
      StrVector xyDrives = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
      final boolean haveXY = !xyDrives.isEmpty();
      final boolean haveZ = !zDrives.isEmpty();
      final int nrZDrives = (int) zDrives.size();

      // set panels visible depending on what drives are actually present
      xyPanel_.setVisible(haveXY);
      zPanel_[0].setVisible(haveZ);
      final String sysConfigFile = org.micromanager.internal.MMStudio.getInstance().getSysConfigFile();  // TODO add method to API
      final String key = NRZPANELS + sysConfigFile;
      int nrZPanels = settings_.getInteger(key, nrZDrives);
      // mailing list report 12/31/2019 encounters nrZPanels == 0, workaround:
      if (nrZPanels <= 0 && nrZDrives > 0) {
         nrZPanels = 1;
      }
      settings_.putInteger(key, nrZPanels);
      for (int idx=1; idx<MAX_NUM_Z_PANELS; ++idx) {
         zPanel_[idx].setVisible(idx < nrZPanels);
      }
      errorPanel_.setVisible(!haveXY && !haveZ);
      settingsPanel_.setVisible(haveXY || haveZ);
      if (xyPanel_.isVisible()) {
         // put the polling checkbox in XY panel if possible, below 1st Z panel if not
         xyPanel_.add(settingsPanel_, "pos 140 20");
      } else {
         add(settingsPanel_, "cell 1 2, center");
      }
      
      // handle Z panels
      if (haveZ) {
         for (int idx = 0; idx < MAX_NUM_Z_PANELS; ++idx) {
            zDriveSelect_[idx].setVisible(nrZDrives > 1);

            // remove item listeners temporarily
            ItemListener[] zDriveItemListeners =
                  zDriveSelect_[idx].getItemListeners();
            for (ItemListener l : zDriveItemListeners) {
               zDriveSelect_[idx].removeItemListener(l);
            }

            // repopulate combo box
            if (zDriveSelect_[idx].getItemCount() != 0) {
               zDriveSelect_[idx].removeAllItems();
            }
            for (int i = 0; i < nrZDrives; ++i) {
               String drive = zDrives.get(i);
               zDriveSelect_[idx].addItem(drive);
            }

            // restore item listeners
            for (ItemListener l : zDriveItemListeners) {
               zDriveSelect_[idx].addItemListener(l);
            }

            // select correct drive, which will grab the correct step sizes via listeners
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) zDriveSelect_[idx].getModel();
            // first attempt to find drive we previously used, then use the drives in ored
            int cbIndex = model.getIndexOf(settings_.getString(CURRENTZDRIVE + idx, ""));  // returns -1 if not found
            if (cbIndex < 0) {
               cbIndex = model.getIndexOf((idx < nrZDrives) ? zDrives.get(idx) : "");  // returns -1 if not found
            }
            if (cbIndex < 0) {
               cbIndex = 0;
            }
            zDriveSelect_[idx].setSelectedIndex(-1);  // needed to make sure setSelectedIndex fires an ItemListener for index 0
            zDriveSelect_[idx].setSelectedIndex(cbIndex);
            
            plusButtons_[idx].setVisible(false);
            minusButtons_[idx].setVisible(false);
         }
         
         // make plus/minus buttons on last ZPanel visible
         plusButtons_[nrZPanels-1].setVisible(true);
         minusButtons_[nrZPanels-1].setVisible(true);
         
         // make sure not to underflow/overflow with plus/minus buttons
         minusButtons_[0].setEnabled(false);
         plusButtons_[MAX_NUM_Z_PANELS-1].setEnabled(false);
      }
      
      updateStagePositions();  // make sure that positions are correct
      refreshTimer(); // start polling if enabled
      pack();  // re-layout the frame depending on what is visible now
   }

   /**
    * Called during constructor and never again.  Creates GUI components and adds
    *    them to JPanel but they may be turned visible/invisible during operation.
    */
   private void initComponents() {
      setTitle("Stage Control");
      setLocationByPlatform(true);
      setResizable(false);
      setLayout(new MigLayout("fill, insets 5, gap 2"));

      xyPanel_ = createXYPanel();
      add(xyPanel_, "hidemode 3");
      
      settingsPanel_ = createSettingsPanel();
      
      // create the Z panels
      // Vertically align Z panel with XY panel. createZPanel() also makes
      // several assumptions about the layout of the XY panel so that its
      // components are nicely vertically aligned.
      for (int idx=0; idx<MAX_NUM_Z_PANELS; ++idx) {
         zPanel_[idx] = createZPanel(idx);
         add(zPanel_[idx], "aligny top, gapleft 20, hidemode 3");
      }

      errorPanel_ = createErrorPanel();
      add(errorPanel_, "grow, hidemode 3");
      
      pack();
   }

   private JPanel createXYPanel() {
      final JFrame theWindow = this;
      JPanel result = new JPanel(new MigLayout("insets 0, gap 0"));
      result.add(new JLabel("XY Stage", JLabel.CENTER),
            "span, alignx center, wrap");

      // Create a layout of buttons like this:
      //    ^
      //    ^
      //    ^
      // <<< >>>
      //    v
      //    v
      //    v
      // Doing this in a reasonably non-redundant, compact way is somewhat
      // tricky as each button is subtly different: they have different icons
      // and move the stage in different directions by different amounts when
      // pressed. We'll define them by index number 0-12: first all the "up"
      // buttons, then all "left" buttons, then all "right" buttons, then all
      // "down" buttons, so i / 4 indicates direction and i % 3 indicates step
      // size (with a minor wrinkle noted later).

      // Utility arrays for icon filenames.
      // Presumably for "single", "double", and "triple".
      String[] stepSizes = new String[] {"s", "d", "t"};
      // Up, left, right, down.
      String[] directions = new String[] {"u", "l", "r", "d"};
      for (int i = 0; i < 12; ++i) {
         // "Right" and "Down" buttons are ordered differently; in any case,
         // the largest-step button is furthest from the center.
         final int stepIndex = (i <= 5) ? (2 - (i % 3)) : (i % 3);
         String path = "/org/micromanager/icons/stagecontrol/arrowhead-" +
            stepSizes[stepIndex] + directions[i / 3];
         final JButton button = new JButton(IconLoader.getIcon(path + ".png"));
         // This copy can be referred to in the action listener.
         final int index = i;
         button.setBorder(null);
         button.setBorderPainted(false);
         button.setContentAreaFilled(false);
         button.setPressedIcon(IconLoader.getIcon(path + "p.png"));
         button.addActionListener((ActionEvent e) -> {
            int dx = 0;
            int dy = 0;
            switch (index / 3) {
               case 0:
                  dy = -1;
                  break;
               case 1:
                  dx = -1;
                  break;
               case 2:
                  dx = 1;
                  break;
               case 3:
                  dy = 1;
                  break;
            }
            try {
               double increment =
                       NumberUtils.displayStringToDouble(xyStepTexts_[stepIndex].getText());
               setRelativeXYStagePosition(dx * increment, dy * increment);
            }
            catch (ParseException ex) {
               JOptionPane.showMessageDialog(theWindow, "XY Step size is not a number");
            }
         });
         // Add the button to the panel.
         String constraint = "";
         if (i < 3 || i > 8) {
            // Up or down button.
            constraint = "span, alignx center, wrap";
         }
         else if (i == 3) {
            // First horizontal button
            constraint = "split, span";
         }
         else if (i == 6) {
            // Fourth horizontal button (start of the "right" buttons); add
            // a gap to the left.
            constraint = "gapleft 30";
         }
         else if (i == 8) {
            // Last horizontal button.
            constraint = "wrap";
         }
         result.add(button, constraint);
      }
      // Add the XY position label in the upper-left.
      xyPositionLabel_ = new JLabel("", JLabel.LEFT);
      result.add(xyPositionLabel_,
            "pos 5 20, width 120!, alignx left");
      
      // Gap between the chevrons and the step size controls.
      result.add(new JLabel(), "height 20!, wrap");

      // Now the controls for setting the step size.
      String[] labels = new String[] {"1 pixel", "0.1 field", "1 field"};
      for (int i = 0; i < 3; ++i) {
         JLabel indicator = new JLabel(IconLoader.getIcon(
                  "/org/micromanager/icons/stagecontrol/arrowhead-" +
                  stepSizes[i] + "r.png"));
         // HACK: make it smaller so the gap between rows is smaller.
         result.add(indicator, "height 20!, split, span");
         // This copy can be referred to in the action listener.
         final int index = i;

         // See above HACK note.
         result.add(xyStepTexts_[i], "height 20!, width 80");

         result.add(new JLabel("\u00b5m"));

         JButton presetButton = new JButton(labels[i]);
         presetButton.setFont(new Font("Arial", Font.PLAIN, 10));
         presetButton.addActionListener((ActionEvent e) -> {
            double pixelSize = core_.getPixelSizeUm();
            double viewSize = core_.getImageWidth() * pixelSize;
            double[] sizes = new double[] {pixelSize, viewSize / 10,
               viewSize};
            double stepSize = sizes[index];
            xyStepTexts_[index].setText(
                    NumberUtils.doubleToDisplayString(stepSize));
         });
         result.add(presetButton, "width 80!, height 20!, wrap");
      } // End creating set-step-size text fields/buttons.
      
      return result;
   }

   /**
    * NOTE: this method makes assumptions about the layout of the XY panel.
    * In particular, it is assumed that each chevron button is 30px tall,
    * that the step size controls are 20px tall, and that there is a 20px gap
    * between the chevrons and the step size controls.
    */
   private JPanel createZPanel(int idx) {
      final JFrame theWindow = this;
      JPanel result = new JPanel(new MigLayout("insets 0, gap 0, flowy"));
      result.add(new JLabel("Z Stage", JLabel.CENTER), "growx, alignx center");
      zDriveSelect_[idx] = new JComboBox<>();
      
      // use ItemListener here instead of ActionListener only so initialize() only has to worry about
      //   one type of listener on the combo-box (there are also ItemListeners for step size fields)
      zDriveSelect_[idx].addItemListener((ItemEvent e) -> {
         if (e.getStateChange() == ItemEvent.SELECTED) {
            settings_.putString(CURRENTZDRIVE + idx, zDriveSelect_[idx].getSelectedItem().toString());
            try {
               getZPosLabelFromCore(idx);
            } catch (Exception ex) {
               studio_.logs().logError(ex);
            }
         }
      });
      
      // HACK: this defined height for the combobox matches the height of one
      // of the chevron buttons, and helps to align components between the XY
      // and Z panels.
      result.add(zDriveSelect_[idx], "height 22!, gaptop 4, gapbottom 4, hidemode 0, growx");

      // Create buttons for stepping up/down.
      // Icon name prefix: double, single, single, double
      String[] prefixes = new String[] {"d", "s", "s", "d"};
      // Icon name component: up, up, down, down
      String[] directions = new String[] {"u", "u", "d", "d"};
      for (int i = 0; i < 4; ++i) {
         String path = "/org/micromanager/icons/stagecontrol/arrowhead-" +
                  prefixes[i] + directions[i];
         JButton button = new JButton(IconLoader.getIcon(path + ".png"));
         button.setBorder(null);
         button.setBorderPainted(false);
         button.setContentAreaFilled(false);
         button.setPressedIcon(IconLoader.getIcon(path + "p.png"));
         // This copy can be referred to in the action listener.
         final int index = i;
         button.addActionListener((ActionEvent e) -> {
            int dz = (index < 2) ? 1 : -1;
            double stepSize;
            JFormattedTextField text = (index == 0 || index == 3) ? 
                    zStepTextsMedium_[idx] : zStepTextsSmall_[idx];
            try {
               stepSize = NumberUtils.displayStringToDouble(text.getText());
            }
            catch (ParseException ex) {
               JOptionPane.showMessageDialog(theWindow, "Z-step value is not a number");
               return;
            }
            setRelativeStagePosition(dz * stepSize, idx);
         });
         result.add(button, "alignx center, growx");
         if (i == 1) {
            // Stick the Z position text in the middle.
            // HACK: As above HACK, this height matches the height of the
            // chevron buttons in the XY panel.
            zPositionLabel_[idx] = new JLabel("", JLabel.CENTER);
            result.add(zPositionLabel_[idx],
                  "height 30!, width 100:, alignx center, growx");
         }
      }

      // Spacer to vertically align stepsize controls with the XY panel.
      // Encompasses one chevron (height 30) and the gap the XY panel has
      // (height 20).
      result.add(new JLabel(), "height 50!");

      // Create the controls for setting the step size.
      // These heights again must match those of the corresponding stepsize
      // controls in the XY panel.
      zStepTextsSmall_[idx] = StageControlFrame.createDoubleEntryFieldFromCombo(settings_, zDriveSelect_[idx], SMALLMOVEMENTZ, 1.1);
      result.add(new JLabel(IconLoader.getIcon("/org/micromanager/icons/stagecontrol/arrowhead-sr.png")),
            "height 20!, span, split 3, flowx");
      result.add(zStepTextsSmall_[idx], "height 20!, width 50");
      result.add(new JLabel("\u00b5m"), "height 20!");

      zStepTextsMedium_[idx] = StageControlFrame.createDoubleEntryFieldFromCombo(settings_, zDriveSelect_[idx], MEDIUMMOVEMENTZ, 11.1);
      result.add(new JLabel(IconLoader.getIcon("/org/micromanager/icons/stagecontrol/arrowhead-dr.png")),
            "span, split 3, flowx");
      result.add(zStepTextsMedium_[idx], "height 20!, width 50");
      result.add(new JLabel("\u00b5m"), "height 20!");
      
      minusButtons_[idx] = new JButton("-");
      minusButtons_[idx].addActionListener((ActionEvent arg0) -> {
         String sysConfigFile = org.micromanager.internal.MMStudio.getInstance().getSysConfigFile();  // TODO add method to API
         int nrZPanels = settings_.getInteger(NRZPANELS + sysConfigFile, 0);
         if (nrZPanels > 1) {
            settings_.putInteger(NRZPANELS + sysConfigFile, nrZPanels-1);
            initialize();
         }
      });
      
      plusButtons_[idx] = new JButton("+");
      plusButtons_[idx].addActionListener((ActionEvent arg0) -> {
         String sysConfigFile = org.micromanager.internal.MMStudio.getInstance().getSysConfigFile();  // TODO add method to API
         int nrZPanels = settings_.getInteger(NRZPANELS + sysConfigFile, 0);
         if (nrZPanels < MAX_NUM_Z_PANELS) {
            settings_.putInteger(NRZPANELS + sysConfigFile, nrZPanels+1);
            initialize();
         }
      });
      
      result.add(minusButtons_[idx], "center, split 2, flowx");
      result.add(plusButtons_[idx]);

      return result;
   }

   /**
    * Starts the timer if updates are enabled, or stops it otherwise.
    */
   private void refreshTimer() {
      if (enableRefreshCB_.isSelected()) {
         startTimer();
      } else {
         stopTimer();
      }
   }
   
   /**
    * Unconditionally starts the timer.
    */
   private void startTimer() {
      // end any existing updater before starting (anew)
      stopTimer();
      timer_ = new Timer(true);
      timer_.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
           // update positions if we aren't already doing it or paused
           // this prevents building up task queue if something slows down
           if (staticFrame_!=null && staticFrame_.isVisible()) {  // don't update if stage control is hidden
              updateStagePositions();
           }
        }
      }, 0, 1000);  // 1 sec interval
   }
   
   /**
    * Unconditionally stops the timer.
    */
   private void stopTimer() {
      if (timer_ != null) {
         timer_.cancel();
      }
   }
   
   private void updateStagePositions() {
      try {
         if (xyPanel_.isVisible()) {
            getXYPosLabelFromCore();
         }
         for (int idx=0; idx<MAX_NUM_Z_PANELS; idx++) {
            if (zPanel_[idx].isVisible()) {
               getZPosLabelFromCore(idx);
            }
         }
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
   }
   
   private JPanel createSettingsPanel() {
      JPanel result = new JPanel(new MigLayout("insets 0, gap 0"));
      
      // checkbox to turn updates on and off
      enableRefreshCB_ = new JCheckBox("Polling updates");
      enableRefreshCB_.addItemListener((ItemEvent e) -> {
         settings_.putBoolean(REFRESH, enableRefreshCB_.isSelected());
         refreshTimer();
      });
      enableRefreshCB_.setSelected(settings_.getBoolean(REFRESH, false));
      result.add(enableRefreshCB_, "center, wrap");
      return result;
   }
   
   private JPanel createErrorPanel() {
      // Provide a friendly message when there are no drives in the device list
      JLabel noDriveLabel = new javax.swing.JLabel(
              "No XY or Z drive found.  Nothing to control.");
      noDriveLabel.setOpaque(true);

      JPanel panel = new JPanel(new MigLayout("fill"));
      panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
      panel.add(noDriveLabel, "align center, grow");
      panel.revalidate();

      return panel;
   }
   
   private void setRelativeXYStagePosition(double x, double y) {
      try {
         if (!core_.deviceBusy(core_.getXYStageDevice())) {
            StageThread st = new StageThread(core_.getXYStageDevice(), x, y);
            stageMotionExecutor_.execute(st);
         }
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
   }

   private void setRelativeStagePosition(double z, int idx) {
      try {
         String curDrive = zDriveSelect_[idx].getSelectedItem().toString();
         if (!core_.deviceBusy(curDrive)) {
            StageThread st = new StageThread(curDrive, z);
            stageMotionExecutor_.execute(st);
         }
      } catch (Exception ex) {
         studio_.logs().showError(ex);
      }
   }

   private void getXYPosLabelFromCore() throws Exception {
      Point2D.Double pos = core_.getXYStagePosition(core_.getXYStageDevice());
      setXYPosLabel(pos.x, pos.y);
   }

   private void setXYPosLabel(double x, double y) {
      xyPositionLabel_.setText(String.format(
              "<html>X: %s \u00b5m<br>Y: %s \u00b5m</html>",
              TextUtils.removeNegativeZero(NumberUtils.doubleToDisplayString(x)),
              TextUtils.removeNegativeZero(NumberUtils.doubleToDisplayString(y)) ));
   }

   private void getZPosLabelFromCore(int idx) throws Exception {
      double zPos = core_.getPosition(zDriveSelect_[idx].getSelectedItem().toString());
      setZPosLabel(zPos, idx);
   }

   private void setZPosLabel(double z, int idx) {
      zPositionLabel_[idx].setText(
              TextUtils.removeNegativeZero(
                      NumberUtils.doubleToDisplayString(z)) + 
               " \u00B5m");
   }
   
   private static JFormattedTextField createDoubleEntryFieldFromCombo(
         final MutablePropertyMapView settings, final JComboBox<String> cb, final String prefix, final double aDefault) {
      
      class FieldListener implements PropertyChangeListener, ItemListener {
         private final JFormattedTextField tf_;
         private final JComboBox<String> cb_;
         private final MutablePropertyMapView settings_;
         private final String prefix_;

         public FieldListener(JFormattedTextField tf, MutablePropertyMapView settings, JComboBox<String> cb, String prefix) {
            tf_ = tf;
            settings_ = settings;
            cb_ = cb;
            prefix_ = prefix;
         }

         @Override
         public String toString() {
            return (prefix_ + (String)cb_.getSelectedItem());
         }
         
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            settings_.putDouble(toString(), ((Number)tf_.getValue()).doubleValue());
         }

         @Override
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               tf_.setValue(settings_.getDouble(toString(), aDefault));
            }
         }
      }
      
      JFormattedTextField tf = new JFormattedTextField(NumberFormat.getNumberInstance());
      FieldListener listener = new FieldListener(tf, settings, cb, prefix);
      tf.setValue(settings.getDouble(listener.toString(), aDefault));
      tf.addPropertyChangeListener("value", listener);
      cb.addItemListener(listener);
      return tf;
   }

   @Subscribe
   public void onSystemConfigurationLoaded(SystemConfigurationLoadedEvent event) {
      initialize();
   }

   @Subscribe
   public void onStagePositionChanged(StagePositionChangedEvent event) {
      for (int idx=0; idx<MAX_NUM_Z_PANELS; ++idx) {
         if (event.getDeviceName().equals((String) zDriveSelect_[idx].getSelectedItem())) {
            setZPosLabel(event.getPos(), idx);
         }
      }
   }

   @Subscribe
   public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
      if (event.getDeviceName().contentEquals(core_.getXYStageDevice())) {
         setXYPosLabel(event.getXPos(), event.getYPos());
      }
   }

      
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.getIsCancelled()) {
         this.dispose();
      }
   }
   
   @Override
   public void dispose() {
      for (int i = 0; i < 3; i++) {
         try {
            settings_.putDouble(XY_MOVEMENTS[i],
                  NumberUtils.displayStringToDouble(xyStepTexts_[i].getText()));
         } catch (ParseException pex) {
            // since we are closing, no need to warn the user
         }
      }
//      for (int idx=0; idx<maxNumZPanels_; ++idx) {
//         storeZValuesInProfile(idx);
//      }
      stopTimer();
      super.dispose();
   }

   private class StageThread implements Runnable {
      final String device_;
      final boolean isXYStage_;
      final double x_;
      final double y_;
      final double z_;
      
      public StageThread(String device, double z) {
         device_ = device;
         z_ = z;
         x_ = y_ = 0;
         isXYStage_ = false;
      }

      public StageThread(String device, double x, double y) {
         device_ = device;
         x_ = x;
         y_ = y;
         z_ = 0;
         isXYStage_ = true;
      }

      @Override
      public void run() {
         try {
            core_.waitForDevice(device_);
            if (isXYStage_) {
               core_.setRelativeXYPosition(device_, x_, y_);
            }
            else {
               core_.setRelativePosition(device_, z_);
            }
            core_.waitForDevice(device_);
            if (isXYStage_) {
               getXYPosLabelFromCore();
            }
            else {
               for (int idx=0; idx<MAX_NUM_Z_PANELS; ++idx) {
                  getZPosLabelFromCore(idx);
               }
            }
         } catch (Exception ex) {
            studio_.logs().logError(ex);
         }
      }
   }
   
}