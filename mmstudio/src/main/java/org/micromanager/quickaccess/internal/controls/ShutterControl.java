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
import java.awt.Insets;
import java.awt.Frame;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.GUIRefreshEvent;
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
      final JCheckBox toggle = new JCheckBox("Auto");
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
      panel.add(toggle);
      return panel;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      return studio_.data().getPropertyMapBuilder().build();
   }

}
