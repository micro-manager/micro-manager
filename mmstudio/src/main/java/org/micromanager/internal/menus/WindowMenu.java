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

/** @author nico */
public class WindowMenu {
  private final MMStudio studio_;
  private final JMenu windowMenu_;
  private final List<JMenuItem> menuItems_;

  public WindowMenu(MMStudio studio, JMenuBar menuBar) {
    studio_ = studio;
    menuItems_ = new ArrayList<>();

    windowMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Window");

    studio_.displays().registerForEvents(this);
  }

  @Subscribe
  public void onEvent(DataViewerAddedEvent e) {
    String name = e.getDataViewer().getName();
    // check whether or not we have this window already
    for (JMenuItem mItem : menuItems_) {
      if (mItem.getText().equals(name)) {
        return;
      }
    }
    menuItems_.add(
        GUIUtils.addMenuItem(
            windowMenu_,
            name,
            name,
            () -> {
              if (e.getDataViewer() instanceof DisplayWindow) {
                DisplayWindow dw = (DisplayWindow) e.getDataViewer();
                dw.toFront();
              }
            }));
  }

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
