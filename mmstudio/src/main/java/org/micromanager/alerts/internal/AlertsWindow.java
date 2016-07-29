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

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

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
      if (staticInstance_.isMuted(alert) || !staticInstance_.shouldShowOnMessage_) {
         return;
      }
      show(studio);
   }

   /**
    * Create a simple alert with a text message.
    */
   public static DefaultAlert addUpdatableAlert(Studio studio, String title,
         String text) {
      ensureWindowExists(studio);
      DefaultAlert alert = new DefaultAlert(staticInstance_, title,
            new JLabel(text));
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
    * Create an alert that can contain multiple categories of messages.
    */
   public static CategorizedAlert addCategorizedAlert(Studio studio, String title) {
      ensureWindowExists(studio);
      CategorizedAlert alert = CategorizedAlert.createAlert(staticInstance_, title);
      showWindowUnlessMuted(studio, alert);
      staticInstance_.addAlert(alert);
      return alert;
   }

   private static final String NO_ALERTS_MSG = "There are no messages at this time.";
   private static final String SHOULD_SHOW_WINDOW = "Show the Messages window when a message is received";

   private Studio studio_;
   private final ArrayList<DefaultAlert> allAlerts_ = new ArrayList<DefaultAlert>();
   private final HashSet<String> mutedAlerts_ = new HashSet<String>();
   private final JPanel alertsPanel_ = new JPanel(new MigLayout("insets 0, fill, flowy"));
   private boolean shouldShowOnMessage_ = true;

   private AlertsWindow(Studio studio) {
      super("Messages");
      studio_ = studio;

      setLayout(new MigLayout("fill, insets 2, gap 0"));

      Font defaultFont = new Font("Arial", Font.PLAIN, 10);

      shouldShowOnMessage_ = studio_.profile().getBoolean(AlertsWindow.class,
            SHOULD_SHOW_WINDOW, true);
      final JCheckBox showWindowCheckBox = new JCheckBox(
            "Open this window when messages arrive", shouldShowOnMessage_);
      showWindowCheckBox.setFont(defaultFont);
      showWindowCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            shouldShowOnMessage_ = showWindowCheckBox.isSelected();
            studio_.profile().setBoolean(AlertsWindow.class,
               SHOULD_SHOW_WINDOW, shouldShowOnMessage_);
         }
      });
      add(showWindowCheckBox, "split, span");

      JButton clearAllButton = new JButton("Clear All");
      clearAllButton.setFont(defaultFont);
      clearAllButton.setToolTipText("Dismiss all alerts, removing them from this window.");
      clearAllButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Reverse iteration because we're modifying allAlerts_ as we
            // iterate over it.
            for (int i = allAlerts_.size() - 1; i >= 0; --i) {
               allAlerts_.get(i).dismiss();
            }
         }
      });
      add(clearAllButton, "gapleft push, wrap");

      JScrollPane scroller = new JScrollPane(alertsPanel_,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scroller.setBorder(null);
      alertsPanel_.add(new JLabel(NO_ALERTS_MSG));
      add(scroller, "push, grow");
      pack();
   }

   public void addAlert(DefaultAlert alert) {
      if (allAlerts_.isEmpty()) {
         // Remove the "there are no alerts" label.
         alertsPanel_.removeAll();
         alertsPanel_.revalidate();
      }
      allAlerts_.add(alert);
      alertsPanel_.add(alert, "pushx, growx, gaptop 0, gapbottom 0");
      studio_.events().post(new AlertUpdatedEvent(alert));
      packLater();
   }

   public void removeAlert(DefaultAlert alert) {
      if (allAlerts_.contains(alert)) {
         allAlerts_.remove(alert);
         alertsPanel_.remove(alert);
         if (allAlerts_.isEmpty()) {
            studio_.events().post(new NoAlertsAvailableEvent());
            alertsPanel_.add(new JLabel(NO_ALERTS_MSG));
         }
         alertsPanel_.revalidate();
         studio_.events().post(new AlertClearedEvent(alert));
      }
      packLater();
   }

   // HACK: for some reason, packing immediately often results in layout
   // errors, so we wait to pack until a later pass by the EDT.
   private void packLater() {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            pack();
         }
      });
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

   public void textUpdated(DefaultAlert alert) {
      studio_.events().post(new AlertUpdatedEvent(alert));
   }
}
