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
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.micromanager.data.SummaryMetadata;
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
public final class MetadataPanel extends InspectorPanel {
   private JSplitPane metadataSplitPane_;
   private JTable imageMetadataTable_;
   private JPanel imageMetadataPanel_;
   private JCheckBox showUnchangingPropertiesCheckbox_;
   private JTable summaryMetadataTable_;
   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private Datastore store_;
   private DataViewer display_;
   private final Thread updateThread_;
   private final LinkedBlockingQueue<Image> updateQueue_;
   private boolean shouldShowUpdates_ = true;
   private boolean shouldForceUpdate_ = false;
   private SummaryMetadata prevSummaryMetadata_ = null;
   private Metadata prevMetadata_ = null;


   /** This class makes smaller JTables, since the default size is absurd. */
   private class SmallerJTable extends JTable {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
         return getPreferredSize();
      }
      @Override
      public boolean getFillsViewportHeight() {
         return true;
      }
      @Override
      public int getAutoResizeMode() {
         return JTable.AUTO_RESIZE_OFF;
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
   }
   
   public void startUpdateThread() {
      updateThread_.start();
   }

   private void initialize() {
      metadataSplitPane_ = new JSplitPane() {
         /**
          * HACK: we have to constrain the size of our scroll pane based on
          * the available size of our panel as a whole. Otherwise their
          * preferred sizes get blown out when the tables they contain get
          * large numbers of rows.
          */
         @Override
         public Dimension getPreferredSize() {
            Dimension defaultSize = super.getPreferredSize();
            Dimension ourSize = MetadataPanel.this.getSize();
            // HACK: subtract a significant amount off of these preferred sizes
            // so that we can properly shrink when there's less space
            // available.
            // TODO: despite this, if the user shrinks the Inspector window
            // sufficiently quickly, a scrollbar (for the inspector window as
            // a whole) may briefly appear.
            return new Dimension(
                  Math.min(ourSize.width, defaultSize.width) - 100,
                  Math.min(ourSize.height, defaultSize.height) - 100);
         }
      };

      metadataSplitPane_.setBorder(null);
      metadataSplitPane_.setOrientation(JSplitPane.VERTICAL_SPLIT);

      summaryMetadataTable_ = new SmallerJTable();
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

      JScrollPane summaryMetadataScrollPane_ = new JScrollPane();
      summaryMetadataScrollPane_.setViewportView(summaryMetadataTable_);

      JPanel summaryMetadataPanel = new JPanel();
      summaryMetadataPanel.setLayout(new MigLayout("flowy, fill, insets 0"));
      summaryMetadataPanel.add(new JLabel("Summary metadata"), "pushy 0");
      summaryMetadataPanel.add(summaryMetadataScrollPane_, "grow, pushy 100");

      metadataSplitPane_.setLeftComponent(summaryMetadataPanel);

      showUnchangingPropertiesCheckbox_ = new JCheckBox("Show unchanging properties");
      showUnchangingPropertiesCheckbox_.setToolTipText("Show/hide properties that are the same for all images in the dataset");
      showUnchangingPropertiesCheckbox_.addActionListener(
            new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent e) {
                  // If we don't force it, then it'll think we're showing
                  // the correct data and thus don't need to do any updates.
                  shouldForceUpdate_ = true;
                  try {
                     updateQueue_.put(display_.getDisplayedImages().get(0));
                  }
                  catch (InterruptedException ex) {
                     ReportingUtils.logError(ex, "Interrupted while putting image into metadata queue");
                  }
               }
      });

      imageMetadataTable_ = new SmallerJTable();
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

      JScrollPane imageMetadataTableScrollPane = new JScrollPane();
      imageMetadataTableScrollPane.setViewportView(imageMetadataTable_);

      imageMetadataPanel_ = new JPanel();
      imageMetadataPanel_.setLayout(
            new MigLayout("insets 0, flowy, fill"));
      imageMetadataPanel_.add(new JLabel("Image metadata"), "pushy 0");
      imageMetadataPanel_.add(showUnchangingPropertiesCheckbox_,
            "pushy 0");
      imageMetadataPanel_.add(imageMetadataTableScrollPane,
            "grow, pushy 100");

      metadataSplitPane_.setRightComponent(imageMetadataPanel_);
      metadataSplitPane_.setResizeWeight(.5);

      setLayout(new MigLayout("flowy, fill, insets 0, gap 0"));
      add(metadataSplitPane_, "grow, pushy 100");
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
            // Note: it would be nicer to use:
            // image = updateQueue_.poll(100L, TimeUnit.MILLISECONDS);
            // and not use a sleep in this thread, however, that approach
            // leads to very hgh CPU usage for reasons I do not understand
            image = updateQueue_.poll();
         }
         if (image != null) {
            imageChangedUpdate(image);
         }
         else {
            // No updates available; just wait for a bit.
            try {
               Thread.sleep(100);
            }
            catch (InterruptedException e) {
               if (!shouldShowUpdates_) {
                  return;
               }
            }
         }
      }
   }

   /**
    * Extract metadata from the provided image, and from the summary metadata,
    * and update our tables. We may need to do some modification of the
    * metadata to format it nicely for our tables.
    * @param image Image for which to show metadata and summary metadata
    */
   public void imageChangedUpdate(final Image image) {
      SummaryMetadata summaryMetadata = store_.getSummaryMetadata();
      Metadata metadata = image.getMetadata();

      // Since Metadata and SummaryMetadata are immutable, we'll have a new
      // object if it has changed. However, use the UUID for plane metadata,
      // in case the (unchanged) Metadata object is recreated for the same
      // plane (otherwise the hiding of unchanging fields may break).
      final boolean summaryChanged = (summaryMetadata != prevSummaryMetadata_);
      prevSummaryMetadata_ = summaryMetadata;
      final boolean metadataChanged = (metadata != prevMetadata_ &&
            (prevMetadata_ == null ||
             !prevMetadata_.getUUID().equals(metadata.getUUID())));
      prevMetadata_ = metadata;

      if (!metadataChanged && !summaryChanged && !shouldForceUpdate_) {
         return;
      }
      shouldForceUpdate_ = false;

      JSONObject jsonSummary = null;
      if (summaryChanged) {
         // If the "UserData" field is present, we need to "flatten" it a bit:
         // the keys and values have been serialized into the JSON using
         // PropertyMap serialization rules, which create a JSONObject for each
         // property. UserData is stored within its own distinct JSONObject.
         jsonSummary = ((DefaultSummaryMetadata) summaryMetadata).toJSON();
         try {
            DefaultPropertyMap userData = (DefaultPropertyMap) summaryMetadata.getUserData();
            if (userData != null) {
               JSONObject userJSON = jsonSummary.getJSONObject("UserData");
               userData.flattenJSONSerialization(userJSON);
               for (String key : MDUtils.getKeys(userJSON)) {
                  jsonSummary.put("UserData:" + key, userJSON.get(key));
               }
            }
         } catch (JSONException e) {
            ReportingUtils.logError(e, "Failed to update Summary metadata display");
         }
      }
      final JSONObject jsonSummaryMetadata = jsonSummary;

      final JSONObject jsonMetadata = ((DefaultMetadata) metadata).toJSON();
      try {
         // If the "userData" and/or "scopeData" properties are present,
         // we need to "flatten" them a bit -- their keys and values
         // have been serialized into the JSON using PropertyMap
         // serialization rules, which create a JSONObject for each
         // property. userData additionally is stored within its own
         // distinct JSONObject while scopeData is stored within the
         // metadata as a whole.
         DefaultPropertyMap scopeData = (DefaultPropertyMap) metadata.getScopeData();
         if (scopeData != null) {
            scopeData.flattenJSONSerialization(jsonMetadata);
         }
         DefaultPropertyMap userData = (DefaultPropertyMap) metadata.getUserData();
         if (userData != null) {
            JSONObject userJSON = jsonMetadata.getJSONObject("userData");
            userData.flattenJSONSerialization(userJSON);
            for (String key : MDUtils.getKeys(userJSON)) {
               jsonMetadata.put("UserData:" + key, userJSON.get(key));
            }
         }
         // Enhance this structure with information about basic image
         // properties.
         jsonMetadata.put("Width", image.getWidth());
         jsonMetadata.put("Height", image.getHeight());
         if (image.getCoords() != null) {
            for (String axis : image.getCoords().getAxes()) {
               jsonMetadata.put(axis + " index",
                     image.getCoords().getIndex(axis));
            }
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Failed to update metadata display");
      }

      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (metadataChanged) {
               imageMetadataModel_.setMetadata(jsonMetadata,
                     showUnchangingPropertiesCheckbox_.isSelected());
            }
            if (summaryChanged) {
               summaryMetadataModel_.setMetadata(jsonSummaryMetadata, true);
            }
         }
      });
   }

   @Override
   public Dimension getMinimumSize() {
      return new Dimension(100, 300);
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
      imageMetadataModel_.resetChangedKeys();
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
