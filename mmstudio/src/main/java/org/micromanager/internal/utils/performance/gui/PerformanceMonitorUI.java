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

import org.micromanager.internal.utils.performance.PerformanceMonitor;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Display time series performance statistics in a frame.
 *
 * @author Mark A. Tsuchida
 */
public class PerformanceMonitorUI {
  private final PerformanceMonitor monitor_;
  private Timer timer_;

  private PerformanceMonitorTableModel model_;
  private JTable table_;
  private JScrollPane scrollPane_;
  private JFrame frame_;

  private static final String SYSPROP = "org.micromanager.showperfmon";

  static {
    // Temporary - for debugging
    // System.setProperty("org.micromanager.showperfmon", "true");
  }

  public static PerformanceMonitorUI create(PerformanceMonitor monitor, String title) {
    return new PerformanceMonitorUI(monitor, title);
  }

  private PerformanceMonitorUI(PerformanceMonitor monitor, final String title) {
    monitor_ = monitor;
    if (!Boolean.getBoolean(SYSPROP)) {
      return;
    }

    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            showUI(title);
          }
        });
  }

  private void showUI(String title) {
    model_ = new PerformanceMonitorTableModel();
    table_ = new JTable(model_);
    scrollPane_ = new JScrollPane(table_);
    frame_ = new JFrame();

    table_.setFillsViewportHeight(true);

    frame_.setTitle(title);
    frame_.add(scrollPane_);
    frame_.pack();

    frame_.setVisible(true);
    timer_ =
        new Timer(
            1000,
            new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                model_.setData(monitor_.getEntries());
              }
            });
    timer_.start();
  }
}
