///////////////////////////////////////////////////////////////////////////////
//FILE:          MetadataPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
package org.micromanager.display.internal;

import ij.gui.ImageWindow;

import java.awt.Dimension;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;

import org.micromanager.internal.utils.ReportingUtils;


public class MetadataPanel extends JPanel {
   private JTable imageMetadataTable_;
   private JCheckBox showUnchangingPropertiesCheckbox_;
   private JTable summaryMetadataTable_;
   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private ImageWindow currentWindow_;
   private Datastore store_;
   private Timer updateTimer_;

   /** Creates new form MetadataPanel */
   public MetadataPanel(Datastore store) {
      store_ = store;
      imageMetadataModel_ = new MetadataTableModel();
      summaryMetadataModel_ = new MetadataTableModel();
      initialize();
      imageMetadataTable_.setModel(imageMetadataModel_);
      summaryMetadataTable_.setModel(summaryMetadataModel_);
   }

   private void initialize() {
      JSplitPane metadataSplitPane = new JSplitPane();
      JPanel imageMetadataScrollPane = new JPanel();
      JScrollPane imageMetadataTableScrollPane = new JScrollPane();
      imageMetadataTable_ = new JTable();
      showUnchangingPropertiesCheckbox_ = new JCheckBox();
      JLabel jLabel2 = new JLabel();
      JPanel summaryMetadataPanel = new JPanel();
      JScrollPane summaryMetadataScrollPane = new JScrollPane();
      summaryMetadataTable_ = new JTable();
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
      summaryMetadataTable_.setToolTipText("Metadata tags for the whole acquisition");
      summaryMetadataScrollPane.setViewportView(summaryMetadataTable_);

      jLabel3.setText("Acquisition properties");

      summaryMetadataPanel.setLayout(new MigLayout("flowy"));
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
      imageMetadataTable_.setToolTipText("Metadata tags for each individual image");
      imageMetadataTable_.setDoubleBuffered(true);
      imageMetadataTableScrollPane.setViewportView(imageMetadataTable_);

      showUnchangingPropertiesCheckbox_.setText("Show unchanging properties");
      showUnchangingPropertiesCheckbox_.setToolTipText("Show/hide properties that are the same for all images in the acquisition");

      jLabel2.setText("Per-image properties");

      imageMetadataScrollPane.setLayout(new MigLayout("flowy"));
      imageMetadataScrollPane.add(jLabel2);
      imageMetadataScrollPane.add(showUnchangingPropertiesCheckbox_);
      imageMetadataScrollPane.add(imageMetadataTableScrollPane, "grow");

      metadataSplitPane.setRightComponent(imageMetadataScrollPane);
      metadataSplitPane.setResizeWeight(.5);

      setLayout(new MigLayout("flowy"));
      add(metadataSplitPane, "grow");
      setMaximumSize(new Dimension(300, 500));
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
            imageMetadataModel_.setMetadata(data.legacyToJSON(),
                  showUnchangingPropertiesCheckbox_.isSelected());
            summaryMetadataModel_.setMetadata(store_.getSummaryMetadata().legacyToJSON(),
                  true);
         }
      };
      // Cancel all pending tasks and then schedule our task for execution
      // 125ms in the future.
      updateTimer_.purge();
      updateTimer_.schedule(task, 125);
   }
}
