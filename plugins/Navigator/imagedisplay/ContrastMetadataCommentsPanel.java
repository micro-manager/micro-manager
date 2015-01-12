///////////////////////////////////////////////////////////////////////////////
//FILE:          MetadataPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, & Arthur Edelstein, 2010
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
package imagedisplay;

import ij.ImagePlus;
import java.awt.Font;
import java.awt.Panel;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import acq.MMImageCache;
import mmcloneclasses.graph.ContrastPanel;
import mmcloneclasses.graph.MultiChannelHistograms;
import mmcloneclasses.graph.SingleChannelHistogram;
import mmcloneclasses.graph.Histograms;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;


public class ContrastMetadataCommentsPanel extends Panel  {

   private JSplitPane CommentsSplitPane;
   private JLabel imageCommentsLabel;
   private JPanel imageCommentsPanel;
   private JScrollPane imageCommentsScrollPane;
   private JTextArea imageCommentsTextArea;
   private JPanel imageMetadataScrollPane;
   private JTable imageMetadataTable;
   private JScrollPane imageMetadataTableScrollPane;
   private JLabel jLabel2;
   private JLabel jLabel3;
   private JSplitPane metadataSplitPane;
   private JCheckBox showUnchangingPropertiesCheckbox;
   private JLabel summaryCommentsLabel;
   private JPanel summaryCommentsPane;
   private JScrollPane summaryCommentsScrollPane;
   private JTextArea summaryCommentsTextArea;
   private JPanel summaryMetadataPanel;
   private JScrollPane summaryMetadataScrollPane;
   private JTable summaryMetadataTable;
   private JTabbedPane tabbedPane;
   private ContrastPanel contrastPanel_;
   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private final String[] columnNames_ = {"Property", "Value"};
   private boolean showUnchangingKeys_;
   private VirtualAcquisitionDisplay currentDisplay_;
   private Timer updateTimer_;
   private Histograms histograms_;

   /** Creates new form MetadataPanel */
   public ContrastMetadataCommentsPanel(VirtualAcquisitionDisplay display) {
      imageMetadataModel_ = new MetadataTableModel();
      summaryMetadataModel_ = new MetadataTableModel();
      contrastPanel_ = new ContrastPanel();
      contrastPanel_.setFont(new Font("", Font.PLAIN, 10));
      initialize();
      imageMetadataTable.setModel(imageMetadataModel_);
      summaryMetadataTable.setModel(summaryMetadataModel_);
      addTextChangeListeners();
      addFocusListeners();
   }
   
   public ContrastPanel getContrastPanel() {
      return contrastPanel_;
   }
   
   public void prepareForClose() {
      updateTimer_.cancel();
      updateTimer_ = null;
   }
   
   public void initialize(DisplayPlus display) {
      //setup for use with a single display
      currentDisplay_ = display;
      summaryCommentsTextArea.setText(currentDisplay_.getSummaryComment());
      imageCommentsTextArea.setText(currentDisplay_.getImageComment());
      if (currentDisplay_.getNumChannels() == 1) {
         histograms_ = new SingleChannelHistogram(currentDisplay_, contrastPanel_);
      } else {
         histograms_ = new MultiChannelHistograms(currentDisplay_, contrastPanel_);
      }

      contrastPanel_.displayChanged(currentDisplay_, histograms_);
      imageChangedUpdate(currentDisplay_);
   }
   
   public Histograms getHistograms() {
      return histograms_;
   }

   private void initialize() {
      tabbedPane = new JTabbedPane();
      metadataSplitPane = new JSplitPane();
      imageMetadataScrollPane = new JPanel();
      imageMetadataTableScrollPane = new JScrollPane();
      imageMetadataTable = new JTable();
      showUnchangingPropertiesCheckbox = new JCheckBox();
      jLabel2 = new JLabel();
      summaryMetadataPanel = new JPanel();
      summaryMetadataScrollPane = new JScrollPane();
      summaryMetadataTable = new JTable();
      jLabel3 = new JLabel();
      CommentsSplitPane = new JSplitPane();
      summaryCommentsPane = new JPanel();
      summaryCommentsLabel = new JLabel();
      summaryCommentsScrollPane = new JScrollPane();
      summaryCommentsTextArea = new JTextArea();
      imageCommentsPanel = new JPanel();
      imageCommentsLabel = new JLabel();
      imageCommentsScrollPane = new JScrollPane();
      imageCommentsTextArea = new JTextArea();

      tabbedPane.setFocusable(false);
      tabbedPane.setPreferredSize(new java.awt.Dimension(400, 640));
      tabbedPane.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent evt) {
            tabbedPaneStateChanged(evt);
         }
      });

      tabbedPane.addTab("Contrast", contrastPanel_);

      metadataSplitPane.setBorder(null);
      metadataSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

      imageMetadataTable.setModel(new DefaultTableModel(
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
      imageMetadataTable.setToolTipText("Metadata tags for each individual image");
      imageMetadataTable.setDebugGraphicsOptions(DebugGraphics.NONE_OPTION);
      imageMetadataTable.setDoubleBuffered(true);
      imageMetadataTableScrollPane.setViewportView(imageMetadataTable);

      showUnchangingPropertiesCheckbox.setText("Show unchanging properties");
      showUnchangingPropertiesCheckbox.setToolTipText("Show/hide properties that are the same for all images in the acquisition");
      showUnchangingPropertiesCheckbox.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showUnchangingPropertiesCheckboxActionPerformed(evt);
         }
      });

      jLabel2.setText("Per-image properties");

      javax.swing.GroupLayout imageMetadataScrollPaneLayout = new javax.swing.GroupLayout(imageMetadataScrollPane);
      imageMetadataScrollPane.setLayout(imageMetadataScrollPaneLayout);
      imageMetadataScrollPaneLayout.setHorizontalGroup(
              imageMetadataScrollPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(imageMetadataTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE).addGroup(javax.swing.GroupLayout.Alignment.TRAILING, imageMetadataScrollPaneLayout.createSequentialGroup().addComponent(jLabel2).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 308, Short.MAX_VALUE).addComponent(showUnchangingPropertiesCheckbox)));
      imageMetadataScrollPaneLayout.setVerticalGroup(
              imageMetadataScrollPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(imageMetadataScrollPaneLayout.createSequentialGroup().addGroup(imageMetadataScrollPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(showUnchangingPropertiesCheckbox).addComponent(jLabel2)).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(imageMetadataTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 517, Short.MAX_VALUE)));

      metadataSplitPane.setRightComponent(imageMetadataScrollPane);

      summaryMetadataPanel.setMinimumSize(new java.awt.Dimension(0, 100));
      summaryMetadataPanel.setPreferredSize(new java.awt.Dimension(539, 100));

      summaryMetadataScrollPane.setMinimumSize(new java.awt.Dimension(0, 0));
      summaryMetadataScrollPane.setPreferredSize(new java.awt.Dimension(454, 80));

      summaryMetadataTable.setModel(new DefaultTableModel(
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
      summaryMetadataTable.setToolTipText("Metadata tags for the whole acquisition");
      summaryMetadataScrollPane.setViewportView(summaryMetadataTable);

      jLabel3.setText("Acquisition properties");

      javax.swing.GroupLayout summaryMetadataPanelLayout = new javax.swing.GroupLayout(summaryMetadataPanel);
      summaryMetadataPanel.setLayout(summaryMetadataPanelLayout);
      summaryMetadataPanelLayout.setHorizontalGroup(
              summaryMetadataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(summaryMetadataScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE).addGroup(summaryMetadataPanelLayout.createSequentialGroup().addComponent(jLabel3).addContainerGap()));
      summaryMetadataPanelLayout.setVerticalGroup(
              summaryMetadataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(javax.swing.GroupLayout.Alignment.TRAILING, summaryMetadataPanelLayout.createSequentialGroup().addComponent(jLabel3).addGap(4, 4, 4).addComponent(summaryMetadataScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

      metadataSplitPane.setLeftComponent(summaryMetadataPanel);

      tabbedPane.addTab("Metadata", metadataSplitPane);

      CommentsSplitPane.setBorder(null);
      CommentsSplitPane.setDividerLocation(200);
      CommentsSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

      summaryCommentsLabel.setText("Acquisition comments:");

      summaryCommentsTextArea.setColumns(20);
      summaryCommentsTextArea.setLineWrap(true);
      summaryCommentsTextArea.setRows(1);
      summaryCommentsTextArea.setTabSize(3);
      summaryCommentsTextArea.setToolTipText("Enter your comments for the whole acquisition here");
      summaryCommentsTextArea.setWrapStyleWord(true);
      summaryCommentsScrollPane.setViewportView(summaryCommentsTextArea);

      javax.swing.GroupLayout summaryCommentsPaneLayout = new javax.swing.GroupLayout(summaryCommentsPane);
      summaryCommentsPane.setLayout(summaryCommentsPaneLayout);
      summaryCommentsPaneLayout.setHorizontalGroup(
              summaryCommentsPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(summaryCommentsPaneLayout.createSequentialGroup().addComponent(summaryCommentsLabel).addContainerGap(491, Short.MAX_VALUE)).addComponent(summaryCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE));
      summaryCommentsPaneLayout.setVerticalGroup(
              summaryCommentsPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(summaryCommentsPaneLayout.createSequentialGroup().addComponent(summaryCommentsLabel).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(summaryCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)));

      CommentsSplitPane.setLeftComponent(summaryCommentsPane);

      imageCommentsPanel.setPreferredSize(new java.awt.Dimension(500, 300));

      imageCommentsLabel.setText("Per-image comments:");

      imageCommentsTextArea.setColumns(20);
      imageCommentsTextArea.setLineWrap(true);
      imageCommentsTextArea.setRows(1);
      imageCommentsTextArea.setTabSize(3);
      imageCommentsTextArea.setToolTipText("Comments for each image may be entered here.");
      imageCommentsTextArea.setWrapStyleWord(true);
      imageCommentsScrollPane.setViewportView(imageCommentsTextArea);

      javax.swing.GroupLayout imageCommentsPanelLayout = new javax.swing.GroupLayout(imageCommentsPanel);
      imageCommentsPanel.setLayout(imageCommentsPanelLayout);
      imageCommentsPanelLayout.setHorizontalGroup(
              imageCommentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(imageCommentsPanelLayout.createSequentialGroup().addComponent(imageCommentsLabel).addGap(400, 400, 400)).addComponent(imageCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE));
      imageCommentsPanelLayout.setVerticalGroup(
              imageCommentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(imageCommentsPanelLayout.createSequentialGroup().addComponent(imageCommentsLabel).addGap(0, 0, 0).addComponent(imageCommentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 429, Short.MAX_VALUE)));

      CommentsSplitPane.setRightComponent(imageCommentsPanel);

      tabbedPane.addTab("Comments", CommentsSplitPane);

      GroupLayout layout = new GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
              layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 625, Short.MAX_VALUE).addContainerGap()));
      layout.setVerticalGroup(
              layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 680, Short.MAX_VALUE).addContainerGap()));
   }

   private void showUnchangingPropertiesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {
      showUnchangingKeys_ = showUnchangingPropertiesCheckbox.isSelected();    
      imageChangedUpdate(currentDisplay_);     
   }

   private void tabbedPaneStateChanged(ChangeEvent evt) {   
      imageChangedUpdate(currentDisplay_);         
   }

   private void addFocusListeners() {
      FocusListener listener = new FocusListener() {
         @Override
         public void focusGained(FocusEvent e) { }
         @Override
         public void focusLost(FocusEvent e) {
            if (currentDisplay_ != null) {
               currentDisplay_.imageCache_.writeDisplaySettings();
            }
         }        
      };
      summaryCommentsTextArea.addFocusListener(listener);
      imageCommentsTextArea.addFocusListener(listener);
   }
   
   private void addTextChangeListeners() {
      summaryCommentsTextArea.getDocument().addDocumentListener(new DocumentListener() {

         private void handleChange() {
            if (currentDisplay_ != null)
               writeSummaryComments();
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

      imageCommentsTextArea.getDocument().addDocumentListener(new DocumentListener() {

         private void handleChange() {
            if (currentDisplay_ != null)
               writeImageComments();
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
      MMImageCache cache = getCache(imgp);
      if (cache != null) {
         for (String key : cache.getChangingKeys()) {
            if (md.has(key)) {
               try {
                  mdChanging.put(key, md.get(key));
               } catch (JSONException ex) {
                  try {
                     mdChanging.put(key, "");
                     //ReportingUtils.logError(ex);
                  } catch (JSONException ex1) {
                     ReportingUtils.logError(ex1);
                  }
               }
            }
         }
      }
      return mdChanging;
   }

   private void writeSummaryComments() {
      if (currentDisplay_ == null)
         return;
      currentDisplay_.setSummaryComment(summaryCommentsTextArea.getText());    
   }

   private void writeImageComments() {
      if (currentDisplay_ == null)
         return;
      currentDisplay_.setImageComment(imageCommentsTextArea.getText());
   }

   private MMImageCache getCache(ImagePlus imgp) {
      if (VirtualAcquisitionDisplay.getDisplay(imgp) != null) {
         return VirtualAcquisitionDisplay.getDisplay(imgp).imageCache_;
      } else {
         return null;
      }
   }

   private VirtualAcquisitionDisplay getVirtualAcquisitionDisplay(ImagePlus imgp) {
      if (imgp == null) {
         return null;
      }
      return VirtualAcquisitionDisplay.getDisplay(imgp);
   }

   /*
    * called just before image is redrawn.  Calcs histogram and stats (and displays
    * if image is in active window), applies LUT to image.  Does NOT explicitly
    * call draw because this function should be only be called just before 
    * ImagePlus.draw or CompositieImage.draw runs as a result of the overriden 
    * methods in MMCompositeImage and MMImagePlus
    * We postpone metadata display updates slightly in case the image display
    * is changing rapidly, to ensure that we don't end up with a race condition
    * that causes us to display the wrong metadata.
    */
   public void imageChangedUpdate(final VirtualAcquisitionDisplay disp) { 
      int tabSelected = tabbedPane.getSelectedIndex();
      if (disp == null ) {
         imageMetadataModel_.setMetadata(null);
         summaryMetadataModel_.setMetadata(null);
         summaryCommentsTextArea.setText("");
         imageCommentsTextArea.setText("");
         contrastPanel_.imageChanged();
      } else {
         if (updateTimer_ == null) {
            updateTimer_ = new Timer("Metadata update");
         }
         TimerTask task = new TimerTask() {
            @Override
            public void run() {
               //Update image comment
               imageCommentsTextArea.setText(disp.getImageComment());
               AcquisitionVirtualStack stack = disp.virtualStack_;
               if (stack != null) {
                  JSONObject md = disp.getCurrentMetadata();
                  if (md == null) {
                     imageMetadataModel_.setMetadata(null);
                  } else {
                     if (!showUnchangingKeys_) {
                        md = selectChangingTags(disp.getHyperImage(), md);
                     }
                     imageMetadataModel_.setMetadata(md);
                  }
                  summaryMetadataModel_.setMetadata(disp.getSummaryMetadata());
               } else {
                  imageMetadataModel_.setMetadata(null);
               }
            }
         };
         // Cancel all pending tasks and then schedule our task for execution
         // 125ms in the future.
         updateTimer_.purge();
         updateTimer_.schedule(task, 125);
         //repaint histograms
         if (tabSelected == 0) {
            contrastPanel_.imageChanged();
         }
      }
      
      
      if (tabSelected == 1) { //Metadata
         
      } else if (tabSelected == 0) { //Histogram panel
         
      } else if (tabSelected == 2) { //Display and comments
         
      }
   }
   
   
}
