package org.micromanager.imagedisplay;

import com.swtdesigner.SwingResourceManager;

import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

/**
 * This class provides the GUI for a button that links DisplaySettings
 * attributes across multiple DisplayWindows.
 */
public class LinkButton extends JToggleButton {
   // This icon is courtesy of
   // http://icon-park.com/icon/black-link-icon-vector-data/
   private static final ImageIcon LINK_ICON = SwingResourceManager.getIcon(
         LinkButton.class, "/org/micromanager/icons/linkflat.png");
   public LinkButton() {
      super(LINK_ICON);
      setMinimumSize(new Dimension(1, 1));
      setMargin(new Insets(0, 0, 0, 0));
   }

   /**
    * We want to be slightly larger than the icon we contain.
    */
   public Dimension getPreferredSize() {
      return new Dimension(LINK_ICON.getIconWidth() + 6,
            LINK_ICON.getIconHeight() + 2);
   }
}
