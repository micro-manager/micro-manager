/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.multichannelshading;

import ij.ImagePlus;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import mmcorej.StrVector;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.FileDialogs;

/**
 *
 * @author kthorn
 */
public class MultiChannelShadingForm extends javax.swing.JFrame {
   private final ScriptInterface gui_;
   private final mmcorej.CMMCore mmc_;
   private final Preferences prefs_;

   private int frameXPos_ = 100;
   private int frameYPos_ = 100;
   
   private final BFProcessor processor_;

   private static final String FRAMEXPOS = "FRAMEXPOS";
   private static final String FRAMEYPOS = "FRAMEYPOS";
   private static final String DARKFIELDFILENAME = "BackgroundFileName";
   private static final String CHANNELGROUP = "ChannelGroup";
   private static final String USECHECKBOX = "UseCheckBox";
   private static final String FLATFIELDNORMALIZE1 = "FLATFIELDNORMALIZE1";
   private static final String FLATFIELDNORMALIZE2 = "FLATFIELDNORMALIZE2";
   private static final String FLATFIELDNORMALIZE3 = "FLATFIELDNORMALIZE3";
   private static final String FLATFIELDNORMALIZE4 = "FLATFIELDNORMALIZE4";
   private static final String FLATFIELDNORMALIZE5 = "FLATFIELDNORMALIZE5";
   private static final String EMPTY_FILENAME_INDICATOR = "None";
   private final String[] IMAGESUFFIXES = {"tif", "tiff", "jpg", "png"};
   private String flatfieldFileName_;
   private String backgroundFileName_;
   private String groupName_;
   private DefaultComboBoxModel configNameList;
   private String item_;
   
    /**
     * Creates new form MultiChannelShadingForm
    * @param processor
     * @param gui
     */
   public MultiChannelShadingForm(BFProcessor processor, ScriptInterface gui) {
      processor_ = processor;
      gui_ = gui;
      mmc_ = gui_.getMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      initComponents();
      setBackground(gui_.getBackgroundColor());   
       
      // Read preferences and apply to the dialog
      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);
      setLocation(frameXPos_, frameYPos_);
      
      useCheckBox_.setSelected(prefs_.getBoolean(USECHECKBOX, true));
      
      //get preferences for normalization and set checkboxes
      flatFieldNormalize1_.setSelected(prefs_.getBoolean(FLATFIELDNORMALIZE1, true));
      flatFieldNormalize2_.setSelected(prefs_.getBoolean(FLATFIELDNORMALIZE2, true));
      flatFieldNormalize3_.setSelected(prefs_.getBoolean(FLATFIELDNORMALIZE3, true));
      flatFieldNormalize4_.setSelected(prefs_.getBoolean(FLATFIELDNORMALIZE4, true));
      flatFieldNormalize5_.setSelected(prefs_.getBoolean(FLATFIELDNORMALIZE5, true));
      processor_.setFlatFieldNormalize(0, flatFieldNormalize1_.isSelected());
      processor_.setFlatFieldNormalize(1, flatFieldNormalize2_.isSelected());
      processor_.setFlatFieldNormalize(2, flatFieldNormalize3_.isSelected());
      processor_.setFlatFieldNormalize(3, flatFieldNormalize4_.isSelected());
      processor_.setFlatFieldNormalize(4, flatFieldNormalize5_.isSelected());
      String flatfieldFile1 = prefs_.get(groupName_ + "file1", "None");
      flatFieldTextField1_.setText(flatfieldFile1);
      
      //populate darkFieldName from preferences and process it.
      String darkFieldFileName = prefs_.get(DARKFIELDFILENAME, "");   
      darkFieldTextField_.setText("".equals(darkFieldFileName) ?
            EMPTY_FILENAME_INDICATOR : darkFieldFileName);
      processBackgroundImage(darkFieldTextField_.getText());
      
      //populate group ComboBox
      String[] channelGroups = mmc_.getAvailableConfigGroups().toArray();
      groupComboBox.setModel(new javax.swing.DefaultComboBoxModel(
              channelGroups));
      groupName_ = prefs_.get(CHANNELGROUP, "");
      groupComboBox.setSelectedItem(groupName_);
      groupName_ = (String) groupComboBox.getSelectedItem();
      processor_.setChannelGroup(groupName_);
      populateFlatFieldComboBoxes();

      processor_.setEnabled(useCheckBox_.isSelected());
   }

   void updateProcessorEnabled(boolean enabled) {
      useCheckBox_.setSelected(enabled);
   }
   
   private void processFlatFieldImage(int channel, String fileName) {
      if (EMPTY_FILENAME_INDICATOR.equals(fileName)) {
         fileName = "";
      }

      // If we have a filename, try to open it and set the flatfield image.
      // If not, set the flatfield image to null (no flatfielding for this
      // channel).
      // TODO User should be made aware if file is missing!
      ImagePlus ip = null;
      if (fileName != null && !fileName.isEmpty()) {
         ij.io.Opener opener = new ij.io.Opener();
         ip = opener.openImage(fileName);
         // prefs_.put(groupName_ + "file1", fileName);
      }
      processor_.setFlatField(channel, ip);
   }

   private void processBackgroundImage(String fileName) {
      if (EMPTY_FILENAME_INDICATOR.equals(fileName)) {
         fileName = "";
      }

      // If we have a filename, trt to open it and set the background image.
      // If not, set the background image to null (no correction).
      // TODO User should be made aware if file is missing!
      ImagePlus ip = null;
      if (fileName != null && !fileName.isEmpty()) {
         ij.io.Opener opener = new ij.io.Opener();
         ip = opener.openImage(fileName);
      }
      processor_.setBackground(ip);

      backgroundFileName_ = fileName;
      prefs_.put(DARKFIELDFILENAME, backgroundFileName_);
   }
    
   private void populateFlatFieldComboBoxes() {
        StrVector configNames;
        groupName_ = (String) groupComboBox.getSelectedItem();
        if (groupName_ == null ) {
            configNames = new StrVector();
        } else {
            configNames = mmc_.getAvailableConfigs(groupName_);
        }
        configNames.add("None");
        flatFieldComboBox1.setModel(new javax.swing.DefaultComboBoxModel(
                configNames.toArray()));
        flatFieldComboBox1.setSelectedItem(prefs_.get(groupName_ + "1", ""));
        flatFieldComboBox2.setModel(new javax.swing.DefaultComboBoxModel(configNames.toArray()));
        flatFieldComboBox2.setSelectedIndex(flatFieldComboBox2.getItemCount()-1);
        flatFieldComboBox3.setModel(new javax.swing.DefaultComboBoxModel(configNames.toArray()));
        flatFieldComboBox3.setSelectedIndex(flatFieldComboBox3.getItemCount()-1);
        flatFieldComboBox4.setModel(new javax.swing.DefaultComboBoxModel(configNames.toArray()));
        flatFieldComboBox4.setSelectedIndex(flatFieldComboBox4.getItemCount()-1);
        flatFieldComboBox5.setModel(new javax.swing.DefaultComboBoxModel(configNames.toArray()));       
        flatFieldComboBox5.setSelectedIndex(flatFieldComboBox5.getItemCount()-1);
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        flatFieldComboBox1 = new javax.swing.JComboBox();
        flatFieldTextField1_ = new javax.swing.JTextField();
        flatFieldButton1 = new javax.swing.JButton();
        groupComboBox = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        darkFieldButton_ = new javax.swing.JButton();
        darkFieldTextField_ = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        flatFieldTextField2_ = new javax.swing.JTextField();
        flatFieldButton2 = new javax.swing.JButton();
        flatFieldComboBox2 = new javax.swing.JComboBox();
        flatFieldTextField3_ = new javax.swing.JTextField();
        flatFieldButton3 = new javax.swing.JButton();
        flatFieldComboBox3 = new javax.swing.JComboBox();
        flatFieldTextField4_ = new javax.swing.JTextField();
        flatFieldButton4 = new javax.swing.JButton();
        flatFieldComboBox4 = new javax.swing.JComboBox();
        flatFieldTextField5_ = new javax.swing.JTextField();
        flatFieldButton5 = new javax.swing.JButton();
        flatFieldComboBox5 = new javax.swing.JComboBox();
        useCheckBox_ = new javax.swing.JCheckBox();
        flatFieldNormalize1_ = new javax.swing.JCheckBox();
        flatFieldNormalize2_ = new javax.swing.JCheckBox();
        flatFieldNormalize4_ = new javax.swing.JCheckBox();
        flatFieldNormalize3_ = new javax.swing.JCheckBox();
        flatFieldNormalize5_ = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();

        setTitle("MultiChannelShading");

        flatFieldComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        flatFieldComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldComboBox1ActionPerformed(evt);
            }
        });

        flatFieldTextField1_.setText("None");
        flatFieldTextField1_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldTextField1_ActionPerformed(evt);
            }
        });

        flatFieldButton1.setText("...");
        flatFieldButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldButton1ActionPerformed(evt);
            }
        });

        groupComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        groupComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                groupComboBoxActionPerformed(evt);
            }
        });

        jLabel1.setText("Channel Group");

        darkFieldButton_.setText("...");
        darkFieldButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                darkFieldButton_ActionPerformed(evt);
            }
        });

        darkFieldTextField_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                darkFieldTextField_ActionPerformed(evt);
            }
        });

        jLabel2.setText("Dark Image (common)");

        jLabel3.setText("FlatField Image (per channel)");

        flatFieldTextField2_.setText("None");
        flatFieldTextField2_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldTextField2_ActionPerformed(evt);
            }
        });

        flatFieldButton2.setText("...");
        flatFieldButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldButton2ActionPerformed(evt);
            }
        });

        flatFieldComboBox2.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        flatFieldComboBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldComboBox2ActionPerformed(evt);
            }
        });

        flatFieldTextField3_.setText("None");
        flatFieldTextField3_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldTextField3_ActionPerformed(evt);
            }
        });

        flatFieldButton3.setText("...");
        flatFieldButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldButton3ActionPerformed(evt);
            }
        });

        flatFieldComboBox3.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        flatFieldComboBox3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldComboBox3ActionPerformed(evt);
            }
        });

        flatFieldTextField4_.setText("None");
        flatFieldTextField4_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldTextField4_ActionPerformed(evt);
            }
        });

        flatFieldButton4.setText("...");
        flatFieldButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldButton4ActionPerformed(evt);
            }
        });

        flatFieldComboBox4.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        flatFieldComboBox4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldComboBox4ActionPerformed(evt);
            }
        });

        flatFieldTextField5_.setText("None");
        flatFieldTextField5_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldTextField5_ActionPerformed(evt);
            }
        });

        flatFieldButton5.setText("...");
        flatFieldButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldButton5ActionPerformed(evt);
            }
        });

        flatFieldComboBox5.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        flatFieldComboBox5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldComboBox5ActionPerformed(evt);
            }
        });

        useCheckBox_.setText("Execute Flat Fielding on Image Acquisition?");
        useCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useCheckBox_ActionPerformed(evt);
            }
        });

        flatFieldNormalize1_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldNormalize1_ActionPerformed(evt);
            }
        });

        flatFieldNormalize2_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldNormalize2_ActionPerformed(evt);
            }
        });

        flatFieldNormalize4_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldNormalize4_ActionPerformed(evt);
            }
        });

        flatFieldNormalize3_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldNormalize3_ActionPerformed(evt);
            }
        });

        flatFieldNormalize5_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flatFieldNormalize5_ActionPerformed(evt);
            }
        });

        jLabel4.setText("Normalize?");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(useCheckBox_)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(groupComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel2)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(darkFieldTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(darkFieldButton_))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(flatFieldComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldTextField1_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldButton1))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(flatFieldComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldTextField2_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldButton2))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(flatFieldComboBox5, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldTextField5_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldButton5))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(flatFieldComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldTextField3_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldButton3))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(flatFieldComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldTextField4_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(flatFieldButton4)))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(flatFieldNormalize1_)
                                    .addComponent(flatFieldNormalize2_)
                                    .addComponent(flatFieldNormalize5_)
                                    .addComponent(flatFieldNormalize3_)
                                    .addComponent(flatFieldNormalize4_))))
                        .addGap(0, 11, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel4)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(groupComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(16, 16, 16)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(darkFieldTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(darkFieldButton_))
                .addGap(21, 21, 21)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel4)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(flatFieldNormalize1_)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(flatFieldComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldTextField1_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldButton1)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(flatFieldComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldTextField2_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldButton2))
                    .addComponent(flatFieldNormalize2_, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(flatFieldComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldTextField3_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldButton3))
                    .addComponent(flatFieldNormalize3_, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(flatFieldComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldTextField4_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldButton4))
                    .addComponent(flatFieldNormalize4_, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(flatFieldComboBox5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldTextField5_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(flatFieldButton5))
                    .addComponent(flatFieldNormalize5_, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 33, Short.MAX_VALUE)
                .addComponent(useCheckBox_)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void flatFieldTextField1_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldTextField1_ActionPerformed
        processFlatFieldImage(0, flatFieldTextField1_.getText());
        
    }//GEN-LAST:event_flatFieldTextField1_ActionPerformed

    private void flatFieldTextField2_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldTextField2_ActionPerformed
        processFlatFieldImage(1, flatFieldTextField2_.getText());
    }//GEN-LAST:event_flatFieldTextField2_ActionPerformed

    private void flatFieldTextField3_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldTextField3_ActionPerformed
        processFlatFieldImage(2, flatFieldTextField3_.getText());
    }//GEN-LAST:event_flatFieldTextField3_ActionPerformed

    private void flatFieldTextField4_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldTextField4_ActionPerformed
        processFlatFieldImage(3, flatFieldTextField4_.getText());
    }//GEN-LAST:event_flatFieldTextField4_ActionPerformed

    private void flatFieldTextField5_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldTextField5_ActionPerformed
        processFlatFieldImage(4, flatFieldTextField5_.getText());
    }//GEN-LAST:event_flatFieldTextField5_ActionPerformed

    private void useCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useCheckBox_ActionPerformed
       processor_.setEnabled(useCheckBox_.isSelected());
      prefs_.putBoolean(USECHECKBOX, useCheckBox_.isSelected());
    }//GEN-LAST:event_useCheckBox_ActionPerformed

    private void groupComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_groupComboBoxActionPerformed
        // triggered when groupComboBox is interacted with
        // get newly selected group and update channel combo boxes with available configs
        groupName_ = (String) groupComboBox.getSelectedItem();
        processor_.setChannelGroup(groupName_);
        prefs_.put(CHANNELGROUP, groupName_);
        populateFlatFieldComboBoxes();
    }//GEN-LAST:event_groupComboBoxActionPerformed

    private void flatFieldComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldComboBox1ActionPerformed
        item_ = (String) flatFieldComboBox1.getSelectedItem();
        //check to see if we've selected the last item_ in the combobox;
        //this avoids checking the item_ name in case there is a channel named None
        if (flatFieldComboBox1.getSelectedIndex() == flatFieldComboBox1.getItemCount()-1) {
            //None is selected
            processor_.setFlatFieldChannel(0, "");
        } else {
            processor_.setFlatFieldChannel(0, item_);
            prefs_.put(groupName_ + "1", item_);
            //check other comboboxes, set them to "None" if they have the same item_ selected
            if (item_.equals((String) flatFieldComboBox2.getSelectedItem())) {
                flatFieldComboBox2.setSelectedIndex(flatFieldComboBox2.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox3.getSelectedItem())) {
                flatFieldComboBox3.setSelectedIndex(flatFieldComboBox3.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox4.getSelectedItem())) {
                flatFieldComboBox4.setSelectedIndex(flatFieldComboBox4.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox5.getSelectedItem())) {
                flatFieldComboBox5.setSelectedIndex(flatFieldComboBox5.getItemCount()-1);
            }
        }           
    }//GEN-LAST:event_flatFieldComboBox1ActionPerformed

    private void flatFieldComboBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldComboBox2ActionPerformed
        //check to see if we've selected the last item_ in the combobox;
        //this avoids checking the item_ name in case there is a channel named None
        //this avoids checking the item name in case there is a channel named None
        if (flatFieldComboBox2.getSelectedIndex() == flatFieldComboBox2.getItemCount()-1) {
            //None is selected
            processor_.setFlatFieldChannel(1, "");
        } else {
            processor_.setFlatFieldChannel(1, item_);
            //check other comboboxes, set them to "None" if they have the same item_ selected
            if (item_.equals((String) flatFieldComboBox1.getSelectedItem())) {
                flatFieldComboBox1.setSelectedIndex(flatFieldComboBox1.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox3.getSelectedItem())) {
                flatFieldComboBox3.setSelectedIndex(flatFieldComboBox3.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox4.getSelectedItem())) {
                flatFieldComboBox4.setSelectedIndex(flatFieldComboBox4.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox5.getSelectedItem())) {
                flatFieldComboBox5.setSelectedIndex(flatFieldComboBox5.getItemCount()-1);
            }
        }    
    }//GEN-LAST:event_flatFieldComboBox2ActionPerformed

    private void flatFieldComboBox3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldComboBox3ActionPerformed
        //check to see if we've selected the last item_ in the combobox;
        //this avoids checking the item_ name in case there is a channel named None
        //this avoids checking the item name in case there is a channel named None
        if (flatFieldComboBox3.getSelectedIndex() == flatFieldComboBox3.getItemCount()-1) {
            //None is selected
            processor_.setFlatFieldChannel(2, "");
        } else {
            processor_.setFlatFieldChannel(2, item_);
            //check other comboboxes, set them to "None" if they have the same item_ selected
            if (item_.equals((String) flatFieldComboBox2.getSelectedItem())) {
                flatFieldComboBox2.setSelectedIndex(flatFieldComboBox2.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox1.getSelectedItem())) {
                flatFieldComboBox1.setSelectedIndex(flatFieldComboBox1.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox4.getSelectedItem())) {
                flatFieldComboBox4.setSelectedIndex(flatFieldComboBox4.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox5.getSelectedItem())) {
                flatFieldComboBox5.setSelectedIndex(flatFieldComboBox5.getItemCount()-1);
            }
        }     
    }//GEN-LAST:event_flatFieldComboBox3ActionPerformed

    private void flatFieldComboBox4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldComboBox4ActionPerformed
        //check to see if we've selected the last item_ in the combobox;
        //this avoids checking the item_ name in case there is a channel named None
        //this avoids checking the item name in case there is a channel named None
        if (flatFieldComboBox4.getSelectedIndex() == flatFieldComboBox4.getItemCount()-1) {
            //None is selected
            processor_.setFlatFieldChannel(3, "");
        } else {
            processor_.setFlatFieldChannel(3, item_);
            //check other comboboxes, set them to "None" if they have the same item_ selected
            if (item_.equals((String) flatFieldComboBox2.getSelectedItem())) {
                flatFieldComboBox2.setSelectedIndex(flatFieldComboBox2.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox3.getSelectedItem())) {
                flatFieldComboBox3.setSelectedIndex(flatFieldComboBox3.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox1.getSelectedItem())) {
                flatFieldComboBox1.setSelectedIndex(flatFieldComboBox1.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox5.getSelectedItem())) {
                flatFieldComboBox5.setSelectedIndex(flatFieldComboBox5.getItemCount()-1);
            }
        }    
    }//GEN-LAST:event_flatFieldComboBox4ActionPerformed

    private void flatFieldComboBox5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldComboBox5ActionPerformed
        //check to see if we've selected the last item_ in the combobox;
        //this avoids checking the item_ name in case there is a channel named None
        //this avoids checking the item name in case there is a channel named None
        if (flatFieldComboBox5.getSelectedIndex() == flatFieldComboBox5.getItemCount()-1) {
            //None is selected
            processor_.setFlatFieldChannel(4, "");
        } else {
            processor_.setFlatFieldChannel(4, item_);
            //check other comboboxes, set them to "None" if they have the same item_ selected
            if (item_.equals((String) flatFieldComboBox2.getSelectedItem())) {
                flatFieldComboBox2.setSelectedIndex(flatFieldComboBox2.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox3.getSelectedItem())) {
                flatFieldComboBox3.setSelectedIndex(flatFieldComboBox3.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox4.getSelectedItem())) {
                flatFieldComboBox4.setSelectedIndex(flatFieldComboBox4.getItemCount()-1);
            }
            if (item_.equals((String) flatFieldComboBox1.getSelectedItem())) {
                flatFieldComboBox1.setSelectedIndex(flatFieldComboBox1.getItemCount()-1);
            }
        }    
    }//GEN-LAST:event_flatFieldComboBox5ActionPerformed

    private void flatFieldButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldButton1ActionPerformed
      File f = FileDialogs.openFile(this, "Flatfield image",
      new FileDialogs.FileType("MMAcq", "Flatfield image",
          flatfieldFileName_, true, IMAGESUFFIXES));
      if (f != null) {
         processFlatFieldImage(0, f.getAbsolutePath());
         flatFieldTextField1_.setText(f.getPath()); 
         prefs_.put(groupName_ + "file1", f.getPath());
      }
    }//GEN-LAST:event_flatFieldButton1ActionPerformed

    private void flatFieldButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldButton2ActionPerformed
      File f = FileDialogs.openFile(this, "Flatfield image",
      new FileDialogs.FileType("MMAcq", "Flatfield image",
          flatfieldFileName_, true, IMAGESUFFIXES));
      if (f != null) {
         processFlatFieldImage(1, f.getAbsolutePath());
         flatFieldTextField2_.setText(f.getPath()); 
      }
    }//GEN-LAST:event_flatFieldButton2ActionPerformed

    private void flatFieldButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldButton3ActionPerformed
      File f = FileDialogs.openFile(this, "Flatfield image",
      new FileDialogs.FileType("MMAcq", "Flatfield image",
          flatfieldFileName_, true, IMAGESUFFIXES));
      if (f != null) {
         processFlatFieldImage(2, f.getAbsolutePath());
         flatFieldTextField3_.setText(f.getPath());
      }
    }//GEN-LAST:event_flatFieldButton3ActionPerformed

    private void flatFieldButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldButton4ActionPerformed
      File f = FileDialogs.openFile(this, "Flatfield image",
      new FileDialogs.FileType("MMAcq", "Flatfield image",
          flatfieldFileName_, true, IMAGESUFFIXES));
      if (f != null) {
         processFlatFieldImage(3, f.getAbsolutePath());
         flatFieldTextField4_.setText(f.getPath());
      }
    }//GEN-LAST:event_flatFieldButton4ActionPerformed

    private void flatFieldButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldButton5ActionPerformed
      File f = FileDialogs.openFile(this, "Flatfield image",
      new FileDialogs.FileType("MMAcq", "Flatfield image",
          flatfieldFileName_, true, IMAGESUFFIXES));
      if (f != null) {
         processFlatFieldImage(4, f.getAbsolutePath());
         flatFieldTextField5_.setText(f.getPath());
      }
    }//GEN-LAST:event_flatFieldButton5ActionPerformed

    private void darkFieldTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_darkFieldTextField_ActionPerformed
        processBackgroundImage(darkFieldTextField_.getText());
    }//GEN-LAST:event_darkFieldTextField_ActionPerformed

    private void darkFieldButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_darkFieldButton_ActionPerformed
      File f = FileDialogs.openFile(this, "Dark image",
      new FileDialogs.FileType("MMAcq", "Dark image",
          backgroundFileName_, true, IMAGESUFFIXES));
      if (f != null) {
         processBackgroundImage(f.getAbsolutePath());
         darkFieldTextField_.setText(backgroundFileName_);
      }
    }//GEN-LAST:event_darkFieldButton_ActionPerformed

    private void flatFieldNormalize1_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldNormalize1_ActionPerformed
         processor_.setFlatFieldNormalize(0, flatFieldNormalize1_.isSelected());
         prefs_.putBoolean(FLATFIELDNORMALIZE1, flatFieldNormalize1_.isSelected());
    }//GEN-LAST:event_flatFieldNormalize1_ActionPerformed

    private void flatFieldNormalize2_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldNormalize2_ActionPerformed
         processor_.setFlatFieldNormalize(1, flatFieldNormalize2_.isSelected());
         prefs_.putBoolean(FLATFIELDNORMALIZE2, flatFieldNormalize2_.isSelected());
    }//GEN-LAST:event_flatFieldNormalize2_ActionPerformed

    private void flatFieldNormalize4_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldNormalize4_ActionPerformed
         processor_.setFlatFieldNormalize(3, flatFieldNormalize4_.isSelected());
         prefs_.putBoolean(FLATFIELDNORMALIZE4, flatFieldNormalize4_.isSelected());
    }//GEN-LAST:event_flatFieldNormalize4_ActionPerformed

    private void flatFieldNormalize3_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldNormalize3_ActionPerformed
         processor_.setFlatFieldNormalize(2, flatFieldNormalize3_.isSelected());
         prefs_.putBoolean(FLATFIELDNORMALIZE3, flatFieldNormalize3_.isSelected());
    }//GEN-LAST:event_flatFieldNormalize3_ActionPerformed

    private void flatFieldNormalize5_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flatFieldNormalize5_ActionPerformed
         processor_.setFlatFieldNormalize(4, flatFieldNormalize5_.isSelected());
         prefs_.putBoolean(FLATFIELDNORMALIZE5, flatFieldNormalize5_.isSelected());
    }//GEN-LAST:event_flatFieldNormalize5_ActionPerformed

    /**
     * @param args the command line arguments
     */


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton darkFieldButton_;
    private javax.swing.JTextField darkFieldTextField_;
    private javax.swing.JButton flatFieldButton1;
    private javax.swing.JButton flatFieldButton2;
    private javax.swing.JButton flatFieldButton3;
    private javax.swing.JButton flatFieldButton4;
    private javax.swing.JButton flatFieldButton5;
    private javax.swing.JComboBox flatFieldComboBox1;
    private javax.swing.JComboBox flatFieldComboBox2;
    private javax.swing.JComboBox flatFieldComboBox3;
    private javax.swing.JComboBox flatFieldComboBox4;
    private javax.swing.JComboBox flatFieldComboBox5;
    private javax.swing.JCheckBox flatFieldNormalize1_;
    private javax.swing.JCheckBox flatFieldNormalize2_;
    private javax.swing.JCheckBox flatFieldNormalize3_;
    private javax.swing.JCheckBox flatFieldNormalize4_;
    private javax.swing.JCheckBox flatFieldNormalize5_;
    private javax.swing.JTextField flatFieldTextField1_;
    private javax.swing.JTextField flatFieldTextField2_;
    private javax.swing.JTextField flatFieldTextField3_;
    private javax.swing.JTextField flatFieldTextField4_;
    private javax.swing.JTextField flatFieldTextField5_;
    private javax.swing.JComboBox groupComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JCheckBox useCheckBox_;
    // End of variables declaration//GEN-END:variables
}
