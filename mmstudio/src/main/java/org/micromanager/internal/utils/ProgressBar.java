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

/**
 * Utility class to show a ProgressBar.
 * Note: this will need to be refactored in the near future.  The current
 * version has to be created on the EDT, which is sometimes difficult, and
 * often is not done, resulting in dangerous code.
 */
public final class ProgressBar extends JPanel {
   private static final long serialVersionUID = 1L;
   private final long delayTimeMs = 3000;
   private final long startTimeMs;
   private final JProgressBar progressBar;
   private final JFrame frame;

   /**
    * Constructor.
    *
    * @param parent Component to place the progressbar on top of
    * @param windowName Name of the progressbar window
    * @param start Start value
    * @param end Last value
    */
   @MustCallOnEDT
   public ProgressBar(Component parent, String windowName, int start, int end) {

      super(new BorderLayout());
      
      frame = new JFrame(windowName);
      frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      frame.setBounds(0, 0, 250 + 12 * windowName.length(), 100);

      progressBar = new JProgressBar(start, end);
      progressBar.setValue(0);
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(progressBar, BorderLayout.CENTER);
      super.add(panel, BorderLayout.CENTER);
      panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

      panel.setOpaque(true);
      frame.setContentPane(panel);

      frame.setLocationRelativeTo(parent);
      startTimeMs = System.currentTimeMillis();
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
      if (!frame.isVisible()) {
         if (System.currentTimeMillis() - startTimeMs > delayTimeMs) {
            frame.setVisible(true);
         }
      }
      if (frame.isVisible()) {
         progressBar.setValue(progress);
         progressBar.repaint();
      }
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