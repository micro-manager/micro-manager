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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Run script" button logic, which allows the user to run
 * a preselected script file.
 */
@Plugin(type = WidgetPlugin.class)
public final class ScriptButton extends WidgetPlugin implements SciJavaPlugin {
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
   public boolean getCanCustomizeIcon() {
      return true;
   }

   @Override
   public JComponent createControl(PropertyMap config) {
      final File file = new File(config.getString("scriptPath", ""));
      Icon icon = studio_.quickAccess().getCustomIcon(config,
            IconLoader.getIcon("/org/micromanager/icons/file.png"));
      String name = TextUtils.truncateFilename(file.getName(), 15);
      // Size it to mostly fill its frame.
      JButton result = new JButton(name, icon) {
         @Override
         public Dimension getPreferredSize() {
            return QuickAccessPlugin.getPaddedCellSize();
         }
      };
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            if (!file.exists()) {
               studio_.logs().showError("Unable to find script file at " +
                  file.getAbsolutePath());
            }
            else {
               studio_.scripter().runFile(file);
            }
         }
      });
      return result;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      File file = FileDialogs.openFile(parent, "Choose Beanshell script",
            ScriptPanel.BSH_FILE);
      if (file == null) {
         return null;
      }
      return PropertyMaps.builder().putString("scriptPath",
              file.getAbsolutePath()).build();
   }
}
