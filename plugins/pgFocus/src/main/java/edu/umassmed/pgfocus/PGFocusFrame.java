package edu.umassmed.pgfocus;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
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
import org.micromanager.Studio;
import org.micromanager.internal.utils.ReportingUtils;


public class PGFocusFrame extends JFrame {

   private final Studio gui_;
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

   private PGFocusPanel pgFocusPanel_;
   private LightPanel lightPanel_;
   private CalibratePanel calibratePanel_;

   private JCheckBox chbUpdateRealTime;
   private JCheckBox chbUpdateLight;
   private JCheckBox chckbxAutoexpose;
   private JPanel settingsPanel;

   private JTextField textInputGain;
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

   /**
    * Creates pgFocus.
    */
   public PGFocusFrame(Studio gui) {


      gui_ = gui;
      core_ = gui.getCMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      pgFocus_ = "";

      mmcorej.StrVector afs =
            core_.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);

      boolean found = false;


      for (String af : afs) {
         try {
            if (core_.hasProperty(af, "Description")) {
               if (core_.getProperty(af, "Description")
                     .equals("Open Source and Open Hardware Focus Stabilization Device")) {
                  found = true;
                  pgFocus_ = af;
               }
            }
         } catch (Exception ex) {
            Logger.getLogger(PGFocus.class.getName()).log(Level.SEVERE, null, ex);
         }
      }

      if (!found) {
         gui_.logs().showError(
               "This plugin needs pgFocus by: \n\nKarl Bellve\nBiomedical Imaging Group\n"
               + "Molecular Medicine\nUniversity of Massachusetts Medical School\n");
         throw new IllegalArgumentException("Could not find the pgFocus hardware");
      }

      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);
      savedCalibrationCurve = prefs_.get(CALIBRATIONCURVE, savedCalibrationCurve);

      setLocation(frameXPos_, frameYPos_);

      initGUI();

   }


   class PGFocusPanel extends JPanel implements ActionListener {
      private static final long serialVersionUID = 1L;
      CMMCore core_;
      int waitTime = getIntProperty("Wait ms after Message");
      /**
       * Timer to refresh graph after every 1 second.
       */
      Timer timer = new Timer(waitTime, this);

      public void actionPerformed(ActionEvent actionevent) {
         updateGraph();
      }

      public static final int SUBPLOT_COUNT = 4;
      private TimeSeriesCollection[] datasets;
      private double[] lastValue;

      public PGFocusPanel(CMMCore core) {

         super(new BorderLayout());

         String title = "pgFocus";

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
                  title = "Offset";
                  break;
               case 1:
                  title = "SD nM";
                  break;
               case 2:
                  title = "Output nM";
                  break;
               case 3:
                  title = "Input nM";
                  break;
               default:
                  break;
            }

            NumberAxis numberaxis = new NumberAxis(title);
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

      /**
       * Timer to refresh graph after every 1 second.
       */
      final Timer timer = new Timer(waitTime, this);

      public void actionPerformed(ActionEvent actionevent) {
         updateGraph();
      }


      public LightPanel(CMMCore core) {

         super(new BorderLayout());
         String title = "Light Profile";

         core_ = core;

         timer.setInitialDelay(5000);

         lightProfile = new XYSeries(title);

         lightProfileSet = new XYSeriesCollection(lightProfile);

         JFreeChart lightChart = ChartFactory.createXYLineChart(
               "",
               "Pixel",
               "Light",
               lightProfileSet,
               PlotOrientation.VERTICAL,
               false, false, false);

         lightChart.removeLegend();
         lightChart.setBorderPaint(Color.black);
         lightChart.setBorderVisible(true);
         lightChart.setBackgroundPaint(Color.white);

         XYPlot xyplot = lightChart.getXYPlot();

         xyplot.setBackgroundPaint(Color.lightGray);
         xyplot.setDomainGridlinePaint(Color.white);
         xyplot.setRangeGridlinePaint(Color.white);
         //xyplot.setAxisOffset(new RectangleInsets(4D, 4D, 4D, 4D));

         ValueAxis xAxis = xyplot.getDomainAxis();
         xAxis.setAutoRange(false);
         xAxis.setRange(0, 127);

         ValueAxis yAxis = xyplot.getRangeAxis();
         yAxis.setAutoRange(false);
         yAxis.setRange(0, 1023);

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

         String[] lightSplit = light.split(",");

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

      /**
       * Timer to refresh graph after every 1 second.
       */
      private Timer timer = new Timer(2000, this);

      public void actionPerformed(ActionEvent actionevent) {
         updateGraph();
      }


      public CalibratePanel(CMMCore core) {
         super(new BorderLayout());
         String title = "Calibration Profile";
         core_ = core;
         timer.setInitialDelay(2000);
         calibrateProfile = new XYSeries(title);
         calibrateProfileSet = new XYSeriesCollection(calibrateProfile);
         JFreeChart calibrateChart = ChartFactory.createXYLineChart(
               "",
               "DAU",
               "Position",
               calibrateProfileSet,
               PlotOrientation.VERTICAL,
               false, false, false);

         calibrateChart.removeLegend();
         calibrateChart.setBorderPaint(Color.black);
         calibrateChart.setBorderVisible(true);
         calibrateChart.setBackgroundPaint(Color.white);

         XYPlot xyplot = calibrateChart.getXYPlot();

         xyplot.setBackgroundPaint(Color.lightGray);
         xyplot.setDomainGridlinePaint(Color.white);
         xyplot.setRangeGridlinePaint(Color.white);

         ValueAxis xAxis = xyplot.getDomainAxis();
         //xAxis.setAutoRange(true);
         xAxis.setRange(-1000, 1000);

         ValueAxis yAxis = xyplot.getRangeAxis();
         //yAxis.setAutoRange(false);
         yAxis.setRange(40, 100);

         calibratePanel = new ChartPanel(calibrateChart);

         add(calibratePanel, BorderLayout.CENTER);

         calibratePanel.setPreferredSize(new Dimension(800, 50));
         calibratePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

         updateValues();

      }

      private boolean updateValues() {
         String curve = getStringProperty("Calibration curve");

         try {
            if (curve.length() > 0) {

               if (curve.equals("Please wait while calibrating...")) {
                  return false;
               }

               if (getStringProperty("Focus Mode").equals("Calibration")) {
                  return false;
               }

               if (curve.equals("Please perform a calibration")) {
                  if (savedCalibrationCurve.isEmpty() == false) {
                     curve = savedCalibrationCurve;
                  } else {
                     return false;
                  }
               }

               savedCalibrationCurve = curve;

               String[] calibrateSplit = curve.split(",");

               calibrateProfile.clear();

               for (int i = 0; i < calibrateSplit.length; i++) {
                  int dau = Integer.parseInt(calibrateSplit[i]) - 16384 / 2;
                  float lightPosition = Float.parseFloat(calibrateSplit[++i]);
                  calibrateProfile.add(dau, lightPosition);
               }
            }
         } catch (NumberFormatException e) {
            System.out.print("pgFocus plugin: corrupted calibration profile!");
         }

         return true;
      }

      private void updateGraph() {

         if (updateValues()) {
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

      JTabbedPane tabbedPane = new JTabbedPane();

      pgFocusPanel_ = new PGFocusPanel(core_);
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

   void initRealTimePanel(PGFocusPanel pgFocusPlot) {
      // Main buttons under "Real Time Graph"
      Box horizontalBox1 = Box.createHorizontalBox();
      pgFocusPlot.add(horizontalBox1, BorderLayout.SOUTH);

      Component horizontalGlue1 = Box.createHorizontalGlue();
      horizontalBox1.add(horizontalGlue1);

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
      Component horizontalGlue2 = Box.createHorizontalGlue();
      horizontalBox1.add(horizontalGlue2);

   }


   void initSettingsPanel(JPanel settingsPanel) {
      String iconImage = "umass_logo.png";
      String iconDescription = "University of Massachusetts Medical School";

      final ImageIcon icon = new ImageIcon(getClass().getResource(iconImage), iconDescription);

      GridBagLayout gblSettingsPanel = new GridBagLayout();
      gblSettingsPanel.columnWidths = new int[] {0, 0, 0, 50, 100, 0};
      gblSettingsPanel.rowHeights = new int[] {0, 0, 0, 0, 0, 0, 0, 50, 125};
      gblSettingsPanel.columnWeights = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
      gblSettingsPanel.rowWeights = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
      this.settingsPanel.setLayout(gblSettingsPanel);

      final JLabel lblIntercept = new JLabel("Intercept:");
      GridBagConstraints gbcLblIntercept = new GridBagConstraints();
      gbcLblIntercept.anchor = GridBagConstraints.EAST;
      gbcLblIntercept.insets = new Insets(0, 0, 5, 5);
      gbcLblIntercept.gridx = 4;
      gbcLblIntercept.gridy = 1;
      this.settingsPanel.add(lblIntercept, gbcLblIntercept);

      lblOnIntercept = new JLabel(getStringProperty("Intercept"));
      GridBagConstraints gbcLblOnIntercept = new GridBagConstraints();
      gbcLblOnIntercept.insets = new Insets(0, 0, 5, 0);
      gbcLblOnIntercept.gridx = 5;
      gbcLblOnIntercept.gridy = 1;
      this.settingsPanel.add(lblOnIntercept, gbcLblOnIntercept);

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
      chckbxAutoexpose.setToolTipText(
            "When unselected, pgFocus will automatically adjust the exposure time");
      GridBagConstraints gbcChckbxAutoexpose = new GridBagConstraints();
      gbcChckbxAutoexpose.insets = new Insets(0, 0, 5, 5);
      gbcChckbxAutoexpose.gridx = 0;
      gbcChckbxAutoexpose.gridy = 2;
      this.settingsPanel.add(chckbxAutoexpose, gbcChckbxAutoexpose);

      final JLabel lblExposure = new JLabel("Exposure:");
      GridBagConstraints gbcLblExposure = new GridBagConstraints();
      gbcLblExposure.anchor = GridBagConstraints.EAST;
      gbcLblExposure.insets = new Insets(0, 0, 5, 5);
      gbcLblExposure.gridx = 1;
      gbcLblExposure.gridy = 2;
      this.settingsPanel.add(lblExposure, gbcLblExposure);

      tfOnExposure = new JTextField(getStringProperty("Exposure"));
      tfOnExposure.setEnabled(false);
      tfOnExposure.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Exposure", tfOnExposure.getText());
         }
      });
      tfOnExposure.setHorizontalAlignment(SwingConstants.RIGHT);
      tfOnExposure.setToolTipText("Exposure time");
      GridBagConstraints gbcTfOnExposure = new GridBagConstraints();
      gbcTfOnExposure.anchor = GridBagConstraints.EAST;
      gbcTfOnExposure.insets = new Insets(0, 0, 5, 5);
      gbcTfOnExposure.gridx = 2;
      gbcTfOnExposure.gridy = 2;
      this.settingsPanel.add(tfOnExposure, gbcTfOnExposure);
      tfOnExposure.setColumns(5);

      final JLabel lblMs3 = new JLabel("ms");
      GridBagConstraints gbcLblMs3 = new GridBagConstraints();
      gbcLblMs3.anchor = GridBagConstraints.WEST;
      gbcLblMs3.insets = new Insets(0, 0, 5, 5);
      gbcLblMs3.gridx = 3;
      gbcLblMs3.gridy = 2;
      this.settingsPanel.add(lblMs3, gbcLblMs3);

      final JLabel lblResiduals = new JLabel("Residuals:");
      GridBagConstraints gbcLblResiduals = new GridBagConstraints();
      gbcLblResiduals.anchor = GridBagConstraints.EAST;
      gbcLblResiduals.insets = new Insets(0, 0, 5, 5);
      gbcLblResiduals.gridx = 4;
      gbcLblResiduals.gridy = 2;
      this.settingsPanel.add(lblResiduals, gbcLblResiduals);

      lblOnResiduals = new JLabel(getStringProperty("Residuals"));
      GridBagConstraints gbcLblOnResiduals = new GridBagConstraints();
      gbcLblOnResiduals.insets = new Insets(0, 0, 5, 0);
      gbcLblOnResiduals.gridx = 5;
      gbcLblOnResiduals.gridy = 2;
      this.settingsPanel.add(lblOnResiduals, gbcLblOnResiduals);

      final JLabel lblMs4 = new JLabel("ms");
      GridBagConstraints gbcLblMs4 = new GridBagConstraints();
      gbcLblMs4.anchor = GridBagConstraints.WEST;
      gbcLblMs4.insets = new Insets(0, 0, 5, 5);
      gbcLblMs4.gridx = 3;
      gbcLblMs4.gridy = 3;
      this.settingsPanel.add(lblMs4, gbcLblMs4);

      final JLabel lblFirmware = new JLabel("Firmware:");
      GridBagConstraints gbcLblFirmware = new GridBagConstraints();
      gbcLblFirmware.anchor = GridBagConstraints.EAST;
      gbcLblFirmware.insets = new Insets(0, 0, 5, 5);
      gbcLblFirmware.gridx = 4;
      gbcLblFirmware.gridy = 6;
      this.settingsPanel.add(lblFirmware, gbcLblFirmware);

      final JLabel lblOnFirmware = new JLabel(getStringProperty("Firmware"));
      GridBagConstraints gbcLblOnFirmware = new GridBagConstraints();
      gbcLblOnFirmware.insets = new Insets(0, 0, 5, 0);
      gbcLblOnFirmware.gridx = 5;
      gbcLblOnFirmware.gridy = 6;
      this.settingsPanel.add(lblOnFirmware, gbcLblOnFirmware);

      chbUpdateRealTime = new JCheckBox("", true);
      chbUpdateRealTime.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            tfOnWaitAfterMessage.setEnabled(chbUpdateRealTime.isSelected());
            if (chbUpdateRealTime.isSelected()) {
               if (!pgFocusPanel_.timer.isRunning()) {
                  pgFocusPanel_.timer.start();
               }
            } else if (pgFocusPanel_.timer.isRunning()) {
               pgFocusPanel_.timer.stop();
            }
         }
      });
      chbUpdateRealTime.setToolTipText("Turn on or off the Real Time Graph");


      GridBagConstraints gbcChbUpdateRealTime = new GridBagConstraints();
      gbcChbUpdateRealTime.fill = GridBagConstraints.VERTICAL;
      gbcChbUpdateRealTime.insets = new Insets(0, 0, 5, 5);
      gbcChbUpdateRealTime.gridx = 0;
      gbcChbUpdateRealTime.gridy = 0;
      settingsPanel.add(chbUpdateRealTime, gbcChbUpdateRealTime);

      final JLabel lblRealTimeGraph = new JLabel("Real Time Graph:");
      GridBagConstraints gbcLblRealTimeGraph = new GridBagConstraints();
      gbcLblRealTimeGraph.insets = new Insets(0, 0, 5, 5);
      gbcLblRealTimeGraph.anchor = GridBagConstraints.EAST;
      gbcLblRealTimeGraph.gridx = 1;
      gbcLblRealTimeGraph.gridy = 0;
      this.settingsPanel.add(lblRealTimeGraph, gbcLblRealTimeGraph);

      tfOnWaitAfterMessage = new JTextField(getStringProperty("Wait ms after Message"));
      tfOnWaitAfterMessage.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Wait ms after Message", tfOnWaitAfterMessage.getText());
            pgFocusPanel_.timer.setDelay(Integer.parseInt(tfOnWaitAfterMessage.getText()));
         }
      });
      tfOnWaitAfterMessage.setColumns(5);
      tfOnWaitAfterMessage.setHorizontalAlignment(SwingConstants.RIGHT);
      tfOnWaitAfterMessage.setToolTipText("refresh rate in ms");

      GridBagConstraints gbcTfOnWaitAfterMessage = new GridBagConstraints();
      gbcTfOnWaitAfterMessage.anchor = GridBagConstraints.EAST;
      gbcTfOnWaitAfterMessage.insets = new Insets(0, 0, 5, 5);
      gbcTfOnWaitAfterMessage.gridx = 2;
      gbcTfOnWaitAfterMessage.gridy = 0;
      settingsPanel.add(tfOnWaitAfterMessage, gbcTfOnWaitAfterMessage);

      final JLabel lblMs1 = new JLabel("ms");
      GridBagConstraints gbcLblMs1 = new GridBagConstraints();
      gbcLblMs1.anchor = GridBagConstraints.WEST;
      gbcLblMs1.insets = new Insets(0, 0, 5, 5);
      gbcLblMs1.gridx = 3;
      gbcLblMs1.gridy = 0;
      this.settingsPanel.add(lblMs1, gbcLblMs1);

      final JLabel lblSlope = new JLabel("Slope:");
      GridBagConstraints gbcLblSlope = new GridBagConstraints();
      gbcLblSlope.anchor = GridBagConstraints.EAST;
      gbcLblSlope.insets = new Insets(0, 0, 5, 5);
      gbcLblSlope.gridx = 4;
      gbcLblSlope.gridy = 0;
      this.settingsPanel.add(lblSlope, gbcLblSlope);

      lblOnSlope = new JLabel(getStringProperty("Slope"));
      GridBagConstraints gbcLblOnSlope = new GridBagConstraints();
      gbcLblOnSlope.insets = new Insets(0, 0, 5, 0);
      gbcLblOnSlope.gridx = 5;
      gbcLblOnSlope.gridy = 0;
      this.settingsPanel.add(lblOnSlope, gbcLblOnSlope);

      chbUpdateLight = new JCheckBox("", true);
      chbUpdateLight.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            tfOnWaitAfterLight.setEnabled(chbUpdateLight.isSelected());
            if (chbUpdateLight.isSelected()) {
               if (!lightPanel_.timer.isRunning()) {
                  lightPanel_.timer.start();
               }
            } else if (lightPanel_.timer.isRunning()) {
               lightPanel_.timer.stop();
            }
         }
      });
      chbUpdateLight.setToolTipText("Turn on or off Light Profile Graph");

      GridBagConstraints gbcChbUpdateLight = new GridBagConstraints();
      gbcChbUpdateLight.fill = GridBagConstraints.VERTICAL;
      gbcChbUpdateLight.insets = new Insets(0, 0, 5, 5);
      gbcChbUpdateLight.gridx = 0;
      gbcChbUpdateLight.gridy = 1;
      settingsPanel.add(chbUpdateLight, gbcChbUpdateLight);

      final JLabel lblLightProfileGraph = new JLabel("Light Profile Graph:");
      GridBagConstraints gbcLblLightProfileGraph = new GridBagConstraints();
      gbcLblLightProfileGraph.insets = new Insets(0, 0, 5, 5);
      gbcLblLightProfileGraph.anchor = GridBagConstraints.EAST;
      gbcLblLightProfileGraph.gridx = 1;
      gbcLblLightProfileGraph.gridy = 1;
      this.settingsPanel.add(lblLightProfileGraph, gbcLblLightProfileGraph);

      tfOnWaitAfterLight = new JTextField(getStringProperty("Wait ms after Light"));
      tfOnWaitAfterLight.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Wait ms after Light", tfOnWaitAfterLight.getText());
            lightPanel_.timer.setDelay(Integer.parseInt(tfOnWaitAfterLight.getText()));
         }
      });
      tfOnWaitAfterLight.setColumns(5);
      tfOnWaitAfterLight.setHorizontalAlignment(SwingConstants.RIGHT);
      tfOnWaitAfterLight.setToolTipText("refresh rate in ms");

      GridBagConstraints gbcTfOnWaitAfterLight = new GridBagConstraints();
      gbcTfOnWaitAfterLight.anchor = GridBagConstraints.EAST;
      gbcTfOnWaitAfterLight.insets = new Insets(0, 0, 5, 5);
      gbcTfOnWaitAfterLight.gridx = 2;
      gbcTfOnWaitAfterLight.gridy = 1;
      settingsPanel.add(tfOnWaitAfterLight, gbcTfOnWaitAfterLight);

      final JLabel lblMs2 = new JLabel("ms");
      GridBagConstraints gbcLblMs2 = new GridBagConstraints();
      gbcLblMs2.anchor = GridBagConstraints.WEST;
      gbcLblMs2.insets = new Insets(0, 0, 5, 5);
      gbcLblMs2.gridx = 3;
      gbcLblMs2.gridy = 1;
      this.settingsPanel.add(lblMs2, gbcLblMs2);

      final JLabel lblWaitAfterLock = new JLabel("Wait After Lock:");
      GridBagConstraints gbcLblWaitAfterLock = new GridBagConstraints();
      gbcLblWaitAfterLock.anchor = GridBagConstraints.EAST;
      gbcLblWaitAfterLock.insets = new Insets(0, 0, 5, 5);
      gbcLblWaitAfterLock.gridx = 1;
      gbcLblWaitAfterLock.gridy = 3;
      this.settingsPanel.add(lblWaitAfterLock, gbcLblWaitAfterLock);

      tfOnWaitAfterLock = new JTextField(getStringProperty("Wait ms after Lock"));
      tfOnWaitAfterLock.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Wait ms after Lock", tfOnWaitAfterLock.getText());
         }
      });
      tfOnWaitAfterLock.setHorizontalAlignment(SwingConstants.TRAILING);
      GridBagConstraints gbcTfOnWaitAfterLock = new GridBagConstraints();
      gbcTfOnWaitAfterLock.anchor = GridBagConstraints.EAST;
      gbcTfOnWaitAfterLock.insets = new Insets(0, 0, 5, 5);
      gbcTfOnWaitAfterLock.gridx = 2;
      gbcTfOnWaitAfterLock.gridy = 3;
      this.settingsPanel.add(tfOnWaitAfterLock, gbcTfOnWaitAfterLock);
      tfOnWaitAfterLock.setColumns(5);

      JLabel lblAdcGain = new JLabel("Input Gain:");
      lblAdcGain.setHorizontalAlignment(SwingConstants.RIGHT);
      GridBagConstraints gbcLblAdcGain = new GridBagConstraints();
      gbcLblAdcGain.anchor = GridBagConstraints.EAST;
      gbcLblAdcGain.insets = new Insets(0, 0, 5, 5);
      gbcLblAdcGain.gridx = 1;
      gbcLblAdcGain.gridy = 4;
      this.settingsPanel.add(lblAdcGain, gbcLblAdcGain);

      textInputGain = new JTextField(getStringProperty("Input Gain"));
      textInputGain.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Input Gain", textInputGain.getText());
         }
      });
      textInputGain.setColumns(5);
      textInputGain.setHorizontalAlignment(SwingConstants.RIGHT);
      GridBagConstraints gbcTextInputGain = new GridBagConstraints();
      gbcTextInputGain.anchor = GridBagConstraints.EAST;
      gbcTextInputGain.insets = new Insets(0, 0, 5, 5);
      gbcTextInputGain.gridx = 2;
      gbcTextInputGain.gridy = 4;
      this.settingsPanel.add(textInputGain, gbcTextInputGain);

      final JLabel lblOffset = new JLabel("Offset:");
      GridBagConstraints gbcLblOffset = new GridBagConstraints();
      gbcLblOffset.anchor = GridBagConstraints.EAST;
      gbcLblOffset.insets = new Insets(0, 0, 5, 5);
      gbcLblOffset.gridx = 1;
      gbcLblOffset.gridy = 5;
      this.settingsPanel.add(lblOffset, gbcLblOffset);

      textOnOffset = new JTextField(getStringProperty("Offset"));
      textOnOffset.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Offset", textOnOffset.getText());
         }
      });
      textOnOffset.setColumns(5);
      textOnOffset.setHorizontalAlignment(SwingConstants.TRAILING);
      GridBagConstraints gbcTextOnOffset = new GridBagConstraints();
      gbcTextOnOffset.anchor = GridBagConstraints.EAST;
      gbcTextOnOffset.insets = new Insets(0, 0, 5, 5);
      gbcTextOnOffset.gridx = 2;
      gbcTextOnOffset.gridy = 5;
      this.settingsPanel.add(textOnOffset, gbcTextOnOffset);

      final JLabel lblMPV = new JLabel("Microns Per Volt:");
      lblMPV.setHorizontalAlignment(SwingConstants.RIGHT);
      GridBagConstraints gbcLblMPV = new GridBagConstraints();
      gbcLblMPV.anchor = GridBagConstraints.EAST;
      gbcLblMPV.insets = new Insets(0, 0, 5, 5);
      gbcLblMPV.gridx = 1;
      gbcLblMPV.gridy = 6;
      this.settingsPanel.add(lblMPV, gbcLblMPV);

      textOnnMPerVolt = new JTextField(getStringProperty("Microns Per Volt"));
      textOnnMPerVolt.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setStringProperty("Microns Per Volt", textOnnMPerVolt.getText());
         }
      });
      textOnnMPerVolt.setColumns(5);
      textOnnMPerVolt.setHorizontalAlignment(SwingConstants.RIGHT);
      GridBagConstraints gbcTextOnnMPerVolt = new GridBagConstraints();
      gbcTextOnnMPerVolt.anchor = GridBagConstraints.EAST;
      gbcTextOnnMPerVolt.insets = new Insets(0, 0, 5, 5);
      gbcTextOnnMPerVolt.gridx = 2;
      gbcTextOnnMPerVolt.gridy = 6;
      this.settingsPanel.add(textOnnMPerVolt, gbcTextOnnMPerVolt);


      JLabel lblogo = new JLabel();
      lblogo.setIcon(icon);

      GridBagConstraints gbcLblogo = new GridBagConstraints();
      gbcLblogo.anchor = GridBagConstraints.EAST;
      gbcLblogo.fill = GridBagConstraints.VERTICAL;
      gbcLblogo.insets = new Insets(0, 0, 0, 5);
      gbcLblogo.gridx = 1;
      gbcLblogo.gridy = 8;
      settingsPanel.add(lblogo, gbcLblogo);

      JLabel lbcontact = new JLabel();
      lbcontact.setText(
            "<html>Karl Bellv&eacute;<br>Biomedical Imaging Group<br>Molecular Medicine<br>University of Massachusetts Medical School<br>http://big.umassmed.edu</html>");
      GridBagConstraints gbcLbcontact = new GridBagConstraints();
      gbcLbcontact.gridwidth = 4;
      gbcLbcontact.fill = GridBagConstraints.BOTH;
      gbcLbcontact.gridx = 2;
      gbcLbcontact.gridy = 8;
      settingsPanel.add(lbcontact, gbcLbcontact);


   }

   void initCalibratePanel(CalibratePanel calibratePlot) {
      // Main buttons under "Calibrate Graph"
      Box horizontalBox = Box.createHorizontalBox();
      calibratePlot.add(horizontalBox, BorderLayout.SOUTH);

      Component horizontalGlue1 = Box.createHorizontalGlue();
      horizontalBox.add(horizontalGlue1);

      btnCalibrateButton.setMaximumSize(new Dimension(150, 25));
      btnCalibrateButton.setPreferredSize(new Dimension(150, 25));
      btnCalibrateButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            if (evt.getActionCommand().equals("Calibrate")) {
               try {
                  setStringProperty("Focus Mode", "Calibration");
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

      Component horizontalGlue2 = Box.createHorizontalGlue();
      horizontalBox.add(horizontalGlue2);

   }

   public void focusLockButton() {

      try {
         if (core_.isContinuousFocusEnabled() == true) {
            if (tglbtnLockButton.getText().compareTo("Locked") != 0) {
               tglbtnLockButton.setText("Locked");
               tglbtnLockButton.setSelected(true);
               tglbtnLockButton.repaint();
            }
         } else {
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

      if (savedCalibrationCurve.equals("Please perform a calibration") == false) {
         if (savedCalibrationCurve.equals("Please wait while calibrating...") == false) {
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
         if (val.equals("NA")) {
            return 0;
         }

         int intVal = Integer.parseInt(val);

         return (intVal);

      } catch (Exception ex) {
         ReportingUtils.showError(
               "pgFocus::getIntValue() Error reading values from pgFocus: " + property);
      }

      return (0);
   }

   float getFloatProperty(String property) {
      try {

         String val;

         val = core_.getProperty(pgFocus_, property);
         if (val.equals("NA")) {
            return 0;
         }

         float floatVal = Float.parseFloat(val);

         return (floatVal);

      } catch (Exception ex) {
         ReportingUtils.showError(
               "pgFocus::getFloatValue() Error reading values from pgFocus: " + property);
      }

      return (0);
   }

   String getStringProperty(String property) {
      try {

         String val;

         val = core_.getProperty(pgFocus_, property);

         return (val);

      } catch (Exception ex) {
         ReportingUtils.showError(
               "pgFocus::getStringValue() Error reading values from pgFocus: " + property);
      }

      return (null);
   }

   void setStringProperty(String property, String value) {
      try {
         core_.setProperty(pgFocus_, property, value);

      } catch (Exception ex) {
         ReportingUtils.showError(
               "pgFocus::getStringValue() Error reading values from pgFocus: " + property);
      }


   }


}

