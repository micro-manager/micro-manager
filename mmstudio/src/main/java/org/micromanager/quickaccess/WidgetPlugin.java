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

import org.micromanager.PropertyMap;

import javax.swing.*;
import java.awt.*;

/**
 * This plugin allows arbitrary controls ("widgets") to be included in the Quick-Access Window. It
 * gives you the maximum control over how your control will appear and behave. However, it also
 * requires you to implement the most logic.
 */
public abstract class WidgetPlugin extends QuickAccessPlugin {
  /**
   * Create the GUI components for this control.
   *
   * @param config A PropertyMap as output by configureControl used to configure this control. The
   *     PropertyMap may be empty or may be preserved from a previous session.
   */
  public abstract JComponent createControl(PropertyMap config);

  /**
   * Generate configuration information that can be used to configure a control generated by this
   * plugin. This method is expected to block the Event Dispatch Thread (EDT) -- thus, any
   * configuration UI should be modal. It is also only ever called from the EDT. If for any reason
   * the control should not be created (for example, because the user canceled a dialog during
   * configuration), then this method must return null.
   *
   * @param parent The Quick-Access Window
   * @return A PropertyMap containing the information needed to generate the control, or null if the
   *     control should not be created.
   */
  public abstract PropertyMap configureControl(Frame parent);

  /**
   * Return the dimensionality of the widget, in cells. For example, a value of 2x1 here would
   * indicate a widget that is 2 cells wide and 1 tall. The size of a single cell is the CELL_WIDTH
   * and CELL_HEIGHT values in QuickAccessPlugin.
   */
  public Dimension getSize() {
    return new Dimension(1, 1);
  }

  /**
   * Returns true if the widget can accept custom icons (i.e. the setIcon() method actually does
   * something). This will allow users to provide their own icons for individual instances of the
   * widget.
   */
  public boolean getCanCustomizeIcon() {
    return false;
  }

  /**
   * Key used for custom icon information. If your plugin returns true in getCanCustomizeIcon(),
   * then there may be a property, in the PropertyMap passed to createControl(), under this key. You
   * should not need to read or modify this property; just pass your config to
   * QuickAccessManager.getCustomIcon() to get the customized icon the user has requested.
   */
  public static final String CUSTOM_ICON_STRING = "__MM_CustomIcon";
}
