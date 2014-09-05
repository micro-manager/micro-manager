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
package org.micromanager.data.test;

import ij.ImagePlus;
import ij.gui.ImageWindow;

import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.DebugGraphics;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;

import org.micromanager.graph.ContrastPanel;
import org.micromanager.utils.ImageFocusListener;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;


public class MetadataPanel extends JPanel {
   private JTextArea imageCommentsTextArea_;
   private JTable imageMetadataTable_;
   private JCheckBox showUnchangingPropertiesCheckbox_;
   private JTextArea summaryCommentsTextArea_;
   private JTable summaryMetadataTable_;
   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private final String[] columnNames_ = {"Property", "Value"};
   private boolean showUnchangingKeys_;
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
      addTextChangeListeners();
      addFocusListeners();
   }

   private void initialize() {
      JTabbedPane tabbedPane = new JTabbedPane();
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
      JSplitPane CommentsSplitPane = new JSplitPane();
      JPanel summaryCommentsPane = new JPanel();
      JLabel summaryCommentsLabel = new JLabel();
      JScrollPane summaryCommentsScrollPane = new JScrollPane();
      summaryCommentsTextArea_ = new JTextArea();
      JPanel imageCommentsPanel = new JPanel();
      JLabel imageCommentsLabel = new JLabel();
      JScrollPane imageCommentsScrollPane = new JScrollPane();
      imageCommentsTextArea_ = new JTextArea();

      tabbedPane.setFocusable(false);
      tabbedPane.setPreferredSize(new java.awt.Dimension(250, 250));
      tabbedPane.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent evt) {
            tabbedPaneStateChanged(evt);
         }
      });

      metadataSplitPane.setBorder(null);
      metadataSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

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
      imageMetadataTable_.setDebugGraphicsOptions(DebugGraphics.NONE_OPTION);
      imageMetadataTable_.setDoubleBuffered(true);
      imageMetadataTableScrollPane.setViewportView(imageMetadataTable_);

      showUnchangingPropertiesCheckbox_.setText("Show unchanging properties");
      showUnchangingPropertiesCheckbox_.setToolTipText("Show/hide properties that are the same for all images in the acquisition");
      showUnchangingPropertiesCheckbox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showUnchangingPropertiesCheckboxActionPerformed(evt);
         }
      });

      jLabel2.setText("Per-image properties");

      javax.swing.GroupLayout imageMetadataScrollPaneLayout = new javax.swing.GroupLayout(imageMetadataScrollPane);
      imageMetadataScrollPane.setLayout(imageMetadataScrollPaneLayout);
      imageMetadataScrollPaneLayout.setHorizontalGroup(
              imageMetadataScrollPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(imageMetadataTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE).addGroup(javax.swing.GroupLayout.Alignment.TRAILING, imageMetadataScrollPaneLayout.createSequentialGroup().addComponent(jLabel2).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 250, Short.MAX_VALUE).addComponent(showUnchangingPropertiesCheckbox_)));
      imageMetadataScrollPaneLayout.setVerticalGroup(
              imageMetadataScrollPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(imageMetadataScrollPaneLayout.createSequentialGroup().addGroup(imageMetadataScrollPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(showUnchangingPropertiesCheckbox_).addComponent(jLabel2)).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(imageMetadataTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)));

      metadataSplitPane.setRightComponent(imageMetadataScrollPane);

      summaryMetadataPanel.setMinimumSize(new java.awt.Dimension(0, 100));
      summaryMetadataPanel.setPreferredSize(new java.awt.Dimension(250, 100));

      summaryMetadataScrollPane.setMinimumSize(new java.awt.Dimension(0, 0));
      summaryMetadataScrollPane.setPreferredSize(new java.awt.Dimension(250, 80));

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

      javax.swing.GroupLayout summaryMetadataPanelLayout = new javax.swing.GroupLayout(summaryMetadataPanel);
      summaryMetadataPanel.setLayout(summaryMetadataPanelLayout);
      summaryMetadataPanelLayout.setHorizontalGroup(
              summaryMetadataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(summaryMetadataScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE).addGroup(summaryMetadataPanelLayout.createSequentialGroup().addComponent(jLabel3).addContainerGap()));
      summaryMetadataPanelLayout.setVerticalGroup(
              summaryMetadataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(javax.swing.GroupLayout.Alignment.TRAILING, summaryMetadataPanelLayout.createSequentialGroup().addComponent(jLabel3).addGap(4, 4, 4).addComponent(summaryMetadataScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

      metadataSplitPane.setLeftComponent(summaryMetadataPanel);

      tabbedPane.addTab("Metadata", metadataSplitPane);

      CommentsSplitPane.setBorder(null);
      CommentsSplitPane.setDividerLocation(200);
      CommentsSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

      summaryCommentsLabel.setText("Acquisition comments:");

      summaryCommentsTextArea_.setColumns(20);
      summaryCommentsTextArea_.setLineWrap(true);
      summaryCommentsTextArea_.setRows(1);
      summaryCommentsTextArea_.setTabSize(3);
      summaryCommentsTextArea_.setToolTipText("Enter your comments for the whole acquisition here");
      summaryCommentsTextArea_.setWrapStyleWord(true);
      summaryCommentsScrollPane.setViewportView(summaryCommentsTextArea_);

      javax.swing.GroupLayout summaryCommentsPaneLayout = new javax.swing.GroupLayout(summaryCommentsPane);
      summaryCommentsPane.setLayout(summaryCommentsPaneLayout);
      summaryCommentsPaneLayout.setHorizontalGroup(
              summaryCommentsPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(summaryCommentsPaneLayout.createSequentialGroup().addComponent(summaryCommentsLabel).addContainerGap(200, Short.MAX_VALUE)).addComponent(summaryCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE));
      summaryCommentsPaneLayout.setVerticalGroup(
              summaryCommentsPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(summaryCommentsPaneLayout.createSequentialGroup().addComponent(summaryCommentsLabel).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(summaryCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)));

      CommentsSplitPane.setLeftComponent(summaryCommentsPane);

      imageCommentsPanel.setPreferredSize(new java.awt.Dimension(250, 100));

      imageCommentsLabel.setText("Per-image comments:");

      imageCommentsTextArea_.setColumns(20);
      imageCommentsTextArea_.setLineWrap(true);
      imageCommentsTextArea_.setRows(1);
      imageCommentsTextArea_.setTabSize(3);
      imageCommentsTextArea_.setToolTipText("Comments for each image may be entered here.");
      imageCommentsTextArea_.setWrapStyleWord(true);
      imageCommentsScrollPane.setViewportView(imageCommentsTextArea_);

      javax.swing.GroupLayout imageCommentsPanelLayout = new javax.swing.GroupLayout(imageCommentsPanel);
      imageCommentsPanel.setLayout(imageCommentsPanelLayout);
      imageCommentsPanelLayout.setHorizontalGroup(
              imageCommentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(imageCommentsPanelLayout.createSequentialGroup().addComponent(imageCommentsLabel).addGap(200, 200, 200)).addComponent(imageCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE));
      imageCommentsPanelLayout.setVerticalGroup(
              imageCommentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(imageCommentsPanelLayout.createSequentialGroup().addComponent(imageCommentsLabel).addGap(0, 0, 0).addComponent(imageCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)));

      CommentsSplitPane.setRightComponent(imageCommentsPanel);

      tabbedPane.addTab("Comments", CommentsSplitPane);

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
              layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE).addContainerGap()));
      layout.setVerticalGroup(
              layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE).addContainerGap()));
   }

   private void showUnchangingPropertiesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {
      showUnchangingKeys_ = showUnchangingPropertiesCheckbox_.isSelected();
      ReportingUtils.logError("TODO: implement showUnchangingProperties");
   }

   private void tabbedPaneStateChanged(ChangeEvent evt) {   
      ReportingUtils.logError("TODO: implement tabbedPaneStateChanged");
   }

   private void addFocusListeners() {
      FocusListener listener = new FocusListener() {
         @Override
         public void focusGained(FocusEvent e) { }
         @Override
         public void focusLost(FocusEvent e) {
            ReportingUtils.logError("TODO: write display settings on lost focus");
         }
      };
      summaryCommentsTextArea_.addFocusListener(listener);
      imageCommentsTextArea_.addFocusListener(listener);
   }
   
   private void addTextChangeListeners() {
      summaryCommentsTextArea_.getDocument().addDocumentListener(new DocumentListener() {

         private void handleChange() {
            ReportingUtils.logError("TODO: write summary comments");
         }

         @Override
         public void insertUpdate(DocumentEvent e) {
            handleChange();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            handleChange();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            handleChange();
         }
      });

      imageCommentsTextArea_.getDocument().addDocumentListener(new DocumentListener() {

         private void handleChange() {
            ReportingUtils.logError("TODO: write image comments");
         }

         @Override
         public void insertUpdate(DocumentEvent e) {
            handleChange();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            handleChange();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            handleChange();
         }
      });
   }

   class MetadataTableModel extends AbstractTableModel {

      Vector<Vector<String>> data_;

      MetadataTableModel() {
         data_ = new Vector<Vector<String>>();
      }

      @Override
      public int getRowCount() {
         return data_.size();
      }

      public void addRow(Vector<String> rowData) {
         data_.add(rowData);
      }

      @Override
      public int getColumnCount() {
         return 2;
      }

      @Override
      public synchronized Object getValueAt(int rowIndex, int columnIndex) {
         if (data_.size() > rowIndex) {
            Vector<String> row = data_.get(rowIndex);
            if (row.size() > columnIndex) {
               return data_.get(rowIndex).get(columnIndex);
            } else {
               return "";
            }
         } else {
            return "";
         }
      }

      public void clear() {
         data_.clear();
      }

      @Override
      public String getColumnName(int colIndex) {
         return columnNames_[colIndex];
      }

      public synchronized void setMetadata(JSONObject md) {
         clear();
         ReportingUtils.logError("Received metadata " + md);
         if (md != null) {
            String[] keys = MDUtils.getKeys(md);
            Arrays.sort(keys);
            for (String key : keys) {
               Vector<String> rowData = new Vector<String>();
               rowData.add(key);
               try {
                  rowData.add(md.getString(key));
               } catch (JSONException ex) {
                  //ReportingUtils.logError(ex);
               }
               addRow(rowData);
            }
         }
         fireTableDataChanged();
      }
   }

   private JSONObject selectChangingTags(ImagePlus imgp, JSONObject md) {
      JSONObject mdChanging = new JSONObject();
//      ImageCache cache = getCache(imgp);
//      if (cache != null) {
//         for (String key : cache.getChangingKeys()) {
//            if (md.has(key)) {
//               try {
//                  mdChanging.put(key, md.get(key));
//               } catch (JSONException ex) {
//                  try {
//                     mdChanging.put(key, "");
//                     //ReportingUtils.logError(ex);
//                  } catch (JSONException ex1) {
//                     ReportingUtils.logError(ex1);
//                  }
//               }
//            }
//         }
//      }
      return mdChanging;
   }

   private void writeSummaryComments() {
   }

   private void writeImageComments() {
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
            imageMetadataModel_.setMetadata(data.legacyToJSON());
            summaryMetadataModel_.setMetadata(store_.getSummaryMetadata().legacyToJSON());
         }
      };
      // Cancel all pending tasks and then schedule our task for execution
      // 125ms in the future.
      updateTimer_.purge();
      updateTimer_.schedule(task, 125);
   }
}
