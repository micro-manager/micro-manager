// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.inspector.internal.panels.metadata;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.AbstractInspectorPanelController;

/**
 * @author Mark A. Tsuchida, in part based on original by Chris Weisiger
 */
public class SummaryMetadataInspectorPanelController extends AbstractInspectorPanelController {
   private final JPanel panel_ = new JPanel();
   private DataProvider dataProvider_;
   private static boolean expanded_ = false;

   private WeakReference<SummaryMetadata> previousMetadataRef_ =
         new WeakReference<SummaryMetadata>(null);

   private List<Map.Entry<String, String>> data_ = Collections.emptyList();

   private final AbstractTableModel tableModel_ = new AbstractTableModel() {
      @Override
      public int getRowCount() {
         return data_.size();
      }

      @Override
      public int getColumnCount() {
         return 2;
      }

      @Override
      public String getColumnName(int columnIndex) {
         switch (columnIndex) {
            case 0:
               return "Key";
            case 1:
               return "Value";
            default:
               throw new IndexOutOfBoundsException();
         }
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
         Map.Entry<String, String> entry = data_.get(rowIndex);
         switch (columnIndex) {
            case 0:
               return entry.getKey();
            case 1:
               return entry.getValue();
            default:
               throw new IndexOutOfBoundsException();
         }
      }
   };

   private final JTable table_;
   private final JScrollPane scrollPane_;

   public static AbstractInspectorPanelController create() {
      return new SummaryMetadataInspectorPanelController();
   }

   private SummaryMetadataInspectorPanelController() {
      panel_.setLayout(new MigLayout(new LC().insets("0").fill()));

      table_ = new JTable(tableModel_);
      table_.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
      table_.setFillsViewportHeight(true);
      table_.setPreferredScrollableViewportSize(new Dimension(240, 180));

      scrollPane_ = new JScrollPane(table_,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane_.setBorder(BorderFactory.createEmptyBorder());
      panel_.add(scrollPane_, new CC().grow().push());
   }

   @Override
   public String getTitle() {
      return "Dataset Summary Metadata";
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      Preconditions.checkNotNull(viewer);
      detachDataViewer();
      dataProvider_ = viewer.getDataProvider();
      dataProvider_.registerForEvents(this);
      updateSummaryMetadata(dataProvider_.getSummaryMetadata());
   }

   @Override
   public void detachDataViewer() {
      if (dataProvider_ == null) {
         return;
      }
      dataProvider_.unregisterForEvents(this);
      dataProvider_ = null;
      updateSummaryMetadata(null);
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return true;
   }

   @Override
   public void setExpanded(boolean state) {
      expanded_ = state;
   }

   @Override
   public boolean initiallyExpand() {
      return expanded_;
   }

   private void updateSummaryMetadata(SummaryMetadata summaryMetadata) {
      if (summaryMetadata == previousMetadataRef_.get()) {
         return;
      }
      previousMetadataRef_ = new WeakReference<SummaryMetadata>(summaryMetadata);

      PropertyMap metadataMap = summaryMetadata == null ?
            PropertyMaps.emptyPropertyMap() :
            ((DefaultSummaryMetadata) summaryMetadata).toPropertyMap();

      final TreeMap<String, String> data = new TreeMap<String, String>();
      for (String key : metadataMap.keySet()) {
         if (PropertyKey.USER_DATA.key().equals(key)) {
            PropertyMap userData = metadataMap.getPropertyMap(key, null);
            for (String subkey : userData.keySet()) {
               data.put("user:" + subkey, userData.getValueAsString(subkey, ""));
            }
         }
         else if (PropertyKey.STAGE_POSITIONS.key().equals(key)) {
            data.put(key, String.format("<%d positions>",
                  metadataMap.getPropertyMapList(PropertyKey.STAGE_POSITIONS.key()).size()));
         }
         else {
            data.put(key, metadataMap.getValueAsString(key, ""));
         }
      }

      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            data_ = new ArrayList<Map.Entry<String, String>>(data.entrySet());
            tableModel_.fireTableDataChanged();
         }
      });
   }

   @Subscribe
   public void onEvent(DataProviderHasNewSummaryMetadataEvent e) {
      updateSummaryMetadata(e.getSummaryMetadata());
   }
}