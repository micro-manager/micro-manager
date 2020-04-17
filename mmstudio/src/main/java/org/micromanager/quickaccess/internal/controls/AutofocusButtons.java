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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Autofocus" buttons (one to run autofocus, one to adjust
 * the autofocus options).
 */
@Plugin(type = WidgetPlugin.class)
public final class AutofocusButtons extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Autofocus";
   }

   @Override
   public String getHelpText() {
      return "Access the Autofocus configuration and run autofocus.";
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
               "/org/micromanager/icons/binoculars@2x.png"));
   }

   // We are not actually configurable.
   @Override
   public JComponent createControl(PropertyMap config) {
      JPanel result = new JPanel(new MigLayout("flowx, insets 0, gap 0"));

      JButton runButton = new JButton("Run",
            IconLoader.getIcon("/org/micromanager/icons/binoculars.png")) {
         @Override
         public Dimension getPreferredSize() {
            // Take up under half a cell.
            Dimension result = QuickAccessPlugin.getPaddedCellSize();
            result.width = (int) (result.width * .4);
            return result;
         }
      };
      runButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            ((MMStudio) studio_).autofocusNow();
         }
      });
      runButton.setFont(GUIUtils.buttonFont);
      runButton.setMargin(new Insets(0, 0, 0, 0));
      result.add(runButton);

      JButton dialogButton = new JButton("Config",
            IconLoader.getIcon("/org/micromanager/icons/wrench.png")) {
         @Override
         public Dimension getPreferredSize() {
            // Take up half a cell.
            Dimension result = QuickAccessPlugin.getPaddedCellSize();
            result.width = (int) (result.width * .4);
            return result;
         }
      };
      dialogButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ((MMStudio) studio_).showAutofocusDialog();
         }
      });
      dialogButton.setFont(GUIUtils.buttonFont);
      dialogButton.setMargin(new Insets(0, 0, 0, 0));
      result.add(dialogButton);

      return result;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      return PropertyMaps.builder().build();
   }
}
