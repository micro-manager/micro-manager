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
package org.micromanager.graph;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.Overlay;
import java.awt.*;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.GroupLayout;
import javax.swing.LayoutStyle;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.internalinterfaces.Histograms;
import org.micromanager.utils.MMScaleBar;

/**
 *
 * @author Henry Pinkard
 * This class is a singleton instance of the component in the contrast tab of the
 * metadata panel.  It has a single instance of the controls on top, and changes which histograms 
 * are displayed based on the frontmost window
 */
public class ContrastPanel extends JPanel {

   private static final String PREF_AUTOSTRETCH = "stretch_contrast";
   private static final String PREF_REJECT_OUTLIERS = "reject_outliers";
   private static final String PREF_REJECT_FRACTION = "reject_fraction";
   private static final String PREF_LOG_HIST = "log_hist";
   private static final String PREF_SYNC_CHANNELS = "sync_channels";
   private static final String PREF_SLOW_HIST = "slow_hist";
   private JScrollPane histDisplayScrollPane_;
   private JComboBox displayModeCombo_;
   private JCheckBox autostretchCheckBox_;
   private JCheckBox rejectOutliersCheckBox_;
   private JSpinner rejectPercentSpinner_;
   private JCheckBox logHistCheckBox_;
   private JCheckBox sizeBarCheckBox_;
   private JComboBox sizeBarComboBox_;
   private JComboBox sizeBarColorComboBox_;
   private JCheckBox syncChannelsCheckBox_;
   private JCheckBox slowHistCheckBox_;
   private JLabel displayModeLabel_;
   private Preferences prefs_;
   private Color overlayColor_ = Color.white;
   private Histograms currentHistograms_;
   private VirtualAcquisitionDisplay currentDisplay_;

   public ContrastPanel() {
      initializeGUI();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      initializeHistogramDisplayArea();
   }

   private void initializeHistogramDisplayArea() {
      histDisplayScrollPane_.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

      showCurrentHistograms();
      configureControls();
   }

   private void showCurrentHistograms() {
      histDisplayScrollPane_.setViewportView(
              currentHistograms_ != null ? (JPanel) currentHistograms_ : new JPanel());
      if (currentDisplay_ != null && currentDisplay_.getImageCache().getNumDisplayChannels() > 1) {
         histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
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
      if (currentDisplay_ == null) {
         displayModeLabel_.setEnabled(false);
         displayModeCombo_.setEnabled(false);
         sizeBarCheckBox_.setEnabled(false);
         sizeBarComboBox_.setEnabled(false);
         sizeBarColorComboBox_.setEnabled(false);
         autostretchCheckBox_.setEnabled(false);
         slowHistCheckBox_.setEnabled(false);
         logHistCheckBox_.setEnabled(false);
         rejectOutliersCheckBox_.setEnabled(false);
         rejectPercentSpinner_.setEnabled(false);
         syncChannelsCheckBox_.setEnabled(false);
      } else if (currentDisplay_.getImageCache().getNumDisplayChannels() == 1) {
         displayModeLabel_.setEnabled(false);
         displayModeCombo_.setEnabled(false);
         sizeBarCheckBox_.setEnabled(true);
         sizeBarComboBox_.setEnabled(sizeBarCheckBox_.isSelected());
         sizeBarColorComboBox_.setEnabled(sizeBarCheckBox_.isSelected());
         syncChannelsCheckBox_.setEnabled(false);
         logHistCheckBox_.setEnabled(true);
         slowHistCheckBox_.setEnabled(true);
         autostretchCheckBox_.setEnabled(true);
         if (autostretchCheckBox_.isSelected()) {
            rejectOutliersCheckBox_.setEnabled(true);
            rejectPercentSpinner_.setEnabled(rejectOutliersCheckBox_.isSelected());
         } else {
            rejectOutliersCheckBox_.setEnabled(false);
            rejectPercentSpinner_.setEnabled(false);
         }
      } else {
         displayModeLabel_.setEnabled(true);
         displayModeCombo_.setEnabled(true);
         sizeBarCheckBox_.setEnabled(true);
         sizeBarComboBox_.setEnabled(sizeBarCheckBox_.isSelected());
         sizeBarColorComboBox_.setEnabled(sizeBarCheckBox_.isSelected());
         logHistCheckBox_.setEnabled(true);
         slowHistCheckBox_.setEnabled(true);
         syncChannelsCheckBox_.setEnabled(true);

      }
   }

   private void loadControlsStates() {
      HistogramControlsState state;

      if (currentDisplay_ != null) {
         state = currentDisplay_.getHistogramControlsState();
      } else {
         state = createDefaultControlsState();
      }

      logHistCheckBox_.setSelected(state.logHist);
      rejectPercentSpinner_.setValue(state.percentToIgnore);
      autostretchCheckBox_.setSelected(state.autostretch);
      rejectOutliersCheckBox_.setSelected(state.ignoreOutliers);
      syncChannelsCheckBox_.setSelected(state.syncChannels);
      slowHistCheckBox_.setSelected(state.slowHist);

      boolean bar = state.scaleBar;
      int color = state.scaleBarColorIndex;
      int location = state.scaleBarLocationIndex;
      sizeBarCheckBox_.setSelected(bar);
      sizeBarColorComboBox_.setSelectedIndex(color);
      sizeBarComboBox_.setSelectedIndex(location);



      if (currentDisplay_ != null && currentDisplay_.getImagePlus() instanceof CompositeImage) {
         //this block keeps all channels from being set to active by the setMode call, so that
         //deselected channels persist when switching windows
         CompositeImage ci = (CompositeImage) currentDisplay_.getImagePlus();
         boolean[] active = new boolean[ci.getActiveChannels().length]; 
         System.arraycopy( ci.getActiveChannels(), 0, active, 0, active.length );
         int index = ((CompositeImage) currentDisplay_.getImagePlus()).getMode() - 2;
         if (index == -1) {
            index = 2;
         }
         displayModeCombo_.setSelectedIndex(index);
         System.arraycopy(active, 0, ci.getActiveChannels(), 0, active.length);
         ci.updateAndDraw();
         currentDisplay_.updateAndDraw(true);
      } else {
         displayModeCombo_.setSelectedIndex(1);
      }

   }

   private void saveCheckBoxStates() {
      prefs_.putBoolean(PREF_AUTOSTRETCH, autostretchCheckBox_.isSelected());
      prefs_.putBoolean(PREF_LOG_HIST, logHistCheckBox_.isSelected());
      prefs_.putDouble(PREF_REJECT_FRACTION, (Double) rejectPercentSpinner_.getValue());
      prefs_.putBoolean(PREF_REJECT_OUTLIERS, rejectOutliersCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SYNC_CHANNELS, syncChannelsCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SLOW_HIST, slowHistCheckBox_.isSelected());

      if (currentDisplay_ == null) {
         return;
      }
      HistogramControlsState s = currentDisplay_.getHistogramControlsState();
      s.autostretch = autostretchCheckBox_.isSelected();
      s.ignoreOutliers = rejectOutliersCheckBox_.isSelected();
      s.logHist = logHistCheckBox_.isSelected();
      s.percentToIgnore = (Double) rejectPercentSpinner_.getValue();
      s.slowHist = slowHistCheckBox_.isSelected();
      s.syncChannels = syncChannelsCheckBox_.isSelected();
      s.scaleBar = sizeBarCheckBox_.isSelected();
      s.scaleBarColorIndex = sizeBarColorComboBox_.getSelectedIndex();
      s.scaleBarLocationIndex = sizeBarComboBox_.getSelectedIndex();
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
            sizeBarComboBoxActionPerformed();
         }
      });

      sizeBarColorComboBox_.setModel(new DefaultComboBoxModel(new String[]{"White", "Black", "Yellow", "Gray"}));
      sizeBarColorComboBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            overlayColorComboBox_ActionPerformed();
         }
      });

      syncChannelsCheckBox_ = new JCheckBox("Sync channels");
      syncChannelsCheckBox_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            syncChannelsCheckboxAction();
         }
      });

      slowHistCheckBox_ = new JCheckBox("Slow hist");
      slowHistCheckBox_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            slowHistCheckboxAction();
         }
      });
      
     
      javax.swing.GroupLayout channelsTablePanel_Layout = new javax.swing.GroupLayout(this);
      this.setLayout(channelsTablePanel_Layout);
      channelsTablePanel_Layout.setHorizontalGroup(
              channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(channelsTablePanel_Layout.createSequentialGroup()
              .addComponent(sizeBarCheckBox_).addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
              .addComponent(sizeBarComboBox_, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
              .addComponent(sizeBarColorComboBox_, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE).addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
              .addComponent(syncChannelsCheckBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addComponent(slowHistCheckBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
              .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addGroup(channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
              .addComponent(histDisplayScrollPane_, GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)));

      channelsTablePanel_Layout.setVerticalGroup(
              channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(channelsTablePanel_Layout.createSequentialGroup()
              .addGroup(channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
              .addComponent(sizeBarCheckBox_)
              .addComponent(sizeBarComboBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addComponent(sizeBarColorComboBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addComponent(syncChannelsCheckBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addComponent(slowHistCheckBox_, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
              .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
              .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addContainerGap(589, Short.MAX_VALUE))
              .addGroup(channelsTablePanel_Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
              .addGroup(channelsTablePanel_Layout.createSequentialGroup().addGap(79, 79, 79)
              .addComponent(histDisplayScrollPane_, GroupLayout.DEFAULT_SIZE, 571, Short.MAX_VALUE))));
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
   
   public void redrawSizeBar() {
      showSizeBar();
   }

   private void showSizeBar() {
      boolean show = sizeBarCheckBox_.isSelected();
      if (currentDisplay_ == null) {
         return;
      }
      ImagePlus ip = currentDisplay_.getHyperImage();
      if (ip == null) {
         return;
      }
      if (show) {
         MMScaleBar sizeBar = new MMScaleBar(ip);
         Overlay ol = ip.getOverlay();
         if (ol != null) {
            //see if there is an old scale bar and remove it if so
            removeScaleBarFromOverlay(ol);
         } else {
            ol = new Overlay();
         }
         //ol.setFillColor(Color.white); // this causes the text to get a white background!
         ol.setStrokeColor(overlayColor_);
         String selected = (String) sizeBarComboBox_.getSelectedItem();
         if (selected.equals("Top-Right")) {
            sizeBar.setPosition(MMScaleBar.Position.TOPRIGHT);
         }
         if (selected.equals("Top-Left")) {
            sizeBar.setPosition(MMScaleBar.Position.TOPLEFT);
         }
         if (selected.equals("Bottom-Right")) {
            sizeBar.setPosition(MMScaleBar.Position.BOTTOMRIGHT);
         }
         if (selected.equals("Bottom-Left")) {
            sizeBar.setPosition(MMScaleBar.Position.BOTTOMLEFT);
         }
         sizeBar.addToOverlay(ol);
         ol.setStrokeColor(overlayColor_);
         ip.setOverlay(ol);

      } else {
         //remove scale bar (don't just hide) so other elements of overlay will persist
         Overlay ol = ip.getOverlay();
         if (ol != null) {
            removeScaleBarFromOverlay(ol);
         }
         //so that it redraws to remove it
         ip.setOverlay(ol);
      }
      saveCheckBoxStates();
   }
   
   private void removeScaleBarFromOverlay(Overlay ol) {
      MMScaleBar.removeScaleBarFromOverlay(ol);
   }

   private void overlayColorComboBox_ActionPerformed() {
      if ((sizeBarColorComboBox_.getSelectedItem()).equals("Black")) {
         overlayColor_ = Color.black;
      } else if ((sizeBarColorComboBox_.getSelectedItem()).equals("White")) {
         overlayColor_ = Color.white;
      } else if ((sizeBarColorComboBox_.getSelectedItem()).equals("Yellow")) {
         overlayColor_ = Color.yellow;
      } else if ((sizeBarColorComboBox_.getSelectedItem()).equals("Gray")) {
         overlayColor_ = Color.gray;
      }
      if (sizeBarCheckBox_.isSelected()) {
         showSizeBar();
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
         if (currentHistograms_ != null) {
            ((MultiChannelHistograms) currentHistograms_).setChannelContrastFromFirst();
            ((MultiChannelHistograms) currentHistograms_).setChannelDisplayModeFromFirst();
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
      if (currentDisplay_ == null || !(currentDisplay_.getHyperImage() instanceof CompositeImage)) {
         return;
      }
      int mode;
      int state = displayModeCombo_.getSelectedIndex();
      if (state == 0) {
         mode = CompositeImage.COLOR;
      } else if (state == 1) {
         mode = CompositeImage.GRAYSCALE;
      } else {
         mode = CompositeImage.COMPOSITE;
      }
      CompositeImage ci = (CompositeImage) currentDisplay_.getHyperImage();
      

      if (state == 2 && currentDisplay_.getImageCache().getNumDisplayChannels() > 7) {
         JOptionPane.showMessageDialog(this, "Images with more than 7 channels cannot be displayed in Composite mode");
         displayModeCombo_.setSelectedIndex(ci.getMode()-2);
         return;
      } else {
         ci.setMode(mode);
         ci.updateAndDraw();
      }
      currentDisplay_.updateAndDraw(true);
      saveCheckBoxStates();
   }

   private void autostretchCheckBoxStateChanged() {
      rejectOutliersCheckBox_.setEnabled(autostretchCheckBox_.isSelected());
      boolean rejectem = rejectOutliersCheckBox_.isSelected() && autostretchCheckBox_.isSelected();
      rejectPercentSpinner_.setEnabled(rejectem);
      saveCheckBoxStates();
      if (autostretchCheckBox_.isSelected()) {
         if (currentHistograms_ != null) {
            currentHistograms_.autoscaleAllChannels();
         }
      } else {
         rejectOutliersCheckBox_.setSelected(false);
      }
   }

   private void rejectOutliersCheckBoxAction() {
      saveCheckBoxStates();
      rejectPercentSpinner_.setEnabled(rejectOutliersCheckBox_.isSelected());
      if (currentHistograms_ != null) {
         currentHistograms_.rejectOutliersChangeAction();
      }
   }

   private void rejectPercentageChanged() {
      saveCheckBoxStates();
      if (currentHistograms_ != null) {
         currentHistograms_.rejectOutliersChangeAction();
      }
   }

   private void logScaleCheckBoxActionPerformed() {
      saveCheckBoxStates();
      if (currentHistograms_ != null) {
         currentHistograms_.calcAndDisplayHistAndStats();
      }
   }

   public void sizeBarCheckBoxActionPerformed() {
      boolean checked = sizeBarCheckBox_.isSelected();
      sizeBarComboBox_.setEnabled(checked);
      sizeBarColorComboBox_.setEnabled(checked);
      showSizeBar();

   }

   private void sizeBarComboBoxActionPerformed() {
      if (sizeBarCheckBox_.isSelected()) {
         showSizeBar();
      }
   }

   public void autostretch() {
      if (currentHistograms_ != null) {
         currentHistograms_.autostretch();
      }
   }

   public void imageChanged() {
      if (currentHistograms_ != null) {
         ((JPanel) currentHistograms_).repaint();
      }
   }

   public synchronized void displayChanged(VirtualAcquisitionDisplay disp) {
      currentDisplay_ = disp;
      currentHistograms_ = disp != null ? disp.getHistograms() : null;
      configureControls();
      showCurrentHistograms();
   }

   public void disableAutostretch() {
      autostretchCheckBox_.setSelected(false);
      saveCheckBoxStates();
   }
}
