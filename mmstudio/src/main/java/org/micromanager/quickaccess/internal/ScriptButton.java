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

import java.io.File;

import java.util.Arrays;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Run script" button logic, which allows the user to run
 * a preselected script file.
 */
@Plugin(type = WidgetPlugin.class)
public class ScriptButton implements WidgetPlugin, SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Run Script";
   }

   @Override
   public String getHelpText() {
      return "Run a Beanshell script file.";
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
            "/org/micromanager/icons/file@2x.png"));
   }

   @Override
   public JComponent createControl(PropertyMap config) {
      final File file = new File(config.getString("scriptPath", ""));
      JButton result = new JButton(file.getName(),
            IconLoader.getIcon("/org/micromanager/icons/file.png"));
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            studio_.scripter().runFile(file);
         }
      });
      return result;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      File file = FileDialogs.openFile(parent, "Choose Beanshell script",
            ScriptPanel.BSH_FILE);
      return studio_.data().getPropertyMapBuilder()
         .putString("scriptPath", file.getAbsolutePath()).build();
   }

   @Override
   public Dimension getSize() {
      return new Dimension(1, 1);
   }
}
