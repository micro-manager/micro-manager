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

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;

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
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMDialog;

/**
 *
 * @author nico
 */
public class MultiChannelShadingMigForm extends MMDialog implements ProcessorConfigurator {
   private  MMDialog mcsPluginWindow;
   private final Studio studio_;
   private final MutablePropertyMapView profileSettings_;
   private final mmcorej.CMMCore mmc_;
   
   public static final String DARKFIELDFILENAME = "BackgroundFileName";
   public static final String CHANNELGROUP = "ChannelGroup";
   public static final String USEOPENCL = "UseOpenCL";
   private static final String EMPTY_FILENAME_INDICATOR = "None";
   private final String[] IMAGESUFFIXES = {"tif", "tiff", "jpg", "png"};
   private String backgroundFileName_;
   private String groupName_;
   private String statusMessage_;
   private final Font arialSmallFont_;
   private final ShadingTableModel shadingTableModel_;
   private final Dimension buttonSize_;
   private final JLabel statusLabel_;
   private ImageCollection imageCollection_;
   
     
    /**
     * Creates new form MultiChannelShadingForm
     * @param settings
     * @param studio
     */
   @SuppressWarnings("LeakingThisInConstructor")
   public MultiChannelShadingMigForm(PropertyMap settings, Studio studio) {
      studio_ = studio;
      profileSettings_ = 
              studio_.profile().getSettings(MultiChannelShadingMigForm.class);
      imageCollection_ = new ImageCollection(studio_);
      mmc_ = studio_.getCMMCore();
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            dispose();
         }
      }
      );
      arialSmallFont_ = new Font("Arial", Font.PLAIN, 12);
      buttonSize_ = new Dimension(70, 21);
      
      final JButton addButton =  mcsButton(buttonSize_, arialSmallFont_);
      final JButton removeButton =  mcsButton(buttonSize_, arialSmallFont_);
      
      mcsPluginWindow = this;
      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      super.setTitle(MultiChannelShading.MENUNAME);

      super.loadAndRestorePosition(100, 100, 375, 275);
      
      JLabel channelGroupLabel = new JLabel("Channel Group:");
      channelGroupLabel.setFont(arialSmallFont_);
      super.add(channelGroupLabel);
      
      //populate group ComboBox
      final JComboBox groupComboBox = new JComboBox();
      String[] channelGroups = mmc_.getAvailableConfigGroups().toArray();
      groupComboBox.setModel(new javax.swing.DefaultComboBoxModel(
              channelGroups));
      groupName_ = settings.getString(CHANNELGROUP,
            profileSettings_.getString(CHANNELGROUP, ""));
      groupComboBox.setSelectedItem(groupName_);
      groupComboBox.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            groupName_ = (String) groupComboBox.getSelectedItem();
            shadingTableModel_.setChannelGroup(groupName_);
            updateAddAndRemoveButtons(addButton, removeButton);
            profileSettings_.putString(CHANNELGROUP, groupName_);
            studio_.data().notifyPipelineChanged();
         }
      });
      super.add(groupComboBox);
      
      JCheckBox useOpenCLCheckBox = new JCheckBox("Use GPU");
      useOpenCLCheckBox.setSelected(profileSettings_.getBoolean(USEOPENCL, false));
      useOpenCLCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            profileSettings_.putBoolean(USEOPENCL,useOpenCLCheckBox.isSelected());
            studio_.data().notifyPipelineChanged();
         }
      });
      super.add(useOpenCLCheckBox, "skip 2");
      
      JButton helpButton = new JButton("Help");
      helpButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new Thread(org.micromanager.internal.utils.GUIUtils.makeURLRunnable(
                    "https://micro-manager.org/wiki/Flat-Field_Correction")).start();
        }
      });
      super.add (helpButton, "wrap");
             
      JLabel darkImageLabel = new JLabel("Dark Image (common):");
      darkImageLabel.setFont(arialSmallFont_);
      super.add (darkImageLabel);
      
      final JTextField darkFieldTextField = new JTextField(50);
      darkFieldTextField.setFont(arialSmallFont_);
      //populate darkFieldName from profile and process it.
      darkFieldTextField.setText(settings.getString(DARKFIELDFILENAME,
               profileSettings_.getString(DARKFIELDFILENAME, "")));
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
      darkFieldTextField.setText(processBackgroundImage(
              darkFieldTextField.getText()));
      super.add(darkFieldTextField, "span 2");


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
      super.add(darkFieldButton, "wrap");
      
      // Table with channel presets and files
      final JScrollPane scrollPane = new JScrollPane() {
         @Override
         public Dimension getPreferredSize() {
            return new Dimension(550, 150);
         }
      };
      super.add(scrollPane, "span 5 2, grow, push");
      shadingTableModel_ = new ShadingTableModel(studio_, imageCollection_);
      shadingTableModel_.setChannelGroup(groupName_);
      final ShadingTable shadingTable = 
              new ShadingTable(studio_, shadingTableModel_, this);
      scrollPane.setViewportView(shadingTable);
      
      // Add and Remove buttons
      // Place them inside their own JPanel
      JPanel buttonPanel = new JPanel();
      buttonPanel.setLayout(new MigLayout("filly, insets 0"));
      
      addButton.setText("Add");
      addButton.setMinimumSize(buttonSize_);
      addButton.setFont(arialSmallFont_);
      addButton.setIcon(new ImageIcon(getClass().getResource(
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
      removeButton.setIcon(new ImageIcon (getClass().getResource(
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
      
      super.add(buttonPanel, "gap 5px, aligny top, wrap");
      super.add(new JLabel(""), "growy, pushy, wrap");
      
      statusLabel_ = new JLabel(" ");
      super.add(statusLabel_, "span 3, wrap");
      updateAddAndRemoveButtons(addButton, removeButton);
      super.pack();
      
      studio_.events().registerForEvents(this);
   }
   
   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public void cleanup() {
      shadingTableModel_.setChannelGroup(groupName_);
      super.dispose();
   }

   @Override
   public PropertyMap getSettings() {
      PropertyMap.Builder builder = PropertyMaps.builder();
      builder.putString(CHANNELGROUP, shadingTableModel_.getChannelGroup());
      builder.putStringList("Presets", shadingTableModel_.getUsedPresets());
      builder.putString(DARKFIELDFILENAME, imageCollection_.getBackgroundFile());
      builder.putBoolean(USEOPENCL, profileSettings_.getBoolean(USEOPENCL, false));
      ArrayList<String> files = new ArrayList<String>();
      for (String preset : shadingTableModel_.getUsedPresets()) {
         files.add(imageCollection_.getFileForPreset(preset));
      }
      builder.putStringList("PresetFiles", files.toArray(new String[] {}));
      return builder.build();
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
   
   /**
    * Processes background image
    * Return filename if successful, empty string otherwise
    * @param fileName
    * @return fileName
    */
   private String processBackgroundImage(String fileName) {
      if (EMPTY_FILENAME_INDICATOR.equals(fileName)) {
         fileName = "";
      }
      try {
         imageCollection_.setBackground(fileName);
         backgroundFileName_ = fileName;
         profileSettings_.putString(DARKFIELDFILENAME, backgroundFileName_);
         studio_.data().notifyPipelineChanged();
      } catch (ShadingException ex) {
         studio_.logs().showError(ex, "Failed to set background image");
         return "";
      }
      return fileName;
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

   @Subscribe
   public void closeRequested( ShutdownCommencingEvent sce){
      this.dispose();
   }
    
}
