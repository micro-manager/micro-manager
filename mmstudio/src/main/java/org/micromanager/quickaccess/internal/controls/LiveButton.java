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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Frame;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JToggleButton;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.PropertyMap;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.Studio;

import org.micromanager.internal.utils.GUIUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Live" button logic.
 */
@Plugin(type = WidgetPlugin.class)
public class LiveButton extends WidgetPlugin implements SciJavaPlugin {
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
      final JToggleButton result = new JToggleButton("Live",
            studio_.live().getIsLiveModeOn() ?
            IconLoader.getIcon("/org/micromanager/icons/cancel.png") :
            IconLoader.getIcon("/org/micromanager/icons/camera_go.png")) {
         @Override
         public Dimension getPreferredSize() {
            if (config.getBoolean("isBig", false)) {
               return QuickAccessPlugin.getPaddedCellSize();
            }
            return super.getPreferredSize();
         }
      };
      result.setSelected(studio_.live().getIsLiveModeOn());
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      Object wrapper = new Object() {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            boolean isOn = event.getIsOn();
            result.setIcon(IconLoader.getIcon(
                  isOn ? "/org/micromanager/icons/cancel.png" :
                  "/org/micromanager/icons/camera_go.png"));
            result.setText(isOn ? "Stop Live" : "Live");
            result.setSelected(isOn);
         }
      };
      studio_.events().registerForEvents(wrapper);
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.live().setLiveMode(result.isSelected());
         }
      });
      return result;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      // When used in the Quick-Access window, we use a bigger size than when
      // used in other contexts.
      return studio_.data().getPropertyMapBuilder()
         .putBoolean("isBig", true).build();
   }
}
