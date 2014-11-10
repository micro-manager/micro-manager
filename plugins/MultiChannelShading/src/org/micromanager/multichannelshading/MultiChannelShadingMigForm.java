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
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MMDialog;

/**
 *
 * @author nico
 */
public class MultiChannelShadingMigForm extends MMDialog {
   private  MMDialog mcsPluginWindow;
   private final ScriptInterface gui_;
   private final mmcorej.CMMCore mmc_;
   private final Preferences prefs_;
   
   private final ShadingProcessor processor_;

   private static final String DARKFIELDFILENAME = "BackgroundFileName";
   private static final String CHANNELGROUP = "ChannelGroup";
   private static final String USECHECKBOX = "UseCheckBox";
   private static final String EMPTY_FILENAME_INDICATOR = "None";
   private final String[] IMAGESUFFIXES = {"tif", "tiff", "jpg", "png"};
   private String backgroundFileName_;
   private String groupName_;
   private final Font arialSmallFont_;
   private final ShadingTableModel shadingTableModel_;
   private final JCheckBox useCheckBox_;
   
  
   
    /**
     * Creates new form MultiChannelShadingForm
     * @param processor
     * @param gui
     */
   public MultiChannelShadingMigForm(ShadingProcessor processor, ScriptInterface gui) {
      processor_ = processor;
      gui_ = gui;
      mmc_ = gui_.getMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      arialSmallFont_ = new Font("Arial", Font.PLAIN, 12);
      
      final JButton addButton =  new JButton();
      final JButton removeButton =  new JButton();
      
      mcsPluginWindow = this;
      this.setBackground(gui_.getBackgroundColor());
      this.setLayout(new MigLayout("flowx, fill, insets 8", "[]", 
              "[][][][][]"));
      this.setTitle("MultiChannelShading");

      loadAndRestorePosition(100, 100, 350, 250);
      
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
      //processor_.setChannelGroup(groupName_);
      groupComboBox.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            groupName_ = (String) groupComboBox.getSelectedItem();
            shadingTableModel_.setChannelGroup(groupName_);
            updateAddRemoveButtons(addButton, removeButton);
            //processor_.setChannelGroup(groupName_);
            prefs_.put(CHANNELGROUP, groupName_);
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
      add(darkFieldTextField, "span 2, growx");


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
      
      // Table with channel presets and files
      final JScrollPane scrollPane = new JScrollPane();
      add(scrollPane, "span 4 3, grow");
      shadingTableModel_ = new ShadingTableModel(gui_); 
      shadingTableModel_.setChannelGroup(groupName_);
      final JTable shadingTable = 
              new ShadingTable(gui_, shadingTableModel_, this);
      scrollPane.setViewportView(shadingTable);
      
      // Add and Remove buttons
      addButton.setText("Add");
      addButton.setMinimumSize(buttonSize);
      addButton.setFont(arialSmallFont_);
      addButton.setIcon(new ImageIcon(MMStudio.class.getResource(
            "/org/micromanager/icons/plus.png")));
      addButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            shadingTableModel_.addRow();
            updateAddRemoveButtons(addButton, removeButton);
         }
      });
      add(addButton, "wrap");
      
      removeButton.setText("Remove");
      removeButton.setMinimumSize(buttonSize);
      removeButton.setFont(arialSmallFont_);
      removeButton.setIcon(new ImageIcon (MMStudio.class.getResource(
            "/org/micromanager/icons/minus.png")));
      removeButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            shadingTableModel_.removeRow(shadingTable.getSelectedRows());
            updateAddRemoveButtons(addButton, removeButton);
         }
      });
      add(removeButton, "wrap");
      add(new JLabel(""), "wrap");
      
      
      useCheckBox_ = new JCheckBox();
      useCheckBox_.setText("Execute Flat Fielding on Image Acquisition?");
      useCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            processor_.setEnabled(useCheckBox_.isSelected());
            prefs_.putBoolean(USECHECKBOX, useCheckBox_.isSelected());
         }
      });
      useCheckBox_.setSelected(prefs_.getBoolean(USECHECKBOX, true));
      add(useCheckBox_, "span 3");


      processor_.setEnabled(useCheckBox_.isSelected());
      
   }

   private void updateAddRemoveButtons(JButton addButton, JButton removeButton) {
      removeButton.setEnabled(shadingTableModel_.getRowCount() > 0);
      int availablePresets = shadingTableModel_.
              getUnusedNumberOfPresetsInCurrentGroup();
      addButton.setEnabled(availablePresets > 0);
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
      useCheckBox_.setSelected(enabled);
      // useCheckBox may already be doing the following:
      processor_.setEnabled(enabled);
      prefs_.putBoolean(USECHECKBOX, enabled);
   }
   
/**
     * Helper function for the individual buttons
     * @param rowNumber Table row associated with action Event
     */
    public void flatFieldButtonActionPerformed(int rowNumber) {
       File f = FileDialogs.openFile(this, "Flatfield image",
            new FileDialogs.FileType("MMAcq", "Flatfield image",
               (String) shadingTableModel_.getValueAt(rowNumber, 1), 
               true, IMAGESUFFIXES)
       );
       if (f != null) {
          shadingTableModel_.setValueAt(f.getAbsolutePath(), rowNumber, 1); 
      }
    }

   
    
}
