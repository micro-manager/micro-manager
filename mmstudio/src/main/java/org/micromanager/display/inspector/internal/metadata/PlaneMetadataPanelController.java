/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.metadata;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.display.DataViewer;
import org.micromanager.display.ImageDidDisplayEvent;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool.CoalescentRunnable;
import org.micromanager.internal.utils.ThreadFactoryFactory;

/**
 *
 * @author mark
 */
public class PlaneMetadataPanelController extends AbstractInspectorPanelController {
   private final JPanel panel_ = new JPanel();
   private DataViewer viewer_;

   // Access: from background executor only
   private Metadata metadata_;

   // Access: guarded by monitor on this
   private boolean displayChangedValuesOnly_;

   // Access: from background executor only; list must not be modified once set
   private TreeMap<String, String> data_ = new TreeMap<String, String>();

   // Access: from background executor only
   private final Map<String, String> unchangingValues_ =
         new HashMap<String, String>();

   // Access: from background executor only
   private boolean unchangingValuesInitialized_;

   // Access: from EDT only; list must not be modified once set
   private List<Map.Entry<String, String>> displayData_ = Collections.emptyList();

   private final AbstractTableModel tableModel_ = new AbstractTableModel() {
      @Override
      public int getRowCount() {
         return displayData_.size();
      }

      @Override
      public int getColumnCount() {
         return 2;
      }

      @Override
      public String getColumnName(int columnIndex) {
         switch (columnIndex) {
            case 0: return "Key";
            case 1: return "Value";
            default: throw new IndexOutOfBoundsException();
         }
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
         Map.Entry<String, String> entry = displayData_.get(rowIndex);
         switch (columnIndex) {
            case 0: return entry.getKey();
            case 1: return entry.getValue();
            default: throw new IndexOutOfBoundsException();
         }
      }
   };

   private final JTable table_;
   private final JScrollPane scrollPane_;
   private final JCheckBox changingOnlyCheckBox_;

   private static final ExecutorService background_ =
         Executors.newSingleThreadExecutor(
               ThreadFactoryFactory.createThreadFactory(
                     "PlaneMetadataInspectorPanel"));

   private final CoalescentEDTRunnablePool runnablePool_ =
         CoalescentEDTRunnablePool.create();

   public static AbstractInspectorPanelController create() {
      return new PlaneMetadataPanelController();
   }

   private PlaneMetadataPanelController() {
      panel_.setLayout(new MigLayout(
            new LC().insets("0").gridGap("0", "0").fill()));

      table_ = new JTable(tableModel_);
      table_.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
      table_.setFillsViewportHeight(true);
      table_.setPreferredScrollableViewportSize(new Dimension(240, 180));

      scrollPane_ = new JScrollPane(table_,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane_.setBorder(BorderFactory.createEmptyBorder());
      panel_.add(scrollPane_, new CC().grow().push().wrap());

      changingOnlyCheckBox_ = new JCheckBox("Hide Constant Values");
      changingOnlyCheckBox_.setSelected(displayChangedValuesOnly_);
      changingOnlyCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            synchronized (PlaneMetadataPanelController.this) {
               displayChangedValuesOnly_ = changingOnlyCheckBox_.isSelected();
            }
            background_.submit(new Runnable() {
               @Override
               public void run() {
                  updateMetadata(metadata_, true);
               }
            });
         }
      });
      panel_.add(changingOnlyCheckBox_, new CC().growX());
   }

   @Override
   public String getTitle() {
      return "Image Plane Metadata";
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      Preconditions.checkNotNull(viewer);
      detachDataViewer();
      viewer_ = viewer;
      viewer_.registerForEvents(this);
      final List<Image> images = viewer_.getDisplayedImages();

      background_.submit(new Runnable() {
         @Override
         public void run() {
            // Note: This will initialize all state even if we receive an
            // event from the viewer before we get here.
            unchangingValues_.clear();
            unchangingValuesInitialized_ = false;
            if (images.isEmpty()) {
               updateMetadata(null, true);
            }
            else {
               updateMetadata(images.get(0).getMetadata(), true);
            }
         }
      });
   }

   @Override
   public void detachDataViewer() {
      if (viewer_ == null) {
         return;
      }
      viewer_.unregisterForEvents(this);
      viewer_ = null;
      background_.submit(new Runnable() {
         @Override
         public void run() {
            updateMetadata(null, true);
         }
      });
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return true;
   }

   private void updateMetadata(Metadata metadata, boolean evenIfUnchanged) {
      if (!evenIfUnchanged && metadata == metadata_) {
         return;
      }
      metadata_ = metadata;

      if (metadata == null) {
         metadata = new DefaultMetadata.Builder().build();
      }

      // TODO Use more efficient conversion (avoid JSONObject)
      JSONObject metadataJSON = ((DefaultMetadata) metadata).toJSON();

      final TreeMap<String, String> data = new TreeMap<String, String>();
      for (String key : MDUtils.getKeys(metadataJSON)) {
         try {
            data.put(key, metadataJSON.getString(key));
         }
         catch (JSONException programmingError) {
            throw new RuntimeException(programmingError);
         }
      }

      // Flatten scope and user data
      DefaultPropertyMap scopeData =
            (DefaultPropertyMap) metadata.getScopeData();
      if (scopeData != null && !scopeData.isEmpty()) {
         for (String key : scopeData.getKeys()) {
            data.put("device:" + key, scopeData.getString(key));
            data.remove(key);
         }
      }
      data.remove("scopeDataKeys");

      DefaultPropertyMap userData =
            (DefaultPropertyMap) metadata.getUserData();
      if (userData != null && !userData.isEmpty()) {
         for (String key : userData.getKeys()) {
            data.put("user:" + key, userData.getString(key));
         }
      }
      data.remove("userData");

      boolean changedOnly;
      synchronized (this) {
         changedOnly = displayChangedValuesOnly_;
      }
      final List<Map.Entry<String, String>> displayData;
      if (changedOnly) {
         if (!unchangingValuesInitialized_) {
            if (!data.isEmpty()) {
               unchangingValues_.putAll(data);
               unchangingValuesInitialized_ = true;
            }
            displayData = new ArrayList<Map.Entry<String, String>>(data.entrySet());
         }
         else {
            displayData = new ArrayList<Map.Entry<String, String>>();
            unchangingValues_.entrySet().retainAll(data.entrySet());
            for (Map.Entry<String, String> entry : data.entrySet()) {
               if (!unchangingValues_.containsKey(entry.getKey())) {
                  displayData.add(entry);
               }
            }
         }
      }
      else {
         displayData = new ArrayList<Map.Entry<String, String>>(data.entrySet());
      }

      data_ = data;

      runnablePool_.invokeAsLateAsPossibleWithCoalescence(new CoalescentRunnable() {
         @Override
         public Class<?> getCoalescenceClass() {
            return UpdateTag.class;
         }

         @Override
         public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
            return later;
         }

         @Override
         public void run() {
            displayData_ = displayData;
            tableModel_.fireTableDataChanged();
         }
      });
   }

   private final class UpdateTag { }

   @Subscribe
   public void onEvent(final ImageDidDisplayEvent e) {
      background_.submit(new Runnable() {
         @Override
         public void run() {
            updateMetadata(e.getPrimaryImage().getMetadata(), false);
         }
      });
   }
}