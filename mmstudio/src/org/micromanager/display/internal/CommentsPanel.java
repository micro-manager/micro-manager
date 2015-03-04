///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal;

import ij.gui.ImageWindow;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.internal.utils.ReportingUtils;


public class CommentsPanel extends JPanel {
   private JTextArea imageCommentsTextArea_;
   private JTextArea summaryCommentsTextArea_;
   private ImageWindow currentWindow_;
   private Datastore store_;
   private MMVirtualStack stack_;
   private Timer updateTimer_;

   /** Creates new form CommentsPanel */
   public CommentsPanel(Datastore store, MMVirtualStack stack) {
      store_ = store;
      stack_ = stack;
      initialize();
      addTextChangeListeners();
      addFocusListeners();
   }

   private void initialize() {
      JPanel summaryPanel = new JPanel(new MigLayout("flowy"));

      summaryPanel.add(new JLabel("Acquisition comments:"));

      summaryCommentsTextArea_ = makeTextArea();
      summaryCommentsTextArea_.setToolTipText("Enter your comments for the whole acquisition here");

      summaryPanel.add(new JScrollPane(summaryCommentsTextArea_), "grow");

      JPanel commentsPanel = new JPanel(new MigLayout("flowy"));

      commentsPanel.add(new JLabel("Per-image comments:"));

      imageCommentsTextArea_ = makeTextArea();
      imageCommentsTextArea_.setToolTipText("Comments for each image may be entered here.");

      commentsPanel.add(new JScrollPane(imageCommentsTextArea_), "grow");

      setLayout(new MigLayout());
      JSplitPane splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
            summaryPanel, commentsPanel);
      // Don't draw a border around the outside of the SplitPane.
      splitter.setBorder(null);
      splitter.setResizeWeight(.5);
      add(splitter, "grow");
   }

   private JTextArea makeTextArea() {
      JTextArea result = new JTextArea();
      result.setLineWrap(true);
      // Semi-experimentally-derived sizes that look decent.
      result.setRows(6);
      result.setColumns(22);
      result.setTabSize(3);
      result.setWrapStyleWord(true);
      return result;
   }

   private void recordCommentsChanges() {
      Image curImage = store_.getImage(stack_.getCurrentImageCoords());
      Metadata metadata = curImage.getMetadata();
      try {
         String oldComments = metadata.getComments();
         if (oldComments == null ||
               !oldComments.equals(imageCommentsTextArea_.getText())) {
            metadata = metadata.copy().comments(imageCommentsTextArea_.getText()).build();
            store_.putImage(curImage.copyWithMetadata(metadata));
         }
         SummaryMetadata summary = store_.getSummaryMetadata();
         String oldSummary = summary.getComments();
         if (oldSummary == null ||
               !oldSummary.equals(summaryCommentsTextArea_.getText())) {
            summary = summary.copy().comments(summaryCommentsTextArea_.getText()).build();
            store_.setSummaryMetadata(summary);
         }
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.showError("Comments cannot be changed because the datastore has been locked.");
      }
   }

   private void addFocusListeners() {
      FocusListener listener = new FocusListener() {
         @Override
         public void focusGained(FocusEvent event) { }
         @Override
         public void focusLost(FocusEvent event) {
            recordCommentsChanges();
         }
      };
      summaryCommentsTextArea_.addFocusListener(listener);
      imageCommentsTextArea_.addFocusListener(listener);
   }
   
   private void addTextChangeListeners() {
      DocumentListener listener = new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            recordCommentsChanges();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            recordCommentsChanges();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            recordCommentsChanges();
         }
      };
      summaryCommentsTextArea_.getDocument().addDocumentListener(listener);
      imageCommentsTextArea_.getDocument().addDocumentListener(listener);
   }

   /**
    * We postpone metadata display updates slightly in case the image display
    * is changing rapidly, to ensure that we don't end up with a race condition
    * that causes us to display the wrong metadata.
    */
   public void imageChangedUpdate(final Image image) { 
      if (updateTimer_ == null) {
         updateTimer_ = new Timer("Metadata update");
      }
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            Metadata data = image.getMetadata();
            // Update image comment
            imageCommentsTextArea_.setText(data.getComments());
         }
      };
      // Cancel all pending tasks and then schedule our task for execution
      // 125ms in the future.
      updateTimer_.purge();
      updateTimer_.schedule(task, 125);
   }
}
