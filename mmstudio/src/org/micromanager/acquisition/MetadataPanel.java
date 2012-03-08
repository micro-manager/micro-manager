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
package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.HashMap;
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
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.Histograms;
import org.micromanager.api.ImageCache;
import org.micromanager.graph.ContrastPanel;
import org.micromanager.graph.SingleChannelHistogram;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.ImageFocusListener;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.SnapLiveContrastSettings;

/**
 *
 * @author arthur
 */
public class MetadataPanel extends JPanel
        implements ImageFocusListener {

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
   private ImageWindow lastWindow_;

   /** Creates new form MetadataPanel */
   public MetadataPanel() {
      makeContrastPanel();
      initialize();
      imageMetadataModel_ = new MetadataTableModel();
      summaryMetadataModel_ = new MetadataTableModel();
      GUIUtils.registerImageFocusListener(this);
      imageMetadataTable.setModel(imageMetadataModel_);
      summaryMetadataTable.setModel(summaryMetadataModel_);
      addTextChangeListeners();
   }

   private void makeContrastPanel() {
      contrastPanel_ = new ContrastPanel(this);
      contrastPanel_.setFont(new Font("", Font.PLAIN, 10));
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

         public void stateChanged(ChangeEvent evt) {
            tabbedPaneStateChanged(evt);
         }
      });

      tabbedPane.addTab("Channels", contrastPanel_);

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

         public Class getColumnClass(int columnIndex) {
            return types[columnIndex];
         }

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

         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showUnchangingPropertiesCheckboxActionPerformed(evt);
         }
      });

      jLabel2.setText("Per-image properties");

      org.jdesktop.layout.GroupLayout imageMetadataScrollPaneLayout = new org.jdesktop.layout.GroupLayout(imageMetadataScrollPane);
      imageMetadataScrollPane.setLayout(imageMetadataScrollPaneLayout);
      imageMetadataScrollPaneLayout.setHorizontalGroup(
              imageMetadataScrollPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(imageMetadataTableScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE).add(org.jdesktop.layout.GroupLayout.TRAILING, imageMetadataScrollPaneLayout.createSequentialGroup().add(jLabel2).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 308, Short.MAX_VALUE).add(showUnchangingPropertiesCheckbox)));
      imageMetadataScrollPaneLayout.setVerticalGroup(
              imageMetadataScrollPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(imageMetadataScrollPaneLayout.createSequentialGroup().add(imageMetadataScrollPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE).add(showUnchangingPropertiesCheckbox).add(jLabel2)).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(imageMetadataTableScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 517, Short.MAX_VALUE)));

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

         public boolean isCellEditable(int rowIndex, int columnIndex) {
            return canEdit[columnIndex];
         }
      });
      summaryMetadataTable.setToolTipText("Metadata tags for the whole acquisition");
      summaryMetadataScrollPane.setViewportView(summaryMetadataTable);

      jLabel3.setText("Acquisition properties");

      org.jdesktop.layout.GroupLayout summaryMetadataPanelLayout = new org.jdesktop.layout.GroupLayout(summaryMetadataPanel);
      summaryMetadataPanel.setLayout(summaryMetadataPanelLayout);
      summaryMetadataPanelLayout.setHorizontalGroup(
              summaryMetadataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(org.jdesktop.layout.GroupLayout.TRAILING, summaryMetadataScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE).add(summaryMetadataPanelLayout.createSequentialGroup().add(jLabel3).addContainerGap()));
      summaryMetadataPanelLayout.setVerticalGroup(
              summaryMetadataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(org.jdesktop.layout.GroupLayout.TRAILING, summaryMetadataPanelLayout.createSequentialGroup().add(jLabel3).add(4, 4, 4).add(summaryMetadataScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

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

      org.jdesktop.layout.GroupLayout summaryCommentsPaneLayout = new org.jdesktop.layout.GroupLayout(summaryCommentsPane);
      summaryCommentsPane.setLayout(summaryCommentsPaneLayout);
      summaryCommentsPaneLayout.setHorizontalGroup(
              summaryCommentsPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(summaryCommentsPaneLayout.createSequentialGroup().add(summaryCommentsLabel).addContainerGap(491, Short.MAX_VALUE)).add(summaryCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE));
      summaryCommentsPaneLayout.setVerticalGroup(
              summaryCommentsPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(summaryCommentsPaneLayout.createSequentialGroup().add(summaryCommentsLabel).addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED).add(summaryCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)));

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

      org.jdesktop.layout.GroupLayout imageCommentsPanelLayout = new org.jdesktop.layout.GroupLayout(imageCommentsPanel);
      imageCommentsPanel.setLayout(imageCommentsPanelLayout);
      imageCommentsPanelLayout.setHorizontalGroup(
              imageCommentsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(imageCommentsPanelLayout.createSequentialGroup().add(imageCommentsLabel).add(400, 400, 400)).add(imageCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE));
      imageCommentsPanelLayout.setVerticalGroup(
              imageCommentsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(imageCommentsPanelLayout.createSequentialGroup().add(imageCommentsLabel).add(0, 0, 0).add(imageCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 429, Short.MAX_VALUE)));

      CommentsSplitPane.setRightComponent(imageCommentsPanel);

      tabbedPane.addTab("Comments", CommentsSplitPane);

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
              layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(layout.createSequentialGroup().addContainerGap().add(tabbedPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 625, Short.MAX_VALUE).addContainerGap()));
      layout.setVerticalGroup(
              layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(layout.createSequentialGroup().addContainerGap().add(tabbedPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 680, Short.MAX_VALUE).addContainerGap()));
   }

   private void showUnchangingPropertiesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {
      showUnchangingKeys_ = showUnchangingPropertiesCheckbox.isSelected();
      ImagePlus img = getCurrentImage();
      if (img != null) {
         ImageCache cache = VirtualAcquisitionDisplay.getDisplay(img).getImageCache();
         imageChangedUpdate(img, cache);
      }
   }

   private void tabbedPaneStateChanged(ChangeEvent evt) {
      ImagePlus img = getCurrentImage();
      if (img != null) {
         ImageCache cache = VirtualAcquisitionDisplay.getDisplay(img).getImageCache();
         imageChangedUpdate(img, cache);
      }
   }

   private void addTextChangeListeners() {
      summaryCommentsTextArea.getDocument().addDocumentListener(new DocumentListener() {

         private void handleChange() {
            writeSummaryComments(getCurrentImage());
         }

         public void insertUpdate(DocumentEvent e) {
            handleChange();
         }

         public void removeUpdate(DocumentEvent e) {
            handleChange();
         }

         public void changedUpdate(DocumentEvent e) {
            handleChange();
         }
      });

      imageCommentsTextArea.getDocument().addDocumentListener(new DocumentListener() {

         private void handleChange() {
            writeImageComments(getCurrentImage());
         }

         public void insertUpdate(DocumentEvent e) {
            handleChange();
         }

         public void removeUpdate(DocumentEvent e) {
            handleChange();
         }

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

      public int getRowCount() {
         return data_.size();
      }

      public void addRow(Vector<String> rowData) {
         data_.add(rowData);
      }

      public int getColumnCount() {
         return 2;
      }

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
               rowData.add((String) key);
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
      ImageCache cache = getCache(imgp);
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

   private AcquisitionVirtualStack getAcquisitionStack(ImagePlus imp) {
      VirtualAcquisitionDisplay display = VirtualAcquisitionDisplay.getDisplay(imp);
      if (display != null) {
         return display.virtualStack_;
      } else {
         return null;
      }
   }

   private void writeSummaryComments(ImagePlus imp) {
      VirtualAcquisitionDisplay acq = getVirtualAcquisitionDisplay(imp);
      if (acq != null) {
         acq.setSummaryComment(summaryCommentsTextArea.getText());
      }
   }

   private void writeImageComments(ImagePlus imgp) {
      VirtualAcquisitionDisplay acq = getVirtualAcquisitionDisplay(imgp);
      if (acq != null) {
         acq.setImageComment(imageCommentsTextArea.getText());
      }
   }

   private ImageCache getCache(ImagePlus imgp) {
      if (VirtualAcquisitionDisplay.getDisplay(imgp) != null) {
         return VirtualAcquisitionDisplay.getDisplay(imgp).imageCache_;
      } else {
         return null;
      }
   }

   public ImagePlus getCurrentImage() {
      if (lastWindow_ == null) {
         return null;
      }
      return lastWindow_.getImagePlus();
   }

   public ContrastSettings getChannelContrast(int channel) {
      return contrastPanel_.getChannelContrast(channel);
   }

   /*
    * used to store contrast settings for snap live window
    */
   public void saveContrastSettings(ImageCache cache) {
      String pixelType = cache.getPixelType();
      int numCh = cache.getNumChannels();
      for (int i = 0; i < numCh; i++) {
         int min = cache.getChannelMin(i);
         int max = cache.getChannelMax(i);
         double gamma = cache.getChannelGamma(i);
         MMStudioMainFrame.getInstance().saveSimpleContrastSettings(new ContrastSettings(min, max, gamma), i, pixelType);
      }
   }

   /*
    * Used for applying loaded contrast settings on intial image
    */
   public void loadSimpleWinContrastWithoutDraw(ImageCache cache, ImagePlus img) {
      contrastPanel_.loadSimpleWinContrastWithoutDraw(cache, img);
   }

   /*
    * used only for autoscaling on intial image
    */
   public void autoscaleWithoutDraw(ImageCache cache, ImagePlus img) {
      contrastPanel_.autoscaleWithoutDraw(cache, img);
   }

   public void autoscaleOverStackWithoutDraw(ImageCache cache, ImagePlus img, int channel,
           HashMap<Integer, Integer> mins, HashMap<Integer, Integer> maxes) {
      contrastPanel_.autoscaleOverStackWithoutDraw(cache, img, channel, mins, maxes);
   }

   public void refresh() {
      contrastPanel_.refresh();
   }

   public synchronized void setup(ImageWindow win) {
      if (win == lastWindow_) {
         return;
      }
      lastWindow_ = win;
      if (win == null || !(win instanceof VirtualAcquisitionDisplay.DisplayWindow)) {
         imageChangedUpdate((ImagePlus) null, null);
         return;
      }

      final ImagePlus imgp = win.getImagePlus();
      final ImageCache cache = getCache(imgp);
      final VirtualAcquisitionDisplay vad = getVirtualAcquisitionDisplay(imgp);     

      if (vad != null) {
         summaryCommentsTextArea.setText(vad.getSummaryComment());
         JSONObject md = cache.getSummaryMetadata();
         summaryMetadataModel_.setMetadata(md);
      } else {
         summaryCommentsTextArea.setText(null);
      }


      if (vad != null ) {
         contrastPanel_.setup(cache);
      }
   }

   public void focusReceived(ImageWindow focusedWindow) {
      setup(focusedWindow);
   }

   private VirtualAcquisitionDisplay getVirtualAcquisitionDisplay(ImagePlus imgp) {
      if (imgp == null) {
         return null;
      }
      return VirtualAcquisitionDisplay.getDisplay(imgp);
   }


   /*
    * applies LUT to image and then redraws image
    */
   public void drawWithoutUpdate(ImagePlus img) {
      if (img == null) {
         return;
      }
      VirtualAcquisitionDisplay disp = VirtualAcquisitionDisplay.getDisplay(img);
      if (disp != null ) {
         contrastPanel_.applyLUTToImage(disp.getHyperImage(), disp.imageCache_);
         disp.drawWithoutUpdate();
      }
   }

   public void drawWithoutUpdate() {
      drawWithoutUpdate(getCurrentImage());
   }

   /*
    * called just before image is redrawn.  Calcs histogram and stats (and displays
    * if image is in active window), applies LUT to image.  Does NOT explicitly
    * call draw because this function should be only be called just before 
    * ImagePlus.draw or CompositieImage.draw runs as a result of the overriden 
    * methods in MMCompositeImage and MMImagePlus
    */
   public void imageChangedUpdate(ImagePlus img, ImageCache cache) {

      int tabSelected = tabbedPane.getSelectedIndex();
      if (img == null) {
         imageMetadataModel_.setMetadata(null);
         summaryMetadataModel_.setMetadata(null);
         summaryCommentsTextArea.setText(null);
         contrastPanel_.showHistograms(ContrastPanel.BLANK);
         contrastPanel_.enableAppropriateControls(ContrastPanel.BLANK);
      } else if (tabSelected == 1) { //Metadata
         AcquisitionVirtualStack stack = getAcquisitionStack(img);
         if (stack != null) {
            int slice = img.getCurrentSlice();
            TaggedImage taggedImg = stack.getTaggedImage(slice);
            if (taggedImg == null) {
               imageMetadataModel_.setMetadata(null);
            } else {
               JSONObject md = stack.getTaggedImage(slice).tags;
               if (!showUnchangingKeys_) {
                  md = selectChangingTags(img, md);
               }
               imageMetadataModel_.setMetadata(md);
            }
            summaryMetadataModel_.setMetadata(stack.getCache().getSummaryMetadata());
         } else {
            imageMetadataModel_.setMetadata(null);
         }
         contrastPanel_.imageChanged(img, cache, false);
      } else if (tabSelected == 0) { //Histogram panel
         contrastPanel_.imageChanged(img, cache, true);
      } else if (tabSelected == 2) { //Display and comments
         VirtualAcquisitionDisplay acq = getVirtualAcquisitionDisplay(img);
         if (acq != null) {
            imageCommentsTextArea.setText(acq.getImageComment());
         }
         contrastPanel_.imageChanged(img, cache, false);
      }
   }

   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      contrastPanel_.setChannelContrast(channelIndex, min, max, gamma);
   }

   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      contrastPanel_.setChannelHistogramDisplayMax(channelIndex, histMax);
   }
}
