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

package org.micromanager.internal.quickaccess;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import org.micromanager.SimpleButtonPlugin;
import org.micromanager.ToggleButtonPlugin;

/**
 * This class creates UI widgets for the Quick-Access Window based on the
 * plugins that are handed to it.
 */
public class QuickAccessFactory {
   /**
    * Given a SimpleButtonPlugin, create a JButton from it.
    */
   public static JButton makeButton(final SimpleButtonPlugin plugin) {
      JButton result = new JButton(plugin.getTitle(), plugin.getIcon());
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            plugin.activate();
         }
      });
      return result;
   }

   /**
    * Given a ToggleButtonPlugin, create a JToggleButton from it.
    */
   public static JToggleButton makeToggleButton(ToggleButtonPlugin plugin) {
      return plugin.createButton();
   }
}
