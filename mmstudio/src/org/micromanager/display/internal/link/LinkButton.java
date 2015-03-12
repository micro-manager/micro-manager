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

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Insets;
import java.awt.Point;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.micromanager.display.DisplayWindow;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides the GUI for a button that links DisplaySettings
 * attributes across multiple DisplayWindows.
 */
public class LinkButton extends JToggleButton {
   // This icon is a modified version of
   // http://icon-park.com/icon/black-link-icon-vector-data/
   private static final ImageIcon LINK_ICON = SwingResourceManager.getIcon(
         LinkButton.class, "/org/micromanager/internal/icons/linkflat.png");

   private SettingsLinker linker_;
   private DisplayWindow display_;

   public LinkButton(final SettingsLinker linker,
         final DisplayWindow display) {
      super(LINK_ICON);
      setMinimumSize(new Dimension(1, 1));
      setMargin(new Insets(0, 0, 0, 0));

      linker_ = linker;
      linker_.setButton(this);
      display_ = display;

      final LinkButton finalThis = this;
      addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            linker_.setIsActive(finalThis.isSelected());
         }
      });

      // On right-click, show a popup menu to manually link this to another
      // display. Note we track both pressed and released because different
      // platforms set isPopupTrigger() = true on different events. As for
      // SwingUtilities, it allows us to detect e.g. control-click on OSX
      // with single-button mice.
      // TODO: control-clicking still toggles the state of the button.
      addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            if (event.isPopupTrigger() ||
               SwingUtilities.isRightMouseButton(event)) {
               finalThis.showLinkMenu(event.getPoint());
            }
         }
         @Override
         public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger() ||
               SwingUtilities.isRightMouseButton(event)) {
               finalThis.showLinkMenu(event.getPoint());
            }
         }
      });
      setToolTipText("Toggle linking of this control across all image windows for this dataset. Right-click to push changes to a specific display.");
      display.registerForEvents(this);
      display.postEvent(new LinkButtonCreatedEvent(this, linker));
   }

   /**
    * Pop up a menu to let the user manually link to a specific display.
    */
   public void showLinkMenu(Point p) {
      JPopupMenu menu = new JPopupMenu();
      final List<DisplayWindow> displays = MMStudio.getInstance().displays().getAllImageWindows();
      if (displays.size() > 2) { // i.e. at least two potential candidates
         JMenuItem allItem = new JMenuItem("All");
         allItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               for (DisplayWindow display : displays) {
                  linker_.pushState(display);
               }
            }
         });
         menu.add(allItem);
      }
      for (final DisplayWindow display : displays) {
         if (display == display_) {
            continue;
         }
         JMenuItem item = new JMenuItem(display.getName());
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               linker_.pushState(display);
            }
         });
         menu.add(item);
      }
      if (displays.size() > 1) { // i.e. more than just our own display
         menu.show(this, p.x, p.y);
      }
   }

   /**
    * We want to be slightly larger than the icon we contain.
    */
   public Dimension getPreferredSize() {
      return new Dimension(LINK_ICON.getIconWidth() + 6,
            LINK_ICON.getIconHeight() + 2);
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
}
