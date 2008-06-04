///////////////////////////////////////////////////////////////////////////////
//FILE:          LUTDialog.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006
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

package org.micromanager.utils;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Brightness & contrast dialog.
 *
 */
public class LUTDialog extends MMDialog {
   private static final long serialVersionUID = -2130943996011385864L;
   private JSlider brightSlider_;
   private JTextField maxLevel_;
   private JTextField minLevel_;
   private ImageWindow imgWin_;
   private JButton fullScaleButton_12_;
   private JButton fullScaleButton_14_;
   private JSlider contrastSlider_;
   int defaultMinC_ = 0;
   int defaultMaxC_ = 0;
   int defaultMinB_ = 0;
   int defaultMaxB_ = 0;
   ContrastSettings settings_;
   
   public LUTDialog(ContrastSettings s) {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowClosed(WindowEvent e) {
            savePosition();
         }
      });
      getContentPane().setLayout(null);
      setTitle("Image display settings");
      loadPosition(100, 100, 351, 183);
      //setBounds(100, 100, 351, 183);
      
      if (s == null)
         settings_ = new ContrastSettings();
      else
         settings_ = s;

      final JLabel minLabel = new JLabel();
      minLabel.setText("Min");
      minLabel.setBounds(13, 15, 45, 21);
      getContentPane().add(minLabel);

      final JLabel maxLabel = new JLabel();
      maxLabel.setText("Max");
      maxLabel.setBounds(13, 38, 45, 21);
      getContentPane().add(maxLabel);

      contrastSlider_ = new JSlider();
      contrastSlider_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            contrastChanged();
         }
      });
      contrastSlider_.setPaintLabels(true);
      contrastSlider_.setPaintTicks(true);
      contrastSlider_.setBounds(70, 69, 121, 30);
      contrastSlider_.setMinimum(-100);
      contrastSlider_.setMaximum(100);
      contrastSlider_.setValue(0);

      
      getContentPane().add(contrastSlider_);

      final JLabel contrastLabel = new JLabel();
      contrastLabel.setText("Contrast");
      contrastLabel.setBounds(7, 69, 54, 26);
      getContentPane().add(contrastLabel);

      minLevel_ = new JTextField();
      minLevel_.addFocusListener(new FocusAdapter() {
         public void focusLost(FocusEvent e) {
            applyContrast();
         }
      });
      minLevel_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            applyContrast();
         }
      });
      minLevel_.setBounds(91, 15, 66, 21);
      getContentPane().add(minLevel_);

      maxLevel_ = new JTextField();
      maxLevel_.addFocusListener(new FocusAdapter() {
         public void focusLost(FocusEvent e) {
            applyContrast();
         }
      });
      maxLevel_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            applyContrast();
         }
      });
      maxLevel_.setBounds(91, 38, 66, 21);
      getContentPane().add(maxLevel_);

      final JButton closeButton = new JButton();
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      closeButton.setText("Close");
      closeButton.setBounds(237, 6, 98, 24);
      getContentPane().add(closeButton);

      final JButton fullScaleButton = new JButton();
      fullScaleButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            resetContrast();
         }
      });
      fullScaleButton.setText("Full Scale");
      fullScaleButton.setBounds(237, 60, 98, 24);
      getContentPane().add(fullScaleButton);

      fullScaleButton_12_ = new JButton();
      fullScaleButton_12_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            set12bitScale();
         }
      });
      fullScaleButton_12_.setText("Full Scale 12");
      fullScaleButton_12_.setBounds(237, 88, 98, 24);
      getContentPane().add(fullScaleButton_12_);

      fullScaleButton_14_ = new JButton();
      fullScaleButton_14_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            set14bitScale();
         }
      });
      fullScaleButton_14_.setText("Full Scale 14");
      fullScaleButton_14_.setBounds(237, 117, 98, 24);
      getContentPane().add(fullScaleButton_14_);

      final JButton autoScale = new JButton();
      autoScale.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            autoScale();
         }
      });
      autoScale.setText("Auto Scale");
      autoScale.setBounds(237, 33, 98, 24);
      getContentPane().add(autoScale);

      brightSlider_ = new JSlider();
      brightSlider_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            brightnessChanged();
         }
      });
      brightSlider_.setValue(0);
      brightSlider_.setPaintTicks(true);
      brightSlider_.setPaintLabels(true);
      brightSlider_.setMinimum(-100);
      brightSlider_.setBounds(70, 103, 121, 30);
      getContentPane().add(brightSlider_);

      final JLabel brightnessLabel = new JLabel();
      brightnessLabel.setText("Brightness");
      brightnessLabel.setBounds(8, 101, 54, 26);
      getContentPane().add(brightnessLabel);
      //
   }
   
   public void setContrastSettings(ContrastSettings s) {
      settings_ = s;
   }
   
   public void setImage(ImageWindow imgWin){
      imgWin_ = imgWin;
      settings_.min = imgWin_.getImagePlus().getProcessor().getMin();
      settings_.max = imgWin_.getImagePlus().getProcessor().getMax();
      minLevel_.setText(Integer.toString((int)settings_.min));
      maxLevel_.setText(Integer.toString((int)settings_.max));
      if ((imgWin_.getImagePlus().getProcessor() instanceof ShortProcessor)){
         fullScaleButton_12_.setEnabled(true);
         fullScaleButton_14_.setEnabled(true);
      } else {
         fullScaleButton_12_.setEnabled(false);
         fullScaleButton_14_.setEnabled(false);
      }
      setDefaultRange();
   }
   public void setProcessor(ImageProcessor proc){
      settings_.min = proc.getMin();
      settings_.max = proc.getMax();
      minLevel_.setText(Integer.toString((int)settings_.min));
      maxLevel_.setText(Integer.toString((int)settings_.max));
      if (proc instanceof ShortProcessor){
         fullScaleButton_12_.setEnabled(true);
         fullScaleButton_14_.setEnabled(true);
      } else {
         fullScaleButton_12_.setEnabled(false);
         fullScaleButton_14_.setEnabled(false);
      }
      
      defaultMinC_ = (int) settings_.min;
      defaultMaxC_ = (int) settings_.max;
      defaultMinB_ = (int) settings_.min;
      defaultMaxB_ = (int) settings_.max;
      contrastSlider_.setValue(0);
      brightSlider_.setValue(0);
   }

   private void applyContrast() {
      ImageProcessor ip = imgWin_.getImagePlus().getProcessor();
      double min = Double.parseDouble(minLevel_.getText());
      double max = Double.parseDouble(maxLevel_.getText());
      ip.setMinAndMax(min, max);      
      imgWin_.getImagePlus().updateAndDraw();
      setDefaultRange();
   }
   
   private void setDefaultRange() {
      double min = imgWin_.getImagePlus().getProcessor().getMin();
      double max = imgWin_.getImagePlus().getProcessor().getMax();
      defaultMinC_ = (int) min;
      defaultMaxC_ = (int) max;
      defaultMinB_ = (int) min;
      defaultMaxB_ = (int) max;
      contrastSlider_.setValue(0);
      brightSlider_.setValue(0);
      settings_.min = min;
      settings_.max = max;
   }
   
   private void resetContrast() {
      ImageProcessor ip = imgWin_.getImagePlus().getProcessor();
      if (ip instanceof ShortProcessor){
         ip.setMinAndMax(0, 65535);
      } else {
         ip.resetMinAndMax();
      }
      minLevel_.setText(Integer.toString((int)(imgWin_.getImagePlus().getProcessor().getMin())));
      maxLevel_.setText(Integer.toString((int)(imgWin_.getImagePlus().getProcessor().getMax())));
      imgWin_.getImagePlus().updateAndDraw();
      
      setDefaultRange();
   }
   
   private void autoScale() {
      ImagePlus imp = imgWin_.getImagePlus();
      Calibration cal = imp.getCalibration();
      imp.setCalibration(null);
      ImageStatistics stats = imp.getStatistics(); // get uncalibrated stats
      imp.setCalibration(cal);
      double min = stats.min;
      double max = stats.max;
      if (min==max) {
         min=stats.min;
         max=stats.max;
      }
      imp.getProcessor().setMinAndMax(min, max);      
      imgWin_.getImagePlus().updateAndDraw();
      minLevel_.setText(Integer.toString((int)(imgWin_.getImagePlus().getProcessor().getMin())));
      maxLevel_.setText(Integer.toString((int)(imgWin_.getImagePlus().getProcessor().getMax())));
      
      setDefaultRange();

   }
   
   private void set14bitScale() {
      ImagePlus imp = imgWin_.getImagePlus();
      imp.getProcessor().setMinAndMax(0, 16383);      
      imgWin_.getImagePlus().updateAndDraw();
      
      setDefaultRange();
   }
   
   private void set12bitScale() {
      ImagePlus imp = imgWin_.getImagePlus();
      imp.getProcessor().setMinAndMax(0, 4095);      
      imgWin_.getImagePlus().updateAndDraw();
      
      setDefaultRange();
   }
   
   private void contrastChanged() {
      int pos = contrastSlider_.getValue();
      if (imgWin_ == null)
         return;
      
      double range = (defaultMaxC_ - defaultMinC_);
      double incr = range / 100.0 * pos;
      double newMin = defaultMinC_ + incr;
      double newMax = defaultMaxC_ - incr;
      minLevel_.setText(Integer.toString((int)newMin));
      maxLevel_.setText(Integer.toString((int)newMax));
      ImageProcessor ip = imgWin_.getImagePlus().getProcessor();
      ip.setMinAndMax(newMin, newMax);      
      defaultMinB_ = (int)newMin;
      defaultMaxB_ = (int)newMax;
      imgWin_.getImagePlus().updateAndDraw();
      settings_.min = newMin;
      settings_.max = newMax;
   }
   
   private void brightnessChanged() {
      int pos = brightSlider_.getValue();
      if (imgWin_ == null)
         return;
      
      double range = (defaultMaxB_ - defaultMinB_);
      double incr = range / 100.0 * pos;
      double newMin = defaultMinB_ - incr;
      double newMax = defaultMaxB_ - incr;
      minLevel_.setText(Integer.toString((int)newMin));
      maxLevel_.setText(Integer.toString((int)newMax));
      ImageProcessor ip = imgWin_.getImagePlus().getProcessor();
      ip.setMinAndMax(newMin, newMax);
      defaultMinC_ = (int)newMin;
      defaultMaxC_ = (int)newMax;
      imgWin_.getImagePlus().updateAndDraw();
      settings_.min = newMin;
      settings_.max = newMax;
   }
}
