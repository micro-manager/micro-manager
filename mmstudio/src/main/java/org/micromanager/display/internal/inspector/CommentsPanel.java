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

package org.micromanager.display.internal.inspector;

import com.google.common.eventbus.Subscribe;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.internal.CommentsHelper;
import org.micromanager.display.DataViewer;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.PixelsSetEvent;
import org.micromanager.internal.utils.ReportingUtils;


public final class CommentsPanel extends InspectorPanel {
   private JTextArea imageCommentsTextArea_;
   private JTextArea summaryCommentsTextArea_;
   private boolean shouldIgnoreUpdates_ = false;
   private JLabel errorLabel_;
   private DataViewer display_;
   private Datastore store_;
   private Thread updateThread_;
   private boolean shouldShowUpdates_ = true;
   private Image curImage_ = null;
   private LinkedBlockingQueue<Image> updateQueue_;

   public CommentsPanel() {
      updateQueue_ = new LinkedBlockingQueue<Image>();
      initialize();
      addTextChangeListeners();

      // Start a new thread to handle display updates.
      updateThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            updateComments();
         }
      });
      updateThread_.start();
   }

   private void initialize() {
      JPanel summaryPanel = new JPanel(
            new MigLayout("flowy, insets 0, fill"));

      errorLabel_ = new JLabel("                                          ");
      summaryPanel.add(errorLabel_, "pushy 0");
      summaryPanel.add(new JLabel("Acquisition comments:"), "pushy 0");

      summaryCommentsTextArea_ = makeTextArea();
      summaryCommentsTextArea_.setToolTipText("Enter your comments for the whole acquisition here");

      summaryPanel.add(new JScrollPane(summaryCommentsTextArea_), "grow, pushy 100");

      JPanel commentsPanel = new JPanel(
            new MigLayout("flowy, insets 0, fill"));

      commentsPanel.add(new JLabel("Per-image comments:"), "pushy 0");

      imageCommentsTextArea_ = makeTextArea();
      imageCommentsTextArea_.setToolTipText("Comments for each image may be entered here.");

      commentsPanel.add(new JScrollPane(imageCommentsTextArea_), "grow, pushy 100");

      setLayout(new MigLayout("fill"));
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
      result.setRows(2);
      result.setColumns(22);
      result.setTabSize(3);
      result.setWrapStyleWord(true);
      return result;
   }

   private void recordCommentsChanges() {
      if (display_.getDisplayedImages().isEmpty()) {
         // No images to record for.
         return;
      }
      if (shouldIgnoreUpdates_) {
         // We're in the middle of manually updating the text fields.
         return;
      }
      Coords imageCoords = display_.getDisplayedImages().get(0).getCoords();
      // Determine if anything has actually changed.
      String imageText = imageCommentsTextArea_.getText();
      String summaryText = summaryCommentsTextArea_.getText();

      CommentsHelper.setImageComment(store_, imageCoords, imageText);
      CommentsHelper.setSummaryComment(store_, summaryText);
      try {
         CommentsHelper.saveComments(store_);
      }
      catch (Exception e) {
         // Only log exceptions if we expect saving to have succeeded.
         if (store_.getSavePath() != null) {
            errorLabel_.setText("Error writing comments to disk.");
            ReportingUtils.logError(e, "Error writing comments to disk");
         }
      }
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
    * This method runs in a new thread and continually polls updateQueue_ for
    * images whose comments need to be updated. We use this method to ensure
    * that, if the image display is changing rapidly, we don't overload the
    * GUI.
    */
   private void updateComments() {
      while (shouldShowUpdates_) {
         Image image = null;
         while (!updateQueue_.isEmpty()) {
            image = updateQueue_.poll();
         }
         if (image == null) {
            // No updates available; just wait for a bit.
            try {
               Thread.sleep(100);
            }
            catch (InterruptedException e) {
               if (!shouldShowUpdates_) {
                  return;
               }
            }
            continue;
         }
         imageChangedUpdate(image);
      }
   }

   /**
    * Get the comments from the image and update our text fields.
    */
   public synchronized void imageChangedUpdate(final Image image) {
      // Do nothing if the new image's comments match our current contents,
      // to avoid reseting the cursor position.
      if (image == curImage_) {
         return;
      }
      curImage_ = image;
      Coords coords = image.getCoords();
      final String newComments = CommentsHelper.getImageComment(store_, coords);
      // Run this in the EDT.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            shouldIgnoreUpdates_ = true;
            imageCommentsTextArea_.setText(newComments);
            shouldIgnoreUpdates_ = false;
         }
      });
   }

   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      try {
         updateQueue_.put(event.getImage());
      }
      catch (InterruptedException e) {
         ReportingUtils.logError(e, "Interrupted when enqueueing image for comments update");
      }
   }

   @Override
   public synchronized void setDataViewer(DataViewer display) {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
            store_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
      }
      // Set these before we register for the new display's events, so we have
      // them set properly before imageChangedUpdate() can be called.
      display_ = display;
      if (display_ == null) {
         return;
      }
      store_ = display_.getDatastore();

      shouldIgnoreUpdates_ = true;
      summaryCommentsTextArea_.setText(
            CommentsHelper.getSummaryComment(store_));
      shouldIgnoreUpdates_ = false;
      display_.registerForEvents(this);
      store_.registerForEvents(this);
      List<Image> images = display_.getDisplayedImages();
      if (images.size() > 0) {
         imageChangedUpdate(images.get(0));
      }
   }

   @Override
   public void setInspector(Inspector inspector) {
      // We don't care.
   }

   @Override
   public synchronized void cleanup() {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
      }
      shouldShowUpdates_ = false;
      updateThread_.interrupt(); // It will then close.
   }
}
