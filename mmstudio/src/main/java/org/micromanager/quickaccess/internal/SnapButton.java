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
import java.awt.Window;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Snap" button logic.
 */
@Plugin(type = WidgetPlugin.class)
public class SnapButton implements WidgetPlugin, SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Snap";
   }

   @Override
   public String getHelpText() {
      return "Snap an image and display it in the Snap/Live Window";
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

   // We are not actually configurable.
   @Override
   public JComponent createControl(PropertyMap config) {
      JButton result = new JButton("Snap",
            IconLoader.getIcon("/org/micromanager/icons/camera.png")) {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            setEnabled(!event.getIsOn());
         }
      };
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.live().snap(true);
         }
      });
      result.setEnabled(!studio_.live().getIsLiveModeOn());
      studio_.events().registerForEvents(result);
      return result;
   }

   @Override
   public PropertyMap configureControl(Window parent) {
      return studio_.data().getPropertyMapBuilder().build();
   }
}
