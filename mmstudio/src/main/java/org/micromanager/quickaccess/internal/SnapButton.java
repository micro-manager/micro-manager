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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;
import java.awt.Frame;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.utils.GUIUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Snap" button logic.
 */
@Plugin(type = WidgetPlugin.class)
public class SnapButton extends WidgetPlugin implements SciJavaPlugin {
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
      return new ImageIcon(IconLoader.loadFromResource(
            "/org/micromanager/icons/camera@2x.png"));
   }

   // Configuration is just used to determine if we use the "big" button size,
   // as the hardcoded snap buttons in e.g. the main window and Snap/Live
   // window don't use that size.
   @Override
   public JComponent createControl(final PropertyMap config) {
      // Make button size mostly fill the cell.
      JButton result = new JButton("Snap",
            IconLoader.getIcon("/org/micromanager/icons/camera.png")) {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            setEnabled(!event.getIsOn());
         }

         @Override
         public Dimension getPreferredSize() {
            if (config.getBoolean("isBig", false)) {
               return QuickAccessPlugin.getPaddedCellSize();
            }
            return super.getPreferredSize();
         }
      };
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
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
   public PropertyMap configureControl(Frame parent) {
      // When used in the Quick-Access window, we use a bigger size than when
      // used in other contexts.
      return studio_.data().getPropertyMapBuilder()
         .putBoolean("isBig", true).build();
   }
}
