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

package org.micromanager.quickaccess.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

import org.micromanager.events.LiveModeEvent;

import org.micromanager.quickaccess.ToggleButtonPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Live" button logic.
 */
@Plugin(type = ToggleButtonPlugin.class)
public class LiveButton implements ToggleButtonPlugin, SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Live button";
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
      return null;
   }

   @Override
   public JToggleButton createButton() {
      // HACK: we have to create a "container" for the button that handles
      // events. If we subscribe the button itself to the LiveModeEvent, then
      // Java will contain that the button might not have been initialized,
      // and thus we aren't allowed to try to modify it.
      final JToggleButton result = new JToggleButton("Live",
            studio_.live().getIsLiveModeOn() ?
            IconLoader.getIcon("/org/micromanager/icons/cancel.png") :
            IconLoader.getIcon("/org/micromanager/icons/camera_go.png"));
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
}
