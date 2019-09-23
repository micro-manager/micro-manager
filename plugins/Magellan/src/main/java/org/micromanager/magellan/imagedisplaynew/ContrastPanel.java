///////////////////////////////////////////////////////////////////////////////
//FILE:          ContrastPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
package org.micromanager.magellan.imagedisplaynew;

import com.google.common.eventbus.Subscribe;
import ij.CompositeImage;
import org.micromanager.magellan.imagedisplay.DisplayOverlayer;
import org.micromanager.magellan.imagedisplay.MMScaleBar;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.magellan.imagedisplay.MagellanDisplay;
import org.micromanager.magellan.imagedisplaynew.MagellanDisplayController;
import org.micromanager.magellan.imagedisplaynew.events.ContrastUpdatedEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry Pinkard This class is a singleton instance of the component in
 * the contrast tab of the metadata panel. It has a single instance of the
 * controls on top, and changes which histograms are displayed based on the
 * frontmost window
 */
 class ContrastPanel extends JPanel {

   private static final String PREF_AUTOSTRETCH = "stretch_contrast";
   private static final String PREF_REJECT_OUTLIERS = "reject_outliers";
   private static final String PREF_REJECT_FRACTION = "reject_fraction";
   private static final String PREF_LOG_HIST = "log_hist";
   private static final String PREF_SYNC_CHANNELS = "sync_channels";
   private static final String PREF_SLOW_HIST = "slow_hist";
   protected JScrollPane histDisplayScrollPane_;
   private JComboBox displayModeCombo_;
   private JCheckBox autostretchCheckBox_;
   private JCheckBox rejectOutliersCheckBox_;
   private JSpinner rejectPercentSpinner_;
   private JCheckBox logHistCheckBox_;
   private JCheckBox sizeBarCheckBox_;
   private JComboBox sizeBarComboBox_;
   private JComboBox sizeBarColorComboBox_;
   private JCheckBox syncChannelsCheckBox_;
   private JLabel displayModeLabel_;
   private MutablePropertyMapView prefs_;
   protected MultiChannelHistograms histograms_;
   private HistogramControlsState histControlsState_;
   private DisplayOverlayer overlayer_;
   //volatile because accessed by overlayer creation thread
   private volatile boolean showScaleBar_ = false;
   private volatile String sizeBarColorSelection_ = "White";
   private volatile int sizeBarPosition_ = 0;
   private MagellanDisplayController display_;

   public ContrastPanel(MagellanDisplayController display) {
      //TODO: this isnt right is it?

      histograms_ = new MultiChannelHistograms(display, this);
      display_ = display;
      display_.registerForEvents(this);
      initializeGUI();
      prefs_ = Magellan.getStudio().profile().getSettings(ContrastPanel.class);
            histControlsState_ = createDefaultControlsState();
      initializeHistogramDisplayArea();
      configureControls();
      showCurrentHistograms();
      imageChanged();
   }
   
      @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      display_.unregisterForEvents(this);
      display_ = null;
      histograms_ = null;
      histControlsState_ = null;
   }

   public void setOverlayer(DisplayOverlayer overlayer) {
      overlayer_ = overlayer;
   }

   public HistogramControlsState getHistogramControlsState() {
      return histControlsState_;
   }

   private void initializeHistogramDisplayArea() {
      histDisplayScrollPane_.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      histDisplayScrollPane_.getVerticalScrollBar().setUnitIncrement(8);
      showCurrentHistograms();
      configureControls();
   }

   private void showCurrentHistograms() {
      histDisplayScrollPane_.setViewportView(
              histograms_ != null ? (JPanel) histograms_ : new JPanel());
      if (histograms_ != null) {
         histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      } else {
         histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      }
      this.repaint();
   }

   public HistogramControlsState createDefaultControlsState() {
      HistogramControlsState state = new HistogramControlsState();
      state.autostretch = prefs_.getBoolean(PREF_AUTOSTRETCH, true);
      state.percentToIgnore = prefs_.getDouble(PREF_REJECT_FRACTION, 2);
      state.logHist = prefs_.getBoolean(PREF_LOG_HIST, false);
      state.ignoreOutliers = prefs_.getBoolean(PREF_REJECT_OUTLIERS, false);
      state.syncChannels = prefs_.getBoolean(PREF_SYNC_CHANNELS, false);
      state.slowHist = prefs_.getBoolean(PREF_SLOW_HIST, false);
      state.scaleBar = false;
      state.scaleBarColorIndex = 0;
      state.scaleBarLocationIndex = 0;
      return state;
   }

   private void configureControls() {
      loadControlsStates();
      displayModeLabel_.setEnabled(true);
      displayModeCombo_.setEnabled(true);
      sizeBarCheckBox_.setEnabled(true);
      sizeBarComboBox_.setEnabled(sizeBarCheckBox_.isSelected());
      sizeBarColorComboBox_.setEnabled(sizeBarCheckBox_.isSelected());
      logHistCheckBox_.setEnabled(true);
      syncChannelsCheckBox_.setEnabled(true);
   }

   private void loadControlsStates() {
      

      logHistCheckBox_.setSelected(histControlsState_.logHist);
      rejectPercentSpinner_.setValue(histControlsState_.percentToIgnore);
      autostretchCheckBox_.setSelected(histControlsState_.autostretch);
      rejectOutliersCheckBox_.setSelected(histControlsState_.ignoreOutliers);
      syncChannelsCheckBox_.setSelected(histControlsState_.syncChannels);

      boolean bar = histControlsState_.scaleBar;
      int color = histControlsState_.scaleBarColorIndex;
      int location = histControlsState_.scaleBarLocationIndex;
      sizeBarCheckBox_.setSelected(bar);
      sizeBarColorComboBox_.setSelectedIndex(color);
      sizeBarComboBox_.setSelectedIndex(location);

      displayModeCombo_.setSelectedIndex(1);

   }

   private void saveCheckBoxStates() {
      prefs_.putBoolean(PREF_AUTOSTRETCH, autostretchCheckBox_.isSelected());
      prefs_.putBoolean(PREF_LOG_HIST, logHistCheckBox_.isSelected());
      prefs_.putDouble(PREF_REJECT_FRACTION, (Double) rejectPercentSpinner_.getValue());
      prefs_.putBoolean(PREF_REJECT_OUTLIERS, rejectOutliersCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SYNC_CHANNELS, syncChannelsCheckBox_.isSelected());

      if (display_ == null) {
         return;
      }
      histControlsState_.autostretch = autostretchCheckBox_.isSelected();
      histControlsState_.ignoreOutliers = rejectOutliersCheckBox_.isSelected();
      histControlsState_.logHist = logHistCheckBox_.isSelected();
      histControlsState_.percentToIgnore = (Double) rejectPercentSpinner_.getValue();
      histControlsState_.syncChannels = syncChannelsCheckBox_.isSelected();
      histControlsState_.scaleBar = sizeBarCheckBox_.isSelected();
      histControlsState_.scaleBarColorIndex = sizeBarColorComboBox_.getSelectedIndex();
      histControlsState_.scaleBarLocationIndex = sizeBarComboBox_.getSelectedIndex();
   }

   private void initializeGUI() {
      JPanel jPanel1 = new JPanel();
      displayModeLabel_ = new JLabel();
      displayModeCombo_ = new JComboBox();
      autostretchCheckBox_ = new JCheckBox();
      rejectOutliersCheckBox_ = new JCheckBox();
      rejectPercentSpinner_ = new JSpinner();
      logHistCheckBox_ = new JCheckBox();
      sizeBarCheckBox_ = new JCheckBox();
      sizeBarComboBox_ = new JComboBox();
      sizeBarColorComboBox_ = new JComboBox();

      histDisplayScrollPane_ = new JScrollPane();

      this.setPreferredSize(new Dimension(400, 594));

      displayModeCombo_.setModel(new DefaultComboBoxModel(new String[]{"Color", "Grayscale", "Composite"}));
      displayModeCombo_.setToolTipText("<html>Choose display mode:<br> - Composite = Multicolor overlay<br> - Color = Single channel color view<br> - Grayscale = Single channel grayscale view</li></ul></html>");
      displayModeCombo_.setSelectedIndex(2);
      displayModeCombo_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            displayModeComboActionPerformed();
         }
      });
      displayModeLabel_.setText("Display mode:");

      autostretchCheckBox_.setText("Autostretch");
      autostretchCheckBox_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent evt) {
            autostretchCheckBoxStateChanged();
         }
      });
      rejectOutliersCheckBox_.setText("ignore %");
      rejectOutliersCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            rejectOutliersCheckBoxAction();
         }
      });

      rejectPercentSpinner_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      rejectPercentSpinner_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent evt) {
            rejectPercentageChanged();
         }
      });
      rejectPercentSpinner_.addKeyListener(new java.awt.event.KeyAdapter() {

         @Override
         public void keyPressed(java.awt.event.KeyEvent evt) {
            rejectPercentageChanged();
         }
      });
      rejectPercentSpinner_.setModel(new SpinnerNumberModel(0.02, 0., 100., 0.1));

      logHistCheckBox_.setText("Log hist");
      logHistCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            logScaleCheckBoxActionPerformed();
         }
      });

      javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
              jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(jPanel1Layout.createSequentialGroup().addContainerGap(24, Short.MAX_VALUE).addComponent(displayModeLabel_).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(displayModeCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(autostretchCheckBox_).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(rejectOutliersCheckBox_).addGap(6, 6, 6).addComponent(rejectPercentSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(logHistCheckBox_)));
      jPanel1Layout.setVerticalGroup(
              jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER).addComponent(autostretchCheckBox_).addComponent(rejectOutliersCheckBox_).addComponent(rejectPercentSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(logHistCheckBox_)).addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(displayModeCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(displayModeLabel_)));

      sizeBarCheckBox_.setText("Scale Bar");
      sizeBarCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarCheckBoxActionPerformed();
         }
      });

      sizeBarComboBox_.setModel(new DefaultComboBoxModel(new String[]{"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"}));
      sizeBarComboBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarPosition_ = sizeBarComboBox_.getSelectedIndex();
            if (overlayer_ != null) {
               overlayer_.redrawOverlay();
            }
         }
      });

      sizeBarColorComboBox_.setModel(new DefaultComboBoxModel(new String[]{"White", "Black", "Green", "Gray"}));
      sizeBarColorComboBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarColorSelection_ = (String) sizeBarColorComboBox_.getSelectedItem();
            if (overlayer_ != null) {
               overlayer_.redrawOverlay();
            }
         }
      });

      syncChannelsCheckBox_ = new JCheckBox("Sync channels");
      syncChannelsCheckBox_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            syncChannelsCheckboxAction();
         }
      });

      javax.swing.GroupLayout channelsTablePanel_Layout = new javax.swing.GroupLayout(this);
      this.setLayout(channelsTablePanel_Layout);
      channelsTablePanel_Layout.setHorizontalGroup(
              channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(channelsTablePanel_Layout.createSequentialGroup().
              addComponent(sizeBarCheckBox_).addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED).addComponent(sizeBarComboBox_, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
              .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(sizeBarColorComboBox_, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE)
              .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED).addComponent(syncChannelsCheckBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)).addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).addGroup(channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(histDisplayScrollPane_, GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)));

      channelsTablePanel_Layout.setVerticalGroup(
              channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(channelsTablePanel_Layout.createSequentialGroup().
              addGroup(channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(sizeBarCheckBox_)
                      .addComponent(sizeBarComboBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).
                      addComponent(sizeBarColorComboBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).
                      addComponent(syncChannelsCheckBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
              .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED).addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).addContainerGap(589, Short.MAX_VALUE)).addGroup(channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(channelsTablePanel_Layout.createSequentialGroup().addGap(79, 79, 79).addComponent(histDisplayScrollPane_, GroupLayout.DEFAULT_SIZE, 571, Short.MAX_VALUE))));
   }

   public void setDisplayMode(int mode) {
      if (mode == CompositeImage.COMPOSITE) {
         displayModeCombo_.setSelectedIndex(2);
      } else if (mode == CompositeImage.COLOR) {
         displayModeCombo_.setSelectedIndex(0);
      } else if (mode == CompositeImage.GRAYSCALE) {
         displayModeCombo_.setSelectedIndex(1);
      }
   }

   private void syncChannelsCheckboxAction() {
      if (!syncChannelsCheckBox_.isEnabled()) {
         return;
      }
      boolean synced = syncChannelsCheckBox_.isSelected();
      if (synced) {
         autostretchCheckBox_.setSelected(false);
         autostretchCheckBox_.setEnabled(false);
         if (histograms_ != null) {
            ((MultiChannelHistograms) histograms_).setChannelContrastFromFirst();
            ((MultiChannelHistograms) histograms_).setChannelDisplayModeFromFirst();
         }
      } else {
         autostretchCheckBox_.setEnabled(true);
      }
      saveCheckBoxStates();
   }

   private void slowHistCheckboxAction() {
      saveCheckBoxStates();
   }

   public void displayModeComboActionPerformed() {
      Runnable runnable = new Runnable() {

         @Override
         public void run() {

            display_.postEvent(new ContrastUpdatedEvent(displayModeCombo_.getSelectedIndex()));
            saveCheckBoxStates();

         }
      };
      if (SwingUtilities.isEventDispatchThread()) {
         runnable.run();
      } else {
         try {
            SwingUtilities.invokeAndWait(runnable);
         } catch (Exception ex) {
            Log.log("Couldn't initialize display mode");
         }
      }
   }

   private void autostretchCheckBoxStateChanged() {
      rejectOutliersCheckBox_.setEnabled(autostretchCheckBox_.isSelected());
      boolean rejectem = rejectOutliersCheckBox_.isSelected() && autostretchCheckBox_.isSelected();
      rejectPercentSpinner_.setEnabled(rejectem);
      saveCheckBoxStates();
      if (autostretchCheckBox_.isSelected()) {
         if (histograms_ != null) {
            histograms_.autoscaleAllChannels();
         }
      } else {
         rejectOutliersCheckBox_.setSelected(false);
      }
   }

   private void rejectOutliersCheckBoxAction() {
      saveCheckBoxStates();
      rejectPercentSpinner_.setEnabled(rejectOutliersCheckBox_.isSelected());
      if (histograms_ != null) {
         histograms_.rejectOutliersChangeAction();
      }
   }

   private void rejectPercentageChanged() {
      saveCheckBoxStates();
      if (histograms_ != null) {
         histograms_.rejectOutliersChangeAction();
      }
   }

   private void logScaleCheckBoxActionPerformed() {
      saveCheckBoxStates();
      if (histograms_ != null) {
         histograms_.calcAndDisplayHistAndStats(true);
      }
   }

   public void sizeBarCheckBoxActionPerformed() {
      showScaleBar_ = sizeBarCheckBox_.isSelected();
      sizeBarComboBox_.setEnabled(showScaleBar_);
      sizeBarColorComboBox_.setEnabled(showScaleBar_);
      overlayer_.redrawOverlay();
   }

   public MMScaleBar.Position getScaleBarPosition() {
      switch (sizeBarPosition_) {
         case 0:
            return MMScaleBar.Position.TOPLEFT;
         case 1:
            return MMScaleBar.Position.TOPRIGHT;
         case 2:
            return MMScaleBar.Position.BOTTOMLEFT;
         default:
            return MMScaleBar.Position.BOTTOMRIGHT;
      }
   }

   public Color getScaleBarColor() {
      if (sizeBarColorSelection_.equals("Black")) {
         return Color.black;
      } else if (sizeBarColorSelection_.equals("White")) {
         return Color.white;
      } else if (sizeBarColorSelection_.equals("Green")) {
         return Color.green;
      } else {
         return Color.gray;
      }
   }

   public boolean showScaleBar() {
      return showScaleBar_;
   }

   public void autostretch() {
      if (histograms_ != null) {
         histograms_.autostretch();
      }
   }

   public void imageChanged() {
      if (histograms_ != null) {
         histograms_.imageChanged();
         ((JPanel) histograms_).repaint();
      }
   }

   public void disableAutostretch() {
      autostretchCheckBox_.setSelected(false);
      saveCheckBoxStates();
   }
}
