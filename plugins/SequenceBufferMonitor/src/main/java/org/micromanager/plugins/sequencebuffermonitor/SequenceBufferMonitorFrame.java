/*
 * AUTHOR:       Mark Tsuchida
 * COPYRIGHT:    University of California, San Francisco, 2014
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.plugins.sequencebuffermonitor;

import org.micromanager.internal.utils.WindowPositioning;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;



class SequenceBufferMonitorFrame extends JFrame {
   org.micromanager.Studio app_;
   JProgressBar usageBar_;
   Timer timer_;

   int previousTotalCapacity_ = -1;
   int updateIntervalMs_ = 100;

   SequenceBufferMonitorFrame(org.micromanager.Studio app) {
      super("Sequence Buffer Monitor");
      setTitle("Sequence Buffer Monitor");

      app_ = app;

      usageBar_ = new JProgressBar();
      usageBar_.setStringPainted(true);

      JTextField intervalField =
         new JTextField(Integer.toString(updateIntervalMs_), 4);
      intervalField.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            JTextField field = (JTextField)e.getSource();
            String text = field.getText();
            int interval;
            try {
               interval = Integer.valueOf(text);
            }
            catch (NumberFormatException nfe) {
               field.setText(Integer.toString(updateIntervalMs_));
               return;
            }

            stop();
            updateIntervalMs_ = interval;
            start();
         }
      });

      setLayout(new net.miginfocom.swing.MigLayout(
               "insets dialog",
               "[grow, fill]",
               "[]related[]"));
      add(usageBar_, "wrap");
      add(new JLabel("Update Interval:"), "split 3, gapleft push");
      add(intervalField);
      add(new JLabel("ms"));

      Dimension size = usageBar_.getPreferredSize();
      usageBar_.setPreferredSize(new Dimension(2 * size.width, size.height));

      pack();
      setMinimumSize(getPreferredSize());
      setResizable(false);

      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent e) {
            stop();
         }
      });

      update();

      setLocation(200, 200);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
   }

   private void update() {
      mmcorej.CMMCore core = app_.getCMMCore();
      if (core == null) {
         usageBar_.setValue(0);
         usageBar_.setString("Core unavailable");
         return;
      }

      int total = core.getBufferTotalCapacity();
      int free = core.getBufferFreeCapacity();
      double percentage = 100.0 * (total - free) / total;

      if (total != previousTotalCapacity_) {
         usageBar_.setMaximum(total);
         previousTotalCapacity_ = total;
      }
      usageBar_.setValue(total - free);
      usageBar_.setString(Integer.toString(total - free) + "/" +
            Integer.toString(total) + " (" +
            Integer.toString((int)Math.round(percentage)) + "%)");
   }

   void start() {
      if (timer_ != null) {
         return;
      }

      ActionListener timerListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            update();
         }
      };
      timer_ = new Timer(updateIntervalMs_, timerListener);
      timer_.start();
   }

   void stop() {
      if (timer_ == null) {
         return;
      }

      timer_.stop();
      timer_ = null;
   }
}
