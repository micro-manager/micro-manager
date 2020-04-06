/*
 * CRISPFrame.java
 * 
 * Micro-Manager plugin for control of the ASI CRISP autofocus unit
 * 
 * Nico Stuurman (nico@cmp.ucsf.edu)
 * 
 * Copyright Regents of the University of California, 2011-2016
 * 
 * Licensed under the BSD license
 *
 * Created on Nov 15, 2011, 5:33:49 PM
 */

package org.micromanager.CRISP;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerModel;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import org.micromanager.Studio;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.MMFrame;

/**
 * Micro-Manager plugin for control of the ASI CRISP autofocus
 * 
 * @author Nico Stuurman
 */
@SuppressWarnings("serial")
public class CRISPFrame extends MMFrame {

    private final Studio gui_;
    private final CMMCore core_;
    private final CRISP parent_;
    private String CRISP_;

    private int frameXPos_ = 100;
    private int frameYPos_ = 100;
    
    
    // GUI elements
    private JButton calibrateButton_;
    private JButton curveButton_;
    private JSpinner gainSpinner_;
    private JSpinner LEDSpinner_;
    private JToggleButton lockButton_;
    private JSpinner naSpinner_;
    private JSpinner nrAvgsSpinner_;
    private JSpinner maxLockRangeSpinner_;
    private JButton resetOffsetButton_;
    private JButton idleButton_;
    private JButton updateButton_;
    private JLabel ledIntLabel;
    private JLabel gainMultiplierLabel;
    private JLabel nrAvgLabel;
    private JLabel naLabel;
    private JLabel versionLabel;
    private JLabel statusLabel_;
    private JLabel AGCLabel_;
    private JCheckBox pollCheckBox_;


    /** 
     * Creates new form CRISPFrame
     * TODO: Add an explanation whenever the D or N state occurs, suggest to
     *   increase gain and LED intensity
     * TODO: write help pages that describe use of the CRISP and this plugin
     * 
     * @param gui MM scriptInterface
     * @param parent Holds on to plugin code to tell it when this frame closes
     */
    public CRISPFrame(Studio gui, CRISP parent)  {
       gui_ = gui;
       core_ = gui.getCMMCore();
       parent_ = parent;
       CRISP_ = "";

       mmcorej.StrVector afs =
               core_.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
       boolean found = false;
       for (String af : afs) {
         try {
            // takes 
            if (core_.getDeviceLibrary(af).equals("ASITiger") &&
                  core_.hasProperty(af, "Description") &&
                  core_.getProperty(af, "Description").startsWith("ASI CRISP AutoFocus")) {
               CRISP_ = af;
               found = true;
               break;
            }
            if (core_.getDeviceLibrary(af).equals("ASIStage") &&
                  core_.hasProperty(af, "Description") &&
                  core_.getProperty(af, "Description").equals("ASI CRISP Autofocus adapter")) {
               found = true;
               CRISP_ = af;
               break;
            }
         } catch (Exception ex) {
            gui_.logs().logError(ex);
            }
       }

       if (!found) {
          gui_.logs().showError("This plugin needs the ASI CRISP Autofcous");
          throw new IllegalArgumentException("This plugin needs at least one camera");
       }

      initComponents();

      super.loadAndRestorePosition(frameXPos_, frameYPos_);

      updateValues(true);
    }

    private void updateValues(boolean allValues) {
        try {
            String val;
            if (allValues) {
                val = core_.getProperty(CRISP_, "LED Intensity");
                int intVal = Integer.parseInt(val);
                LEDSpinner_.getModel().setValue(intVal);

                val = core_.getProperty(CRISP_, "GainMultiplier");
                intVal = Integer.parseInt(val);
                gainSpinner_.getModel().setValue(intVal);

                val = core_.getProperty(CRISP_, "Number of Averages");
                intVal = Integer.parseInt(val);
                nrAvgsSpinner_.getModel().setValue(intVal);

                val = core_.getProperty(CRISP_, "Objective NA");
                float floatVal = Float.parseFloat(val);
                naSpinner_.getModel().setValue(floatVal);
                                
                val = core_.getProperty(CRISP_, "Max Lock Range(mm)");
                floatVal = Float.parseFloat(val);
                maxLockRangeSpinner_.getModel().setValue( (int) (floatVal * 1000.0) );
            }
 
         val = core_.getProperty(CRISP_, "CRISP State");
         statusLabel_.setText(val);
         
         val = core_.getProperty(CRISP_, "LogAmpAGC");
         AGCLabel_.setText(val);
         
       } catch (Exception ex) {
          gui_.logs().showError("Error reading values from CRISP");
       }

    }


    /**
    * Create a frame with a plot of the data given in XYSeries
    * @param title title of the ploy
    * @param data data series to be plotted
    * @param xTitle name of X-axis
    * @param yTitle name of Y-axis
    * @param xLocation position on the screen
    * @param yLocation position on the screen
    */
   public static void plotData(String title, XYSeries data, String xTitle,
           String yTitle, int xLocation, int yLocation) {
      // JFreeChart code
      XYSeriesCollection dataset = new XYSeriesCollection();
      dataset.addSeries(data);
      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
                xTitle, // x-axis Label
                yTitle, // y-axis Label
                dataset, // Dataset
                PlotOrientation.VERTICAL, // Plot Orientation
                false, // Show Legend
                true, // Use tooltips
                false // Configure chart to generate URLs?
            );
      XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(true);
      renderer.setSeriesPaint(0, Color.black);
      renderer.setSeriesFillPaint(0, Color.white);
      renderer.setSeriesLinesVisible(0, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      renderer.setUseFillPaint(true);

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
   }

    /** This method is called from within the constructor to
     * initialize the form.
     * History: This function was first created by the Netbeans GUI 
     * creator, then modified to use MigLayout as Layout manager
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lockButton_ = new javax.swing.JToggleButton();
        calibrateButton_ = new javax.swing.JButton();
        curveButton_ = new javax.swing.JButton();
        ledIntLabel = new javax.swing.JLabel();
        gainMultiplierLabel = new javax.swing.JLabel();
        nrAvgLabel = new javax.swing.JLabel();
        naLabel = new javax.swing.JLabel();
        LEDSpinner_ = new javax.swing.JSpinner();
        gainSpinner_ = new javax.swing.JSpinner();
        nrAvgsSpinner_ = new javax.swing.JSpinner();
        maxLockRangeSpinner_ = new JSpinner();
        JLabel maxLockRangeLabel = new JLabel();
        naSpinner_ = new javax.swing.JSpinner();
        updateButton_ = new javax.swing.JButton();
        idleButton_ = new javax.swing.JButton();
        resetOffsetButton_ = new javax.swing.JButton();
        versionLabel = new javax.swing.JLabel();
        statusLabel_ = new javax.swing.JLabel();
        AGCLabel_ = new JLabel();
        pollCheckBox_ = new JCheckBox(); 

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        final JFrame frame = this;
        this.addWindowListener(new WindowAdapter() {
          
           @Override
           public void windowClosing(WindowEvent e) {           
              parent_.tellFrameClosed();
              frame.setVisible(false);
              frame.dispose();
           }

        });
        setTitle("ASI CRISP Control");

        lockButton_.setText("Lock");
        lockButton_.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LockButton_ActionPerformed(evt);
            }
        });

        calibrateButton_.setText("Calibrate");
        calibrateButton_.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CalibrateButton_ActionPerformed(evt);
            }
        });

        curveButton_.setText("Focus Curve");
        curveButton_.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CurveButton_ActionPerformed(evt);
            }
        });

        ledIntLabel.setText("LED Int.");

        gainMultiplierLabel.setText("Gain");

        nrAvgLabel.setText("Nr of Avgs");

        naLabel.setText("Obj. NA");
        
        maxLockRangeLabel.setText("Range(" + "\u00B5" + "m)");

        LEDSpinner_.setModel(new javax.swing.SpinnerNumberModel(50, 0, 100, 1));
        LEDSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        LEDSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                LEDSpinner_StateChanged(evt);
            }
        });

        gainSpinner_.setModel(new javax.swing.SpinnerNumberModel(10, 0, 100, 1));
        gainSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        gainSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                GainSpinner_StateChanged(evt);
            }
        });

        nrAvgsSpinner_.setModel(new javax.swing.SpinnerNumberModel(1, 0, 10, 1));
        nrAvgsSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        nrAvgsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                NrAvgsSpinner_StateChanged(evt);
            }
        });

        naSpinner_.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.65f), Float.valueOf(0.0f), Float.valueOf(1.4f), Float.valueOf(0.05f)));
        naSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        naSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                NASpinner_StateChanged(evt);
            }
        });

        maxLockRangeSpinner_.setModel(new javax.swing.SpinnerNumberModel(50, 1, 10000, 1));
        maxLockRangeSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        maxLockRangeSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                int newRangeValue = (Integer) maxLockRangeSpinner_.getModel().getValue();
                try {
                    core_.setProperty(CRISP_, "Max Lock Range(mm)", (float) newRangeValue / 1000.0);
                } catch (Exception ex) {
                    gui_.logs().showError("Problem while setting LED intensity");
                }
            }
        });

        updateButton_.setText("Refresh");
        updateButton_.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateValues(true);
            }
        });

        idleButton_.setText("Idle");
        idleButton_.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                try {
                    core_.setProperty(CRISP_, "CRISP State", "Idle");
                } catch (Exception ex) {
                    gui_.logs().showError("Problem setting Idle State");
                }
            }
        });

        resetOffsetButton_.setText("Reset Offset");
        resetOffsetButton_.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ResetOffsetButton_ActionPerformed(evt);
            }
        });
        
        final Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                updateValues(false);
            }
        });
        pollCheckBox_.setText("Continuously update State and AGC");
        pollCheckBox_.addChangeListener(new ChangeListener(){
            @Override
            public void stateChanged(ChangeEvent ce) {
                if (pollCheckBox_.isSelected()) {
                    timer.restart();
                } else {
                    timer.stop();
                }
            }
        });

        versionLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        // TODO: get version from plugin itself
        versionLabel.setText("Version 1.0");

        this.setLayout(new MigLayout("", "30[]10[]25[]"));
        this.add(lockButton_, "span 3, split 2, grow, gapright 40");
        this.add(calibrateButton_, "span 3, split 2, grow, gapleft 40, wrap 20px");
        this.add(new JSeparator(), "span, grow, wrap 20px");
        this.add(new JLabel("CRISP State: "));
        this.add(statusLabel_, "span 2, grow, wrap");
        this.add(new JLabel("AGC: "));
        this.add(AGCLabel_, "span 2, grow, wrap");
        
        this.add(ledIntLabel);
        this.add(LEDSpinner_);
        this.add(updateButton_, "grow, wrap");
        this.add(gainMultiplierLabel);
        this.add(gainSpinner_);
        this.add(idleButton_, "grow, wrap");
        this.add(nrAvgLabel);
        this.add(nrAvgsSpinner_);
        this.add(resetOffsetButton_, "grow, wrap");
        this.add(naLabel);
        this.add(naSpinner_);
        this.add(curveButton_, "grow, wrap");
        this.add(maxLockRangeLabel);
        this.add(maxLockRangeSpinner_, "grow, wrap");
        this.add(pollCheckBox_, "span 3, grow, wrap");
        this.add(versionLabel, "span 3, align right, wrap");        

        pack();
    }

            

    private void LockButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LockButton_ActionPerformed
       if ("Lock".equals(evt.getActionCommand())) {
          lockButton_.setText("Unlock");
          // lock the device
          try {
             core_.enableContinuousFocus(true);
          } catch (Exception ex) {
             gui_.alerts().postAlert("CRISP", this.getClass(), "Failed to lock");
          }

          lockButton_.setSelected(true);
       } else if ("Unlock".equals(evt.getActionCommand())) {
          lockButton_.setText("Lock");
          // unlock the device
          try {
             core_.enableContinuousFocus(false);
          } catch (Exception ex) {
             gui_.alerts().postAlert("CRISP", this.getClass(), "Failed to unlock");
          }

          lockButton_.setSelected(false);
       }
    }//GEN-LAST:event_LockButton_ActionPerformed

    private void CalibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CalibrateButton_ActionPerformed
       try {
          core_.setProperty(CRISP_, "CRISP State", "loG_cal");
          // HACK: The controller appears to be unresponsive for ~1.5 s after  
          // setting the loG_cal state.  Either the user can set the serial port 
          // timeout to something higher than 2000ms, or we can wait here 
          // UPDATE: Newer firmware is no longer unresponsive, so this sleep can
          // go away.  On the other hand, it does not reallty hurt and this way
          // we stay backward compatible
          setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
          Thread.sleep(2000);
          setCursor(Cursor.getDefaultCursor());
          
          String state = "";
          int counter = 0;
          while (!state.equals("loG_cal") && counter < 50) {
            state = core_.getProperty(CRISP_, "CRISP State");
            Thread.sleep(100);
         }
         Double snr = new Double(core_.getProperty(CRISP_, "Signal Noise Ratio"));

         if (snr < 2.0)
            gui_.logs().showMessage("Signal Noise Ratio is smaller than 2.0.  " +
                    "Focus on your sample, increase LED intensity and try again.");

         core_.setProperty(CRISP_, "CRISP State", "Dither");

         String value = core_.getProperty(CRISP_, "Dither Error");
         
         final JLabel jl = new JLabel();
         final JLabel jlA = new JLabel();
         @SuppressWarnings("unused")
         final String msg1 = "Value:  ";
         final String msg2 = "Adjust the detector lateral adjustment screw until the value is > 100 or" +
                 "< -100 and stable.";
         jlA.setText(msg1);
         jl.setText(value);
         jl.setAlignmentX(JLabel.CENTER);
         
         Object[] msg = {msg1, jl, msg2};

         ActionListener al = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
               try {
                 jl.setText(core_.getProperty(CRISP_, "Dither Error"));
               } catch (Exception ex) {
                  gui_.logs().logError("Error while getting CRISP dither Error");
               }
            }
         };

         Timer timer = new Timer(100, al);
         timer.setInitialDelay(500);
         timer.start();

         JOptionPane.showMessageDialog(null, msg, "CRISP Calibration", JOptionPane.OK_OPTION);

         timer.stop();

         core_.setProperty(CRISP_, "CRISP State", "gain_Cal");

         counter = 0;
         while (!state.equals("Ready") && counter < 50) {
            state = core_.getProperty(CRISP_, "CRISP State");
            Thread.sleep(100);
            counter++;
         }
      } catch (Exception ex) {
         gui_.logs().showMessage("Calibration failed. Focus, make sure that the NA variable is set correctly and try again." + 
               "\nYou can also try increasing the serial port timeout in the HCW."); 
      }
    }

    private void LEDSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_LEDSpinner_StateChanged
       SpinnerModel numberModel = LEDSpinner_.getModel();

       int newLEDValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "LED Intensity", newLEDValue);
       } catch (Exception ex) {
          gui_.logs().showError("Problem while setting LED intensity");
       }
    }

    private void GainSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {
       SpinnerModel numberModel = gainSpinner_.getModel();

       int newGainValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "GainMultiplier", newGainValue);
       } catch (Exception ex) {
          gui_.logs().showError("Problem while setting LED intensity");
       }
    }

    private void NrAvgsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {
       SpinnerModel numberModel = nrAvgsSpinner_.getModel();

       int newNrAvgValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "Number of Averages", newNrAvgValue);
       } catch (Exception ex) {
          gui_.logs().showError("Problem while setting LED intensity");
       }
    }

    private void NASpinner_StateChanged(javax.swing.event.ChangeEvent evt) {
       SpinnerModel numberModel = naSpinner_.getModel();

       float newNAValue = (Float) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "Objective NA", newNAValue);
       } catch (Exception ex) {
          gui_.logs().showError("Problem while setting LED intensity");
       }
    }

    private void ResetOffsetButton_ActionPerformed(java.awt.event.ActionEvent evt) {
       try {
          core_.setProperty(CRISP_, "CRISP State", "Reset Focus Offset");
       } catch (Exception ex) {
          gui_.logs().showError("Problem resetting Focus Offset");
       }
    }

    private void CurveButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CurveButton_ActionPerformed
       try {
          core_.enableContinuousFocus(false);
          // TODO: emulate pressing Zero button


          setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

          core_.setProperty(CRISP_, "Obtain Focus Curve", "Do it");
          int index = 0;
          String vals = "";
          while (core_.hasProperty(CRISP_, "Focus Curve Data" + index)) {
            vals += core_.getProperty(CRISP_, "Focus Curve Data" + index);
            index++;
         }
          
          XYSeries data = new XYSeries("");
          String[] valLines = vals.split("\r\n");
          for (String valLine : valLines) {
             String[] tokens = valLine.split("\\s+");
             if (tokens.length == 4) { 
               data.add(Float.parseFloat(tokens[2]), Integer.parseInt(tokens[3]));
             }
          }

          String na = core_.getProperty(CRISP_, "Objective NA");
          plotData("CRISP Focus Curve, for NA=" + na,  data, "Z-position(microns)",
              "Focus Error Signal", 200, 200);
          
       } catch (Exception ex) {
          gui_.logs().showError("Problem acquiring focus curve");
       } finally {
          setCursor(Cursor.getDefaultCursor());
       }
    }


}
