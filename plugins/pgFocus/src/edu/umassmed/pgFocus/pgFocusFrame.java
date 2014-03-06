package edu.umassmed.pgfocus;


import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.text.DecimalFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
//import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.awt.event.WindowEvent;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
//import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.JButton;
import javax.swing.Box;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
//import org.jfree.ui.RectangleInsets;








import mmcorej.CMMCore;
import mmcorej.DeviceType;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

import javax.swing.JTextField;
import javax.swing.JLabel;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.SwingConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class pgFocusFrame extends JFrame {

	private final ScriptInterface gui_;
    private final CMMCore core_;
    private Preferences prefs_;
    private static String pgFocus_;

    private static final long serialVersionUID = 1L;
    
    private int frameXPos_ = 100;
    private int frameYPos_ = 100;
    
    private static final String FRAMEXPOS = "FRAMEXPOS";
    private static final String FRAMEYPOS = "FRAMEYPOS";
    
    private static final String CALIBRATIONCURVE = "CALIBRATIONCURVE";

    JButton btnCalibrateButton = new JButton("Calibrate");
    JToggleButton tglbtnLockButton = new JToggleButton();
    
    private  pgFocusPanel pgFocusPanel_;
    private LightPanel lightPanel_;
    private CalibratePanel calibratePanel_;
    
    private JCheckBox chbUpdateRealTime;
    private JCheckBox chbUpdateLight;
    private JCheckBox chckbxAutoexpose;
    private JPanel settingsPanel;
    
    private JTextField textInput_Gain;
    private JTextField textOnnMPerVolt;
    private JTextField textOnOffset;
    private JTextField tfOnWaitAfterLock;
    private JTextField tfOnWaitAfterMessage;
	private JTextField tfOnWaitAfterLight;
    private JTextField tfOnExposure;
  
    private JLabel lblOnResiduals;
    private JLabel lblOnSlope;
    private JLabel lblOnIntercept;
    
    private String savedCalibrationCurve = "";
    
    /** Creates pgFocus */
    public pgFocusFrame(ScriptInterface gui)  {
    	
   
       gui_ = gui;
       core_ = gui.getMMCore();
       prefs_ = Preferences.userNodeForPackage(this.getClass());
       pgFocus_ = "";

       mmcorej.StrVector afs =
               core_.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
       
       boolean found = false;
      
       
       for (String af : afs) {
         try {
            if (core_.hasProperty(af, "Description")) {
               if (core_.getProperty(af, "Description").equals("Open Source and Open Hardware Focus Stabilization Device")) {
                  found = true;
                  pgFocus_ = af;
               }
            }
         } catch (Exception ex) {
            Logger.getLogger(pgFocus.class.getName()).log(Level.SEVERE, null, ex);
         }
       }

       if (!found) {
          gui_.showError("This plugin needs pgFocus by: \n\nKarl Bellve\nBiomedical Imaging Group\nMolecular Medicine\nUniversity of Massachusetts Medical School\n");
          throw new IllegalArgumentException("Could not find the pgFocus hardware");
       }

      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);
      savedCalibrationCurve = prefs_.get(CALIBRATIONCURVE, savedCalibrationCurve);
      
      setLocation(frameXPos_, frameYPos_);
      
      initGUI();      
      
    }

  
    class pgFocusPanel extends JPanel implements ActionListener {

    	private static final long serialVersionUID = 1L;
    	
    	CMMCore core_;
        
   	 	int waitTime = getIntProperty("Wait ms after Message"); 
   	 	/** Timer to refresh graph after every 1 second */
        Timer timer = new Timer(waitTime, this);

        public void actionPerformed(ActionEvent actionevent) {
        	updateGraph();
        }

        public static final int SUBPLOT_COUNT = 4;
        private TimeSeriesCollection datasets[];
        private double lastValue[];
        
        public pgFocusPanel(CMMCore core) {

            super(new BorderLayout());
            
        	String Title = "pgFocus";
        	
            core_ = core;
            
            timer.setInitialDelay(1000);
            
            lastValue = new double[SUBPLOT_COUNT];
            CombinedDomainXYPlot combineddomainxyplot = new CombinedDomainXYPlot(new DateAxis("Time"));
            
            datasets = new TimeSeriesCollection[4];
            for (int i = 0; i < SUBPLOT_COUNT; i++) {
                TimeSeries timeseries = new TimeSeries("Time");
                datasets[i] = new TimeSeriesCollection(timeseries);
                
                switch (i) {
	                case 0:
	                	Title = "Offset";
	                	break;
	                case 1:
	                	Title =  "SD nM";
	                	break;
	                case 2:
	                	Title =  "Output nM";
	                	break;
	                case 3:
	                	Title =  "Input nM";
	                	break;
                }
                
                NumberAxis numberaxis = new NumberAxis(Title);
                numberaxis.setAutoRangeIncludesZero(false);
                numberaxis.setNumberFormatOverride(new DecimalFormat("###0.00")); 
                XYPlot xyplot = new XYPlot(datasets[i], null, numberaxis, new StandardXYItemRenderer());
                xyplot.setBackgroundPaint(Color.lightGray);
                xyplot.setDomainGridlinePaint(Color.white);
                xyplot.setRangeGridlinePaint(Color.white);
                combineddomainxyplot.add(xyplot);
            }

            JFreeChart jfreechart = new JFreeChart("", combineddomainxyplot);
            jfreechart.removeLegend();
            jfreechart.setBorderPaint(Color.black);
            jfreechart.setBorderVisible(true);
            jfreechart.setBackgroundPaint(Color.white);
            combineddomainxyplot.setBackgroundPaint(Color.lightGray);
            combineddomainxyplot.setDomainGridlinePaint(Color.white);
            combineddomainxyplot.setRangeGridlinePaint(Color.white);
            //combineddomainxyplot.setAxisOffset(new RectangleInsets(4D, 4D, 4D, 4D));
            ValueAxis valueaxis = combineddomainxyplot.getDomainAxis();
            valueaxis.setAutoRange(true);
            valueaxis.setFixedAutoRange(60000D);
            
            
            ChartPanel chartpanel = new ChartPanel(jfreechart);
           
            add(chartpanel, BorderLayout.CENTER);
            
            chartpanel.setPreferredSize(new Dimension(800, 500));
            chartpanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            timer.start();
            
        }        
        
        private void updateGraph() {
        	// adjust timer delay
			int waitTime = getIntProperty("Wait ms after Message"); 
			timer.setDelay(waitTime);
			
			focusLockButton();
						
			lastValue[0] = getFloatProperty("Offset"); 
			 
			lastValue[1] = getFloatProperty("Standard Deviation nM");
			
			lastValue[2] = getFloatProperty("Output nM");
			
			lastValue[3] = getFloatProperty("Input nM");
			 		
			for (int i = 0; i < SUBPLOT_COUNT; i++) {
				datasets[i].getSeries(0).add(new Millisecond(), lastValue[i]);
			}
        }      
    }
     
    class LightPanel extends JPanel implements ActionListener {

    	private static final long serialVersionUID = 1L;
    	
    	XYSeries lightProfile;
    	XYDataset lightProfileSet;
    	JFreeChart lightChart;
    	ChartPanel lightPanel;
    	
    	CMMCore core_;
    	
    	int waitTime = getIntProperty("Wait ms after Light");
        
        /** Timer to refresh graph after every 1 second */
        final Timer timer = new Timer(waitTime, this);

        public void actionPerformed(ActionEvent actionevent) {
        	updateGraph();
        }



        public LightPanel(CMMCore core) {

            super(new BorderLayout());
        	String Title = "Light Profile";
        	
            core_ = core;
            
            timer.setInitialDelay(5000);
            
            lightProfile = new XYSeries(Title);
            
            lightProfileSet = new XYSeriesCollection(lightProfile);
                      
            JFreeChart lightChart = ChartFactory.createXYLineChart(
            "",
    		"Pixel",
    		"Light",
    		lightProfileSet,
    		PlotOrientation.VERTICAL,
    		false,false, false);

            lightChart.removeLegend();
            lightChart.setBorderPaint(Color.black);
            lightChart.setBorderVisible(true);
            lightChart.setBackgroundPaint(Color.white);
            
            XYPlot xyplot = lightChart.getXYPlot();
            
            xyplot.setBackgroundPaint(Color.lightGray);
            xyplot.setDomainGridlinePaint(Color.white);
            xyplot.setRangeGridlinePaint(Color.white);
            //xyplot.setAxisOffset(new RectangleInsets(4D, 4D, 4D, 4D));
            
            ValueAxis x_axis = xyplot.getDomainAxis();
            x_axis.setAutoRange(false);
            x_axis.setRange(0,127);
            
            ValueAxis y_axis = xyplot.getRangeAxis();
            y_axis.setAutoRange(false);
            y_axis.setRange(0,1023);
            		
            lightPanel = new ChartPanel(lightChart);
            
            add(lightPanel, BorderLayout.CENTER);
            
            lightPanel.setPreferredSize(new Dimension(800, 50));
            lightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            timer.start();
            
        }       
        
        private void updateGraph() {

        	 // adjust timer delay
        	 int waitTime = getIntProperty("Wait ms after Light"); 
        	 timer.setDelay(waitTime);
        	 
        	 // get light profile
			 String light = getStringProperty("Light Profile"); 
			 
			 String lightSplit[] = light.split(",");			 	
			 
			 lightProfile.clear();
			 
			 try {
				 for (int i = 0; i < lightSplit.length; i++) {	 
					 lightProfile.add(i, Integer.parseInt(lightSplit[i]));
				 }	
        	} catch (NumberFormatException e) {
        		System.out.print("pgFocus Plugin: corrupted light profile!");
        	}		
        }  
        
    } 
    
    class CalibratePanel extends JPanel implements ActionListener {

    	private static final long serialVersionUID = 1L;
    	
    	XYSeries calibrateProfile;
    	XYDataset calibrateProfileSet;
    	JFreeChart calibrateChart;
    	ChartPanel calibratePanel;
    	
    	CMMCore core_;
        
        /** Timer to refresh graph after every 1 second */
        private Timer timer = new Timer(2000, this);
       
        public void actionPerformed(ActionEvent actionevent) {
            updateGraph();
        }


        public CalibratePanel(CMMCore core) {

            super(new BorderLayout());
        	String Title = "Calibration Profile";
        	
            core_ = core;
            
            timer.setInitialDelay(2000);
        
            calibrateProfile = new XYSeries(Title);
            
            calibrateProfileSet = new XYSeriesCollection(calibrateProfile);
                      
            JFreeChart calibrateChart = ChartFactory.createXYLineChart(
            "",
    		"DAU",
    		"Position",
    		calibrateProfileSet,
    		PlotOrientation.VERTICAL,
    		false,false, false);

            calibrateChart.removeLegend();
            calibrateChart.setBorderPaint(Color.black);
            calibrateChart.setBorderVisible(true);
            calibrateChart.setBackgroundPaint(Color.white);
            
            XYPlot xyplot = calibrateChart.getXYPlot();
            
            xyplot.setBackgroundPaint(Color.lightGray);
            xyplot.setDomainGridlinePaint(Color.white);
            xyplot.setRangeGridlinePaint(Color.white);
            
            ValueAxis x_axis = xyplot.getDomainAxis();
            //x_axis.setAutoRange(true);
            x_axis.setRange(-1000,1000);
            
            ValueAxis y_axis = xyplot.getRangeAxis();
            //y_axis.setAutoRange(false);
            y_axis.setRange(40,100);
            		
            calibratePanel = new ChartPanel(calibrateChart);
            
            add(calibratePanel, BorderLayout.CENTER);
            
            calibratePanel.setPreferredSize(new Dimension(800, 50));
            calibratePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            updateValues();
            
        }       
        
        private boolean updateValues() {
        	String Curve = getStringProperty("Calibration Curve"); 

        	try {
        		if (Curve.length() > 0) {

        			if (Curve.equals("Please wait while calibrating...")) return false;
        			
	        		if (getStringProperty("Focus Mode").equals("Calibration")) return false;
	        		
	        		if (Curve.equals("Please perform a calibration")) 
	        			if (savedCalibrationCurve.isEmpty() == false) Curve = savedCalibrationCurve;
	        			else return false;
	        		
	        		savedCalibrationCurve = Curve;
	        		
	        		String calibrateSplit[] = Curve.split(",");	
	
	        		calibrateProfile.clear();
	        		
        			for (int i = 0; i < calibrateSplit.length; i++) {	 
        				int dau = Integer.parseInt(calibrateSplit[i]) - 16384/2;
        				float lightPosition = Float.parseFloat(calibrateSplit[++i]);
        				calibrateProfile.add(dau,lightPosition); 
        			}
        		}
        	}
        	catch (NumberFormatException e) 
        	{
        		System.out.print("pgFocus plugin: corrupted calibration profile!");
        	}
        	
        	return true;
        }
        private void updateGraph() {
        	
				if (updateValues()) 
				{
					timer.stop();
					btnCalibrateButton.setText("Calibrate");
					btnCalibrateButton.repaint();
					lblOnResiduals.setText(getStringProperty("Residuals"));
					lblOnResiduals.repaint();
					lblOnIntercept.setText(getStringProperty("Intercept"));
					lblOnIntercept.repaint();
					lblOnSlope.setText(getStringProperty("Slope"));
					lblOnSlope.repaint();
				}
			
        }  
    } 
    
    void initGUI() {
	   
    	setTitle("pgFocus");
	    setBackground(gui_.getBackgroundColor());
	    gui_.addMMBackgroundListener(this);
	 	    	
	    
    	JTabbedPane tabbedPane = new JTabbedPane();
	    
	    pgFocusPanel_ = new pgFocusPanel(core_); 
	    tabbedPane.addTab("Real Time", pgFocusPanel_);
	    
	    lightPanel_ = new LightPanel(core_); 
	    tabbedPane.addTab("Light Profile", null, lightPanel_, null);
	    
	    calibratePanel_ = new CalibratePanel(core_); 
	    tabbedPane.addTab("Calibration", null, calibratePanel_, null);
	    
	    settingsPanel = new JPanel();	    
	    tabbedPane.addTab("Settings", null, settingsPanel, null);
	    
	    setContentPane(tabbedPane);
	
	    initRealTimePanel(pgFocusPanel_);
	    
	    initCalibratePanel(calibratePanel_);   
	    
	    initSettingsPanel(settingsPanel);
	    
    }
    
  void initRealTimePanel(pgFocusPanel pgFocusPlot_)
    {
	    // Main buttons under "Real Time Graph"    
	    Box horizontalBox1 = Box.createHorizontalBox();
	    pgFocusPlot_.add(horizontalBox1, BorderLayout.SOUTH);
	    
	    Component horizontalGlue_1 = Box.createHorizontalGlue();
	    horizontalBox1.add(horizontalGlue_1);
	    
	    tglbtnLockButton.setMaximumSize(new Dimension(150, 25));
	    tglbtnLockButton.setPreferredSize(new Dimension(150, 25));
	    tglbtnLockButton.addActionListener(new java.awt.event.ActionListener() {
		    public void actionPerformed(java.awt.event.ActionEvent evt) {
	        	if (evt.getActionCommand().equals("Unlocked")) {
	            	try {
	            		core_.enableContinuousFocus(true);
	            		tglbtnLockButton.setText("Locked");
	            		tglbtnLockButton.setSelected(true);
	    			} catch (Exception e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	            } 
	            
	            if (evt.getActionCommand().equals("Locked")) {
	            	try {
	            		core_.enableContinuousFocus(false);
	            		tglbtnLockButton.setText("Unlocked");
	            		tglbtnLockButton.setSelected(false);
	    			} catch (Exception e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	            } 
	        }
        });
	    
	    horizontalBox1.add(tglbtnLockButton);   
	    Component horizontalGlue_2 = Box.createHorizontalGlue();
	    horizontalBox1.add(horizontalGlue_2);
	    
    }
    
  
  void initSettingsPanel(JPanel settingsPanel_)
  {
	  	String iconImage = "umass_logo.png";
	  	String iconDescription = "University of Massachusetts Medical School";
	  	
	  	ImageIcon icon =  new ImageIcon(getClass().getResource(iconImage), iconDescription);
	  	
		GridBagLayout gbl_settingsPanel = new GridBagLayout();
		gbl_settingsPanel.columnWidths = new int[] {0, 0, 0, 50, 100, 0};
		gbl_settingsPanel.rowHeights = new int[] {0, 0, 0, 0, 0, 0, 0, 50, 125};
		gbl_settingsPanel.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
		gbl_settingsPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
		settingsPanel.setLayout(gbl_settingsPanel);
	    
	    JLabel lblIntercept = new JLabel("Intercept:");
	    GridBagConstraints gbc_lblIntercept = new GridBagConstraints();
	    gbc_lblIntercept.anchor = GridBagConstraints.EAST;
	    gbc_lblIntercept.insets = new Insets(0, 0, 5, 5);
	    gbc_lblIntercept.gridx = 4;
	    gbc_lblIntercept.gridy = 1;
	    settingsPanel.add(lblIntercept, gbc_lblIntercept);
	    
	    lblOnIntercept = new JLabel(getStringProperty("Intercept"));
	    GridBagConstraints gbc_lblOnIntercept = new GridBagConstraints();
	    gbc_lblOnIntercept.insets = new Insets(0, 0, 5, 0);
	    gbc_lblOnIntercept.gridx = 5;
	    gbc_lblOnIntercept.gridy = 1;
	    settingsPanel.add(lblOnIntercept, gbc_lblOnIntercept);
	    
	    chckbxAutoexpose = new JCheckBox("");
	    chckbxAutoexpose.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent arg0) {
	    		tfOnExposure.setEnabled(chckbxAutoexpose.isSelected());
	    		if (chckbxAutoexpose.isSelected()) {
	    			setStringProperty("Auto Exposure", "Off");
	    		} else {
	    			setStringProperty("Auto Exposure", "On");
	    		}
	    		
	    	}
	    });
	    chckbxAutoexpose.setToolTipText("When unselected, pgFocus will automatically adjust the exposure time");
	    GridBagConstraints gbc_chckbxAutoexpose = new GridBagConstraints();
	    gbc_chckbxAutoexpose.insets = new Insets(0, 0, 5, 5);
	    gbc_chckbxAutoexpose.gridx = 0;
	    gbc_chckbxAutoexpose.gridy = 2;
	    settingsPanel.add(chckbxAutoexpose, gbc_chckbxAutoexpose);
	    
	    JLabel lblExposure = new JLabel("Exposure:");
	    GridBagConstraints gbc_lblExposure = new GridBagConstraints();
	    gbc_lblExposure.anchor = GridBagConstraints.EAST;
	    gbc_lblExposure.insets = new Insets(0, 0, 5, 5);
	    gbc_lblExposure.gridx = 1;
	    gbc_lblExposure.gridy = 2;
	    settingsPanel.add(lblExposure, gbc_lblExposure);
	    
	    tfOnExposure = new JTextField(getStringProperty("Exposure"));
	    tfOnExposure.setEnabled(false);
	    tfOnExposure.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent arg0) {
	    		setStringProperty("Exposure",tfOnExposure.getText());
	    	}
	    });
	    tfOnExposure.setHorizontalAlignment(SwingConstants.RIGHT);
	    tfOnExposure.setToolTipText("Exposure time");
	    GridBagConstraints gbc_tfOnExposure = new GridBagConstraints();
	    gbc_tfOnExposure.anchor = GridBagConstraints.EAST;
	    gbc_tfOnExposure.insets = new Insets(0, 0, 5, 5);
	    gbc_tfOnExposure.gridx = 2;
	    gbc_tfOnExposure.gridy = 2;
	    settingsPanel.add(tfOnExposure, gbc_tfOnExposure);
	    tfOnExposure.setColumns(5);
	    
	    JLabel lblMs_3 = new JLabel("ms");
	    GridBagConstraints gbc_lblMs_3 = new GridBagConstraints();
	    gbc_lblMs_3.anchor = GridBagConstraints.WEST;
	    gbc_lblMs_3.insets = new Insets(0, 0, 5, 5);
	    gbc_lblMs_3.gridx = 3;
	    gbc_lblMs_3.gridy = 2;
	    settingsPanel.add(lblMs_3, gbc_lblMs_3);
	    
	    JLabel lblResiduals = new JLabel("Residuals:");
	    GridBagConstraints gbc_lblResiduals = new GridBagConstraints();
	    gbc_lblResiduals.anchor = GridBagConstraints.EAST;
	    gbc_lblResiduals.insets = new Insets(0, 0, 5, 5);
	    gbc_lblResiduals.gridx = 4;
	    gbc_lblResiduals.gridy = 2;
	    settingsPanel.add(lblResiduals, gbc_lblResiduals);
	    
	    lblOnResiduals = new JLabel(getStringProperty("Residuals"));
	    GridBagConstraints gbc_lblOnResiduals = new GridBagConstraints();
	    gbc_lblOnResiduals.insets = new Insets(0, 0, 5, 0);
	    gbc_lblOnResiduals.gridx = 5;
	    gbc_lblOnResiduals.gridy = 2;
	    settingsPanel.add(lblOnResiduals, gbc_lblOnResiduals);
	    
	    JLabel lblMs_4 = new JLabel("ms");
	    GridBagConstraints gbc_lblMs_4 = new GridBagConstraints();
	    gbc_lblMs_4.anchor = GridBagConstraints.WEST;
	    gbc_lblMs_4.insets = new Insets(0, 0, 5, 5);
	    gbc_lblMs_4.gridx = 3;
	    gbc_lblMs_4.gridy = 3;
	    settingsPanel.add(lblMs_4, gbc_lblMs_4);
	    
	    JLabel lblFirmware = new JLabel("Firmware:");
	    GridBagConstraints gbc_lblFirmware = new GridBagConstraints();
	    gbc_lblFirmware.anchor = GridBagConstraints.EAST;
	    gbc_lblFirmware.insets = new Insets(0, 0, 5, 5);
	    gbc_lblFirmware.gridx = 4;
	    gbc_lblFirmware.gridy = 6;
	    settingsPanel.add(lblFirmware, gbc_lblFirmware);
	    
	    JLabel lblOnFirmware = new JLabel(getStringProperty("Firmware"));
	    GridBagConstraints gbc_lblOnFirmware = new GridBagConstraints();
	    gbc_lblOnFirmware.insets = new Insets(0, 0, 5, 0);
	    gbc_lblOnFirmware.gridx = 5;
	    gbc_lblOnFirmware.gridy = 6;
	    settingsPanel.add(lblOnFirmware, gbc_lblOnFirmware);
		  
		chbUpdateRealTime = new JCheckBox("", true);
		chbUpdateRealTime.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				tfOnWaitAfterMessage.setEnabled(chbUpdateRealTime.isSelected());
				if (chbUpdateRealTime.isSelected()) {
					if (!pgFocusPanel_.timer.isRunning()) pgFocusPanel_.timer.start(); 
				}
				else if (pgFocusPanel_.timer.isRunning()) pgFocusPanel_.timer.stop();
			}
		});
		chbUpdateRealTime.setToolTipText("Turn on or off the Real Time Graph");
		

		GridBagConstraints gbc_chbUpdateRealTime = new GridBagConstraints();
		gbc_chbUpdateRealTime.fill = GridBagConstraints.VERTICAL;
		gbc_chbUpdateRealTime.insets = new Insets(0, 0, 5, 5);
		gbc_chbUpdateRealTime.gridx = 0;
		gbc_chbUpdateRealTime.gridy = 0;
		settingsPanel_.add(chbUpdateRealTime, gbc_chbUpdateRealTime);   
		
		JLabel lblRealTimeGraph = new JLabel("Real Time Graph:");
		GridBagConstraints gbc_lblRealTimeGraph = new GridBagConstraints();
		gbc_lblRealTimeGraph.insets = new Insets(0, 0, 5, 5);
		gbc_lblRealTimeGraph.anchor = GridBagConstraints.EAST;
		gbc_lblRealTimeGraph.gridx = 1;
		gbc_lblRealTimeGraph.gridy = 0;
		settingsPanel.add(lblRealTimeGraph, gbc_lblRealTimeGraph);
		
		tfOnWaitAfterMessage = new JTextField(getStringProperty("Wait ms after Message"));
		tfOnWaitAfterMessage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setStringProperty("Wait ms after Message",tfOnWaitAfterMessage.getText());
				pgFocusPanel_.timer.setDelay(Integer.parseInt(tfOnWaitAfterMessage.getText()));
			}
		});
		tfOnWaitAfterMessage.setColumns(5);
		tfOnWaitAfterMessage.setHorizontalAlignment(SwingConstants.RIGHT);
		tfOnWaitAfterMessage.setToolTipText("refresh rate in ms");
		
				GridBagConstraints gbc_tfOnWaitAfterMessage = new GridBagConstraints();
				gbc_tfOnWaitAfterMessage.anchor = GridBagConstraints.EAST;
				gbc_tfOnWaitAfterMessage.insets = new Insets(0, 0, 5, 5);
				gbc_tfOnWaitAfterMessage.gridx = 2;
				gbc_tfOnWaitAfterMessage.gridy = 0;
				settingsPanel_.add(tfOnWaitAfterMessage, gbc_tfOnWaitAfterMessage);
		
		JLabel lblMs_1 = new JLabel("ms");
		GridBagConstraints gbc_lblMs_1 = new GridBagConstraints();
		gbc_lblMs_1.anchor = GridBagConstraints.WEST;
		gbc_lblMs_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblMs_1.gridx = 3;
		gbc_lblMs_1.gridy = 0;
		settingsPanel.add(lblMs_1, gbc_lblMs_1);
		
		JLabel lblSlope = new JLabel("Slope:");
		GridBagConstraints gbc_lblSlope = new GridBagConstraints();
		gbc_lblSlope.anchor = GridBagConstraints.EAST;
		gbc_lblSlope.insets = new Insets(0, 0, 5, 5);
		gbc_lblSlope.gridx = 4;
		gbc_lblSlope.gridy = 0;
		settingsPanel.add(lblSlope, gbc_lblSlope);
		
		lblOnSlope = new JLabel(getStringProperty("Slope"));
		GridBagConstraints gbc_lblOnSlope = new GridBagConstraints();
		gbc_lblOnSlope.insets = new Insets(0, 0, 5, 0);
		gbc_lblOnSlope.gridx = 5;
		gbc_lblOnSlope.gridy = 0;
		settingsPanel.add(lblOnSlope, gbc_lblOnSlope);
		
		chbUpdateLight = new JCheckBox("", true);
		chbUpdateLight.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				tfOnWaitAfterLight.setEnabled(chbUpdateLight.isSelected());
				if (chbUpdateLight.isSelected()) {
					if (!lightPanel_.timer.isRunning()) lightPanel_.timer.start(); 
				}
				else if (lightPanel_.timer.isRunning()) lightPanel_.timer.stop();
			}
		});
		chbUpdateLight.setToolTipText("Turn on or off Light Profile Graph");
		
				GridBagConstraints gbc_chbUpdateLight = new GridBagConstraints();
				gbc_chbUpdateLight.fill = GridBagConstraints.VERTICAL;
				gbc_chbUpdateLight.insets = new Insets(0, 0, 5, 5);
				gbc_chbUpdateLight.gridx = 0;
				gbc_chbUpdateLight.gridy = 1;
				settingsPanel_.add(chbUpdateLight, gbc_chbUpdateLight);
		
		JLabel lblLightProfileGraph = new JLabel("Light Profile Graph:");
		GridBagConstraints gbc_lblLightProfileGraph = new GridBagConstraints();
		gbc_lblLightProfileGraph.insets = new Insets(0, 0, 5, 5);
		gbc_lblLightProfileGraph.anchor = GridBagConstraints.EAST;
		gbc_lblLightProfileGraph.gridx = 1;
		gbc_lblLightProfileGraph.gridy = 1;
		settingsPanel.add(lblLightProfileGraph, gbc_lblLightProfileGraph);
		
		tfOnWaitAfterLight = new JTextField(getStringProperty("Wait ms after Light"));
		tfOnWaitAfterLight.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setStringProperty("Wait ms after Light",tfOnWaitAfterLight.getText());
				lightPanel_.timer.setDelay(Integer.parseInt(tfOnWaitAfterLight.getText()));
			}
		});
		tfOnWaitAfterLight.setColumns(5);
		tfOnWaitAfterLight.setHorizontalAlignment(SwingConstants.RIGHT);
		tfOnWaitAfterLight.setToolTipText("refresh rate in ms");
		
				GridBagConstraints gbc_tfOnWaitAfterLight = new GridBagConstraints();
				gbc_tfOnWaitAfterLight.anchor = GridBagConstraints.EAST;
				gbc_tfOnWaitAfterLight.insets = new Insets(0, 0, 5, 5);
				gbc_tfOnWaitAfterLight.gridx = 2;
				gbc_tfOnWaitAfterLight.gridy = 1;
				settingsPanel_.add(tfOnWaitAfterLight, gbc_tfOnWaitAfterLight);
		
		JLabel lblMs_2 = new JLabel("ms");
		GridBagConstraints gbc_lblMs_2 = new GridBagConstraints();
		gbc_lblMs_2.anchor = GridBagConstraints.WEST;
		gbc_lblMs_2.insets = new Insets(0, 0, 5, 5);
		gbc_lblMs_2.gridx = 3;
		gbc_lblMs_2.gridy = 1;
		settingsPanel.add(lblMs_2, gbc_lblMs_2);
		
		JLabel lblWaitAfterLock = new JLabel("Wait After Lock:");
		GridBagConstraints gbc_lblWaitAfterLock = new GridBagConstraints();
		gbc_lblWaitAfterLock.anchor = GridBagConstraints.EAST;
		gbc_lblWaitAfterLock.insets = new Insets(0, 0, 5, 5);
		gbc_lblWaitAfterLock.gridx = 1;
		gbc_lblWaitAfterLock.gridy = 3;
		settingsPanel.add(lblWaitAfterLock, gbc_lblWaitAfterLock);
		
		tfOnWaitAfterLock = new JTextField(getStringProperty("Wait ms after Lock"));
		tfOnWaitAfterLock.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setStringProperty("Wait ms after Lock",tfOnWaitAfterLock.getText());
			}
		});
		tfOnWaitAfterLock.setHorizontalAlignment(SwingConstants.TRAILING);
		GridBagConstraints gbc_tfOnWaitAfterLock = new GridBagConstraints();
		gbc_tfOnWaitAfterLock.anchor = GridBagConstraints.EAST;
		gbc_tfOnWaitAfterLock.insets = new Insets(0, 0, 5, 5);
		gbc_tfOnWaitAfterLock.gridx = 2;
		gbc_tfOnWaitAfterLock.gridy = 3;
		settingsPanel.add(tfOnWaitAfterLock, gbc_tfOnWaitAfterLock);
		tfOnWaitAfterLock.setColumns(5);
		
		JLabel lblAdcGain = new JLabel("Input Gain:");
		lblAdcGain.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_lblAdcGain = new GridBagConstraints();
		gbc_lblAdcGain.anchor = GridBagConstraints.EAST;
		gbc_lblAdcGain.insets = new Insets(0, 0, 5, 5);
		gbc_lblAdcGain.gridx = 1;
		gbc_lblAdcGain.gridy = 4;
		settingsPanel.add(lblAdcGain, gbc_lblAdcGain);
		
		textInput_Gain = new JTextField(getStringProperty("Input Gain"));
		textInput_Gain.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setStringProperty("Input Gain",textInput_Gain.getText());
			}
		});
		textInput_Gain.setColumns(5);
		textInput_Gain.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_textInput_Gain = new GridBagConstraints();
		gbc_textInput_Gain.anchor = GridBagConstraints.EAST;
		gbc_textInput_Gain.insets = new Insets(0, 0, 5, 5);
		gbc_textInput_Gain.gridx = 2;
		gbc_textInput_Gain.gridy = 4;
		settingsPanel.add(textInput_Gain, gbc_textInput_Gain);
		
		JLabel lblOffset = new JLabel("Offset:");
		GridBagConstraints gbc_lblOffset = new GridBagConstraints();
		gbc_lblOffset.anchor = GridBagConstraints.EAST;
		gbc_lblOffset.insets = new Insets(0, 0, 5, 5);
		gbc_lblOffset.gridx = 1;
		gbc_lblOffset.gridy = 5;
		settingsPanel.add(lblOffset, gbc_lblOffset);
		
		textOnOffset = new JTextField(getStringProperty("Offset"));
		textOnOffset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setStringProperty("Offset",textOnOffset.getText());
			}
		});
		textOnOffset.setColumns(5);
		textOnOffset.setHorizontalAlignment(SwingConstants.TRAILING);
		GridBagConstraints gbc_textOnOffset = new GridBagConstraints();
		gbc_textOnOffset.anchor = GridBagConstraints.EAST;
		gbc_textOnOffset.insets = new Insets(0, 0, 5, 5);
		gbc_textOnOffset.gridx = 2;
		gbc_textOnOffset.gridy = 5;
		settingsPanel.add(textOnOffset, gbc_textOnOffset);
		
		JLabel lblMPV = new JLabel("Microns Per Volt:");
		lblMPV.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_lblMPV = new GridBagConstraints();
		gbc_lblMPV.anchor = GridBagConstraints.EAST;
		gbc_lblMPV.insets = new Insets(0, 0, 5, 5);
		gbc_lblMPV.gridx = 1;
		gbc_lblMPV.gridy = 6;
		settingsPanel.add(lblMPV, gbc_lblMPV);
		
		textOnnMPerVolt = new JTextField(getStringProperty("Microns Per Volt"));
		textOnnMPerVolt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setStringProperty("Microns Per Volt",textOnnMPerVolt.getText());
			}
		});
		textOnnMPerVolt.setColumns(5);
		textOnnMPerVolt.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_textOnnMPerVolt = new GridBagConstraints();
		gbc_textOnnMPerVolt.anchor = GridBagConstraints.EAST;
		gbc_textOnnMPerVolt.insets = new Insets(0, 0, 5, 5);
		gbc_textOnnMPerVolt.gridx = 2;
		gbc_textOnnMPerVolt.gridy = 6;
		settingsPanel.add(textOnnMPerVolt, gbc_textOnnMPerVolt);
	    
	
	    JLabel lblogo=new JLabel();
	    lblogo.setIcon(icon);
	    
	    GridBagConstraints gbc_lblogo = new GridBagConstraints();
	    gbc_lblogo.anchor = GridBagConstraints.EAST;
	    gbc_lblogo.fill = GridBagConstraints.VERTICAL;
	    gbc_lblogo.insets = new Insets(0, 0, 0, 5);
	    gbc_lblogo.gridx = 1;
	    gbc_lblogo.gridy = 8;
	    settingsPanel_.add(lblogo, gbc_lblogo);
	    
	    JLabel lbcontact=new JLabel();
	    lbcontact.setText("<html>Karl Bellv&eacute;<br>Biomedical Imaging Group<br>Molecular Medicine<br>University of Massachusetts Medical School<br>http://big.umassmed.edu</html>");
	    GridBagConstraints gbc_lbcontact = new GridBagConstraints();
	    gbc_lbcontact.gridwidth = 4;
	    gbc_lbcontact.fill = GridBagConstraints.BOTH;
	    gbc_lbcontact.gridx = 2;
	    gbc_lbcontact.gridy = 8;
	    settingsPanel_.add(lbcontact, gbc_lbcontact); 
		    
		
  }
  
    void initCalibratePanel(CalibratePanel calibratePlot)
    {
	    // Main buttons under "Calibrate Graph"    
	    Box horizontalBox = Box.createHorizontalBox();
	    calibratePlot.add(horizontalBox, BorderLayout.SOUTH);
	    
	    Component horizontalGlue_1 = Box.createHorizontalGlue();
	    horizontalBox.add(horizontalGlue_1);
	    
	    btnCalibrateButton.setMaximumSize(new Dimension(150, 25));
	    btnCalibrateButton.setPreferredSize(new Dimension(150, 25));
	    btnCalibrateButton.addActionListener(new java.awt.event.ActionListener() {
		    public void actionPerformed(java.awt.event.ActionEvent evt) {
	        	if (evt.getActionCommand().equals("Calibrate")) {
	            	try {
	            		setStringProperty("Focus Mode","Calibration");
	            		btnCalibrateButton.setText("Processing");
	            		calibratePanel_.timer.start();
	    			} catch (Exception e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	            } 
	        }
        });
	    horizontalBox.add(btnCalibrateButton);
	    
	    Component horizontalGlue_2 = Box.createHorizontalGlue();
	    horizontalBox.add(horizontalGlue_2);
	
    }
    
    public void focusLockButton() {
    
		try {
			if (core_.isContinuousFocusEnabled() == true) {
				if (tglbtnLockButton.getText().compareTo("Locked") != 0) 
				{
					tglbtnLockButton.setText("Locked");
					tglbtnLockButton.setSelected(true);
					tglbtnLockButton.repaint();
				}
			}
			else {
				if (tglbtnLockButton.getText().compareTo("Unlocked") != 0) {
					tglbtnLockButton.setText("Unlocked");
					tglbtnLockButton.setSelected(false);
					tglbtnLockButton.repaint();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    public void safePrefs() {
    	
		prefs_.putInt(FRAMEXPOS, this.getX());
		prefs_.putInt(FRAMEYPOS, this.getY());
		        
		if (savedCalibrationCurve.equals("Please perform a calibration") == false ) {
			if (savedCalibrationCurve.equals("Please wait while calibrating...") == false ) {
			   if (savedCalibrationCurve.isEmpty() == false) {
				   System.out.print("pgFocus: saving curve: ");
				   System.out.println(savedCalibrationCurve);
				   prefs_.put(CALIBRATIONCURVE, savedCalibrationCurve);
			   }
			}
		}

     }
    
    
    int getIntProperty(String property) {
    	try {
    		
    		String val;
    				
            val = core_.getProperty(pgFocus_, property);
            if (val.equals("NA")) return 0;
            
            int intVal = Integer.parseInt(val);
            
            return (intVal);
    	
    	} catch (Exception ex) {
    		ReportingUtils.showError("pgFocus::getIntValue() Error reading values from pgFocus: " + property);
        }
  
    	return (0);
    }
    
    float getFloatProperty(String property) {
    	try {
    		
    		String val;
    		
            val = core_.getProperty(pgFocus_, property);
            if (val.equals("NA")) return 0;
            
            float floatVal = Float.parseFloat(val);
                  
            return (floatVal);
    	
    	} catch (Exception ex) {
    		ReportingUtils.showError("pgFocus::getFloatValue() Error reading values from pgFocus: " + property);
        }
  
    	return (0);
    }
    
     String getStringProperty(String property) {
    	try {
    		
    		String val;
    		
            val = core_.getProperty(pgFocus_, property);
                       
            return (val);
    	
    	} catch (Exception ex) {
    		ReportingUtils.showError("pgFocus::getStringValue() Error reading values from pgFocus: " + property);
        }
  
    	return (null);
    }
     
     void setStringProperty(String property, String value) {
     	try {
	     	core_.setProperty(pgFocus_, property, value);           
     	
     	} catch (Exception ex) {
     		ReportingUtils.showError("pgFocus::getStringValue() Error reading values from pgFocus: " + property);
         }
   

     }
     


}

