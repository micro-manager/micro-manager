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

package org.micromanager.quickaccess.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;
import java.awt.Frame;

import java.util.Arrays;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.utils.GUIUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Preset" button logic, allowing the user to quickly set a
 * configuration group to be at a desired preset.
 */
@Plugin(type = WidgetPlugin.class)
public class PresetButton extends WidgetPlugin implements SciJavaPlugin {
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
      return null;
   }

   @Override
   public JComponent createControl(PropertyMap config) {
      final String group = config.getString("configGroup", null);
      // This preset name is solely for purposes of setting up the icon in
      // configure mode.
      final String preset = config.getString("presetName", "GFP");
      JButton result = new JButton(preset,
            IconLoader.getIcon("/org/micromanager/icons/color_filter.png"));
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            try {
               studio_.core().setConfig(group, preset);
               studio_.compat().refreshGUI();
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
   public PropertyMap configureControl(Frame parent) {
      JPanel contents = new JPanel(new MigLayout("flowy"));
      contents.add(new JLabel("Select which configuration group and preset to set when the button is clicked."));

      contents.add(new JLabel("Config group: "), "split 2, flowx");
      final String[] groups = studio_.core().getAvailableConfigGroups().toArray();
      final JComboBox groupSelector = new JComboBox(groups);
      contents.add(groupSelector);

      contents.add(new JLabel("Config preset: "), "split 2, flowx");
      final JComboBox presetSelector = new JComboBox();
      contents.add(presetSelector);

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
         }
      });
      // Default to the Channel group, if available.
      for (String group : groups) {
         if (group.toLowerCase().contains("channel")) {
            groupSelector.setSelectedItem(group);
            break;
         }
      }

      JOptionPane.showMessageDialog(parent, contents,
            "Configure presets button", JOptionPane.PLAIN_MESSAGE);

      return studio_.data().getPropertyMapBuilder()
         .putString("configGroup", (String) groupSelector.getSelectedItem())
         .putString("presetName", (String) presetSelector.getSelectedItem())
         .build();
   }
}
