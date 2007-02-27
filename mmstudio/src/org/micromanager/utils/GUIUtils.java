package org.micromanager.utils;

import java.awt.event.ActionListener;

import javax.swing.JComboBox;

public class GUIUtils {
   public static void setComboSelection(JComboBox cb, String sel){
      ActionListener[] listeners = cb.getActionListeners();
      for (int i=0; i<listeners.length; i++)            
         cb.removeActionListener(listeners[i]);
      cb.setSelectedItem(sel);
      for (int i=0; i<listeners.length; i++)            
         cb.addActionListener(listeners[i]);
   }

   public static void replaceComboContents(JComboBox cb, String[] items) {
      
      // remove listeners
      ActionListener[] listeners = cb.getActionListeners();
      for (int i=0; i<listeners.length; i++)            
         cb.removeActionListener(listeners[i]);

      if (cb.getItemCount() > 0)
         cb.removeAllItems();
      
      // add contents
      for (int i=0; i<items.length; i++){
         cb.addItem(items[i]);
      }
      
      // restore listeners
      for (int i=0; i<listeners.length; i++)            
         cb.addActionListener(listeners[i]);
   }
}
