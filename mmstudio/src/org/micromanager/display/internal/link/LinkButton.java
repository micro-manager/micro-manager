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
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;

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
      // platforms set isPopupTrigger() = true on different events.
      addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            if (event.isPopupTrigger()) {
               finalThis.showLinkMenu(event.getPoint());
            }
         }
         @Override
         public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger()) {
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
      List<DisplayWindow> displays = MMStudio.getInstance().display().getAllImageWindows();
      int numItems = 0;
      for (final DisplayWindow display : displays) {
         // TODO: make this a JCheckBoxMenuItem to reflect if the display
         // is already linked.
         String title = display.getDatastore().getSummaryMetadata().getFileName();
         if (title == null) {
            title = display.getImageWindow().getTitle();
         }
         JMenuItem item = new JMenuItem(title);
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//             display_.postEvent(new PushLinkEvent(display,
//                   linker_, true));
            }
         });
         menu.add(item);
         numItems++;
      }
      if (numItems > 0) {
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
