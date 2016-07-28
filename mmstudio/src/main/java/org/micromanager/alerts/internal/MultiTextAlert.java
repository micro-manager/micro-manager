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

   private JScrollPane scroller_;
   private JPanel scrollerContents_;
   // Holds text lines we have been handed.
   private ArrayList<JLabel> lines_ = new ArrayList<JLabel>();

   /**
    * Sets up the contents of the alert before passing them to the constructor,
    * so they can in turn be passed to the DefaultAlert constructor.
    */
   public static MultiTextAlert createAlert(AlertsWindow parent, String title,
         JPanel header) {
      JPanel contents = new JPanel(new MigLayout("fill, flowy, insets 0, gap 0"));
      contents.add(header, "gapbottom 2, pushx, growx");
      return new MultiTextAlert(parent, title, contents);
   }

   private MultiTextAlert(AlertsWindow parent, String title, JPanel contents) {
      super(parent, title, contents);
      scrollerContents_ = new JPanel(new MigLayout("fill, insets 0, gap 0, flowy"));
      scroller_ = new JScrollPane(scrollerContents_);
      scroller_.addMouseListener(showCloseButtonAdapter_);
      // Don't let the scroller grow too huge.
      contents.add(scroller_, "pushx, growx, height ::300");
   }

   /**
    * Add a new label to our contents.
    */
   public void addText(String text) {
      JLabel label = new JLabel(text);
      lines_.add(label);
      // Update our summary text.
      text_ = text;
      scrollerContents_.add(label, "growx");
      // Scroll to the bottom. We invoke this later as the scrollbar needs
      // a chance to recognize that its scrollable range has changed.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            scroller_.getVerticalScrollBar().setValue(
                  scroller_.getVerticalScrollBar().getMaximum());
         }
      });
      parent_.pack();
      // HACK: for some reason if we don't do this, our viewable area is tiny.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            invalidate();
            parent_.validate();
         }
      });
      parent_.textUpdated(this);
   }

   @Override
   public void setText(String text) {
      addText(text);
      super.setText(text);
   }
}
