///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
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

package org.micromanager.display.internal.gearmenu;

import com.bulenkov.iconloader.IconLoader;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.inspector.InspectorFrame;
import org.micromanager.Studio;

import org.micromanager.internal.utils.SortedPopupMenu;

/**
 * This class provides access to various rarely-used functions (like save or
 * duplicate) via a dropdown menu.
 */
public class GearButton extends JButton {
   private SortedPopupMenu menu_;

   public GearButton(final DisplayWindow display, Studio studio) {
      setToolTipText("Access additional commands");
      menu_ = new SortedPopupMenu();
      JMenuItem openInspector = new JMenuItem("New Inspector Window");
      openInspector.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            InspectorFrame.createInspector(display);
         }
      });
      menu_.addUnsorted(openInspector);

      JMenuItem duplicate = new JMenuItem("New Window For This Data");
      duplicate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display.duplicate();
         }
      });
      menu_.addUnsorted(duplicate);

      menu_.addSeparator();

      // Insert plugins. Sorted alphabetically by name/submenu name.
      HashMap<String, DisplayGearMenuPlugin> plugins = studio.plugins().getDisplayGearMenuPlugins();
      HashMap<String, SortedPopupMenu> subMenus = new HashMap<String, SortedPopupMenu>();
      ArrayList<JMenuItem> items = new ArrayList<JMenuItem>();
      for (final DisplayGearMenuPlugin plugin : plugins.values()) {
         JMenuItem item = new JMenuItem(plugin.getName());
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               plugin.onPluginSelected(display);
            }
         });
         String subMenu = plugin.getSubMenu();
         if (subMenu.contentEquals("")) {
            // Add directly to the base menu.
            menu_.add(item);
         }
         else {
            // Add it to a submenu, creating it if necessary.
            if (!subMenus.containsKey(subMenu)) {
               SortedPopupMenu popup = new SortedPopupMenu();
               subMenus.put(subMenu, popup);
            }
            subMenus.get(subMenu).add(item);
         }
      }

      final JButton staticThis = this;
      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            menu_.show(staticThis, e.getX(), e.getY());
         }
      });

      // This icon adapted from the public domain icon at
      // https://openclipart.org/detail/35533/tango-emblem-system
      setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/gear.png"));
   }
}
