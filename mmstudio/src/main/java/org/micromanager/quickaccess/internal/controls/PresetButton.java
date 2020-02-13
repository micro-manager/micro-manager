///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.quickaccess.internal.controls;

import com.bulenkov.iconloader.IconLoader;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.display.internal.RememberedSettings;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Preset" button logic, allowing the user to quickly set a
 * configuration group to be at a desired preset.
 */
@Plugin(type = WidgetPlugin.class)
public final class PresetButton extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Set Preset";
   }

   @Override
   public String getHelpText() {
      return "Set the preset for a selected configuration group.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Open Imaging, Inc.";
   }

   @Override
   public ImageIcon getIcon() {
      return new ImageIcon(IconLoader.loadFromResource(
               "/org/micromanager/icons/color_filter@2x.png"));
   }

   @Override
   public JComponent createControl(final PropertyMap config) {
      final String group = config.getString("configGroup", null);
      // This preset name is solely for purposes of setting up the icon in
      // configure mode.
      final String preset = config.getString("presetName", "GFP");
      Icon icon = studio_.quickAccess().getCustomIcon(config,
            IconLoader.getIcon("/org/micromanager/icons/color_filter.png"));
      JButton result = new JButton(preset, icon) {
         @Override
         public Dimension getPreferredSize() {
            // For iconized mode, we want a smaller button.
            if (config.getString("configGroup", null) == null) {
               return super.getPreferredSize();
            }
            return QuickAccessPlugin.getPaddedCellSize();
         }
      };
      result.setOpaque(true);
      result.setBackground(new Color(config.getInteger("backgroundColor",
                  Color.GREEN.getRGB())));
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            try {
               studio_.core().setConfig(group, preset);
               studio_.core().waitForConfig(group, preset);
               studio_.app().refreshGUIFromCache();
            }
            catch (Exception e) {
               studio_.logs().showError(e, "Error setting config group " +
                  group + " to mode " + preset);
            }
         }
      });
      return result;
   }

   @Override
   public PropertyMap configureControl(final Frame parent) {
      JPanel contents = new JPanel(new MigLayout("flowx"));
      contents.add(new JLabel("Select which configuration group and preset to set when the button is clicked."), "span, wrap");

      // Changing the dropdowns may change the color of this label, so we need
      // to create it now before we specify dropdown behavior.
      final JLabel pickerLabel = new JLabel();

      contents.add(new JLabel("Config group: "));
      final String[] groups = studio_.core().getAvailableConfigGroups().toArray();
      if (groups.length == 0) {
         JOptionPane.showMessageDialog(parent,
               "There are no configuration groups available. Please create at least one configuration group before using this control.",
               "No configuration groups found",
               JOptionPane.ERROR_MESSAGE);
         return null;
      }
      final JComboBox groupSelector = new JComboBox(groups);
      contents.add(groupSelector, "wrap");

      contents.add(new JLabel("Config preset: "));
      final JComboBox presetSelector = new JComboBox();
      contents.add(presetSelector, "wrap");

      groupSelector.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            // Populate the presets dropdown.
            String group = (String) groupSelector.getSelectedItem();
            String[] presets = studio_.core().getAvailableConfigs(group)
                  .toArray();
            presetSelector.removeAllItems();
            for (String preset : presets) {
               presetSelector.addItem(preset);
            }
            try {
               String curPreset = studio_.core().getCurrentConfig(group);
               presetSelector.setSelectedItem(curPreset);
            }
            catch (Exception e) {
               studio_.logs().logError(e, "Invalid configuration group name " + group);
            }
            // Try to get a new color for the color picker. This won't do
            // anything useful when not dealing with channel groups, in which
            // case the color will remain the same.
            pickerLabel.setBackground(
                    RememberedSettings.loadChannel(studio_, 
                            (String) groupSelector.getSelectedItem(), 
                            (String) presetSelector.getSelectedItem()).getColor());
         }
      });

      presetSelector.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            // Try to get a new color for the color picker. This won't do
            // anything useful when not dealing with channel groups, in which
            // case the color will remain the same.
            pickerLabel.setBackground(
               RememberedSettings.loadChannel(studio_, 
                            (String) groupSelector.getSelectedItem(), 
                            (String) presetSelector.getSelectedItem()).getColor());
         }
      });
      // Default to the Channel group, if available.
      boolean didSetDefault = false;
      for (String group : groups) {
         if (group.toLowerCase().contains("channel")) {
            groupSelector.setSelectedItem(group);
            try {
               presetSelector.setSelectedItem(studio_.core().getCurrentConfig(group));
               didSetDefault = true;
            }
            catch (Exception e) {
               studio_.logs().logError(e, "Unable to get config for group " + group);
            }
            break;
         }
      }
      if (!didSetDefault) {
         // Couldn't find a channel group, but we need to select *something*
         // or else the presets dropdown will be invalid.
         groupSelector.setSelectedItem(groups[0]);
      }

      contents.add(new JLabel("Background color: "));
      pickerLabel.setOpaque(true);
      pickerLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
      pickerLabel.setMinimumSize(new Dimension(20, 20));
      pickerLabel.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            Color newColor = JColorChooser.showDialog(parent,
               "Choose a background color for this preset.",
               pickerLabel.getBackground());
            pickerLabel.setBackground(newColor);
         }
      });
      contents.add(pickerLabel, "wrap");

      JOptionPane.showMessageDialog(parent, contents,
            "Configure presets button", JOptionPane.PLAIN_MESSAGE);

      return PropertyMaps.builder()
         .putString("configGroup", (String) groupSelector.getSelectedItem())
         .putString("presetName", (String) presetSelector.getSelectedItem())
         .putInteger("backgroundColor", pickerLabel.getBackground().getRGB())
         .build();
   }

   @Override
   public boolean getCanCustomizeIcon() {
      return true;
   }
}
