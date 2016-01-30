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

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.display.DataViewer;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.PixelsSetEvent;

import org.micromanager.PropertyMap;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;


public class CommentsPanel extends InspectorPanel {
   /** File that comments are saved in. */
   private static final String COMMENTS_FILE = "comments.txt";
   /** String key used to access comments in annotations. */
   private static final String COMMENTS_KEY = "comments";

   private JTextArea imageCommentsTextArea_;
   private JTextArea summaryCommentsTextArea_;
   private boolean shouldIgnoreUpdates_ = false;
   private JLabel errorLabel_;
   private DataViewer display_;
   private Datastore store_;
   private Annotation annotation_;
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
            new MigLayout("flowy, insets 0, fillx"));

      errorLabel_ = new JLabel("                                          ");
      summaryPanel.add(errorLabel_);
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

   private void recordCommentsChanges() {
      if (display_.getDisplayedImages().isEmpty()) {
         // No images to record for.
         return;
      }
      if (shouldIgnoreUpdates_) {
         // We're in the middle of manually updating the text fields.
         return;
      }
      if (annotation_ == null) {
         // Unable to load annotation.
         return;
      }
      Coords imageCoords = display_.getDisplayedImages().get(0).getCoords();
      // Determine if anything has actually changed.
      String imageText = imageCommentsTextArea_.getText();
      String summaryText = summaryCommentsTextArea_.getText();

      PropertyMap imageProps = annotation_.getImageAnnotation(imageCoords);
      if (imageProps == null) {
         imageProps = MMStudio.getInstance().data().getPropertyMapBuilder().build();
      }
      imageProps = imageProps.copy().putString(COMMENTS_KEY, imageText).build();
      annotation_.setImageAnnotation(imageCoords, imageProps);

      PropertyMap generalProps = annotation_.getGeneralAnnotation();
      if (generalProps == null) {
         generalProps = MMStudio.getInstance().data().getPropertyMapBuilder().build();
      }
      generalProps = generalProps.copy().putString(COMMENTS_KEY, summaryText).build();
      annotation_.setGeneralAnnotation(generalProps);
      try {
         annotation_.save();
      }
      catch (IOException e) {
         errorLabel_.setText("Error writing comments to disk.");
         ReportingUtils.logError(e, "Error writing comments to disk");
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
      if (annotation_ == null) {
         // Unable to get annotation.
         return;
      }
      curImage_ = image;
      Coords coords = image.getCoords();
      if (annotation_.getImageAnnotation(coords) != null) {
         final String newComments = annotation_.getImageAnnotation(coords).getString(COMMENTS_KEY, "");
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

      try {
         annotation_ = store_.loadAnnotation(COMMENTS_FILE);
      }
      catch (IOException e) {
         errorLabel_.setText("Unable to load comments file.");
      }

      shouldIgnoreUpdates_ = true;
      if (annotation_.getGeneralAnnotation() != null) {
         summaryCommentsTextArea_.setText(annotation_.getGeneralAnnotation().getString(COMMENTS_KEY, ""));
      }
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

   /**
    * Returns the summary comment for the specified Datastore, or "" if it
    * does not exist.
    */
   public static String getSummaryComment(Datastore store) {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";
      }
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         if (annotation.getGeneralAnnotation() == null) {
            return "";
         }
         return annotation.getGeneralAnnotation().getString(COMMENTS_KEY, "");
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error accessing comments annotation");
         return "";
      }
   }

   /**
    * Write a new summary comment for the given Datastore.
    */
   public static void setSummaryComment(Datastore store, String comment) {
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         PropertyMap prop = annotation.getGeneralAnnotation();
         if (prop == null) {
            prop = MMStudio.getInstance().data().getPropertyMapBuilder().build();
         }
         prop = prop.copy().putString(COMMENTS_KEY, comment).build();
         annotation.setGeneralAnnotation(prop);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error setting summary comment");
      }
   }

   /**
    * Returns the comment for the specified Image in the specified Datastore,
    * or "" if it does not exist.
    */
   public static String getImageComment(Datastore store, Coords coords) {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";
      }
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         if (annotation.getImageAnnotation(coords) == null) {
            return "";
         }
         return annotation.getImageAnnotation(coords).getString(COMMENTS_KEY, "");
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error accessing comments annotation");
         return "";
      }
   }

   /**
    * Write a new image comment for the given Datastore.
    */
   public static void setImageComment(Datastore store, Coords coords,
         String comment) {
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         PropertyMap prop = annotation.getImageAnnotation(coords);
         if (prop == null) {
            prop = MMStudio.getInstance().data().getPropertyMapBuilder().build();
         }
         prop = prop.copy().putString(COMMENTS_KEY, comment).build();
         annotation.setImageAnnotation(coords, prop);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error setting image comment");
      }
   }

   /**
    * Return true if there's a comments annotation.
    */
   public static boolean hasAnnotation(Datastore store) {
      return store.hasAnnotation(COMMENTS_FILE);
   }

   /**
    * Create a new comments annotation.
    */
   public static void createAnnotation(Datastore store) {
      store.createNewAnnotation(COMMENTS_FILE);
   }

   /**
    * Save the store's comments annotation.
    */
   public static void save(Datastore store) {
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         annotation.save();
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error saving comment annotations");
      }
   }
}
