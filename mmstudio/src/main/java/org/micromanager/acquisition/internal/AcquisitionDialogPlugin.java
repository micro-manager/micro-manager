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

package org.micromanager.acquisition.internal;

import javax.swing.ImageIcon;
import org.micromanager.MMPlugin;

/**
 * This plugin type allows you to override the Multi-Dimensional Acquisition dialog used to perform
 * data acquisition. If an AcquisitionDialogPlugin is installed, then when the user clicks on the
 * "Acquire!" button in the main window, they will be presented with a popup menu of different
 * dialogs they may choose from, which will include plugin-provided options in addition to the
 * built-in dialog. The name of the plugin (as given by MMPlugin.getName() will be used to identify
 * the plugin in the menu).
 */
public interface AcquisitionDialogPlugin extends MMPlugin {
  /**
   * Provide an ImageIcon for display in the selection menu. May be null.
   *
   * @return an ImageIcon for use when selecting this plugin, or null.
   */
  public ImageIcon getIcon();

  /**
   * This method will be invoked when the user selects this plugin from the dropdown menu of
   * available AcquisitionDialogPlugins.
   */
  public void showAcquisitionDialog();
}
