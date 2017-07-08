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
package org.micromanager.duplicator;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import net.miginfocom.swing.MigLayout;


public final class ProgressBar extends JFrame {
   private final JProgressBar progressBar;
   private final AtomicBoolean cancel = new AtomicBoolean(false);

   public ProgressBar (String name, int start, int end) {
      
      super(name);
      super.setDefaultCloseOperation (JFrame.DISPOSE_ON_CLOSE);
      super.setBounds(0,0,150 + 5 * name.length() ,100);

      progressBar = new JProgressBar(start,end);
      progressBar.setValue(0);
      
      JPanel panel = new JPanel(new MigLayout("flowx, fill, insets 8"));
      panel.add(progressBar, "growx, wrap");
      
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener(){
         @Override
         public void actionPerformed(ActionEvent ae) {
            cancel.set(true);
         }
      });
      panel.add(cancelButton, "center");

      panel.setOpaque(true);
      super.setContentPane(panel);
      super.setLocationRelativeTo(null);
      super.setVisible(true);
   }

   public void setProgress(int progress) {
      progressBar.setValue(progress);
      progressBar.repaint();
   }

    public void setRange(int min, int max) {
        progressBar.setMinimum(min);
        progressBar.setMaximum(max);
    }

    public boolean isCancelled() {
       return cancel.get();
    }

}
