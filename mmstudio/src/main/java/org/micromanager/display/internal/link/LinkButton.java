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

package org.micromanager.display.internal.link;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.DisplayWindow;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides the GUI for a button that links DisplaySettings
 * attributes across multiple DisplayWindows.
 */
public class LinkButton extends JButton {
   // These icons are modified versions of the public domain icon at
   // http://icon-park.com/icon/black-link-icon-vector-data/
   private static final Icon ACTIVE_ICON = IconLoader.getIcon(
                   "/org/micromanager/icons/linkflat_active.png");
   private static final Icon INACTIVE_ICON = IconLoader.getIcon(
                   "/org/micromanager/icons/linkflat.png");

   private SettingsLinker linker_;
   private final DisplayWindow display_;

   public LinkButton(final SettingsLinker linker,
         final DisplayWindow display) {
      super(INACTIVE_ICON);
      setMinimumSize(new Dimension(1, 1));
      setMargin(new Insets(0, 0, 0, 0));

      linker_ = linker;
      linker_.addButton(this);
      display_ = display;

      // Show a popup menu to manually link this to another display.
      addMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent event) {
            showLinkMenu(event.getPoint());
         }
      });
      setToolTipText("Toggle linking of this control across all image windows for this dataset. Right-click to push changes to a specific display.");
      DisplayGroupManager.getInstance().addNewLinker(linker, display_);
      display.registerForEvents(this);
   }

   /**
    * Pop up a menu to let the user manually link to a specific display.
    * @param p
    */
   public void showLinkMenu(Point p) {
      JPopupMenu menu = new JPopupMenu();
      // TODO: replace "this property" with a more descriptive phrase for the
      // given SettingsLinker.
      JMenuItem instructions = new JMenuItem(
            String.format("Link %s to other windows", linker_.getProperty()));
      instructions.setEnabled(false);
      menu.add(instructions);
      menu.addSeparator();

      if (linker_.getIsActive()) {
         JMenuItem removeItem = new JMenuItem("Unlink this window");
         removeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               linker_.desynchronize(linker_);
            }
         });
         menu.add(removeItem);
      }

      final List<SettingsLinker> siblings = linker_.getSortedSiblings();
      // If there are at least two potential siblings to link to, then add
      // an "All" item.
      if (siblings.size() > 1) {
         JMenuItem allItem = new JMenuItem("All applicable windows");
         allItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               for (SettingsLinker sibling : siblings) {
                  linker_.synchronize(sibling);
               }
            }
         });
         menu.add(allItem);
      }

      // If there are at least two siblings for the same dataset, then add an
      // "all for this data" option.
      final ArrayList<SettingsLinker> displaySiblings = new ArrayList<SettingsLinker>();
      for (SettingsLinker linker : siblings) {
         if (linker.getDisplay().getDatastore() == display_.getDatastore()) {
            displaySiblings.add(linker);
         }
      }
      if (displaySiblings.size() > 1) {
         JMenuItem relatedItem = new JMenuItem("All windows for this dataset");
         relatedItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               for (SettingsLinker sibling : displaySiblings) {
                  linker_.synchronize(sibling);
               }
            }
         });
         menu.add(relatedItem);
      }

      for (final SettingsLinker sibling : siblings) {
         final JCheckBoxMenuItem item = new JCheckBoxMenuItem(
               sibling.getDisplay().getName(),
               linker_.getIsSynchronized(sibling));
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (item.isSelected()) {
                  linker_.synchronize(sibling);
               }
               else {
                  // Already synchronized; desynch instead.
                  linker_.desynchronize(sibling);
               }
            }
         });
         menu.add(item);
      }
      if (siblings.size() > 0) { // i.e. more than just our own display
         menu.show(this, p.x, p.y);
      }
      else {
         ReportingUtils.logError("Somehow got a link menu when there's no siblings");
      }
   }

   /**
    * Toggle "active" status, by switching our icon.
    */
   public void setActive(boolean isActive) {
      setIcon(isActive ? ACTIVE_ICON : INACTIVE_ICON);
   }

   /**
    * We want to be slightly larger than the icon we contain.
    * @return 
    */
   @Override
   public Dimension getPreferredSize() {
      return new Dimension(ACTIVE_ICON.getIconWidth() + 6,
            ACTIVE_ICON.getIconHeight() + 2);
   }

   public DisplayWindow getDisplay() {
      return display_;
   }

   /**
    * We're about to go away, so make certain no trace of us remains.
    */
   public void cleanup() {
      display_.postEvent(new LinkerRemovedEvent(linker_));
   }

   /**
    * When a LinkButton on another display toggles, we need to also toggle
    * if their linker matches our own.
    * @param event
    */
   @Subscribe
   public void onRemoteLinkEvent(RemoteLinkEvent event) {
      try {
         if (event.getLinker().getID() == linker_.getID()) {
            setSelected(event.getIsLinked());
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Unable to respond to remote link event");
      }
   }

   /**
    * Unregister for events so that garbage collection can go through.
    * @param event
    */
   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      display_.unregisterForEvents(this);
      linker_.removeButton(this);
   }
}
