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
 * which will be displayed in small dialogs along the side of the display. You
 * can access the AlertManager via Studio.alerts() or Studio.getAlertManager().
 */
public interface AlertManager {
   /**
    * Create a one-time alert that displays some text for the user.  A new
    * dialog will be created each time this method is called; calling it
    * repeatedly will therefore rapidly hit the message limit (the point where
    * new dialogs are not shown until old ones have been disposed of by the
    * user).
    * @param text Text to display to the user.
    * @return Newly-created Alert
    */
   public Alert showTextAlert(String text);

   /**
    * Create a text alert for the user that is associated with the provided
    * owner Object. If there is already a text alert for the owner, and that
    * alert is still usable (per its isUsable() method), then the new text will
    * be added as an additional line in the alert. If the owner is null, then
    * this method is equivalent to calling the showTextAlert() method that
    * takes no owner parameter.
    * @param text Text to display to the user.
    * @param owner Owner of this alert; multiple alerts from the same owner
    *        will be combined together.
    * @return Newly-created Alert, or pre-existing Alert if it is re-used.
    * @throws IllegalArgumentException if there is an existing alert with this
    *         owner that was not created by this method.
    */
   public Alert showTextAlert(String text, Object owner) throws IllegalArgumentException;

   /**
    * Create an alert containing the provided special contents.
    * @param contents Contents to be inserted into the alert dialog.
    * @param owner Owner of this alert; used only to allow the alert to be
    *        dismissed later via dismissAlert().
    * @throws IllegalArgumentException if there is an existing alert with this
    *         owner.
    * @return Newly-created Alert
    */
   public Alert showAlert(JComponent contents, Object owner) throws IllegalArgumentException;

   /**
    * Dismiss an alert that has the specified owner.
    * @param owner Owner of the alert to dismiss.
    */
   public void dismissAlert(Object owner);
}
