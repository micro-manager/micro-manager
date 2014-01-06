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

import com.swtdesigner.SwingResourceManager;
import ij.WindowManager;
import ij.gui.ImageWindow;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import java.util.prefs.Preferences;
import javax.swing.*;



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

        /**
     * Verifies if the given point is visible on the screen.
     * From http://www.java2s.com/Code/Java/Swing-JFC/Verifiesifthegivenpointisvisibleonthescreen.htm
     * @param   location     The given location on the screen.
     * @return           True if the location is on the screen, false otherwise.
     */
    public static boolean isLocationInScreenBounds(Point location) 
    {
      
      // Check if the location is in the bounds of one of the graphics devices.
    GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] graphicsDevices = graphicsEnvironment.getScreenDevices();
    Rectangle graphicsConfigurationBounds = new Rectangle();
    
    // Iterate over the graphics devices.
    for (int j = 0; j < graphicsDevices.length; j++) {
      
      // Get the bounds of the device.
      GraphicsDevice graphicsDevice = graphicsDevices[j];
      graphicsConfigurationBounds.setRect(graphicsDevice.getDefaultConfiguration().getBounds());
      
        // Is the location in this bounds?
      graphicsConfigurationBounds.setRect(graphicsConfigurationBounds.x, graphicsConfigurationBounds.y,
          graphicsConfigurationBounds.width, graphicsConfigurationBounds.height);
      if (graphicsConfigurationBounds.contains(location.x, location.y)) {
        
        // The location is in this screengraphics.
        return true;
        
      }
      
    }
    
    // We could not find a device that contains the given point.
    return false;
    
    }
    
   public static void recallPosition(final Window win) {
      Preferences prefs = Preferences.userNodeForPackage(win.getClass());
      Point dialogPosition = (Point) JavaUtils.getObjectFromPrefs(prefs, DIALOG_POSITION, null);
      if (dialogPosition == null || !isLocationInScreenBounds(dialogPosition)) {
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

   private static void storePosition(final Window win) {
      Preferences prefs = Preferences.userNodeForPackage(win.getClass());
      JavaUtils.putObjectInPrefs(prefs, DIALOG_POSITION, win.getLocation());
   }

   public static void registerImageFocusListener(final ImageFocusListener listener) {
      AWTEventListener awtEventListener = new AWTEventListener() {
         private ImageWindow currentImageWindow_ = null;
         @Override
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

   /*
    * Wraps SwingUtilities.invokeAndWait so that if it is being called
    * from the EDT, then the runnable is simply run.
    */
   public static void invokeAndWait(Runnable r) throws InterruptedException, InvocationTargetException {
      if (SwingUtilities.isEventDispatchThread()) {
         r.run();
      } else {
         SwingUtilities.invokeAndWait(r);
      }
   }

      /*
    * Wraps SwingUtilities.invokeLater so that if it is being called
    * from the EDT, then the runnable is simply run.
    */
   public static void invokeLater(Runnable r) throws InterruptedException, InvocationTargetException {
      if (SwingUtilities.isEventDispatchThread()) {
         r.run();
      } else {
         SwingUtilities.invokeLater(r);
      }
   }
   
   
   
       
   /* 
    * Attach properly formatted tooltip text to the specified
    * JComponent.
    */
   public static void setToolTipText(JComponent component,
            String toolTipText) {
      if (JavaUtils.isMac()) {// running on a mac
         component.setToolTipText(toolTipText);
      }
      else {
         component.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(toolTipText));
      }
    }
    
   /*
    * Add an icon from the "org/micromanager/icons/ folder with
    * given file name, to specified the button or menu.
    */
   public static void setIcon(AbstractButton component, String iconFileName) {
      component.setIcon(SwingResourceManager.getIcon(
              org.micromanager.MMStudioMainFrame.class,
              "/org/micromanager/icons/" + iconFileName));
   }
   
      
   /////////////////////// MENU ITEM UTILITY METHODS ///////////
   
   /*
    * Add a menu to the specified menu bar.
    */
   public static JMenu createMenuInMenuBar(final JMenuBar menuBar, final String menuName) {
      final JMenu menu = new JMenu();
      menu.setText(menuName);
      menuBar.add(menu);
      return menu;
   }

    /*
     * Add a menu item to the specified parent menu.
     */
   public static JMenuItem addMenuItem(final JMenu parentMenu,
            JMenuItem menuItem,
            final String menuItemToolTip,
           final Runnable menuActionRunnable) {
      if (menuItemToolTip != null) {
         setToolTipText(menuItem, menuItemToolTip);
      }
      if (menuActionRunnable != null) {
         menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ignoreEvent) {
               menuActionRunnable.run();
            }
         });
      }
      parentMenu.add(menuItem);
      return menuItem;
   }
   
   /*
    * Add a menu item with given text to the specified parent menu.
    */
   public static JMenuItem addMenuItem(final JMenu parentMenu,
           final String menuItemText,
           final String menuItemToolTip,
           final Runnable menuActionRunnable) {
      return addMenuItem(parentMenu, new JMenuItem(menuItemText),
              menuItemToolTip, menuActionRunnable);      
   }
   
   public static JMenuItem addMenuItem(final JMenu parentMenu,
           final String menuItemText,
           final String menuItemToolTip,
           final Runnable menuActionRunnable,
           final String iconFileName) {
      final JMenuItem menuItem = addMenuItem(parentMenu,
              menuItemText, menuItemToolTip, menuActionRunnable);
      setIcon(menuItem, iconFileName);
      return menuItem;
   }
  
   /*
    * Add a menu item that can be checked or unchecked to the specified
    * parent menu.
    */
   public static JCheckBoxMenuItem addCheckBoxMenuItem(final JMenu parentMenu,
           final String menuItemText,
           final String menuItemToolTip,
           final Runnable menuActionRunnable,
           final boolean initState) {
      final JCheckBoxMenuItem menuItem = (JCheckBoxMenuItem)
              addMenuItem(parentMenu, new JCheckBoxMenuItem(menuItemText),
              menuItemToolTip, menuActionRunnable);
      menuItem.setSelected(initState);
      return menuItem;
   }
   
   ////////////// END MENU ITEM UTILITY METHODS ////////////////
   
    /* Add a component to the parent panel, set positions of the edges of
     * component relative to panel. If edges are positive, then they are
     * positioned relative to north and west edges of parent container. If edges
     * are negative, then they are positioned relative to south and east
     * edges of parent container.
     * Requires that parent container uses SpringLayout.
     */
   public static void addWithEdges(Container parentContainer, JComponent component, int west, int north, int east, int south) {
      parentContainer.add(component);
      SpringLayout topLayout = (SpringLayout) parentContainer.getLayout();
      topLayout.putConstraint(SpringLayout.EAST, component, east,
              (east > 0) ? SpringLayout.WEST : SpringLayout.EAST, parentContainer);
      topLayout.putConstraint(SpringLayout.WEST, component, west,
              (west >= 0) ? SpringLayout.WEST : SpringLayout.EAST, parentContainer);
      topLayout.putConstraint(SpringLayout.SOUTH, component, south,
              (south > 0) ? SpringLayout.NORTH : SpringLayout.SOUTH, parentContainer);
      topLayout.putConstraint(SpringLayout.NORTH, component, north,
              (north >= 0) ? SpringLayout.NORTH : SpringLayout.SOUTH, parentContainer);
   }
   


   
    /* Add a component to the parent panel, set positions of the edges of
     * component relative to panel. If edges are positive, then they are
     * positioned relative to north and west edges of parent container. If edges
     * are negative, then they are positioned relative to south and east
     * edges of parent container.
     * Requires that parent container uses SpringLayout.
     */

   public static AbstractButton createButton(final boolean isToggleButton,
           final String name,
           final String text,
           final String toolTipText,
           final Runnable buttonActionRunnable,
           final String iconFileName,
           final Container parentPanel,
           int west, int north, int east, int south) {
      AbstractButton button = isToggleButton ? new JToggleButton() : new JButton();
      button.setFont(new Font("Arial", Font.PLAIN, 10));
      button.setMargin(new Insets(0, 0, 0, 0));
      button.setName(name);
      if (text != null) {
         button.setText(text);
      }
      if (iconFileName != null) {
         button.setIconTextGap(4);
         setIcon(button, iconFileName);
      }
      if (toolTipText != null) {
         button.setToolTipText(toolTipText);
      }
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            buttonActionRunnable.run();
         }
      });
      GUIUtils.addWithEdges(parentPanel, button, west, north, east, south);
      return button;
   }
   
}