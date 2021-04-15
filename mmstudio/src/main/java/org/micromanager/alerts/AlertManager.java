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
 * which will be displayed in a special window, the Messages window. You can
 * access the AlertManager via Studio.alerts() or Studio.getAlertManager().
 */
public interface AlertManager {
   /**
    * Send notification of a specific event. Events are grouped by both title
    * and group (the group is not directly exposed to the user in the GUI), and
    * only the most recent event for a given title/group pair is normally shown
    * to the user. This can thus be useful for notifying the user of events
    * that you anticipate may occur many times (for example, errors in
    * processing images due to a malconfigured processor). If the group is
    * null, then the alert will always be shown, in addition to all other
    * alerts for this title.
    *
    * If the user dismisses one of these alerts, they are effectively clearing
    * the "message history" for the alert. Future calls to this method will
    * create a new alert rather than resurrect the old one.
    *
    * @param title Title text to show above the alert. May be null.
    * @param group Object to use for grouping multiple alert texts together.
    *        May be null; null-group alerts are always displayed.
    * @param text Text of the alert.
    * @return Either a newly-created Alert, or the Alert that already existed,
    *         to which this new alert text was added.
    */
   Alert postAlert(String title, Class<?> group, String text);

   /**
    * Create a UpdatableAlert. UpdatableAlerts can have their contents changed
    * after they have been posted. However, if the user dismisses the alert,
    * changing the text will not bring it back.  Each UpdatableAlert is shown
    * separately in the Messages window, so calling this method repeatedly can
    * rapidly fill the window with alerts.
    * @param title Title text to show above the main text. May be null.
    * @param text Initial text to display to the user.
    * @return Newly-created UpdatableAlert
    */
   UpdatableAlert postUpdatableAlert(String title, String text);

   /**
    * Create an alert containing the provided special contents. If there is
    * already a custom alert with the same title and contents, and that alert
    * is still usable (per its isUsable() method), then no action will be
    * taken.
    *
    * This method returns a UpdatableAlert primarily so that the custom alert
    * may update the text of the alert (via UpdatableAlert.setText()). While
    * this does not directly affect the display of the alert in the Messages
    * window (the caller is of course responsible for that), it does affect the
    * text that is displayed in the main window.
    *
    * @param title Title text to show above the custom contents. May be null.
    * @param contents Contents to be inserted into the alert dialog.
    * @return Newly-created Alert
    */
   UpdatableAlert postCustomAlert(String title, JComponent contents);
}
