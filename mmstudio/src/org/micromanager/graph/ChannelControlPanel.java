/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ChannelControlsPanel.java
 *
 * Created on Sep 27, 2010, 1:27:24 PM
 */
package org.micromanager.graph;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
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


/**
 *
 * @author arthur
 */
public class ChannelControlPanel extends JPanel implements CursorListener {

   private static final Dimension CONTROLS_SIZE = new Dimension(120,115);
   private static final int NUM_BINS = 256;
   private static final double BIN_SIZE_MIN = 1.0/8;
   private final int BIN_SIZE_MAX;

   private final int channelIndex_;
   private final HistogramPanel hp_;
   private final MultiChannelContrastPanel mccPanel_;
   private final MetadataPanel mdPanel_;

   private JButton autoButton_;
   private JCheckBox channelNameCheckbox_;
   private JLabel colorPickerLabel_;
   private JButton fullButton_;
   private JPanel histogramPanelHolder_;
   private JLabel maxLabel_;
   private JLabel minLabel_;
   private JButton zoomInButton_;
   private JButton zoomOutButton_;
   
   private double binSize_;
   private boolean logScale_;
   private double fractionToReject_; 
   private int height_;
   private String histMaxLabel_;
   private int histMax_;
   private JPanel controls_;
   private JPanel controlsHolderPanel_;
   
   private double contrastMin_;
   private double contrastMax_;
   private double gamma_ = 1;
	private double minAfterRejectingOutliers_;
	private double maxAfterRejectingOutliers_;
   private double pixelMin_ = 0.0;
   private double pixelMax_ = 255.0;
   final private int maxIntensity_;
   private Color color_;

   public ChannelControlPanel(int channelIndex, MultiChannelContrastPanel mccPanel, MetadataPanel md, int height,
           Color color, int bitDepth, double fractionToReject, boolean logScale) {
      height_ = height;
      fractionToReject_ = fractionToReject;
      logScale_ = logScale;
      color_ = color;
      maxIntensity_ = (int) Math.pow(2, bitDepth) -1;
      histMax_ = maxIntensity_+1;
      binSize_ = histMax_ / NUM_BINS;
      BIN_SIZE_MAX = (int) (Math.pow(2, bitDepth) / NUM_BINS);
      histMaxLabel_ = "" + histMax_;
      mdPanel_ = md;
      initComponents(); 
      channelIndex_ = channelIndex;
      hp_ = addHistogramPanel();
      mccPanel_ = mccPanel; 
      ImageCache cache = VirtualAcquisitionDisplay.getDisplay(WindowManager.getCurrentImage()).getImageCache();
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
      zoomInButton_ = new javax.swing.JButton();
      zoomOutButton_ = new javax.swing.JButton();
      minLabel_ = new javax.swing.JLabel();
      maxLabel_ = new javax.swing.JLabel();

      setOpaque(false);
      setPreferredSize(new java.awt.Dimension(250,height_ ));

      fullButton_.setFont(fullButton_.getFont().deriveFont((float)9));
      fullButton_.setText("Full");
      fullButton_.setToolTipText("Stretch the display gamma curve over the full pixel range");
      fullButton_.setMargin(new java.awt.Insets(2, 4, 2, 4));
      fullButton_.setPreferredSize(new java.awt.Dimension(75, 30));
      fullButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fullButtonAction();
         }  });

      autoButton_.setFont(autoButton_.getFont().deriveFont((float)9));
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
         }  });

      colorPickerLabel_.setBackground(color_);
      colorPickerLabel_.setToolTipText("Change the color for displaying this channel");
      colorPickerLabel_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
      colorPickerLabel_.setOpaque(true);
      colorPickerLabel_.addMouseListener(new java.awt.event.MouseAdapter() {
         public void mouseClicked(java.awt.event.MouseEvent evt) {
            colorPickerLabelMouseClicked();
         }   });

      channelNameCheckbox_.setSelected(true);
      channelNameCheckbox_.setText("Channel");
      channelNameCheckbox_.setToolTipText("Show/hide this channel in the multi-dimensional viewer");
      channelNameCheckbox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            channelNameCheckboxAction();
         } });

      histogramPanelHolder_.setToolTipText("Adjust the brightness and contrast by dragging triangles at top and bottom. Change the gamma by dragging the curve. (These controls only change display, and do not edit the image data.)");
      histogramPanelHolder_.setAlignmentX(0.3F);
      histogramPanelHolder_.setPreferredSize(new java.awt.Dimension(0, 100));
      histogramPanelHolder_.setLayout(new BorderLayout());
      
      zoomInButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/zoom_in.png"))); // NOI18N
      zoomInButton_.setToolTipText("Histogram zoom in");
      zoomInButton_.setMaximumSize(new java.awt.Dimension(20, 20));
      zoomInButton_.setMinimumSize(new java.awt.Dimension(20, 20));
      zoomInButton_.setPreferredSize(new java.awt.Dimension(20, 20));
      zoomInButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zoomInButtonAction();
         }
      });

      zoomOutButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/zoom_out.png"))); // NOI18N
      zoomOutButton_.setToolTipText("Histogram zoom out");
      zoomOutButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zoomOutButtonAction();
         }
      });

      minLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      minLabel_.setText("min");

      maxLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      maxLabel_.setText("max");

      
      this.setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
      controlsHolderPanel_ =  new JPanel();
      controls_ = new JPanel();
      this.add(controlsHolderPanel_);
      this.add(histogramPanelHolder_);

      controlsHolderPanel_.add(controls_);
      GridBagLayout gbl = new GridBagLayout();
      controls_.setLayout(gbl);
      
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = 7;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      controls_.add(channelNameCheckbox_,gbc);
        
      gbc = new GridBagConstraints();
      gbc.gridx = 1;
      gbc.gridy = 1;
      gbc.ipadx = 15;
      gbc.ipady = 15;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 3;
      gbc.insets = new Insets(2,2,2,2);
      controls_.add(colorPickerLabel_,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 2;
      gbc.gridwidth = 1;
      gbc.weightx = 1;
      gbc.weighty = 1;     
      gbc.insets = new Insets(2,2,2,2);
      gbc.fill = GridBagConstraints.NONE;
      zoomInButton_.setPreferredSize(new Dimension(22,22));
      controls_.add(zoomInButton_,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 1;
      gbc.gridy = 2;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 1;
      gbc.insets = new Insets(2,2,2,2);
      gbc.fill = GridBagConstraints.NONE;
      zoomOutButton_.setPreferredSize(new Dimension(22,22));
      controls_.add(zoomOutButton_,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 3;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 7;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(minLabel_,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 4;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 7;
      gbc.anchor = GridBagConstraints.LINE_START;
      controls_.add(maxLabel_,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 5;
      gbc.gridy = 1;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 1;
      fullButton_.setPreferredSize(new Dimension(50,20));
      controls_.add(fullButton_,gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridx = 5;
      gbc.gridy = 2;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.gridwidth = 1;
      autoButton_.setPreferredSize(new Dimension(50,20));
      controls_.add(autoButton_,gbc);


   }
   
   public void setHeight(int height) {    
      hp_.setSize(hp_.getWidth(),height);
      histogramPanelHolder_.setSize(hp_.getSize());
      this.setSize(this.getSize().width, height);   
      controlsHolderPanel_.setSize(CONTROLS_SIZE);
   }

    private void fullButtonAction() {
       setFullScale();
       mdPanel_.drawWithoutUpdate();
    }

        
    public void autoButtonAction() {
       autostretch();
       mdPanel_.drawWithoutUpdate();
    }
    
    private void colorPickerLabelMouseClicked() {
      //Can only edit color in this way if there is an active window
     //so it is ok to get image cache in this way
     ImageCache cache = VirtualAcquisitionDisplay.getDisplay(WindowManager.getCurrentImage()).getImageCache();
      String name = "selected";
      if (cache.getChannelName(channelIndex_) != null)
         name = cache.getChannelName(channelIndex_);
      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + name + " channel", cache.getChannelColor(channelIndex_));
      if (newColor != null) 
         cache.setChannelColor(channelIndex_, newColor.getRGB());
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
       if (core == null || summary == null)
          return;
      int numMultiCamChannels = (int) core.getNumberOfCameraChannels(), numDataChannels = -1;
      JSONArray dataNames = null;
      String[] cameraNames = new String[numMultiCamChannels];
      try {
         numDataChannels = MDUtils.getNumChannels(summary);
         dataNames = summary.getJSONArray("ChNames");
         for (int i = 0; i < numMultiCamChannels; i++)
            cameraNames[i] = core.getCameraChannelName(i);

       if ( numMultiCamChannels > 1 &&  numDataChannels == numMultiCamChannels
               && cameraNames.length == dataNames.length()) {
          for (int h = 0; h < cameraNames.length; h++)
             if (!cameraNames[h].equals(dataNames.getString(h)) )
                  return;
          Preferences root = Preferences.userNodeForPackage(AcqControlDlg.class);              
          Preferences colorPrefs = root.node(root.absolutePath() + "/" + AcqControlDlg.COLOR_SETTINGS_NODE);
          colorPrefs.putInt("Color_Camera_" + cameraNames[channelIndex_],color);            
         }
      } catch (Exception ex) {
         return;
      }
   }

    private void channelNameCheckboxAction() {
       CompositeImage ci = (CompositeImage) WindowManager.getCurrentImage();
       ImageCache cache = VirtualAcquisitionDisplay.getDisplay(ci).getImageCache();
       boolean[] active = ci.getActiveChannels();
       if (ci.getMode() != CompositeImage.COMPOSITE)
          if (active[channelIndex_]) {
             channelNameCheckbox_.setSelected(true);
             return;
          } else 
             ci.setPosition(channelIndex_+1, ci.getSlice(), ci.getFrame());
 
       cache.setChannelVisibility(channelIndex_, channelNameCheckbox_.isSelected());
       mdPanel_.drawWithoutUpdate();
    }

    private void zoomInButtonAction() {
       binSize_ = Math.max(binSize_ / 2, BIN_SIZE_MIN);
       updateHistMax();
       calcAndDisplayHistAndStats(WindowManager.getCurrentImage(),true);
       updateHistogramCursors();
    }

    private void zoomOutButtonAction() {
       binSize_ = Math.min(binSize_ * 2, BIN_SIZE_MAX);              
       updateHistMax();
       calcAndDisplayHistAndStats(WindowManager.getCurrentImage(),true);
       updateHistogramCursors();
    }
    
    public void setFullScale(){
      mccPanel_.autostretchCheckbox_.setSelected(false);
      contrastMin_ = 0;
      contrastMax_ = histMax_;
    }
    
    public void autostretch() {
      contrastMin_ = pixelMin_;
      contrastMax_ = pixelMax_;
      if (mccPanel_.rejectOutliersCB_.isSelected()) {
         if (contrastMin_ < minAfterRejectingOutliers_) 
            if (0 < minAfterRejectingOutliers_) 
               contrastMin_ = minAfterRejectingOutliers_;
         if (maxAfterRejectingOutliers_ < contrastMax_) 
            contrastMax_ = maxAfterRejectingOutliers_;
      }
   }
    
    public void setFractionToReject(double frac) {
       fractionToReject_ = frac;
    }

   public void loadDisplaySettings(ImageCache cache) {
      contrastMax_ = cache.getChannelMax(channelIndex_);
      if (contrastMax_ < 0)
         contrastMax_ = maxIntensity_;
      contrastMin_ = cache.getChannelMin(channelIndex_);
      gamma_ = cache.getChannelGamma(channelIndex_);
   }

   public void setLogScale(boolean logScale) {
      logScale_ = logScale;
      calcAndDisplayHistAndStats(WindowManager.getCurrentImage(),true);
   }

   private final HistogramPanel addHistogramPanel() {
      HistogramPanel hp = new HistogramPanel() {
        @Override
        public void paint(Graphics g) {
           super.paint(g);
           //For drawing max label
           g.setColor(Color.black);
           g.setFont(new Font("Lucida Grande", 0, 10));          
           g.drawString(histMaxLabel_, this.getSize().width - 7*histMaxLabel_.length(), this.getSize().height );
        } };
      hp.setMargins(8, 12);
      hp.setTextVisible(false);
      hp.setGridVisible(false);
      
        
      hp.setTraceStyle(true, color_);

      
      histogramPanelHolder_.add(hp, BorderLayout.CENTER);
//      updateBinSize();
      
      hp.addCursorListener(this);
              
      hp.addMouseWheelListener(new MouseWheelListener() {
         public void mouseWheelMoved(MouseWheelEvent e) {
            int notches = e.getWheelRotation();
            if (notches > 0)
               zoomOutButtonAction();
            else if (notches < 0)
               zoomInButtonAction();
         }});
      return hp;
   }


   public void updateChannelNameAndColor(ImageCache cache) {
      color_ = cache.getChannelColor(channelIndex_);
      colorPickerLabel_.setBackground(color_);
      hp_.setTraceStyle(true, color_);
      String name = cache.getChannelName(channelIndex_);
      if (name.length() > 11) 
         name = name.substring(0, 9) + "...";
      channelNameCheckbox_.setText(name);
      calcAndDisplayHistAndStats(cache.getImagePlus(),true);
      mdPanel_.drawWithoutUpdate();
      this.repaint();
   }

   private void updateHistMax() {
      if (binSize_ < BIN_SIZE_MIN) 
         binSize_ = BIN_SIZE_MIN;
      else if (binSize_ > BIN_SIZE_MAX)
         binSize_ = BIN_SIZE_MAX;
      histMax_ = (int) Math.pow(2, Math.round( Math.log(binSize_*NUM_BINS)/Math.log(2.0) ));
      histMaxLabel_ = histMax_+"";
   }   
   
   private void setChannelWithoutMovingSlider(ImagePlus img, int channel) {
      if (img != null) {
         int z = img.getSlice();
         int t = img.getFrame();
         img.updatePosition(channel + 1, z, t);
      }
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

   public void applyChannelLUTToImage(ImagePlus img, ImageCache cache) {
      if (!(img instanceof CompositeImage))
         return;
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
         if (ci.getMode() == CompositeImage.GRAYSCALE)
            ci.updateImage();
      }
     


      //store contrast settings
      cache.storeChannelDisplaySettings(channelIndex_, (int) contrastMin_, (int) contrastMax_, gamma_);

      updateHistogramCursors();
   }
   
   private void updateHistogramCursors() {
      hp_.setCursors(contrastMin_ / binSize_, (contrastMax_+1) / binSize_, gamma_);
		hp_.repaint();
   }

   public void calcAndDisplayHistAndStats(ImagePlus img, boolean drawHist) {
      if (img == null)
         return;
      ImageProcessor ip = null;
      if (((CompositeImage) img).getMode() == CompositeImage.COMPOSITE)
         ip = ((CompositeImage) img).getProcessor(channelIndex_ + 1);
      else if (channelIndex_ == img.getChannel() - 1)
         ip = img.getProcessor();
     

      boolean[] active = ((CompositeImage) img).getActiveChannels();
      channelNameCheckbox_.setSelected(active[channelIndex_]);
      if (ip == null) { 
         hp_.setVisible(false);
         return;
      }
      hp_.setVisible(true);
      
      int[] rawHistogram = ip.getHistogram();
      int imgWidth = img.getWidth();
      int imgHeight = img.getHeight();
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
               if (pixelMin_ == -1) 
                  pixelMin_ = rawHistIndex;
            }
         }
         total += histogram[i];
         if (logScale_) {
            histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
         }
      }
      if (pixelMin_ == pixelMax_) {
         if (pixelMin_ == 0) {
            pixelMax_++;
         } else {
            pixelMin_--;
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

         maxLabel_.setText("Max: " + NumberUtils.intToDisplayString((int) pixelMax_));
         minLabel_.setText("Min: " + NumberUtils.intToDisplayString((int) pixelMin_));
         
         
      }
   }

   private VirtualAcquisitionDisplay getCurrentDisplay() {
      ImagePlus img = WindowManager.getCurrentImage();
      if (img == null)
         return null;
      return VirtualAcquisitionDisplay.getDisplay(img);
   }

   public void onLeftCursor(double pos) {
      mccPanel_.autostretchCheckbox_.setSelected(false);
      contrastMin_ = Math.max(0, pos) * binSize_;
      if (contrastMax_ < contrastMin_)
         contrastMax_ = contrastMin_;
      mdPanel_.drawWithoutUpdate();
   }
   
   public void onRightCursor(double pos) {
      mccPanel_.autostretchCheckbox_.setSelected(false);
      contrastMax_ = Math.min(NUM_BINS-1, pos) * binSize_;
      if (contrastMin_ > contrastMax_)
         contrastMin_ = contrastMax_;
      mdPanel_.drawWithoutUpdate();
   }

   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1)
            gamma_ = 1;
         else 
            gamma_ = gamma;
         mdPanel_.drawWithoutUpdate();
      }
   }
}
