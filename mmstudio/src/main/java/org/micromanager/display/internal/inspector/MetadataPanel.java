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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;
import java.util.UUID;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.PixelsSetEvent;

import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class handles displaying metadata about the currently-displayed image.
 * As with several other aspects of the GUI that do things about the "current
 * image", we maintain a queue of images that are pending updates, and discard
 * all but the most recent image so that we don't fall behind during periods
 * of rapid display updates.
 */
public class MetadataPanel extends InspectorPanel {
   private JTable imageMetadataTable_;
   private JCheckBox showUnchangingPropertiesCheckbox_;
   private JTable summaryMetadataTable_;
   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private ImageWindow currentWindow_;
   private Datastore store_;
   private DataViewer display_;
   private Thread updateThread_;
   private LinkedBlockingQueue<Image> updateQueue_;
   private boolean shouldShowUpdates_ = true;
   private UUID lastImageUUID_ = null;

   /** This class makes smaller JTables, since the default size is absurd. */
   private class SmallerJTable extends JTable {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
         Dimension defSize = super.getPreferredScrollableViewportSize();
         return new Dimension(Math.min(defSize.width, 325),
               Math.min(defSize.height, 75));
      }
   }

   /** Creates new form MetadataPanel */
   public MetadataPanel() {
      imageMetadataModel_ = new MetadataTableModel();
      summaryMetadataModel_ = new MetadataTableModel();
      initialize();
      imageMetadataTable_.setModel(imageMetadataModel_);
      summaryMetadataTable_.setModel(summaryMetadataModel_);
      updateQueue_ = new LinkedBlockingQueue<Image>();
      updateThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            updateMetadata();
         }
      });
      updateThread_.start();
   }

   private void initialize() {
      JSplitPane metadataSplitPane = new JSplitPane();
      JPanel imageMetadataScrollPane = new JPanel();
      JScrollPane imageMetadataTableScrollPane = new JScrollPane();
      imageMetadataTable_ = new SmallerJTable();
      showUnchangingPropertiesCheckbox_ = new JCheckBox();
      JLabel jLabel2 = new JLabel();
      JPanel summaryMetadataPanel = new JPanel();
      JScrollPane summaryMetadataScrollPane = new JScrollPane();
      summaryMetadataTable_ = new SmallerJTable();
      JLabel jLabel3 = new JLabel();

      metadataSplitPane.setBorder(null);
      metadataSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

      summaryMetadataTable_.setModel(new DefaultTableModel(
              new Object[][]{
                 {null, null},
                 {null, null},
                 {null, null},
                 {null, null}
              },
              new String[]{
                 "Property", "Value"
              }) {

         boolean[] canEdit = new boolean[]{
            false, false
         };

         @Override
         public boolean isCellEditable(int rowIndex, int columnIndex) {
            return canEdit[columnIndex];
         }
      });
      summaryMetadataTable_.setToolTipText(
            "Metadata tags for the whole acquisition");
      summaryMetadataScrollPane.setViewportView(summaryMetadataTable_);

      jLabel3.setText("Summary metadata");

      summaryMetadataPanel.setLayout(new MigLayout("flowy, fillx, insets 0"));
      summaryMetadataPanel.add(jLabel3);
      summaryMetadataPanel.add(summaryMetadataScrollPane, "grow");

      metadataSplitPane.setLeftComponent(summaryMetadataPanel);

      imageMetadataTable_.setModel(new DefaultTableModel(
              new Object[][]{},
              new String[]{"Property", "Value"}) {

         Class[] types = new Class[]{
            java.lang.String.class, java.lang.String.class
         };
         boolean[] canEdit = new boolean[]{
            false, false
         };

         @Override
         public Class getColumnClass(int columnIndex) {
            return types[columnIndex];
         }

         @Override
         public boolean isCellEditable(int rowIndex, int columnIndex) {
            return canEdit[columnIndex];
         }
      });
      imageMetadataTable_.setToolTipText("Individual image metadata");
      imageMetadataTable_.setDoubleBuffered(true);
      imageMetadataTableScrollPane.setViewportView(imageMetadataTable_);

      showUnchangingPropertiesCheckbox_.setText("Show unchanging properties");
      showUnchangingPropertiesCheckbox_.setToolTipText("Show/hide properties that are the same for all images in the dataset");
      showUnchangingPropertiesCheckbox_.addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent e) {
                  try {
                     updateQueue_.put(display_.getDisplayedImages().get(0));
                  }
                  catch (InterruptedException ex) {
                     ReportingUtils.logError(ex, "Interrupted while putting image into metadata queue");
                  }
               }
      });

      jLabel2.setText("Image metadata");

      imageMetadataScrollPane.setLayout(
            new MigLayout("insets 0, flowy, fillx"));
      imageMetadataScrollPane.add(jLabel2);
      imageMetadataScrollPane.add(showUnchangingPropertiesCheckbox_);
      imageMetadataScrollPane.add(imageMetadataTableScrollPane, "grow");

      metadataSplitPane.setRightComponent(imageMetadataScrollPane);
      metadataSplitPane.setResizeWeight(.5);

      setLayout(new MigLayout("flowy, fillx"));
      add(metadataSplitPane, "grow");
      setMaximumSize(new Dimension(800, 500));
   }

   /**
    * This method runs continuously in a separate thread, consuming images from
    * the updateQueue_ and updating our tables to show the corresponding
    * metadata. When multiple images arrive while we're doing one update, we
    * throw away all but the most recent image, to avoid falling behind during
    * rapid changes.
    */
   private void updateMetadata() {
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
    * Extract metadata from the provided image, and from the summary metadata,
    * and update our tables. We may need to do some modification of the
    * metadata to format it nicely for our tables.
    */
   public void imageChangedUpdate(final Image image) {
      Metadata data = image.getMetadata();
      if (data.getUUID() == lastImageUUID_) {
         // We're already displaying this image's metadata.
         return;
      }
      final JSONObject metadata = ((DefaultMetadata) data).toJSON();
      try {
         // If the "userData" and/or "scopeData" properties are present,
         // we need to "flatten" them a bit -- their keys and values
         // have been serialized into the JSON using PropertyMap
         // serialization rules, which create a JSONObject for each
         // property. userData additionally is stored within its own
         // distinct JSONObject while scopeData is stored within the
         // metadata as a whole.
         // TODO: this is awfully tightly-bound to the hacks we've put in
         // to maintain backwards compatibility with our file formats.
         if (data.getScopeData() != null) {
            DefaultPropertyMap scopeData = (DefaultPropertyMap) data.getScopeData();
            scopeData.flattenJSONSerialization(metadata);
         }
         if (data.getUserData() != null) {
            DefaultPropertyMap userData = (DefaultPropertyMap) data.getUserData();
            JSONObject userJSON = metadata.getJSONObject("userData");
            userData.flattenJSONSerialization(userJSON);
            for (String key : MDUtils.getKeys(userJSON)) {
               metadata.put(key, userJSON.get(key));
            }
         }
         // Enhance this structure with information about basic image
         // properties.
         metadata.put("Width", image.getWidth());
         metadata.put("Height", image.getHeight());
         if (image.getCoords() != null) {
            for (String axis : image.getCoords().getAxes()) {
               metadata.put(axis + " index",
                     image.getCoords().getIndex(axis));
            }
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Failed to update metadata display");
      }
      // Update the tables in the EDT.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            imageMetadataModel_.setMetadata(metadata,
                  showUnchangingPropertiesCheckbox_.isSelected());
            summaryMetadataModel_.setMetadata(
                  ((DefaultSummaryMetadata) store_.getSummaryMetadata()).toJSON(),
                  true);
         }
      });
      lastImageUUID_ = data.getUUID();
   }

   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      try {
         updateQueue_.put(event.getImage());
      }
      catch (InterruptedException e) {
         ReportingUtils.logError(e, "Interrupted while enqueueing image for metadata display");
      }
   }

   @Override
   public synchronized void setDataViewer(DataViewer display) {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
      }
      display_ = display;
      if (display_ == null) {
         // Don't show stale metadata info.
         imageMetadataModel_.setMetadata(new JSONObject(), false);
         summaryMetadataModel_.setMetadata(new JSONObject(), false);
         return;
      }
      else {
         // Show metadata for the displayed images, if any.
         List<Image> images = display_.getDisplayedImages();
         if (images.size() > 0) {
            try {
               updateQueue_.put(images.get(0));
            }
            catch (InterruptedException e) {
               ReportingUtils.logError(e, "Interrupted when shifting metadata display to new viewer");
            }
         }
      }
      store_ = display.getDatastore();
      display_.registerForEvents(this);
   }

   @Override
   public void setInspector(Inspector inspector) {
      // We don't care.
   }

   @Override
   public synchronized void cleanup() {
      if (display_ != null) {
         display_.unregisterForEvents(this);
      }
      shouldShowUpdates_ = false;
      updateThread_.interrupt(); // It will then close.
   }
}
