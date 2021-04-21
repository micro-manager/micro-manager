package org.micromanager.internal.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public final class EDTProfiler {
  private static final int INTERVAL = 100;

  private Timer timer_;
  private final LinkedList<Long> startTimes_;
  private final LinkedList<Long> executionTimes_;

  public EDTProfiler() {
    timer_ = new Timer();
    startTimes_ = new LinkedList<Long>();
    executionTimes_ = new LinkedList<Long>();
    JFrame frame = new JFrame();
    JButton stop = new JButton("stop");
    stop.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            end();
          }
        });
    JButton start = new JButton("start");
    start.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            start();
          }
        });
    frame.setLayout(new GridLayout(2, 1));
    frame.add(start);
    frame.add(stop);
    frame.pack();
    frame.setVisible(true);
  }

  private void start() {
    timer_.schedule(getTask(), 0, INTERVAL);
  }

  private void end() {
    timer_.cancel();
    for (int i = 0; i < startTimes_.size(); i++) {
      String print = startTimes_.get(i) + "\t";
      if (i < executionTimes_.size()) {
        print += executionTimes_.get(i) + "";
      }
      System.out.println(print);
    }
  }

  private TimerTask getTask() {
    return new TimerTask() {
      @Override
      public void run() {
        startTimes_.add(System.currentTimeMillis());
        SwingUtilities.invokeLater(
            new Runnable() {
              @Override
              public void run() {
                executionTimes_.add(System.currentTimeMillis());
              }
            });
      }
    };
  }
}
