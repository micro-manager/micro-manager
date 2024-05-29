///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, December, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$
//

package org.micromanager.internal.utils;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Utility class to show a ProgressBar.
 * Construction of the ProgressBar is done on the EDT, on the first call of
 * SetProgress.  Thus, it is safe to call this class from any thread.
 */
public final class ProgressBar extends JPanel {
   private static final long serialVersionUID = 1L;
   private final long delayTimeMs_ = 3000;
   private final long startTimeMs_;
   private JProgressBar progressBar_;
   private JFrame frame_;

   private final Component parent_;
   private final String windowName_;
   private int start_;
   private int end_;

   /**
    * Constructor.
    *
    * @param parent     Component to place the progressbar on top of
    * @param windowName Name of the progressbar window
    * @param start      Start value
    * @param end        Last value
    */
   public ProgressBar(Component parent, String windowName, int start, int end) {
      super(new BorderLayout());
      parent_ = parent;
      windowName_ = windowName;
      start_ = start;
      end_ = end;
      startTimeMs_ = System.currentTimeMillis();
   }

   @MustCallOnEDT
   private void initialize() {
      frame_ = new JFrame(windowName_);
      frame_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      frame_.setBounds(0, 0, 250 + 12 * windowName_.length(), 110);

      progressBar_ = new JProgressBar(start_, end_);
      progressBar_.setValue(0);
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(progressBar_, BorderLayout.CENTER);
      super.add(panel, BorderLayout.CENTER);
      panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

      panel.setOpaque(true);
      frame_.setContentPane(panel);

      frame_.setLocationRelativeTo(parent_);
   }

   /**
    * Updates the Progress bar.
    *
    * @param progress where to set the bar to.
    */
   public void setProgress(int progress) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            setProgress(progress);
         });
         return;
      }
      if (frame_ == null) {
         initialize();
      }
      if (!frame_.isVisible()) {
         if (System.currentTimeMillis() - startTimeMs_ > delayTimeMs_) {
            frame_.setVisible(true);
         }
      }
      if (frame_.isVisible()) {
         progressBar_.setValue(progress);
         progressBar_.repaint();
      }
      if (progress >= end_) {
         frame_.setVisible(false);
      }
   }

   @Override
   public void setVisible(boolean visible) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            setVisible(visible);
         });
         return;
      }
      if (visible && frame_ == null) {
         initialize();
      }
      if (frame_ != null) {
         frame_.setVisible(visible);
      }
   }

   public void setRange(int min, int max) {
      progressBar_.setMinimum(min);
      progressBar_.setMaximum(max);
   }

   /*
   public static void main(String[] args) {
      ProgressBar testBar = new ProgressBar ("Opening File...", 0, 100);
      for (int i=0; i<=100; i++) {
         try {
            Thread.sleep(10);
         } catch (InterruptedException ignore) { ReportingUtils.logError(ignore); }
         testBar.setProgress(i);
      }
      testBar.setVisible(false);
   }
   */
}