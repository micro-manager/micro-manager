///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.pipelinesaver;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMFrame;

public class SaverConfigurator extends MMFrame implements ProcessorConfigurator {
   private static final String PREFERRED_FORMAT = "preferred format for saving mid-pipeline datasets";
   private static final String SHOULD_DISPLAY_PIPELINE_DATA = "whether or not to display mid-pipeline datasets";
   private static final String SAVE_PATH = "default save path for saving mid-pipeline datasets";

   private final Studio studio_;
   private final JCheckBox shouldDisplay_;
   private final JComboBox saveFormat_;
   private JTextField savePath_;
   private final JButton browseButton_;

   public SaverConfigurator(PropertyMap settings, Studio studio) {
      studio_ = studio;
      JPanel panel = new JPanel(new MigLayout("flowx"));
      panel.add(new JLabel("<html>This \"processor\" will save images at this point in the pipeline.</html>"), "span, wrap");

      panel.add(new JLabel("Save format: "), "split 2");
      String[] formats = new String[] {SaverPlugin.RAM,
         SaverPlugin.MULTIPAGE_TIFF, SaverPlugin.SINGLEPLANE_TIFF_SERIES};
      saveFormat_ = new JComboBox(formats);
      saveFormat_.setSelectedItem(
            settings.getString("format", getPreferredSaveFormat()));
      saveFormat_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateControls();
         }
      });
      panel.add(saveFormat_, "wrap");

      shouldDisplay_ = new JCheckBox("Display saved images in new window");
      shouldDisplay_.setSelected(
            settings.getBoolean("shouldDisplay", getShouldDisplay()));
      panel.add(shouldDisplay_, "wrap");

      panel.add(new JLabel("Save path: "), "wrap");
      savePath_ = new JTextField(30);
      savePath_.setText(settings.getString("savePath", getSavePath()));
      panel.add(savePath_, "split 2, span");
      browseButton_ = new JButton("...");
      browseButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Pop up a browse dialog.
            File path = FileDialogs.save(SaverConfigurator.this,
               "Please choose a directory to save to",
               FileDialogs.MM_DATA_SET);
            if (path != null) {
               savePath_.setText(path.getAbsolutePath());
            }
         }
      });
      panel.add(browseButton_, "wrap");
      super.add(panel);
      updateControls();

      super.loadAndRestorePosition(300, 300);
   }

   private void updateControls() {
      // Toggle availability of the save path controls.
      boolean isRAM = saveFormat_.getSelectedIndex() == 0;
      if (isRAM) {
         // Can't not display RAM data.
         shouldDisplay_.setSelected(true);
      }
      shouldDisplay_.setEnabled(!isRAM);
      savePath_.setEnabled(!isRAM);
      browseButton_.setEnabled(!isRAM);
   }

   @Override
   public void showGUI() {
      pack();
      setVisible(true);
   }

   @Override
   public PropertyMap getSettings() {
      // Save preferences now.
      String format = (String) saveFormat_.getSelectedItem();
      setPreferredSaveFormat(format);
      setShouldDisplay(shouldDisplay_.isSelected());
      setSavePath(savePath_.getText());
      PropertyMap.Builder builder = PropertyMaps.builder();
      builder.putString("format", format);
      builder.putBoolean("shouldDisplay", shouldDisplay_.isSelected());
      builder.putString("savePath", savePath_.getText());
      return builder.build();
   }

   @Override
   public void cleanup() {
      dispose();
   }

   private String getPreferredSaveFormat() {
      return studio_.profile().getSettings(SaverConfigurator.class).getString(
            PREFERRED_FORMAT, SaverPlugin.RAM);
   }

   private void setPreferredSaveFormat(String format) {
      studio_.profile().getSettings(SaverConfigurator.class).putString(
            PREFERRED_FORMAT, format);
   }

   private boolean getShouldDisplay() {
      return studio_.profile().getSettings(SaverConfigurator.class).getBoolean(
            SHOULD_DISPLAY_PIPELINE_DATA, true);
   }

   private void setShouldDisplay(boolean shouldDisplay) {
      studio_.profile().getSettings(SaverConfigurator.class).putBoolean(
            SHOULD_DISPLAY_PIPELINE_DATA, shouldDisplay);
   }

   private String getSavePath() {
      return studio_.profile().getSettings(SaverConfigurator.class).getString(
            SAVE_PATH, "");
   }

   private void setSavePath(String path) {
      studio_.profile().getSettings(SaverConfigurator.class).putString(
              SAVE_PATH, path);
   }
}
