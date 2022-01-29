// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.internal.utils.performance.gui;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 * Display time series performance statistics in a frame.
 *
 * @author Mark A. Tsuchida
 */
public class PerformanceMonitorUI {
   private final PerformanceMonitor monitor_;
   private PerformanceMonitorTableModel model_;

   private static final String SYSPROP = "org.micromanager.showperfmon";

   static {
      // Temporary - for debugging
      // System.setProperty("org.micromanager.showperfmon", "true");
   }

   public static PerformanceMonitorUI create(PerformanceMonitor monitor,
         String title) {
      return new PerformanceMonitorUI(monitor, title);
   }

   private PerformanceMonitorUI(PerformanceMonitor monitor, final String title) {
      monitor_ = monitor;
      if (!Boolean.getBoolean(SYSPROP)) {
         return;
      }

      SwingUtilities.invokeLater(() -> showUI(title));
   }

   private void showUI(String title) {
      model_ = new PerformanceMonitorTableModel();
      JTable table = new JTable(model_);
      JScrollPane scrollPane = new JScrollPane(table);
      JFrame frame = new JFrame();

      table.setFillsViewportHeight(true);

      frame.setTitle(title);
      frame.add(scrollPane);
      frame.pack();

      frame.setVisible(true);
      Timer timer = new Timer(1000, e -> model_.setData(monitor_.getEntries()));
      timer.start();
   }
}