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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Insets;
import java.awt.Frame;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import net.miginfocom.swing.MigLayout;

import org.micromanager.events.AutoShutterEvent;
import org.micromanager.events.ShutterEvent;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.NumberUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Allows you to open and close the shutter, and toggle autoshutter.
 */
@Plugin(type = WidgetPlugin.class)
public class ShutterControl extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Shutter Control";
   }

   @Override
   public String getHelpText() {
      return "Open and close the shutter, or toggle autoshutter.";
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
               "/org/micromanager/icons/shutter_open.png"));
   }

   // We are not actually configurable; config is ignored.
   @Override
   public JComponent createControl(PropertyMap config) {
      JPanel panel = new JPanel(new MigLayout("flowy, gap 0, insets 0"));
      final JLabel icon = new JLabel();
      // Toggle icon state when mouse is pressed.
      icon.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            try {
               if (studio_.shutter().getAutoShutter()) {
                  studio_.shutter().setAutoShutter(false);
               }
               studio_.shutter().setShutter(!studio_.shutter().getShutter());
            }
            catch (Exception ex) {
               studio_.logs().logError(ex, "Error toggling shutter icon");
            }
         }
      });
      updateIcon(icon);
      panel.add(icon, "aligny center, wrap");
      final JCheckBox toggle = new JCheckBox("Auto");
      toggle.setFont(GUIUtils.buttonFont);
      toggle.setSelected(studio_.shutter().getAutoShutter());
      toggle.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               studio_.shutter().setAutoShutter(toggle.isSelected());
            }
            catch (Exception ex) {
               studio_.logs().showError(ex, "Error setting auto shutter");
            }
         }
      });
      panel.add(toggle, "split 2");
      final JToggleButton button = new JToggleButton();
      button.setEnabled(!studio_.shutter().getAutoShutter());
      try {
         button.setText(studio_.shutter().getShutter() ? "Close" : "Open");
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Unable to get shutter state");
      }
      button.setFont(GUIUtils.buttonFont);
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               studio_.shutter().setShutter(!studio_.shutter().getShutter());
            }
            catch (Exception ex) {
               studio_.logs().logError(ex, "Unable to toggle shutter");
            }
         }
      });
      panel.add(button);
      // Create an object to subscribe for events and keep the GUI updated.
      Object registrant = new Object() {
         @Subscribe
         public void onShutter(ShutterEvent event) {
            button.setText(event.getShutter() ? "Close" : "Open");
            updateIcon(icon);
         }
         @Subscribe
         public void onAutoShutter(AutoShutterEvent event) {
            toggle.setSelected(event.getAutoShutter());
            button.setEnabled(!event.getAutoShutter());
            updateIcon(icon);
         }
      };
      studio_.events().registerForEvents(registrant);
      return panel;
   }

   /**
    * Set the icon for the shutter state display.
    * TODO: this code is all a duplicate of what's in MainFrame for their icon.
    */
   private void updateIcon(JLabel icon) {
      String path = "/org/micromanager/icons/shutter_";
      String tooltip = "The shutter is ";
      // Note that we use the mode both for the tooltip and for
      // constructing the image file path, since it happens to work out.
      try {
         String mode = studio_.shutter().getShutter() ? "open" : "closed";
         path += mode;
         tooltip += mode;
         if (studio_.shutter().getAutoShutter()) {
            path += "_auto";
            tooltip += ". Autoshutter is enabled";
         }
         path += ".png";
         tooltip += ". Click to open or close the shutter.";
         icon.setIcon(IconLoader.getIcon(path));
         icon.setToolTipText(tooltip);
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Unable to update shutter state display");
      }
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      return studio_.data().getPropertyMapBuilder().build();
   }

}
