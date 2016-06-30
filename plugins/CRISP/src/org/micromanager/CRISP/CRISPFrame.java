/*
 * CRISPFrame.java
 * 
 * Micro-Manager plugin for control of the ASI CRISP autofocus unit
 * 
 * Nico Stuurman (nico@cmp.ucsf.edu)
 * 
 * Copyright UCSF, 2011
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
import java.awt.geom.Ellipse2D;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SpinnerModel;
import javax.swing.Timer;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Micro-Manager plugin for control of the ASI CRISP autofocus
 * 
 * @author Nico Stuurman
 */
@SuppressWarnings("serial")
public class CRISPFrame extends javax.swing.JFrame {

    private final ScriptInterface gui_;
    private final CMMCore core_;
    private Preferences prefs_;
    private String CRISP_;

    private int frameXPos_ = 100;
    private int frameYPos_ = 100;
    private boolean tiger_ = false;

    private static final String FRAMEXPOS = "FRAMEXPOS";
    private static final String FRAMEYPOS = "FRAMEYPOS";


    /** Creates new form CRISPFrame */
    public CRISPFrame(ScriptInterface gui)  {
       gui_ = gui;
       core_ = gui.getMMCore();
       prefs_ = Preferences.userNodeForPackage(this.getClass());
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
               tiger_ = true;
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
            Logger.getLogger(CRISPFrame.class.getName()).log(Level.SEVERE, null, ex);
         }
       }

       if (!found) {
          gui_.showError("This plugin needs the ASI CRISP Autofcous");
          throw new IllegalArgumentException("This plugin needs at least one camera");
       }

      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);

      initComponents();

      setLocation(frameXPos_, frameYPos_);

      setBackground(gui_.getBackgroundColor());
      gui_.addMMBackgroundListener(this);

      updateValues();
    }

    private void updateValues() {
       try {
         String val;
         val = core_.getProperty(CRISP_, "LED Intensity");
         int intVal = Integer.parseInt(val);
         LEDSpinner_.getModel().setValue(intVal);

         val = core_.getProperty(CRISP_, "GainMultiplier");
         intVal = Integer.parseInt(val);
         GainSpinner_.getModel().setValue(intVal);

         val = core_.getProperty(CRISP_, "Number of Averages");
         intVal = Integer.parseInt(val);
         NrAvgsSpinner_.getModel().setValue(intVal);

         val = core_.getProperty(CRISP_, "Objective NA");
         float floatVal = Float.parseFloat(val);
         NASpinner_.getModel().setValue(floatVal);
         
       } catch (Exception ex) {
          ReportingUtils.showError("Error reading values from CRISP");
       }

    }


    /**
    * Create a frame with a plot of the data given in XYSeries
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
      renderer.setBaseShapesVisible(true);
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
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        LockButton_ = new javax.swing.JToggleButton();
        CalibrateButton_ = new javax.swing.JButton();
        CurveButton_ = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        LEDSpinner_ = new javax.swing.JSpinner();
        GainSpinner_ = new javax.swing.JSpinner();
        NrAvgsSpinner_ = new javax.swing.JSpinner();
        NASpinner_ = new javax.swing.JSpinner();
        UpdateButton_ = new javax.swing.JButton();
        SaveButton_ = new javax.swing.JButton();
        ResetOffsetButton_ = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("ASI CRISP Control");

        LockButton_.setText("Lock");
        LockButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LockButton_ActionPerformed(evt);
            }
        });

        CalibrateButton_.setText("Calibrate");
        CalibrateButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CalibrateButton_ActionPerformed(evt);
            }
        });

        CurveButton_.setText("Focus Curve");
        CurveButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CurveButton_ActionPerformed(evt);
            }
        });

        jLabel1.setText("LED Int.");

        jLabel2.setText("Gain Multiplier");

        jLabel3.setText("Nr of Avgs");

        jLabel4.setText("NA");

        LEDSpinner_.setModel(new javax.swing.SpinnerNumberModel(50, 0, 100, 1));
        LEDSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        LEDSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                LEDSpinner_StateChanged(evt);
            }
        });

        GainSpinner_.setModel(new javax.swing.SpinnerNumberModel(10, 0, 100, 1));
        GainSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        GainSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                GainSpinner_StateChanged(evt);
            }
        });

        NrAvgsSpinner_.setModel(new javax.swing.SpinnerNumberModel(1, 0, 10, 1));
        NrAvgsSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        NrAvgsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                NrAvgsSpinner_StateChanged(evt);
            }
        });

        NASpinner_.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.65f), Float.valueOf(0.0f), Float.valueOf(1.4f), Float.valueOf(0.05f)));
        NASpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        NASpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                NASpinner_StateChanged(evt);
            }
        });

        UpdateButton_.setText("Refresh");
        UpdateButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateButton_ActionPerformed(evt);
            }
        });

        SaveButton_.setText("Save Calibration");
        SaveButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveButton_ActionPerformed(evt);
            }
        });

        ResetOffsetButton_.setText("Reset Offset");
        ResetOffsetButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ResetOffsetButton_ActionPerformed(evt);
            }
        });

        jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        jLabel5.setText("Version 1.0");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 289, Short.MAX_VALUE)
                                .addGap(6, 6, 6))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(jLabel4)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 83, Short.MAX_VALUE)
                                        .addComponent(NASpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(jLabel3)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 35, Short.MAX_VALUE)
                                        .addComponent(NrAvgsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(jLabel2)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(GainSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(jLabel1)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 53, Short.MAX_VALUE)
                                        .addComponent(LEDSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGap(16, 16, 16)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(UpdateButton_, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                                    .addComponent(ResetOffsetButton_, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                                    .addComponent(CurveButton_, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                                    .addComponent(SaveButton_, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(18, 18, 18)
                                .addComponent(LockButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 82, Short.MAX_VALUE)
                                .addComponent(CalibrateButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(19, 19, 19)))
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addGap(31, 31, 31))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CalibrateButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(LockButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1)
                        .addComponent(UpdateButton_))
                    .addComponent(LEDSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE)
                    .addComponent(GainSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SaveButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE)
                    .addComponent(NrAvgsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ResetOffsetButton_))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, 28, Short.MAX_VALUE)
                    .addComponent(NASpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(CurveButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel5)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void LockButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LockButton_ActionPerformed
       if ("Lock".equals(evt.getActionCommand())) {
          LockButton_.setText("Unlock");
          // lock the device
          try {
             core_.enableContinuousFocus(true);
          } catch (Exception ex) {
             ReportingUtils.displayNonBlockingMessage("Failed to lock");
          }

          LockButton_.setSelected(true);
       } else if ("Unlock".equals(evt.getActionCommand())) {
          LockButton_.setText("Lock");
          // unlock the device
          try {
             core_.enableContinuousFocus(false);
          } catch (Exception ex) {
             ReportingUtils.displayNonBlockingMessage("Failed to lock");
          }

          LockButton_.setSelected(false);
       }
    }//GEN-LAST:event_LockButton_ActionPerformed

    private void CalibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CalibrateButton_ActionPerformed
      try {
         core_.setProperty(CRISP_, "CRISP State", "loG_cal");

         String state = "";
         int counter = 0;
         while (!state.equals("loG_cal") && counter < 50) {
            state = core_.getProperty(CRISP_, "CRISP State");
            Thread.sleep(100);
         }
         Double snr = new Double(core_.getProperty(CRISP_, "Signal Noise Ratio"));

         if (snr < 2.0)
            ReportingUtils.showMessage("Signal Noise Ratio is smaller than 2.0.  " +
                    "Focus on your sample, increase LED intensity and try again.");

         core_.setProperty(CRISP_, "CRISP State", "Dither");

         String value = core_.getProperty(CRISP_, "Dither Error");
         
         final JLabel jl = new JLabel();
         final JLabel jlA = new JLabel();
         @SuppressWarnings("unused")
         final JLabel jlB = new JLabel();
         final String msg1 = "Value:  ";
         final String msg2 = "Adjust the detector lateral adjustment screw until the value is > 100 or" +
                 "< -100 and stable.";
         jlA.setText(msg1);
         jl.setText(value);
         jl.setAlignmentX(JLabel.CENTER);
         
         Object[] msg = {msg1, jl, msg2};

         ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
               try {
                 jl.setText(core_.getProperty(CRISP_, "Dither Error"));
               } catch (Exception ex) {
                  ReportingUtils.logError("Error while getting CRISP dither Error");
               }
            }
         };

         Timer timer = new Timer(100, al);
         timer.setInitialDelay(500);
         timer.start();

         /*JOptionPane optionPane = new JOptionPane(new JLabel("Hello World",JLabel.CENTER));
         JDialog dialog = optionPane.createDialog("");
    dialog.setModal(true);
    dialog.setVisible(true); */

         JOptionPane.showMessageDialog(null, msg, "CRISP Calibration", JOptionPane.OK_OPTION);

         timer.stop();

         core_.setProperty(CRISP_, "CRISP State", "gain_Cal");

         counter = 0;
         while (!state.equals("Ready") && counter < 50) {
            state = core_.getProperty(CRISP_, "CRISP State");
            Thread.sleep(100);
         }
         // ReportingUtils.showMessage("Calibration failed. Focus, make sure that the NA variable is set correctly and try again.");

      } catch (Exception ex) {
         ReportingUtils.showMessage("Calibration failed. Focus, make sure that the NA variable is set correctly and try again.");
      }
    }//GEN-LAST:event_CalibrateButton_ActionPerformed

    private void LEDSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_LEDSpinner_StateChanged
       SpinnerModel numberModel = LEDSpinner_.getModel();

       int newLEDValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "LED Intensity", newLEDValue);
       } catch (Exception ex) {
          ReportingUtils.showError("Problem while setting LED intensity");
       }
    }//GEN-LAST:event_LEDSpinner_StateChanged

    private void UpdateButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UpdateButton_ActionPerformed
       updateValues();
    }//GEN-LAST:event_UpdateButton_ActionPerformed

    private void GainSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_GainSpinner_StateChanged
       SpinnerModel numberModel = GainSpinner_.getModel();

       int newGainValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "GainMultiplier", newGainValue);
       } catch (Exception ex) {
          ReportingUtils.showError("Problem while setting LED intensity");
       }
    }//GEN-LAST:event_GainSpinner_StateChanged

    private void NrAvgsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_NrAvgsSpinner_StateChanged
       SpinnerModel numberModel = NrAvgsSpinner_.getModel();

       int newNrAvgValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "Number of Averages", newNrAvgValue);
       } catch (Exception ex) {
          ReportingUtils.showError("Problem while setting LED intensity");
       }
    }//GEN-LAST:event_NrAvgsSpinner_StateChanged

    private void NASpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_NASpinner_StateChanged
       SpinnerModel numberModel = NASpinner_.getModel();

       float newNAValue = (Float) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "Objective NA", newNAValue);
       } catch (Exception ex) {
          ReportingUtils.showError("Problem while setting LED intensity");
       }
    }//GEN-LAST:event_NASpinner_StateChanged

    private void ResetOffsetButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ResetOffsetButton_ActionPerformed
       try {
          core_.setProperty(CRISP_, "CRISP State", "Reset Focus Offset");
       } catch (Exception ex) {
          ReportingUtils.showError("Problem resetting Focus Offset");
       }
    }//GEN-LAST:event_ResetOffsetButton_ActionPerformed

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
          for (int i=0; i < valLines.length; i++) {
             String[] tokens = valLines[i].split("\\s+");
             if (tokens.length == 4) {
               data.add(Float.parseFloat(tokens[2]), Integer.parseInt(tokens[3]));
             }
          }

          String na = core_.getProperty(CRISP_, "Objective NA");
          plotData("CRISP Focus Curve, for NA=" + na,  data, "Z-position(microns)",
              "Focus Error Signal", 200, 200);
          
       } catch (Exception ex) {
          ReportingUtils.showError("Problem acquiring focus curve");
       } finally {
          setCursor(Cursor.getDefaultCursor());
       }
    }//GEN-LAST:event_CurveButton_ActionPerformed

    private void SaveButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveButton_ActionPerformed
       try {
          core_.setProperty(CRISP_, "CRISP State", "Save to Controller");
       } catch (Exception ex) {
          ReportingUtils.showError("Problem acquiring focus curve");
       }
    }//GEN-LAST:event_SaveButton_ActionPerformed

  public void safePrefs() {
      prefs_.putInt(FRAMEXPOS, this.getX());
      prefs_.putInt(FRAMEYPOS, this.getY());
   }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton CalibrateButton_;
    private javax.swing.JButton CurveButton_;
    private javax.swing.JSpinner GainSpinner_;
    private javax.swing.JSpinner LEDSpinner_;
    private javax.swing.JToggleButton LockButton_;
    private javax.swing.JSpinner NASpinner_;
    private javax.swing.JSpinner NrAvgsSpinner_;
    private javax.swing.JButton ResetOffsetButton_;
    private javax.swing.JButton SaveButton_;
    private javax.swing.JButton UpdateButton_;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JSeparator jSeparator1;
    // End of variables declaration//GEN-END:variables

}
