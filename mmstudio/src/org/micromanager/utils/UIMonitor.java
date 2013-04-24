/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 *
 * @author Arthur
 */
public class UIMonitor {

   private static AWTEventListener clickListener_ = null;

   private static String getComponentIdentifier(Component component) {
      if (component instanceof AbstractButton) {
         return ((AbstractButton) component).getText();
      } else if (component instanceof Button) {
         return ((Button) component).getLabel();
      } else {
         return "";
      }
   }

   private static String getActionText(Component component) {
      if (component instanceof JToggleButton) {
         return "toggled " + (((JToggleButton) component).isSelected() ? "on" : "off");
      } else if (component instanceof JCheckBoxMenuItem){
         return "toggled " + (((JCheckBoxMenuItem) component).isSelected() ? "on" : "off");
      } else {
         return "clicked";
      }
   }

   public static void disableUIMonitor() {
      Toolkit.getDefaultToolkit().removeAWTEventListener(clickListener_);
      clickListener_ = null;
   }

   public static void enableUIMonitor() {
      if (clickListener_ != null) {
         return;
      }
      clickListener_ = new AWTEventListener() {
         @Override
         public void eventDispatched(AWTEvent event) {
            MouseEvent mouseEvent = (MouseEvent) event;
            String message = "";
            if (event.getID() == MouseEvent.MOUSE_CLICKED) {
               Object source = mouseEvent.getSource();
               if (source instanceof Component) {
                     Component component = (Component) source;
                     String text = component.getName();
                     if (text == null || text.isEmpty()) {
                        text = getComponentIdentifier(component);
                     }
                     if (!text.isEmpty()) {
                        text = "\"" + text + "\" ";
                     }
                     message += "\n" + source.getClass().getSimpleName() + " " + text + getActionText(component) + " ";
                  }
                  if (!message.isEmpty()) {
                     message += "in " + ((Window) SwingUtilities.getAncestorOfClass(Window.class, (Component) source)).getClass().getSimpleName() + ".";
                     ReportingUtils.logMessage(message);
                  }
               }

         }
      };
      Toolkit.getDefaultToolkit().addAWTEventListener(clickListener_, AWTEvent.MOUSE_EVENT_MASK);
   }
}
