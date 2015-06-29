///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.events.FullScreenEvent;
import org.micromanager.internal.utils.GUIUtils;


/**
 * This class includes the buttons at the bottom of the display window, along
 * with any other custom controls the creator of the display window has decided
 * to include.
 */
public class ButtonPanel extends JPanel {

   private final JButton fullButton_;

   public ButtonPanel(final DisplayWindow display,
         ControlsFactory controlsFactory) {
      setLayout(new MigLayout("insets 0"));
      // Add user-supplied custom controls, if any.
      List<Component> customControls = new ArrayList<Component>();
      if (controlsFactory != null) {
         customControls = controlsFactory.makeControls(display);
      }
      for (Component c : customControls) {
         add(c);
      }

      fullButton_ = new JButton("Fullscreen");
      fullButton_.setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/fullscreen.png"));
      fullButton_.setFont(GUIUtils.buttonFont);
      fullButton_.setToolTipText("Turn fullscreen mode on or off.");
      fullButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            display.toggleFullScreen();
         }
      });
      add(fullButton_);

      // This and the other zoom icon adapted from the public-domain icon at
      // https://openclipart.org/detail/170636/magnifier-search-zoom
      JButton zoomInButton = new JButton();
      zoomInButton.setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/zoom_in.png"));
      zoomInButton.setToolTipText("Zoom in");
      zoomInButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display.adjustZoom(2.0);
         }
      });
      add(zoomInButton);
      JButton zoomOutButton = new JButton();
      zoomOutButton.setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/zoom_out.png"));
      zoomOutButton.setToolTipText("Zoom out");
      zoomOutButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display.adjustZoom(0.5);
         }
      });
      add(zoomOutButton);

      add(new SaveButton(display.getDatastore(), display));

      add(new GearButton(display));
      display.registerForEvents(this);
   }

   /**
    * The icons in this method were adapted from
    * https://openclipart.org/detail/33691/tango-view-fullscreen
    * @param event
    */
   @Subscribe
   public void onFullScreen(FullScreenEvent event) {
      if (event.getIsFullScreen()) {
         fullButton_.setText("Windowed");
         fullButton_.setIcon(IconLoader.getIcon(
                  "/org/micromanager/icons/windowed.png"));
      }
      else {
         fullButton_.setText("Fullscreen");
         fullButton_.setIcon(IconLoader.getIcon(
                  "/org/micromanager/icons/fullscreen.png"));
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      event.getDisplay().unregisterForEvents(this);
   }
}

