///////////////////////////////////////////////////////////////////////////////
//
// AUTHOR:       Chris Weisiger, January 2016
//
// COPYRIGHT:    Open Imaging, Inc. 2016
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

package org.micromanager.acquisition.internal;

import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import org.micromanager.acquisition.AcquisitionDialogPlugin;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.Studio;

/**
 * This class provides a JComboBox for selecting from the various available
 * AcquisitionDialogPlugins, and a button for displaying the selected plugin.
 * Or, if there is only one AcquisitionDialogPlugin, it provides a button for
 * activating just that one plugin.
 */
public class AcquisitionSelector {

   public static JComponent makeSelector(Studio studio) {
      final HashMap<String, AcquisitionDialogPlugin> plugins = studio.plugins().getAcquisitionDialogPlugins();
      if (plugins.size() == 1) {
         // Button to activate the single plugin.
         final AcquisitionDialogPlugin plugin = plugins.values().iterator().next();
         JButton button = new JButton(plugin.getName(), plugin.getIcon());
         button.setMargin(new Insets(0, 0, 0, 0));
         button.setFont(GUIUtils.buttonFont);
         button.setToolTipText(plugin.getHelpText());
         button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               plugin.onPluginSelected();
            }
         });
         return button;
      }
      else {
         // Combobox to select from the available plugins.
         final HashMap<JLabel, String> itemToName = new HashMap<JLabel, String>();
         ArrayList<String> names = new ArrayList<String>(plugins.keySet());
         Collections.sort(names);
         Vector<JLabel> labels = new Vector<JLabel>();
         for (String name : names) {
            studio.logs().logError("Adding acq dialog " + name);
            ImageIcon icon = plugins.get(name).getIcon();
            JLabel label = new JLabel(plugins.get(name).getName(),
                  plugins.get(name).getIcon(), SwingConstants.LEFT);
            label.setFont(GUIUtils.buttonFont);
            labels.add(label);
            itemToName.put(label, name);
         }
         final JComboBox selector = new JComboBox(labels);
         selector.setRenderer(new PluginRenderer());
         selector.setFont(GUIUtils.buttonFont);
         // TODO selector should remember user's preference.
         selector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               JLabel item = (JLabel) selector.getSelectedItem();
               plugins.get(itemToName.get(item)).onPluginSelected();
            }
         });
         return selector;
      }
   }

   /**
    * Simple renderer for showing strings with icons.
    */
   private static class PluginRenderer extends JLabel implements ListCellRenderer {
      public PluginRenderer() {
         super();
         setOpaque(true);
      }
      @Override
      public Component getListCellRendererComponent(JList list,
            Object value, int index, boolean isSelected, boolean hasFocus) {
         JLabel label = (JLabel) value;
         setText(label.getText());
         setIcon(label.getIcon());
         setFont(label.getFont());
         // TODO this color is wrong in daytime mode.
         setBackground(isSelected ? DaytimeNighttime.getLightBackgroundColor() : DaytimeNighttime.getBackgroundColor());
         return this;
      }
   }
}
