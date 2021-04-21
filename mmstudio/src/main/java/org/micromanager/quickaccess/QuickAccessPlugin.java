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

package org.micromanager.quickaccess;

import java.awt.Dimension;
import javax.swing.ImageIcon;
import org.micromanager.MMPlugin;

/**
 * QuickAccessPlugins are used for controls that can show up in the Quick- Access Window, which
 * shows frequently-used controls.
 */
public abstract class QuickAccessPlugin implements MMPlugin {
  /** The width of a single cell in the Quick-Access Window. */
  public static final int CELL_WIDTH = 120;

  /** The height of a single cell in the Quick-Access Window. */
  public static final int CELL_HEIGHT = 50;

  /**
   * Provides a dimension that mostly fills 1 cell in the Quick-Access Window. This dimension should
   * be used by any "simple" controls (like buttons) to preserve visual uniformity.
   */
  public static Dimension getPaddedCellSize() {
    return new Dimension(CELL_WIDTH - 10, CELL_HEIGHT - 10);
  }

  /**
   * Provide an icon to use to represent this plugin when configuring the Quick-Access Window. May
   * be null, in which case a rendering of the plugin's controls will be used instead. Note that if
   * you want to use a null icon here, then your plugin *must* create some kind of sensible control
   * even when there is no configuration (for WidgetPlugins), as the icon of the control in the
   * configuration mode is derived from a non-configured control.
   */
  public abstract ImageIcon getIcon();
}
