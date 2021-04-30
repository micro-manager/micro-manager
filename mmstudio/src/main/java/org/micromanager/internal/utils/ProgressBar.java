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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;


public final class ProgressBar extends JPanel {
   private static final long serialVersionUID = 1L;
   private final long delayTimeMs = 1000;
   private final long startTimeMs;
   private final JProgressBar progressBar;
   private final JFrame frame;

   @MustCallOnEDT
   public ProgressBar (Component parent, String windowName, int start, int end) {

      super(new BorderLayout());
      
      frame = new JFrame(windowName);
      frame.setDefaultCloseOperation (JFrame.DISPOSE_ON_CLOSE);
      frame.setBounds(0, 0, 250 + 12 * windowName.length(), 100);

      progressBar = new JProgressBar(start,end);
      progressBar.setValue(0);
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(progressBar, BorderLayout.CENTER);
      super.add(panel, BorderLayout.CENTER);
      panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

      JComponent newContentPane = panel;
      newContentPane.setOpaque(true);
      frame.setContentPane(newContentPane);

      frame.setLocationRelativeTo(parent);
      startTimeMs = System.currentTimeMillis();
   }

   public void setProgress(int progress) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            setProgress(progress);
         });
         return;
      }
      if (!frame.isVisible()) {
         if (System.currentTimeMillis() - startTimeMs > delayTimeMs) {
            frame.setVisible(true);
         }
      }
      progressBar.setValue(progress);
      progressBar.repaint();
   }

   @Override
   public void setVisible(boolean visible) {
      frame.setVisible(visible);
   }

    public void setRange(int min, int max) {
        progressBar.setMinimum(min);
        progressBar.setMaximum(max);
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
