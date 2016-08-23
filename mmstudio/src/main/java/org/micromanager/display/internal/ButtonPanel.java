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
import org.micromanager.Studio;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.events.FullScreenEvent;
import org.micromanager.display.internal.gearmenu.GearButton;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class includes the buttons at the bottom of the display window, along
 * with any other custom controls the creator of the display window has decided
 * to include.
 */
public final class ButtonPanel extends JPanel {

   // Zoom levels copied from ij.gui.ImageCanvas.
   private static final Double[] ALLOWED_ZOOMS = new Double[] {
      1 / 72.0, 1 / 48.0, 1 / 32.0, 1 / 24.0, 1 / 16.0, 1 / 12.0,
      1 / 8.0, 1 / 6.0, 1 / 4.0, 1 / 3.0, 1 / 2.0, 0.75, 1.0, 1.5,
      2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0
         };

   private final JButton fullButton_;

   public ButtonPanel(final DisplayWindow display,
         ControlsFactory controlsFactory, Studio studio) {
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
            adjustZoom(display, 1);
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
            adjustZoom(display, -1);
         }
      });
      add(zoomOutButton);

      add(new SaveButton(display.getDatastore(), display));

      add(new GearButton(display, studio));
      display.registerForEvents(this);
   }

   /**
    * Move the provided display's magnification to the next allowed zoom
    * level based on its current zoom level. We can't just double or halve the
    * zoom level because ImageJ's own magnifying glass tool uses "unusual" zoom
    * levels like 75%; once we switch to a zoom like that, doubling or halving
    * can no longer return you to 100% zoom.
    */
   private static void adjustZoom(DisplayWindow display, int indexOffset) {
      Double curMag = display.getDisplaySettings().getMagnification();
      if (curMag == null) {
         curMag = 1.0;
      }
      // Find the closest match.
      Double best = -1.0;
      int bestIndex = -1;
      for (int i = 0; i < ALLOWED_ZOOMS.length; ++i) {
         Double zoom = ALLOWED_ZOOMS[i];
         if (Math.abs(zoom - curMag) < Math.abs(best - curMag)) {
            best = zoom;
            bestIndex = i;
         }
      }
      if (bestIndex == -1) {
         // This should never happen!
         ReportingUtils.logError("Failed to find a match for zoom " + curMag);
         return;
      }
      // Update the display with the new zoom.
      int nextIndex = Math.max(0, Math.min(bestIndex + indexOffset,
               ALLOWED_ZOOMS.length - 1));
      display.setDisplaySettings(display.getDisplaySettings().copy()
            .magnification(ALLOWED_ZOOMS[nextIndex]).build());
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

