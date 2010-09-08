///////////////////////////////////////////////////////////////////////////////
//FILE:          ContrastPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$
//
package org.micromanager.graph;

import ij.CompositeImage;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.ImageWindow;

import ij.process.ColorProcessor;
import ij.process.ImageStatistics;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.process.ByteProcessor;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import java.text.NumberFormat;
import java.text.ParseException;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.api.ImageFocusListener;

import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.GammaSliderCalculator;
import org.micromanager.utils.ImageController;
import org.micromanager.utils.MMImageWindow;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.NumberUtils;

/**
 * Slider and histogram panel for adjusting contrast and brightness.
 * 
 */
public class ContrastPanel extends JPanel implements ImageController,
        PropertyChangeListener, ImageFocusListener, ImageListener {
	private static final long serialVersionUID = 1L;
	private JComboBox modeComboBox_;
	private HistogramPanel histogramPanel_;
	private JLabel maxField_;
	private JLabel minField_;
   private JLabel avgField_;
   private JLabel varField_;
	private SpringLayout springLayout;
	private ImagePlus image_;
	private GraphData histogramData_;
	private JSlider sliderLow_;
	private JSlider sliderHigh_;
   private JSlider sliderGamma_;
   private GammaSliderCalculator gammaSliderCalculator_;
   private JFormattedTextField gammaValue_;
   private NumberFormat numberFormat_;
   private double gamma_ = 1.0;
	private int maxIntensity_ = 255;
   private double min_ = 0.0;
   private double max_ = 255.0;
	private int binSize_ = 1;
	private static final int HIST_BINS = 256;
	private int numLevels_ = 256;
   //private DecimalFormat twoDForm_ = new DecimalFormat("#.##");
	ContrastSettings cs8bit_;
	ContrastSettings cs16bit_;
	private JCheckBox stretchCheckBox_;
	private boolean logScale_ = false;
	private JCheckBox logHistCheckBox_;
   private boolean imageUpdated_;
   private boolean liveWindow_;
   private boolean liveStretchMode_;

	/**
	 * Create the panel
	 */
	public ContrastPanel() {
		super();
		setToolTipText("Switch between linear and log histogram");
		setFont(new Font("", Font.PLAIN, 10));
		springLayout = new SpringLayout();
		setLayout(springLayout);

      numberFormat_ = NumberFormat.getNumberInstance();

		final JButton fullScaleButton_ = new JButton();
		fullScaleButton_.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setFullScale();
			}
		});
		fullScaleButton_.setFont(new Font("Arial", Font.PLAIN, 10));
		fullScaleButton_
				.setToolTipText("Set display levels to full pixel range");
		fullScaleButton_.setText("Full");
		add(fullScaleButton_);
		springLayout.putConstraint(SpringLayout.EAST, fullScaleButton_, 80,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, fullScaleButton_, 5,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, fullScaleButton_, 25,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, fullScaleButton_, 5,
				SpringLayout.NORTH, this);

		final JButton autoScaleButton = new JButton();
		autoScaleButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setAutoScale();
			}
		});
		autoScaleButton.setFont(new Font("Arial", Font.PLAIN, 10));
		autoScaleButton
				.setToolTipText("Set display levels to maximum contrast");
		autoScaleButton.setText("Auto");
		add(autoScaleButton);
		springLayout.putConstraint(SpringLayout.EAST, autoScaleButton, 80,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, autoScaleButton, 5,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, autoScaleButton, 46,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, autoScaleButton, 26,
				SpringLayout.NORTH, this);

		minField_ = new JLabel();
		minField_.setFont(new Font("", Font.PLAIN, 10));
		add(minField_);
		springLayout.putConstraint(SpringLayout.EAST, minField_, 95,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, minField_, 45,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, minField_, 78,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, minField_, 64,
				SpringLayout.NORTH, this);

		maxField_ = new JLabel();
		maxField_.setFont(new Font("", Font.PLAIN, 10));
		add(maxField_);
		springLayout.putConstraint(SpringLayout.EAST, maxField_, 95,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, maxField_, 45,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, maxField_, 94,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, maxField_, 80,
				SpringLayout.NORTH, this);

		JLabel minLabel = new JLabel();
		minLabel.setFont(new Font("", Font.PLAIN, 10));
		minLabel.setText("Min");
		add(minLabel);
		springLayout.putConstraint(SpringLayout.SOUTH, minLabel, 78,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, minLabel, 64,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.EAST, minLabel, 30,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, minLabel, 5,
				SpringLayout.WEST, this);

		JLabel maxLabel = new JLabel();
		maxLabel.setFont(new Font("", Font.PLAIN, 10));
		maxLabel.setText("Max");
		add(maxLabel);
		springLayout.putConstraint(SpringLayout.SOUTH, maxLabel, 94,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, maxLabel, 80,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.EAST, maxLabel, 30,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, maxLabel, 5,
				SpringLayout.WEST, this);

      JLabel avgLabel = new JLabel();
      avgLabel.setFont(new Font("", Font.PLAIN, 10));
      avgLabel.setText("Avg");
      add(avgLabel);
      springLayout.putConstraint(SpringLayout.EAST, avgLabel, 42, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, avgLabel, 5, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, avgLabel, 110, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, avgLabel, 96, SpringLayout.NORTH, this);

      avgField_ = new JLabel();                                              
      avgField_.setFont(new Font("", Font.PLAIN, 10));                       
      add(avgField_);                                                        
      springLayout.putConstraint(SpringLayout.EAST, avgField_, 95, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, avgField_, 45, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, avgField_, 110, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, avgField_, 96, SpringLayout.NORTH, this);
                                                                             
      JLabel varLabel = new JLabel();
      varLabel.setFont(new Font("", Font.PLAIN, 10));
      varLabel.setText("Std Dev");
      add(varLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, varLabel, 126, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, varLabel, 112, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.EAST, varLabel, 42, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, varLabel, 5, SpringLayout.WEST, this);

      varField_ = new JLabel();                                              
      varField_.setFont(new Font("", Font.PLAIN, 10));                       
      add(varField_);
      springLayout.putConstraint(SpringLayout.EAST, varField_, 95, SpringLayout.WEST, this); 
      springLayout.putConstraint(SpringLayout.WEST, varField_, 45, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, varField_, 126, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, varField_, 112, SpringLayout.NORTH, this);

		sliderLow_ = new JSlider();
		sliderLow_.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				onSliderMove();
			}
		});
		sliderLow_.setToolTipText("Minimum display level");
		add(sliderLow_);

		sliderHigh_ = new JSlider();
		sliderHigh_.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				onSliderMove();
			}
		});
		sliderHigh_.setToolTipText("Maximum display level");
		add(sliderHigh_);
		springLayout.putConstraint(SpringLayout.EAST, sliderHigh_, -1,
				SpringLayout.EAST, this);
		springLayout.putConstraint(SpringLayout.WEST, sliderHigh_, 0,
				SpringLayout.WEST, sliderLow_);

      final int gammaLow = 0;
      final int gammaHigh = 100;
      gammaSliderCalculator_ = new GammaSliderCalculator(gammaLow, gammaHigh);

		sliderGamma_ = new JSlider();
		sliderGamma_.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				onGammaSliderMove();
			}
		});
		sliderGamma_.setToolTipText("Gamma");
		sliderGamma_.setMinimum(gammaLow);
		sliderGamma_.setMaximum(gammaHigh);
		add(sliderGamma_);
		springLayout.putConstraint(SpringLayout.EAST, sliderGamma_, -1,
				SpringLayout.EAST, this);
		springLayout.putConstraint(SpringLayout.WEST, sliderGamma_, 0,
				SpringLayout.WEST, sliderLow_);

      JLabel gammaLabel = new JLabel();
      gammaLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      gammaLabel.setPreferredSize(new Dimension(40, 15));
      gammaLabel.setText("Gamma");
      add(gammaLabel);
		springLayout.putConstraint(SpringLayout.WEST, gammaLabel, 5,
				SpringLayout.WEST, this);

      gammaValue_ = new JFormattedTextField(numberFormat_);
      gammaValue_.setFont(new Font("Arial", Font.PLAIN, 10));
      gammaValue_.setValue(gamma_);
      gammaValue_.addPropertyChangeListener("value", this);
      gammaValue_.setPreferredSize(new Dimension(35, 15));
      add(gammaValue_);
		springLayout.putConstraint(SpringLayout.WEST, gammaValue_, 45,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.EAST, gammaValue_, 95,
				SpringLayout.WEST, this);

		histogramPanel_ = new HistogramPanel();
		histogramPanel_.setMargins(1, 1);
		histogramPanel_.setTextVisible(false);
		histogramPanel_.setGridVisible(false);
		add(histogramPanel_);
		springLayout.putConstraint(SpringLayout.EAST, histogramPanel_, -5,
				SpringLayout.EAST, this);
		springLayout.putConstraint(SpringLayout.WEST, histogramPanel_, 100,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, sliderLow_, 29,
				SpringLayout.SOUTH, histogramPanel_);
		springLayout.putConstraint(SpringLayout.NORTH, sliderLow_, 4,
				SpringLayout.SOUTH, histogramPanel_);
		springLayout.putConstraint(SpringLayout.SOUTH, sliderHigh_, 53,
				SpringLayout.SOUTH, histogramPanel_);
		springLayout.putConstraint(SpringLayout.NORTH, sliderHigh_, 28,
				SpringLayout.SOUTH, histogramPanel_);
		springLayout.putConstraint(SpringLayout.SOUTH, sliderGamma_, 77,
				SpringLayout.SOUTH, histogramPanel_);
		springLayout.putConstraint(SpringLayout.SOUTH, gammaValue_, 0,
				SpringLayout.SOUTH, sliderGamma_);
		springLayout.putConstraint(SpringLayout.NORTH, gammaValue_, 0,
				SpringLayout.NORTH, sliderGamma_);
		springLayout.putConstraint(SpringLayout.SOUTH, gammaLabel, 0,
				SpringLayout.SOUTH, sliderGamma_);
		springLayout.putConstraint(SpringLayout.NORTH, gammaLabel, 0,
				SpringLayout.NORTH, sliderGamma_);
		springLayout.putConstraint(SpringLayout.SOUTH, histogramPanel_, -81,
				SpringLayout.SOUTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, histogramPanel_, 0,
				SpringLayout.NORTH, fullScaleButton_);

		stretchCheckBox_ = new JCheckBox();
		stretchCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		stretchCheckBox_.setText("Auto-stretch");
		stretchCheckBox_.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
            if (stretchCheckBox_.isSelected())
               setAutoScale();
				sliderLow_.setEnabled(!stretchCheckBox_.isSelected());
				sliderHigh_.setEnabled(!stretchCheckBox_.isSelected());
			};
		});
		add(stretchCheckBox_);
		springLayout.putConstraint(SpringLayout.EAST, sliderLow_, -1,
				SpringLayout.EAST, this);
		springLayout.putConstraint(SpringLayout.WEST, sliderLow_, 0,
				SpringLayout.EAST, stretchCheckBox_);
		springLayout.putConstraint(SpringLayout.EAST, stretchCheckBox_, -2,
				SpringLayout.WEST, histogramPanel_);
		springLayout.putConstraint(SpringLayout.WEST, stretchCheckBox_, 1,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, stretchCheckBox_, 185,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, stretchCheckBox_, 160,
				SpringLayout.NORTH, this);

		modeComboBox_ = new JComboBox();
		modeComboBox_.setFont(new Font("", Font.PLAIN, 10));
		modeComboBox_.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setIntensityMode(modeComboBox_.getSelectedIndex()-1);
			}
		});
		modeComboBox_.setModel(new DefaultComboBoxModel(new String[] {
				"camera", "8bit", "10bit", "12bit", "14bit", "16bit" }));
		add(modeComboBox_);
		springLayout.putConstraint(SpringLayout.EAST, modeComboBox_, 0,
				SpringLayout.EAST, maxField_);
		springLayout.putConstraint(SpringLayout.WEST, modeComboBox_, 0,
				SpringLayout.WEST, minLabel);
		springLayout.putConstraint(SpringLayout.SOUTH, modeComboBox_, 27,
				SpringLayout.SOUTH, varLabel);
		springLayout.putConstraint(SpringLayout.NORTH, modeComboBox_, 5,
				SpringLayout.SOUTH, varLabel);

		logHistCheckBox_ = new JCheckBox();
		logHistCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		logHistCheckBox_.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (logHistCheckBox_.isSelected())
					logScale_ = true;
				else
					logScale_ = false;

				update();
			}
		});
		logHistCheckBox_.setText("Log hist.");
		add(logHistCheckBox_);
		springLayout.putConstraint(SpringLayout.SOUTH, logHistCheckBox_, 0,
				SpringLayout.NORTH, minField_);
		springLayout.putConstraint(SpringLayout.NORTH, logHistCheckBox_, -18,
				SpringLayout.NORTH, minField_);
		springLayout.putConstraint(SpringLayout.EAST, logHistCheckBox_, 74,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, logHistCheckBox_, 1,
				SpringLayout.WEST, this);

      ImagePlus.addImageListener(this);
      GUIUtils.registerImageFocusListener(this);
	}

   public void setPixelBitDepth(int depth, boolean forceDepth) 
   { 
	   // histogram for 32bits is not supported in this implementation 
      if(depth >= 32)
	      depth = 8; 
      numLevels_ = 1 << depth; 
      maxIntensity_ = numLevels_ - 1;
	   binSize_ = (maxIntensity_ + 1)/ HIST_BINS;
	  
	   // override histogram depth based on the selected mode 
	   if (!forceDepth && modeComboBox_.getSelectedIndex() > 0) {
         setIntensityMode(modeComboBox_.getSelectedIndex()-1); 
      }
	  
	   if (forceDepth) { // update the mode display to camera-auto
	       modeComboBox_.setSelectedIndex(0); 
       } 
   }

   public void setSingleProcessorGamma(double gamma_, ImageProcessor ip, int colIndex) {
      if (ip == null)
         return;

      double maxValue = 255.0;
      byte[] r = new byte[256];
      byte[] g = new byte[256];
      byte[] b = new byte[256];
      for (int i = 0; i < 256; i++) {
         double val = Math.pow((double) i / maxValue, gamma_) * (double) maxValue;
         r[i] = (byte) ((colIndex == 0 || colIndex == 1) ? val : 0);
         g[i] = (byte) ((colIndex == 0 || colIndex == 2) ? val : 0);
         b[i] = (byte) ((colIndex == 0 || colIndex == 3) ? val : 0);
      }
      LUT lut = new LUT(8, 256, r, g, b);
      ip.setColorModel(lut);
      image_.updateAndDraw();
   }

   public void updateHistogram() {
      if (image_ != null) {
         int[] rawHistogram = image_.getProcessor().getHistogram();
         if (histogramData_ == null) {
            histogramData_ = new GraphData();
         } // 256 bins
         int[] histogram = new int[HIST_BINS];
         int limit = Math.min(rawHistogram.length / binSize_, HIST_BINS);
         int total = 0;
         for (int i = 0; i < limit; i++) {
            histogram[i] = 0;
            for (int j = 0; j < binSize_; j++) {
               histogram[i] += rawHistogram[i * binSize_ + j];
            }
            total += histogram[i];
         }
         // work around what is apparently a bug in ImageJ
         if (total == 0) {
            if (image_.getProcessor().getMin() == 0) {
               histogram[0] = image_.getWidth() * image_.getHeight();
            } else {
               histogram[limit - 1] = image_.getWidth() * image_.getHeight();
            }
         }
         if (logScale_) {
            for (int i = 0; i < histogram.length; i++) {
               histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
            }
         }

         histogramData_.setData(histogram);
         histogramPanel_.setGamma(gammaSliderCalculator_.gammaToSlider(gamma_));
         histogramPanel_.setData(histogramData_);
         histogramPanel_.setAutoScale();
         ImageStatistics stats = image_.getStatistics();
         maxField_.setText(NumberUtils.intToDisplayString((int) stats.max));
         max_ = stats.max;
         minField_.setText(NumberUtils.intToDisplayString((int) stats.min));
         min_ = stats.min;
         avgField_.setText(NumberUtils.intToDisplayString((int) stats.mean));
         //varField_.setText(Double.toString(Double.valueOf(twoDForm_.format(stats.stdDev))));
         varField_.setText(NumberUtils.doubleToDisplayString(stats.stdDev));
         if (min_ == max_) {
            if (min_ == 0) {
               max_ += 1;
            } else {
               min_ -= 1;
            }
         }
         histogramPanel_.repaint();
      }
   }
	 
	private void setIntensityMode(int mode) {
		switch (mode) {
		case 0: 
			maxIntensity_ = 255;
			break;
		case 1:
			maxIntensity_ = 1023;
			break;
		case 2:
			maxIntensity_ = 4095;
			break;
		case 3:
			maxIntensity_ = 16383;
			break;
		case 4:
			maxIntensity_ = 65535;
			break;
		default:
			break;
		}
		binSize_ = (maxIntensity_ + 1) / HIST_BINS;
		update();
	}

	protected void onSliderMove() {
		int min = sliderLow_.getValue();
		int max = sliderHigh_.getValue();

		// correct slider relative positions if necessary
      if (min >= max) {
         if (sliderHigh_.getValueIsAdjusting() && max > 0)
            min = max - 1;
         else 
            if (max < maxIntensity_)
               max += 1;

         sliderHigh_.setValue(max);
         sliderLow_.setValue(min);
      }

		updateCursors();

      applyContrastSettings();
	}

	protected void onGammaSliderMove() {
		gamma_ = gammaSliderCalculator_.sliderToGamma(sliderGamma_.getValue());
      if (gammaValue_ != null) {
         // gamma_ = Double.valueOf(numberFormat_.format(gamma_));
         gammaValue_.setValue(gamma_);
      }
	}

   // only used for Gamma
   public void propertyChange(PropertyChangeEvent e) {
      try { 
         gamma_ = (double) NumberUtils.displayStringToDouble(numberFormat_.format(gammaValue_.getValue()));
      } catch (ParseException p) {
         ReportingUtils.logError(p, "ContrastPanel, Function propertyChange");
      }
      sliderGamma_.setValue(gammaSliderCalculator_.gammaToSlider(gamma_));
      setLutGamma(gamma_);
      updateCursors();
   }

   private void setLutGamma(double gamma_) {
       if (image_ == null)
          return;

       //if (gamma_ == 1)
       //    return;
       
       // TODO: deal with color images
       if (image_.getProcessor() instanceof ColorProcessor)
          return;

       if (!(image_ instanceof CompositeImage)) {
          ImageProcessor ip = image_.getProcessor();
              setSingleProcessorGamma(gamma_, ip, 0);
       } else {
          for (int i=1;i<=3;++i) {
             ImageProcessor ip = ((CompositeImage) image_).getProcessor(i);
             setSingleProcessorGamma(gamma_, ip,  i);
          }
       }
   }


	public void update() {
      // calculate histogram
      if (image_ == null || image_.getProcessor() == null)
         return;
      if (stretchCheckBox_.isSelected()) {
         setAutoScale();
      }
      updateHistogram();
      setLutGamma(gamma_);
      updateSliders();

      image_.updateAndDraw();
	}

	private void updateSliders(boolean force, int min, int max) {

		// set sliders without activating them.
		ChangeListener[] l1 = sliderLow_.getChangeListeners();
		ChangeListener[] l2 = sliderHigh_.getChangeListeners();
		
		for (ChangeListener l:l1)
			sliderLow_.removeChangeListener(l);
		for (ChangeListener l:l2)
			sliderHigh_.removeChangeListener(l);		

		sliderLow_.setMinimum(0);
		sliderLow_.setMaximum(maxIntensity_);
      if (!sliderLow_.isEnabled() || force)
         sliderLow_.setValue(min);

		sliderHigh_.setMinimum(0);
		sliderHigh_.setMaximum(maxIntensity_);
      if (!sliderHigh_.isEnabled() || force) {
         sliderHigh_.setValue(max);
      }

		for (ChangeListener l:l1)
			sliderLow_.addChangeListener(l);
		for (ChangeListener l:l2)
			sliderHigh_.addChangeListener(l);
		
		updateCursors();
	}

   private void updateSliders(boolean force) {
      updateSliders(force, Math.max((int)min_, 0), Math.min((int)max_, maxIntensity_));
   }

   private void updateSliders() {
      updateSliders(false);
   }

	// override from ImageController
	public void setImagePlus(ImagePlus ip, ContrastSettings cs8bit,
			ContrastSettings cs16bit) {
      cs8bit_ = cs8bit;
      cs16bit_ = cs16bit;
		image_ = ip;
		setIntensityMode(modeComboBox_.getSelectedIndex()-1);
	}

	/**
	 * Auto-scales image display to clip at minimum and maximum pixel values.
	 * 
	 */
	private void setAutoScale() {
		if (image_ == null) {
			return;
      }

      liveStretchMode_ = true;

      // protect against an 'Unhandled Exception' inside getStatistics
      if ( null != image_.getProcessor()){
         ImageStatistics stats = image_.getStatistics();

         int min = (int) stats.min;
         int max = (int) stats.max;
         if (min == max)
            if (min == 0)
               max += 1;
            else
               min -= 1;

         updateSliders(true, min, max);
      }
      else{
         ReportingUtils.logError("Internal error: ImageProcessor is null");
      }

		image_.updateAndDraw();
	}


	private void setFullScale() {
		if (image_ == null)
			return;

      image_.getProcessor().setMinAndMax(0, maxIntensity_);

		updateSliders(true, 0, maxIntensity_);

		image_.updateAndDraw();
	}

	private void updateCursors() {
		if (image_ == null)
			return;

		histogramPanel_.setCursors(sliderLow_.getValue() / binSize_,
				sliderHigh_.getValue() / binSize_,
            gammaSliderCalculator_.gammaToSlider(gamma_));
		histogramPanel_.repaint();
		if (cs8bit_ == null || cs16bit_ == null)
			return;

		if (image_.getProcessor() != null) {
			// record settings
			if (image_.getProcessor() instanceof ShortProcessor) {
				cs16bit_.min = sliderLow_.getValue();
				cs16bit_.max = sliderHigh_.getValue();
            image_.getProcessor().setMinAndMax(cs16bit_.min, cs16bit_.max);
			} else {
				cs8bit_.min = sliderLow_.getValue();
				cs8bit_.max = sliderHigh_.getValue();
            image_.getProcessor().setMinAndMax(cs8bit_.min, cs8bit_.max);
			}
		}
	}

	public void setContrastSettings(ContrastSettings cs8bit,
			ContrastSettings cs16bit) {
		cs8bit_ = cs8bit;
		cs16bit_ = cs16bit;

	}

	public void applyContrastSettings() {
		applyContrastSettings(cs8bit_, cs16bit_);
	};

   public void applyContrastSettings(ContrastSettings contrast8,
           ContrastSettings contrast16) {
      applyContrastSettings(image_, contrast8, contrast16);  
   }

	public void applyContrastSettings(ImagePlus img, ContrastSettings contrast8,
			ContrastSettings contrast16) {
		if (img == null)
			return;

      if (!(img instanceof CompositeImage)) {
         applyContrastSettings(img.getProcessor(),
                 contrast8, contrast16);
      } else {
         for (int i=1;i<=3;++i) {
            ImageProcessor proc = ((CompositeImage) img).getProcessor(i);
            applyContrastSettings(proc,
                 contrast8, contrast16);
         }
      }
		updateSliders();

		img.updateAndDraw();
	}

   public void applyContrastSettings(ImageProcessor proc,
           ContrastSettings contrast8, ContrastSettings contrast16) {
      if (proc == null)
         return;

     if (proc instanceof ShortProcessor) {
        proc.setMinAndMax(contrast16.min, contrast16.max);
     } else if (proc instanceof ByteProcessor) {
        proc.setMinAndMax(contrast8.min, contrast8.max);
     }

   }

	public void setContrastStretch(boolean stretch) {
		stretchCheckBox_.setSelected(stretch);
	}

	public boolean isContrastStretch() {
		return stretchCheckBox_.isSelected();
	}

   public ContrastSettings getContrastSettings() {
      ContrastSettings ret = cs8bit_;
      if( null != image_) {
         if (image_.getProcessor() instanceof ShortProcessor)
            ret = cs16bit_;
         else
            ret = cs8bit_;
      }
      return ret;
   }

   private void updateStretchBox() {
      if (liveWindow_) {
         stretchCheckBox_.setEnabled(true);
         stretchCheckBox_.setSelected(liveStretchMode_);
      } else {
         stretchCheckBox_.setEnabled(false);
         stretchCheckBox_.setSelected(false);
      }
   }

   public void focusReceived(ImageWindow focusedWindow) {
      ImagePlus imgp = focusedWindow.getImagePlus();
      liveWindow_ = (focusedWindow instanceof MMImageWindow);
      if (!liveWindow_)
         return;
      updateStretchBox();


//      ImageProcessor proc = imgp.getChannelProcessor();
      double min = imgp.getDisplayRangeMin();
      double max = imgp.getDisplayRangeMax();
        setImagePlus(imgp, new ContrastSettings(min, max), new ContrastSettings(min, max));
              imageUpdated(imgp);
//      update();
   }

   public void imageOpened(ImagePlus ip) {
      update();
   }

   public void imageClosed(ImagePlus ip) {
      update();
   }

   public void imageUpdated(ImagePlus ip) {
      if (liveWindow_ && !imageUpdated_) {
         System.out.println("imagedUpdated()");
         System.out.println(ip.getDisplayRangeMax());
         imageUpdated_ = true;
         updateHistogram();
         double min = ip.getDisplayRangeMin();
         double max = ip.getDisplayRangeMax();
         setContrastSettings(new ContrastSettings(min, max), new ContrastSettings(min, max));
         updateSliders(true, (int) ip.getDisplayRangeMin(), (int) ip.getDisplayRangeMax());
         imageUpdated_ = false;
      }
   }

}