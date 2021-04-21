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

import javax.swing.JToggleButton;

/**
 * This plugin type is used for buttons that can be embedded in the Quick-Access Window. It is
 * similar to the SimpleButtonPlugin, except that its state may be toggled.
 */
public abstract class ToggleButtonPlugin extends QuickAccessPlugin {
  /** Provide a JToggleButton that will behave as desired by the plugin. */
  public abstract JToggleButton createButton();
}
