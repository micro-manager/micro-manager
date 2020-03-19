///////////////////////////////////////////////////////////////////////////////
//FILE:          MetadataPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, & Arthur Edelstein, 2010
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
package org.micromanager.magellan.internal.imagedisplay;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import org.micromanager.magellan.internal.imagedisplay.MagellanDisplayController;
import org.micromanager.magellan.internal.imagedisplay.events.ContrastUpdatedEvent;
import org.micromanager.magellan.internal.imagedisplay.HistogramPanel.CursorListener;
import org.micromanager.magellan.internal.misc.HistogramUtils;

/**
 * Draws one histogram of the Multi-Channel control panel
 *
 *
 */
class ChannelControlPanel extends JPanel implements CursorListener {

   private static final Dimension CONTROLS_SIZE = new Dimension(130, 150);
   public static final Dimension MINIMUM_SIZE = new Dimension(400, CONTROLS_SIZE.height);

   private final int channelIndex_;
   private HistogramPanel hp_;
   private MultiChannelHistograms mcHistograms_;
   private MagellanDisplayController display_;
   private JButton autoButton_;
   private JCheckBox channelNameCheckbox_;
   private JLabel colorPickerLabel_;
   private JButton fullButton_;
   private JPanel histogramPanelHolder_;
   private JLabel minMaxLabel_;
   private double binSize_;
   private int height_;
   private String histMaxLabel_;
   private int histMax_;
   private JPanel controls_;
   private JPanel controlsHolderPanel_;
   final private int maxIntensity_;
   final private int bitDepth_;
   private Color color_;
   private ContrastPanel contrastPanel_;
   private final String channelName_;
   private int pixelMin_, pixelMax_;

   public ChannelControlPanel(int channelIndex, MultiChannelHistograms mcHistograms, MagellanDisplayController disp,
           ContrastPanel contrastPanel, String name, Color color, int bitDepth) {
      channelName_ = name;
      contrastPanel_ = contrastPanel;
      display_ = disp;
      disp.registerForEvents(this);
      color_ = color;
      bitDepth_ = bitDepth;
      maxIntensity_ = (int) Math.pow(2, bitDepth_) - 1;
      histMax_ = maxIntensity_ + 1;
      binSize_ = histMax_ / DisplaySettings.NUM_DISPLAY_HIST_BINS;
      histMaxLabel_ = "" + histMax_;
      mcHistograms_ = mcHistograms;
      channelIndex_ = channelIndex;
      initComponents();
      channelNameCheckbox_.setSelected(display_.getDisplaySettings().isActive(channelName_));
      redraw();
   }

   private void initComponents() {

      fullButton_ = new javax.swing.JButton();
      autoButton_ = new javax.swing.JButton();
      colorPickerLabel_ = new javax.swing.JLabel();
      channelNameCheckbox_ = new javax.swing.JCheckBox();
      histogramPanelHolder_ = new javax.swing.JPanel();
      minMaxLabel_ = new javax.swing.JLabel();

      setOpaque(false);
      setPreferredSize(new java.awt.Dimension(250, height_));

      fullButton_.setFont(fullButton_.getFont().deriveFont((float) 9));
      fullButton_.setName("Full channel histogram width");
      fullButton_.setText("Full");
      fullButton_.setToolTipText("Stretch the display gamma curve over the full pixel range");
      fullButton_.setMargin(new java.awt.Insets(2, 4, 2, 4));
      fullButton_.setPreferredSize(new java.awt.Dimension(75, 30));
      fullButton_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fullButtonAction();
         }
      });

      autoButton_.setFont(autoButton_.getFont().deriveFont((float) 9));
      autoButton_.setName("Auto channel histogram width");
      autoButton_.setText("Auto");
      autoButton_.setToolTipText("Align the display gamma curve with minimum and maximum measured intensity values");
      autoButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      autoButton_.setIconTextGap(0);
      autoButton_.setMargin(new java.awt.Insets(2, 4, 2, 4));
      autoButton_.setMaximumSize(new java.awt.Dimension(75, 30));
      autoButton_.setMinimumSize(new java.awt.Dimension(75, 30));
      autoButton_.setPreferredSize(new java.awt.Dimension(75, 30));
      autoButton_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            autoButtonAction();
         }
      });

      colorPickerLabel_.setBackground(color_);
      colorPickerLabel_.setToolTipText("Change the color for displaying this channel");
      colorPickerLabel_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
      colorPickerLabel_.setOpaque(true);
      colorPickerLabel_.addMouseListener(new java.awt.event.MouseAdapter() {

         @Override
         public void mouseClicked(java.awt.event.MouseEvent evt) {
            colorPickerLabelMouseClicked();
         }
      });

      channelNameCheckbox_.setText(channelName_);
      channelNameCheckbox_.setToolTipText("Show/hide this channel in the multi-dimensional viewer");
      channelNameCheckbox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            channelNameCheckboxAction();
         }
      });

      histogramPanelHolder_.setToolTipText("Adjust the brightness and contrast by dragging triangles at top and bottom. Change the gamma by dragging the curve. (These controls only change display, and do not edit the image data.)");
      histogramPanelHolder_.setAlignmentX(0.3F);
      histogramPanelHolder_.setPreferredSize(new java.awt.Dimension(0, 100));
      histogramPanelHolder_.setLayout(new BorderLayout());

      minMaxLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      minMaxLabel_.setText("Min:   Max:");


      this.setMinimumSize(MINIMUM_SIZE);
      this.setPreferredSize(MINIMUM_SIZE);

      hp_ = addHistogramPanel();

      this.setLayout(new BorderLayout());
      controlsHolderPanel_ = new JPanel(new BorderLayout());
      controlsHolderPanel_.setPreferredSize(CONTROLS_SIZE);

      controls_ = new JPanel();
      this.add(controlsHolderPanel_, BorderLayout.LINE_START);
      this.add(histogramPanelHolder_, BorderLayout.CENTER);

      controlsHolderPanel_.add(controls_, BorderLayout.PAGE_START);
      GridBagLayout gbl = new GridBagLayout();
      controls_.setLayout(gbl);

      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = 5;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls_.add(channelNameCheckbox_, gbc);

      fullButton_.setPreferredSize(new Dimension(45, 20));
      autoButton_.setPreferredSize(new Dimension(45, 20));
      colorPickerLabel_.setPreferredSize(new Dimension(18, 18));
      FlowLayout flow = new FlowLayout();
      flow.setHgap(4);
      flow.setVgap(0);
      JPanel line2 = new JPanel(flow);
      line2.setPreferredSize(CONTROLS_SIZE);
      line2.add(fullButton_);
      line2.add(autoButton_);
      line2.add(colorPickerLabel_);
      line2.setPreferredSize(new Dimension(CONTROLS_SIZE.width, 20));

      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 1;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(line2, gbc);

      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 4;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls_.add(minMaxLabel_, gbc);

      controls_.setPreferredSize(controls_.getMinimumSize());
   }

   public String getChannelName() {
      return channelName_;
   }

   private void redraw() {
      int contrastMin = display_.getDisplaySettings().getContrastMin(channelName_);
      int contrastMax = display_.getDisplaySettings().getContrastMax(channelName_);
      double gamma = display_.getDisplaySettings().getContrastGamma(channelName_);

      hp_.setCursorText(contrastMin + "", contrastMax + "");
      hp_.setCursors(contrastMin / binSize_, (contrastMax + 1) / binSize_, gamma);
      hp_.repaint();
   }

   private void fullButtonAction() {
      display_.getDisplaySettings().setContrastMin(channelName_, 0);
      display_.getDisplaySettings().setContrastMax(channelName_, (int) (Math.pow(2, bitDepth_) - 1));

      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
   }

   public void autoButtonAction() {
      display_.getDisplaySettings().setContrastMin(channelName_, pixelMin_);
      display_.getDisplaySettings().setContrastMax(channelName_, pixelMax_);

      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      redraw();

   }

   private void colorPickerLabelMouseClicked() {
      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + channelName_ + " channel", color_);
      if (newColor != null) {
         color_ = newColor;
      }
      colorPickerLabel_.setBackground(color_);
      hp_.setTraceStyle(true, color_);
      String name = channelName_;
      if (channelName_.length() > 11) {
         name = name.substring(0, 9) + "...";
      }
      channelNameCheckbox_.setText(name);
      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      this.repaint();
   }

   /*
    * save color to preferences, but only if this is multicamera.  Since
    * we cannot check for this directly from metadata,this performs a series of
    * checks to error on the side of not saving the preference
    */
   private void saveColorPreference(MagellanImageCache cache, int color) {
//      CMMCore core = Magellan.getCore();
//      JSONObject summary = cache.getSummaryMetadata();
//      if (core == null || summary == null) {
//         return;
//      }
//      int numMultiCamChannels = (int) core.getNumberOfCameraChannels();
//      int numDataChannels;
//      JSONArray dataNames;
//      String[] cameraNames = new String[numMultiCamChannels];
//      try {
//         numDataChannels = MD.getNumChannels(summary);
//         dataNames = summary.getJSONArray("ChNames");
//         for (int i = 0; i < numMultiCamChannels; i++) {
//            cameraNames[i] = core.getCameraChannelName(i);
//         }
//
//         if (numMultiCamChannels > 1 && numDataChannels == numMultiCamChannels
//                 && cameraNames.length == dataNames.length()) {
//            for (int h = 0; h < cameraNames.length; h++) {
//               if (!cameraNames[h].equals(dataNames.getString(h))) {
//                  return;
//               }
//            }
//            GlobalSettings.getInstance().storeIntInPrefs("Color_Camera_" + cameraNames[channelIndex_], color);
//         }
//      } catch (Exception ex) {
//
//      }
   }

   private void channelNameCheckboxAction() {
      display_.channelSetActive(channelIndex_, channelNameCheckbox_.isSelected());
      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      redraw();

   }

   private HistogramPanel addHistogramPanel() {
      HistogramPanel hp = new HistogramPanel() {

         @Override
         public void paint(Graphics g) {
            if (channelNameCheckbox_.isSelected()) {
               super.paint(g);
               //For drawing max label
               g.setColor(Color.black);
               g.setFont(new Font("Lucida Grande", 0, 10));
               g.drawString(histMaxLabel_, this.getSize().width - 8 * histMaxLabel_.length(), this.getSize().height);
            }
         }
      };
      hp.setMargins(12, 12);
      hp.setTraceStyle(true, color_);
      hp.setToolTipText("Click and drag curve to adjust gamma");
      histogramPanelHolder_.add(hp, BorderLayout.CENTER);
      hp.addCursorListener(this);
      return hp;
   }

   public int getChannelIndex() {
      return channelIndex_;
   }

   public void updateHistogram(int[] histogram, int pixelMin, int pixelMax) {
      pixelMin_ = pixelMin;
      pixelMax_ = pixelMax;
      hp_.setVisible(true);
      //Draw histogram and stats
      GraphData histogramData = new GraphData();
      histogramData.setData(histogram);
      hp_.setData(histogramData);
      hp_.setAutoScale();
      hp_.repaint();
      minMaxLabel_.setText("Min: " + pixelMin + "   " + "Max: " + pixelMax);
      redraw();

   }

   public void contrastMaxInput(int max) {
      contrastPanel_.disableAutostretch();
      display_.getDisplaySettings().setContrastMax(channelName_, max);
      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      redraw();

   }

   @Override
   public void contrastMinInput(int min) {
      contrastPanel_.disableAutostretch();
      display_.getDisplaySettings().setContrastMax(channelName_, min);
      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      redraw();

   }

   public void onLeftCursor(double pos) {
      contrastPanel_.disableAutostretch();
      display_.getDisplaySettings().setContrastMin(channelName_, (int) (Math.min(DisplaySettings.NUM_DISPLAY_HIST_BINS - 1, pos) * binSize_));
      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      redraw();
   }

   @Override
   public void onRightCursor(double pos) {
      contrastPanel_.disableAutostretch();
      display_.getDisplaySettings().setContrastMax(channelName_, (int) (Math.min(DisplaySettings.NUM_DISPLAY_HIST_BINS - 1, pos) * binSize_));
      display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      redraw();

   }

   @Override
   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1) {
            gamma = 1;
         }
         display_.getDisplaySettings().setGamma(channelName_, gamma);
         display_.postEvent(new ContrastUpdatedEvent(channelIndex_));
      }
      redraw();
   }

   void updateActiveCheckbox(boolean active) {
      channelNameCheckbox_.setSelected(active);
      redraw();
   }

}
