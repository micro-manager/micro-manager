package org.micromanager.imagedisplay.link;

import com.google.common.eventbus.EventBus;
import com.swtdesigner.SwingResourceManager;

import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

import org.micromanager.api.display.DisplayWindow;

/**
 * This class provides the GUI for a button that links DisplaySettings
 * attributes across multiple DisplayWindows.
 */
public class LinkButton extends JToggleButton {
   // This icon is a modified version of
   // http://icon-park.com/icon/black-link-icon-vector-data/
   private static final ImageIcon LINK_ICON = SwingResourceManager.getIcon(
         LinkButton.class, "/org/micromanager/icons/linkflat.png");

   public LinkButton(final SettingsLinker linker,
         final DisplayWindow display) {
      super(LINK_ICON);
      setMinimumSize(new Dimension(1, 1));
      setMargin(new Insets(0, 0, 0, 0));

      linker.setButton(this);
      final LinkButton finalThis = this;
      addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display.postEvent(new LinkButtonEvent(linker, display,
                  finalThis));
         }
      });
   }

   /**
    * We want to be slightly larger than the icon we contain.
    */
   public Dimension getPreferredSize() {
      return new Dimension(LINK_ICON.getIconWidth() + 6,
            LINK_ICON.getIconHeight() + 2);
   }
}
