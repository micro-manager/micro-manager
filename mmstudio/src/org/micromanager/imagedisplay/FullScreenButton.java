package org.micromanager.imagedisplay;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JToggleButton;

import org.micromanager.api.display.DisplayWindow;

/**
 * This class provides a GUI for turning fullscreen mode on and off.
 */
public class FullScreenButton extends JToggleButton {
   public FullScreenButton(final DisplayWindow display) {
      super("Fullscreen");

      addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            display.toggleFullScreen();
         }
      });
   }
}
