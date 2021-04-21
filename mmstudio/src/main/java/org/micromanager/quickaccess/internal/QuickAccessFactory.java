///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import org.micromanager.PropertyMaps;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.SimpleButtonPlugin;
import org.micromanager.quickaccess.ToggleButtonPlugin;
import org.micromanager.quickaccess.WidgetPlugin;

/**
 * This class creates UI widgets for the Quick-Access Window based on the plugins that are handed to
 * it.
 */
public final class QuickAccessFactory {
  /**
   * Given a QuickAccessPlugin, make the appropriate GUI for it. This just farms out to the
   * appropriate more specific method.
   */
  public static JComponent makeGUI(QuickAccessPlugin plugin) {
    if (plugin instanceof SimpleButtonPlugin) {
      return makeButton((SimpleButtonPlugin) plugin);
    } else if (plugin instanceof ToggleButtonPlugin) {
      return makeToggleButton((ToggleButtonPlugin) plugin);
    } else if (plugin instanceof WidgetPlugin) {
      return makeWidget((WidgetPlugin) plugin);
    }
    ReportingUtils.logError("Unrecognized plugin type " + plugin);
    return null;
  }

  /** Given a SimpleButtonPlugin, create a JButton from it. */
  public static JButton makeButton(final SimpleButtonPlugin plugin) {
    // Size the button to mostly fill its cell.
    JButton result =
        new JButton(plugin.getTitle(), plugin.getButtonIcon()) {
          @Override
          public Dimension getPreferredSize() {
            return QuickAccessPlugin.getPaddedCellSize();
          }
        };
    result.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            plugin.activate();
          }
        });
    return result;
  }

  /** Given a ToggleButtonPlugin, create a JToggleButton from it. */
  public static JToggleButton makeToggleButton(ToggleButtonPlugin plugin) {
    return plugin.createButton();
  }

  /**
   * Given a WidgetPlugin, create its controls. This method doesn't allow for interactive
   * configuration; we just provide a blank PropertyMap as the config.
   */
  public static JComponent makeWidget(WidgetPlugin plugin) {
    return plugin.createControl(PropertyMaps.emptyPropertyMap());
  }
}
