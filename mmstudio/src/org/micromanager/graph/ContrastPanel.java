///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiChannelContrastPanel.java
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.MetadataPanel;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.Histograms;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.ScaleBar;

/**
 *
 * @author Henry
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
   private MetadataPanel mdPanel_;
   private Preferences prefs_;
   private Color overlayColor_ = Color.white;
   private Histograms currentHistograms_;
   private VirtualAcquisitionDisplay currentDisplay_;

   public ContrastPanel(MetadataPanel md) {
      mdPanel_ = md;
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
       histDisplayScrollPane_.setViewportView( currentHistograms_ != null?(JPanel)currentHistograms_ : new JPanel());
       if (currentDisplay_ != null && currentDisplay_.getImageCache().getNumChannels() > 1) {
           histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
       } else {
           histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
       }
       this.repaint();
   }
   
   public HistogramControlsState createDefaultControlsState() {      
      HistogramControlsState state = new HistogramControlsState();
      state.autostretch = prefs_.getBoolean(PREF_AUTOSTRETCH, false);
      state.percentToIgnore = prefs_.getDouble(PREF_REJECT_FRACTION, 2);
      state.logHist = prefs_.getBoolean(PREF_LOG_HIST, false);
      state.ignoreOutliers = prefs_.getBoolean(PREF_REJECT_OUTLIERS, false);
      state.syncChannels = prefs_.getBoolean(PREF_SYNC_CHANNELS, false);
      state.slowHist = prefs_.getBoolean(PREF_SLOW_HIST, false);
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
      } else if (currentDisplay_.getImageCache().getNumChannels() == 1) {
         displayModeLabel_.setEnabled(false);
         displayModeCombo_.setEnabled(false);
         sizeBarCheckBox_.setEnabled(true);
         sizeBarComboBox_.setEnabled(true);
         sizeBarColorComboBox_.setEnabled(true);
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
         sizeBarComboBox_.setEnabled(true);
         sizeBarColorComboBox_.setEnabled(true);
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
      sizeBarCheckBox_.setSelected(state.scaleBar);
      sizeBarColorComboBox_.setSelectedIndex(state.scaleBarColorIndex);
      sizeBarComboBox_.setSelectedIndex(state.scaleBarLocationIndex);


      if (currentDisplay_ != null && currentDisplay_.getImagePlus() instanceof CompositeImage) {
         displayModeCombo_.setSelectedIndex(((CompositeImage) currentDisplay_.getImagePlus()).getMode() - 1);
      } else {
         displayModeCombo_.setSelectedIndex(2);
      }

   }

   private void saveCheckBoxStates() {
      prefs_.putBoolean(PREF_AUTOSTRETCH, autostretchCheckBox_.isSelected());
      prefs_.putBoolean(PREF_LOG_HIST, logHistCheckBox_.isSelected());
      prefs_.putDouble(PREF_REJECT_FRACTION, (Double) rejectPercentSpinner_.getValue());
      prefs_.putBoolean(PREF_REJECT_OUTLIERS, rejectOutliersCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SYNC_CHANNELS, syncChannelsCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SLOW_HIST, slowHistCheckBox_.isSelected());
      
      if (currentDisplay_ == null)
         return;
      HistogramControlsState s = currentDisplay_.getHistogramControlsState();
      s.autostretch = autostretchCheckBox_.isSelected();
      s.ignoreOutliers = rejectOutliersCheckBox_.isSelected();
      s.logHist = logHistCheckBox_.isSelected();
      s.percentToIgnore = (Double) rejectPercentSpinner_.getValue();
      s.slowHist = slowHistCheckBox_.isSelected();
      s.syncChannels = syncChannelsCheckBox_.isSelected();
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

      displayModeCombo_.setModel(new DefaultComboBoxModel(new String[]{"Composite", "Color", "Grayscale"}));
      displayModeCombo_.setToolTipText("<html>Choose display mode:<br> - Composite = Multicolor overlay<br> - Color = Single channel color view<br> - Grayscale = Single channel grayscale view</li></ul></html>");
      displayModeCombo_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            displayModeComboActionPerformed();
         }
      });
      displayModeLabel_.setText("Display mode:");

      autostretchCheckBox_.setText("Autostretch");
      autostretchCheckBox_.addChangeListener(new ChangeListener() {

         public void stateChanged(ChangeEvent evt) {
            autostretchCheckBoxStateChanged();
         }
      });
      rejectOutliersCheckBox_.setText("ignore %");
      rejectOutliersCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            rejectOutliersCheckBoxAction();
         }
      });

      rejectPercentSpinner_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      rejectPercentSpinner_.addChangeListener(new ChangeListener() {

         public void stateChanged(ChangeEvent evt) {
            rejectPercentageChanged();
         }
      });
      rejectPercentSpinner_.addKeyListener(new java.awt.event.KeyAdapter() {

         public void keyPressed(java.awt.event.KeyEvent evt) {
            rejectPercentageChanged();
         }
      });
      rejectPercentSpinner_.setModel(new SpinnerNumberModel(0.02, 0., 100., 0.1));


      logHistCheckBox_.setText("Log hist");
      logHistCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            logScaleCheckBoxActionPerformed();
         }
      });

      org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
              jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(jPanel1Layout.createSequentialGroup().addContainerGap(24, Short.MAX_VALUE).add(displayModeLabel_).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(displayModeCombo_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(autostretchCheckBox_).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(rejectOutliersCheckBox_).add(6, 6, 6).add(rejectPercentSpinner_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 63, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(logHistCheckBox_)));
      jPanel1Layout.setVerticalGroup(
              jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER).add(autostretchCheckBox_).add(rejectOutliersCheckBox_).add(rejectPercentSpinner_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(logHistCheckBox_)).add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE).add(displayModeCombo_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(displayModeLabel_)));

      sizeBarCheckBox_.setText("Scale Bar");
      sizeBarCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarCheckBoxActionPerformed();
         }
      });

      sizeBarComboBox_.setModel(new DefaultComboBoxModel(new String[]{"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"}));
      sizeBarComboBox_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarComboBoxActionPerformed();
         }
      });

      sizeBarColorComboBox_.setModel(new DefaultComboBoxModel(new String[]{"White", "Black", "Yellow", "Gray"}));
      sizeBarColorComboBox_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            overlayColorComboBox_ActionPerformed();
         }
      });

      syncChannelsCheckBox_ = new JCheckBox("Sync channels");
      syncChannelsCheckBox_.addChangeListener(new ChangeListener() {

         public void stateChanged(ChangeEvent e) {
            syncChannelsCheckboxAction();
         }
      });

      slowHistCheckBox_ = new JCheckBox("Slow hist");
      slowHistCheckBox_.addChangeListener(new ChangeListener() {

         public void stateChanged(ChangeEvent e) {
            slowHistCheckboxAction();
         }
      });



      org.jdesktop.layout.GroupLayout channelsTablePanel_Layout = new org.jdesktop.layout.GroupLayout(this);
      this.setLayout(channelsTablePanel_Layout);
      channelsTablePanel_Layout.setHorizontalGroup(
              channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(channelsTablePanel_Layout.createSequentialGroup().add(sizeBarCheckBox_).addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(sizeBarComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(sizeBarColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(syncChannelsCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(slowHistCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)).add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(histDisplayScrollPane_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)));

      channelsTablePanel_Layout.setVerticalGroup(
              channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(channelsTablePanel_Layout.createSequentialGroup().add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE).add(sizeBarCheckBox_).add(sizeBarComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(sizeBarColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(syncChannelsCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(slowHistCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)).addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(589, Short.MAX_VALUE)).add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(channelsTablePanel_Layout.createSequentialGroup().add(79, 79, 79).add(histDisplayScrollPane_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 571, Short.MAX_VALUE))));
   }

   public void setDisplayMode(int mode) {
      if (mode == CompositeImage.COMPOSITE) {
         displayModeCombo_.setSelectedIndex(0);
      } else if (mode == CompositeImage.COLOR) {
         displayModeCombo_.setSelectedIndex(1);
      } else if (mode == CompositeImage.GRAYSCALE) {
         displayModeCombo_.setSelectedIndex(2);
      }
   }

   private void showSizeBar() {
      boolean show = sizeBarCheckBox_.isSelected();
      if (currentDisplay_ == null)
         return;
      ImagePlus ip = currentDisplay_.getHyperImage();
      if (show) {
         ScaleBar sizeBar = new ScaleBar(ip);

         if (sizeBar != null) {
            Overlay ol = new Overlay();
            //ol.setFillColor(Color.white); // this causes the text to get a white background!
            ol.setStrokeColor(overlayColor_);
            String selected = (String) sizeBarComboBox_.getSelectedItem();
            if (selected.equals("Top-Right")) {
               sizeBar.setPosition(ScaleBar.Position.TOPRIGHT);
            }
            if (selected.equals("Top-Left")) {
               sizeBar.setPosition(ScaleBar.Position.TOPLEFT);
            }
            if (selected.equals("Bottom-Right")) {
               sizeBar.setPosition(ScaleBar.Position.BOTTOMRIGHT);
            }
            if (selected.equals("Bottom-Left")) {
               sizeBar.setPosition(ScaleBar.Position.BOTTOMLEFT);
            }
            sizeBar.addToOverlay(ol);
            ol.setStrokeColor(overlayColor_);
            ip.setOverlay(ol);
         }
      }
      ip.setHideOverlay(!show);
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
      showSizeBar();
   }

   private void syncChannelsCheckboxAction() {
      if (!syncChannelsCheckBox_.isEnabled())
         return;
      boolean synced = syncChannelsCheckBox_.isSelected();
      if (synced) {
         autostretchCheckBox_.setSelected(false);
         autostretchCheckBox_.setEnabled(false);
         ((MultiChannelHistograms) currentHistograms_).setChannelContrastFromFirst();
         ((MultiChannelHistograms) currentHistograms_).setChannelDisplayModeFromFirst();
      } else {
         autostretchCheckBox_.setEnabled(true);
      }
      saveCheckBoxStates();
   }

   private void slowHistCheckboxAction() {
      saveCheckBoxStates();
   }

   public void displayModeComboActionPerformed() {
      if (currentDisplay_ == null || !(currentDisplay_.getHyperImage() instanceof CompositeImage)  )
         return;
      int mode;
      int state = displayModeCombo_.getSelectedIndex();
      if (state == 0) {
         mode = CompositeImage.COMPOSITE;
      } else if (state == 1) {
         mode = CompositeImage.COLOR;
      } else {
         mode = CompositeImage.GRAYSCALE;
      }

      CompositeImage ci = (CompositeImage) currentDisplay_.getHyperImage();
      ci.setMode(mode);
      ci.updateAndDraw();
      currentDisplay_.updateAndDraw();
      saveCheckBoxStates();
   }

   private void autostretchCheckBoxStateChanged() {
      rejectOutliersCheckBox_.setEnabled(autostretchCheckBox_.isSelected());
      boolean rejectem = rejectOutliersCheckBox_.isSelected() && autostretchCheckBox_.isSelected();
      rejectPercentSpinner_.setEnabled(rejectem);
      if (autostretchCheckBox_.isSelected()) {
         if (currentHistograms_ != null) {
            currentHistograms_.autoscaleAllChannels();
         }
      } else {
         rejectOutliersCheckBox_.setSelected(false);
      }
      saveCheckBoxStates();
   }

   private void rejectOutliersCheckBoxAction() {
      rejectPercentSpinner_.setEnabled(rejectOutliersCheckBox_.isSelected());
      if (currentHistograms_ != null) {
         currentHistograms_.rejectOutliersChangeAction();
      }
      saveCheckBoxStates();
   }

   private void rejectPercentageChanged() {
      if (currentHistograms_ != null) {
         currentHistograms_.rejectOutliersChangeAction();
      }
      saveCheckBoxStates();
   }

   private void logScaleCheckBoxActionPerformed() {
      if (currentHistograms_ != null) {
         currentHistograms_.setLogScale();
      }
      saveCheckBoxStates();
   }

   public void sizeBarCheckBoxActionPerformed() {
      showSizeBar();
   }

   private void sizeBarComboBoxActionPerformed() {
      showSizeBar();
   }

   public void autostretch() {
      if (currentHistograms_ != null) {
         currentHistograms_.autostretch();
      }
   }

   public void imageChanged() {
      if (currentHistograms_ != null)
         ((JPanel)currentHistograms_).repaint();
   }

   public synchronized void displayChanged(VirtualAcquisitionDisplay disp) {
      currentDisplay_ = disp;
      currentHistograms_ = disp != null ? disp.getHistograms() : null;
      configureControls();
      showCurrentHistograms();
   }

   public void disableAutostretch() {
      autostretchCheckBox_.setSelected(false);
   }
}
