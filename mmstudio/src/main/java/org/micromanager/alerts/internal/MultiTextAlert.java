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

import java.awt.Dimension;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;

/**
 * Subclass of DefaultAlert intended for showing multiple lines of alert texts,
 * using a scrollpane if necessary.
 */
public class MultiTextAlert extends DefaultAlert {
   // Going over this limit causes us to embed things in a scrollpane.
   private static final int MAX_LINES = 10;

   private JPanel contents_;
   private JScrollPane scroller_ = null;
   private JPanel scrollerContents_ = null;
   // Holds text lines we have been handed.
   private ArrayList<JLabel> lines_ = new ArrayList<JLabel>();

   /**
    * Sets up the contents of the alert before passing them to the constructor,
    * so they can in turn be passed to the DefaultAlert constructor.
    */
   public static MultiTextAlert addAlert(Studio studio) {
      JPanel contents = new JPanel(new MigLayout("flowy"));
      return new MultiTextAlert(studio, contents);
   }

   private MultiTextAlert(Studio studio, JPanel contents) {
      super(studio, contents, false);
      contents_ = contents;
   }

   /**
    * Add a new label to our contents, embedding everything into a JScrollPane
    * if necessary.
    */
   public void addText(String text) {
      JLabel label = new JLabel(text);
      lines_.add(label);
      if (lines_.size() == MAX_LINES) {
         // Hit the size limit.
         contents_.removeAll();
         scrollerContents_ = new JPanel(
               new MigLayout("fill, flowy, insets 0"));
         scroller_ = new JScrollPane(scrollerContents_) {
            @Override
            public Dimension getPreferredSize() {
               return new Dimension(300, 200);
            }
         };
         scroller_.addMouseListener(showCloseButtonAdapter_);
         contents_.add(scroller_, "grow");
         for (JLabel line : lines_) {
            scrollerContents_.add(line, "growx");
         }
      }
      else if (lines_.size() > MAX_LINES) {
         scrollerContents_.add(label, "growx");
      }
      else {
         contents_.add(label, "growx");
      }
      pack();
      // HACK: if we don't do this, then the scrollpane shrinks to a tiny size
      // each time we call this method, until the user mouses over it. No idea
      // why.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            getContentPane().layout();
         }
      });
   }
}
