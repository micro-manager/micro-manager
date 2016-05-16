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

import javax.swing.JDialog;

/**
 * An Alert is a temporary, undecorated dialog that shows up along the side of
 * the screen for information that doesn't need to interrupt the user. Alerts
 * can be created via the AlertManager.
 */
public abstract class Alert extends JDialog {
   /**
    * Returns whether or not this Alert is still visible to the user and can
    * therefore be updated or have more content added to it.
    */
   public abstract boolean isUsable();
}
