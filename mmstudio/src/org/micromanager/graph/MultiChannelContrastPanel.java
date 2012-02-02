/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.graph;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.json.JSONException;
import org.micromanager.acquisition.MetadataPanel;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.ContrastPanel;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.ScaleBar;

/**
 *
 * @author Henry
 */
public class MultiChannelContrastPanel extends JPanel implements ContrastPanel {

   private static final String PREF_AUTOSTRETCH = "mc_stretch_contrast";
   private static final String PREF_REJECT_OUTLIERS = "mc_reject_outliers";
   private static final String PREF_REJECT_FRACTION = "mc_reject_fraction";
   private static final String PREF_LOG_HIST = "mc_log_hist";
   
   
   private JScrollPane contrastScrollPane_;
   JComboBox displayModeCombo_;
   JCheckBox autostretchCheckbox_;
   JCheckBox rejectOutliersCB_;
   JSpinner rejectPercentSpinner_;
   JCheckBox logScaleCheckBox_;
   JCheckBox sizeBarCheckBox_;
   JComboBox sizeBarComboBox_;
   JComboBox overlayColorComboBox_;
   JCheckBox syncChannelsCheckbox_;
   private MetadataPanel mdPanel_;
   private ArrayList<ChannelControlPanel> ccpList_;
   private Preferences prefs_;
   
   private Color overlayColor_ = Color.white;


   public MultiChannelContrastPanel(MetadataPanel md) {
      mdPanel_ = md;
      initialize();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      loadSettings();     
   }
   
   private void saveSettings() {
      prefs_.putBoolean(PREF_AUTOSTRETCH, autostretchCheckbox_.isSelected());
      prefs_.putBoolean(PREF_LOG_HIST, logScaleCheckBox_.isSelected());
      prefs_.putDouble(PREF_REJECT_FRACTION, (Double) rejectPercentSpinner_.getValue());
      prefs_.putBoolean(PREF_REJECT_OUTLIERS, rejectOutliersCB_.isSelected());
   }
   
   private void loadSettings() {
      autostretchCheckbox_.setSelected(prefs_.getBoolean(PREF_AUTOSTRETCH, false));           
      logScaleCheckBox_.setSelected(prefs_.getBoolean(PREF_LOG_HIST, false));
      rejectOutliersCB_.setSelected(prefs_.getBoolean(PREF_REJECT_OUTLIERS, false));
      rejectPercentSpinner_.setValue(prefs_.getDouble(PREF_REJECT_FRACTION, 0.02));
      if (!autostretchCheckbox_.isSelected()) {
         rejectOutliersCB_.setEnabled(false);
         rejectPercentSpinner_.setEnabled(false);
      } else if (rejectOutliersCB_.isSelected())
         rejectPercentSpinner_.setEnabled(true);
      
   }

   private void initialize() {

      JPanel jPanel1 = new JPanel();
      JLabel displayModeLabel = new JLabel();
      displayModeCombo_ = new JComboBox();
      autostretchCheckbox_ = new JCheckBox();
      rejectOutliersCB_ = new JCheckBox();
      rejectPercentSpinner_ = new JSpinner();
      logScaleCheckBox_ = new JCheckBox();
      sizeBarCheckBox_ = new JCheckBox();
      sizeBarComboBox_ = new JComboBox();
      overlayColorComboBox_ = new JComboBox();
      contrastScrollPane_ = new JScrollPane();


      this.setPreferredSize(new Dimension(400, 594));

      displayModeCombo_.setModel(new DefaultComboBoxModel(new String[]{"Composite", "Color", "Grayscale"}));
      displayModeCombo_.setToolTipText("<html>Choose display mode:<br> - Composite = Multicolor overlay<br> - Color = Single channel color view<br> - Grayscale = Single channel grayscale view</li></ul></html>");
      displayModeCombo_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            displayModeComboActionPerformed();
         }});
      displayModeLabel.setText("Display mode:");

      autostretchCheckbox_.setText("Autostretch");
      autostretchCheckbox_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent evt) {
            autostretchCheckBoxStateChanged();
         }});
      rejectOutliersCB_.setText("ignore %");
      rejectOutliersCB_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            rejectOutliersCB_ActionPerformed();
         }});

      rejectPercentSpinner_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      rejectPercentSpinner_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent evt) {
            rejectPercentSpinner_StateChanged();
         }});
      rejectPercentSpinner_.addKeyListener(new java.awt.event.KeyAdapter() {
         public void keyPressed(java.awt.event.KeyEvent evt) {
            rejectPercentSpinner_StateChanged();
         }});
      rejectPercentSpinner_.setModel(new SpinnerNumberModel( 0.02  , 0., 100., 0.1));


      logScaleCheckBox_.setText("Log hist");
      logScaleCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            logScaleCheckBoxActionPerformed();
         }});

      org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
              jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(jPanel1Layout.createSequentialGroup().addContainerGap(24, Short.MAX_VALUE).add(displayModeLabel).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(displayModeCombo_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(autostretchCheckbox_).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(rejectOutliersCB_).add(6, 6, 6).add(rejectPercentSpinner_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 63, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(logScaleCheckBox_)));
      jPanel1Layout.setVerticalGroup(
              jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER).add(autostretchCheckbox_).add(rejectOutliersCB_).add(rejectPercentSpinner_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(logScaleCheckBox_)).add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE).add(displayModeCombo_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).add(displayModeLabel)));

      sizeBarCheckBox_.setText("Scale Bar");
      sizeBarCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarCheckBoxActionPerformed();
         }});

      sizeBarComboBox_.setModel(new DefaultComboBoxModel(new String[]{"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"}));
      sizeBarComboBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sizeBarComboBoxActionPerformed();
         }});

      overlayColorComboBox_.setModel(new DefaultComboBoxModel(new String[]{"White", "Black", "Yellow", "Gray"}));
      overlayColorComboBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            overlayColorComboBox_ActionPerformed();
         }});
      
      syncChannelsCheckbox_ = new JCheckBox("Sync channel contrast");
      syncChannelsCheckbox_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            syncChannelsCheckboxAction();
         }});

      org.jdesktop.layout.GroupLayout channelsTablePanel_Layout = new org.jdesktop.layout.GroupLayout(this);
      this.setLayout(channelsTablePanel_Layout);
      channelsTablePanel_Layout.setHorizontalGroup(
              channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
              .add(channelsTablePanel_Layout.createSequentialGroup().add(sizeBarCheckBox_)
              .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
              .add(sizeBarComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
              .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
              .add(overlayColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
//               .add(syncChannelsCheckbox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
              )
              .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
              .add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(contrastScrollPane_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)));
      
      channelsTablePanel_Layout.setVerticalGroup(
              channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
              .add(channelsTablePanel_Layout.createSequentialGroup()
              .add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE).add(sizeBarCheckBox_)
              .add(sizeBarComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
              .add(overlayColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
//              .add(syncChannelsCheckbox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE) 
              )
              .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED).add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(589, Short.MAX_VALUE))
              .add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
              .add(channelsTablePanel_Layout.createSequentialGroup().add(79, 79, 79).add(contrastScrollPane_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 571, Short.MAX_VALUE))));
   }

   public synchronized void setupChannelControls(ImageCache cache) {
      final int nChannels;
      boolean rgb;
      try {
         rgb = MDUtils.isRGB(cache.getSummaryMetadata());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         rgb = false;
      }
      if (rgb)
         nChannels = 3;
      else
         nChannels = cache.getNumChannels();

      final SpringLayout layout = new SpringLayout();
      final JPanel p = new JPanel() {
         @Override
         public void paint(Graphics g) {
            int channelHeight = Math.max(115, contrastScrollPane_.getViewport().getSize().height / nChannels);
            this.setPreferredSize(new Dimension(this.getSize().width, channelHeight * nChannels));
            if (ccpList_ != null) {
               for (int i = 0; i < ccpList_.size(); i++) {
                  ccpList_.get(i).setHeight(channelHeight);
                  ccpList_.get(i).setLocation(0, channelHeight * i);
               }}
            super.paint(g);
         }};

      int hpHeight = Math.max(115, (contrastScrollPane_.getSize().height - 2) / nChannels);
      p.setPreferredSize(new Dimension(200, nChannels * hpHeight));
      contrastScrollPane_.setViewportView(p);

      p.setLayout(layout);
      ccpList_ = new ArrayList<ChannelControlPanel>();
      for (int i = 0; i < nChannels; ++i) {
         ChannelControlPanel ccp = new ChannelControlPanel(i, this, mdPanel_, hpHeight,
                 cache.getChannelColor(i) ,cache.getBitDepth(),getFractionToReject(),
                 logScaleCheckBox_.isSelected());
         layout.putConstraint(SpringLayout.EAST, ccp, 0, SpringLayout.EAST, p);
         layout.putConstraint(SpringLayout.WEST, ccp, 0, SpringLayout.WEST, p);
         p.add(ccp);
         ccpList_.add(ccp);
      }

      layout.putConstraint(SpringLayout.NORTH, ccpList_.get(0), 0, SpringLayout.NORTH, p);
      layout.putConstraint(SpringLayout.SOUTH, ccpList_.get(nChannels - 1), 0, SpringLayout.SOUTH, p);
      for (int i = 1; i < ccpList_.size(); i++) {
         layout.putConstraint(SpringLayout.NORTH, ccpList_.get(i), 0, SpringLayout.SOUTH, ccpList_.get(i - 1));
      }


   }

   public void setDisplayMode(int mode) {
      if (mode == CompositeImage.COMPOSITE) 
         displayModeCombo_.setSelectedIndex(0);
      else if (mode == CompositeImage.COLOR) 
         displayModeCombo_.setSelectedIndex(1);
      else if (mode == CompositeImage.GRAYSCALE)
         displayModeCombo_.setSelectedIndex(2);  
   }
   
   private void showSizeBar() {
      boolean show = sizeBarCheckBox_.isSelected();
      ImagePlus ip = WindowManager.getCurrentImage();
      if (show) {
         ScaleBar sizeBar = new ScaleBar(ip);

         if (sizeBar != null) {
            Overlay ol = new Overlay();
            //ol.setFillColor(Color.white); // this causes the text to get a white background!
            ol.setStrokeColor(overlayColor_);
            String selected = (String) sizeBarComboBox_.getSelectedItem();
            if (selected.equals("Top-Right")) 
               sizeBar.setPosition(ScaleBar.Position.TOPRIGHT);
            if (selected.equals("Top-Left")) 
               sizeBar.setPosition(ScaleBar.Position.TOPLEFT);
            if (selected.equals("Bottom-Right")) 
               sizeBar.setPosition(ScaleBar.Position.BOTTOMRIGHT);
            if (selected.equals("Bottom-Left")) 
               sizeBar.setPosition(ScaleBar.Position.BOTTOMLEFT);
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
      
   }
   
   public double getFractionToReject() {
      try {
         double value = 0.01 * NumberUtils.displayStringToDouble(this.rejectPercentSpinner_.getValue().toString());
         return value;
      } catch (Exception e) {
         return 0;
      }
   }

   public void displayModeComboActionPerformed() {
      int mode;
      int state = displayModeCombo_.getSelectedIndex();
      if (state == 0)
         mode = CompositeImage.COMPOSITE;
      else if (state == 1)
         mode = CompositeImage.COLOR;
      else
         mode = CompositeImage.GRAYSCALE;
      ImagePlus imgp = WindowManager.getCurrentImage();
      if (imgp instanceof CompositeImage) {
         CompositeImage ci = (CompositeImage) imgp;
         ci.setMode(mode);
         ci.updateAndDraw();
         mdPanel_.drawWithoutUpdate();
      }
      saveSettings();
   }

   private void autostretchCheckBoxStateChanged() {
       rejectOutliersCB_.setEnabled(autostretchCheckbox_.isSelected());
       boolean rejectem = rejectOutliersCB_.isSelected() && autostretchCheckbox_.isSelected();
       rejectPercentSpinner_.setEnabled(rejectem);
       if (autostretchCheckbox_.isSelected())
          if (ccpList_ != null && ccpList_.size() > 0)
             for (ChannelControlPanel c : ccpList_)
                c.autoButtonAction();
   }

   private void rejectOutliersCB_ActionPerformed() {
      rejectPercentSpinner_.setEnabled(rejectOutliersCB_.isSelected());
      if (ccpList_ != null && ccpList_.size() > 0)
         for (ChannelControlPanel c : ccpList_) {
            c.setFractionToReject(getFractionToReject());
            c.calcAndDisplayHistAndStats(WindowManager.getCurrentImage(),true);
            c.autoButtonAction();
         }
      saveSettings();
   }

   private void rejectPercentSpinner_StateChanged() {
     if (ccpList_ != null && ccpList_.size() > 0)
         for (ChannelControlPanel c : ccpList_) {
            c.setFractionToReject(getFractionToReject());
            c.calcAndDisplayHistAndStats(WindowManager.getCurrentImage(),true);
            c.autoButtonAction();
         }
      saveSettings();
   }

   private void logScaleCheckBoxActionPerformed() {
        if (ccpList_ != null && ccpList_.size() > 0)
         for (ChannelControlPanel c : ccpList_)
            c.setLogScale(logScaleCheckBox_.isSelected());
        
      saveSettings();
   }

   public void sizeBarCheckBoxActionPerformed() {
      showSizeBar();
   }

   private void sizeBarComboBoxActionPerformed() {
       showSizeBar();
   }
   
   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex >= ccpList_.size())
         return;
      ccpList_.get(channelIndex).setContrast(min, max, gamma);
   }

   public void autostretch() {
      if (ccpList_ != null)   {
         for (ChannelControlPanel c : ccpList_) {
            c.autostretch();
         }
      }
   }
   
   public void calcAndDisplayHistAndStats(ImagePlus img,boolean drawHist) {
       if (ccpList_ != null)   {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(img,drawHist);
         }
      }
   }
 
  public void applyLUTToImage(ImagePlus img, ImageCache cache) {
     if (ccpList_ == null)
        return;
     for (ChannelControlPanel c : ccpList_)
         c.applyChannelLUTToImage(img, cache);
  }
   
   public void imageChanged(ImagePlus img, ImageCache cache, boolean drawHist) {
      if (ccpList_ == null)
         return;
      for (ChannelControlPanel c : ccpList_) {
         c.calcAndDisplayHistAndStats(img,drawHist);
         if (autostretchCheckbox_.isSelected())
            c.autostretch();        
         c.applyChannelLUTToImage(img,cache);
      }
   }
   
    public void displayChanged(ImagePlus img, ImageCache cache) {
       for (ChannelControlPanel c : ccpList_) {
          c.calcAndDisplayHistAndStats(img, true);
          if (autostretchCheckbox_.isSelected())
             c.autostretch();
          else
             c.loadDisplaySettings(cache);
       }
       mdPanel_.drawWithoutUpdate();
   }

   public boolean getAutoStretch() {
      return autostretchCheckbox_.isSelected();
   }

   public boolean getSlowHist() {
      //Multi channel contrast panel doesn't have this feature...yet
      return false;
   }

   public boolean getLogHist() {
      return logScaleCheckBox_.isSelected();
   }

   public boolean getRejectOutliers() {
      return rejectOutliersCB_.isSelected();
   }
}
