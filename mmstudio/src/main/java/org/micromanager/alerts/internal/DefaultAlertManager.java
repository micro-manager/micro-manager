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

package org.micromanager.alerts.internal;

import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.alerts.Alert;
import org.micromanager.alerts.AlertManager;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;

public class DefaultAlertManager implements AlertManager {

   private static DefaultAlertManager staticInstance_;
   static {
      staticInstance_ = new DefaultAlertManager(MMStudio.getInstance());
   }

   private Studio studio_;
   private HashMap<String, MultiTextAlert> titleToTextAlert_ = new HashMap<String, MultiTextAlert>();
   private HashMap<String, DefaultAlert> titleToCustomAlert_ = new HashMap<String, DefaultAlert>();

   private DefaultAlertManager(Studio studio) {
      studio_ = studio;
   }

   @Override
   public Alert showTextAlert(String title, String text) {
      return AlertsWindow.addOneShotAlert(studio_, title, text);
   }

   @Override
   public Alert showCombiningTextAlert(String title, String text) {
      MultiTextAlert alert;
      if (titleToTextAlert_.containsKey(title) &&
            titleToTextAlert_.get(title).isUsable()) {
         alert = titleToTextAlert_.get(title);
      }
      else {
         // Make a new Alert to hold messages.
         alert = AlertsWindow.addTextAlert(studio_, title);
         titleToTextAlert_.put(title, alert);
      }
      alert.addText(text);
      return alert;
   }

   @Override
   public Alert showCustomAlert(String title, JComponent contents) {
      JPanel panel = new JPanel(new MigLayout("insets 0, gap 0, fill"));
      panel.add(contents, "grow");
      DefaultAlert alert = AlertsWindow.addAlert(studio_, panel);
      titleToCustomAlert_.put(title, alert);
      return alert;
   }

   public static DefaultAlertManager getInstance() {
      return staticInstance_;
   }
}
