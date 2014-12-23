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
   private static final ImageIcon LINK_ICON = SwingResourceManager.getIcon(
         LinkButton.class, "/org/micromanager/icons/linkflat30.png");
   public LinkButton() {
      super(LINK_ICON);
      setMinimumSize(new Dimension(1, 1));
      setMargin(new Insets(0, 0, 0, 0));
   }

   public Dimension getPreferredSize() {
      return new Dimension(LINK_ICON.getIconWidth() + 2,
            LINK_ICON.getIconHeight() + 2);
   }
}
