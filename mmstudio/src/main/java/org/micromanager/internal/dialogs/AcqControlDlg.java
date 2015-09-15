///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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

package org.micromanager.internal.dialogs;


import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;

import mmcorej.CMMCore;

import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.display.internal.ChannelSettings;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.ChannelSpec;
import org.micromanager.internal.utils.ColorEditor;
import org.micromanager.internal.utils.ColorRenderer;
import org.micromanager.internal.utils.ContrastSettings;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TooltipTextMaker;

/**
 * Time-lapse, channel and z-stack acquisition setup dialog.
 * This dialog specifies all parameters for the MDA acquisition. 
 *
 */
public class AcqControlDlg extends MMFrame implements PropertyChangeListener, 
        AcqSettingsListener { 

   private static final long serialVersionUID = 1L;
   protected JButton listButton_;
   private final JButton afButton_;
   private JSpinner afSkipInterval_;
   private final JComboBox acqOrderBox_;
   private final JLabel acquisitionOrderText_;
   private static final String SHOULD_SYNC_EXPOSURE = "should sync exposure times between main window and Acquire dialog";
   private static final String SHOULD_HIDE_DISPLAY = "should hide image display windows for multi-dimensional acquisitions";
   private static final String SAVE_MODE = "default save mode";
   private static final String SHOULD_CHECK_EXPOSURE_SANITY = "whether to prompt the user if their exposure times seem excessively long";
   // This array allows us to convert from SaveModes to integers. Of course it
   // needs to be updated if any new save modes are added in the future.
   private JComboBox channelGroupCombo_;
   private final JTextArea commentTextArea_;
   private final JComboBox zValCombo_;
   private final JTextField nameField_;
   private final JTextField rootField_;
   private final JTextArea summaryTextArea_;
   private final JComboBox timeUnitCombo_;
   private final JFormattedTextField interval_;
   private final JFormattedTextField zStep_;
   private final JFormattedTextField zTop_;
   private final JFormattedTextField zBottom_;
   private AcquisitionEngine acqEng_;
   private final JScrollPane channelTablePane_;
   private JTable channelTable_;
   private final JSpinner numFrames_;
   private ChannelTableModel model_;
   private File acqFile_;
   private String acqDir_;
   private int zVals_ = 0;
   private final JButton acquireButton_;
   private final JButton setBottomButton_;
   private final JButton setTopButton_;
   private MMStudio studio_;
   private final NumberFormat numberFormat_;
   private final JLabel namePrefixLabel_;
   private final JLabel saveTypeLabel_;
   private final JRadioButton singleButton_;
   private final JRadioButton multiButton_;
   private final JLabel rootLabel_;
   private final JButton browseRootButton_;
   private final JCheckBox stackKeepShutterOpenCheckBox_;
   private final JCheckBox chanKeepShutterOpenCheckBox_;
   private final AcqOrderMode[] acqOrderModes_;
   private AdvancedOptionsDialog advancedOptionsWindow_;
   // persistent properties (app settings)
   private static final String ACQ_FILE_DIR = "dir";
   private static final String ACQ_INTERVAL = "acqInterval";
   private static final String ACQ_TIME_UNIT = "acqTimeInit";
   private static final String ACQ_ZBOTTOM = "acqZbottom";
   private static final String ACQ_ZTOP = "acqZtop";
   private static final String ACQ_ZSTEP = "acqZstep";
   private static final String ACQ_ENABLE_SLICE_SETTINGS = "enableSliceSettings";
   private static final String ACQ_ENABLE_MULTI_POSITION = "enableMultiPosition";
   private static final String ACQ_ENABLE_MULTI_FRAME = "enableMultiFrame";
   private static final String ACQ_ENABLE_MULTI_CHANNEL = "enableMultiChannels";
   private static final String ACQ_ORDER_MODE = "acqOrderMode";
   private static final String ACQ_NUMFRAMES = "acqNumframes";
   private static final String ACQ_CHANNEL_GROUP = "acqChannelGroup";
   private static final String ACQ_NUM_CHANNELS = "acqNumchannels";
   private static final String ACQ_CHANNELS_KEEP_SHUTTER_OPEN = "acqChannelsKeepShutterOpen";
   private static final String ACQ_STACK_KEEP_SHUTTER_OPEN = "acqStackKeepShutterOpen";
   private static final String CHANNEL_NAME_PREFIX = "acqChannelName";
   private static final String CHANNEL_USE_PREFIX = "acqChannelUse";
   private static final String CHANNEL_EXPOSURE_PREFIX = "acqChannelExp";
   private static final String CHANNEL_ZOFFSET_PREFIX = "acqChannelZOffset";
   private static final String CHANNEL_DOZSTACK_PREFIX = "acqChannelDoZStack";
   private static final String CHANNEL_CONTRAST_MIN_PREFIX = "acqChannelContrastMin";
   private static final String CHANNEL_CONTRAST_MAX_PREFIX = "acqChannelContrastMax";
   private static final String CHANNEL_CONTRAST_GAMMA_PREFIX = "acqChannelContrstGamma";
   private static final String CHANNEL_COLOR_R_PREFIX = "acqChannelColorR";
   private static final String CHANNEL_COLOR_G_PREFIX = "acqChannelColorG";
   private static final String CHANNEL_COLOR_B_PREFIX = "acqChannelColorB";
   private static final String CHANNEL_SKIP_PREFIX = "acqSkip";
   private static final String ACQ_Z_VALUES = "acqZValues";
   private static final String ACQ_DIR_NAME = "acqDirName";
   private static final String ACQ_ROOT_NAME = "acqRootName";
   private static final String ACQ_SAVE_FILES = "acqSaveFiles";
   private static final String ACQ_DISPLAY_MODE = "acqDisplayMode";
   private static final String ACQ_AF_ENABLE = "autofocus_enabled";
   private static final String ACQ_AF_SKIP_INTERVAL = "autofocusSkipInterval";
   private static final String ACQ_COLUMN_WIDTH = "column_width";
   private static final String ACQ_COLUMN_ORDER = "column_order";
   private static final int ACQ_DEFAULT_COLUMN_WIDTH = 77;
   private static final String CUSTOM_INTERVAL_PREFIX = "customInterval";
   private static final String ACQ_ENABLE_CUSTOM_INTERVALS = "enableCustomIntervals";
   private static final FileType ACQ_SETTINGS_FILE = new FileType("ACQ_SETTINGS_FILE", "Acquisition settings",
           System.getProperty("user.home") + "/AcqSettings.txt",
           true, "xml");
   private int columnWidth_[];
   private int columnOrder_[];
   private CheckBoxPanel framesPanel_;
   private final JPanel framesSubPanel_;
   private final CardLayout framesSubPanelLayout_;
   private static final String DEFAULT_FRAMES_PANEL_NAME = "Default frames panel";
   private static final String OVERRIDE_FRAMES_PANEL_NAME = "Override frames panel";
   private CheckBoxPanel channelsPanel_;
   private CheckBoxPanel slicesPanel_;
   protected CheckBoxPanel positionsPanel_;
   private JPanel acquisitionOrderPanel_;
   private CheckBoxPanel afPanel_;
   private JPanel summaryPanel_;
   private CheckBoxPanel savePanel_;
   private ComponentTitledPanel commentsPanel_;
   private Border dayBorder_;
   private Border nightBorder_;
   private ArrayList<JPanel> panelList_;
   private boolean disableGUItoSettings_ = false;

   public final void createChannelTable() {
      model_ = new ChannelTableModel(studio_, acqEng_);
      model_.addTableModelListener(model_);

      channelTable_ = new DaytimeNighttime.Table() {
         @Override
         @SuppressWarnings("serial")
         protected JTableHeader createDefaultTableHeader() {          
            return new JTableHeader(columnModel) {

               @Override
               public String getToolTipText(MouseEvent e) {
                  String tip = null;
                  java.awt.Point p = e.getPoint();
                  int index = columnModel.getColumnIndexAtX(p.x);
                  int realIndex = columnModel.getColumn(index).getModelIndex();
                  return model_.getToolTipText(realIndex);
               }
            };
         }
      };

      channelTable_.setFont(new Font("Dialog", Font.PLAIN, 10));
      channelTable_.setAutoCreateColumnsFromModel(false);
      channelTable_.setModel(model_);
      model_.setChannels(acqEng_.getChannels());

      ChannelCellEditor cellEditor = new ChannelCellEditor(acqEng_);
      ChannelCellRenderer cellRenderer = new ChannelCellRenderer(acqEng_);
      channelTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

      for (int k = 0; k < model_.getColumnCount(); k++) {
         int colIndex = search(columnOrder_, k);
         if (colIndex < 0) {
            colIndex = k;
         }
         TableColumn column;
         if (colIndex == model_.getColumnCount() - 1) {
            // The color selection column has different properties.
            ColorRenderer cr = new ColorRenderer(true);
            ColorEditor ce = new ColorEditor(model_, model_.getColumnCount() - 1);
            column = new TableColumn(model_.getColumnCount() - 1, 200, cr, ce);
            column.setPreferredWidth(columnWidth_[model_.getColumnCount() - 1]);

         } else {
            column = new TableColumn(colIndex, 200, cellRenderer, cellEditor);
            column.setPreferredWidth(columnWidth_[colIndex]);
            // HACK: the "Configuration" tab should be wider than the others.
            if (colIndex == 1) {
               column.setMinWidth((int) (ACQ_DEFAULT_COLUMN_WIDTH * 1.25));
            }
         }
         channelTable_.addColumn(column);
      }

      channelTablePane_.setViewportView(channelTable_);
   }

   public JPanel createPanel(String text, int left, int top, int right, int bottom) {
      return createPanel(text, left, top, right, bottom, false);
   }

   public JPanel createPanel(String text, int left, int top, int right, int bottom, boolean checkBox) {
      ComponentTitledPanel thePanel;
      if (checkBox) {
         thePanel = new CheckBoxPanel(text);
      } else {
         thePanel = new LabelPanel(text);
      }

      thePanel.setTitleFont(new Font("Dialog", Font.BOLD, 12));
      panelList_.add(thePanel);
      thePanel.setBounds(left, top, right - left, bottom - top);
      dayBorder_ = BorderFactory.createEtchedBorder();
      nightBorder_ = BorderFactory.createEtchedBorder(Color.gray, Color.darkGray);

      thePanel.setLayout(null);
      getContentPane().add(thePanel);
      return thePanel;
   }

   public final void createEmptyPanels() {
      panelList_ = new ArrayList<JPanel>();

      framesPanel_ = (CheckBoxPanel) createPanel("Time points", 5, 5, 220, 91, true); // (text, left, top, right, bottom)
      positionsPanel_ = (CheckBoxPanel) createPanel("Multiple positions (XY)", 5, 93, 220, 154, true);
      slicesPanel_ = (CheckBoxPanel) createPanel("Z-stacks (slices)", 5, 156, 220, 306, true);

      acquisitionOrderPanel_ = createPanel("Acquisition order", 226, 5, 427, 93);

      summaryPanel_ = createPanel("Summary", 226, 176, 427, 306);
      afPanel_ = (CheckBoxPanel) createPanel("Autofocus", 226, 95, 427, 174, true);

      channelsPanel_ = (CheckBoxPanel) createPanel("Channels", 5, 308, 510, 451, true);
      savePanel_ = (CheckBoxPanel) createPanel("Save images", 5, 453, 510, 560, true);
      commentsPanel_ = (ComponentTitledPanel) createPanel("Acquisition Comments",5, 564, 530,650,false);

   }

   private void createToolTips() {
      framesPanel_.setToolTipText("Acquire images over a repeating time interval");
      positionsPanel_.setToolTipText("Acquire images from a series of positions in the XY plane");
      slicesPanel_.setToolTipText("Acquire images from a series of Z positions");
      acquisitionOrderPanel_.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip("Determine the precedence of different acquisition axes (time, slice, channel, and stage position). The rightmost axis will be cycled through most quickly, so e.g. \"Time, Channel\" means \"Collect all channels for each timepoint before going to the next timepoint\"."));

      afPanel_.setToolTipText("Toggle autofocus on/off");
      channelsPanel_.setToolTipText("Lets you acquire images in multiple channels (groups of "
              + "properties with multiple preset values");
      savePanel_.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip("Save images continuously to disk as the acquisition proceeds. If not enabled, then images will be stored in RAM and may be saved later."));


   }

   /**
    * Update the example text describing how the acquisition will proceed.
    */
   private void updateAcquisitionOrderText() {
      if (acquisitionOrderText_ != null && acqOrderBox_ != null &&
            acqOrderBox_.getSelectedItem() != null) {
         acquisitionOrderText_.setText(
               ((AcqOrderMode) (acqOrderBox_.getSelectedItem())).getExample());
      }
   }

   /**
    * Acquisition control dialog box.
    * Specification of all parameters required for the acquisition.
    * @param acqEng - acquisition engine
    * @param gui - ScriptINterface
    */
   public AcqControlDlg(AcquisitionEngine acqEng, MMStudio gui) {
      super("acquisition configuration dialog");

      studio_ = gui;

      setIconImage(Toolkit.getDefaultToolkit().getImage(
              MMStudio.class.getResource(
            "/org/micromanager/icons/microscope.gif")));
      
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      numberFormat_ = NumberFormat.getNumberInstance();

      addWindowListener(new WindowAdapter() {

         @Override
         public void windowClosing(final WindowEvent e) {
            close();
         }
      });
      acqEng_ = acqEng;
      acqEng.addSettingsListener(this);

      getContentPane().setLayout(null);
      setResizable(false);
      setTitle("Multi-Dimensional Acquisition");

      createEmptyPanels();


      // Frames panel
      JPanel defaultPanel = new JPanel();
      JPanel overridePanel = new JPanel();
      defaultPanel.setLayout(null);
      JLabel overrideLabel = new JLabel("Custom time intervals enabled");

      overrideLabel.setFont(new Font("Arial", Font.BOLD, 12));
      overrideLabel.setForeground(Color.red);

      JButton disableCustomIntervalsButton = new JButton("Disable custom intervals");
      disableCustomIntervalsButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            acqEng_.enableCustomTimeIntervals(false);
            updateGUIContents();
         }
      });
      disableCustomIntervalsButton.setFont(new Font("Arial", Font.PLAIN, 10));

      overridePanel.add(overrideLabel, BorderLayout.PAGE_START);
      overridePanel.add(disableCustomIntervalsButton, BorderLayout.PAGE_END);

      framesPanel_.setLayout(new BorderLayout());
      framesSubPanelLayout_ = new CardLayout();
      framesSubPanel_ = new JPanel(framesSubPanelLayout_);
      //this subpanel is needed for the time points panel to properly render
      framesPanel_.add(framesSubPanel_);

      framesSubPanel_.add(defaultPanel, DEFAULT_FRAMES_PANEL_NAME);
      framesSubPanel_.add(overridePanel, OVERRIDE_FRAMES_PANEL_NAME);

      framesSubPanelLayout_.show(framesSubPanel_, DEFAULT_FRAMES_PANEL_NAME);


      framesPanel_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            applySettings();
         }
      });

      final JLabel numberLabel = new JLabel();
      numberLabel.setFont(new Font("Arial", Font.PLAIN, 10));

      numberLabel.setText("Number");
      defaultPanel.add(numberLabel);
      numberLabel.setBounds(15, 0, 54, 24);

      SpinnerModel sModel = new SpinnerNumberModel(
              new Integer(1),
              new Integer(1),
              null,
              new Integer(1));

      numFrames_ = new JSpinner(sModel);
      ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField().setFont(new Font("Arial", Font.PLAIN, 10));

      //numFrames_.setValue((int) acqEng_.getNumFrames());
      defaultPanel.add(numFrames_);
      numFrames_.setBounds(60, 0, 70, 24);
      numFrames_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            applySettings();
         }
      });

      final JLabel intervalLabel = new JLabel();
      intervalLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      intervalLabel.setText("Interval");
      intervalLabel.setToolTipText("Interval between successive time points.  Setting an interval"
              + "of 0 will cause micromanager to acquire 'burts' of images as fast as possible");
      defaultPanel.add(intervalLabel);
      intervalLabel.setBounds(15, 27, 43, 24);

      interval_ = new JFormattedTextField(numberFormat_);
      interval_.setFont(new Font("Arial", Font.PLAIN, 10));
      interval_.setValue(1.0);
      interval_.addPropertyChangeListener("value", this);
      defaultPanel.add(interval_);
      interval_.setBounds(60, 27, 55, 24);

      timeUnitCombo_ = new JComboBox();
      timeUnitCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
      timeUnitCombo_.setFont(new Font("Arial", Font.PLAIN, 10));
      timeUnitCombo_.setBounds(120, 27, 67, 24);
      defaultPanel.add(timeUnitCombo_);


      // Positions (XY) panel


      listButton_ = new JButton();
      listButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.compat().showXYPositionList();
         }
      });
      listButton_.setToolTipText("Open XY list dialog");
      listButton_.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/application_view_list.png")));
      listButton_.setText("Edit position list...");
      listButton_.setMargin(new Insets(2, 5, 2, 5));
      listButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      listButton_.setBounds(42, 25, 136, 26);
      positionsPanel_.add(listButton_);

      // Slices panel

      slicesPanel_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            // enable disable all related contrtols
            applySettings();
         }
      });

      final JLabel zbottomLabel = new JLabel();
      zbottomLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      zbottomLabel.setText("Z-start [um]");
      zbottomLabel.setBounds(30, 30, 69, 15);
      slicesPanel_.add(zbottomLabel);

      zBottom_ = new JFormattedTextField(numberFormat_);
      zBottom_.setFont(new Font("Arial", Font.PLAIN, 10));
      zBottom_.setBounds(95, 27, 54, 21);
      zBottom_.setValue(1.0);
      zBottom_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zBottom_);

      setBottomButton_ = new JButton();
      setBottomButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            setBottomPosition();
         }
      });
      setBottomButton_.setMargin(new Insets(-5, -5, -5, -5));
      setBottomButton_.setFont(new Font("", Font.PLAIN, 10));
      setBottomButton_.setText("Set");
      setBottomButton_.setToolTipText("Set value as microscope's current Z position");
      setBottomButton_.setBounds(150, 27, 50, 22);
      slicesPanel_.add(setBottomButton_);

      final JLabel ztopLabel = new JLabel();
      ztopLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      ztopLabel.setText("Z-end [um]");
      ztopLabel.setBounds(30, 53, 69, 15);
      slicesPanel_.add(ztopLabel);

      zTop_ = new JFormattedTextField(numberFormat_);
      zTop_.setFont(new Font("Arial", Font.PLAIN, 10));
      zTop_.setBounds(95, 50, 54, 21);
      zTop_.setValue(1.0);
      zTop_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zTop_);

      setTopButton_ = new JButton();
      setTopButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            setTopPosition();
         }
      });
      setTopButton_.setMargin(new Insets(-5, -5, -5, -5));
      setTopButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      setTopButton_.setText("Set");
      setTopButton_.setToolTipText("Set value as microscope's current Z position");
      setTopButton_.setBounds(150, 50, 50, 22);
      slicesPanel_.add(setTopButton_);

      final JLabel zstepLabel = new JLabel();
      zstepLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      zstepLabel.setText("Z-step [um]");
      zstepLabel.setBounds(30, 76, 69, 15);
      slicesPanel_.add(zstepLabel);

      zStep_ = new JFormattedTextField(numberFormat_);
      zStep_.setFont(new Font("Arial", Font.PLAIN, 10));
      zStep_.setBounds(95, 73, 54, 21);
      zStep_.setValue(1.0);
      zStep_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zStep_);

      zValCombo_ = new JComboBox();
      zValCombo_.setFont(new Font("Arial", Font.PLAIN, 10));
      zValCombo_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            zValCalcChanged();
         }
      });
      zValCombo_.setModel(new DefaultComboBoxModel(new String[]{"relative Z", "absolute Z"}));
      zValCombo_.setBounds(30, 97, 110, 22);
      slicesPanel_.add(zValCombo_);

      stackKeepShutterOpenCheckBox_ = new JCheckBox();
      stackKeepShutterOpenCheckBox_.setText("Keep shutter open");
      stackKeepShutterOpenCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      stackKeepShutterOpenCheckBox_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            applySettings();
         }
      });
      stackKeepShutterOpenCheckBox_.setSelected(false);
      stackKeepShutterOpenCheckBox_.setBounds(60, 121, 150, 22);
      slicesPanel_.add(stackKeepShutterOpenCheckBox_);

      // Acquisition order panel

      acqOrderBox_ = new JComboBox();
      acqOrderBox_.setFont(new Font("", Font.PLAIN, 10));
      acqOrderBox_.setBounds(2, 26, 195, 22);
      acquisitionOrderPanel_.add(acqOrderBox_);
      acquisitionOrderText_ = new JLabel(" ");
      acquisitionOrderText_.setFont(new Font("", Font.PLAIN, 9));
      acquisitionOrderText_.setBounds(5, 32, 195, 60);
      acquisitionOrderPanel_.add(acquisitionOrderText_);

      acqOrderModes_ = new AcqOrderMode[4];
      acqOrderModes_[0] = new AcqOrderMode(AcqOrderMode.TIME_POS_SLICE_CHANNEL);
      acqOrderModes_[1] = new AcqOrderMode(AcqOrderMode.TIME_POS_CHANNEL_SLICE);
      acqOrderModes_[2] = new AcqOrderMode(AcqOrderMode.POS_TIME_SLICE_CHANNEL);
      acqOrderModes_[3] = new AcqOrderMode(AcqOrderMode.POS_TIME_CHANNEL_SLICE);
      acqOrderBox_.addItem(acqOrderModes_[0]);
      acqOrderBox_.addItem(acqOrderModes_[1]);
      acqOrderBox_.addItem(acqOrderModes_[2]);
      acqOrderBox_.addItem(acqOrderModes_[3]);


      // Summary panel

      summaryTextArea_ = new JTextArea();
      summaryTextArea_.setFont(new Font("Arial", Font.PLAIN, 11));
      summaryTextArea_.setEditable(false);
      summaryTextArea_.setBounds(4, 19, 350, 120);
      summaryTextArea_.setMargin(new Insets(2, 2, 2, 2));
      summaryTextArea_.setOpaque(false);
      summaryPanel_.add(summaryTextArea_);

      // Autofocus panel

      afPanel_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
         }
      });

      afButton_ = new JButton();
      afButton_.setToolTipText("Set autofocus options");
      afButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent arg0) {
            afOptions();
         }
      });
      afButton_.setText("Options...");
      afButton_.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/wrench_orange.png")));
      afButton_.setMargin(new Insets(2, 5, 2, 5));
      afButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      afButton_.setBounds(50, 26, 100, 28);
      afPanel_.add(afButton_);


      final JLabel afSkipFrame1 = new JLabel();
      afSkipFrame1.setFont(new Font("Dialog", Font.PLAIN, 10));
      afSkipFrame1.setText("Skip frame(s): ");
      afSkipFrame1.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip("The number of 'frames skipped' corresponds"
              + "to the number of time intervals of image acquisition that pass before micromanager autofocuses again.  Micromanager "
              + "will always autofocus when moving to a new position regardless of this value"));


      afSkipFrame1.setBounds(35, 54, 70, 21);
      afPanel_.add(afSkipFrame1);


      afSkipInterval_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
      ((JSpinner.DefaultEditor) afSkipInterval_.getEditor()).getTextField().setFont(new Font("Arial", Font.PLAIN, 10));
      afSkipInterval_.setBounds(105, 54, 55, 22);
      afSkipInterval_.setValue(acqEng_.getAfSkipInterval());
      afSkipInterval_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            applySettings();
            afSkipInterval_.setValue(acqEng_.getAfSkipInterval());
         }
      });
      afPanel_.add(afSkipInterval_);


      // Channels panel
      channelsPanel_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            applySettings();
         }
      });

      final JLabel channelsLabel = new JLabel();
      channelsLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      channelsLabel.setBounds(90, 19, 80, 24);
      channelsLabel.setText("Channel group:");
      channelsPanel_.add(channelsLabel);


      channelGroupCombo_ = new JComboBox();
      channelGroupCombo_.setFont(new Font("", Font.PLAIN, 10));
      updateGroupsCombo();

      channelGroupCombo_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent arg0) {
            String newGroup = (String) channelGroupCombo_.getSelectedItem();

            if (acqEng_.setChannelGroup(newGroup)) {
               model_.cleanUpConfigurationList();
               if (studio_.getAutofocusManager() != null) {
                  try {
                     studio_.getAutofocusManager().refresh();
                  } catch (MMException e) {
                     ReportingUtils.showError(e);
                  }
               }
            } else {
               updateGroupsCombo();
            }
         }
      });
      channelGroupCombo_.setBounds(165, 20, 150, 22);
      channelsPanel_.add(channelGroupCombo_);

      channelTablePane_ = new JScrollPane();
      channelTablePane_.setFont(new Font("Arial", Font.PLAIN, 10));
      channelTablePane_.setBounds(10, 45, 414, 90);
      channelsPanel_.add(channelTablePane_);


      final JButton addButton = new JButton();
      addButton.setFont(new Font("Arial", Font.PLAIN, 10));
      addButton.setMargin(new Insets(0, 0, 0, 0));
      addButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            applySettings();
            model_.addNewChannel();
            model_.fireTableStructureChanged();
         }
      });
      addButton.setText("New");
      addButton.setToolTipText("Create new channel for currently selected channel group");
      addButton.setBounds(430, 45, 68, 22);
      channelsPanel_.add(addButton);

      final JButton removeButton = new JButton();
      removeButton.setFont(new Font("Arial", Font.PLAIN, 10));
      removeButton.setMargin(new Insets(-5, -5, -5, -5));
      removeButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            int sel = channelTable_.getSelectedRow();
            if (sel > -1) {
               applySettings();
               model_.removeChannel(sel);
               model_.fireTableStructureChanged();
               if (channelTable_.getRowCount() > sel) {
                  channelTable_.setRowSelectionInterval(sel, sel);
               }
            }
         }
      });
      removeButton.setText("Remove");
      removeButton.setToolTipText("Remove currently selected channel");
      removeButton.setBounds(430, 69, 68, 22);
      channelsPanel_.add(removeButton);

      final JButton upButton = new JButton();
      upButton.setFont(new Font("Arial", Font.PLAIN, 10));
      upButton.setMargin(new Insets(0, 0, 0, 0));
      upButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            int sel = channelTable_.getSelectedRow();
            if (sel > -1) {
               applySettings();
               int newSel = model_.rowUp(sel);
               model_.fireTableStructureChanged();
               channelTable_.setRowSelectionInterval(newSel, newSel);
               //applySettings();
            }
         }
      });
      upButton.setText("Up");
      upButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Move currently selected channel up (Channels higher on list are acquired first)"));
      upButton.setBounds(430, 93, 68, 22);
      channelsPanel_.add(upButton);

      final JButton downButton = new JButton();
      downButton.setFont(new Font("Arial", Font.PLAIN, 10));
      downButton.setMargin(new Insets(0, 0, 0, 0));
      downButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            int sel = channelTable_.getSelectedRow();
            if (sel > -1) {
               applySettings();
               int newSel = model_.rowDown(sel);
               model_.fireTableStructureChanged();
               channelTable_.setRowSelectionInterval(newSel, newSel);
               //applySettings();
            }
         }
      });
      downButton.setText("Down");
      downButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Move currently selected channel down (Channels lower on list are acquired later)"));
      downButton.setBounds(430, 117, 68, 22);
      channelsPanel_.add(downButton);

      chanKeepShutterOpenCheckBox_ = new JCheckBox();
      chanKeepShutterOpenCheckBox_.setText("Keep shutter open");
      chanKeepShutterOpenCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      chanKeepShutterOpenCheckBox_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            applySettings();
         }
      });
      chanKeepShutterOpenCheckBox_.setSelected(false);
      chanKeepShutterOpenCheckBox_.setBounds(330, 20, 150, 22);
      channelsPanel_.add(chanKeepShutterOpenCheckBox_);


      // Save panel
      savePanel_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            applySettings();
         }
      });

      rootLabel_ = new JLabel();
      rootLabel_.setFont(new Font("Arial", Font.PLAIN, 10));
      rootLabel_.setText("Directory root");
      rootLabel_.setBounds(10, 30, 72, 22);
      savePanel_.add(rootLabel_);

      rootField_ = new JTextField();
      rootField_.setFont(new Font("Arial", Font.PLAIN, 10));
      rootField_.setBounds(90, 30, 354, 22);
      savePanel_.add(rootField_);

      browseRootButton_ = new JButton();
      browseRootButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            setRootDirectory();
         }
      });
      browseRootButton_.setMargin(new Insets(2, 5, 2, 5));
      browseRootButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      browseRootButton_.setText("...");
      browseRootButton_.setBounds(445, 30, 47, 24);
      savePanel_.add(browseRootButton_);
      browseRootButton_.setToolTipText("Browse");

      namePrefixLabel_ = new JLabel();
      namePrefixLabel_.setFont(new Font("Arial", Font.PLAIN, 10));
      namePrefixLabel_.setText("Name prefix");
      namePrefixLabel_.setBounds(10, 55, 76, 22);
      savePanel_.add(namePrefixLabel_);

      nameField_ = new JTextField();
      nameField_.setFont(new Font("Arial", Font.PLAIN, 10));
      nameField_.setBounds(90, 55, 354, 22);
      savePanel_.add(nameField_);
      
      saveTypeLabel_ = new JLabel("Saving format: ");         
      saveTypeLabel_.setFont(new Font("Arial", Font.PLAIN, 10));
      saveTypeLabel_.setBounds(10,80, 100,22);
      savePanel_.add(saveTypeLabel_);

      
      singleButton_ = new JRadioButton("Separate image files");
      singleButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      singleButton_.setBounds(110,80,150,22);
      savePanel_.add(singleButton_);
      singleButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DefaultDatastore.setPreferredSaveMode(
               Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
         }});

      multiButton_ = new JRadioButton("Image stack file");
      multiButton_.setFont(new Font("Arial", Font.PLAIN, 10));      
      multiButton_.setBounds(260,80,200,22);
      savePanel_.add(multiButton_);
      multiButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DefaultDatastore.setPreferredSaveMode(
               Datastore.SaveMode.MULTIPAGE_TIFF);
         }});
      
      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(singleButton_);
      buttonGroup.add(multiButton_);
      updateSavingTypeButtons();

      JScrollPane commentScrollPane = new JScrollPane();
      commentScrollPane.setBounds(10, 28, 485, 50);
      commentsPanel_.add(commentScrollPane);

      commentTextArea_ = new JTextArea();
      commentScrollPane.setViewportView(commentTextArea_);
      commentTextArea_.setFont(new Font("", Font.PLAIN, 10));
      commentTextArea_.setToolTipText("Comment for the current acquistion");
      commentTextArea_.setWrapStyleWord(true);
      commentTextArea_.setLineWrap(true);
      commentTextArea_.setBorder(new EtchedBorder(EtchedBorder.LOWERED));



      // Main buttons
      final JButton closeButton = new JButton();
      closeButton.setFont(new Font("Arial", Font.PLAIN, 10));
      closeButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            saveSettings();
            saveAcqSettings();
            AcqControlDlg.this.dispose();
            studio_.compat().makeActive();
         }
      });
      closeButton.setText("Close");
      closeButton.setBounds(432, 10, 80, 22);
      getContentPane().add(closeButton);

      acquireButton_ = new JButton();
      acquireButton_.setMargin(new Insets(-9, -9, -9, -9));
      acquireButton_.setFont(new Font("Arial", Font.BOLD, 12));
      acquireButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            AbstractCellEditor ae = (AbstractCellEditor) channelTable_.getCellEditor();
            if (ae != null) {
               ae.stopCellEditing();
            }
            runAcquisition();
         }
      });
      acquireButton_.setText("Acquire!");
      acquireButton_.setBounds(432, 44, 80, 22);
      getContentPane().add(acquireButton_);


      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(final ActionEvent e) {
            acqEng_.abortRequest();
         }
      });
      stopButton.setText("Stop");
      stopButton.setFont(new Font("Arial", Font.BOLD, 12));
      stopButton.setBounds(432, 68, 80, 22);
      getContentPane().add(stopButton);



      final JButton loadButton = new JButton();
      loadButton.setFont(new Font("Arial", Font.PLAIN, 10));
      loadButton.setMargin(new Insets(-5, -5, -5, -5));
      loadButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            loadAcqSettingsFromFile();
         }
      });

      loadButton.setText("Load...");
      loadButton.setBounds(432, 102, 80, 22);
      getContentPane().add(loadButton);
      loadButton.setToolTipText("Load acquisition settings");

      final JButton saveAsButton = new JButton();
      saveAsButton.setFont(new Font("Arial", Font.PLAIN, 10));
      saveAsButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            saveAsAcqSettingsToFile();
         }
      });
      saveAsButton.setToolTipText("Save current acquisition settings as");
      saveAsButton.setText("Save as...");
      saveAsButton.setBounds(432, 126, 80, 22);
      saveAsButton.setMargin(new Insets(-5, -5, -5, -5));
      getContentPane().add(saveAsButton);

      final JButton advancedButton = new JButton();
      advancedButton.setFont(new Font("Arial", Font.PLAIN, 10));
      advancedButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            showAdvancedDialog();
            updateGUIContents();
         }
      });
      advancedButton.setText("Advanced");
      advancedButton.setBounds(432, 170, 80, 22);
      getContentPane().add(advancedButton);

      // update GUI contents
      // -------------------


      // add update event listeners
      positionsPanel_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
         }
      });
      acqOrderBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateAcquisitionOrderText();
            applySettings();
         }
      });


      // load acquistion settings
      loadAcqSettings();

      // create the table of channels
      createChannelTable();

      // update summary
      updateGUIContents();

      // update settings in the acq engine
      applySettings();

      createToolTips();
      
      // load window position from prefs
      this.loadAndRestorePosition(100, 100);
      this.setSize(521, 690);

   }

   /** 
    * Called when a field's "value" property changes. 
    * Causes the Summary to be updated
    * @param e
    */
   @Override
   public void propertyChange(PropertyChangeEvent e) {
      // update summary
      applySettings();
      summaryTextArea_.setText(acqEng_.getVerboseSummary());
   }
   
   /**
    * Sets the exposure time of a given channel
    * The channel has to be preset in the current channel group
    * Will also update the exposure associated with this channel in the preferences,
    * i.e. even if the preset is not shown, this exposure time will be used
    * next time it is shown
    * 
    * @param channelGroup - name of the channelgroup.  If it does not match the current
    * channel group, no action will be taken
    * @param channel - name of the preset in the current channel group
    * @param exposure  - new exposure time
    */
   public void setChannelExposureTime(String channelGroup, String channel, 
           double exposure) {
      if (!channelGroup.equals(acqEng_.getChannelGroup()) ||
               acqEng_.getChannelConfigs().length <= 0) {
         return;
      }
      for (String config : acqEng_.getChannelConfigs()) {
         if (channel.equals(config)) {
            setChannelExposure(acqEng_.getChannelGroup(), channel, exposure);
            model_.setChannelExposureTime(channelGroup, channel, exposure);
         }
      }
   }
   
   /**
    * Returns exposure time for the desired preset in the given channelgroup
    * Acquires its info from the preferences
    * 
    * @param channelGroup
    * @param channel - 
    * @param defaultExp - default value
    * @return exposure time
    */
   public double getChannelExposureTime(String channelGroup, String channel,
           double defaultExp) {
      // Redirect to the static version.
      return getChannelExposure(channelGroup, channel, defaultExp);
   }

   protected void afOptions() {
      if (studio_.getAutofocusManager().getDevice() != null) {
         studio_.getAutofocusManager().showOptionsDialog();
      }
   }

   public boolean inArray(String member, String[] group) {
      for (String group1 : group) {
         if (member.equals(group1)) {
            return true;
         }
      }
      return false;
   }
   
   public final void updateSavingTypeButtons() {
      Datastore.SaveMode mode = studio_.data().getPreferredSaveMode();
      if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
         singleButton_.setSelected(true);
      }
      else if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
         multiButton_.setSelected(true);
      }
      else {
         ReportingUtils.logError("Unrecognized save mode " + mode);
      }
   }

   public void close() {
      try {
         saveSettings();
      } catch (Throwable t) {
         ReportingUtils.logError(t, "in saveSettings");
      }
      try {
         saveAcqSettings();
      } catch (Throwable t) {
         ReportingUtils.logError(t, "in saveAcqSettings");
      }
      try {
         dispose();
      } catch (Throwable t) {
         ReportingUtils.logError(t, "in dispose");
      }
      if (null != studio_) {
         try {
            studio_.compat().makeActive();
         } catch (Throwable t) {
            ReportingUtils.logError(t, "in makeActive");
         }
      }
   }

   public final void updateGroupsCombo() {
      String groups[] = acqEng_.getAvailableGroups();
      if (groups.length != 0) {
         channelGroupCombo_.setModel(new DefaultComboBoxModel(groups));
         if (!inArray(acqEng_.getChannelGroup(), groups)) {
            acqEng_.setChannelGroup(acqEng_.getFirstConfigGroup());
         }

         channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());
      }
   }

   public void updateChannelAndGroupCombo() {
      updateGroupsCombo();
      model_.cleanUpConfigurationList();
   }

   public final synchronized void loadAcqSettings() {
      disableGUItoSettings_ = true;

      // load acquisition engine preferences
      acqEng_.clear();
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      int numFrames = profile.getInt(this.getClass(), ACQ_NUMFRAMES, 1);
      double interval = profile.getDouble(this.getClass(), ACQ_INTERVAL, 0.0);

      acqEng_.setFrames(numFrames, interval);
      acqEng_.enableFramesSetting(profile.getBoolean(
               this.getClass(), ACQ_ENABLE_MULTI_FRAME, false));

      boolean framesEnabled = acqEng_.isFramesSettingEnabled(); 
      framesPanel_.setSelected(framesEnabled);
      framesPanel_.setSelected(framesEnabled);
      Component[] comps = framesSubPanel_.getComponents();
      for (Component c: comps)
         for (Component co: ((JPanel)c).getComponents() )
            co.setEnabled(framesEnabled);
      framesPanel_.repaint();
            
      numFrames_.setValue(acqEng_.getNumFrames());

      int unit = profile.getInt(this.getClass(), ACQ_TIME_UNIT, 0);
      timeUnitCombo_.setSelectedIndex(unit);

      double bottom = profile.getDouble(this.getClass(), ACQ_ZBOTTOM, 0.0);
      double top = profile.getDouble(this.getClass(), ACQ_ZTOP, 0.0);
      // TODO: ideally we would be able to check this value against the
      // physical resolution of the Z positioner.
      double step = profile.getDouble(this.getClass(), ACQ_ZSTEP, 1.0);
      zVals_ = profile.getInt(this.getClass(), ACQ_Z_VALUES, 0);
      acqEng_.setSlices(bottom, top, step, (zVals_ != 0));
      acqEng_.enableZSliceSetting(profile.getBoolean(this.getClass(), ACQ_ENABLE_SLICE_SETTINGS, acqEng_.isZSliceSettingEnabled()));
      acqEng_.enableMultiPosition(profile.getBoolean(this.getClass(), ACQ_ENABLE_MULTI_POSITION, acqEng_.isMultiPositionEnabled()));
      positionsPanel_.setSelected(acqEng_.isMultiPositionEnabled());
      positionsPanel_.repaint();

      slicesPanel_.setSelected(acqEng_.isZSliceSettingEnabled());
      slicesPanel_.repaint();

      acqEng_.enableChannelsSetting(profile.getBoolean(this.getClass(), ACQ_ENABLE_MULTI_CHANNEL, false));
      channelsPanel_.setSelected(acqEng_.isChannelsSettingEnabled());
      channelsPanel_.repaint();

      savePanel_.setSelected(profile.getBoolean(this.getClass(), ACQ_SAVE_FILES, false));

      nameField_.setText(profile.getString(this.getClass(), ACQ_DIR_NAME, "Untitled"));
      String os_name = System.getProperty("os.name", "");
      rootField_.setText(profile.getString(this.getClass(), ACQ_ROOT_NAME, System.getProperty("user.home") + "/AcquisitionData"));

      acqEng_.setAcqOrderMode(profile.getInt(this.getClass(), ACQ_ORDER_MODE, acqEng_.getAcqOrderMode()));

      acqEng_.enableAutoFocus(profile.getBoolean(this.getClass(), ACQ_AF_ENABLE, acqEng_.isAutoFocusEnabled()));
      acqEng_.setAfSkipInterval(profile.getInt(this.getClass(), ACQ_AF_SKIP_INTERVAL, acqEng_.getAfSkipInterval()));
      acqEng_.setChannelGroup(profile.getString(this.getClass(), ACQ_CHANNEL_GROUP, acqEng_.getFirstConfigGroup()));
      afPanel_.setSelected(acqEng_.isAutoFocusEnabled());
      acqEng_.keepShutterOpenForChannels(profile.getBoolean(this.getClass(), ACQ_CHANNELS_KEEP_SHUTTER_OPEN, false));
      acqEng_.keepShutterOpenForStack(profile.getBoolean(this.getClass(), ACQ_STACK_KEEP_SHUTTER_OPEN, false));

      ArrayList<Double> customIntervals = new ArrayList<Double>();
      int h = 0;
      while (profile.getDouble(this.getClass(), CUSTOM_INTERVAL_PREFIX + h, -1.0) >= 0.0) {
         customIntervals.add(profile.getDouble(this.getClass(), CUSTOM_INTERVAL_PREFIX + h, -1.0));
         h++;
      }
      double[] intervals = new double[customIntervals.size()];
      for (int j = 0; j < intervals.length; j++) {
         intervals[j] = customIntervals.get(j);
      }
      acqEng_.setCustomTimeIntervals(intervals);
      acqEng_.enableCustomTimeIntervals(profile.getBoolean(this.getClass(), ACQ_ENABLE_CUSTOM_INTERVALS, false));


      int numChannels = profile.getInt(this.getClass(), ACQ_NUM_CHANNELS, 0);

      ChannelSpec defaultChannel = new ChannelSpec();

      acqEng_.getChannels().clear();
      for (int i = 0; i < numChannels; i++) {
         String name = profile.getString(this.getClass(), CHANNEL_NAME_PREFIX + i, "Undefined");
         boolean use = profile.getBoolean(this.getClass(), CHANNEL_USE_PREFIX + i, true);
         double exp = profile.getDouble(this.getClass(), CHANNEL_EXPOSURE_PREFIX + i, 0.0);
         Boolean doZStack = profile.getBoolean(this.getClass(), CHANNEL_DOZSTACK_PREFIX + i, true);
         double zOffset = profile.getDouble(this.getClass(), CHANNEL_ZOFFSET_PREFIX + i, 0.0);
         ContrastSettings con = new ContrastSettings();
         con.min = profile.getInt(this.getClass(), CHANNEL_CONTRAST_MIN_PREFIX + i, defaultChannel.contrast.min);
         con.max = profile.getInt(this.getClass(), CHANNEL_CONTRAST_MAX_PREFIX + i, defaultChannel.contrast.max);
         con.gamma = profile.getDouble(this.getClass(), CHANNEL_CONTRAST_GAMMA_PREFIX + i, defaultChannel.contrast.gamma);
         int r = profile.getInt(this.getClass(), CHANNEL_COLOR_R_PREFIX + i, defaultChannel.color.getRed());
         int g = profile.getInt(this.getClass(), CHANNEL_COLOR_G_PREFIX + i, defaultChannel.color.getGreen());
         int b = profile.getInt(this.getClass(), CHANNEL_COLOR_B_PREFIX + i, defaultChannel.color.getBlue());
         int skip = profile.getInt(this.getClass(), CHANNEL_SKIP_PREFIX + i, defaultChannel.skipFactorFrame);
         Color c = new Color(r, g, b);
         acqEng_.addChannel(name, exp, doZStack, zOffset, con, skip, c, use);
      }

      // Restore Column Width and Column order
      int columnCount = 7;
      columnWidth_ = new int[columnCount];
      columnOrder_ = new int[columnCount];
      for (int k = 0; k < columnCount; k++) {
         columnWidth_[k] = profile.getInt(this.getClass(), ACQ_COLUMN_WIDTH + k, ACQ_DEFAULT_COLUMN_WIDTH);
         columnOrder_[k] = profile.getInt(this.getClass(), ACQ_COLUMN_ORDER + k, k);
      }

      disableGUItoSettings_ = false;
   }

   public synchronized void saveAcqSettings() {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();

      applySettings();

      profile.setBoolean(this.getClass(), ACQ_ENABLE_MULTI_FRAME, acqEng_.isFramesSettingEnabled());
      profile.setBoolean(this.getClass(), ACQ_ENABLE_MULTI_CHANNEL, acqEng_.isChannelsSettingEnabled());
      profile.setInt(this.getClass(), ACQ_NUMFRAMES, acqEng_.getNumFrames());
      profile.setDouble(this.getClass(), ACQ_INTERVAL, acqEng_.getFrameIntervalMs());
      profile.setInt(this.getClass(), ACQ_TIME_UNIT, timeUnitCombo_.getSelectedIndex());
      profile.setDouble(this.getClass(), ACQ_ZBOTTOM, acqEng_.getSliceZBottomUm());
      profile.setDouble(this.getClass(), ACQ_ZTOP, acqEng_.getZTopUm());
      profile.setDouble(this.getClass(), ACQ_ZSTEP, acqEng_.getSliceZStepUm());
      profile.setBoolean(this.getClass(), ACQ_ENABLE_SLICE_SETTINGS, acqEng_.isZSliceSettingEnabled());
      profile.setBoolean(this.getClass(), ACQ_ENABLE_MULTI_POSITION, acqEng_.isMultiPositionEnabled());
      profile.setInt(this.getClass(), ACQ_Z_VALUES, zVals_);
      profile.setBoolean(this.getClass(), ACQ_SAVE_FILES, savePanel_.isSelected());
      profile.setString(this.getClass(), ACQ_DIR_NAME, nameField_.getText());
      profile.setString(this.getClass(), ACQ_ROOT_NAME, rootField_.getText());

      profile.setInt(this.getClass(), ACQ_ORDER_MODE, acqEng_.getAcqOrderMode());

      profile.setBoolean(this.getClass(), ACQ_AF_ENABLE, acqEng_.isAutoFocusEnabled());
      profile.setInt(this.getClass(), ACQ_AF_SKIP_INTERVAL, acqEng_.getAfSkipInterval());
      profile.setBoolean(this.getClass(), ACQ_CHANNELS_KEEP_SHUTTER_OPEN, acqEng_.isShutterOpenForChannels());
      profile.setBoolean(this.getClass(), ACQ_STACK_KEEP_SHUTTER_OPEN, acqEng_.isShutterOpenForStack());

      profile.setString(this.getClass(), ACQ_CHANNEL_GROUP, acqEng_.getChannelGroup());
      ArrayList<ChannelSpec> channels = acqEng_.getChannels();
      profile.setInt(this.getClass(), ACQ_NUM_CHANNELS, channels.size());
      for (int i = 0; i < channels.size(); i++) {
         ChannelSpec channel = channels.get(i);
         profile.setString(this.getClass(), CHANNEL_NAME_PREFIX + i, channel.config);
         profile.setBoolean(this.getClass(), CHANNEL_USE_PREFIX + i, channel.useChannel);
         profile.setDouble(this.getClass(), CHANNEL_EXPOSURE_PREFIX + i, channel.exposure);
         profile.setBoolean(this.getClass(), CHANNEL_DOZSTACK_PREFIX + i, channel.doZStack);
         profile.setDouble(this.getClass(), CHANNEL_ZOFFSET_PREFIX + i, channel.zOffset);
         profile.setInt(this.getClass(), CHANNEL_CONTRAST_MIN_PREFIX + i, channel.contrast.min);
         profile.setInt(this.getClass(), CHANNEL_CONTRAST_MAX_PREFIX + i, channel.contrast.max);
         profile.setDouble(this.getClass(), CHANNEL_CONTRAST_GAMMA_PREFIX + i, channel.contrast.gamma);
         profile.setInt(this.getClass(), CHANNEL_COLOR_R_PREFIX + i, channel.color.getRed());
         profile.setInt(this.getClass(), CHANNEL_COLOR_G_PREFIX + i, channel.color.getGreen());
         profile.setInt(this.getClass(), CHANNEL_COLOR_B_PREFIX + i, channel.color.getBlue());
         profile.setInt(this.getClass(), CHANNEL_SKIP_PREFIX + i, channel.skipFactorFrame);
      }

      //Save custom time intervals
      double[] customIntervals = acqEng_.getCustomTimeIntervals();
      if (customIntervals != null && customIntervals.length > 0) {
         for (int h = 0; h < customIntervals.length; h++) {
            profile.setDouble(this.getClass(), CUSTOM_INTERVAL_PREFIX + h, customIntervals[h]);
         }
      }

      profile.setBoolean(this.getClass(), ACQ_ENABLE_CUSTOM_INTERVALS, acqEng_.customTimeIntervalsEnabled());

      // Save preferred save mode.
      if (singleButton_.isSelected()) {
         DefaultDatastore.setPreferredSaveMode(
            Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
      }
      else if (multiButton_.isSelected()) {
         DefaultDatastore.setPreferredSaveMode(
            Datastore.SaveMode.MULTIPAGE_TIFF);
      }
      else {
         ReportingUtils.logError("Unknown save mode button is selected, or no buttons are selected");
      }

      // Save model column widths and order
      for (int k = 0; k < model_.getColumnCount(); k++) {
         profile.setInt(this.getClass(), ACQ_COLUMN_WIDTH + k, findTableColumn(channelTable_, k).getWidth());
         profile.setInt(this.getClass(), ACQ_COLUMN_ORDER + k, channelTable_.convertColumnIndexToView(k));
      }
   }

   // Returns the TableColumn associated with the specified column
   // index in the model
   public TableColumn findTableColumn(JTable table, int columnModelIndex) {
      Enumeration<?> e = table.getColumnModel().getColumns();
      for (; e.hasMoreElements();) {
         TableColumn col = (TableColumn) e.nextElement();
         if (col.getModelIndex() == columnModelIndex) {
            return col;
         }
      }
      return null;
   }

   protected void enableZSliceControls(boolean state) {
      zBottom_.setEnabled(state);
      zTop_.setEnabled(state);
      zStep_.setEnabled(state);
      zValCombo_.setEnabled(state);
   }

   protected void setRootDirectory() {
      File result = FileDialogs.openDir(this,
              "Please choose a directory root for image data",
              MMStudio.MM_DATA_SET);
      if (result != null) {
         rootField_.setText(result.getAbsolutePath());
         acqEng_.setRootName(result.getAbsolutePath());
      }
   }

   public void setTopPosition() {
      double z = acqEng_.getCurrentZPos();
      zTop_.setText(NumberUtils.doubleToDisplayString(z));
      applySettings();
      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary());
   }

   protected void setBottomPosition() {
      double z = acqEng_.getCurrentZPos();
      zBottom_.setText(NumberUtils.doubleToDisplayString(z));
      applySettings();
      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary());
   }

   protected void loadAcqSettingsFromFile() {
      File f = FileDialogs.openFile(this, "Load acquisition settings", ACQ_SETTINGS_FILE);
      if (f != null) {
         try {
            loadAcqSettingsFromFile(f.getAbsolutePath());
         } catch (MMScriptException ex) {
            ReportingUtils.showError("Failed to load Acquisition setting file");
         }
      }
   }

   public void loadAcqSettingsFromFile(String path) throws MMScriptException {
      acqFile_ = new File(path);
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      try {
         profile.appendFile(path);
         loadAcqSettings();
         GUIUtils.invokeAndWait(new Runnable() {
            @Override
            public void run() {
               updateGUIContents();
            }
         });
         acqDir_ = acqFile_.getParent();
         if (acqDir_ != null) {
            profile.setString(this.getClass(), ACQ_FILE_DIR, acqDir_);
         }
      } catch (InterruptedException e) {
         throw new MMScriptException (e);
      } catch (InvocationTargetException e) {
         throw new MMScriptException (e);
      }
   }
   
   protected boolean saveAsAcqSettingsToFile() {
      saveAcqSettings();
      File f = FileDialogs.save(this, "Save the acquisition settings file", ACQ_SETTINGS_FILE);
      if (f != null) {
         try {
            DefaultUserProfile.getInstance().exportProfileSubsetToFile(
                  this.getClass(), f.getAbsolutePath());
         } catch (IOException e) {
            ReportingUtils.showError(e);
            return false;
         }
         return true;
      }
      return false;
   }

   private long estimateMemoryUsage() {
      // XXX This ought to be done by the acquisition engine
      boolean channels = channelsPanel_.isSelected();
      boolean frames = framesPanel_.isSelected();
      boolean slices = slicesPanel_.isSelected();
      boolean positions = positionsPanel_.isSelected();
      
      int numFrames = Math.max(1, (Integer) numFrames_.getValue());
      if (acqEng_.customTimeIntervalsEnabled()) {
         int h = 0;
         while (DefaultUserProfile.getInstance().getDouble(this.getClass(),
                  CUSTOM_INTERVAL_PREFIX + h, -1.0) >= 0.0) {
            h++;
         }
         numFrames = Math.max(1, h);
      }
      
      double zTop, zBottom, zStep;
      try {
         zTop = NumberUtils.displayStringToDouble(zTop_.getText());
         zBottom = NumberUtils.displayStringToDouble(zBottom_.getText());
         zStep = NumberUtils.displayStringToDouble(zStep_.getText()); 
      } catch (ParseException ex) {
         ReportingUtils.showError("Invalid Z-Stacks input value");
         return -1;
      }
      
      int numSlices = Math.max(1, (int) (1 + Math.floor( 
              (Math.abs(zTop - zBottom) /  zStep))));
      int numPositions = 1;
      try {
         numPositions = Math.max(1, studio_.compat().getPositionList().getNumberOfPositions());
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex);
      }
      
      int numImages;
      
      if (channels) {
         ArrayList<ChannelSpec> list = ((ChannelTableModel) channelTable_.getModel() ).getChannels();
         ArrayList<Integer> imagesPerChannel = new ArrayList<Integer>();
         for (ChannelSpec list1 : list) {
            if (!list1.useChannel) {
               continue;
            }
            int num = 1;
            if (frames) {
               num *= Math.max(1, numFrames / (list1.skipFactorFrame + 1));
            }
            if (slices && list1.doZStack) {
               num *= numSlices;
            }
            if (positions) {
               num *= numPositions;
            }
            imagesPerChannel.add(num);
         }
         numImages = 0;
         for (Integer i : imagesPerChannel) {
            numImages += i;
         }
      } else {
         numImages = 1;
         if (slices) {
            numImages *= numSlices;
         }
         if (frames) {
            numImages *= numFrames;
         }
         if (positions) {
            numImages *= numPositions;
         }
      }

      CMMCore core = MMStudio.getInstance().getCore();
      long byteDepth = core.getBytesPerPixel();
      long width = core.getImageWidth();
      long height = core.getImageHeight();
      long bytesPerImage = byteDepth*width*height;

      return bytesPerImage * numImages;
   }

   // Returns false if user chooses to cancel.
   private boolean warnIfMemoryMayNotBeSufficient() {
      if (savePanel_.isSelected())
         return true; 

      long acqTotalBytes = estimateMemoryUsage();
      if (acqTotalBytes < 0) {
         return false;
      }

      // Currently, images are stored in direct byte buffers in the case of
      // acquire-to-RAM. This means that the image (pixel and metadata) data do
      // not fill up the Java heap memory. The best we can do is to try to
      // estimate the available physical memory.
      //
      // In reality, there is a hard cap to the direct memory size in the
      // HotSpot JVM, but there is no way to get that limit, much less how much
      // of it is currently in use (there is the non-API
      // sun.misc.VM.maxDirectMemory() method, which we could call via
      // reflection where available, but we would need to estimate current
      // usage on our own). The limit can be set from the JVM command line
      // using e.g. -XX:MaxDirectMemorySize=16G.
      //
      // As of this writing, we ship the 64-bit version with
      // -XX:MaxDirectMemroySize=1000g, which essentially disables the limit,
      // so the assumptions we make here are not completely off.

      long freeRAM;
      java.lang.management.OperatingSystemMXBean osMXB =
         java.lang.management.ManagementFactory.getOperatingSystemMXBean();
      try { // Use HotSpot extensions if available
         Class<?> sunOSMXBClass = Class.forName("com.sun.management.OperatingSystemMXBean");
         java.lang.reflect.Method freeMemMethod = sunOSMXBClass.getMethod("getFreePhysicalMemorySize");
         freeRAM = ((Long) freeMemMethod.invoke(osMXB));
      }
      catch (ClassNotFoundException e) {
         return true; // We just don't warn the user in this case.
      } catch (NoSuchMethodException e) {
         return true; // We just don't warn the user in this case.
      } catch (SecurityException e) {
         return true; // We just don't warn the user in this case.
      } catch (IllegalAccessException e) {
         return true; // We just don't warn the user in this case.
      } catch (IllegalArgumentException e) {
         return true; // We just don't warn the user in this case.
      } catch (InvocationTargetException e) {
         return true; // We just don't warn the user in this case.
      }

      // There is no hard reason for the 80% factor.
      if (acqTotalBytes > 0.8 * freeRAM) {
         int answer = JOptionPane.showConfirmDialog(this,
               "<html><body><p width='400'>" +
               "Available RAM may not be sufficient for this acquisition " +
               "(the estimate is approximate). After RAM is exhausted, the " +
               "acquisition may slow down or fail.</p>" +
               "<p>Would you like to start the acquisition anyway?</p>" +
               "</body></html>",
               "Insufficient memory warning",
               JOptionPane.YES_NO_OPTION);
         return answer == 0;
      }
      return true;
   }

   public Datastore runAcquisition() {
      if (acqEng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this, "Cannot start acquisition: previous acquisition still in progress.");
         return null;
      }

      if (!warnIfMemoryMayNotBeSufficient()) {
         return null;
      }

      try {
         applySettings();
         saveAcqSettings();
         ChannelTableModel model = (ChannelTableModel) channelTable_.getModel();
         if (acqEng_.isChannelsSettingEnabled() && model.duplicateChannels()) {
            JOptionPane.showMessageDialog(this, "Cannot start acquisition using the same channel twice");
            return null;
         }
         // Check for excessively long exposure times.
         ArrayList<String> badChannels = new ArrayList<String>();
         for (ChannelSpec spec : model.getChannels()) {
            if (spec.exposure > 30000) { // More than 30s
               badChannels.add(spec.config);
            }
         }
         if (badChannels.size() > 0 && getShouldCheckExposureSanity()) {
            String channelString = (badChannels.size() == 1) ?
               String.format("the %s channel", badChannels.get(0)) :
               String.format("these channels: %s", badChannels.toString());
            String message = String.format("I found unusually long exposure times for %s. Are you sure you want to run this acquisition?",
                  channelString);
            JCheckBox neverAgain = new JCheckBox("Do not ask me again.");
            int response = JOptionPane.showConfirmDialog(this,
                  new Object[] {message, neverAgain},
                  "Confirm exposure times", JOptionPane.YES_NO_OPTION);
            setShouldCheckExposureSanity(!neverAgain.isSelected());
            if (response == JOptionPane.NO_OPTION) {
               return null;
            }
         }
         return acqEng_.acquire();
      } catch (MMException e) {
         ReportingUtils.showError(e);
         return null;
      }
   }

   public Datastore runAcquisition(String acqName, String acqRoot) {
      if (acqEng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this, "Unable to start the new acquisition task: previous acquisition still in progress.");
         return null;
      }

      if (! warnIfMemoryMayNotBeSufficient()) {
         return null;
      }

      try {
         applySettings();
         ChannelTableModel model = (ChannelTableModel) channelTable_.getModel();
         if (acqEng_.isChannelsSettingEnabled() && model.duplicateChannels()) {
            JOptionPane.showMessageDialog(this, "Cannot start acquisition using the same channel twice");
            return null;
         }
         acqEng_.setDirName(acqName);
         acqEng_.setRootName(acqRoot);
         acqEng_.setSaveFiles(true);
         return acqEng_.acquire();
      } catch (MMException e) {
         ReportingUtils.showError(e);
         return null;
      }
   }

   public boolean isAcquisitionRunning() {
      return acqEng_.isAcquisitionRunning();
   }

   public static int search(int[] numbers, int key) {
      for (int index = 0; index < numbers.length; index++) {
         if (numbers[index] == key) {
            return index;
         }
      }
      return -1;
   }

   private void checkForCustomTimeIntervals() {
      if (acqEng_.customTimeIntervalsEnabled()) {
         framesSubPanelLayout_.show(framesSubPanel_, OVERRIDE_FRAMES_PANEL_NAME);
      } else {
         framesSubPanelLayout_.show(framesSubPanel_, DEFAULT_FRAMES_PANEL_NAME);
      }
   }

   public final void updateGUIContents() {
      if (disableGUItoSettings_) {
         return;
      }
      disableGUItoSettings_ = true;
      // Disable update prevents action listener loops


      // TODO: remove setChannels()
      model_.setChannels(acqEng_.getChannels());

      double intervalMs = acqEng_.getFrameIntervalMs();
      interval_.setText(numberFormat_.format(convertMsToTime(intervalMs, timeUnitCombo_.getSelectedIndex())));

      zBottom_.setText(NumberUtils.doubleToDisplayString(acqEng_.getSliceZBottomUm()));
      zTop_.setText(NumberUtils.doubleToDisplayString(acqEng_.getZTopUm()));
      zStep_.setText(NumberUtils.doubleToDisplayString(acqEng_.getSliceZStepUm()));

      boolean framesEnabled = acqEng_.isFramesSettingEnabled(); 
      framesPanel_.setSelected(framesEnabled);
      Component[] comps = framesSubPanel_.getComponents();
      for (Component c: comps)
         for (Component co: ((JPanel)c).getComponents() )
            co.setEnabled(framesEnabled);
      
      
      checkForCustomTimeIntervals();
      slicesPanel_.setSelected(acqEng_.isZSliceSettingEnabled());
      positionsPanel_.setSelected(acqEng_.isMultiPositionEnabled());
      afPanel_.setSelected(acqEng_.isAutoFocusEnabled());
      acqOrderBox_.setEnabled(positionsPanel_.isSelected() || framesPanel_.isSelected()
              || slicesPanel_.isSelected() || channelsPanel_.isSelected());

      afSkipInterval_.setEnabled(acqEng_.isAutoFocusEnabled());

      // These values need to be cached or we will loose them due to the Spinners OnChanged methods calling applySetting
      Integer numFrames = acqEng_.getNumFrames();
      Integer afSkipInterval = acqEng_.getAfSkipInterval();
      if (acqEng_.isFramesSettingEnabled()) {
         numFrames_.setValue(numFrames);
      }

      afSkipInterval_.setValue(afSkipInterval);

      enableZSliceControls(acqEng_.isZSliceSettingEnabled());
      model_.fireTableStructureChanged();

      channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());


      for (AcqOrderMode mode : acqOrderModes_) {
         mode.setEnabled(framesPanel_.isSelected(), positionsPanel_.isSelected(),
                 slicesPanel_.isSelected(), channelsPanel_.isSelected());
      }

      // add correct acquisition order options
      int selectedIndex = acqEng_.getAcqOrderMode();
      acqOrderBox_.removeAllItems();
      if (framesPanel_.isSelected() && positionsPanel_.isSelected()
              && slicesPanel_.isSelected() && channelsPanel_.isSelected()) {
         acqOrderBox_.addItem(acqOrderModes_[0]);
         acqOrderBox_.addItem(acqOrderModes_[1]);
         acqOrderBox_.addItem(acqOrderModes_[2]);
         acqOrderBox_.addItem(acqOrderModes_[3]);
      } else if (framesPanel_.isSelected() && positionsPanel_.isSelected()) {
         if (selectedIndex == 0 || selectedIndex == 2) {
            acqOrderBox_.addItem(acqOrderModes_[0]);
            acqOrderBox_.addItem(acqOrderModes_[2]);
         } else {
            acqOrderBox_.addItem(acqOrderModes_[1]);
            acqOrderBox_.addItem(acqOrderModes_[3]);
         }
      } else if (channelsPanel_.isSelected() && slicesPanel_.isSelected()) {
         if (selectedIndex == 0 || selectedIndex == 1) {
            acqOrderBox_.addItem(acqOrderModes_[0]);
            acqOrderBox_.addItem(acqOrderModes_[1]);
         } else {
            acqOrderBox_.addItem(acqOrderModes_[2]);
            acqOrderBox_.addItem(acqOrderModes_[3]);
         }
      } else {
         acqOrderBox_.addItem(acqOrderModes_[selectedIndex]);
      }

      acqOrderBox_.setSelectedItem(acqOrderModes_[acqEng_.getAcqOrderMode()]);


      zValCombo_.setSelectedIndex(zVals_);
      stackKeepShutterOpenCheckBox_.setSelected(acqEng_.isShutterOpenForStack());
      chanKeepShutterOpenCheckBox_.setSelected(acqEng_.isShutterOpenForChannels());

      channelTable_.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

      boolean selected = channelsPanel_.isSelected();
      channelTable_.setEnabled(selected);
      channelTable_.getTableHeader().setForeground(selected ? Color.black : Color.gray);

      updateSavingTypeButtons();

      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary());

      disableGUItoSettings_ = false;
   }
       
   private void updateDoubleValue(double value, JTextField field) {
       try {
           if (NumberUtils.displayStringToDouble(field.getText()) != value) {
               field.setText(NumberUtils.doubleToDisplayString(value));
           }
       } catch (ParseException e) {
           field.setText(NumberUtils.doubleToDisplayString(value));
       }
   }
       
   private void updateCheckBox(boolean setting,  CheckBoxPanel panel) {
      if (panel.isSelected() != setting) {
          panel.setSelected(setting);
      }
   }
   
   @Override      
   public void settingsChanged() {
   }
      
   private void applySettings() {
      if (disableGUItoSettings_) {
         return;
      }
      disableGUItoSettings_ = true;

      AbstractCellEditor ae = (AbstractCellEditor) channelTable_.getCellEditor();
      if (ae != null) {
         ae.stopCellEditing();
      }

      try {
         // TODO: ideally we would be able to check this value against the
         // physical resolution of the Z positioner.
         double zStep = NumberUtils.displayStringToDouble(zStep_.getText());
         acqEng_.setSlices(NumberUtils.displayStringToDouble(zBottom_.getText()), NumberUtils.displayStringToDouble(zTop_.getText()), zStep, (zVals_ != 0));
         acqEng_.enableZSliceSetting(slicesPanel_.isSelected());
         acqEng_.enableMultiPosition(positionsPanel_.isSelected());


         acqEng_.setAcqOrderMode(((AcqOrderMode) acqOrderBox_.getSelectedItem()).getID());
         acqEng_.enableChannelsSetting(channelsPanel_.isSelected());
         acqEng_.setChannels(((ChannelTableModel) channelTable_.getModel()).getChannels());
         acqEng_.enableFramesSetting(framesPanel_.isSelected());
         acqEng_.setFrames((Integer) numFrames_.getValue(),
                 convertTimeToMs(NumberUtils.displayStringToDouble(interval_.getText()), timeUnitCombo_.getSelectedIndex()));
         acqEng_.setAfSkipInterval(NumberUtils.displayStringToInt(afSkipInterval_.getValue().toString()));
         acqEng_.keepShutterOpenForChannels(chanKeepShutterOpenCheckBox_.isSelected());
         acqEng_.keepShutterOpenForStack(stackKeepShutterOpenCheckBox_.isSelected());

      } catch (ParseException p) {
         ReportingUtils.showError(p);
         // TODO: throw error
      }

      acqEng_.setSaveFiles(savePanel_.isSelected());    
      // avoid dangerous characters in the name that will be used as a directory name
      String name = nameField_.getText().replaceAll("[/\\*!':]", "-");
      acqEng_.setDirName(name);
      acqEng_.setRootName(rootField_.getText());

      // update summary

      acqEng_.setComment(commentTextArea_.getText());

      acqEng_.enableAutoFocus(afPanel_.isSelected());

      disableGUItoSettings_ = false;
      updateGUIContents();
   }

   /**
    * Save settings to application properties.
    *
    */
   private void saveSettings() {
      this.savePosition();
   }

   private double convertTimeToMs(double interval, int units) {
      if (units == 1) {
         return interval * 1000; // sec
      } else if (units == 2) {
         return interval * 60.0 * 1000.0; // min
      } else if (units == 0) {
         return interval; // ms
      }
      ReportingUtils.showError("Unknown units supplied for acquisition interval!");
      return interval;
   }

   private double convertMsToTime(double intervalMs, int units) {
      if (units == 1) {
         return intervalMs / 1000; // sec
      } else if (units == 2) {
         return intervalMs / (60.0 * 1000.0); // min
      } else if (units == 0) {
         return intervalMs; // ms
      }
      ReportingUtils.showError("Unknown units supplied for acquisition interval!");
      return intervalMs;
   }

   private void zValCalcChanged() {
      if (zValCombo_.getSelectedIndex() == 0) {
         setTopButton_.setEnabled(false);
         setBottomButton_.setEnabled(false);
      } else {
         setTopButton_.setEnabled(true);
         setBottomButton_.setEnabled(true);
      }

      if (zVals_ == zValCombo_.getSelectedIndex()) {
         return;
      }

      zVals_ = zValCombo_.getSelectedIndex();
      double zBottomUm, zTopUm;
      try {
         zBottomUm = NumberUtils.displayStringToDouble(zBottom_.getText());
         zTopUm = NumberUtils.displayStringToDouble(zTop_.getText());
      } catch (ParseException e) {
         ReportingUtils.logError(e);
         return;
      }

      double curZ = acqEng_.getCurrentZPos();

      double newTop, newBottom;
      if (zVals_ == 0) {
         // convert from absolute to relative
         newTop = zTopUm - curZ;
         newBottom = zBottomUm - curZ;
      } else {
         // convert from relative to absolute
         newTop = zTopUm + curZ;
         newBottom = zBottomUm + curZ;
      }
      zBottom_.setText(NumberUtils.doubleToDisplayString(newBottom));
      zTop_.setText(NumberUtils.doubleToDisplayString(newTop));
      applySettings();
   }

   private void showAdvancedDialog() {
      if (advancedOptionsWindow_ == null) {
         advancedOptionsWindow_ = new AdvancedOptionsDialog(acqEng_,studio_);
      }
      advancedOptionsWindow_.setVisible(true);
   }

   @SuppressWarnings("serial")
   public class ComponentTitledPanel extends JPanel {
      public ComponentTitledBorder compTitledBorder;
      public boolean borderSet_ = false;
      public Component titleComponent;

      @Override
      public void setBorder(Border border) {
         if (compTitledBorder != null && borderSet_) {
            compTitledBorder.setBorder(border);
         } else {
            super.setBorder(border);
         }
      }

      @Override
      public Border getBorder() {
         return compTitledBorder;
      }

      /**
       * HACK: when the look and feel changes, the background colors of all
       * components are supposed to get updated. For some reason, our custom
       * titled components don't receive this update, so we have to propagate
       * it manually.
       * 
       * @param c Color to use for custom title components
       */
      @Override
      public void setBackground(Color c) {
         super.setBackground(c);
         // This can get called from the super constructor, at which point
         // titleComponent won't be defined yet.
         if (titleComponent != null) {
            titleComponent.setBackground(c);
         }
      }

      public void setTitleFont(Font font) {
         titleComponent.setFont(font);
      }
   }

   @SuppressWarnings("serial")
   public class LabelPanel extends ComponentTitledPanel {
      LabelPanel(String title) {
         super();
         JLabel label = new JLabel(title);
         titleComponent = label;
         label.setOpaque(true);
         label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
         compTitledBorder = new ComponentTitledBorder(label, this,
               BorderFactory.createEtchedBorder());
         this.setBorder(compTitledBorder);
         borderSet_ = true;
      }
      
      public void writeObject(java.io.ObjectOutputStream stream) throws MMException  {
         throw new MMException("Do not serialize this class");
      }
   }

   @SuppressWarnings("serial")
   public class CheckBoxPanel extends ComponentTitledPanel {

      JCheckBox checkBox;

      CheckBoxPanel(String title) {
         super();
         titleComponent = new JCheckBox(title);
         checkBox = (JCheckBox) titleComponent;

         compTitledBorder = new ComponentTitledBorder(checkBox, this, BorderFactory.createEtchedBorder());
         this.setBorder(compTitledBorder);
         borderSet_ = true;

         final CheckBoxPanel thisPanel = this;

         checkBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               boolean enable = checkBox.isSelected();
               thisPanel.setChildrenEnabled(enable);
            }
            
            public void writeObject(java.io.ObjectOutputStream stream) throws MMException  {
               throw new MMException("Do not serialize this class");
            }
         });
      }

      public void setChildrenEnabled(boolean enabled) {
         Component comp[] = this.getComponents();
         for (Component comp1 : comp) {
            if (comp1.getClass().equals(JPanel.class)) {
               Component[] subComp = ((JPanel) comp1).getComponents();
               for (Component subComp1 : subComp) {
                  subComp1.setEnabled(enabled);
               }
            } else {
               comp1.setEnabled(enabled);
            }
         }
      }

      public boolean isSelected() {
         return checkBox.isSelected();
      }

      public void setSelected(boolean selected) {
         checkBox.setSelected(selected);
         setChildrenEnabled(selected);
      }

      public void addActionListener(ActionListener actionListener) {
         checkBox.addActionListener(actionListener);
      }

      public void removeActionListeners() {
         for (ActionListener l : checkBox.getActionListeners()) {
            checkBox.removeActionListener(l);
         }
      }
   }

   public static Double getChannelExposure(String channelGroup,
         String channel, double defaultVal) {
      return DefaultUserProfile.getInstance().getDouble(
            AcqControlDlg.class, "Exposure_" + channelGroup + "_" + channel,
            defaultVal);
   }

   public static void setChannelExposure(String channelGroup,
         String channel, double exposure) {
      DefaultUserProfile.getInstance().setDouble(AcqControlDlg.class,
            "Exposure_" + channelGroup + "_" + channel, exposure);
   }

   public static Integer getChannelColor(String channelGroup,
         String channel, int defaultVal) {
      return ChannelSettings.getColorForChannel(channel, channelGroup,
            new Color(defaultVal)).getRGB();
   }

   public static void setChannelColor(String channelGroup, String channel,
         int color) {
      // TODO: this is kind of an ugly way to do this.
      ChannelSettings settings = ChannelSettings.loadSettings(channel,
            channelGroup, Color.WHITE, 0, -1, true);
      settings = new ChannelSettings(channel, channelGroup,
            new Color(color), settings.getHistogramMin(),
            settings.getHistogramMax(), settings.getShouldAutoscale());
      settings.saveToProfile();
   }

   public static boolean getShouldSyncExposure() {
      return DefaultUserProfile.getInstance().getBoolean(AcqControlDlg.class,
            SHOULD_SYNC_EXPOSURE, false);
   }

   public static void setShouldSyncExposure(boolean shouldSync) {
      DefaultUserProfile.getInstance().setBoolean(AcqControlDlg.class,
            SHOULD_SYNC_EXPOSURE, shouldSync);
   }

   public static boolean getShouldHideMDADisplay() {
      return DefaultUserProfile.getInstance().getBoolean(AcqControlDlg.class,
            SHOULD_HIDE_DISPLAY, false);
   }

   public static void setShouldHideMDADisplay(boolean shouldHide) {
      DefaultUserProfile.getInstance().setBoolean(AcqControlDlg.class,
            SHOULD_HIDE_DISPLAY, shouldHide);
   }

   public static boolean getShouldCheckExposureSanity() {
      return DefaultUserProfile.getInstance().getBoolean(AcqControlDlg.class,
            SHOULD_CHECK_EXPOSURE_SANITY, true);
   }

   public static void setShouldCheckExposureSanity(boolean shouldCheck) {
      DefaultUserProfile.getInstance().setBoolean(AcqControlDlg.class,
            SHOULD_CHECK_EXPOSURE_SANITY, shouldCheck);
   }
}
