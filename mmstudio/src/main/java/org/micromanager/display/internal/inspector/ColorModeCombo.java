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

package org.micromanager.display.internal.inspector;

import net.miginfocom.swing.MigLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.LUTMaster;

/**
 * This module provides a button with a dropdown menu, allowing the user to
 * select a color mode to use for displaying images -- including grayscale,
 * color, composite, and various LUT (lookup table)-based methods.
 * It behaves basically similarly to a JComboBox, hence the name, but doesn't
 * use JComboBox because we ran into issues with rendering.
 * TODO: LUTs with color displays don't currently work.
 */
public class ColorModeCombo extends JButton {
   private DisplayWindow display_;

   public ColorModeCombo(DisplayWindow display) {
      super();
      display_ = display;
      int index = DisplaySettings.ColorMode.COLOR.getIndex();
      DisplaySettings.ColorMode mode = display_.getDisplaySettings().getChannelColorMode();
      if (mode != null) {
         index = mode.getIndex();
      }
      setText(LUTMaster.ICONS.get(index).text_);
      setIcon(LUTMaster.ICONS.get(index).icon_);

      setToolTipText("Set how the image display uses color");

      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            showMenu();
         }
      });
   }

   /**
    * Generate and display the popup menu.
    */
   private void showMenu() {
      JPopupMenu menu = new JPopupMenu();

      for (int i = 0; i < LUTMaster.ICONS.size(); ++i) {
         final int index = i;
         LUTMaster.IconWithStats icon = LUTMaster.ICONS.get(i);
         JMenuItem item = new JMenuItem(icon.text_, icon.icon_);
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               setText(LUTMaster.ICONS.get(index).text_);
               setIcon(LUTMaster.ICONS.get(index).icon_);
               LUTMaster.setModeByIndex(display_, index);
               DisplaySettings settings = display_.getDisplaySettings();
               settings = settings.copy().channelColorMode(
                  DisplaySettings.ColorMode.fromInt(index)).build();
               display_.setDisplaySettings(settings);
            }
         });
         menu.add(item);

         // HACK: stick a separator after the Grayscale option.
         if (icon == LUTMaster.GRAY) {
            menu.addSeparator();
         }
      }
      menu.show(this, 0, 0);
   }
}
