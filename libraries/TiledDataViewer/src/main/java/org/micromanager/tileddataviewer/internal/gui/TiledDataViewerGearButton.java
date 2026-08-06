///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    Regents of the University of California, 2026
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

package org.micromanager.tileddataviewer.internal.gui;

import com.bulenkov.iconloader.IconLoader;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.event.MouseInputAdapter;
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerGearMenuPlugin;
import org.micromanager.internal.utils.SortedMenu;
import org.micromanager.internal.utils.SortedPopupMenu;

/**
 * The gear menu of a TiledDataViewer window, mirroring the one in the main
 * Micro-Manager viewer.
 *
 * <p>The main viewer's {@code GearButton} takes a {@code DisplayWindow}, which a
 * TiledDataViewer is not, so this is a parallel implementation driven by
 * {@link DataViewerGearMenuPlugin} instead of {@code DisplayGearMenuPlugin}.
 */
public final class TiledDataViewerGearButton extends JButton {

   private SortedPopupMenu menu_;
   private MouseInputAdapter mouseAdapter_;

   /**
    * Builds the gear button for the given viewer.
    *
    * @param viewer viewer whose gear menu this is
    * @param studio the Studio, used to discover plugins and open the Inspector
    */
   public TiledDataViewerGearButton(final DataViewer viewer, final Studio studio) {
      menu_ = new SortedPopupMenu();

      JMenuItem openInspector = new JMenuItem("Image Inspector...");
      openInspector.addActionListener(
            e -> studio.displays().createInspectorForDataViewer(viewer));
      menu_.addUnsorted(openInspector);

      menu_.addSeparator();

      // Insert applicable plugins, sorted alphabetically by name / submenu name.
      HashMap<String, DataViewerGearMenuPlugin> plugins =
            studio.plugins().getDataViewerGearMenuPlugins();
      HashMap<String, SortedMenu> subMenus = new HashMap<>();
      for (final DataViewerGearMenuPlugin plugin : plugins.values()) {
         if (!plugin.isApplicableToDataViewer(viewer)) {
            continue;
         }
         JMenuItem item = new JMenuItem(plugin.getName());
         item.addActionListener(e -> plugin.onPluginSelected(viewer));
         String subMenu = plugin.getSubMenu();
         if (subMenu.contentEquals("")) {
            menu_.add(item);
         } else {
            if (!subMenus.containsKey(subMenu)) {
               SortedMenu sub = new SortedMenu(subMenu);
               subMenus.put(subMenu, sub);
               menu_.add(sub);
            }
            subMenus.get(subMenu).add(item);
         }
      }

      final JButton staticThis = this;
      mouseAdapter_ = new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            if (menu_ != null) {
               menu_.show(staticThis, e.getX(), e.getY());
            }
         }
      };
      super.addMouseListener(mouseAdapter_);

      // This icon adapted from the public domain icon at
      // https://openclipart.org/detail/35533/tango-emblem-system
      super.setIcon(IconLoader.getIcon("/org/micromanager/icons/gear.png"));
      super.setToolTipText("Additional viewer commands");
   }

   /**
    * Releases the menu and its listeners. Called when the display closes.
    */
   public void cleanup() {
      if (menu_ != null) {
         for (MenuElement element : menu_.getSubElements()) {
            if (element instanceof JMenuItem) {
               JMenuItem curItem = (JMenuItem) element;
               for (ActionListener al : curItem.getActionListeners()) {
                  curItem.removeActionListener(al);
               }
            }
         }
         menu_.removeAll();
         menu_ = null;
      }
      if (mouseAdapter_ != null) {
         // Remove the adapter we actually registered; the main viewer's GearButton
         // constructs a fresh one here and so removes nothing.
         super.removeMouseListener(mouseAdapter_);
         mouseAdapter_ = null;
      }
   }
}
