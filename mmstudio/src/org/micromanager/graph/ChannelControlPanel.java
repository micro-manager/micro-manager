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
package org.micromanager.graph;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.prefs.Preferences;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.AcqControlDlg;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.MetadataPanel;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.graph.GraphData;
import org.micromanager.graph.HistogramPanel;
import org.micromanager.graph.HistogramPanel.CursorListener;
import org.micromanager.utils.HistogramUtils;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

public class ChannelControlPanel extends JPanel implements CursorListener {

   private static final Dimension CONTROLS_SIZE = new Dimension(130, 115);
   public static final Dimension MINIMUM_SIZE = new Dimension(400,CONTROLS_SIZE.height);
   
   private static final int NUM_BINS = 256;
   private static final double BIN_SIZE_MIN = 1.0 / 8;
   private final int BIN_SIZE_MAX;
   private final int channelIndex_;
   private HistogramPanel hp_;
   private final MultiChannelContrastPanel mccPanel_;
   private final MetadataPanel mdPanel_;
   private JButton autoButton_;
   private JCheckBox channelNameCheckbox_;
   private JLabel colorPickerLabel_;
   private JButton fullButton_;
   private JPanel histogramPanelHolder_;
   private JLabel minMaxLabel_;
   private JComboBox modeComboBox_;
   private double binSize_;
   private boolean logScale_;
   private double fractionToReject_;
   private int height_;
   private String histMaxLabel_;
   private int histMax_;
   private JPanel controls_;
   private JPanel controlsHolderPanel_;
   private int contrastMin_;
   private int contrastMax_;
   private double gamma_ = 1;
   private int minAfterRejectingOutliers_;
   private int maxAfterRejectingOutliers_;
   private int pixelMin_ = 0;
   private int pixelMax_ = 255;
   final private int maxIntensity_;
   private Color color_;

   public ChannelControlPanel(int channelIndex, MultiChannelContrastPanel mccPanel, MetadataPanel md, ImageCache cache,
           Color color, int bitDepth, double fractionToReject, boolean logScale) {
      fractionToReject_ = fractionToReject;
      logScale_ = logScale;
      color_ = color;
      maxIntensity_ = (int) Math.pow(2, bitDepth) - 1;
      histMax_ = maxIntensity_ + 1;
      binSize_ = histMax_ / NUM_BINS;
      BIN_SIZE_MAX = (int) (Math.pow(2, bitDepth) / NUM_BINS);
      histMaxLabel_ = "" + histMax_;
      mdPanel_ = md;
      initComponents();
      channelIndex_ = channelIndex;
      mccPanel_ = mccPanel;
      loadDisplaySettings(cache);
      updateChannelNameAndColor(cache);
      cache.setChannelColor(channelIndex_, color_.getRGB());
      
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
      fullButton_.setText("Full");
      fullButton_.setToolTipText("Stretch the display gamma curve over the full pixel range");
      fullButton_.setMargin(new java.awt.Insets(2, 4, 2, 4));
      fullButton_.setPreferredSize(new java.awt.Dimension(75, 30));
      fullButton_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fullButtonAction();
         }
      });

      autoButton_.setFont(autoButton_.getFont().deriveFont((float) 9));
      autoButton_.setText("Auto");
      autoButton_.setToolTipText("Align the display gamma curve with minimum and maximum measured intensity values");
      autoButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      autoButton_.setIconTextGap(0);
      autoButton_.setMargin(new java.awt.Insets(2, 4, 2, 4));
      autoButton_.setMaximumSize(new java.awt.Dimension(75, 30));
      autoButton_.setMinimumSize(new java.awt.Dimension(75, 30));
      autoButton_.setPreferredSize(new java.awt.Dimension(75, 30));
      autoButton_.addActionListener(new java.awt.event.ActionListener() {

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            autoButtonAction();
         }
      });

      colorPickerLabel_.setBackground(color_);
      colorPickerLabel_.setToolTipText("Change the color for displaying this channel");
      colorPickerLabel_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
      colorPickerLabel_.setOpaque(true);
      colorPickerLabel_.addMouseListener(new java.awt.event.MouseAdapter() {

         public void mouseClicked(java.awt.event.MouseEvent evt) {
            colorPickerLabelMouseClicked();
         }
      });

      channelNameCheckbox_.setSelected(true);
      channelNameCheckbox_.setText("Channel");
      channelNameCheckbox_.setToolTipText("Show/hide this channel in the multi-dimensional viewer");
      channelNameCheckbox_.addActionListener(new java.awt.event.ActionListener() {

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



      modeComboBox_ = new JComboBox();
      modeComboBox_.setFont(new Font("", Font.PLAIN, 10));
      modeComboBox_.addActionListener(new ActionListener() {

         public void actionPerformed(final ActionEvent e) {
            if (mccPanel_.syncedChannels()) {
               mccPanel_.updateOtherDisplayCombos(modeComboBox_.getSelectedIndex());
            }
            displayComboAction();
         }
      });
      modeComboBox_.setModel(new DefaultComboBoxModel(new String[]{
                 "Auto", "4bit (0-31)", "6bit (0-127)", "8bit (0-255)", "10bit (0-1023)", 
                 "12bit (0-4095)", "14bit (0-16383)", "16bit (0-65535)"}));

      
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

      JLabel comboLabel = new JLabel("Histogram range:");
      comboLabel.setFont(new Font("Lucida Grande", 0, 11));

      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = 5;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(channelNameCheckbox_, gbc);      
      
      fullButton_.setPreferredSize(new Dimension(45, 20));
      autoButton_.setPreferredSize(new Dimension(45, 20));
      colorPickerLabel_.setPreferredSize(new Dimension(20,20));
      colorPickerLabel_.setMaximumSize(new Dimension(20,20));
      FlowLayout flow = new FlowLayout();
      flow.setHgap(4);
      flow.setVgap(0);
      JPanel line2 = new JPanel(flow);
      line2.setPreferredSize(CONTROLS_SIZE);
      line2.add(fullButton_);
      line2.add(autoButton_);
      line2.add(colorPickerLabel_);
      line2.setPreferredSize(new Dimension(CONTROLS_SIZE.width,20));
      
      
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 1;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 4;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(line2,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 2;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(comboLabel, gbc);

      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 3;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      modeComboBox_.setPreferredSize(new Dimension(CONTROLS_SIZE.width, 20));
      controls_.add(modeComboBox_, gbc);

      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 4;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(minMaxLabel_, gbc);

      controls_.setPreferredSize(controls_.getMinimumSize());
   }

   
   public void setDisplayComboIndex(int index) {
      modeComboBox_.setSelectedIndex(index);
   }
   
   public int getDisplayComboIndex() {
      return modeComboBox_.getSelectedIndex();
   }
   
   public void displayComboAction() {
      switch (modeComboBox_.getSelectedIndex() - 1) {
         case -1:
            histMax_ = maxIntensity_;
            break;
         case 0:
            histMax_ = 31;
            break;
         case 1:
            histMax_ = 127;
            break;
         case 2:
            histMax_ = 255;
            break;
         case 3:
            histMax_ = 1023;
            break;
         case 4:
            histMax_ = 4095;
            break;
         case 5:
            histMax_ = 16383;
            break;
         case 6:
            histMax_ = 65535;
            break;
         default:
            break;
      }    
      binSize_ = ((double)(histMax_ + 1)) / ((double)NUM_BINS);
      histMaxLabel_ = histMax_ + "";
      updateHistogram();
      ImagePlus img = mdPanel_.getCurrentImage();
      if (img != null) {
         calcAndDisplayHistAndStats(img, true);
      }
   }

   private void updateHistogram() {
      hp_.setCursors(contrastMin_ / binSize_, contrastMax_ / binSize_, gamma_);
      hp_.repaint();
   }

   private void fullButtonAction() {
      if (mccPanel_.syncedChannels()) {
         mccPanel_.fullScaleChannels();
      } else {
         setFullScale();
         mdPanel_.drawWithoutUpdate();
      }
   }

   public void autoButtonAction() {
      if (mccPanel_.syncedChannels()) {
         autostretch();
         mccPanel_.applyContrastToAllChannels(contrastMin_, contrastMax_, gamma_);
      } else {
         autostretch();
         mdPanel_.drawWithoutUpdate();
      }
   }

   private void colorPickerLabelMouseClicked() {
      //Can only edit color in this way if there is an active window
      //so it is ok to get image cache in this way
      ImageCache cache = VirtualAcquisitionDisplay.getDisplay(mdPanel_.getCurrentImage()).getImageCache();
      String name = "selected";
      if (cache.getChannelName(channelIndex_) != null) {
         name = cache.getChannelName(channelIndex_);
      }
      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + name + " channel", cache.getChannelColor(channelIndex_));
      if (newColor != null) {
         cache.setChannelColor(channelIndex_, newColor.getRGB());
      }
      updateChannelNameAndColor(cache);

      //if multicamera, save color
      saveColorPreference(cache, newColor.getRGB());
   }

   /*
    * save color to preferences, but only if this is multicamera.  Since
    * we cannot check for this directly from metadata,this performs a series of
    * checks to error on the side of not saving the preference
    */
   private void saveColorPreference(ImageCache cache, int color) {
      CMMCore core = MMStudioMainFrame.getInstance().getCore();
      JSONObject summary = cache.getSummaryMetadata();
      if (core == null || summary == null) {
         return;
      }
      int numMultiCamChannels = (int) core.getNumberOfCameraChannels(), numDataChannels = -1;
      JSONArray dataNames = null;
      String[] cameraNames = new String[numMultiCamChannels];
      try {
         numDataChannels = MDUtils.getNumChannels(summary);
         dataNames = summary.getJSONArray("ChNames");
         for (int i = 0; i < numMultiCamChannels; i++) {
            cameraNames[i] = core.getCameraChannelName(i);
         }

         if (numMultiCamChannels > 1 && numDataChannels == numMultiCamChannels
                 && cameraNames.length == dataNames.length()) {
            for (int h = 0; h < cameraNames.length; h++) {
               if (!cameraNames[h].equals(dataNames.getString(h))) {
                  return;
               }
            }
            Preferences root = Preferences.userNodeForPackage(AcqControlDlg.class);
            Preferences colorPrefs = root.node(root.absolutePath() + "/" + AcqControlDlg.COLOR_SETTINGS_NODE);
            colorPrefs.putInt("Color_Camera_" + cameraNames[channelIndex_], color);
         }
      } catch (Exception ex) {
         return;
      }
   }

   private void channelNameCheckboxAction() {
      CompositeImage ci = (CompositeImage) mdPanel_.getCurrentImage();
      ImageCache cache = VirtualAcquisitionDisplay.getDisplay(ci).getImageCache();
      boolean[] active = ci.getActiveChannels();
      if (ci.getMode() != CompositeImage.COMPOSITE) {
         if (active[channelIndex_]) {
            channelNameCheckbox_.setSelected(true);
            return;
         } else {
            ci.setPosition(channelIndex_ + 1, ci.getSlice(), ci.getFrame());
         }
      }

      cache.setChannelVisibility(channelIndex_, channelNameCheckbox_.isSelected());
      mdPanel_.drawWithoutUpdate();
   }

   public void setFullScale() {
      mccPanel_.autostretchCheckbox_.setSelected(false);
      contrastMin_ = 0;
      contrastMax_ = histMax_;
   }

   public void autostretch() {
      contrastMin_ = pixelMin_;
      contrastMax_ = pixelMax_;
      if (pixelMin_ == pixelMax_) {
          if (pixelMax_ > 0) {
              contrastMin_--;
          } else {
              contrastMax_++;
          }
      }
      if (mccPanel_.rejectOutliersCB_.isSelected()) {
         if (contrastMin_ < minAfterRejectingOutliers_) {
            if (0 < minAfterRejectingOutliers_) {
               contrastMin_ = minAfterRejectingOutliers_;
            }
         }
         if (maxAfterRejectingOutliers_ < contrastMax_) {
            contrastMax_ = maxAfterRejectingOutliers_;
         }
         if (contrastMax_ <= contrastMin_) {
             if (contrastMax_ > 0) {
                 contrastMin_ = contrastMax_ - 1;
             } else {
                 contrastMax_ = contrastMin_ + 1;
             }
         }
      }
   }

   public void setFractionToReject(double frac) {
      fractionToReject_ = frac;
   }

   public void loadDisplaySettings(ImageCache cache) {
      contrastMax_ = cache.getChannelMax(channelIndex_);
      if (contrastMax_ < 0) {
         contrastMax_ = maxIntensity_;
      }
      contrastMin_ = cache.getChannelMin(channelIndex_);
      gamma_ = cache.getChannelGamma(channelIndex_);
   }

   public void setLogScale(boolean logScale) {
      logScale_ = logScale;
      calcAndDisplayHistAndStats(mdPanel_.getCurrentImage(), true);
   }

   private HistogramPanel addHistogramPanel() {
      HistogramPanel hp = new HistogramPanel() {

         @Override
         public void paint(Graphics g) {
            super.paint(g);
            //For drawing max label
            g.setColor(Color.black);
            g.setFont(new Font("Lucida Grande", 0, 10));
            g.drawString(histMaxLabel_, this.getSize().width - 7 * histMaxLabel_.length(), this.getSize().height);
         }
      };
      hp.setMargins(8, 12);
      hp.setTextVisible(false);
      hp.setGridVisible(false);


      hp.setTraceStyle(true, color_);


      histogramPanelHolder_.add(hp, BorderLayout.CENTER);
//      updateBinSize();

      hp.addCursorListener(this);

      return hp;
   }

   public void updateChannelNameAndColor(ImageCache cache) {
      color_ = cache.getChannelColor(channelIndex_);
      colorPickerLabel_.setBackground(color_);
      hp_.setTraceStyle(true, color_);
      String name = cache.getChannelName(channelIndex_);
      if (name.length() > 11) {
         name = name.substring(0, 9) + "...";
      }
      channelNameCheckbox_.setText(name);
      calcAndDisplayHistAndStats(cache.getImagePlus(), true);
      mdPanel_.drawWithoutUpdate();
      this.repaint();
   }

   public int getContrastMin() {
      return contrastMin_;
   }

   public int getContrastMax() {
      return contrastMax_;
   }

   public double getContrastGamma() {
      return gamma_;
   }

   public void setContrast(int min, int max) {
      contrastMin_ = min;
      contrastMax_ = max;
   }

   public void setContrast(int min, int max, double gamma) {
      contrastMin_ = min;
      contrastMax_ = max;
      gamma_ = gamma;
   }

   //Need to put this on EDT to avoid index out of bounds because of setting currentChannel to -1
   public void applyChannelLUTToImage(final ImagePlus img, final ImageCache cache) {
      Runnable run = new Runnable() {

         @Override
         public void run() {
            if (!(img instanceof CompositeImage)) {
               return;
            }
            CompositeImage ci = (CompositeImage) img;
            Color color = cache.getChannelColor(channelIndex_);

            LUT lut = ImageUtils.makeLUT(color, gamma_);
            lut.min = contrastMin_;
            lut.max = contrastMax_;
            //uses lut.min and lut.max to set min and max of precessor
            ci.setChannelLut(lut, channelIndex_ + 1);

            //ImageJ workaround: do this so the appropriate color model and min/max get applied 
            //in color or grayscael mode
            try {
               JavaUtils.setRestrictedFieldValue(ci, CompositeImage.class, "currentChannel", -1);
            } catch (NoSuchFieldException ex) {
               ReportingUtils.logError(ex);
            }

            if (ci.getChannel() == channelIndex_ + 1) {
               LUT grayLut = ImageUtils.makeLUT(Color.white, gamma_);
               ci.getProcessor().setColorModel(grayLut);
               ci.getProcessor().setMinAndMax(contrastMin_, contrastMax_);
               if (ci.getMode() == CompositeImage.GRAYSCALE) {
                  ci.updateImage();
               }
            }
            //store contrast settings
            cache.storeChannelDisplaySettings(channelIndex_, (int) contrastMin_, (int) contrastMax_, gamma_);

            updateHistogramCursors();

         }
      };
      if (SwingUtilities.isEventDispatchThread()) {
         run.run();
      } else {
         SwingUtilities.invokeLater(run);
      }
   }

   private void updateHistogramCursors() {
      hp_.setCursors(contrastMin_ / binSize_, (contrastMax_ + 1) / binSize_, gamma_);
      hp_.repaint();
   }

   /**
    * 
    * @param img
    * @param drawHist
    * @return true if hist and stats calculated successfully
    */
   public boolean calcAndDisplayHistAndStats(ImagePlus img, boolean drawHist) {
      if (img == null) {
         return false;
      }
      ImageProcessor ip = null;
      if (((CompositeImage) img).getMode() == CompositeImage.COMPOSITE) {
         ip = ((CompositeImage) img).getProcessor(channelIndex_ + 1);
      } else if (channelIndex_ == img.getChannel() - 1) {
         ip = img.getProcessor();
      }


      boolean[] active = ((CompositeImage) img).getActiveChannels();
      channelNameCheckbox_.setSelected(active[channelIndex_]);
      if (ip == null) {
         hp_.setVisible(false);
         return false;
      }
      hp_.setVisible(true);

      int[] rawHistogram = ip.getHistogram();
      int imgWidth = img.getWidth();
      int imgHeight = img.getHeight();

      if (rawHistogram[0] == imgWidth * imgHeight) {
         return false;  //Blank pixels 
      }
      if (mccPanel_.rejectOutliersCB_.isSelected()) {
         // todo handle negative values
         maxAfterRejectingOutliers_ = rawHistogram.length;
         // specified percent of pixels are ignored in the automatic contrast setting
         int totalPoints = imgHeight * imgWidth;
         HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, fractionToReject_);
         minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
         maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();
      }
      GraphData histogramData = new GraphData();

      pixelMin_ = -1;
      pixelMax_ = 0;

      int numBins = (int) Math.min(rawHistogram.length / binSize_, NUM_BINS);
      int[] histogram = new int[NUM_BINS];
      int total = 0;
      for (int i = 0; i < numBins; i++) {
         histogram[i] = 0;
         for (int j = 0; j < binSize_; j++) {
            int rawHistIndex = (int) (i * binSize_ + j);
            int rawHistVal = rawHistogram[rawHistIndex];
            histogram[i] += rawHistVal;
            if (rawHistVal > 0) {
               pixelMax_ = rawHistIndex;
               if (pixelMin_ == -1) {
                  pixelMin_ = rawHistIndex;
               }
            }
         }
         total += histogram[i];
         if (logScale_) {
            histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
         }
      }
      //Make sure max has correct value is hist display mode isnt auto
      if (modeComboBox_.getSelectedIndex() != -1) {
         pixelMin_ = rawHistogram.length-1;
         for (int i = rawHistogram.length-1; i > 0; i--) {
            if (rawHistogram[i] > 0 && i > pixelMax_ ) {
               pixelMax_ = i;
            }
            if (rawHistogram[i] > 0 && i < pixelMin_ ) {
               pixelMin_ = i;
            }
         }
      }

      // work around what is apparently a bug in ImageJ
      if (total == 0) {
         if (img.getProcessor().getMin() == 0) {
            histogram[0] = imgWidth * imgHeight;
         } else {
            histogram[numBins - 1] = imgWidth * imgHeight;
         }
      }

      if (drawHist) {
         //Draw histogram and stats
         histogramData.setData(histogram);
         hp_.setData(histogramData);
         hp_.setAutoScale();
         hp_.repaint();

         minMaxLabel_.setText("Min: " + NumberUtils.intToDisplayString((int) pixelMin_) + "   "
                 + "Max: " + NumberUtils.intToDisplayString((int) pixelMax_));
      }
      return true;
   }

   private VirtualAcquisitionDisplay getCurrentDisplay() {
      ImagePlus img = mdPanel_.getCurrentImage();
      if (img == null) {
         return null;
      }
      return VirtualAcquisitionDisplay.getDisplay(img);
   }

   public void onLeftCursor(double pos) {
      mccPanel_.autostretchCheckbox_.setSelected(false);
      contrastMin_ = (int) (Math.max(0, pos) * binSize_);
      if (contrastMin_ >= maxIntensity_)
          contrastMin_ = maxIntensity_ - 1;
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      if (mccPanel_.syncedChannels()) {
         mccPanel_.applyContrastToAllChannels(contrastMin_, contrastMax_, gamma_);
      } else {
         mdPanel_.drawWithoutUpdate();
      }
   }

   public void onRightCursor(double pos) {
      mccPanel_.autostretchCheckbox_.setSelected(false);
      contrastMax_ = (int) (Math.min(NUM_BINS - 1, pos) * binSize_);
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      if (mccPanel_.syncedChannels()) {
         mccPanel_.applyContrastToAllChannels(contrastMin_, contrastMax_, gamma_);
      } else {
         mdPanel_.drawWithoutUpdate();
      }
   }

   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1) {
            gamma_ = 1;
         } else {
            gamma_ = gamma;
         }
         if (mccPanel_.syncedChannels()) {
            mccPanel_.applyContrastToAllChannels(contrastMin_, contrastMax_, gamma_);
         } else {
            mdPanel_.drawWithoutUpdate();
         }
      }
   }
}
