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

package org.micromanager.alerts.internal;

import com.bulenkov.iconloader.IconLoader;

import java.util.ArrayList;

import java.awt.Font;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;

import org.micromanager.alerts.Alert;
import org.micromanager.internal.utils.GUIUtils;


public class AlertsWindow extends JFrame {
   private static AlertsWindow staticInstance_;
   /**
    * Create the AlertsWindow if necessary, then display it.
    */
   public static void show(Studio studio) {
      if (staticInstance_ == null) {
         staticInstance_ = new AlertsWindow(studio);
      }
      staticInstance_.setVisible(true);
      staticInstance_.toFront();
   }

   private static JPanel createHeader(String title) {
      JPanel contents = new JPanel(new MigLayout("insets 0, gap 0, fill, flowy"));
      if (title != null && !title.contentEquals("")) {
         JLabel titleLabel = new JLabel(title);
         titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
         contents.add(titleLabel);
      }
      return contents;
   }

   /**
    * Create a simple alert with a text message.
    */
   public static DefaultAlert addOneShotAlert(Studio studio, String title,
         String text) {
      show(studio);
      JPanel contents = new JPanel(new MigLayout("fill, flowy, insets 2, gap 0"));
      contents.add(createHeader(title));
      contents.add(new JLabel(text));
      return addAlert(studio, contents, true);
   }

   /**
    * Create a custom alert with any contents
    */
   public static DefaultAlert addAlert(Studio studio, JPanel contents,
         boolean isOneShot) {
      show(studio);
      DefaultAlert alert = new DefaultAlert(staticInstance_, contents, isOneShot);
      staticInstance_.addAlert(alert);
      return alert;
   }

   /**
    * Variant of the above specifically for MultiTextAlerts.
    */
   public static MultiTextAlert addTextAlert(Studio studio, String title) {
      show(studio);
      MultiTextAlert alert = MultiTextAlert.createAlert(staticInstance_,
            createHeader(title));
      staticInstance_.addAlert(alert);
      return alert;
   }

   private static final String NO_ALERTS_MSG = "There are no alerts at this time.";

   private Studio studio_;
   private final ArrayList<Alert> allAlerts_ = new ArrayList<Alert>();
   private final JPanel alertsPanel = new JPanel(new MigLayout("fill, flowy"));

   private AlertsWindow(Studio studio) {
      super("Alerts");
      studio_ = studio;

      setLayout(new MigLayout("fill, insets 2, gap 0"));
      JScrollPane scroller = new JScrollPane(alertsPanel);
      scroller.setBorder(null);
      alertsPanel.add(new JLabel(NO_ALERTS_MSG));
      add(scroller, "push, grow");
      pack();
   }

   public void addAlert(Alert alert) {
      if (allAlerts_.isEmpty()) {
         // Remove the "there are no alerts" label.
         alertsPanel.removeAll();
      }
      allAlerts_.add(alert);
      alertsPanel.add(alert, "pushx, growx");
      studio_.events().post(new AlertCreatedEvent());
      pack();
   }

   public void removeAlert(Alert alert) {
      if (allAlerts_.contains(alert)) {
         allAlerts_.remove(alert);
         alertsPanel.remove(alert);
         if (allAlerts_.isEmpty()) {
            studio_.events().post(new NoAlertsAvailableEvent());
            alertsPanel.add(new JLabel(NO_ALERTS_MSG));
         }
      }
      pack();
   }

   /**
    * HACK: before packing, we want to expand our width to a hardcoded maximum,
    * if necessary, to contain our various alerts.
    */
   @Override
   public void pack() {
      int maxWidth = 0;
      for (Alert alert : allAlerts_) {
         maxWidth = Math.max(alert.getSize().width, maxWidth);
      }
      setSize(Math.min(400, Math.max(100, maxWidth)), getSize().height);
      super.pack();
   }
}
