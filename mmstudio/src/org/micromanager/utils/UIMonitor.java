/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 *
 * @author Arthur
 */
public class UIMonitor {

   private static AWTEventListener clickListener_ = null;

   public static void enableUIMonitor() {
      if (clickListener_ != null) {
         return;
      }
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      clickListener_ = new AWTEventListener() {
         @Override
         public void eventDispatched(AWTEvent event) {
            MouseEvent mouseEvent = (MouseEvent) event;
            String message = "";
            if (event.getID() == MouseEvent.MOUSE_CLICKED) {
               Object source = mouseEvent.getSource();
               if (source instanceof AbstractButton) {
                  AbstractButton button = (AbstractButton) source;
                  message += "\n\"" + button.getText() + "\" ";
                  boolean selectable = (source instanceof JToggleButton)
                             || (source instanceof JMenuItem);
                  if (selectable) { 
                    message += "toggled " + (button.isSelected() ? "on " : "off ");
                  } else {
                    message += "clicked ";
                  }
               }
               if (!message.isEmpty()) {
                  message += "in " + ((Window) SwingUtilities.getAncestorOfClass(Window.class, (JComponent) source)).getClass().getSimpleName() + ".";
                  ReportingUtils.logMessage(message);
               }            
            }
         }
      };
      toolkit.addAWTEventListener(clickListener_, AWTEvent.MOUSE_EVENT_MASK);
   }

   public static void disableUIMonitor() {
      Toolkit.getDefaultToolkit().removeAWTEventListener(clickListener_);
      clickListener_ = null;
   }
}
