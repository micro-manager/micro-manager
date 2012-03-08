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

   private static final String SINGLE = "Single Channel";
   private static final String MULTIPLE = "Multiple Channels";
   public static final String BLANK = "Blank";
   private static final String PREF_AUTOSTRETCH = "stretch_contrast";
   private static final String PREF_REJECT_OUTLIERS = "reject_outliers";
   private static final String PREF_REJECT_FRACTION = "reject_fraction";
   private static final String PREF_LOG_HIST = "log_hist";
   private static final String PREF_SYNC_CHANNELS = "sync_channels";
   private static final String PREF_SLOW_HIST = "slow_hist";
   private static final int SLOW_HIST_UPDATE_INTERVAL_MS = 1000;
   private long lastUpdateTime_;
   private JScrollPane multiChannelScrollPane_;
   private JPanel histogramHolderPanel_;
   private JComboBox displayModeCombo_;
   private JCheckBox autostretchCheckBox_;
   private JCheckBox rejectOutliersCheckBox_;
   private JSpinner rejectPercentSpinner_;
   private JCheckBox logHistCheckBox_;
   private JCheckBox sizeBarCheckBox_;
   private JComboBox sizeBarComboBox_;
   private JComboBox overlayColorComboBox_;
   private JCheckBox syncChannelsCheckBox_;
   private JCheckBox slowHistCheckBox_;
   private JLabel displayModeLabel_;
   private MetadataPanel mdPanel_;
   private Preferences prefs_;
   private Color overlayColor_ = Color.white;
   private Histograms currentHistograms_;
   private Histograms singleHistogram_;
   private Histograms multipleHistograms_;
   private CardLayout layout_;

   public ContrastPanel(MetadataPanel md) {
      mdPanel_ = md;
      initialize();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      enableAppropriateControls(BLANK);
      initializeHistograms();
   }

   private void initializeHistograms() {
      singleHistogram_ = new SingleChannelHistogram(mdPanel_, this);
      multipleHistograms_ = new MultiChannelHistograms(mdPanel_, this);

      multiChannelScrollPane_.setViewportView((JPanel)multipleHistograms_);
      multiChannelScrollPane_.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

      layout_ = new CardLayout();
      histogramHolderPanel_.setLayout(layout_);
      histogramHolderPanel_.add(multiChannelScrollPane_, MULTIPLE);
      histogramHolderPanel_.add((JPanel)singleHistogram_, SINGLE);
      histogramHolderPanel_.add(new JPanel(new BorderLayout()), BLANK);
      showHistograms(BLANK);
   }

   public void enableAppropriateControls(String label) {
      loadControlsStates();
      if (label.equals(BLANK)) {
         displayModeLabel_.setEnabled(false);
         displayModeCombo_.setEnabled(false);
         sizeBarCheckBox_.setEnabled(false);
         sizeBarComboBox_.setEnabled(false);
         overlayColorComboBox_.setEnabled(false);
         autostretchCheckBox_.setEnabled(false);
         slowHistCheckBox_.setEnabled(false);
         logHistCheckBox_.setEnabled(false);
         rejectOutliersCheckBox_.setEnabled(false);
         rejectPercentSpinner_.setEnabled(false);
         syncChannelsCheckBox_.setEnabled(false);
      } else if (label.equals(SINGLE)) {
         displayModeLabel_.setEnabled(false);
         displayModeCombo_.setEnabled(false);
         sizeBarCheckBox_.setEnabled(true);
         sizeBarComboBox_.setEnabled(true);
         overlayColorComboBox_.setEnabled(true);
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


      } else if (label.equals(MULTIPLE)) {
         displayModeLabel_.setEnabled(true);
         displayModeCombo_.setEnabled(true);
         sizeBarCheckBox_.setEnabled(true);
         sizeBarComboBox_.setEnabled(true);
         overlayColorComboBox_.setEnabled(true);
         logHistCheckBox_.setEnabled(true);
         slowHistCheckBox_.setEnabled(true);      
         syncChannelsCheckBox_.setEnabled(true);

      }
   }

   private void loadControlsStates() {
      logHistCheckBox_.setSelected(prefs_.getBoolean(PREF_LOG_HIST, false));
      rejectPercentSpinner_.setValue(prefs_.getDouble(PREF_REJECT_FRACTION, 2));
      autostretchCheckBox_.setSelected(prefs_.getBoolean(PREF_AUTOSTRETCH, false));
      rejectOutliersCheckBox_.setSelected(prefs_.getBoolean(PREF_REJECT_OUTLIERS, false));
      syncChannelsCheckBox_.setSelected(prefs_.getBoolean(PREF_SYNC_CHANNELS, false));
      slowHistCheckBox_.setSelected(prefs_.getBoolean(PREF_SLOW_HIST, false));
      ImagePlus img = mdPanel_.getCurrentImage();
      if (img != null) {
         if (img.getOverlay() != null)
            sizeBarCheckBox_.setSelected(true);
         if (img instanceof CompositeImage) {
            displayModeCombo_.setSelectedIndex( ((CompositeImage) img).getMode() -1  );
         } else {
            displayModeCombo_.setSelectedIndex(2);
         }
      }
   }

   public void showHistograms(String label) {
      layout_.show(histogramHolderPanel_, label);
      if (label.equals(BLANK)) {
         currentHistograms_ = null;
      } else if (label.equals(SINGLE)) {
         currentHistograms_ = singleHistogram_;
      } else if (label.equals(MULTIPLE)) {
         currentHistograms_ = multipleHistograms_;
      }
   }

   private void saveCheckBoxStates() {
      prefs_.putBoolean(PREF_AUTOSTRETCH, autostretchCheckBox_.isSelected());
      prefs_.putBoolean(PREF_LOG_HIST, logHistCheckBox_.isSelected());
      prefs_.putDouble(PREF_REJECT_FRACTION, (Double) rejectPercentSpinner_.getValue());
      prefs_.putBoolean(PREF_REJECT_OUTLIERS, rejectOutliersCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SYNC_CHANNELS, syncChannelsCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SLOW_HIST, slowHistCheckBox_.isSelected());
   }

   private void initialize() {
      JPanel jPanel1 = new JPanel();
      displayModeLabel_ = new JLabel();
      displayModeCombo_ = new JComboBox();
      autostretchCheckBox_ = new JCheckBox();
      rejectOutliersCheckBox_ = new JCheckBox();
      rejectPercentSpinner_ = new JSpinner();
      logHistCheckBox_ = new JCheckBox();
      sizeBarCheckBox_ = new JCheckBox();
      sizeBarComboBox_ = new JComboBox();
      overlayColorComboBox_ = new JComboBox();

      multiChannelScrollPane_ = new JScrollPane();
      histogramHolderPanel_ = new JPanel();


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

      overlayColorComboBox_.setModel(new DefaultComboBoxModel(new String[]{"White", "Black", "Yellow", "Gray"}));
      overlayColorComboBox_.addActionListener(new java.awt.event.ActionListener() {

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
              channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(channelsTablePanel_Layout.createSequentialGroup().add(sizeBarCheckBox_).addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(sizeBarComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(overlayColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(syncChannelsCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(slowHistCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)).add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(histogramHolderPanel_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)));

      channelsTablePanel_Layout.setVerticalGroup(
              channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(channelsTablePanel_Layout.createSequentialGroup().add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE).add(sizeBarCheckBox_).add(sizeBarComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(overlayColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(syncChannelsCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(slowHistCheckBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)).addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(589, Short.MAX_VALUE)).add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(channelsTablePanel_Layout.createSequentialGroup().add(79, 79, 79).add(histogramHolderPanel_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 571, Short.MAX_VALUE))));
   }

   public synchronized void setup(ImageCache cache) {
      ImagePlus imgp = cache.getImagePlus();
      String label = imgp instanceof CompositeImage ? MULTIPLE : SINGLE;

      showHistograms(label);
      
      //load appropriate contrast settings calc and display hist, apply LUT and draw
      displayChanged(imgp, cache);
      
      if (cache.getNumChannels() > 1) {
         boolean[] oldActive = ((CompositeImage) imgp).getActiveChannels();
         boolean[] active = Arrays.copyOf(oldActive, oldActive.length);
         this.setDisplayMode(((CompositeImage) imgp).getMode());
         for (int i = 0; i < active.length; i++) {
            ((CompositeImage) imgp).getActiveChannels()[i] = active[i];
         }

         sizeBarCheckBoxActionPerformed();
      }
      enableAppropriateControls(label);

      mdPanel_.imageChangedUpdate(imgp, cache);
   }

   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (currentHistograms_ != null) {
         currentHistograms_.setChannelContrast(channelIndex, min, max, gamma);
      }
      mdPanel_.drawWithoutUpdate();
   }

   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (currentHistograms_ != null) {
         currentHistograms_.setChannelHistogramDisplayMax(channelIndex, histMax);
      }
   }

   public ContrastSettings getChannelContrast(int channel) {
      ImagePlus img = mdPanel_.getCurrentImage();
      if (img == null) {
         return null;
      }
      if (currentHistograms_ != null) {
         return currentHistograms_.getChannelContrastSettings(channel);
      }
      return null;
   }

   public void loadSimpleWinContrastWithoutDraw(ImageCache cache, ImagePlus img) {
      if (currentHistograms_ != null) {
         int n = cache.getNumChannels();
         ContrastSettings c;
         for (int i = 0; i < n; i++) {
            c = MMStudioMainFrame.getInstance().loadSimpleContrastSettigns(cache.getPixelType(), i);
            currentHistograms_.setChannelContrast(i, c.min, c.max, c.gamma);
         }
         currentHistograms_.applyLUTToImage(img, cache);
      }
   }

   public void autoscaleWithoutDraw(ImageCache cache, ImagePlus img) {
      if (currentHistograms_ != null) {
         currentHistograms_.calcAndDisplayHistAndStats(img, true);
         currentHistograms_.autostretch();
         currentHistograms_.applyLUTToImage(img, cache);
      }
   }

   public void autoscaleOverStackWithoutDraw(ImageCache cache, ImagePlus img, int channel,
           HashMap<Integer, Integer> mins, HashMap<Integer, Integer> maxes) {
      int nSlices = ((VirtualAcquisitionDisplay.IMMImagePlus) img).getNSlicesUnverified();
      int nChannels = ((VirtualAcquisitionDisplay.IMMImagePlus) img).getNChannelsUnverified();
      int bytes = img.getBytesPerPixel();
      int pixMin, pixMax;
      if (mins.containsKey(channel)) {
         pixMin = mins.get(channel);
         pixMax = maxes.get(channel);
      } else {
         pixMax = 0;
         pixMin = (int) (Math.pow(2, 8 * bytes) - 1);
      }
      int z = img.getSlice() - 1;
      int flatIndex = 1 + channel + z * nChannels;
      if (bytes == 2) {
         short[] pixels = (short[]) img.getStack().getPixels(flatIndex);
         for (short value : pixels) {
            if (value < pixMin) {
               pixMin = value;
            }
            if (value > pixMax) {
               pixMax = value;
            }
         }
      } else if (bytes == 1) {
         byte[] pixels = (byte[]) img.getStack().getPixels(flatIndex);
         for (byte value : pixels) {
            if (value < pixMin) {
               pixMin = value;
            }
            if (value > pixMax) {
               pixMax = value;
            }
         }
      }

      //autoscale the channel
      if (currentHistograms_ != null) {
         currentHistograms_.setChannelContrast(channel, pixMin, pixMax, 1.0);
      }

      if (currentHistograms_ != null) {
         currentHistograms_.applyLUTToImage(img, cache);
      }

      mins.put(channel, pixMin);
      maxes.put(channel, pixMax);
   }

   public void refresh() {
      ImagePlus img = mdPanel_.getCurrentImage();
      if (currentHistograms_ == null || img == null) {
         return;
      }
      VirtualAcquisitionDisplay vad = VirtualAcquisitionDisplay.getDisplay(img);
      if (vad == null) {
         return;
      }
      ImageCache cache = vad.getImageCache();
      if (currentHistograms_ instanceof MultiChannelHistograms) {
         ((MultiChannelHistograms)currentHistograms_).setupChannelControls(cache);
      }
      currentHistograms_.displayChanged(img, cache);
      mdPanel_.imageChangedUpdate(img, cache);
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
      ImagePlus ip = mdPanel_.getCurrentImage();
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
      if ((overlayColorComboBox_.getSelectedItem()).equals("Black")) {
         overlayColor_ = Color.black;
      } else if ((overlayColorComboBox_.getSelectedItem()).equals("White")) {
         overlayColor_ = Color.white;
      } else if ((overlayColorComboBox_.getSelectedItem()).equals("Yellow")) {
         overlayColor_ = Color.yellow;
      } else if ((overlayColorComboBox_.getSelectedItem()).equals("Gray")) {
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
      int mode;
      int state = displayModeCombo_.getSelectedIndex();
      if (state == 0) {
         mode = CompositeImage.COMPOSITE;
      } else if (state == 1) {
         mode = CompositeImage.COLOR;
      } else {
         mode = CompositeImage.GRAYSCALE;
      }
      ImagePlus imgp = mdPanel_.getCurrentImage();
      if (imgp instanceof CompositeImage) {
         CompositeImage ci = (CompositeImage) imgp;
         ci.setMode(mode);
         ci.updateAndDraw();
         mdPanel_.drawWithoutUpdate();
      }
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

   public void calcAndDisplayHistAndStats(ImagePlus img, boolean drawHist) {
      if (currentHistograms_ != null) {
         currentHistograms_.calcAndDisplayHistAndStats(img, drawHist);
      }
   }

   public void applyLUTToImage(ImagePlus img, ImageCache cache) {
      if (currentHistograms_ != null) {
         currentHistograms_.applyLUTToImage(img, cache);
      }
   }

   public void imageChanged(ImagePlus img, ImageCache cache, boolean drawHist) {
      boolean slowHistUpdate = true;
      if (slowHistCheckBox_.isSelected()) {
         long time = System.currentTimeMillis();
         if (time - lastUpdateTime_ < SLOW_HIST_UPDATE_INTERVAL_MS) {
            slowHistUpdate = false;
         } else {
            lastUpdateTime_ = time;
         }
      }

      if (currentHistograms_ != null) {
         currentHistograms_.imageChanged(img, cache, drawHist, slowHistUpdate);
      }


   }

   public void displayChanged(ImagePlus img, ImageCache cache) {
      if (currentHistograms_ != null) {
         currentHistograms_.displayChanged(img, cache);
      }
      loadControlsStates();
   }

   public boolean getSyncChannels() {
      return syncChannelsCheckBox_.isSelected();
   }

   public boolean getSlowHist() {
      return slowHistCheckBox_.isSelected();
   }

   public boolean getAutostretch() {
      return autostretchCheckBox_.isSelected();
   }

   public boolean getRejectOutliers() {
      return rejectOutliersCheckBox_.isSelected();
   }

   public double getIgnorePercent() {
      try {
         double value = 0.01 * NumberUtils.displayStringToDouble(this.rejectPercentSpinner_.getValue().toString());
         return value;
      } catch (Exception e) {
         return 0;
      }
   }

   public boolean getLogHist() {
      return logHistCheckBox_.isSelected();
   }

   public void disableAutostretch() {
      autostretchCheckBox_.setSelected(false);
   }
}
