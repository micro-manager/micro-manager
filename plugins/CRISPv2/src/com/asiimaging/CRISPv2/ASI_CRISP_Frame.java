/*
* ASI_CRISP_V2.java
* Micro Manager Plugin for ASIs CRISP Autofocus
* Based on Nico Stuurman's original ASI CRISP Control plugin.
* Modified by Vikram Kopuri, ASI
* Last Updated 9/12/2014
* Changelog
* 2.0
* First Draft

 */
package com.asiimaging.CRISPv2;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.SpinnerModel;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

import org.micromanager.ScriptInterface;
import org.micromanager.internal.utils.ReportingUtils;
//import java.util.Timer;
/**
 *
 * @author Vik
 */
public class ASI_CRISP_Frame extends javax.swing.JFrame {

    private final ScriptInterface gui_;
    private final CMMCore core_;
    private Preferences prefs_;
    private String CRISP_;
    private int timer_poll=200;//200 millisec
    private int frameXPos_ = 100;
    private int frameYPos_ = 100;
    javax.swing.Timer myTimer ;//= new javax.swing.Timer(timer_poll, taskPerformer);
    private static final String FRAMEXPOS = "FRAMEXPOS";
    private static final String FRAMEYPOS = "FRAMEYPOS";
    /**
     * Creates new form ASI_CRISP_Frame
     */
    public ASI_CRISP_Frame(ScriptInterface gui) {
       initComponents();
        
       gui_=gui;
       core_=gui.getMMCore();
       prefs_ = Preferences.userNodeForPackage(this.getClass());
       CRISP_ = "";
            
       jLabel1.setText("ASI CRISP Control v2.0");
       mmcorej.StrVector afs =
               core_.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
       boolean found = false;
       for (String af : afs) {
         try {
            if (core_.hasProperty(af, "Description")) {
               if (core_.getProperty(af, "Description").equals("ASI CRISP Autofocus adapter") ||
                     core_.getProperty(af, "Description").startsWith("ASI CRISP AutoFocus")) {  // this line is for Tiger
                  found = true;
                  CRISP_ = af;
                  jLabel6.setText(af);
                  myTimer = new javax.swing.Timer(timer_poll, taskPerformer);
                  //starting timers and such
                  myTimer.start();
                  core_.setProperty(CRISP_, "RefreshPropertyValues", "Yes");
               }
            }
         } catch (Exception ex) {
            Logger.getLogger(ASI_CRISP_Frame.class.getName()).log(Level.SEVERE, null, ex);
         }
       }

       if (!found) {
          gui_.showError("This plugin needs the ASI CRISP Autofcous");
          throw new IllegalArgumentException("This plugin needs at least one camera");
       }

      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);



      setLocation(frameXPos_, frameYPos_);

      setBackground(gui_.getBackgroundColor());
      gui_.addMMBackgroundListener(this);

      updateValues();
    }

    private void updateValues() {
       
         String val;
         int intVal;
         try {
                val = core_.getProperty(CRISP_, "LED Intensity");
                
            } catch (Exception ex) 
            {
                try
                {// sometimes this property is also called led intensity with a %
                val = core_.getProperty(CRISP_, "LED Intensity(%)");
                }
                catch (Exception ex1) 
                {
                ReportingUtils.showError("Error reading LED Intensity from CRISP");
                val="0";
                }
            }
                intVal = Integer.parseInt(val);
                LEDSpinner_.getModel().setValue(intVal);
         
          try {
                val = core_.getProperty(CRISP_, "GainMultiplier");
                
                } catch (Exception ex) 
                {
                    try
                    {
                    val = core_.getProperty(CRISP_, "LoopGainMultiplier");
                    }
                    catch (Exception ex1)
                    {
                    ReportingUtils.showError("Error reading Gain Multiplier from CRISP");
                    val="0";
                    }
                }
                intVal = Integer.parseInt(val);
                GainSpinner_.getModel().setValue(intVal);
          
          try {
               val = core_.getProperty(CRISP_, "Number of Averages");
               intVal = Integer.parseInt(val);
               NrAvgsSpinner_.getModel().setValue(intVal);
            } catch (Exception ex) 
            {
          ReportingUtils.showError("Error reading No of Avg from CRISP");
             }
          try {
               val = core_.getProperty(CRISP_, "Objective NA");
               float floatVal = Float.parseFloat(val);
               NASpinner_.getModel().setValue(floatVal);
         } catch (Exception ex) 
         {
          ReportingUtils.showError("Error reading Objective NA from CRISP");
         }
       
update_status();
    }
    
    private void update_status()
    {
    try{
        label_crisp_error.setText(core_.getProperty(CRISP_, "Dither Error"));
    }
    catch(Exception ex) 
            {
                label_crisp_error.setText("read error");            
                // getting errors, best to stop
                //myTimer.stop();
            }
    
    try{
        label_crisp_state.setText(core_.getProperty(CRISP_, "CRISP State"));
    }
    catch(Exception ex) 
            {
            label_crisp_state.setText("read error");
                // getting errors , best to stop
                //myTimer.stop();
            }
    
    try{
        label_snr.setText(core_.getProperty(CRISP_, "Signal Noise Ratio"));
    }
    catch(Exception ex) 
            {
                try
                {
                label_snr.setText(core_.getProperty(CRISP_, "Signal to Noise Ratio"));
                }
                catch(Exception ex1)
                {
                label_snr.setText("read error");
                }
                // getting errors , best to stop
                //myTimer.stop();
            }
    
    }
    ActionListener taskPerformer = new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
          update_status();
      }
            };
    
      public void safePrefs() {
      prefs_.putInt(FRAMEXPOS, this.getX());
      prefs_.putInt(FRAMEYPOS, this.getY());
   }
    
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        LEDSpinner_ = new javax.swing.JSpinner();
        GainSpinner_ = new javax.swing.JSpinner();
        NrAvgsSpinner_ = new javax.swing.JSpinner();
        NASpinner_ = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        btn_lock = new javax.swing.JButton();
        btn_unlock = new javax.swing.JButton();
        btn_idle = new javax.swing.JButton();
        btn_logcal = new javax.swing.JButton();
        btn_bither = new javax.swing.JButton();
        btn_setgain = new javax.swing.JButton();
        cb_polling = new javax.swing.JCheckBox();
        btn_offset = new javax.swing.JButton();
        btn_save = new javax.swing.JButton();
        panel_poll_data = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        label_crisp_state = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        label_crisp_error = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        label_snr = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("ASI CRISP Control v2");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel1.setText("ASI CRISP Interface v2");

        LEDSpinner_.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(50), null, Integer.valueOf(100), Integer.valueOf(1)));
        LEDSpinner_.setMinimumSize(new java.awt.Dimension(50, 20));
        LEDSpinner_.setPreferredSize(new java.awt.Dimension(50, 20));
        LEDSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                LEDSpinner_StateChanged(evt);
            }
        });

        GainSpinner_.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(10), null, Integer.valueOf(100), Integer.valueOf(1)));
        GainSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                GainSpinner_StateChanged(evt);
            }
        });

        NrAvgsSpinner_.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), null, Integer.valueOf(10), Integer.valueOf(1)));
        NrAvgsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                NrAvgsSpinner_StateChanged(evt);
            }
        });

        NASpinner_.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.65f), null, Float.valueOf(1.4f), Float.valueOf(0.05f)));
        NASpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                NASpinner_StateChanged(evt);
            }
        });

        jLabel2.setText("LED Intensity");

        jLabel3.setText("Gain");

        jLabel4.setText("Avg");

        jLabel5.setText("Obj NA");

        jLabel6.setText("Axis");

        btn_lock.setText("Lock");
        btn_lock.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_lockMouseClicked(evt);
            }
        });
        btn_lock.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_lockActionPerformed(evt);
            }
        });

        btn_unlock.setText("Unlock");
        btn_unlock.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_unlockMouseClicked(evt);
            }
        });

        btn_idle.setText("1) IDLE");
        btn_idle.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_idleMouseClicked(evt);
            }
        });

        btn_logcal.setText("2) LOG CAL");
        btn_logcal.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_logcalMouseClicked(evt);
            }
        });

        btn_bither.setText("3) DITHER");
        btn_bither.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_bitherMouseClicked(evt);
            }
        });

        btn_setgain.setText("4) SET GAIN");
        btn_setgain.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_setgainMouseClicked(evt);
            }
        });

        cb_polling.setSelected(true);
        cb_polling.setText("Polling");
        cb_polling.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                cb_pollingStateChanged(evt);
            }
        });

        btn_offset.setText("Reset Offsets");
        btn_offset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_offsetMouseClicked(evt);
            }
        });

        btn_save.setText("Save");
        btn_save.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btn_saveMouseClicked(evt);
            }
        });

        jLabel8.setText("CRISP State:");

        label_crisp_state.setText("State");

        jLabel10.setText("Error #");

        label_crisp_error.setText("###");

        jLabel7.setText("SNR:");

        label_snr.setText("SNR");

        javax.swing.GroupLayout panel_poll_dataLayout = new javax.swing.GroupLayout(panel_poll_data);
        panel_poll_data.setLayout(panel_poll_dataLayout);
        panel_poll_dataLayout.setHorizontalGroup(
            panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_poll_dataLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel8)
                    .addComponent(jLabel10)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(label_crisp_state)
                    .addComponent(label_crisp_error)
                    .addComponent(label_snr))
                .addGap(43, 43, 43))
        );
        panel_poll_dataLayout.setVerticalGroup(
            panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_poll_dataLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(label_crisp_state))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(label_crisp_error))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_poll_dataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(label_snr))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(40, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(panel_poll_data, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cb_polling)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(jLabel6)
                                .addGroup(layout.createSequentialGroup()
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel2)
                                        .addComponent(jLabel3))
                                    .addGap(18, 18, 18)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(GainSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(LEDSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(NrAvgsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(NASpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 26, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(btn_idle, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(btn_logcal, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(btn_bither, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(btn_offset, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(btn_lock, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btn_unlock, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btn_save, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btn_setgain, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(LEDSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel2)
                            .addComponent(btn_idle))
                        .addGap(10, 10, 10)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(NrAvgsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel4)
                            .addComponent(btn_bither))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel5))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(29, 29, 29)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btn_logcal)
                            .addComponent(GainSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(35, 35, 35)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btn_setgain)
                            .addComponent(NASpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn_offset)
                    .addComponent(cb_polling))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(panel_poll_data, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btn_lock)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_unlock)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_save)
                        .addGap(0, 6, Short.MAX_VALUE))))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void LEDSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_LEDSpinner_StateChanged
       SpinnerModel numberModel = LEDSpinner_.getModel();

       int newLEDValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "LED Intensity", newLEDValue);
       } catch (Exception ex) {
          try
          {
           core_.setProperty(CRISP_, "LED Intensity(%)", newLEDValue);
          }
          catch (Exception ex1)
          {
           ReportingUtils.showError("Problem while setting LED intensity");
          }
       }
    }//GEN-LAST:event_LEDSpinner_StateChanged

    private void GainSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_GainSpinner_StateChanged
        SpinnerModel numberModel = GainSpinner_.getModel();

       int newGainValue = (Integer) numberModel.getValue();
       try {
          core_.setProperty(CRISP_, "GainMultiplier", newGainValue);
       } catch (Exception ex) {
          try
          {
           core_.setProperty(CRISP_, "LoopGainMultiplier", newGainValue);
          }
          catch (Exception ex1)
          {
           ReportingUtils.showError("Problem while setting Gain Multiplier");
          }
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

    private void btn_idleMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_idleMouseClicked
       try{
        core_.setProperty(CRISP_, "CRISP State", "Idle");
       }
       catch (Exception ex) {
          ReportingUtils.showError("Problem while Dithering");
       }
    }//GEN-LAST:event_btn_idleMouseClicked

    private void btn_logcalMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_logcalMouseClicked
       try{
        core_.setProperty(CRISP_, "CRISP State", "loG_cal");
       }
       catch (Exception ex) {
          ReportingUtils.showError("Problem while Log Cal");
       }
    }//GEN-LAST:event_btn_logcalMouseClicked

    private void btn_bitherMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_bitherMouseClicked
        
               try{
        core_.setProperty(CRISP_, "CRISP State", "Dither");
       }
       catch (Exception ex) {
          ReportingUtils.showError("Problem while Dithering");
       }
    }//GEN-LAST:event_btn_bitherMouseClicked

    private void btn_setgainMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_setgainMouseClicked
        try{
        core_.setProperty(CRISP_, "CRISP State", "gain_Cal");
       }
       catch (Exception ex) {
          ReportingUtils.showError("Problem while Dithering");
       }
    }//GEN-LAST:event_btn_setgainMouseClicked

    private void btn_lockMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_lockMouseClicked
        try {
             core_.enableContinuousFocus(true);
          } catch (Exception ex) {
             ReportingUtils.displayNonBlockingMessage("Failed to lock");
          }
    }//GEN-LAST:event_btn_lockMouseClicked

    private void btn_unlockMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_unlockMouseClicked
        try {
             core_.enableContinuousFocus(false);
          } catch (Exception ex) {
             ReportingUtils.displayNonBlockingMessage("Failed to lock");
          }
    }//GEN-LAST:event_btn_unlockMouseClicked

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        try {
        core_.setProperty(CRISP_, "RefreshPropertyValues", "No");
        }
        catch(Exception ex)
                {
                }
        myTimer.stop();
    }//GEN-LAST:event_formWindowClosing

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
         try {
        //core_.setProperty(CRISP_, "RefreshPropertyValues", "Yes");
        }
        catch(Exception ex)
                {
                }
        //myTimer.start();
    }//GEN-LAST:event_formWindowOpened

    private void cb_pollingStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_cb_pollingStateChanged
       if(cb_polling.isSelected() == true)
       {
       myTimer.start();
       panel_poll_data.setEnabled(true);
       try {
             core_.setProperty(CRISP_, "RefreshPropertyValues", "Yes");
             }
        catch(Exception ex)
                {
                }
        
       }
       else
        {
         panel_poll_data.setEnabled(false);
        try {
        core_.setProperty(CRISP_, "RefreshPropertyValues", "No");
        }
        catch(Exception ex)
                {
                }
        myTimer.stop();
        }
    }//GEN-LAST:event_cb_pollingStateChanged

    private void btn_lockActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_lockActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btn_lockActionPerformed

    private void btn_offsetMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_offsetMouseClicked
        try {
          core_.setProperty(CRISP_, "CRISP State", "Reset Focus Offset");
       } catch (Exception ex) {
          ReportingUtils.showError("Problem resetting Focus Offset");
       }
    }//GEN-LAST:event_btn_offsetMouseClicked

    private void btn_saveMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btn_saveMouseClicked
        try {
          core_.setProperty(CRISP_, "CRISP State", "Save to Controller");
       } catch (Exception ex) {
          ReportingUtils.showError("Problem acquiring focus curve");
       }
    }//GEN-LAST:event_btn_saveMouseClicked

    /**
     * @param args the command line arguments
     */
//    public static void main(String args[]) {
//        /* Set the Nimbus look and feel */
//        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
//        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
//         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
//         */
//        try {
//            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                if ("Nimbus".equals(info.getName())) {
//                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
//                    break;
//                }
//            }
//        } catch (ClassNotFoundException ex) {
//            java.util.logging.Logger.getLogger(ASI_CRISP_Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (InstantiationException ex) {
//            java.util.logging.Logger.getLogger(ASI_CRISP_Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (IllegalAccessException ex) {
//            java.util.logging.Logger.getLogger(ASI_CRISP_Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
//            java.util.logging.Logger.getLogger(ASI_CRISP_Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        }
//        //</editor-fold>
//
//        /* Create and display the form */
//        java.awt.EventQueue.invokeLater(new Runnable() {
//            public void run() {
//                new ASI_CRISP_Frame().setVisible(true);
//            }
//        });
//    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSpinner GainSpinner_;
    private javax.swing.JSpinner LEDSpinner_;
    private javax.swing.JSpinner NASpinner_;
    private javax.swing.JSpinner NrAvgsSpinner_;
    private javax.swing.JButton btn_bither;
    private javax.swing.JButton btn_idle;
    private javax.swing.JButton btn_lock;
    private javax.swing.JButton btn_logcal;
    private javax.swing.JButton btn_offset;
    private javax.swing.JButton btn_save;
    private javax.swing.JButton btn_setgain;
    private javax.swing.JButton btn_unlock;
    private javax.swing.JCheckBox cb_polling;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel label_crisp_error;
    private javax.swing.JLabel label_crisp_state;
    private javax.swing.JLabel label_snr;
    private javax.swing.JPanel panel_poll_data;
    // End of variables declaration//GEN-END:variables
}
