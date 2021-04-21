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

import javax.swing.*;

/**
 * This plugin type is used for buttons that can be embedded in the Quick-Access Window. It's for
 * simple buttons that perform an action when clicked.
 */
public abstract class SimpleButtonPlugin extends QuickAccessPlugin {
  /** Returns the text to show in the button. */
  public abstract String getTitle();

  /** Returns an icon to show in the button. May be null. */
  public abstract Icon getButtonIcon();

  /** This method will be called when the button is clicked. */
  public abstract void activate();
}
