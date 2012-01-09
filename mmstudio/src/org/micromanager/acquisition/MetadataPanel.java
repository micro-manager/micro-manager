/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * MetadataPanel.java
 *
 * Created on Oct 20, 2010, 10:40:52 AM
 */
package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.StackWindow;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DebugGraphics;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SpringLayout;
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
import org.micromanager.api.ImageCache;
import org.micromanager.graph.ContrastPanel;
import org.micromanager.utils.ImageFocusListener;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.ScaleBar;

/**
 *
 * @author arthur
 */
public class MetadataPanel extends JPanel
        implements ImageFocusListener {

   private static final String SINGLE_CHANNEL = "Single Channel";
   private static final String MULTIPLE_CHANNELS = "Multiple Channels";
   ;
    private JSplitPane CommentsSplitPane;
    private JCheckBox autostretchCheckBox;
    private JPanel multipleChannelsPanel_;
    private JScrollPane contrastScrollPane;
    private JComboBox displayModeCombo;
    private JLabel imageCommentsLabel;
    private JPanel imageCommentsPanel;
    private JScrollPane imageCommentsScrollPane;
    private JTextArea imageCommentsTextArea;
    private JPanel imageMetadataScrollPane;
    private JTable imageMetadataTable;
    private JScrollPane imageMetadataTableScrollPane;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JLabel jLabel3;
    private JPanel jPanel1;
    private JCheckBox logScaleCheckBox;
    private JSplitPane metadataSplitPane;
    private JComboBox overlayColorComboBox_;
    private JCheckBox rejectOutliersCB_;
    private JSpinner rejectPercentSpinner_;
    private JCheckBox showUnchangingPropertiesCheckbox;
    private JCheckBox sizeBarCheckBox;
    private JComboBox sizeBarComboBox;
    private JLabel summaryCommentsLabel;
    private JPanel summaryCommentsPane;
    private JScrollPane summaryCommentsScrollPane;
    private JTextArea summaryCommentsTextArea;
    private JPanel summaryMetadataPanel;
    private JScrollPane summaryMetadataScrollPane;
    private JTable summaryMetadataTable;
    private JTabbedPane tabbedPane;
    private JPanel masterContrastPanel_;
    private JPanel singleChannelPanel_;
    private CardLayout contrastPanelLayout_;
   
   private ContrastPanel singleChannelContrastPanel_;
   private static MetadataPanel singletonViewer_ = null;
   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private final String[] columnNames_ = {"Property", "Value"};
   private boolean showUnchangingKeys_;
   private boolean updatingDisplayModeCombo_ = false;
   private ArrayList<ChannelControlPanel> ccpList_;
   private Color overlayColor_ = Color.white;
   private ImageWindow focusedWindow_;
   private boolean prevUseSingleChannelHist_ = true;

   /** Creates new form MetadataPanel */
   public MetadataPanel(ContrastPanel cp) {
      singleChannelContrastPanel_ = cp;
      initialize();
      imageMetadataModel_ = new MetadataTableModel();
      summaryMetadataModel_ = new MetadataTableModel();
//      ImagePlus.addImageListener(this);
      GUIUtils.registerImageFocusListener(this);
      //update(WindowManager.getCurrentImage());
      imageMetadataTable.setModel(imageMetadataModel_);
      summaryMetadataTable.setModel(summaryMetadataModel_);
      addTextChangeListeners();
      setDisplayState(CompositeImage.COMPOSITE);
      this.autostretchCheckBoxStateChanged(null);

      rejectPercentSpinner_.setModel(new SpinnerNumberModel(0., 0., 100., 0.1));
   }

   public static MetadataPanel showMetadataPanel() {
      if (singletonViewer_ == null) {
         try {
            singletonViewer_ = new MetadataPanel((ContrastPanel)
                    JavaUtils.getRestrictedFieldValue(new ContrastPanel(), ContrastPanel.class, "contrastPanel_"));
            //GUIUtils.recallPosition(singletonViewer_);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.logError(ex);
         }
      }
      singletonViewer_.setVisible(true);
      return singletonViewer_;
   }

   /** This method is called from within the constructor to
    * initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is
    * always regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
       // <editor-fold defaultstate="collapsed" desc="Generated Code">
    private void initialize() {

        tabbedPane = new JTabbedPane();
        singleChannelPanel_ = new JPanel(new BorderLayout());
        multipleChannelsPanel_ = new JPanel();
        jPanel1 = new JPanel();
        displayModeCombo = new JComboBox();
        jLabel1 = new JLabel();
        autostretchCheckBox = new JCheckBox();
        rejectOutliersCB_ = new JCheckBox();
        rejectPercentSpinner_ = new JSpinner();
        logScaleCheckBox = new JCheckBox();
        sizeBarCheckBox = new JCheckBox();
        sizeBarComboBox = new JComboBox();
        overlayColorComboBox_ = new JComboBox();
        contrastScrollPane = new JScrollPane();
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

        tabbedPane.setToolTipText("Examine and adjust display settings, metadata, and comments for the multi-dimensional acquisition in the frontmost window.");
        tabbedPane.setFocusable(false);
        tabbedPane.setPreferredSize(new java.awt.Dimension(400, 640));
        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent evt) {
                tabbedPaneStateChanged(evt);
            }
        });

        multipleChannelsPanel_.setPreferredSize(new java.awt.Dimension(400, 594));

        displayModeCombo.setModel(new DefaultComboBoxModel(new String[] { "Composite", "Color", "Grayscale" }));
        displayModeCombo.setToolTipText("<html>Choose display mode:<br> - Composite = Multicolor overlay<br> - Color = Single channel color view<br> - Grayscale = Single channel grayscale view</li></ul></html>");
        displayModeCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayModeComboActionPerformed(evt);
            }
        });

        jLabel1.setText("Display mode:");

        autostretchCheckBox.setText("Autostretch");
        autostretchCheckBox.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent evt) {
                autostretchCheckBoxStateChanged(evt);
            }
        });

        rejectOutliersCB_.setText("ignore %");
        rejectOutliersCB_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rejectOutliersCB_ActionPerformed(evt);
            }
        });

        rejectPercentSpinner_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        rejectPercentSpinner_.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent evt) {
                rejectPercentSpinner_StateChanged(evt);
            }
        });
        rejectPercentSpinner_.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                rejectPercentSpinner_KeyPressed(evt);
            }
        });

        logScaleCheckBox.setText("Log hist");
        logScaleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logScaleCheckBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap(24, Short.MAX_VALUE)
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(displayModeCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(autostretchCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(rejectOutliersCB_)
                .add(6, 6, 6)
                .add(rejectPercentSpinner_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 63, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(logScaleCheckBox))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(autostretchCheckBox)
                .add(rejectOutliersCB_)
                .add(rejectPercentSpinner_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(logScaleCheckBox))
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(displayModeCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(jLabel1))
        );

        sizeBarCheckBox.setText("Scale Bar");
        sizeBarCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sizeBarCheckBoxActionPerformed(evt);
            }
        });

        sizeBarComboBox.setModel(new DefaultComboBoxModel(new String[] { "Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right" }));
        sizeBarComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sizeBarComboBoxActionPerformed(evt);
            }
        });

        overlayColorComboBox_.setModel(new DefaultComboBoxModel(new String[] { "White", "Black", "Yellow", "Gray" }));
        overlayColorComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overlayColorComboBox_ActionPerformed(evt);
            }
        });

        contrastScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        contrastScrollPane.setPreferredSize(new java.awt.Dimension(400, 4));

        singleChannelPanel_.add(singleChannelContrastPanel_);
        contrastPanelLayout_ = new CardLayout();
        masterContrastPanel_ = new JPanel(contrastPanelLayout_);
        masterContrastPanel_.add(multipleChannelsPanel_, MULTIPLE_CHANNELS);
        masterContrastPanel_.add(singleChannelPanel_, SINGLE_CHANNEL);
        showSingleChannelContrastPanel();
        org.jdesktop.layout.GroupLayout channelsTablePanel_Layout = new org.jdesktop.layout.GroupLayout(multipleChannelsPanel_);
        multipleChannelsPanel_.setLayout(channelsTablePanel_Layout);
        channelsTablePanel_Layout.setHorizontalGroup(
            channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(channelsTablePanel_Layout.createSequentialGroup()
                .add(sizeBarCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(sizeBarComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(overlayColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(contrastScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE))
        );
        channelsTablePanel_Layout.setVerticalGroup(
            channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(channelsTablePanel_Layout.createSequentialGroup()
                .add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(sizeBarCheckBox)
                    .add(sizeBarComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(overlayColorComboBox_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(589, Short.MAX_VALUE))
            .add(channelsTablePanel_Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(channelsTablePanel_Layout.createSequentialGroup()
                    .add(79, 79, 79)
                    .add(contrastScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 571, Short.MAX_VALUE)))
        );

        tabbedPane.addTab("Channels", masterContrastPanel_);

        metadataSplitPane.setBorder(null);
        metadataSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

        imageMetadataTable.setModel(new DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Property", "Value"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
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
            imageMetadataScrollPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(imageMetadataTableScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, imageMetadataScrollPaneLayout.createSequentialGroup()
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 308, Short.MAX_VALUE)
                .add(showUnchangingPropertiesCheckbox))
        );
        imageMetadataScrollPaneLayout.setVerticalGroup(
            imageMetadataScrollPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(imageMetadataScrollPaneLayout.createSequentialGroup()
                .add(imageMetadataScrollPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(showUnchangingPropertiesCheckbox)
                    .add(jLabel2))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(imageMetadataTableScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 517, Short.MAX_VALUE))
        );

        metadataSplitPane.setRightComponent(imageMetadataScrollPane);

        summaryMetadataPanel.setMinimumSize(new java.awt.Dimension(0, 100));
        summaryMetadataPanel.setPreferredSize(new java.awt.Dimension(539, 100));

        summaryMetadataScrollPane.setMinimumSize(new java.awt.Dimension(0, 0));
        summaryMetadataScrollPane.setPreferredSize(new java.awt.Dimension(454, 80));

        summaryMetadataTable.setModel(new DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Property", "Value"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        summaryMetadataTable.setToolTipText("Metadata tags for the whole acquisition");
        summaryMetadataScrollPane.setViewportView(summaryMetadataTable);

        jLabel3.setText("Acquisition properties");

        org.jdesktop.layout.GroupLayout summaryMetadataPanelLayout = new org.jdesktop.layout.GroupLayout(summaryMetadataPanel);
        summaryMetadataPanel.setLayout(summaryMetadataPanelLayout);
        summaryMetadataPanelLayout.setHorizontalGroup(
            summaryMetadataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, summaryMetadataScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
            .add(summaryMetadataPanelLayout.createSequentialGroup()
                .add(jLabel3)
                .addContainerGap())
        );
        summaryMetadataPanelLayout.setVerticalGroup(
            summaryMetadataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, summaryMetadataPanelLayout.createSequentialGroup()
                .add(jLabel3)
                .add(4, 4, 4)
                .add(summaryMetadataScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

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
            summaryCommentsPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(summaryCommentsPaneLayout.createSequentialGroup()
                .add(summaryCommentsLabel)
                .addContainerGap(491, Short.MAX_VALUE))
            .add(summaryCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
        );
        summaryCommentsPaneLayout.setVerticalGroup(
            summaryCommentsPaneLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(summaryCommentsPaneLayout.createSequentialGroup()
                .add(summaryCommentsLabel)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(summaryCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE))
        );

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
            imageCommentsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(imageCommentsPanelLayout.createSequentialGroup()
                .add(imageCommentsLabel)
                .add(400, 400, 400))
            .add(imageCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
        );
        imageCommentsPanelLayout.setVerticalGroup(
            imageCommentsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(imageCommentsPanelLayout.createSequentialGroup()
                .add(imageCommentsLabel)
                .add(0, 0, 0)
                .add(imageCommentsScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 429, Short.MAX_VALUE))
        );

        CommentsSplitPane.setRightComponent(imageCommentsPanel);

        tabbedPane.addTab("Comments", CommentsSplitPane);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(tabbedPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 625, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(tabbedPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 680, Short.MAX_VALUE)
                .addContainerGap())
        );
    }
   
    private void displayModeComboActionPerformed(java.awt.event.ActionEvent evt) {                                                 
       if (!updatingDisplayModeCombo_) {
          setDisplayState(displayModeCombo.getSelectedIndex() + 1);
       }
}                                                

    private void showUnchangingPropertiesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {                                                                 
       showUnchangingKeys_ = showUnchangingPropertiesCheckbox.isSelected();
       update(WindowManager.getCurrentImage());
}                                                                

    private void tabbedPaneStateChanged(ChangeEvent evt) {                                        
       try {
          update(WindowManager.getCurrentImage());
       } catch (Exception e) {
       }
}                                       

    private void sizeBarCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {                                                
       showSizeBar();
    }                                               

    private void sizeBarComboBoxActionPerformed(java.awt.event.ActionEvent evt) {                                                
       showSizeBar();
    }                                               

    private void overlayColorComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {                                                      
       if ((overlayColorComboBox_.getSelectedItem()).equals("Black")) {
          overlayColor_ = Color.black;
       } else if ((overlayColorComboBox_.getSelectedItem()).equals("White")) {
          overlayColor_ = Color.white;
       } else if ((overlayColorComboBox_.getSelectedItem()).equals("Yellow")) {
          overlayColor_ = Color.yellow;
       } else if ((overlayColorComboBox_.getSelectedItem()).equals("Gray")) {
          overlayColor_ = Color.gray;
       }
       showSizeBar();

    }                                                     

    private void logScaleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {                                                 
       for (ChannelControlPanel ccp : ccpList_) {
          ccp.setLogScale(logScaleCheckBox.isSelected());
       }
       updateAndDrawHistograms();
    }                                                

    private void rejectOutliersCB_ActionPerformed(java.awt.event.ActionEvent evt) {                                                  
       rejectPercentSpinner_.setEnabled(rejectOutliersCB_.isSelected() && autostretchCheckBox.isSelected());
             updateAndDrawHistograms();

    }                                                 

    private void rejectPercentSpinner_StateChanged(ChangeEvent evt) {                                                   
             updateAndDrawHistograms();

    }                                                  

    private void autostretchCheckBoxStateChanged(ChangeEvent evt) {                                                 
       rejectOutliersCB_.setEnabled(autostretchCheckBox.isSelected());
       boolean rejectem = rejectOutliersCB_.isSelected() && autostretchCheckBox.isSelected();
       rejectPercentSpinner_.setEnabled(rejectem);
             updateAndDrawHistograms();

    }                                                

    private void rejectPercentSpinner_KeyPressed(java.awt.event.KeyEvent evt) {                                                 
              updateAndDrawHistograms();
    }                                                

    public ContrastPanel getSingleChannelContrastPanel() {
       return singleChannelContrastPanel_;
    }
    
   private ImagePlus getCurrentImage() {
      try {
         return WindowManager.getCurrentImage();
      } catch (Exception e) {
         return null;
      }
   }

   private void setDisplayState(int state) {
      ImagePlus imgp = getCurrentImage();
      if (imgp instanceof CompositeImage) {
         CompositeImage ci = (CompositeImage) imgp;
         ci.setMode(state);
         ci.updateAndDraw();
      }
   }

   void setAutostretch(boolean state) {
      autostretchCheckBox.setSelected(state);
   }
   

   private void addTextChangeListeners() {
      summaryCommentsTextArea.getDocument()
              .addDocumentListener(new DocumentListener() {
         private void handleChange() {
            writeSummaryComments(WindowManager.getCurrentImage());
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

      imageCommentsTextArea.getDocument()
              .addDocumentListener(new DocumentListener() {
         private void handleChange() {
            writeImageComments(WindowManager.getCurrentImage());
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

   private boolean isHyperImage(ImagePlus imgp) {
      return VirtualAcquisitionDisplay.getDisplay(imgp) != null;
   }

   private AcquisitionVirtualStack getAcquisitionStack(ImagePlus imp) {
      VirtualAcquisitionDisplay display = VirtualAcquisitionDisplay.getDisplay(imp);
      if (display != null) {
         return display.virtualStack_;
      } else {
         return null;
      }
   }

   @Override
   public void setVisible(boolean visible) {
      super.setVisible(visible);
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

   // should be called whenever image changes or sliders are moved  
   public void update(ImagePlus imp) {
      int tabSelected = tabbedPane.getSelectedIndex();
      if (imp == null) {
         imageMetadataModel_.setMetadata(null);
         summaryMetadataModel_.setMetadata(null);
         summaryCommentsTextArea.setText(null);
         contrastScrollPane.setViewportView(null);
         ccpList_ = null;         
         if(useSingleChannelHistogram())
            singleChannelContrastPanel_.clearHistogram();
         else
            updateAndDrawHistograms();         
      } else {
         if (tabSelected == 1) {
            AcquisitionVirtualStack stack = getAcquisitionStack(imp);
            if (stack != null) {
               int slice = imp.getCurrentSlice();
               TaggedImage taggedImg = stack.getTaggedImage(slice);
               if (taggedImg == null) {
                  imageMetadataModel_.setMetadata(null);
               } else {
                  JSONObject md = stack.getTaggedImage(slice).tags;
                  if (!showUnchangingKeys_) {
                     md = selectChangingTags(imp, md);
                  }
                  imageMetadataModel_.setMetadata(md);
               }
               summaryMetadataModel_.setMetadata(stack.getCache().getSummaryMetadata());
            } else {
               imageMetadataModel_.setMetadata(null);
            }
         } else if (tabSelected == 0) {
            updateAndDrawHistograms();
         } else if (tabSelected == 2) {
            VirtualAcquisitionDisplay acq = getVirtualAcquisitionDisplay(imp);
            if (acq != null) {
               imageCommentsTextArea.setText(acq.getImageComment());
            }
         }
      }

   }

   private void showSizeBar() {
      boolean show = sizeBarCheckBox.isSelected();
      ImagePlus ip = WindowManager.getCurrentImage();
      if (show) {
         ScaleBar sizeBar = new ScaleBar(ip);

         if (sizeBar != null) {
            Overlay ol = new Overlay();
            //ol.setFillColor(Color.white); // this causes the text to get a white background!
            ol.setStrokeColor(overlayColor_);
            String selected = (String) sizeBarComboBox.getSelectedItem();
            if (selected.equals("Top-Right")) {
               sizeBar.setPosition(ScaleBar.Position.TOPRIGHT);
            }
            if (selected.equals("Top-Left")) {
               sizeBar.setPosition(ScaleBar.Position.TOPLEFT);
            }
            if (selected.equals("Bottom-Right")) {
               sizeBar.setPosition(ScaleBar.Position.BOTTOMRIGHT);
            }
            if (selected.equals("Bottom-Left")) {
               sizeBar.setPosition(ScaleBar.Position.BOTTOMLEFT);
            }
            sizeBar.addToOverlay(ol);
            ol.setStrokeColor(overlayColor_);
            ip.setOverlay(ol);
         }
      }
      ip.setHideOverlay(!show);
   }

   //Implements AWTEventListener
   /*
    * This is called, in contrast to update(), only when the ImageWindow
    * in focus has changed.
    */
   public void focusReceived(ImageWindow focusedWindow) {
      if (focusedWindow == null) {
         update((ImagePlus)null);
         return;
      }
      focusedWindow_ = focusedWindow;

      ImagePlus imgp = focusedWindow.getImagePlus();
      ImageCache cache = getCache(imgp);
      VirtualAcquisitionDisplay acq = getVirtualAcquisitionDisplay(imgp);
      sizeBarCheckBox.setSelected(imgp.getOverlay() != null && !imgp.getHideOverlay());
      
      if (useSingleChannelHistogram())
         singleChannelContrastPanel_.setImage(imgp);  //this call loads appropriate contrast settings
      
      if (acq != null) {
         summaryCommentsTextArea.setText(acq.getSummaryComment());
         JSONObject md = cache.getSummaryMetadata();
         summaryMetadataModel_.setMetadata(md);
      } else {
         summaryCommentsTextArea.setText(null);
      }

      if (imgp instanceof CompositeImage) {
         CompositeImage cimp = (CompositeImage) imgp;
         updatingDisplayModeCombo_ = true;
         displayModeCombo.setSelectedIndex(cimp.getMode() - 1);
         updatingDisplayModeCombo_ = false;
      }
      if (acq != null) {
         setupChannelControls(acq);
         
         if (acq.firstImage()) 
            if (useSingleChannelHistogram()) {
               singleChannelContrastPanel_.autostretch(true);
               singleChannelContrastPanel_.updateHistogram();
            } else if (ccpList_ != null) 
                  for (ChannelControlPanel c : ccpList_) 
                     c.autoScale();   
            
         update(imgp);
      }

   }

   private VirtualAcquisitionDisplay getVirtualAcquisitionDisplay(ImagePlus imgp) {
      if (imgp == null) {
         return null;
      }
      return VirtualAcquisitionDisplay.getDisplay(imgp);
   }

   public synchronized void setupChannelControls(VirtualAcquisitionDisplay acq) {
      if (useSingleChannelHistogram() ) {
         showSingleChannelContrastPanel();
         singleChannelContrastPanel_.setDisplay(acq);
      } else {
         showMultipleChannelsContrastPanel();
         
         final int nChannels = acq.getNumGrayChannels();
     
         final SpringLayout layout = new SpringLayout();
         final JPanel p = new JPanel() {
            @Override
            public void paint(Graphics g) {    
               int channelHeight = Math.max(115,contrastScrollPane.getViewport().getSize().height / nChannels);        
               this.setPreferredSize(new Dimension(this.getSize().width, channelHeight * nChannels));
               if (ccpList_ != null) {
                  for (int i = 0; i < ccpList_.size(); i++) {
                     ccpList_.get(i).setHeight(channelHeight);
                     ccpList_.get(i).setLocation(0, channelHeight * i);
                  }
               }
               super.paint(g);
            }
         };
         
         int hpHeight = Math.max(115, (contrastScrollPane.getSize().height-2) / nChannels);
         p.setPreferredSize(new Dimension(200, nChannels * hpHeight));
         contrastScrollPane.setViewportView(p);
         
         p.setLayout(layout);
         ccpList_ = new ArrayList<ChannelControlPanel>();
         for (int i = 0; i < nChannels; ++i) {
            ChannelControlPanel ccp = new ChannelControlPanel(acq, i, this,hpHeight);
            layout.putConstraint(SpringLayout.EAST, ccp, 0, SpringLayout.EAST, p);
            layout.putConstraint(SpringLayout.WEST, ccp, 0, SpringLayout.WEST, p);
            p.add(ccp);
            ccpList_.add(ccp);
         }
         
         layout.putConstraint(SpringLayout.NORTH, ccpList_.get(0), 0, SpringLayout.NORTH, p);
         layout.putConstraint(SpringLayout.SOUTH, ccpList_.get(nChannels-1), 0, SpringLayout.SOUTH, p);
         for (int i = 1; i < ccpList_.size(); i++)
            layout.putConstraint(SpringLayout.NORTH, ccpList_.get(i), 0, SpringLayout.SOUTH, ccpList_.get(i-1));

         
         
         
         updateAndDrawHistograms();
      }
   }
  

   private Double getFractionOutliersToReject() {
      try {
         double value = 0.01 * NumberUtils.displayStringToDouble(this.rejectPercentSpinner_.getValue().toString());
         return value;
      } catch (Exception e) {
         return null;
      }
   }

   private void drawDisplaySettings(ChannelControlPanel ccp) {
      ccp.drawDisplaySettings();
   }

   
   private synchronized void updateAndDrawHistograms() {
      if (useSingleChannelHistogram() ) {
         singleChannelContrastPanel_.updateContrast();
      } else if (ccpList_ != null) {
         for (ChannelControlPanel ccp : ccpList_) {
           updateChannelSettings(ccp);
           drawDisplaySettings(ccp);
         }
      } 
   }



   private void updateChannelSettings(ChannelControlPanel ccp) {
      Double fractionOutliersToReject = getFractionOutliersToReject();
      if (fractionOutliersToReject != null) {
         ccp.setFractionToReject(fractionOutliersToReject);
      }
      ccp.setAutostretch(autostretchCheckBox.isSelected());
      ccp.setRejectOutliers(rejectOutliersCB_.isSelected() && autostretchCheckBox.isSelected());
      ccp.updateChannelSettings();
   }
   
   
   private boolean useSingleChannelHistogram() {
      if (focusedWindow_ == null)
         return prevUseSingleChannelHist_;
      ImagePlus imgp = focusedWindow_.getImagePlus();
      if (imgp == null )
         return prevUseSingleChannelHist_;
      VirtualAcquisitionDisplay vad = getVirtualAcquisitionDisplay(imgp); 
      if (vad != null)
         prevUseSingleChannelHist_ = (vad.getNumChannels()== 1);
      return prevUseSingleChannelHist_;
   }
   
   private void showSingleChannelContrastPanel() {
     contrastPanelLayout_.show(masterContrastPanel_, SINGLE_CHANNEL);
   }
   
   private void showMultipleChannelsContrastPanel() {
     contrastPanelLayout_.show(masterContrastPanel_, MULTIPLE_CHANNELS);
   }
}
