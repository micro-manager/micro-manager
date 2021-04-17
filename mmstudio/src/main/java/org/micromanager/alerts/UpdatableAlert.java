///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
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

/**
 * UpdatableAlerts are Alerts whose message texts can be changed after they have
 * been posted. They are intended to be used to provide ongoing information
 * about some changing event. For example, the "images received" tracker that
 * runs during an acquisition is a UpdatableAlert. UpdatableAlerts may be
 * created via AlertManager.postUpdatableAlert(). This class type is also used
 * for custom alerts created via AlertManager.postCustomAlert().
 */
public interface UpdatableAlert extends Alert {
   /**
    * Update the text displayed by the Alert. This will affect the text shown
    * in the main Micro-Manager window for this alert. For simple text alerts
    * and combining text alerts, this will also update the text in the alert in
    * the Messages window.
    * Note that this method is only appropriate for alerts that show the
    * current status of something (like the acquisition status alert). Usually
    * you should use a combining text alert rather than update this text
    * directly.
    * @param text New text of alert.
    */
   void setText(String text);

   /**
    * Returns the current text of the alert.
    * @return Current text of the alert.
    */
   String getText();
}
