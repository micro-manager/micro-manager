///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiChannelShadingMigForm.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

import ij.ImagePlus;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.GUIUtils;

/**
 *
 * @author nico
 */
public class MultiChannelShadingMigForm extends javax.swing.JFrame {
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
   private final String[] NUMBERS = {"1", "2", "3", "4", "5", "6"};
   private String flatfieldFileName_;
   private String backgroundFileName_;
   private String groupName_;
   private DefaultComboBoxModel configNameList;
   private final Font arialSmallFont_;
   
  
   
    /**
     * Creates new form MultiChannelShadingForm
     * @param processor
     * @param gui
     */
   public MultiChannelShadingMigForm(BFProcessor processor, ScriptInterface gui) {
      processor_ = processor;
      gui_ = gui;
      mmc_ = gui_.getMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      arialSmallFont_ = new Font("Arial", Font.PLAIN, 12);
      
      final javax.swing.JFrame mcsPluginWindow = this;
      this.setBackground(gui_.getBackgroundColor());
      this.setLayout(new MigLayout("flowx, filly, insets 8", "[]", "[][][][grow,fill][]"));
      this.setTitle("MultiChannelShading");
      this.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            mcsPluginWindow.dispose();
         }
      });
      
      // Read preferences and apply to the dialog
      GUIUtils.recallPosition(this); // move this out of the class
      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);
      setLocation(frameXPos_, frameYPos_);
      
      Dimension buttonSize = new Dimension(88, 21);
      
      JLabel channelGroupLabel = new JLabel("Channel Group");
      channelGroupLabel.setFont(arialSmallFont_);
      add(channelGroupLabel);
      
      //populate group ComboBox
      final JComboBox groupComboBox = new JComboBox();
      String[] channelGroups = mmc_.getAvailableConfigGroups().toArray();
      groupComboBox.setModel(new javax.swing.DefaultComboBoxModel(
              channelGroups));
      groupName_ = prefs_.get(CHANNELGROUP, "");
      groupComboBox.setSelectedItem(groupName_);
      groupName_ = (String) groupComboBox.getSelectedItem();
      processor_.setChannelGroup(groupName_);
      groupComboBox.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            groupName_ = (String) groupComboBox.getSelectedItem();
            processor_.setChannelGroup(groupName_);
            prefs_.put(CHANNELGROUP, groupName_);
            //populateFlatFieldComboBoxes();
         }
      });
      add(groupComboBox, "wrap");
             
      JLabel darkImageLabel = new JLabel("Dark Image (common)");
      darkImageLabel.setFont(arialSmallFont_);
      add (darkImageLabel, "wrap");
      
      final JTextField darkFieldTextField = new JTextField();
      darkFieldTextField.setFont(arialSmallFont_);
      //populate darkFieldName from preferences and process it.
      String darkFieldFileName = prefs_.get(DARKFIELDFILENAME, "");
      darkFieldTextField.setText("".equals(darkFieldFileName)
              ? EMPTY_FILENAME_INDICATOR : darkFieldFileName);
      darkFieldTextField.setHorizontalAlignment(JTextField.RIGHT);
      darkFieldTextField.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            processBackgroundImage(darkFieldTextField.getText());
         }
      });
      processBackgroundImage(darkFieldTextField.getText());
      add(darkFieldTextField, "span 2");


      final JButton darkFieldButton =  new JButton();
      darkFieldButton.setText("...");
      darkFieldButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            File f = FileDialogs.openFile(mcsPluginWindow, "Dark image",
                    new FileDialogs.FileType("MMAcq", "Dark image",
                            backgroundFileName_, true, IMAGESUFFIXES));
            if (f != null) {
               processBackgroundImage(f.getAbsolutePath());
               darkFieldTextField.setText(backgroundFileName_);
            }
         }
      });
      add(darkFieldButton, "wrap");
      
      final JScrollPane scrollPane = new JScrollPane();
      add(scrollPane, "span 4, grow, wrap");

      final JCheckBox useCheckBox = new JCheckBox();
      useCheckBox.setText("Execute Flat Fielding on Image Acquisition?");
      useCheckBox.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            processor_.setEnabled(useCheckBox.isSelected());
            prefs_.putBoolean(USECHECKBOX, useCheckBox.isSelected());
         }
      });
      useCheckBox.setSelected(prefs_.getBoolean(USECHECKBOX, true));
      add(useCheckBox, "span 3");


      processor_.setEnabled(useCheckBox.isSelected());
   }

   private JButton mcsButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      return button;
   }
   
   private void processBackgroundImage(String fileName) {
      if (EMPTY_FILENAME_INDICATOR.equals(fileName)) {
         fileName = "";
      }

      // If we have a filename, try to open it and set the background image.
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
   
    void updateProcessorEnabled(boolean enabled) {
      // TODO
   }
   
}
