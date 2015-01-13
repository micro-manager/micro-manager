package org.micromanager.display.internal.link;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

import org.micromanager.display.DisplayWindow;

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
      display_ = display;

      final LinkButton finalThis = this;
      addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display.postEvent(new LinkButtonEvent(linker, display,
                  finalThis.isSelected()));
         }
      });
      setToolTipText("Toggle linking of this control across all image windows for this dataset");
      display.registerForEvents(this);
      display.postEvent(new LinkButtonCreatedEvent(this, linker));
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
