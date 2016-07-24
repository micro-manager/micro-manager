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

import com.bulenkov.iconloader.IconLoader;

import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import org.micromanager.acquisition.internal.AcquisitionDialogPlugin;
import org.micromanager.internal.pluginmanagement.DefaultPluginManager;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.Studio;

/**
 * This class provides a JButton that accesses the acquisition dialog -- or,
 * if there is more than one such dialog available, provides a popup menu
 * of dialogs to choose from.
 */
public class AcquisitionSelector {

   public static JComponent makeSelector(Studio studio) {
      // This requires access to non-API methods.
      DefaultPluginManager pluginMan = (DefaultPluginManager) studio.plugins();
      final HashMap<String, AcquisitionDialogPlugin> plugins = pluginMan.getAcquisitionDialogPlugins();
      final JButton button = new JButton();
      button.setMargin(new Insets(0, 0, 0, 0));
      button.setFont(GUIUtils.buttonFont);
      if (plugins.size() == 1) {
         // Button to activate the single plugin.
         final AcquisitionDialogPlugin plugin = plugins.values().iterator().next();
         button.setText(plugin.getName());
         button.setToolTipText(plugin.getHelpText());
         button.setIcon(plugin.getIcon());
         button.setToolTipText(plugin.getHelpText());
         button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               plugin.showAcquisitionDialog();
            }
         });
         return button;
      }
      else {
         // Button to show a popup menu selecting from the available plugins
         button.setText("Acquire data");
         button.setToolTipText("Show various data acquisition dialogs");
         button.setIcon(IconLoader.getIcon("/org/micromanager/icons/film.png"));
         button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
               showPopupMenu(button, plugins, e);
            }
         });
         return button;
      }
   }

   /**
    * Show a popup menu allowing the user to select which plugin to run.
    */
   private static void showPopupMenu(JComponent parent,
         final HashMap<String, AcquisitionDialogPlugin> plugins,
         MouseEvent event) {
      JPopupMenu menu = new JPopupMenu();
      ArrayList<String> names = new ArrayList<String>(plugins.keySet());
      Collections.sort(names);
      for (final String name : names) {
         JMenuItem item = new JMenuItem(plugins.get(name).getName(),
               plugins.get(name).getIcon());
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               plugins.get(name).showAcquisitionDialog();
            }
         });
         menu.add(item);
      }
      menu.show(parent, event.getX(), event.getY());
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
         setBackground(isSelected ? DaytimeNighttime.getInstance().getLightBackgroundColor() : DaytimeNighttime.getInstance().getBackgroundColor());
         return this;
      }
   }
}
