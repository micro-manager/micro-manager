///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
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

package org.micromanager.alerts.internal;

import org.micromanager.Studio;
import org.micromanager.alerts.Alert;
import org.micromanager.alerts.AlertManager;
import org.micromanager.alerts.UpdatableAlert;

import javax.swing.*;
import java.util.HashMap;

public final class DefaultAlertManager implements AlertManager {
  private final Studio studio_;
  private final HashMap<String, CategorizedAlert> titleToCategorizedAlert_ =
      new HashMap<String, CategorizedAlert>();
  private final HashMap<String, DefaultAlert> titleToCustomAlert_ =
      new HashMap<String, DefaultAlert>();
  private AlertsWindow alertsWindow_;

  public DefaultAlertManager(Studio studio) {
    studio_ = studio;
    alertsWindow_ = new AlertsWindow(studio_);
  }

  @Override
  public UpdatableAlert postUpdatableAlert(String title, String text) {
    return alertsWindow_.addUpdatableAlert(title, text);
  }

  @Override
  public Alert postAlert(String title, Class<?> category, String text) {
    CategorizedAlert alert;
    if (titleToCategorizedAlert_.containsKey(title)
        && titleToCategorizedAlert_.get(title).isUsable()) {
      alert = titleToCategorizedAlert_.get(title);
    } else {
      // Make a new Alert to hold messages.
      alert = alertsWindow_.addCategorizedAlert(title);
      titleToCategorizedAlert_.put(title, alert);
    }
    alertsWindow_.showWindowUnlessMuted(alert);
    alert.addText(category, text);
    return alert;
  }

  @Override
  public UpdatableAlert postCustomAlert(String title, JComponent contents) {
    if (titleToCustomAlert_.containsKey(title)
        && titleToCustomAlert_.get(title).getContents() == contents) {
      // Already have this alert.
      return titleToCustomAlert_.get(title);
    }
    DefaultAlert alert = alertsWindow_.addCustomAlert(title, contents);
    // TODO: this potentially replaces an existing alert.
    titleToCustomAlert_.put(title, alert);
    return alert;
  }

  public AlertsWindow alertsWindow() {
    return alertsWindow_;
  }
}
