///////////////////////////////////////////////////////////////////////////////
//FILE:          SingleChannelHistogram.java
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

import com.swtdesigner.SwingResourceManager;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.micromanager.MMStudio;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.internalinterfaces.Histograms;
import org.micromanager.api.ImageCache;
import org.micromanager.graph.HistogramPanel.CursorListener;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.HistogramUtils;
import org.micromanager.utils.NumberUtils;

/**
 * A single histogram and a few controls for manipulating image contrast 
 * and histogram display
 * 
 * More LUTS can easily be added.  Look at the fire LUT for an example
 * 
 */
public class SingleChannelHistogram extends JPanel implements Histograms, CursorListener {

   private static final long serialVersionUID = 1L;
   private static final int SLOW_HIST_UPDATE_INTERVAL_MS = 1000;
   
   private long lastUpdateTime_;
   private JComboBox histRangeComboBox_;
   private JComboBox lutComboBox_;
   private HistogramPanel histogramPanel_;
   private JLabel maxLabel_;
   private JLabel minLabel_;
   private JLabel meanLabel_;
   private JLabel stdDevLabel_;
   private double gamma_ = 1.0;
   private int histMax_;
   private int maxIntensity_;
   private int bitDepth_;
   private double mean_;
   private double stdDev_;
   private int pixelMin_ = 0;
   private int pixelMax_ = 255;
   private double binSize_ = 1;
   private static final int HIST_BINS = 256;
   private int contrastMin_;
   private int contrastMax_;
   private double minAfterRejectingOutliers_;
   private double maxAfterRejectingOutliers_;
   private VirtualAcquisitionDisplay display_;
   private ImagePlus img_;
   private ImageCache cache_;
   
   private static final byte[][] fireLUT_;
   static {
      fireLUT_ = new byte[3][256];
      int l = fire(fireLUT_[0], fireLUT_[1], fireLUT_[2]);
      if (l < 256) {
         interpolate (fireLUT_[0], fireLUT_[1], fireLUT_[2], l);
      }
   }
   private static final byte[][] redHotLUT_;
   static {
      redHotLUT_ = new byte[3][256];
      int l = redhot(redHotLUT_[0], redHotLUT_[1], redHotLUT_[2]);
      if (l < 256) {
         interpolate(redHotLUT_[0], redHotLUT_[1], redHotLUT_[2], l);
      }
   }
   private static final byte[][] spectrumLUT_;
   static {
      spectrumLUT_ = new byte[3][256];
      int l = spectrum(spectrumLUT_[0], spectrumLUT_[1], spectrumLUT_[2]);
      if (l < 256) {
         interpolate(spectrumLUT_[0], spectrumLUT_[1], spectrumLUT_[2], l);
      }
   }

   public SingleChannelHistogram(VirtualAcquisitionDisplay disp) {
      super();
      display_ = disp;
      img_ = disp.getImagePlus();
      cache_ = disp.getImageCache();
      bitDepth_ = cache_.getBitDepth();
      maxIntensity_ = (int) (Math.pow(2, bitDepth_) - 1);     
      histMax_ = maxIntensity_;
      binSize_ = ((double) (histMax_ + 1)) / ((double) HIST_BINS);
      initGUI();
      loadDisplaySettings();
   }

   private void initGUI() {
      this.setLayout(new BorderLayout());
      this.setFont(new Font("", Font.PLAIN, 10));

      histogramPanel_ = new HistogramPanel() {

         @Override
         public void paint(Graphics g) {
            super.paint(g);
            //For drawing max label
            g.setColor(Color.black);
            g.setFont(new Font("Lucida Grande", 0, 10));
            String label = "" + histMax_;
            g.drawString(label, this.getSize().width - 7 * label.length(), this.getSize().height);
         }
      };
      histogramPanel_.setMargins(12, 10);
      histogramPanel_.setTraceStyle(true, Color.white);
      histogramPanel_.addCursorListener(this);
      this.add(histogramPanel_, BorderLayout.CENTER);

      JPanel controls = new JPanel();
      JPanel controlHolder = new JPanel(new BorderLayout());
      controlHolder.add(controls, BorderLayout.PAGE_START);
      this.add(controlHolder, BorderLayout.LINE_START);
      
      
      // generate ImageIcons for LUT dropdown     
      final int iconWidth = 128;
      final int iconHeight = 10;
      byte[] r = new byte[256];
      byte[] g = new byte[256];
      byte[] b = new byte[256];
      for (int i = 0; i < 256; i++) {
         r[i] = (byte) i; g[i] = (byte) i; b[i] = (byte) i;
      }
      ImageIcon grayIcon = getIcon(r, g, b, iconWidth, iconHeight);
      // extend the ends of glow over/under so that they become visible in the icon
      for (int i = 0; i < 6; i++) {
         r[i] = (byte) 0; g[i] = (byte) 0; b[i] = (byte) 255;
         r[255 - i] = (byte) 255; g[255 - i] = (byte) 0; b[255 - i] = (byte) 0;
      }
      ImageIcon glowOverUnderIcon = getIcon(r, g, b, iconWidth, iconHeight);
      ImageIcon fireIcon = getIcon(fireLUT_[0], fireLUT_[1], fireLUT_[2], iconWidth, 
              iconHeight);
      ImageIcon redHotIcon = getIcon(redHotLUT_[0], redHotLUT_[1], redHotLUT_[2], iconWidth, 
              iconHeight);
      ImageIcon spectrumIcon = getIcon(spectrumLUT_[0], spectrumLUT_[1], spectrumLUT_[2],
              iconWidth, iconHeight);
      Object[] items =
        {grayIcon, glowOverUnderIcon, fireIcon, redHotIcon, spectrumIcon};
      lutComboBox_ = new JComboBox(items);
      lutComboBox_.setFont(new Font("", Font.PLAIN, 10));
      lutComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            lutComboAction();
         }
      });

      
      JButton fullScaleButton = new JButton();
      fullScaleButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            fullButtonAction();
         }
      });
      fullScaleButton.setFont(new Font("Arial", Font.PLAIN, 10));
      fullScaleButton.setToolTipText("Set display levels to full pixel range");
      fullScaleButton.setText("Full");

      final JButton autoScaleButton = new JButton();
      autoScaleButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            autoButtonAction();
         }
      });
      autoScaleButton.setFont(new Font("Arial", Font.PLAIN, 10));
      autoScaleButton.setToolTipText("Set display levels to maximum contrast");
      autoScaleButton.setText("Auto");

      minLabel_ = new JLabel();
      minLabel_.setFont(new Font("", Font.PLAIN, 10));
      maxLabel_ = new JLabel();
      maxLabel_.setFont(new Font("", Font.PLAIN, 10));
      meanLabel_ = new JLabel();
      meanLabel_.setFont(new Font("", Font.PLAIN, 10));
      stdDevLabel_ = new JLabel();
      stdDevLabel_.setFont(new Font("", Font.PLAIN, 10));


      JButton zoomInButton = new JButton();
      zoomInButton.setIcon(SwingResourceManager.getIcon(MMStudio.class,
              "/org/micromanager/icons/zoom_in.png"));
      JButton zoomOutButton = new JButton();
      zoomOutButton.setIcon(SwingResourceManager.getIcon(MMStudio.class,
              "/org/micromanager/icons/zoom_out.png"));
      zoomInButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            zoomInAction();
         }
      });
      zoomOutButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            zoomOutAction();
         }
      });
      zoomInButton.setPreferredSize(new Dimension(22, 22));
      zoomOutButton.setPreferredSize(new Dimension(22, 22));


      JPanel p = new JPanel();
      JLabel histRangeLabel = new JLabel("Hist range:");
      histRangeLabel.setFont(new Font("Arial", Font.PLAIN, 10));

      p.add(histRangeLabel);
      p.add(zoomInButton);
      p.add(zoomOutButton);

      histRangeComboBox_ = new JComboBox();
      histRangeComboBox_.setFont(new Font("", Font.PLAIN, 10));
      histRangeComboBox_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            histRangeComboAction();
         }
      });
      histRangeComboBox_.setModel(new DefaultComboBoxModel(new String[]{
                 "Camera Depth", "4bit (0-15)", "5bit (0-31)", "6bit (0-63)", "7bit (0-127)",
                 "8bit (0-255)", "9bit (0-511)", "10bit (0-1023)", "11bit (0-2047)",
                 "12bit (0-4095)", "13bit (0-8191)", "14bit (0-16383)", "15bit (0-32767)", "16bit (0-65535)"}));



      GridBagLayout layout = new GridBagLayout();
      controls.setLayout(layout);


      JPanel statsPanel = new JPanel(new GridLayout(5, 1));
      statsPanel.add(new JLabel(" "));
      statsPanel.add(minLabel_);
      statsPanel.add(maxLabel_);
      statsPanel.add(meanLabel_);
      statsPanel.add(stdDevLabel_);

      JPanel histZoomLine = new JPanel();
      histZoomLine.add(histRangeLabel);
      histZoomLine.add(zoomOutButton);
      histZoomLine.add(zoomInButton);

      
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridy = 0;
      gbc.weightx = 0;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.BOTH;
      controls.add(new JLabel(" "), gbc);
   
      gbc = new GridBagConstraints();
      gbc.gridy = 1;
      gbc.weightx = 1;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls.add(lutComboBox_, gbc);
      
      gbc = new GridBagConstraints();
      gbc.gridy = 2;
      gbc.gridx = 0;
      gbc.weightx = 1;
      gbc.ipadx = 4;
      gbc.ipady = 4;
      fullScaleButton.setPreferredSize(new Dimension(60, 15));
      controls.add(fullScaleButton, gbc);

      gbc = new GridBagConstraints();
      gbc.gridy = 2;
      gbc.gridx = 1;
      gbc.weightx = 1;
      gbc.ipadx = 4;
      gbc.ipady = 4;
      autoScaleButton.setPreferredSize(new Dimension(60, 15));
      controls.add(autoScaleButton, gbc);

      gbc = new GridBagConstraints();
      gbc.gridy = 3;
      gbc.weightx = 1;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls.add(histZoomLine, gbc);

      gbc = new GridBagConstraints();
      gbc.gridy = 4;
      gbc.weightx = 1;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls.add(histRangeComboBox_, gbc);

      gbc = new GridBagConstraints();
      gbc.gridy = 5;
      gbc.weightx = 1;
      gbc.gridwidth = 2;

      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      controls.add(statsPanel, gbc);
   }
      
   private void loadDisplaySettings() {
      contrastMax_ = cache_.getChannelMax(0);
      if (contrastMax_ < 0 || contrastMax_ > maxIntensity_) {
         contrastMax_ = maxIntensity_;
      }
      contrastMin_ = cache_.getChannelMin(0);
      gamma_ = cache_.getChannelGamma(0);
      int histMax = cache_.getChannelHistogramMax(0);
      if (histMax != -1) {
         int index = (int) (Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
         histRangeComboBox_.setSelectedIndex(index);
      }
   }
   
   private void autoButtonAction() {
      autostretch();
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   private void fullButtonAction() {
      setFullScale();
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   private void histRangeComboAction() {
      setHistMaxAndBinSize();     
      calcAndDisplayHistAndStats(true);
   }
   
   // todo: implement!
   private void lutComboAction () {
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   @Override
   public void rejectOutliersChangeAction() {
      calcAndDisplayHistAndStats(true);
      autoButtonAction();
   }

   @Override
   public void autoscaleAllChannels() {
      autoButtonAction();
   }

   @Override
   public void applyLUTToImage() {
      if (img_ == null) {
         return;
      }
      ImageProcessor ip = img_.getProcessor();
      if (ip == null) {
         return;
      }

      final double maxValue = 255.0;
      final int length = 256;
      byte[] r = new byte[length];
      byte[] g = new byte[length];
      byte[] b = new byte[length];
      
      // Gray scale and glow over/under
      if (lutComboBox_.getSelectedIndex() < 2) {
         for (int i = 0; i < length; i++) {
            double val = Math.pow((double) i / maxValue, gamma_) * maxValue;
            r[i] = (byte) val;
            g[i] = (byte) val;
            b[i] = (byte) val;
         }

         if (lutComboBox_.getSelectedIndex() == 1) {
            // glow over/under LUT
            r[0] = (byte) 0;
            g[0] = (byte) 0;
            b[0] = (byte) 255;
            r[255] = (byte) 255;
            g[255] = (byte) 0;
            b[255] = (byte) 0;
         }
      }
      
      // Fire
      if (lutComboBox_.getSelectedIndex() == 2) {
         for (int i = 0; i < length; i++) {
            double val = Math.pow((double) i / maxValue, gamma_) * maxValue;
            r[i] = fireLUT_[0][(int) val];
            g[i] = fireLUT_[1][(int) val];
            b[i] = fireLUT_[2][(int) val];
         }
      }
      
      // redHot
      if (lutComboBox_.getSelectedIndex() == 3) {
         for (int i = 0; i < length; i++) {
            double val = Math.pow((double) i / maxValue, gamma_) * maxValue;
            r[i] = redHotLUT_[0][(int) val];
            g[i] = redHotLUT_[1][(int) val];
            b[i] = redHotLUT_[2][(int) val];
         }
      }
      
      // Spectrum
      if (lutComboBox_.getSelectedIndex() == 4) {
         for (int i = 0; i < length; i++) {
            double val = Math.pow((double) i / maxValue, gamma_) * maxValue;
            r[i] = spectrumLUT_[0][(int) val];
            g[i] = spectrumLUT_[1][(int) val];
            b[i] = spectrumLUT_[2][(int) val];
         }
      }
      
      //apply gamma and contrast to image
      ip.setColorModel(new LUT(8, length, r, g, b));    //doesnt explicitly redraw
      ip.setMinAndMax(contrastMin_, contrastMax_);   //doesnt explicitly redraw

      saveDisplaySettings();

      updateHistogram();
   }
   
   /**
    * Generate small fire lut data.  
    * Copied from ImageJ source
    */
   public static int fire(byte[] reds, byte[] greens, byte[] blues) {
		int[] r = {0,0,1,25,49,73,98,122,146,162,173,184,195,207,217,229,240,252,255,255,255,255,255,255,255,255,255,255,255,255,255,255};
		int[] g = {0,0,0,0,0,0,0,0,0,0,0,0,0,14,35,57,79,101,117,133,147,161,175,190,205,219,234,248,255,255,255,255};
		int[] b = {0,61,96,130,165,192,220,227,210,181,151,122,93,64,35,5,0,0,0,0,0,0,0,0,0,0,0,35,98,160,223,255};
		for (int i=0; i<r.length; i++) {
			reds[i] = (byte)r[i];
			greens[i] = (byte)g[i];
			blues[i] = (byte)b[i];
		}
		return r.length;
	}
   
   /**
    * Generate small redhot lut data
    * constructed by Nico Stuurman based on LUT included with ImageJ
    */
   public static int redhot(byte[] reds, byte[] greens, byte[] blues) {
		int[] r = {0,1,27,52,78,103,130,155,181,207,233,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255};
		int[] g = {0,1, 0, 0, 0,  0,  0,  0,  0,  0,  0,  3, 29, 55, 81,106,133,158,184,209,236,255,255,255,255,255,255,255,255,255,255,255};
		int[] b = {0,1, 0, 0, 0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  6, 32, 58, 84,110,135,161,187,160,213,255};
		for (int i=0; i<r.length; i++) {
			reds[i] = (byte)r[i];
			greens[i] = (byte)g[i];
			blues[i] = (byte)b[i];
		}
		return r.length;
	}
   
   public static int spectrum(byte[] reds, byte[] greens, byte[] blues) {
		Color c;
		for (int i=0; i<256; i++) {
			c = Color.getHSBColor(i/255f, 1f, 1f);
			reds[i] = (byte)c.getRed();
			greens[i] = (byte)c.getGreen();
			blues[i] = (byte)c.getBlue();
		}
		return 256;
	}

   /**
    * Interpolate small LUTs into larger ones by interpolation
    * Copied from ImageJ source
    */
   public static void interpolate(byte[] reds, byte[] greens, byte[] blues, int nColors) {
		byte[] r = new byte[nColors]; 
		byte[] g = new byte[nColors]; 
		byte[] b = new byte[nColors];
		System.arraycopy(reds, 0, r, 0, nColors);
		System.arraycopy(greens, 0, g, 0, nColors);
		System.arraycopy(blues, 0, b, 0, nColors);
		double scale = nColors/256.0;
		int i1, i2;
		double fraction;
		for (int i=0; i<256; i++) {
			i1 = (int)(i*scale);
			i2 = i1+1;
			if (i2==nColors) i2 = nColors-1;
			fraction = i*scale - i1;
			reds[i] = (byte)((1.0-fraction)*(r[i1]&255) + fraction*(r[i2]&255));
			greens[i] = (byte)((1.0-fraction)*(g[i1]&255) + fraction*(g[i2]&255));
			blues[i] = (byte)((1.0-fraction)*(b[i1]&255) + fraction*(b[i2]&255));
		}
	}
   
   /**
    * Generates ImageIcon from LUTs.
    * Byte Arrays are expected to be 256 in size
    * @param r - red byte array (length 256)
    * @param g - green byte array (length 256)
    * @param b - blue byte array (length 256)
    * @param width - desired width of image
    * @param height - desired height of image
    * @return - generated ImageIcon
    */
   public static ImageIcon getIcon(byte[] r, byte[] g, byte[] b, int width, int height) {
      int[] pixels = new int[width * height];
      double ratio = (double) 256 / (double) width;
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int index = (int) (ratio * x);
            int ri = 0xff & r[index];
            int rg = 0xff & g[index];
            int rb = 0xff & b[index];
            pixels[y * width + x] = ((0xff << 24) | (ri << 16)
                    | (rg << 8) | (rb) );
         }
      }
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      image.setRGB(0, 0, width, height, pixels, 0, width);
      return new ImageIcon(image);
   }
   

   public void saveDisplaySettings() {
      int histMax = histRangeComboBox_.getSelectedIndex() == 0 ? -1 : histMax_;
      display_.storeChannelHistogramSettings(0,  contrastMin_, contrastMax_, gamma_, histMax, 1);
   }

   @Override
   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (channelIndex != 0) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      histRangeComboBox_.setSelectedIndex(index);
   }

   private void updateHistogram() {
      histogramPanel_.setCursorText(contrastMin_+"", contrastMax_+"");
      histogramPanel_.setCursors(contrastMin_ / binSize_, (contrastMax_+1) / binSize_, gamma_);
      histogramPanel_.repaint();
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

   private void setHistMaxAndBinSize() {
      int bits = histRangeComboBox_.getSelectedIndex() + 3;
      if (bits == 3) {
         histMax_ = maxIntensity_;
      } else {
         histMax_ = (int) (Math.pow(2, bits) - 1);
      }
      binSize_ = ((double) (histMax_ + 1)) / ((double) HIST_BINS);

      updateHistogram();

      saveDisplaySettings();
   }

   /**
    * Calculates autostretch, doesnt apply or redraw
    */
   @Override
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
      if (display_.getHistogramControlsState().ignoreOutliers) {
         if (contrastMin_ < minAfterRejectingOutliers_) {
            if (0 < minAfterRejectingOutliers_) {
               contrastMin_ = (int) minAfterRejectingOutliers_;
            }
         }
         if (maxAfterRejectingOutliers_ < contrastMax_) {
            contrastMax_ = (int) maxAfterRejectingOutliers_;
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

   private void setFullScale() {
      setHistMaxAndBinSize();
      display_.disableAutoStretchCheckBox();
      contrastMin_ = 0;
      contrastMax_ = histMax_;
   }

   @Override
    public void imageChanged() {
        boolean update = true;
        if (display_.acquisitionIsRunning() ||
                (MMStudio.getInstance().isLiveModeOn())) {
            if (display_.getHistogramControlsState().slowHist) {
                long time = System.currentTimeMillis();
                if (time - lastUpdateTime_ < SLOW_HIST_UPDATE_INTERVAL_MS) {
                    update = false;
                } else {
                    lastUpdateTime_ = time;
                }
            }
        }

        if (update) {
            calcAndDisplayHistAndStats(display_.isActiveDisplay());
            if (display_.getHistogramControlsState().autostretch) {
                autostretch();
            }
            applyLUTToImage();
        }
    }

   @Override
   public void calcAndDisplayHistAndStats(boolean drawHist) {
      if (img_ == null || img_.getProcessor() == null) {
         return;
      }
      int[] rawHistogram = img_.getProcessor().getHistogram();
      if (rawHistogram == null) { // Histogram is not implemented in ImageJ for FloatProcessor (GRAY32)
          ImageStatistics stats = img_.getStatistics(ImageStatistics.MIN_MAX);
          pixelMax_ = (int) stats.max;
          pixelMin_ = (int) stats.min;
          if (contrastMax_ > 255) {
             contrastMax_ = 255;
          }
          return;
      }
     
      int imgWidth = img_.getWidth();
      int imgHeight = img_.getHeight();
      if (display_.getHistogramControlsState().ignoreOutliers) {
         // todo handle negative values
         maxAfterRejectingOutliers_ = rawHistogram.length;
         // specified percent of pixels are ignored in the automatic contrast setting
         int totalPoints = imgHeight * imgWidth;
         HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, 0.01*display_.getHistogramControlsState().percentToIgnore);
         minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
         maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();
      }
      GraphData histogramData = new GraphData();


      int numBins = (int) Math.min(rawHistogram.length / binSize_, HIST_BINS);
      int[] histogram = new int[HIST_BINS];
      int total = 0;
      for (int i = 0; i < numBins; i++) {
         histogram[i] = 0;
         for (int j = 0; j < binSize_; j++) {
            int rawHistIndex = (int) (i * binSize_ + j);
            int rawHistVal = rawHistogram[rawHistIndex];
            histogram[i] += rawHistVal;
         }
         total += histogram[i];
         if (display_.getHistogramControlsState().logHist) {
            histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
         }
      }

      // work around what is apparently a bug in ImageJ
      if (total == 0) {
         if (img_.getProcessor().getMin() == 0) {
            histogram[0] = imgWidth * imgHeight;
         } else {
            if (numBins > 0) {
               histogram[numBins - 1] = imgWidth * imgHeight;
            }
         }
      }
      if (drawHist) {

         ImageStatistics stats = img_.getStatistics(ImageStatistics.MEAN | ImageStatistics.MIN_MAX | ImageStatistics.STD_DEV);
         pixelMax_ = (int) stats.max;
         pixelMin_ = (int) stats.min;
         mean_ = stats.mean;
         stdDev_ = stats.stdDev;
         
         //Draw histogram and stats
         histogramData.setData(histogram);
         histogramPanel_.setData(histogramData);
         histogramPanel_.setAutoScale();
         histogramPanel_.setToolTipText("Click and drag curve to adjust gamma");

         maxLabel_.setText("Max: " + NumberUtils.intToDisplayString(pixelMax_));
         minLabel_.setText("Min: " + NumberUtils.intToDisplayString(pixelMin_));
         meanLabel_.setText("Mean: " + NumberUtils.intToDisplayString((int) mean_));
         stdDevLabel_.setText("Std Dev: " + NumberUtils.intToDisplayString((int) stdDev_));

         updateHistogram();
      }

   }

   @Override
   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex != 0) {
         return;
      }
      contrastMax_ = Math.min(maxIntensity_,max);
      contrastMin_ = min;
      gamma_ = gamma;
   }
   
   @Override
   public void contrastMinInput(int min) {     
      display_.disableAutoStretchCheckBox();
      
      contrastMin_ = min;
      if (contrastMin_ >= maxIntensity_) {
         contrastMin_ = maxIntensity_ - 1;
      }
      if (contrastMin_ < 0) {
         contrastMin_ = 0;
      }
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }
   
   @Override
   public void contrastMaxInput(int max) {     
      display_.disableAutoStretchCheckBox();
      contrastMax_ = max;
      if (contrastMax_ > maxIntensity_) {
         contrastMax_ = maxIntensity_;
      }
      if (contrastMax_ < 0) {
         contrastMax_ = 0;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   @Override
   public void onLeftCursor(double pos) {
      display_.disableAutoStretchCheckBox();
      
      contrastMin_ = (int) (Math.max(0, pos) * binSize_);
      if (contrastMin_ >= maxIntensity_) {
         contrastMin_ = maxIntensity_ - 1;
      }
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   @Override
   public void onRightCursor(double pos) {
      display_.disableAutoStretchCheckBox();

      contrastMax_ = (int) (Math.min(255, pos) * binSize_);
      if (contrastMax_ < 1) {
         contrastMax_ = 1;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   @Override
   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1) {
            gamma_ = 1;
         } else {
            gamma_ = gamma;
         }
         applyLUTToImage();
         display_.drawWithoutUpdate();
      }
   }

   @Override
   public void setupChannelControls(ImageCache cache) {
   }

   @Override
   public ContrastSettings getChannelContrastSettings(int channel) {
      if (channel != 0) {
         return null;
      }
      return new ContrastSettings(contrastMin_, contrastMax_, gamma_);
   }

   @Override
   public int getNumberOfChannels() {
      return 1;
   }
}
