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
package mmcloneclasses.graph;

import com.swtdesigner.SwingResourceManager;
import ij.CompositeImage;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.*;
import mmcloneclasses.acquisition.MMImageCache;
import mmcloneclasses.graph.HistogramPanel.CursorListener;
import mmcloneclasses.imagedisplay.MMCompositeImage;
import mmcloneclasses.imagedisplay.ContrastMetadataCommentsPanel;
import mmcloneclasses.imagedisplay.VirtualAcquisitionDisplay;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.utils.*;

/**
 * Draws one histogram of the Multi-Channel control panel
 * 
 * 
 */


public class ChannelControlPanel extends JPanel implements CursorListener {

   private static final Dimension CONTROLS_SIZE = new Dimension(130, 150);
   public static final Dimension MINIMUM_SIZE = new Dimension(400,CONTROLS_SIZE.height);
   
   private static final int NUM_BINS = 256;
   private final int channelIndex_;
   private HistogramPanel hp_;
   private MultiChannelHistograms mcHistograms_;
   private VirtualAcquisitionDisplay display_;
   private MMImageCache cache_;
   private CompositeImage img_;
   private JButton autoButton_;
   private JButton zoomInButton_;
   private JButton zoomOutButton_;
   private JCheckBox channelNameCheckbox_;
   private JLabel colorPickerLabel_;
   private JButton fullButton_;
   private JPanel histogramPanelHolder_;
   private JLabel minMaxLabel_;
   private JComboBox histRangeComboBox_;
   private double binSize_;
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
   final private int bitDepth_;
   private Color color_;
   private String name_;
   private HistogramControlsState histControlState_;
   private ContrastPanel contrastPanel_;

   public ChannelControlPanel(int channelIndex, MultiChannelHistograms mcHistograms, VirtualAcquisitionDisplay disp,
           ContrastPanel contrastPanel) {
      histControlState_ = contrastPanel.getHistogramControlsState();
      contrastPanel_ = contrastPanel;
      display_ = disp;
      img_ = (CompositeImage) disp.getHyperImage();
      cache_ = disp.getImageCache(); 
      color_ = cache_.getChannelColor(channelIndex);
      name_ = cache_.getChannelName(channelIndex);
      bitDepth_ = cache_.getBitDepth();
      maxIntensity_ = (int) Math.pow(2, bitDepth_) - 1;
      histMax_ = maxIntensity_ + 1;
      binSize_ = histMax_ / NUM_BINS;
      histMaxLabel_ = "" + histMax_;
      mcHistograms_ = mcHistograms;
      channelIndex_ = channelIndex;
      initComponents();
      loadDisplaySettings(cache_);
      updateHistogram();
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

      channelNameCheckbox_.setText(name_);
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



      histRangeComboBox_ = new JComboBox();
      histRangeComboBox_.setFont(new Font("", Font.PLAIN, 10));
      histRangeComboBox_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            if (histControlState_.syncChannels) {
               mcHistograms_.updateOtherDisplayCombos(histRangeComboBox_.getSelectedIndex());
            }
            displayComboAction();
         }
      });
      histRangeComboBox_.setModel(new DefaultComboBoxModel(new String[]{
                 "Camera Depth", "4bit (0-15)", "5bit (0-31)", "6bit (0-63)", "7bit (0-127)", 
                 "8bit (0-255)", "9bit (0-511)", "10bit (0-1023)", "11bit (0-2047)", 
                 "12bit (0-4095)", "13bit (0-8191)", "14bit (0-16383)", "15bit (0-32767)", "16bit (0-65535)"}));

      zoomInButton_ = new JButton();
      zoomInButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/zoom_in.png"));
      zoomOutButton_ = new JButton();
      zoomOutButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/zoom_out.png"));   
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomInAction();
         }
      });
      zoomOutButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomOutAction();
         }
      });

      
      
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
      

      JLabel histRangeLabel = new JLabel("Hist. range:");
      histRangeLabel.setFont(new Font("Lucida Grande", 0, 11));

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
      colorPickerLabel_.setPreferredSize(new Dimension(18,18));
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
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(line2,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 2;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 3;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(histRangeLabel, gbc);

      zoomInButton_.setPreferredSize(new Dimension(22, 22));
      zoomOutButton_.setPreferredSize(new Dimension(22, 22));
      gbc = new GridBagConstraints();
      gbc.gridx = 4;
      gbc.gridy = 2;
      gbc.weightx = 0;
      gbc.weighty = 1;
      gbc.gridwidth = 1;
      controls_.add(zoomInButton_, gbc);

      gbc = new GridBagConstraints();
      gbc.gridx = 3;
      gbc.gridy = 2;
      gbc.weightx = 0;
      gbc.weighty = 1;
      gbc.gridwidth = 1;
      controls_.add(zoomOutButton_, gbc);

      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 3;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 5;
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls_.add(histRangeComboBox_, gbc);

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

   
   public void setDisplayComboIndex(int index) {
      histRangeComboBox_.setSelectedIndex(index);
   }
   
   public int getDisplayComboIndex() {
      return histRangeComboBox_.getSelectedIndex();
   }
   
   private void zoomInAction() {
      int selected = histRangeComboBox_.getSelectedIndex();
      if (selected == 0) {
         selected = bitDepth_ - 3;
      }
      if (selected != 1) {
         selected--;
      }
      histRangeComboBox_.setSelectedIndex(selected);
   }
   
   private void zoomOutAction() {
      int selected = histRangeComboBox_.getSelectedIndex();
      if (selected == 0) {
         selected = bitDepth_ - 3;
      }
      if (selected < histRangeComboBox_.getModel().getSize() - 1) {
         selected++;
      }
      histRangeComboBox_.setSelectedIndex(selected);  
   }
   
   public void displayComboAction() {
      int bits = histRangeComboBox_.getSelectedIndex() + 3;
      if (bits == 3) {
         histMax_ = maxIntensity_;
      } else {
         histMax_ = (int) (Math.pow(2, bits) - 1);
      }
      binSize_ = ((double) (histMax_ + 1)) / ((double) NUM_BINS);
      histMaxLabel_ = histMax_ + "";
      updateHistogram();
      calcAndDisplayHistAndStats(true);
      storeDisplaySettings();
      
   }

   private void updateHistogram() {
      hp_.setCursorText(contrastMin_ +"", contrastMax_ + "");
      hp_.setCursors(contrastMin_ / binSize_, (contrastMax_+1) / binSize_, gamma_);
      hp_.repaint();
   }

   private void fullButtonAction() {
      if (histControlState_.syncChannels) {
         mcHistograms_.fullScaleChannels();
      } else {
         setFullScale();
         mcHistograms_.applyLUTToImage();
         display_.drawWithoutUpdate();
      }
   }

   public void autoButtonAction() {
      autostretch();
      applyLUT();
   }

   private void colorPickerLabelMouseClicked() {
      //Can only edit color in this way if there is an active window
      //so it is ok to get image cache in this way
      String name = "selected";
      if (cache_.getChannelName(channelIndex_) != null) {
         name = cache_.getChannelName(channelIndex_);
      }
      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + name + " channel", cache_.getChannelColor(channelIndex_));
      if (newColor != null) {
         cache_.setChannelColor(channelIndex_, newColor.getRGB());
      }
      updateChannelNameAndColorFromCache();

      //if multicamera, save color
      if (newColor != null) {
         saveColorPreference(cache_, newColor.getRGB());
      }
   }

   /*
    * save color to preferences, but only if this is multicamera.  Since
    * we cannot check for this directly from metadata,this performs a series of
    * checks to error on the side of not saving the preference
    */
   private void saveColorPreference(MMImageCache cache, int color) {
      CMMCore core = MMStudio.getInstance().getCore();
      JSONObject summary = cache.getSummaryMetadata();
      if (core == null || summary == null) {
         return;
      }
      int numMultiCamChannels = (int) core.getNumberOfCameraChannels();
      int numDataChannels;
      JSONArray dataNames;
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
         
      }
   }

   private void channelNameCheckboxAction() {
      boolean[] active = img_.getActiveChannels();
      if (img_.getMode() != CompositeImage.COMPOSITE) {
         if (active[channelIndex_]) {
            channelNameCheckbox_.setSelected(true);
            return;
         } else {
            display_.setChannel(channelIndex_);
         }
      } else {
         img_.getActiveChannels()[channelIndex_] = channelNameCheckbox_.isSelected();
      }
        
      img_.getActiveChannels()[channelIndex_] = channelNameCheckbox_.isSelected();
      img_.updateAndDraw();
   }

   public void setFullScale() {
      contrastPanel_.disableAutostretch();
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
      if (histControlState_.ignoreOutliers) {
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


   private void loadDisplaySettings(MMImageCache cache) {
      contrastMax_ =  cache.getChannelMax(channelIndex_);
      if (contrastMax_ < 0 || contrastMax_ > maxIntensity_) {
         contrastMax_ = maxIntensity_;
      }
      contrastMin_ = cache.getChannelMin(channelIndex_);
      gamma_ = cache.getChannelGamma(channelIndex_);
      int histMax = cache.getChannelHistogramMax(channelIndex_);
      if (histMax != -1) {
         int index = (int) (Math.ceil(Math.log(histMax)/Math.log(2)) - 3);
         histRangeComboBox_.setSelectedIndex(index);
      }     
//      mcHistograms_.setDisplayMode(cache.getDisplayMode());
   }

   private HistogramPanel addHistogramPanel() {
      HistogramPanel hp = new HistogramPanel() {

         @Override
         public void paint(Graphics g) {
            super.paint(g);
            //For drawing max label
            g.setColor(Color.black);
            g.setFont(new Font("Lucida Grande", 0, 10));
            g.drawString(histMaxLabel_, this.getSize().width - 8 * histMaxLabel_.length(), this.getSize().height);
         }
      };
      hp.setMargins(12, 12);
      hp.setTraceStyle(true, color_);         
      hp.setToolTipText("Click and drag curve to adjust gamma");
      histogramPanelHolder_.add(hp, BorderLayout.CENTER);
      hp.addCursorListener(this);
      return hp;
   }

   public void updateChannelNameAndColorFromCache() {
      color_ = cache_.getChannelColor(channelIndex_);
      colorPickerLabel_.setBackground(color_);
      hp_.setTraceStyle(true, color_);
      String name = cache_.getChannelName(channelIndex_);
      if (name.length() > 11) {
         name = name.substring(0, 9) + "...";
      }
      channelNameCheckbox_.setText(name);
      calcAndDisplayHistAndStats(true);
      mcHistograms_.applyLUTToImage();
      display_.drawWithoutUpdate();
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

   public void setContrast(int min, int max, double gamma) {
      contrastMin_ = min;
      contrastMax_ = Math.min(maxIntensity_, max);
      gamma_ = gamma;
   }

   //Need to put this on EDT to avoid index out of bounds because of setting currentChannel to -1
   public void applyChannelLUTToImage() {
      Runnable run = new Runnable() {

         @Override
         public void run() {
            if (!cache_.getIsOpen()) {
               // Cache has been closed; give up.
               return;
            }
            Color color = cache_.getChannelColor(channelIndex_);

            LUT lut = ImageUtils.makeLUT(color, gamma_);
            lut.min = contrastMin_;
            lut.max = contrastMax_;
            //uses lut.min and lut.max to set min and max of precessor
            img_.setChannelLut(lut, channelIndex_ + 1);

            //ImageJ workaround: do this so the appropriate color model and min/max get applied 
            //in color or grayscael mode
            try {
               JavaUtils.setRestrictedFieldValue(img_, CompositeImage.class, "currentChannel", -1);
            } catch (NoSuchFieldException ex) {
               ReportingUtils.logError(ex);
            }

            if (img_.getChannel() == channelIndex_ + 1) {
               LUT grayLut = ImageUtils.makeLUT(Color.white, gamma_);
               ImageProcessor processor = img_.getProcessor();
               if (processor != null) {
                  processor.setColorModel(grayLut);
                  processor.setMinAndMax(contrastMin_, contrastMax_);
               }
               if (img_.getMode() == CompositeImage.GRAYSCALE) {
                  img_.updateImage();
               }
            }
            storeDisplaySettings();

            updateHistogram();

         }
      };
      if (SwingUtilities.isEventDispatchThread()) {
         run.run();
      } else {
         SwingUtilities.invokeLater(run);
      }
   }
   
   private void storeDisplaySettings() {
      int histMax = histRangeComboBox_.getSelectedIndex() == 0 ? -1 : histMax_;
      display_.storeChannelHistogramSettings(channelIndex_, contrastMin_, contrastMax_,
              gamma_, histMax,((MMCompositeImage) img_).getMode());
   }
   
   public int getChannelIndex() {
      return channelIndex_;
   }

   /**
    * @param drawHist - set true if hist and stats calculated successfully
    * 
    */
   public void calcAndDisplayHistAndStats(boolean drawHist) {
      if (img_ == null || img_.getProcessor() == null) {
         return;
      }
      ImageProcessor ip;
      if (img_.getMode() == CompositeImage.COMPOSITE) {
         ip = img_.getProcessor(channelIndex_ + 1);
         if (ip != null) {
            ip.setRoi(img_.getRoi());
         }
      } else {
         MMCompositeImage ci = (MMCompositeImage) img_;
         int flatIndex = 1 + channelIndex_ + (img_.getSlice() - 1) * ci.getNChannelsUnverified()
                 + (img_.getFrame() - 1) * ci.getNSlicesUnverified() * ci.getNChannelsUnverified();
         ip = img_.getStack().getProcessor(flatIndex);

      }

      if (((MMCompositeImage) img_).getNChannelsUnverified() <= 7) {
         boolean active = img_.getActiveChannels()[channelIndex_];
         channelNameCheckbox_.setSelected(active);
         if (!active) {
            drawHist = false;
         }
      }
      if (((MMCompositeImage) img_).getMode() != CompositeImage.COMPOSITE) {
         if (img_.getChannel() - 1 != channelIndex_) {
            drawHist = false;
         }
      }
      
      if (ip == null ) {
         return;
      }

      int[] rawHistogram = ip.getHistogram();
      int imgWidth = img_.getWidth();
      int imgHeight = img_.getHeight();

      if (rawHistogram[0] == imgWidth * imgHeight) {
         return;  //Blank pixels 
      }
      if (histControlState_.ignoreOutliers) {
         // todo handle negative values
         maxAfterRejectingOutliers_ = rawHistogram.length;
         // specified percent of pixels are ignored in the automatic contrast setting
         int totalPoints = imgHeight * imgWidth;
         HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, 0.01*histControlState_.percentToIgnore);
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
         if (histControlState_.logHist) {
            histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
         }
      }
      //Make sure max has correct value is hist display mode isnt auto
      if (histRangeComboBox_.getSelectedIndex() != -1) {
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
         if (img_.getProcessor().getMin() == 0) {
            histogram[0] = imgWidth * imgHeight;
         } else {
            histogram[numBins - 1] = imgWidth * imgHeight;
         }
      }

      
      if (drawHist) {
         hp_.setVisible(true);
         //Draw histogram and stats
         histogramData.setData(histogram);
         hp_.setData(histogramData);
         hp_.setAutoScale();
         hp_.repaint();

         minMaxLabel_.setText("Min: " + NumberUtils.intToDisplayString( pixelMin_) + "   "
                 + "Max: " + NumberUtils.intToDisplayString( pixelMax_));
      } else {
          hp_.setVisible(false);        
      }
      
   }
   
   public void contrastMaxInput(int max) {
      contrastPanel_.disableAutostretch();
      contrastMax_ = max;
      if (contrastMax_ > maxIntensity_ ) {
         contrastMax_ = maxIntensity_;
      }
      if (contrastMax_ < 0) {
         contrastMax_ = 0;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      applyLUT();
   }
   
   @Override
   public void contrastMinInput(int min) {    
      contrastPanel_.disableAutostretch();
      contrastMin_ = min;
      if (contrastMin_ >= maxIntensity_)
          contrastMin_ = maxIntensity_ - 1;
      if(contrastMin_ < 0 ) {
         contrastMin_ = 0;
      }
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      applyLUT();
   }

   public void onLeftCursor(double pos) {
      contrastPanel_.disableAutostretch();
      contrastMin_ = (int) (Math.max(0, pos) * binSize_);
      if (contrastMin_ >= maxIntensity_)
          contrastMin_ = maxIntensity_ - 1;
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      applyLUT();
   }

   @Override
   public void onRightCursor(double pos) {
      contrastPanel_.disableAutostretch();
      contrastMax_ = (int) (Math.min(NUM_BINS - 1, pos) * binSize_);
      if (contrastMax_ < 1) {
         contrastMax_ = 1;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      applyLUT();
   }

   @Override
   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1) {
            gamma_ = 1;
         } else {
            gamma_ = gamma;
         }
         applyLUT();
      }
   }

   private void applyLUT() {
      if (histControlState_.syncChannels) {
         mcHistograms_.applyContrastToAllChannels(contrastMin_, contrastMax_, gamma_);
      } else {
         mcHistograms_.applyLUTToImage();
         display_.drawWithoutUpdate();
      }
   }
}
