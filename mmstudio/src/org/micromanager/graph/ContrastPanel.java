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

import ij.ImagePlus;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.utils.ContrastSettings;

/**
 * Slider and histogram panel for adjusting contrast and brightness.
 *
 */
public class ContrastPanel extends JPanel {
   private static final long serialVersionUID = 1L;
   private JComboBox modeComboBox_;
   private HistogramPanel histogramPanel_;
   private JLabel maxField_;
   private JLabel minField_;
   private SpringLayout springLayout;
   private ImagePlus image_;
   private GraphData histogramData_;
   private JLabel maxLabel_;
   private JLabel minLabel_;
   private JSlider sliderLow_;
   private JSlider sliderHigh_;  
   private int maxIntensity_ = 255;
   private int binSize_ = 1;
   private static final int HIST_BINS = 256;
//   int defMode8bit_ = 0;
//   int defMode16bit_ = 4;
   int numLevels_ = 256;
   ContrastSettings cs8bit_;
   ContrastSettings cs16bit_;
   private JCheckBox stretchCheckBox_;
   private boolean logScale_ = false;
   private JCheckBox logHistCheckBox_;
   
   /**
    * Create the panel
    */
   public ContrastPanel() {
      super();
      setToolTipText("Switch between linear and log histogram");
      setFont(new Font("", Font.PLAIN, 10));
      springLayout = new SpringLayout();
      setLayout(springLayout);
      
      final JButton fullScaleButton_ = new JButton();
      fullScaleButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setFullScale();
         }
      });
      fullScaleButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      fullScaleButton_.setToolTipText("Set display levels to full pixel range");
      fullScaleButton_.setText("Full");
      add(fullScaleButton_);
      springLayout.putConstraint(SpringLayout.EAST, fullScaleButton_, 80, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, fullScaleButton_, 5, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, fullScaleButton_, 25, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, fullScaleButton_, 5, SpringLayout.NORTH, this);

      final JButton autoScaleButton = new JButton();
      autoScaleButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setAutoScale();
         }
      });
      autoScaleButton.setFont(new Font("Arial", Font.PLAIN, 10));
      autoScaleButton.setToolTipText("Set display levels to maximum contrast");
      autoScaleButton.setText("Auto");
      add(autoScaleButton);
      springLayout.putConstraint(SpringLayout.EAST, autoScaleButton, 80, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, autoScaleButton, 5, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, autoScaleButton, 46, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, autoScaleButton, 26, SpringLayout.NORTH, this);

      minField_ = new JLabel();
      minField_.setFont(new Font("", Font.PLAIN, 10));
      add(minField_);
      springLayout.putConstraint(SpringLayout.EAST, minField_, 80, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, minField_, 29, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, minField_, 78, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, minField_, 64, SpringLayout.NORTH, this);

      maxField_ = new JLabel();
      maxField_.setFont(new Font("", Font.PLAIN, 10));
      add(maxField_);
      springLayout.putConstraint(SpringLayout.EAST, maxField_, 80, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, maxField_, 29, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, maxField_, 94, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, maxField_, 80, SpringLayout.NORTH, this);

      minLabel_ = new JLabel();
      minLabel_.setFont(new Font("", Font.PLAIN, 10));
      minLabel_.setText("Min");
      add(minLabel_);
      springLayout.putConstraint(SpringLayout.SOUTH, minLabel_, 78, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, minLabel_, 64, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.EAST, minLabel_, 30, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, minLabel_, 5, SpringLayout.WEST, this);

      maxLabel_ = new JLabel();
      maxLabel_.setFont(new Font("", Font.PLAIN, 10));
      maxLabel_.setText("Max");
      add(maxLabel_);
      springLayout.putConstraint(SpringLayout.SOUTH, maxLabel_, 94, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, maxLabel_, 80, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.EAST, maxLabel_, 30, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, maxLabel_, 5, SpringLayout.WEST, this);

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
      springLayout.putConstraint(SpringLayout.EAST, sliderHigh_, -1, SpringLayout.EAST, this);
      springLayout.putConstraint(SpringLayout.WEST, sliderHigh_, 0, SpringLayout.WEST, sliderLow_);

      histogramPanel_ = new HistogramPanel();
      histogramPanel_.setMargins(1, 1);
      histogramPanel_.setTextVisible(false);
      histogramPanel_.setGridVisible(false);
      add(histogramPanel_);
      springLayout.putConstraint(SpringLayout.EAST, histogramPanel_, -5, SpringLayout.EAST, this);
      springLayout.putConstraint(SpringLayout.WEST, histogramPanel_, 100, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, sliderLow_, 29, SpringLayout.SOUTH, histogramPanel_);
      springLayout.putConstraint(SpringLayout.NORTH, sliderLow_, 4, SpringLayout.SOUTH, histogramPanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, sliderHigh_, 53, SpringLayout.SOUTH, histogramPanel_);
      springLayout.putConstraint(SpringLayout.NORTH, sliderHigh_, 28, SpringLayout.SOUTH, histogramPanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, histogramPanel_, -57, SpringLayout.SOUTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, histogramPanel_, 0, SpringLayout.NORTH, fullScaleButton_);

      stretchCheckBox_ = new JCheckBox();
      stretchCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      stretchCheckBox_.setText("Auto-stretch");
      add(stretchCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, sliderLow_, -1, SpringLayout.EAST, this);
      springLayout.putConstraint(SpringLayout.WEST, sliderLow_, 0, SpringLayout.EAST, stretchCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, stretchCheckBox_, -5, SpringLayout.WEST, histogramPanel_);
      springLayout.putConstraint(SpringLayout.WEST, stretchCheckBox_, 5, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, stretchCheckBox_, 145, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, stretchCheckBox_, 120, SpringLayout.NORTH, this);

      modeComboBox_ = new JComboBox();
      modeComboBox_.setFont(new Font("", Font.PLAIN, 10));
      modeComboBox_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            if (modeComboBox_.getSelectedIndex() > 0)
               setIntensityMode(modeComboBox_.getSelectedIndex()-1);
            
            update();
         }
      });
      modeComboBox_.setModel(new DefaultComboBoxModel(new String[] {"camera", "8bit", "10bit", "12bit", "14bit", "16bit"}));
      add(modeComboBox_);
      springLayout.putConstraint(SpringLayout.EAST, modeComboBox_, 0, SpringLayout.EAST, maxField_);
      springLayout.putConstraint(SpringLayout.WEST, modeComboBox_, 0, SpringLayout.WEST, minLabel_);
      springLayout.putConstraint(SpringLayout.SOUTH, modeComboBox_, 27, SpringLayout.SOUTH, maxLabel_);
      springLayout.putConstraint(SpringLayout.NORTH, modeComboBox_, 5, SpringLayout.SOUTH, maxLabel_);

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
      springLayout.putConstraint(SpringLayout.SOUTH, logHistCheckBox_, 0, SpringLayout.NORTH, minField_);
      springLayout.putConstraint(SpringLayout.NORTH, logHistCheckBox_, -18, SpringLayout.NORTH, minField_);
      springLayout.putConstraint(SpringLayout.EAST, logHistCheckBox_, 74, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, logHistCheckBox_, 3, SpringLayout.WEST, this);
      //
   }
   
   public void setContrastSettings(ContrastSettings cs8bit, ContrastSettings cs16bit) {
      cs8bit_ = cs8bit;
      cs16bit_ = cs16bit;
   }
   
   public void setPixelBitDepth(int depth, boolean forceDepth) {
      numLevels_ = 1 << depth;
      maxIntensity_ = numLevels_ - 1;
      binSize_ = (maxIntensity_ + 1)/ HIST_BINS;
      
      // override histogram depth based on the selected mode
      if (!forceDepth && modeComboBox_.getSelectedIndex() > 0) {
         setIntensityMode(modeComboBox_.getSelectedIndex()-1);
      }
      
      if (forceDepth) {
         // update the mode display to camera-aouto
         modeComboBox_.setSelectedIndex(0);
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
      binSize_ = (maxIntensity_ + 1)/ HIST_BINS;
      update();
   }

   protected void onSliderMove() {
      if (image_ == null)
         return;
      
      int min = sliderLow_.getValue();
      int max = sliderHigh_.getValue();
      
      // correct slider relative positions if necessary
      if (max < min) {
         max = min;
         sliderHigh_.setValue(max);
         sliderLow_.setValue(min);
      }
      
      minField_.setText(Integer.toString(min));
      maxField_.setText(Integer.toString(max));
      if (image_.getProcessor() != null) {
         image_.getProcessor().setMinAndMax(min, max);
         image_.updateAndDraw();
      }
      
      updateCursors();
   }

   public void update(){      
      // calculate histogram
      if (image_ == null || image_.getProcessor() == null)
         return;
      int rawHistogram[] = image_.getProcessor().getHistogram();
      if (histogramData_ == null)
         histogramData_ = new GraphData();
      
      // preprocess histogram to conform to the current mode and to use only 256 bins
      int histogram[] = new int[HIST_BINS];
      int limit = Math.min(rawHistogram.length / binSize_, HIST_BINS);
      
      for (int i=0; i<limit; i++) {
         histogram[i] = 0;
         for (int j=0; j<binSize_; j++) {
            histogram[i] += rawHistogram[i*binSize_+j];
         }
      }
      
      // log scale
      if (logScale_) {
         for (int i=0; i<histogram.length; i++)
            histogram[i] = histogram[i] > 0 ? (int)(1000 * Math.log(histogram[i])) : 0;
      }
      
      if (stretchCheckBox_.isSelected())
         setAutoScale();

      histogramData_.setData(histogram);
      histogramPanel_.setData(histogramData_);
      histogramPanel_.setAutoScale();
      
      updateSliders();
      updateCursors();
   }

   private void updateSliders() {
      if (image_ == null)
         return;
      
      // update limits
      double min = 0;
      double max = maxIntensity_;
      
      if (image_.getProcessor() != null) {
         min = image_.getProcessor().getMin();
         max = image_.getProcessor().getMax();
      }
      minField_.setText(Integer.toString((int)min));
      maxField_.setText(Integer.toString((int)max));
      
      // set sliders
      sliderLow_.setMinimum(0);
      sliderHigh_.setMinimum(0);
      sliderLow_.setMaximum(maxIntensity_);
      sliderHigh_.setMaximum(maxIntensity_);
      
      sliderLow_.setValue(Math.max((int)min, 0));
      sliderHigh_.setValue(Math.min((int)max, maxIntensity_));
   }
   
   public void setImagePlus(ImagePlus ip) {
      image_ = ip;
   }
   
   /**
    * Auto-scales image display to clip at minimum and maximum pixel values.
    *
    */
   private void setAutoScale() {
      if (image_ == null)
         return;
      
      ImageStatistics stats = image_.getStatistics(); // get uncalibrated stats
      double min = stats.min;
      double max = stats.max;
      image_.getProcessor().setMinAndMax(min, max);      
      image_.updateAndDraw();
      updateSliders();
      updateCursors();
   }
   
   private void setFullScale() {
      if (image_ == null)
         return;
      
      if (image_.getProcessor() instanceof ShortProcessor){
         maxIntensity_ = 65535;
         image_.getProcessor().setMinAndMax(0, 65535);
      } else {
         image_.getProcessor().resetMinAndMax();
         maxIntensity_ = 255;
      }
      image_.updateAndDraw();
      updateSliders();
      updateCursors();
   }

   private void updateCursors() {
      if (image_ == null)
         return;
      
      histogramPanel_.setCursors(sliderLow_.getValue()/binSize_, sliderHigh_.getValue()/binSize_);
      image_.updateAndDraw();
      updateSliders();
      histogramPanel_.repaint();      
      if (cs8bit_ == null || cs16bit_ == null)
         return;
      
      // record settings
      if (image_.getProcessor() instanceof ShortProcessor){
         cs16bit_.min = sliderLow_.getValue();
         cs16bit_.max = sliderHigh_.getValue();         
      } else {
         cs8bit_.min = sliderLow_.getValue();
         cs8bit_.max = sliderHigh_.getValue();         
      }
   }

   public void applyContrastSettings(ContrastSettings contrast8, ContrastSettings contrast16) {
      if (image_ == null)
         return;
      
      if (image_.getProcessor() instanceof ShortProcessor){
         image_.getProcessor().setMinAndMax(contrast16.min, contrast16.max);
      } else {
         image_.getProcessor().setMinAndMax(contrast8.min, contrast8.max);
      }
      image_.updateAndDraw();
      updateSliders();
      updateCursors();
   }

   public void setContrastStretch(boolean stretch) {
      stretchCheckBox_.setSelected(stretch);      
   }
   
   public boolean isContrastStretch() {
      return stretchCheckBox_.isSelected();
   }
}


