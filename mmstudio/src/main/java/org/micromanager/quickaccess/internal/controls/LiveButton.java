///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
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

package org.micromanager.quickaccess.internal.controls;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Live" button logic.
 */
@Plugin(type = WidgetPlugin.class)
public final class LiveButton extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Start/Stop Live";
   }

   @Override
   public String getHelpText() {
      return "Show a live feed from the currently-active camera.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Open Imaging, Inc.";
   }

   @Override
   public ImageIcon getIcon() {
      return new ImageIcon(IconLoader.loadFromResource(
            "/org/micromanager/icons/camera_go@2x.png"));
   }

   // Configuration is just used to determine if we use the "big" button size,
   // as the hardcoded snap buttons in e.g. the main window and Snap/Live
   // window don't use that size.
   @Override
   public JComponent createControl(final PropertyMap config) {
      // HACK: we have to create a "container" for the button that handles
      // events. If we subscribe the button itself to the LiveModeEvent, then
      // Java will complain that the button might not have been initialized,
      // and thus we aren't allowed to try to modify it.
      // Make the button mostly fill its cell.
      final JButton result = new JButton("Live") {
         @Override
         public Dimension getPreferredSize() {
            if (config.getBoolean("isBig", false)) {
               return QuickAccessPlugin.getPaddedCellSize();
            }
            return super.getPreferredSize();
         }
      };
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.live().setLiveMode(!studio_.live().getIsLiveModeOn());
         }
      });
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      // This wrapper a) handles changing the button's state when
      // LiveModeEvents happen, and b) unsubscribes itself from events when
      // the button is destroyed.
      HierarchyListener wrapper = new HierarchyListener() {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            boolean isOn = event.getIsOn();
            result.setIcon(IconLoader.getIcon(
                  isOn ? "/org/micromanager/icons/cancel.png" :
                  "/org/micromanager/icons/camera_go.png"));
            result.setText(isOn ? "Stop Live" : "Live");
         }

         @Override
         public void hierarchyChanged(HierarchyEvent e) {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
               if (!result.isDisplayable()) {
                  try {
                     studio_.events().unregisterForEvents(this);
                  }
                  catch (IllegalArgumentException ex) {
                     // We were already unsubscribed; ignore it.
                  }
               }
               else {
                  studio_.events().registerForEvents(this);
               }
            }
         }
      };
      result.setIcon(studio_.live().getIsLiveModeOn() ?
            IconLoader.getIcon("/org/micromanager/icons/cancel.png") :
            IconLoader.getIcon("/org/micromanager/icons/camera_go.png"));
      result.addHierarchyListener(wrapper);
      studio_.events().registerForEvents(wrapper);
      return result;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      // When used in the Quick-Access window, we use a bigger size than when
      // used in other contexts.
      return PropertyMaps.builder().putBoolean("isBig", true).build();
   }
}
