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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JComponent;
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
   private static void ensureWindowExists(Studio studio) {
      if (staticInstance_ == null) {
         staticInstance_ = new AlertsWindow(studio);
      }
   }

   /**
    * Display the AlertsWindow, creating it if necessary.
    */
   public static void show(Studio studio) {
      ensureWindowExists(studio);
      staticInstance_.setVisible(true);
      staticInstance_.toFront();
   }

   /**
    * Display the AlertsWindow, if the given alert is not muted.
    */
   public static void showWindowUnlessMuted(Studio studio, DefaultAlert alert) {
      ensureWindowExists(studio);
      if (staticInstance_.isMuted(alert)) {
         return;
      }
      show(studio);
   }

   /**
    * Create a simple alert with a text message.
    */
   public static DefaultAlert addOneShotAlert(Studio studio, String title,
         String text) {
      ensureWindowExists(studio);
      JPanel wrapper = new JPanel(new MigLayout("fill, flowy, insets 2, gap 0"));
      wrapper.add(new JLabel(text));
      DefaultAlert alert = new DefaultAlert(staticInstance_, title, wrapper);
      showWindowUnlessMuted(studio, alert);
      staticInstance_.addAlert(alert);
      return alert;
   }

   /**
    * Create a custom alert with any contents
    */
   public static DefaultAlert addCustomAlert(Studio studio, String title,
         JComponent contents) {
      ensureWindowExists(studio);
      DefaultAlert alert = new DefaultAlert(staticInstance_, title, contents);
      showWindowUnlessMuted(studio, alert);
      staticInstance_.addAlert(alert);
      return alert;
   }

   /**
    * Variant of the above specifically for MultiTextAlerts.
    */
   public static MultiTextAlert addTextAlert(Studio studio, String title) {
      ensureWindowExists(studio);
      MultiTextAlert alert = MultiTextAlert.createAlert(staticInstance_,
            title, new JPanel());
      showWindowUnlessMuted(studio, alert);
      staticInstance_.addAlert(alert);
      return alert;
   }

   private static final String NO_ALERTS_MSG = "There are no alerts at this time.";

   private Studio studio_;
   private final ArrayList<DefaultAlert> allAlerts_ = new ArrayList<DefaultAlert>();
   private final HashSet<String> mutedAlerts_ = new HashSet<String>();
   private final JPanel alertsPanel_ = new JPanel(new MigLayout("fill, flowy"));

   private AlertsWindow(Studio studio) {
      super("Alerts");
      studio_ = studio;

      setLayout(new MigLayout("fill, insets 2, gap 0"));

      JButton unmuteButton = new JButton("Unmute All Alerts");
      unmuteButton.setToolTipText("Unmutes all alerts, so that they will re-open this window when they occur in future.");
      unmuteButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            mutedAlerts_.clear();
            for (DefaultAlert alert : allAlerts_) {
               alert.setMuteButtonState(false);
            }
         }
      });
      add(unmuteButton, "split, span");
      JButton clearAllButton = new JButton("Clear All Alerts");
      clearAllButton.setToolTipText("Dismiss all alerts, removing them from this window.");
      clearAllButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            for (int i = allAlerts_.size() - 1; i >= 0; --i) {
               allAlerts_.get(i).dismiss();
            }
         }
      });
      add(clearAllButton, "gapright push, wrap");

      JScrollPane scroller = new JScrollPane(alertsPanel_);
      scroller.setBorder(null);
      alertsPanel_.add(new JLabel(NO_ALERTS_MSG));
      add(scroller, "push, grow");
      pack();
   }

   public void addAlert(DefaultAlert alert) {
      if (allAlerts_.isEmpty()) {
         // Remove the "there are no alerts" label.
         alertsPanel_.removeAll();
      }
      allAlerts_.add(alert);
      alertsPanel_.add(alert, "pushx, growx");
      studio_.events().post(new AlertCreatedEvent());
      pack();
   }

   public void removeAlert(DefaultAlert alert) {
      if (allAlerts_.contains(alert)) {
         allAlerts_.remove(alert);
         alertsPanel_.remove(alert);
         if (allAlerts_.isEmpty()) {
            studio_.events().post(new NoAlertsAvailableEvent());
            alertsPanel_.add(new JLabel(NO_ALERTS_MSG));
         }
      }
      pack();
   }

   /**
    * Muted alerts won't cause the window to be shown when they appear.
    */
   public void setMuted(DefaultAlert alert, boolean isMuted) {
      if (isMuted) {
         mutedAlerts_.add(alert.getTitle());
      }
      else if (mutedAlerts_.contains(alert.getTitle())) {
         mutedAlerts_.remove(alert.getTitle());
      }
   }

   public boolean isMuted(DefaultAlert alert) {
      return mutedAlerts_.contains(alert.getTitle());
   }
}
