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

import ij.gui.ImageWindow;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;
import java.util.List;
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

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.display.internal.MMVirtualStack;
import org.micromanager.display.PixelsSetEvent;

import org.micromanager.internal.utils.ReportingUtils;


public class CommentsPanel extends InspectorPanel {
   private JTextArea imageCommentsTextArea_;
   private JTextArea summaryCommentsTextArea_;
   private ImageWindow currentWindow_;
   private DisplayWindow display_;
   private Datastore store_;
   private Timer updateTimer_;
   private HashMap<Image, Timer> imageToSaveTimer_;

   /** Creates new form CommentsPanel */
   public CommentsPanel() {
      imageToSaveTimer_ = new HashMap<Image, Timer>();
      initialize();
      addTextChangeListeners();
      addFocusListeners();
   }

   private void initialize() {
      JPanel summaryPanel = new JPanel(
            new MigLayout("flowy, insets 0, fillx"));

      summaryPanel.add(new JLabel("Acquisition comments:"));

      summaryCommentsTextArea_ = makeTextArea();
      summaryCommentsTextArea_.setToolTipText("Enter your comments for the whole acquisition here");

      summaryPanel.add(new JScrollPane(summaryCommentsTextArea_), "grow");

      JPanel commentsPanel = new JPanel(
            new MigLayout("flowy, insets 0, fillx"));

      commentsPanel.add(new JLabel("Per-image comments:"));

      imageCommentsTextArea_ = makeTextArea();
      imageCommentsTextArea_.setToolTipText("Comments for each image may be entered here.");

      commentsPanel.add(new JScrollPane(imageCommentsTextArea_), "grow");

      setLayout(new MigLayout("fillx"));
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

   /**
    * Every time we change the metadata on an Image, we have to call putImage()
    * on the Datastore to replace the old image, which in turn causes the
    * displayed image to "change" and our imageChangedUpdate method to be
    * called. To avoid flicker, we delay saves using a timer to wait until
    * after the user is done typing (5s).
    */
   private void recordCommentsChanges() {
      if (display_.getDisplayedImages().size() == 0) {
         // No images to record for.
         return;
      }
      Image curImage = display_.getDisplayedImages().get(0);
      // Determine if anything has actually changed.
      String imageText = imageCommentsTextArea_.getText();
      String summaryText = summaryCommentsTextArea_.getText();

      Metadata metadata = curImage.getMetadata();
      String oldComments = metadata.getComments();
      SummaryMetadata summary = store_.getSummaryMetadata();
      String oldSummary = summary.getComments();

      if (imageText.equals(oldComments) ||
            (imageText.equals("") && oldComments == null)) {
         // Don't update image text.
         imageText = null;
      }
      if (summaryText.equals(oldSummary) ||
            (summaryText.equals("") && oldSummary == null)) {
         // Don't update summary text.
         summaryText = null;
      }

      synchronized(imageToSaveTimer_) {
         if (imageToSaveTimer_.containsKey(curImage)) {
            // Cancel the current timer. Since the task is synchronized around
            // imageToSaveTimer_, there should be no race condition here.
            imageToSaveTimer_.get(curImage).cancel();
         }
         Timer timer = new Timer("Ephemeral comments save timer");
         timer.schedule(makeSaveTask(curImage, imageText, summaryText, store_),
               5000);
         imageToSaveTimer_.put(curImage, timer);
      }
   }

   /**
    * Create a task that will record changes in the comments text for the
    * given image. Only makes changes if the input strings are non-null.
    * Takes the Datastore as a passed-in parameter because setDisplay may
    * change store_ out from under us otherwise.
    */
   private TimerTask makeSaveTask(final Image image,
         final String imageText, final String summaryText,
         final Datastore store) {
      return new TimerTask() {
         @Override
         public void run() {
            synchronized(imageToSaveTimer_) {
               try {
                  if (imageText != null) {
                     // Comments have changed.
                     Metadata metadata = image.getMetadata();
                     metadata = metadata.copy().comments(imageText).build();
                     store.putImage(image.copyWithMetadata(metadata));
                  }
                  if (summaryText != null) {
                     // Summary comments have changed.
                     SummaryMetadata summary = store.getSummaryMetadata();
                     summary = summary.copy().comments(summaryText).build();
                     store.setSummaryMetadata(summary);
                  }
               }
               catch (DatastoreFrozenException e) {
                  ReportingUtils.showError("Comments cannot be changed because the datastore has been locked.");
                  // Prevent redundant errors by forcing the text back to
                  // what it should be.
                  Image curImage = display_.getDisplayedImages().get(0);
                  imageCommentsTextArea_.setText(curImage.getMetadata().getComments());
                  summaryCommentsTextArea_.setText(store.getSummaryMetadata().getComments());
               }
               imageToSaveTimer_.remove(image);
            }
         }
      };
   }

   /**
    * TODO: why do we care about losing focus?
    */
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
    * We postpone comments display updates slightly in case the image display
    * is changing rapidly, to ensure that we don't end up with a race condition
    * that causes us to display the wrong metadata.
    */
   public synchronized void imageChangedUpdate(final Image image) {
      // Do nothing if the new image's comments match our current contents,
      // to avoid reseting the cursor position.
      String newComments = image.getMetadata().getComments();
      if (newComments != null &&
            newComments.contentEquals(imageCommentsTextArea_.getText())) {
         return;
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
      if (updateTimer_ != null) {
         updateTimer_.cancel();
      }
      updateTimer_ = new Timer("Metadata update");
      updateTimer_.schedule(task, 125);
   }

   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      try {
         imageChangedUpdate(event.getImage());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error on pixels set");
      }
   }

   @Override
   public synchronized void setDisplay(DisplayWindow display) {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
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
      imageCommentsTextArea_.setEditable(!store_.getIsFrozen());
      summaryCommentsTextArea_.setEditable(!store_.getIsFrozen());
      summaryCommentsTextArea_.setText(
            store_.getSummaryMetadata().getComments());
      display_.registerForEvents(this);
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
   }
}
