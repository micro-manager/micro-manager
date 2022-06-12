// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.internal.utils;

import java.awt.Container;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.event.EventListenerSupport;

/**
 * A button that shows a popup menu or view.
 * <p>
 * This one actually behaves correctly!
 *
 * @author Mark A. Tsuchida
 */
public class PopupButton extends JToggleButton
      implements PopupMenuListener, HierarchyListener,
      WindowFocusListener, ComponentListener {
   public interface Listener {
      /**
       * Provides the listener an opportunity to configure or swap the popup
       * before it is displayed.
       *
       * @param button
       */
      void popupButtonWillShowPopup(PopupButton button);
   }

   private final EventListenerSupport<Listener> listeners_ =
         new EventListenerSupport<>(Listener.class, Listener.class.getClassLoader());

   private JPopupMenu popup_;
   private JComponent component_;

   // Keep a copy of the menu upon showing it, so that we can hide the correct
   // popup even if the component is replaced while it is displayed.
   private JPopupMenu displayedPopup_;

   private long timeWhenPopupWasBecomingInvisibleMs_;
   private boolean currentMousePressOnButtonIsToCancelPopup_;

   private Window monitoredTopLevelWindow_;

   public static PopupButton create() {
      return create(null, null, null);
   }

   public static PopupButton create(String text) {
      return create(text, null, null);
   }

   public static PopupButton create(Icon icon) {
      return create(null, icon, null);
   }

   public static PopupButton create(String text, JComponent popup) {
      return create(text, null, popup);
   }

   public static PopupButton create(Icon icon, JComponent popup) {
      return create(null, icon, popup);
   }

   public static PopupButton create(String text, Icon icon, JComponent popup) {
      PopupButton instance = new PopupButton(text, icon);
      instance.setPopupComponent(popup);
      instance.addHierarchyListener(instance);
      return instance;
   }

   private PopupButton(String text, Icon icon) {
      super(text, icon);
      addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (currentMousePressOnButtonIsToCancelPopup_) {
               setSelected(false);
               return;
            }
            if (getModel().isSelected()) {
               showPopup();
               if (popup_ == null) {
                  setSelected(false);
               }
            }
            else {
               hidePopup();
            }
         }
      });
      addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            long timeSincePopupMenuWasBecomingInvisibleMs =
                  System.currentTimeMillis() -
                        timeWhenPopupWasBecomingInvisibleMs_;
            if (timeSincePopupMenuWasBecomingInvisibleMs < 50) {
               currentMousePressOnButtonIsToCancelPopup_ = true;
            }
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            currentMousePressOnButtonIsToCancelPopup_ = false;
         }
      });
   }

   public void addPopupButtonListener(Listener listener) {
      listeners_.addListener(listener, true);
   }

   public void removePopupButtonListener(Listener listener) {
      listeners_.removeListener(listener);
   }

   public void setPopupComponent(JComponent popup) {
      if (popup_ != null) {
         popup_.removePopupMenuListener(this);
         popup_ = null;
      }

      component_ = popup;
      if (component_ instanceof JPopupMenu) {
         popup_ = (JPopupMenu) component_;
      }
      else if (component_ != null) {
         popup_ = new JPopupMenu();
         popup_.add(component_);
         popup_.validate();
      }

      if (popup_ != null) {
         popup_.addPopupMenuListener(this);
      }
   }

   public JComponent getPopupComponent() {
      return component_;
   }

   private void showPopup() {
      // Give listeners a chance to populate or replace the popup
      listeners_.fire().popupButtonWillShowPopup(this);
      if (popup_ == null) {
         return;
      }
      popup_.show(this, getInsets().left, getHeight() - getInsets().bottom);
      displayedPopup_ = popup_;
   }

   private void hidePopup() {
      if (displayedPopup_ != null) {
         displayedPopup_.setVisible(false);
      }
      displayedPopup_ = null;
   }

   @Override
   public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
   }

   @Override
   public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      setSelected(false);
      timeWhenPopupWasBecomingInvisibleMs_ = System.currentTimeMillis();
   }

   @Override
   public void popupMenuCanceled(PopupMenuEvent e) {
   }

   @Override
   public void hierarchyChanged(HierarchyEvent e) {
      if (monitoredTopLevelWindow_ != null) {
         monitoredTopLevelWindow_.removeComponentListener(this);
         monitoredTopLevelWindow_.removeWindowFocusListener(this);
         monitoredTopLevelWindow_ = null;
      }

      Container topLevel = getTopLevelAncestor();
      if (topLevel instanceof Window) {
         monitoredTopLevelWindow_ = ((Window) topLevel);
         monitoredTopLevelWindow_.addWindowFocusListener(this);
         monitoredTopLevelWindow_.addComponentListener(this);
      }
   }

   @Override
   public void windowGainedFocus(WindowEvent e) {
   }

   @Override
   public void windowLostFocus(WindowEvent e) {
      Window windowGainingFocus = e.getOppositeWindow();
      if (windowGainingFocus != null &&
            windowGainingFocus.isAncestorOf(popup_)) {
         return;
      }

      // Window containing button lost focus, and not to the popup itself.
      hidePopup();
   }

   @Override
   public void componentResized(ComponentEvent e) {
      hidePopup();
   }

   @Override
   public void componentMoved(ComponentEvent e) {
      hidePopup();
   }

   @Override
   public void componentShown(ComponentEvent e) {
   }

   @Override
   public void componentHidden(ComponentEvent e) {
      hidePopup();
   }


   //
   // Test driver
   //

   public static void main(String[] argv) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            test();
         }
      });
   }

   private static void test() {
      JFrame frame = new JFrame();
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setLayout(new MigLayout());

      JPopupMenu menu = new JPopupMenu("JPopupMenu");
      menu.add("Item 1");
      menu.add("Item 2");

      PopupButton button1 = PopupButton.create("Conventional", menu);
      frame.add(button1);

      JSlider slider = new JSlider(HORIZONTAL, 0, 100, 50);
      System.err.println(slider.getPreferredSize().toString());
      PopupButton button2 = PopupButton.create("Special", slider);
      frame.add(button2);

      frame.pack();
      frame.setVisible(true);
   }
}