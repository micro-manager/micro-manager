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
import ij.IJ;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.events.ChannelGroupChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * Creates the dialog.
 *
 * @author nico
 */
public class MultiChannelShadingMigForm extends JDialog implements ProcessorConfigurator {
   private JDialog mcsPluginWindow;
   private final Studio studio_;
   private final PropertyMap settings_;
   private final mmcorej.CMMCore mmc_;

   public static final String DARKFIELDFILENAME = "BackgroundFileName";
   public static final String CHANNELGROUP = "ChannelGroup";
   public static final String USEOPENCL = "UseOpenCL";
   public static final String PIXELSIZECALIBRATION = "PixelSizeCalibration";
   private static final String EMPTY_FILENAME_INDICATOR = "None";
   private static final String ANY_PIXELSIZE = "any";
   private final String[] imageSuffixes = {"tif", "tiff", "jpg", "png"};
   private String backgroundFileName_;
   private String groupName_;
   private String statusMessage_;
   private final Font arialSmallFont_;
   private final JComboBox<String> groupComboBox_;
   private final JComboBox<String> pixelSizeComboBox_;
   private final JCheckBox useOpenCLCheckBox_;
   private final ShadingTableModel shadingTableModel_;
   private final Dimension buttonSize_;
   private final JLabel statusLabel_;
   private ImageCollection imageCollection_;


   /**
    * Creates new form MultiChannelShadingForm.
    *
    * @param settings PropertyMap Settings
    * @param studio Studio object
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public MultiChannelShadingMigForm(PropertyMap settings, Studio studio) {
      studio_ = studio;
      settings_ = settings;
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

      final JButton addButton = mcsButton(buttonSize_, arialSmallFont_);
      final JButton removeButton = mcsButton(buttonSize_, arialSmallFont_);

      mcsPluginWindow = this;
      super.setLayout(new MigLayout("flowx, fill, insets 8",
            "[][grow,fill][][][]"));
      String processorName = settings_.getString("ProcessorName", "");
      if (!processorName.isEmpty()) {
         super.setTitle(processorName);
      } else {
         super.setTitle(MultiChannelShading.MENUNAME);
      }

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setBounds(100, 100, 375, 375);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(),
            processorName.isEmpty() ? null : processorName);

      JLabel pixelSizeLabel = new JLabel("Only use with Pixel Size Calibration:");
      pixelSizeLabel.setFont(arialSmallFont_);
      super.add(pixelSizeLabel);

      pixelSizeComboBox_ = new JComboBox<>();
      pixelSizeComboBox_.addItem(ANY_PIXELSIZE);
      for (String pixelSizeConfig : mmc_.getAvailablePixelSizeConfigs()) {
         pixelSizeComboBox_.addItem(pixelSizeConfig);
      }
      pixelSizeComboBox_.setSelectedItem(
            settings_.getString(PIXELSIZECALIBRATION,
                  studio_.profile().getSettings(MultiChannelShadingMigForm.class)
                        .getString(PIXELSIZECALIBRATION, ANY_PIXELSIZE)));
      pixelSizeComboBox_.addActionListener(e -> {
         studio_.data().notifyPipelineChanged();
      });
      super.add(pixelSizeComboBox_, "wmax 200, wrap");

      JLabel channelGroupLabel = new JLabel("Channel Group:");
      channelGroupLabel.setFont(arialSmallFont_);
      super.add(channelGroupLabel);

      //populate group ComboBox
      groupComboBox_ = new JComboBox<>();
      groupComboBox_.addItem("");
      for (String group : mmc_.getAvailableConfigGroups()) {
         groupComboBox_.addItem(group);
      }
      groupName_ = settings_.getString(CHANNELGROUP,
            studio_.profile().getSettings(MultiChannelShadingMigForm.class)
                  .getString(CHANNELGROUP, ""));
      groupComboBox_.setSelectedItem(groupName_);
      groupComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            groupName_ = (String) groupComboBox_.getSelectedItem();
            shadingTableModel_.setChannelGroup(groupName_);
            updateAddAndRemoveButtons(addButton, removeButton);
            studio_.data().notifyPipelineChanged();
         }
      });
      super.add(groupComboBox_, "wmax 200");

      useOpenCLCheckBox_ = new JCheckBox("Use GPU");
      useOpenCLCheckBox_.setSelected(settings_.getBoolean(USEOPENCL,
            studio_.profile().getSettings(MultiChannelShadingMigForm.class)
                  .getBoolean(USEOPENCL, false)));
      useOpenCLCheckBox_.addActionListener(e -> {
         studio_.data().notifyPipelineChanged();
      });
      super.add(useOpenCLCheckBox_);

      JButton helpButton = new JButton("Help");
      helpButton.addActionListener(e ->
              new Thread(GUIUtils.makeURLRunnable(
            "https://micro-manager.org/wiki/Flat-Field_Correction")).start());
      super.add(helpButton, "wrap");

      JLabel darkImageLabel = new JLabel("Dark Image (common):");
      darkImageLabel.setFont(arialSmallFont_);
      super.add(darkImageLabel);

      final JTextField darkFieldTextField = new JTextField(50);
      darkFieldTextField.setFont(arialSmallFont_);
      //populate darkFieldName from profile and process it.
      darkFieldTextField.setText(settings_.getString(DARKFIELDFILENAME,
            studio_.profile().getSettings(MultiChannelShadingMigForm.class)
                  .getString(DARKFIELDFILENAME, "")));
      darkFieldTextField.setHorizontalAlignment(JTextField.RIGHT);
      darkFieldTextField.addActionListener(evt ->
              processBackgroundImage(darkFieldTextField.getText()));
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
      super.add(darkFieldTextField, "span 2, growx");


      final JButton darkFieldButton = mcsButton(buttonSize_, arialSmallFont_);
      darkFieldButton.setText("...");
      darkFieldButton.addActionListener(evt -> {
         File f = FileDialogs.openFile(mcsPluginWindow, "Dark image",
               new FileDialogs.FileType("MMAcq", "Dark image",
                     backgroundFileName_, true, imageSuffixes));
         if (f != null) {
            processBackgroundImage(f.getAbsolutePath());
            darkFieldTextField.setText(backgroundFileName_);
         }
      });
      super.add(darkFieldButton);

      final JButton darkFieldShowButton = mcsButton(buttonSize_, arialSmallFont_);
      darkFieldShowButton.setText("Show");
      darkFieldShowButton.addActionListener(evt -> {
         String path = darkFieldTextField.getText();
         if (path != null && !path.isEmpty()) {
            IJ.open(path);
         }
      });
      super.add(darkFieldShowButton, "wrap");

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
      // Restore per-instance preset/file mappings from saved settings
      List<String> savedPresets = settings_.getStringList("Presets");
      List<String> savedFiles = settings_.getStringList("PresetFiles");
      if (!savedPresets.isEmpty() && savedPresets.size() == savedFiles.size()) {
         shadingTableModel_.loadPresets(savedPresets, savedFiles);
      }
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
      addButton.addActionListener(evt -> {
         shadingTableModel_.addRow();
         updateAddAndRemoveButtons(addButton, removeButton);
         studio_.data().notifyPipelineChanged();
      });
      buttonPanel.add(addButton, "wrap");

      removeButton.setText("Remove");
      removeButton.setMinimumSize(buttonSize_);
      removeButton.setFont(arialSmallFont_);
      removeButton.setIcon(new ImageIcon(getClass().getResource(
            "/org/micromanager/icons/minus.png")));
      removeButton.addActionListener(evt -> {
         shadingTable.stopCellEditing();
         shadingTableModel_.removeRow(shadingTable.getSelectedRows());
         updateAddAndRemoveButtons(addButton, removeButton);
         studio_.data().notifyPipelineChanged();
      });
      buttonPanel.add(removeButton);

      super.add(buttonPanel, "gap 5px, aligny top, wrap");
      super.add(new JLabel(""), "growy, pushy, wrap");

      statusLabel_ = new JLabel(" ");
      super.add(statusLabel_, "span 5, wrap");
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
      builder.putBoolean(USEOPENCL, useOpenCLCheckBox_.isSelected());
      builder.putString(PIXELSIZECALIBRATION,
            (String) pixelSizeComboBox_.getSelectedItem());
      ArrayList<String> files = new ArrayList<>();
      for (String preset : shadingTableModel_.getUsedPresets()) {
         String file = imageCollection_.getFileForPreset(preset);
         files.add(file != null ? file : "");
      }
      builder.putStringList("PresetFiles", files.toArray(new String[] {}));
      return builder.build();
   }

   private void updateAddAndRemoveButtons(JButton addButton, JButton removeButton) {
      removeButton.setEnabled(shadingTableModel_.getRowCount() > 0);
      int availablePresets = shadingTableModel_.getUnusedNumberOfPresetsInCurrentGroup();
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
      SwingUtilities.invokeLater(() -> {
         // update the statusLabel from this thread
         if (status != null) {
            statusLabel_.setText(status);
         }
      });
   }

   public synchronized String getStatus() {
      String status = statusMessage_;
      statusMessage_ = null;
      return status;
   }

   /**
    * Processes background image.
    * Return filename if successful, empty string otherwise
    *
    * @param fileName - name of the background image file to process
    * @return fileName - filename if successful, empty string otherwise
    */
   private String processBackgroundImage(String fileName) {
      if (EMPTY_FILENAME_INDICATOR.equals(fileName)) {
         fileName = "";
      }
      try {
         imageCollection_.setBackground(fileName);
         backgroundFileName_ = fileName;
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
    * Opens the flatfield image for the given row in ImageJ.
    *
    * @param rowNumber Table row whose image to show
    */
   public void showFlatFieldImage(int rowNumber) {
      String path = (String) shadingTableModel_.getValueAt(rowNumber, 1);
      if (path != null && !path.isEmpty()) {
         IJ.open(path);
      }
   }

   /**
    * Helper function for the individual buttons.
    *
    * @param rowNumber Table row associated with action Event
    */
   public void flatFieldButtonActionPerformed(int rowNumber) {
      File f = FileDialogs.openFile(this, "Flatfield image",
            new FileDialogs.FileType("MMAcq", "Flatfield image",
                  (String) shadingTableModel_.getValueAt(rowNumber, 1),
                  true, imageSuffixes)
      );
      if (f != null) {
         shadingTableModel_.setValueAt(f.getAbsolutePath(), rowNumber, 1);
         studio_.data().notifyPipelineChanged();
      }
   }

   @Subscribe
   public void closeRequested(ShutdownCommencingEvent sce) {
      if (!sce.isCanceled()) {
         dispose();
      }
   }

   @Subscribe
   public void onChannelGroupChanged(ChannelGroupChangedEvent channelGroupChangedEvent) {
      List<String> channelGroups  = new ArrayList<>();
      channelGroups.add("");
      for (String group : mmc_.getAvailableConfigGroups()) {
         channelGroups.add(group);
      }
      groupComboBox_.setModel(new DefaultComboBoxModel<String>(channelGroups.toArray(
              new String[]{""})));
      groupComboBox_.setSelectedItem(channelGroupChangedEvent.getNewChannelGroup());
   }

}
