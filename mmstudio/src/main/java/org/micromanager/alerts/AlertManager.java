///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    (c) 2016 Open Imaging, Inc.
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
//

package org.micromanager.alerts;

import javax.swing.JComponent;

/**
 * The AlertManager allows you to show non-intrusive messages to the user,
 * which will be displayed in a special window, the Alerts window. You can
 * access the AlertManager via Studio.alerts() or Studio.getAlertManager().
 */
public interface AlertManager {
   /**
    * Create a one-time alert that displays some text for the user.  A new
    * alert will be created each time this method is called; calling it
    * repeatedly will therefore rapidly fill the Alerts window.  It is
    * therefore strongly encouraged that you use the variant of this method
    * that takes an "owner" parameter if your code may produce multiple alerts.
    * @param title Title text to show above the main text. May be null.
    * @param text Text to display to the user.
    * @return Newly-created Alert
    */
   public Alert showTextAlert(String title, String text);

   /**
    * Create a text alert that can combine multiple alerts together. If there
    * is already a combining text alert with the same title, and that
    * alert is still usable (per its isUsable() method), then the new text will
    * be added as an additional line in the alert.
    * @param title Title text to show above the main text. May be null.
    * @param text Text to display to the user.
    * @return Newly-created Alert, or pre-existing Alert if it is re-used.
    */
   public Alert showCombiningTextAlert(String title, String text);

   /**
    * Create an alert containing the provided special contents. If there is
    * already a custom alert with the same title and contents, and that alert
    * is still usable (per its isUsable() method), then no action will be
    * taken.
    * @param title Title text to show above the custom contents. May be null.
    * @param contents Contents to be inserted into the alert dialog.
    * @return Newly-created Alert
    */
   public Alert showCustomAlert(String title, JComponent contents);
}
