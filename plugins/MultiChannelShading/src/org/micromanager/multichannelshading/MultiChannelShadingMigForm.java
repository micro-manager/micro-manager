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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

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
   private String statusMessage_;
   private final Font arialSmallFont_;
   private final ShadingTableModel shadingTableModel_;
   private final JCheckBox useCheckBox_;
   private final Dimension buttonSize_;
   private final JLabel statusLabel_;
   
  
   
    /**
     * Creates new form MultiChannelShadingForm
     * @param processor
     * @param gui
     */
   @SuppressWarnings("LeakingThisInConstructor")
   public MultiChannelShadingMigForm(ShadingProcessor processor, ScriptInterface gui) {
      processor_ = processor;
      gui_ = gui;
      mmc_ = gui_.getMMCore();
      prefs_ = this.getPrefsNode();
      this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      arialSmallFont_ = new Font("Arial", Font.PLAIN, 12);
      buttonSize_ = new Dimension(70, 21);
      
      final JButton addButton =  mcsButton(buttonSize_, arialSmallFont_);
      final JButton removeButton =  mcsButton(buttonSize_, arialSmallFont_);
      
      mcsPluginWindow = this;
      this.setLayout(new MigLayout("flowx, fill, insets 8"));
      this.setTitle("MultiChannelShading");

      loadAndRestorePosition(100, 100, 350, 250);
      
      JLabel channelGroupLabel = new JLabel("Channel Group:");
      channelGroupLabel.setFont(arialSmallFont_);
      add(channelGroupLabel);
      
      //populate group ComboBox
      final JComboBox groupComboBox = new JComboBox();
      String[] channelGroups = mmc_.getAvailableConfigGroups().toArray();
      groupComboBox.setModel(new javax.swing.DefaultComboBoxModel(
              channelGroups));
      groupName_ = prefs_.get(CHANNELGROUP, "");
      groupComboBox.setSelectedItem(groupName_);
      groupComboBox.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            groupName_ = (String) groupComboBox.getSelectedItem();
            shadingTableModel_.setChannelGroup(groupName_);
            updateAddAndRemoveButtons(addButton, removeButton);
            prefs_.put(CHANNELGROUP, groupName_);
         }
      });
      add(groupComboBox, "wrap");
             
      JLabel darkImageLabel = new JLabel("Dark Image (common):");
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
      darkFieldTextField.addFocusListener(new FocusListener() {
         @Override
         public void focusGained(FocusEvent fe) {
            // great, so what?
         }

         @Override
         public void focusLost(FocusEvent fe) {
            processBackgroundImage(darkFieldTextField.getText());
         }
      });
      processBackgroundImage(darkFieldTextField.getText());
      add(darkFieldTextField, "span 2, growx ");


      final JButton darkFieldButton =  mcsButton(buttonSize_, arialSmallFont_);
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
      add(scrollPane, "span 4 2, grow, push");
      shadingTableModel_ = new ShadingTableModel(gui_, 
              processor_.getImageCollection()); 
      shadingTableModel_.setChannelGroup(groupName_);
      final ShadingTable shadingTable = 
              new ShadingTable(gui_, shadingTableModel_, this);
      scrollPane.setViewportView(shadingTable);
      
      // Add and Remove buttons
      // Place them inside their own JPanel
      JPanel buttonPanel = new JPanel();
      buttonPanel.setLayout(new MigLayout("filly, insets 0"));
      
      addButton.setText("Add");
      addButton.setMinimumSize(buttonSize_);
      addButton.setFont(arialSmallFont_);
      addButton.setIcon(new ImageIcon(MMStudio.class.getResource(
            "/org/micromanager/icons/plus.png")));
      addButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            shadingTableModel_.addRow();
            updateAddAndRemoveButtons(addButton, removeButton);
         }
      });
      buttonPanel.add(addButton, "wrap");
      
      removeButton.setText("Remove");
      removeButton.setMinimumSize(buttonSize_);
      removeButton.setFont(arialSmallFont_);
      removeButton.setIcon(new ImageIcon (MMStudio.class.getResource(
            "/org/micromanager/icons/minus.png")));
      removeButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            shadingTable.stopCellEditing();
            shadingTableModel_.removeRow(shadingTable.getSelectedRows());
            updateAddAndRemoveButtons(addButton, removeButton);
         }
      });
      buttonPanel.add(removeButton);
      
      add(buttonPanel, "gap 5px, aligny top, wrap");
      add(new JLabel(""), "growy, pushy, wrap");
      
      statusLabel_ = new JLabel(" ");
      useCheckBox_ = new JCheckBox();
      useCheckBox_.setText("Execute Flat Fielding on Image Acquisition?");
      useCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            processor_.setEnabled(useCheckBox_.isSelected());
            prefs_.putBoolean(USECHECKBOX, useCheckBox_.isSelected());
            statusLabel_.setText(" ");
         }
      });
      useCheckBox_.setSelected(prefs_.getBoolean(USECHECKBOX, true));
      add(useCheckBox_, "span 3, wrap");     
      add(statusLabel_, "span 3, wrap");
     
      processor_.setEnabled(useCheckBox_.isSelected());
      
   }
   
   @Override
   public void dispose() {
      super.dispose();
      processor_.setMyFrameToNull();
   }

   private void updateAddAndRemoveButtons(JButton addButton, JButton removeButton) {
      removeButton.setEnabled(shadingTableModel_.getRowCount() > 0);
      int availablePresets = shadingTableModel_.
              getUnusedNumberOfPresetsInCurrentGroup();
      addButton.setEnabled(availablePresets > 0);
   }
   
   public final Font getButtonFont() {
      return arialSmallFont_;
   }
   
   public final Dimension getButtonDimension() {
      return buttonSize_;
   }
   
   
   public final JButton mcsButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      return button;
   }
   
   public synchronized void setStatus(final String status) {
      statusMessage_ = status;
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            // update the statusLabel from this thread
            if (status != null) {
               statusLabel_.setText(status);
            }
         }
      });
   }

   public synchronized String getStatus() {
      String status = statusMessage_;
      statusMessage_ = null;
      return status;
   }
   
   private void processBackgroundImage(String fileName) {
      if (EMPTY_FILENAME_INDICATOR.equals(fileName)) {
         fileName = "";
      }
      try {
         ImageCollection ic = processor_.getImageCollection();
         ic.setBackground(fileName);
         backgroundFileName_ = fileName;
         prefs_.put(DARKFIELDFILENAME, backgroundFileName_);
      } catch (MMException ex) {
         ReportingUtils.showError(ex, "Failed to set background image");
      }
   }
   
    public void updateProcessorEnabled(boolean enabled) {
      useCheckBox_.setSelected(enabled);
      // useCheckBox may already be doing the following:
      // processor_.setEnabled(enabled);
      prefs_.putBoolean(USECHECKBOX, enabled);
   }
    
   public ShadingTableModel getShadingTableModel() {
      return shadingTableModel_;
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
