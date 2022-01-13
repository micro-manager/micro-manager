
package org.micromanager.internal.menus;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;

/**
 * Class that creates and manages the Window menu in the Micro-Manager application.
 *
 * @author Nico Stuurman
 */
public class WindowMenu {
   private final MMStudio studio_;
   private final JMenu windowMenu_;
   private final List<JMenuItem> menuItems_;

   /**
    * Create the Window menu.
    *
    * @param studio The omnipresent Micro-Manager Studio object
    * @param menuBar The menubar to which this WindowMenu belongs
    */
   public WindowMenu(MMStudio studio, JMenuBar menuBar) {
      studio_ = studio;
      menuItems_ = new ArrayList<>();

      windowMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Window");
      
      studio_.displays().registerForEvents(this);
   }

   /**
    * Handles event signalling that a DataViewer was opened.  Usually this will result in
    * addition of the DataViewer to our menu.
    *
    * @param e Event with information about the newly added DataViewer
    */
   @Subscribe
   public void onEvent(DataViewerAddedEvent e) {
      String name = e.getDataViewer().getName();
      // check whether or not we have this window already
      for (JMenuItem mItem : menuItems_) {
         if (mItem.getText().equals(name)) {
            return;
         }
      }
      menuItems_.add(GUIUtils.addMenuItem(windowMenu_, name, name, () -> {
         if (e.getDataViewer() instanceof DisplayWindow) {
            DisplayWindow dw = (DisplayWindow) e.getDataViewer();
            dw.toFront();
         }
      }));
   }


   /**
    * Handles event signalling that a DataViewer will close.  Usually this results in removal
    * of the corresponding entry in the Windows Menu.
    *
    * @param e Contains information about the DataViewer that will close.
    */
   @Subscribe
   public void onEvent(DataViewerWillCloseEvent e) {   
      String name = e.getDataViewer().getName();
      for (JMenuItem mItem : menuItems_) {
         if (mItem.getText().equals(name)) {
            windowMenu_.remove(mItem);
            menuItems_.remove(mItem);
            return;
         }
      }
   }
   
}
