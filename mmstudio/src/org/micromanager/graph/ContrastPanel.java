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
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.json.JSONException;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.graph.HistogramPanel.CursorListener;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.HistogramUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.NumberUtils;

/**
 * Slider and histogram panel for adjusting contrast and brightness.
 * 
 */
public class ContrastPanel extends JPanel implements 
        PropertyChangeListener, 
        CursorListener {
   private static final double SLOW_HIST_UPDATE_TIME_MS = 2000;
   
	private static final long serialVersionUID = 1L;
	private JComboBox modeComboBox_;
	private HistogramPanel histogramPanel_;
	private JLabel maxLabel_;
	private JLabel minLabel_;
   private JLabel meanLabel_;
   private JLabel stdDevLabel_;
	private SpringLayout springLayout;
	private ImagePlus image_;
	private GraphData histogramData_;
   private JFormattedTextField gammaValue_;
   private NumberFormat numberFormat_;
   private double gamma_ = 1.0;
	private int maxIntensity_ = 255;
   private double mean_;
   private double stdDev_;
   private double pixelMin_ = 0.0;
   private double pixelMax_ = 255.0;
	private int binSize_ = 1;
	private static final int HIST_BINS = 256;
	private int numLevels_ = 256;
	ContrastSettings cs8bit_;
	ContrastSettings cs16bit_;
	private JCheckBox autoStretchCheckBox_;
	private JCheckBox rejectOutliersCheckBox_;
   private JCheckBox slowHistogramCheckBox_;
   private boolean slowHistogram_ = false;
   private boolean calcHistogram_ = true;
	private boolean logScale_ = false;
	private JCheckBox logHistCheckBox_;
   private boolean autoStretch_;
   private double contrastMin_;
   private double contrastMax_;
	private double minAfterRejectingOutliers_;
	private double maxAfterRejectingOutliers_;
   JSpinner rejectOutliersPercentSpinner_;
   private double fractionToReject_;
   JLabel percentOutliersLabel_;
   private int[] histogram_;
   private int slowHistogramCount_;
   private int numFramesForSlowHist_;
   private VirtualAcquisitionDisplay virtAcq_;



	/**
	 * Create the panel
	 */
	public ContrastPanel() {
		super();

      numFramesForSlowHist_ = (int) (SLOW_HIST_UPDATE_TIME_MS
              / MMStudioMainFrame.getInstance().getLiveModeInterval() );
      HistogramUtils h = new HistogramUtils(null);
      fractionToReject_ = h.getFractionToReject(); // get the default value
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
				autostretch(true);
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

		minLabel_ = new JLabel();
		minLabel_.setFont(new Font("", Font.PLAIN, 10));
		add(minLabel_);
		springLayout.putConstraint(SpringLayout.EAST, minLabel_, 95,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, minLabel_, 45,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, minLabel_, 98,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, minLabel_, 84,
				SpringLayout.NORTH, this);

      maxLabel_ = new JLabel();
		maxLabel_.setFont(new Font("", Font.PLAIN, 10));
		add(maxLabel_);
		springLayout.putConstraint(SpringLayout.EAST, maxLabel_, 95,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, maxLabel_, 45,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, maxLabel_, 114,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, maxLabel_, 100,
				SpringLayout.NORTH, this);

		JLabel minLabel = new JLabel();
		minLabel.setFont(new Font("", Font.PLAIN, 10));
		minLabel.setText("Min");
		add(minLabel);
		springLayout.putConstraint(SpringLayout.SOUTH, minLabel, 98,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, minLabel, 84,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.EAST, minLabel, 30,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, minLabel, 5,
				SpringLayout.WEST, this);

		JLabel maxLabel = new JLabel();
		maxLabel.setFont(new Font("", Font.PLAIN, 10));
		maxLabel.setText("Max");
		add(maxLabel);
		springLayout.putConstraint(SpringLayout.SOUTH, maxLabel, 114,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, maxLabel, 100,
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
      springLayout.putConstraint(SpringLayout.SOUTH, avgLabel, 130, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, avgLabel, 116, SpringLayout.NORTH, this);

      meanLabel_ = new JLabel();                                              
      meanLabel_.setFont(new Font("", Font.PLAIN, 10));                       
      add(meanLabel_);                                                        
      springLayout.putConstraint(SpringLayout.EAST, meanLabel_, 95, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, meanLabel_, 45, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, meanLabel_, 130, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, meanLabel_, 116, SpringLayout.NORTH, this);
                                                                             
      JLabel varLabel = new JLabel();
      varLabel.setFont(new Font("", Font.PLAIN, 10));
      varLabel.setText("Std Dev");
      add(varLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, varLabel, 146, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, varLabel, 132, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.EAST, varLabel, 42, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.WEST, varLabel, 5, SpringLayout.WEST, this);

      stdDevLabel_ = new JLabel();                                              
      stdDevLabel_.setFont(new Font("", Font.PLAIN, 10));                       
      add(stdDevLabel_);
      springLayout.putConstraint(SpringLayout.EAST, stdDevLabel_, 95, SpringLayout.WEST, this); 
      springLayout.putConstraint(SpringLayout.WEST, stdDevLabel_, 45, SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.SOUTH, stdDevLabel_, 146, SpringLayout.NORTH, this);
      springLayout.putConstraint(SpringLayout.NORTH, stdDevLabel_, 132, SpringLayout.NORTH, this);

      JLabel gammaLabel = new JLabel();
      gammaLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      gammaLabel.setPreferredSize(new Dimension(40, 20));
      gammaLabel.setText("Gamma");
      add(gammaLabel);
		springLayout.putConstraint(SpringLayout.WEST, gammaLabel, 5,
				SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.NORTH, gammaLabel, 250,
				SpringLayout.NORTH, this);

      gammaValue_ = new JFormattedTextField(numberFormat_);
      gammaValue_.setFont(new Font("Arial", Font.PLAIN, 10));
      gammaValue_.setValue(gamma_);
      gammaValue_.addPropertyChangeListener("value", this);
      gammaValue_.setPreferredSize(new Dimension(35, 20));

      add(gammaValue_);
		springLayout.putConstraint(SpringLayout.WEST, gammaValue_, 45,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.EAST, gammaValue_, 95,
				SpringLayout.WEST, this);
  		springLayout.putConstraint(SpringLayout.NORTH, gammaValue_, 0,
				SpringLayout.NORTH, gammaLabel);

		histogramPanel_ = new HistogramPanel();
		histogramPanel_.setMargins(8, 10);
      histogramPanel_.setTraceStyle(true, new Color(50,50,50));
		histogramPanel_.setTextVisible(false);
		histogramPanel_.setGridVisible(false);
      
      histogramPanel_.addCursorListener(this);


		add(histogramPanel_);
		springLayout.putConstraint(SpringLayout.EAST, histogramPanel_, -5,
				SpringLayout.EAST, this);
		springLayout.putConstraint(SpringLayout.WEST, histogramPanel_, 100,
				SpringLayout.WEST, this);

		springLayout.putConstraint(SpringLayout.SOUTH, histogramPanel_, -6,
				SpringLayout.SOUTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, histogramPanel_, 0,
				SpringLayout.NORTH, fullScaleButton_);

		autoStretchCheckBox_ = new JCheckBox();
		autoStretchCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		autoStretchCheckBox_.setText("Auto-stretch");
		autoStretchCheckBox_.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
            rejectOutliersCheckBox_.setEnabled(autoStretchCheckBox_.isSelected());
            boolean rejectControlsEnabled  = autoStretchCheckBox_.isSelected() && rejectOutliersCheckBox_.isSelected() ;
            percentOutliersLabel_.setEnabled(rejectControlsEnabled);
            rejectOutliersPercentSpinner_.setEnabled(rejectControlsEnabled );
            if (autoStretchCheckBox_.isSelected()) {
               autoStretch_ = true;
               autostretch(true);
            } else {
               autoStretch_ = false;
            }
			};
		});
		add(autoStretchCheckBox_);

		springLayout.putConstraint(SpringLayout.EAST, autoStretchCheckBox_, 5,
				SpringLayout.WEST, histogramPanel_);
		springLayout.putConstraint(SpringLayout.WEST, autoStretchCheckBox_, 0,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, autoStretchCheckBox_, 205,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, autoStretchCheckBox_, 180,
				SpringLayout.NORTH, this);


	   rejectOutliersCheckBox_ = new JCheckBox();
		rejectOutliersCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		rejectOutliersCheckBox_.setText("");
		rejectOutliersCheckBox_.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
            rejectOutliersPercentSpinner_.setEnabled(rejectOutliersCheckBox_.isSelected());
            percentOutliersLabel_.setEnabled(rejectOutliersCheckBox_.isSelected());    
            calcHistogramAndStatistics();
            autostretch(true);
			};
		});
		add(rejectOutliersCheckBox_);

		springLayout.putConstraint(SpringLayout.EAST, rejectOutliersCheckBox_, 30,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, rejectOutliersCheckBox_, 0,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, rejectOutliersCheckBox_, 230,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, rejectOutliersCheckBox_, 210,
				SpringLayout.NORTH, this);

      
      
      SpinnerModel smodel = new SpinnerNumberModel(100*fractionToReject_,0,100,.1);
      rejectOutliersPercentSpinner_ = new JSpinner();
      rejectOutliersPercentSpinner_.setModel(smodel);
      Dimension sd = rejectOutliersPercentSpinner_.getSize();
      rejectOutliersPercentSpinner_.setFont(new Font("Arial", Font.PLAIN, 9));
      // user sees the fraction as percent
      add(rejectOutliersPercentSpinner_);
      rejectOutliersPercentSpinner_.setEnabled(false);
      rejectOutliersPercentSpinner_.setToolTipText("% pixels dropped or saturated to reject");
      smodel.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            calcHistogramAndStatistics();
            autostretch(true);
         }});


		springLayout.putConstraint(SpringLayout.EAST, rejectOutliersPercentSpinner_, 90,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, rejectOutliersPercentSpinner_, 35,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, rejectOutliersPercentSpinner_, 230,
				SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.NORTH, rejectOutliersPercentSpinner_, 210,
				SpringLayout.NORTH, this);

      percentOutliersLabel_ = new JLabel();
      percentOutliersLabel_.setFont(new Font("Arial", Font.PLAIN, 10));
      percentOutliersLabel_.setText("% outliers to ignore");
      add(percentOutliersLabel_);
		springLayout.putConstraint(SpringLayout.WEST, percentOutliersLabel_, 5,
				SpringLayout.WEST, this);
      springLayout.putConstraint(SpringLayout.NORTH, percentOutliersLabel_, 230,
				SpringLayout.NORTH, this);
 		springLayout.putConstraint(SpringLayout.EAST, percentOutliersLabel_, 5,
				SpringLayout.WEST, histogramPanel_);


		modeComboBox_ = new JComboBox();
		modeComboBox_.setFont(new Font("", Font.PLAIN, 10));
		modeComboBox_.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setMaxIntensityAndBinSize();
            updateContrast();
			}});
		modeComboBox_.setModel(new DefaultComboBoxModel(new String[] {
				"camera", "8bit", "10bit", "12bit", "14bit", "16bit" }));
		add(modeComboBox_);
		springLayout.putConstraint(SpringLayout.EAST, modeComboBox_, 0,
				SpringLayout.EAST, maxLabel_);
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

//				update();
			}
		});
      slowHistogramCheckBox_ = new JCheckBox();
		slowHistogramCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		slowHistogramCheckBox_.setText("Slow hist.");
		slowHistogramCheckBox_.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
            slowHistogram_ = slowHistogramCheckBox_.isSelected();
            if (slowHistogram_) {
               numFramesForSlowHist_ = (int) (SLOW_HIST_UPDATE_TIME_MS
                       / MMStudioMainFrame.getInstance().getLiveModeInterval() );
               slowHistogramCount_ = numFramesForSlowHist_;
               calcHistogram_ = false;
            }
            else {
               calcHistogram_ = true;
               calcHistogramAndStatistics();
               updateCursors();
               updateHistogram();
               if (autoStretch_)
                  autostretch(true);
            }
			};
		});
      
		logHistCheckBox_.setText("Log hist.");
		add(logHistCheckBox_);
		springLayout.putConstraint(SpringLayout.SOUTH, logHistCheckBox_, -20,
				SpringLayout.NORTH, minLabel);
		springLayout.putConstraint(SpringLayout.NORTH, logHistCheckBox_, 0,
				SpringLayout.SOUTH, autoScaleButton);
		springLayout.putConstraint(SpringLayout.EAST, logHistCheckBox_, 74,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, logHistCheckBox_, 1,
				SpringLayout.WEST, this);

      
   
		add(slowHistogramCheckBox_);
		springLayout.putConstraint(SpringLayout.EAST, slowHistogramCheckBox_, 74,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.WEST, slowHistogramCheckBox_, 1,
				SpringLayout.WEST, this);
		springLayout.putConstraint(SpringLayout.SOUTH, slowHistogramCheckBox_, 0,
				SpringLayout.NORTH, minLabel);
		springLayout.putConstraint(SpringLayout.NORTH, slowHistogramCheckBox_, -20,
				SpringLayout.NORTH, minLabel);
      	}

   public void setPixelBitDepth(int depth) 
   { 
	   // histogram for 32bits is not supported in this implementation 
      if(depth >= 32)
	      depth = 8; 
      numLevels_ = 1 << depth; 
      maxIntensity_ = numLevels_ - 1;
	   binSize_ = (maxIntensity_ + 1)/ HIST_BINS;
	  
	   // override histogram depth based on the selected mode 
	   if ( modeComboBox_.getSelectedIndex() > 0) {
         setMaxIntensityAndBinSize(); 
      }  
   }

   public void applyGammaAndContrastToImage() {
      if (image_ == null)
         return;
      ImageProcessor ip = image_.getProcessor();
      if (ip == null)
         return;

      double maxValue = 255.0;
      byte[] r = new byte[256];
      byte[] g = new byte[256];
      byte[] b = new byte[256];
      for (int i = 0; i < 256; i++) {
         double val = Math.pow((double) i / maxValue, gamma_) * (double) maxValue;
         r[i] = (byte) val;
         g[i] = (byte) val;
         b[i] = (byte) val;
      }
      ip.setColorModel( new LUT(8, 256, r, g, b));
      ip.setMinAndMax(contrastMin_, contrastMax_);
      
      VirtualAcquisitionDisplay.getDisplay(image_).storeSingleChannelDisplaySettings(
              (int) contrastMin_, (int) contrastMax_, gamma_);
   }
   
   public void updateHistogram() {
      if (image_ != null && histogram_ != null) {
         histogramData_.setData(histogram_);
         histogramPanel_.setData(histogramData_);
         histogramPanel_.setAutoScale();
         
         maxLabel_.setText(NumberUtils.intToDisplayString((int) pixelMax_));
         minLabel_.setText(NumberUtils.intToDisplayString((int) pixelMin_));
         meanLabel_.setText(NumberUtils.intToDisplayString((int) mean_));
         stdDevLabel_.setText(NumberUtils.doubleToDisplayString(stdDev_));
         
         histogramPanel_.repaint();
      } 
   }
	 
	private void setMaxIntensityAndBinSize() {
		switch (modeComboBox_.getSelectedIndex()-1) {        
      case -1:
         int bitDepth = 8;
         if (virtAcq_ != null) {
            try {
               bitDepth = virtAcq_.getCurrentMetadata().getInt("BitDepth");
            } catch (JSONException ex) {
               bitDepth = (int) MMStudioMainFrame.getInstance().getCore().getImageBitDepth();
            }
         } else {
            bitDepth = (int) MMStudioMainFrame.getInstance().getCore().getImageBitDepth();
         }
         maxIntensity_ = (int) (Math.pow(2, bitDepth) - 1);
         break;
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
	}

   // only used for Gamma
   public void propertyChange(PropertyChangeEvent e) {
      try { 
         gamma_ = (double) NumberUtils.displayStringToDouble(numberFormat_.format(gammaValue_.getValue()));
      } catch (ParseException p) {
         ReportingUtils.logError(p, "ContrastPanel, Function propertyChange");
      }
      applyGammaAndContrastToImage();
      updateCursors();
      if (image_ != null)
         image_.updateAndDraw();
   }

	/**
	 * Auto-scales image display to clip at minimum and maximum pixel values.
	 * 
	 */
	public void autostretch(boolean applyAndUpdate) {
         if (image_ == null)
            return;
         contrastMin_ = pixelMin_;
         contrastMax_ = pixelMax_;

			if(rejectOutliersCheckBox_.isSelected()){
				if( contrastMin_ < minAfterRejectingOutliers_  ){
               if( 0 < minAfterRejectingOutliers_){
                  contrastMin_ =  minAfterRejectingOutliers_;
               }
				}
				if( maxAfterRejectingOutliers_ < contrastMax_){
                  contrastMax_ = maxAfterRejectingOutliers_;
				}
			}
      
      if (applyAndUpdate) {
         applyGammaAndContrastToImage();
         updateCursors();  
         image_.updateAndDraw();        
      }
	}


	private void setFullScale() {
		if (image_ == null)
			return;
      autoStretchCheckBox_.setSelected(false);
      contrastMin_ = 0;
      contrastMax_ = maxIntensity_;
     
      applyGammaAndContrastToImage();
      updateCursors();      
		image_.updateAndDraw();
	}

	private void updateCursors() {
		if (image_ == null)
			return;
		histogramPanel_.setCursors(contrastMin_ / binSize_, contrastMax_ / binSize_, gamma_);
		histogramPanel_.repaint();
	}


   public void applyContrastSettings(ContrastSettings contrast8,
           ContrastSettings contrast16) {
      if (image_ == null) {
         contrastMin_ = contrast16.min;
         contrastMax_ = contrast16.max;
         return;
      }
      if (image_.getProcessor() instanceof ShortProcessor) {
         contrastMin_ = contrast16.min;
         contrastMax_ = contrast16.max;
      } else {
         contrastMin_ = contrast8.min;
         contrastMax_ = contrast8.max;
      }
      applyGammaAndContrastToImage();
      updateCursors();
      image_.updateAndDraw();;
   }

	

	public void setContrastStretch(boolean stretch) {
		autoStretchCheckBox_.setSelected(stretch);
	}

	public boolean isContrastStretch() {
		return autoStretchCheckBox_.isSelected();
	}
   
   public void setSlowHist(boolean b) {
      slowHistogramCheckBox_.setSelected(b);
   }
   
   public boolean getSlowHist() {
      return slowHistogram_;
   }
   
   public void setLogHist(boolean b) {
      logHistCheckBox_.setSelected(b);
   }
   
   public boolean getLogHist() {
      return logScale_;
   }

   public void setRejectOutliers(boolean reject) {
      rejectOutliersCheckBox_.setSelected(reject);
   }

   public boolean isRejectOutliers() {
      return rejectOutliersCheckBox_.isSelected();
   }
   
   public double getFractionToReject() {
      return fractionToReject_;
   }
   
   public void setFractionToReject(double frac) {
      fractionToReject_ = frac;
      rejectOutliersPercentSpinner_.setValue(fractionToReject_ / 0.01);
      
   }
	
   public ContrastSettings getContrastSettings() {
      return new ContrastSettings(contrastMin_,contrastMax_);
   }

   private void updateStretchBox(boolean liveWin) {
      if (liveWin) {
         autoStretchCheckBox_.setEnabled(true);
         autoStretchCheckBox_.setSelected(true);         
      } else {
         autoStretchCheckBox_.setEnabled(false);
         autoStretchCheckBox_.setSelected(false);
      }
   }
   
   private void loadContrastSettings() {
      VirtualAcquisitionDisplay disp = VirtualAcquisitionDisplay.getDisplay(image_);
      contrastMax_ = disp.getChannelMax(0);
      contrastMin_ = disp.getChannelMin(0);
      gamma_ = disp.getChannelGamma(0);
      updateCursors();
   }

   public void setImage(ImagePlus image) {
      image_ =  image;
      if (image_ == null)
         return;
      loadContrastSettings();
     
      calcHistogramAndStatistics();
           
      applyGammaAndContrastToImage();
       updateCursors();
       updateHistogram();
   }
   
   @Override
   public void paint (Graphics g) {
      super.paint(g);
   }

   
   private void calcHistogramAndStatistics() {
      if (image_ != null) {
         int[] rawHistogram = image_.getProcessor().getHistogram();
         int imgWidth = image_.getWidth();
         int imgHeight = image_.getHeight();
         if (rejectOutliersCheckBox_.isSelected()) {
            // todo handle negative values
            maxAfterRejectingOutliers_ = rawHistogram.length;
            // specified percent of pixels are ignored in the automatic contrast setting
            int totalPoints = imgHeight * imgWidth;
            fractionToReject_ = 0.01 * (Double) rejectOutliersPercentSpinner_.getValue();
            HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, fractionToReject_);
            minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
            maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();
         }
         if (histogramData_ == null) {
            histogramData_ = new GraphData();
         } // 256 bins
         
         
          pixelMin_ = -1;
         pixelMax_ = 0;
         mean_ = 0;
         
         histogram_ = new int[HIST_BINS];
         int limit = Math.min(rawHistogram.length / binSize_, HIST_BINS);
         int total = 0;
         for (int i = 0; i < limit; i++) {
            histogram_[i] = 0;
            for (int j = 0; j < binSize_; j++) {
               int rawHistIndex = i * binSize_ + j;
               int rawHistVal = rawHistogram[rawHistIndex];
               histogram_[i] += rawHistVal;
               if (rawHistVal > 0) {
                  pixelMax_ = rawHistIndex;
                  if (pixelMin_ == -1) {
                     pixelMin_ = rawHistIndex;
                  }
                  mean_ += rawHistIndex * rawHistVal;
               }
            }
            total += histogram_[i];
            if (logScale_) 
               histogram_[i] = histogram_[i] > 0 ? (int) (1000 * Math.log(histogram_[i])) : 0;
         }
         mean_ /= imgWidth*imgHeight;
         if (pixelMin_ == pixelMax_) 
            if (pixelMin_ == 0) 
               pixelMax_++;
            else 
               pixelMin_--;

         // work around what is apparently a bug in ImageJ
         if (total == 0) {
            if (image_.getProcessor().getMin() == 0) {
               histogram_[0] = imgWidth * imgHeight;
            } else {
               histogram_[limit - 1] = imgWidth * imgHeight;
            }
         }
                      
         stdDev_ = 0;
         for (int i = 0; i < rawHistogram.length; i++) {
           for (int j = 0; j < rawHistogram[i]; j++) {
              stdDev_ += (i - mean_)*(i - mean_);
           }
         }
         stdDev_ = Math.sqrt(stdDev_/(imgWidth*imgHeight));         
      }
   }
   
   public void clearHistogram() {
      mean_ = 0;
      stdDev_ = 0;
      pixelMax_ = 255;
      pixelMin_ = 0;
      histogram_ = new int[HIST_BINS];
      updateHistogram();
   }

   public void updateContrast() {
      if (virtAcq_ == null)
         return;
      if (virtAcq_.windowClosed()) {
         histogram_ = null;
         updateHistogram();
         virtAcq_ = null;
         image_ = null;
         return;
      }
      ImagePlus ip = virtAcq_.getImagePlus();
      if (ip == null)
         return;
      image_ = ip;
      loadContrastSettings();
      if (slowHistogram_) {
         slowHistogramCount_++;
         if (slowHistogramCount_ >= numFramesForSlowHist_) {
            slowHistogramCount_ = 0;
            calcHistogram_ = true;
         }
      }
      if (calcHistogram_) {
         calcHistogramAndStatistics();
         setMaxIntensityAndBinSize();
         if (autoStretch_)
            autostretch(false);
      }
      applyGammaAndContrastToImage();
      if (autoStretch_ && calcHistogram_)
         updateCursors();
      if (calcHistogram_) 
         updateHistogram();  
  
      if (slowHistogram_) 
         calcHistogram_ = false;
   }

   public void onLeftCursor(double pos) {
      if (autoStretch_)
         autoStretchCheckBox_.setSelected(false);

      contrastMin_ = Math.max(0, pos) * binSize_;
      if (contrastMax_ < contrastMin_)
         contrastMax_ = contrastMin_;
      applyGammaAndContrastToImage();
      updateCursors();
      if (image_ != null)
         image_.updateAndDraw();
   }

   public void onRightCursor(double pos) {
      if (autoStretch_)
         autoStretchCheckBox_.setSelected(false);
      
      contrastMax_ = Math.min(255, pos) * binSize_;
      if (contrastMin_ > contrastMax_)
         contrastMin_ = contrastMax_;
      applyGammaAndContrastToImage();
      updateCursors();
       if (image_ != null)
         image_.updateAndDraw();
   }

   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1)
            gamma_ = 1;
         else
            gamma_ = gamma;
         gammaValue_.setValue(gamma_);
      applyGammaAndContrastToImage();
      updateCursors();
       if (image_ != null)
         image_.updateAndDraw();
      }
   } 
   
   public void setDisplay(VirtualAcquisitionDisplay vad) {
      virtAcq_ = vad;
   }
}