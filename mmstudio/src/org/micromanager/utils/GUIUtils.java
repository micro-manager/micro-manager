///////////////////////////////////////////////////////////////////////////////
//FILE:          GUIUtils.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, 2005
//
// COPYRIGHT:    University of California San Francisco, 2005
//               100X Imaging Inc, 2009
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
//

package org.micromanager.utils;

import ij.WindowManager;
import ij.gui.ImageWindow;
import java.awt.AWTEvent;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;

import javax.swing.JTable;



public class GUIUtils {
   private static String DIALOG_POSITION = "dialogPosition";

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
   

   /* 
    * This takes care of a Java bug that would throw several exceptions when a Projector device 
    * is attached in Windows.
    */
   public static void preventDisplayAdapterChangeExceptions() {
	   try {
		   if (JavaUtils.isWindows()) { // Check that we are in windows.
			   //Dynamically load sun.awt.Win32GraphicsEnvironment, because it seems to be missing from
			   //the Mac OS X JVM.
			   ClassLoader cl = ClassLoader.getSystemClassLoader ();
			   Class<?> envClass = cl.loadClass("sun.awt.Win32GraphicsEnvironment");
			   //Get the current local graphics environment.
			   GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			   //Send notification that display may have changed, so that display count is updated.
			   envClass.getDeclaredMethod("displayChanged").invoke(envClass.cast(ge));
		   }
	   } catch (Exception e) {
           ReportingUtils.logError(e);
	   }

   }

   public static void setClickCountToStartEditing(JTable table, int count) {
       ((DefaultCellEditor) table.getDefaultEditor(String.class)).setClickCountToStart(count);
   }

    public static void stopEditingOnLosingFocus(final JTable table) {
        table.addFocusListener(new FocusAdapter() {
         @Override
            public void focusLost(FocusEvent e) {
                Component focused = e.getOppositeComponent();
                try {
                  if (table!=focused && !table.isAncestorOf(focused))
                     table.getDefaultEditor(String.class).stopCellEditing();
                } catch (Exception ex) {
                   ReportingUtils.logError(ex);
                }
            }
        });
    }

   public static void recallPosition(final JFrame win) {
      Preferences prefs = Preferences.userNodeForPackage(win.getClass());
      Point dialogPosition = (Point) JavaUtils.getObjectFromPrefs(prefs, DIALOG_POSITION, null);
      if (dialogPosition == null) {
         Dimension screenDims = JavaUtils.getScreenDimensions();
         dialogPosition = new Point((screenDims.width - win.getWidth()) / 2, (screenDims.height - win.getHeight()) / 2);
      }
      win.setLocation(dialogPosition);

      win.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            storePosition(win);
         }
      });
   }

   private static void storePosition(JFrame win) {
      Preferences prefs = Preferences.userNodeForPackage(win.getClass());
      JavaUtils.putObjectInPrefs(prefs, DIALOG_POSITION, win.getLocation());
   }

   public static void registerImageFocusListener(final ImageFocusListener listener) {
      AWTEventListener awtEventListener = new AWTEventListener() {
         private ImageWindow currentImageWindow_ = null;
         public void eventDispatched(AWTEvent event) {
            if (event instanceof WindowEvent) {
               if (0 != (event.getID() & WindowEvent.WINDOW_GAINED_FOCUS)) {
                  if (event.getSource() instanceof ImageWindow) {
                     ImageWindow focusedWindow = WindowManager.getCurrentWindow();
                     if (currentImageWindow_ != focusedWindow) {
                        //if (focusedWindow.isVisible() && focusedWindow instanceof ImageWindow) {
                           listener.focusReceived(focusedWindow);
                           currentImageWindow_ = focusedWindow;
                        //}
                     }
                  }
               }
            }
         }
      };

      Toolkit.getDefaultToolkit().addAWTEventListener(awtEventListener,
              AWTEvent.WINDOW_FOCUS_EVENT_MASK);
   }
}
