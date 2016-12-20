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

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.display.internal.RememberedChannelSettings;
import org.micromanager.events.ChannelExposureEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.internal.ChannelGroupEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.ColorEditor;
import org.micromanager.internal.utils.ColorRenderer;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TooltipTextMaker;

/**
 * Time-lapse, channel and z-stack acquisition setup dialog.
 * This dialog specifies all parameters for the MDA acquisition. 
 *
 */
public final class AcqControlDlg extends MMFrame implements PropertyChangeListener, 
        AcqSettingsListener { 

   private static final long serialVersionUID = 1L;
   private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 10);
   private static final String RELATIVE_Z = "Relative Z";
   private static final String ABSOLUTE_Z = "Absolute Z";
   private static final String SHOULD_SYNC_EXPOSURE = "should sync exposure times between main window and Acquire dialog";
   private static final String SHOULD_HIDE_DISPLAY = "should hide image display windows for multi-dimensional acquisitions";
   private static final String SAVE_MODE = "default save mode";
   private static final String SHOULD_CHECK_EXPOSURE_SANITY = "whether to prompt the user if their exposure times seem excessively long";
   private static final String BUTTON_SIZE = "width 80!, height 22!";
   private static final String PANEL_CONSTRAINT = "fillx, gap 2, insets 2";

   protected JButton listButton_;
   private JSpinner afSkipInterval_;
   private JComboBox acqOrderBox_;
   private JTextArea acquisitionOrderText_;
   private JComboBox channelGroupCombo_;
   private JTextArea commentTextArea_;
   private JComboBox zValCombo_;
   private JTextField nameField_;
   private JTextField rootField_;
   private JTextArea summaryTextArea_;
   private JComboBox timeUnitCombo_;
   private JFormattedTextField interval_;
   private JFormattedTextField zStep_;
   private JFormattedTextField zTop_;
   private JFormattedTextField zBottom_;
   private AcquisitionWrapperEngine acqEng_;
   private JScrollPane channelTablePane_;
   private JTable channelTable_;
   private JSpinner numFrames_;
   private ChannelTableModel model_;
   private File acqFile_;
   private String acqDir_;
   private int zVals_ = 0;
   private JButton acquireButton_;
   private JButton setBottomButton_;
   private JButton setTopButton_;
   private MMStudio studio_;
   private NumberFormat numberFormat_;
   private JLabel namePrefixLabel_;
   private JLabel saveTypeLabel_;
   private JRadioButton singleButton_;
   private JRadioButton multiButton_;
   private JLabel rootLabel_;
   private JButton browseRootButton_;
   private JCheckBox stackKeepShutterOpenCheckBox_;
   private JCheckBox chanKeepShutterOpenCheckBox_;
   private AcqOrderMode[] acqOrderModes_;
   private CustomTimesDialog customTimesWindow;
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
   public static final FileType ACQ_SETTINGS_FILE = new FileType("ACQ_SETTINGS_FILE", "Acquisition settings",
           System.getProperty("user.home") + "/AcqSettings.txt",
           true, "txt");
   private int columnWidth_[];
   private int columnOrder_[];
   private CheckBoxPanel framesPanel_;
   private JPanel defaultTimesPanel_;
   private JPanel customTimesPanel_;
   private CheckBoxPanel channelsPanel_;
   private CheckBoxPanel slicesPanel_;
   protected CheckBoxPanel positionsPanel_;
   private JPanel acquisitionOrderPanel_;
   private CheckBoxPanel afPanel_;
   private JPanel summaryPanel_;
   private CheckBoxPanel savePanel_;
   private ComponentTitledPanel commentsPanel_;
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

   private CheckBoxPanel createCheckBoxPanel(String text) {
      CheckBoxPanel thePanel = new CheckBoxPanel(text);
      setupPanel(thePanel);
      return thePanel;
   }

   private LabelPanel createLabelPanel(String text) {
      LabelPanel thePanel = new LabelPanel(text);
      setupPanel(thePanel);
      return thePanel;
   }

   private void setupPanel(ComponentTitledPanel thePanel) {
      thePanel.setTitleFont(new Font("Dialog", Font.BOLD, 12));
      thePanel.setLayout(null);
   }

   /**
    * Create the panel for showing the timepoints settings. This one can have
    * its contents overridden by the custom time intervals system, in which
    * case its normal contents get hidden.
    */
   private JPanel createTimePoints() {
      framesPanel_ = createCheckBoxPanel("Time Points");
      framesPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT + ", hidemode 3",
               "[grow, fill]", "[grow, fill]"));

      defaultTimesPanel_ = new JPanel(
            new MigLayout("fill, gap 2, insets 0",
               "push[][][]push", "[][][]"));
      framesPanel_.add(defaultTimesPanel_, "grow");

      customTimesPanel_ = new JPanel(
            new MigLayout("fill, gap 0, insets 0"));
      customTimesPanel_.setVisible(false);
      framesPanel_.add(customTimesPanel_, "grow");

      final JLabel numberLabel = new JLabel("Count:");
      numberLabel.setFont(DEFAULT_FONT);

      defaultTimesPanel_.add(numberLabel, "alignx label");

      SpinnerModel sModel = new SpinnerNumberModel(1, 1, null, 1);

      numFrames_ = new JSpinner(sModel);
      JTextField field = ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField();
      field.setColumns(5);
      ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField().setFont(DEFAULT_FONT);
      numFrames_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            applySettings();
         }
      });

      defaultTimesPanel_.add(numFrames_, "wrap");

      final JLabel intervalLabel = new JLabel("Interval:");
      intervalLabel.setFont(DEFAULT_FONT);
      intervalLabel.setToolTipText(
            "Interval between successive time points.  Setting an interval " +
            "less than the exposure time will cause micromanager to acquire a 'burst' of images as fast as possible");
      defaultTimesPanel_.add(intervalLabel, "alignx label");

      interval_ = new JFormattedTextField(numberFormat_);
      interval_.setColumns(5);
      interval_.setFont(DEFAULT_FONT);
      interval_.setValue(1.0);
      interval_.addPropertyChangeListener("value", this);
      defaultTimesPanel_.add(interval_);

      timeUnitCombo_ = new JComboBox();
      timeUnitCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
      timeUnitCombo_.setFont(DEFAULT_FONT);
      // We shove this thing to the left a bit so that it takes up the same
      // vertical space as the spinner for the number of timepoints.
      defaultTimesPanel_.add(timeUnitCombo_, "pad 0 -15 0 0, wrap");

      JButton advancedButton = new JButton("Advanced...");
      advancedButton.setFont(DEFAULT_FONT);
      advancedButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            showCustomTimesDialog();
            updateGUIContents();
         }
      });

      defaultTimesPanel_.add(advancedButton, "skip, span 2, align left");

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
      disableCustomIntervalsButton.setFont(DEFAULT_FONT);

      customTimesPanel_.add(overrideLabel, "alignx center, wrap");
      customTimesPanel_.add(disableCustomIntervalsButton, "alignx center");

      framesPanel_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            applySettings();
         }
      });
      return framesPanel_;
   }

   private JPanel createMultiPositions() {
      positionsPanel_ = createCheckBoxPanel("Multiple Positions (XY)");
      positionsPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT,
               "[grow]", "[grow, fill]"));
      listButton_ = new JButton("Edit Position List...");
      listButton_.setToolTipText("Open XY list dialog");
      listButton_.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/application_view_list.png"));
      listButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      listButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.app().showPositionList();
         }
      });

      // Not sure why 'span' is needed to prevent second column from appearing
      // (interaction with CheckBoxPanel layout??)
      positionsPanel_.add(listButton_, "span, alignx center");
      return positionsPanel_;
   }

   private JPanel createZStacks() {
      slicesPanel_ = createCheckBoxPanel("Z-Stacks (Slices)");
      slicesPanel_.setLayout(new MigLayout("fillx, gap 2, insets 2",
            "push[][][][]push", ""));

      String labelConstraint = "pushx 100, alignx label";

      // Simplify inserting unit labels slightly.
      Runnable addUnits = new Runnable() {
         @Override
         public void run() {
            JLabel label = new JLabel("\u00b5m");
            label.setFont(DEFAULT_FONT);
            slicesPanel_.add(label, "gapleft 0, gapright 4");
         }
      };

      final JLabel zbottomLabel = new JLabel("Start Z:");
      zbottomLabel.setFont(DEFAULT_FONT);
      slicesPanel_.add(zbottomLabel, labelConstraint);

      zBottom_ = new JFormattedTextField(numberFormat_);
      zBottom_.setColumns(5);
      zBottom_.setFont(DEFAULT_FONT);
      zBottom_.setValue(1.0);
      zBottom_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zBottom_);
      addUnits.run();

      // Slightly smaller than BUTTON_SIZE
      String buttonSize = "width 50!, height 20!";

      setBottomButton_ = new JButton("Set");
      setBottomButton_.setMargin(new Insets(-5, -5, -5, -5));
      setBottomButton_.setFont(new Font("", Font.PLAIN, 10));
      setBottomButton_.setToolTipText("Set value as microscope's current Z position");
      setBottomButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setBottomPosition();
         }
      });
      slicesPanel_.add(setBottomButton_, buttonSize + ", pushx 100, wrap");

      final JLabel ztopLabel = new JLabel("End Z:");
      ztopLabel.setFont(DEFAULT_FONT);
      slicesPanel_.add(ztopLabel, labelConstraint);

      zTop_ = new JFormattedTextField(numberFormat_);
      zTop_.setColumns(5);
      zTop_.setFont(DEFAULT_FONT);
      zTop_.setValue(1.0);
      zTop_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zTop_);
      addUnits.run();

      setTopButton_ = new JButton("Set");
      setTopButton_.setMargin(new Insets(-5, -5, -5, -5));
      setTopButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      setTopButton_.setToolTipText("Set value as microscope's current Z position");
      setTopButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setTopPosition();
         }
      });
      slicesPanel_.add(setTopButton_, buttonSize + ", pushx 100, wrap");

      final JLabel zstepLabel = new JLabel("Step size:");
      zstepLabel.setFont(DEFAULT_FONT);
      slicesPanel_.add(zstepLabel, labelConstraint);

      zStep_ = new JFormattedTextField(numberFormat_);
      zStep_.setColumns(5);
      zStep_.setFont(DEFAULT_FONT);
      zStep_.setValue(1.0);
      zStep_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zStep_);
      addUnits.run();
      slicesPanel_.add(new JLabel(""), "wrap");

      zValCombo_ = new JComboBox(new String[] {RELATIVE_Z, ABSOLUTE_Z});
      zValCombo_.setFont(DEFAULT_FONT);
      zValCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            zValCalcChanged();
         }
      });
      slicesPanel_.add(zValCombo_,
            "skip 1, spanx, gaptop 4, gapbottom 0, alignx left, wrap");

      stackKeepShutterOpenCheckBox_ = new JCheckBox("Keep shutter open");
      stackKeepShutterOpenCheckBox_.setFont(DEFAULT_FONT);
      stackKeepShutterOpenCheckBox_.setSelected(false);
      stackKeepShutterOpenCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            applySettings();
         }
      });
      slicesPanel_.add(stackKeepShutterOpenCheckBox_,
            "skip 1, spanx, gaptop 0, alignx left");

      slicesPanel_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            // enable disable all related contrtols
            applySettings();
         }
      });
      return slicesPanel_;
   }

   private JPanel createAcquisitionOrder() {
      acquisitionOrderPanel_ = createLabelPanel("Acquisition Order");
      acquisitionOrderPanel_.setLayout(
            new MigLayout(PANEL_CONSTRAINT + ", flowy"));
      acqOrderBox_ = new JComboBox();
      acqOrderBox_.setFont(new Font("", Font.PLAIN, 10));
      acquisitionOrderPanel_.add(acqOrderBox_, "alignx center");
      acquisitionOrderText_ = new JTextArea(3, 25);
      acquisitionOrderText_.setEditable(false);
      acquisitionOrderText_.setFont(new Font("", Font.PLAIN, 9));
      acquisitionOrderPanel_.add(acquisitionOrderText_, "alignx center");

      acqOrderModes_ = new AcqOrderMode[4];
      acqOrderModes_[0] = new AcqOrderMode(AcqOrderMode.TIME_POS_SLICE_CHANNEL);
      acqOrderModes_[1] = new AcqOrderMode(AcqOrderMode.TIME_POS_CHANNEL_SLICE);
      acqOrderModes_[2] = new AcqOrderMode(AcqOrderMode.POS_TIME_SLICE_CHANNEL);
      acqOrderModes_[3] = new AcqOrderMode(AcqOrderMode.POS_TIME_CHANNEL_SLICE);
      acqOrderBox_.addItem(acqOrderModes_[0]);
      acqOrderBox_.addItem(acqOrderModes_[1]);
      acqOrderBox_.addItem(acqOrderModes_[2]);
      acqOrderBox_.addItem(acqOrderModes_[3]);
      return acquisitionOrderPanel_;
   }

   private JPanel createAutoFocus() {
      afPanel_ = createCheckBoxPanel("Autofocus");
      afPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT));

      JButton afButton = new JButton("Options...");
      afButton.setToolTipText("Set autofocus options");
      afButton.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/wrench_orange.png"));
      afButton.setMargin(new Insets(2, 5, 2, 5));
      afButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      afButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            afOptions();
         }
      });
      afPanel_.add(afButton, "alignx center, wrap");

      final JLabel afSkipFrame1 = new JLabel("Skip frame(s):");
      afSkipFrame1.setFont(new Font("Dialog", Font.PLAIN, 10));
      afSkipFrame1.setToolTipText("How many frames to skip between running autofocus. Autofocus is always run at new stage positions");

      afPanel_.add(afSkipFrame1, "split, spanx, alignx center");

      afSkipInterval_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
      JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) afSkipInterval_.getEditor();
      editor.setFont(DEFAULT_FONT);
      editor.getTextField().setColumns(3);
      afSkipInterval_.setValue(acqEng_.getAfSkipInterval());
      afSkipInterval_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            applySettings();
            afSkipInterval_.setValue(acqEng_.getAfSkipInterval());
         }
      });
      afPanel_.add(afSkipInterval_);

      afPanel_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
         }
      });
      return afPanel_;
   }

   private JPanel createSummary() {
      summaryPanel_ = createLabelPanel("Summary");
      summaryPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT + ", filly, insets 4 8 4 8"));
      summaryTextArea_ = new JTextArea(8, 25);
      summaryTextArea_.setFont(new Font("Arial", Font.PLAIN, 11));
      summaryTextArea_.setEditable(false);
      summaryTextArea_.setOpaque(false);
      summaryPanel_.add(summaryTextArea_, "grow");
      return summaryPanel_;
   }

   private JPanel createChannelsPanel() {
      channelsPanel_ = createCheckBoxPanel("Channels");
      channelsPanel_.setLayout(new MigLayout("fill, gap 2, insets 2",
               "[grow][]", "[][grow]"));

      final JLabel channelsLabel = new JLabel("Channel group:");
      channelsLabel.setFont(DEFAULT_FONT);
      channelsPanel_.add(channelsLabel, "split, alignx label");

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
                  studio_.getAutofocusManager().refresh();
               }
            } else {
               updateGroupsCombo();
            }
         }
      });
      channelsPanel_.add(channelGroupCombo_, "alignx left");

      chanKeepShutterOpenCheckBox_ = new JCheckBox("Keep shutter open");
      chanKeepShutterOpenCheckBox_.setFont(DEFAULT_FONT);
      chanKeepShutterOpenCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            applySettings();
         }
      });
      chanKeepShutterOpenCheckBox_.setSelected(false);
      channelsPanel_.add(chanKeepShutterOpenCheckBox_, "gapleft push, wrap");

      channelTablePane_ = new JScrollPane();
      channelTablePane_.setFont(DEFAULT_FONT);
      channelsPanel_.add(channelTablePane_, "height 60:60:, grow");

      // Slightly smaller than BUTTON_SIZE, and the gap matches the insets of
      // the panel.
      String buttonConstraint = "width 60!, height 20!, gapleft 2";

      final JButton addButton = new JButton("New");
      addButton.setFont(DEFAULT_FONT);
      addButton.setMargin(new Insets(0, 0, 0, 0));
      addButton.setToolTipText("Add an additional channel");
      addButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            applySettings();
            model_.addNewChannel();
            model_.fireTableStructureChanged();
         }
      });
      channelsPanel_.add(addButton, buttonConstraint + ", flowy, split, aligny top");

      final JButton removeButton = new JButton("Remove");
      removeButton.setFont(DEFAULT_FONT);
      removeButton.setMargin(new Insets(-5, -5, -5, -5));
      removeButton.setToolTipText("Remove currently selected channel");
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
      channelsPanel_.add(removeButton, buttonConstraint);

      final JButton upButton = new JButton("Up");
      upButton.setFont(DEFAULT_FONT);
      upButton.setMargin(new Insets(0, 0, 0, 0));
      upButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Move currently selected channel up (Channels higher on list are acquired first)"));
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
      channelsPanel_.add(upButton, buttonConstraint);

      final JButton downButton = new JButton("Down");
      downButton.setFont(DEFAULT_FONT);
      downButton.setMargin(new Insets(0, 0, 0, 0));
      downButton.setText("Down");
      downButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Move currently selected channel down (Channels lower on list are acquired later)"));
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
      channelsPanel_.add(downButton, buttonConstraint);

      channelsPanel_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            applySettings();
         }
      });
      return channelsPanel_;
   }

   private JComponent createCloseButton() {
      final JButton closeButton = new JButton("Close");
      closeButton.setFont(DEFAULT_FONT);
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            saveSettings();
            saveAcqSettings();
            AcqControlDlg.this.dispose();
            studio_.app().makeActive();
         }
      });
      return closeButton;
   }

   private JPanel createRunButtons() {
      JPanel result = new JPanel(new MigLayout("flowy, insets 0, gapx 0, gapy 2"));
      acquireButton_ = new JButton("Acquire!");
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
      result.add(acquireButton_, BUTTON_SIZE);

      final JButton stopButton = new JButton("Stop");
      stopButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            acqEng_.abortRequest();
         }
      });
      stopButton.setFont(new Font("Arial", Font.BOLD, 12));
      result.add(stopButton, BUTTON_SIZE);
      return result;
   }

   private JPanel createSaveButtons() {
      JPanel result = new JPanel(new MigLayout("flowy, insets 0, gapx 0, gapy 2"));
      final JButton loadButton = new JButton("Load...");
      loadButton.setToolTipText("Load acquisition settings");
      loadButton.setFont(DEFAULT_FONT);
      loadButton.setMargin(new Insets(-5, -5, -5, -5));
      loadButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            loadAcqSettingsFromFile();
         }
      });
      result.add(loadButton, BUTTON_SIZE);

      final JButton saveAsButton = new JButton("Save as...");
      saveAsButton.setToolTipText("Save current acquisition settings as");
      saveAsButton.setFont(DEFAULT_FONT);
      saveAsButton.setMargin(new Insets(-5, -5, -5, -5));
      saveAsButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            saveAsAcqSettingsToFile();
         }
      });
      result.add(saveAsButton, BUTTON_SIZE);
      return result;
   }

   private JPanel createSavePanel() {
      savePanel_ = createCheckBoxPanel("Save Images");
      savePanel_.setLayout(new MigLayout(PANEL_CONSTRAINT,
               "[][grow, fill][]", "[][][]"));

      rootLabel_ = new JLabel("Directory root:");
      rootLabel_.setFont(DEFAULT_FONT);
      savePanel_.add(rootLabel_, "alignx label");

      rootField_ = new JTextField();
      rootField_.setFont(DEFAULT_FONT);
      savePanel_.add(rootField_);

      browseRootButton_ = new JButton("...");
      browseRootButton_.setToolTipText("Browse");
      browseRootButton_.setMargin(new Insets(2, 5, 2, 5));
      browseRootButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      browseRootButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setRootDirectory();
         }
      });
      savePanel_.add(browseRootButton_, "wrap");

      namePrefixLabel_ = new JLabel("Name prefix:");
      namePrefixLabel_.setFont(DEFAULT_FONT);
      savePanel_.add(namePrefixLabel_, "alignx label");

      nameField_ = new JTextField();
      nameField_.setFont(DEFAULT_FONT);
      savePanel_.add(nameField_, "wrap");

      saveTypeLabel_ = new JLabel("Saving format:");
      saveTypeLabel_.setFont(DEFAULT_FONT);
      savePanel_.add(saveTypeLabel_, "alignx label");

      singleButton_ = new JRadioButton("Separate image files");
      singleButton_.setFont(DEFAULT_FONT);
      singleButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DefaultDatastore.setPreferredSaveMode(
               Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
         }
      });
      savePanel_.add(singleButton_, "spanx, split");

      multiButton_ = new JRadioButton("Image stack file");
      multiButton_.setFont(DEFAULT_FONT);
      multiButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DefaultDatastore.setPreferredSaveMode(
               Datastore.SaveMode.MULTIPAGE_TIFF);
         }
      });
      savePanel_.add(multiButton_, "gapafter push");

      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(singleButton_);
      buttonGroup.add(multiButton_);
      updateSavingTypeButtons();

      savePanel_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            applySettings();
         }
      });
      return savePanel_;
   }

   private JPanel createCommentsPanel() {
      commentsPanel_ = createLabelPanel("Acquisition Comments");
      commentsPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT,
               "[grow, fill]", "[]"));

      commentTextArea_ = new JTextArea();
      commentTextArea_.setRows(4);
      commentTextArea_.setFont(new Font("", Font.PLAIN, 10));
      commentTextArea_.setToolTipText("Comment for the acquisition to be run");
      commentTextArea_.setWrapStyleWord(true);
      commentTextArea_.setLineWrap(true);

      JScrollPane commentScrollPane = new JScrollPane();
      commentScrollPane.setBorder(BorderFactory.createCompoundBorder(
               BorderFactory.createLoweredBevelBorder(),
               BorderFactory.createEtchedBorder()));
      commentScrollPane.setViewportView(commentTextArea_);

      commentsPanel_.add(commentScrollPane, "wmin 0, height pref!, span");
      return commentsPanel_;
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
   public AcqControlDlg(AcquisitionWrapperEngine acqEng, MMStudio gui) {
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

      setTitle("Multi-Dimensional Acquisition");
      setLayout(new MigLayout("fill, flowy, gap 2, insets 6",
               "[grow, fill]",
               "[][grow][][]"));

      // Contains timepoints, multiple positions, and Z-slices; acquisition
      // order, autofocus, and summary; control buttons, in three columns.
      JPanel topPanel = new JPanel(new MigLayout(
               "fill, insets 0",
               "[grow, fill]6[grow, fill]6[]",
               "[grow, fill]"));

      JPanel topLeftPanel = new JPanel(new MigLayout(
               "fill, flowy, insets 0",
               "[grow, fill]",
               "[]push[]push[]"));
      JPanel topMiddlePanel = new JPanel(new MigLayout(
               "fill, flowy, insets 0",
               "[grow, fill]",
               "[]push[]push[]"));
      JPanel topRightPanel = new JPanel(new MigLayout(
               "flowy, insets 0",
               "[]",
               "10[]10[]10[]push"));

      topLeftPanel.add(createTimePoints());
      topLeftPanel.add(createMultiPositions());
      topLeftPanel.add(createZStacks());

      topMiddlePanel.add(createAcquisitionOrder());
      topMiddlePanel.add(createAutoFocus());
      topMiddlePanel.add(createSummary());

      topRightPanel.add(createCloseButton(), BUTTON_SIZE);
      topRightPanel.add(createRunButtons());
      topRightPanel.add(createSaveButtons());

      topPanel.add(topLeftPanel);
      topPanel.add(topMiddlePanel);
      topPanel.add(topRightPanel);

      add(topPanel, "grow");
      add(createChannelsPanel(), "grow");
      add(createSavePanel(), "growx");
      add(createCommentsPanel(), "growx");


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


      // load acquisition settings
      loadAcqSettings();

      // create the table of channels
      createChannelTable();

      // update summary
      updateGUIContents();

      // update settings in the acq engine
      applySettings();

      createToolTips();

      pack();
      Dimension size = getPreferredSize();
      size.height += 10; // Compensate for inaccurate size given by Apple Java 6
      setMinimumSize(size);
      this.loadAndRestorePosition(100, 100, size.width, size.height);

      studio_.events().registerForEvents(this);
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
            storeChannelExposure(acqEng_.getChannelGroup(), channel, exposure);
            model_.setChannelExposureTime(channelGroup, channel, exposure);
         }
      }
   }

   @Subscribe
   public void onChannelExposure(ChannelExposureEvent event) {
      String channel = event.getChannel();
      if (!channel.equals("")) {
         String channelGroup = event.getChannelGroup();
         double exposure = event.getNewExposureTime();
         storeChannelExposure(channelGroup, channel, exposure);
         if (getShouldSyncExposure()) {
            setChannelExposureTime(channelGroup, channel, exposure);
         }
      }
   }

   @Subscribe
   public void onGUIRefresh(GUIRefreshEvent event) {
      // The current active channel may have changed, necessitating a refresh
      // of the main exposure time.
      if (getShouldSyncExposure()) {
         try {
            String channelGroup = studio_.core().getChannelGroup();
            String channel = studio_.core().getCurrentConfig(channelGroup);
            double exposure = getChannelExposureTime(
                  channelGroup, channel, 10.0);
            studio_.app().setChannelExposureTime(channelGroup, channel,
                  exposure);
         }
         catch (Exception e) {
            studio_.logs().logError(e, "Error getting channel exposure time");
         }
      }
   }

   @Subscribe
   public void onChannelGroup(ChannelGroupEvent event) {
      updateGroupsCombo();
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
      studio_.app().showAutofocusDialog();
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
            studio_.app().makeActive();
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
      defaultTimesPanel_.setVisible(!framesEnabled);
      customTimesPanel_.setVisible(framesEnabled);
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
         int r = profile.getInt(this.getClass(), CHANNEL_COLOR_R_PREFIX + i, defaultChannel.color.getRed());
         int g = profile.getInt(this.getClass(), CHANNEL_COLOR_G_PREFIX + i, defaultChannel.color.getGreen());
         int b = profile.getInt(this.getClass(), CHANNEL_COLOR_B_PREFIX + i, defaultChannel.color.getBlue());
         int skip = profile.getInt(this.getClass(), CHANNEL_SKIP_PREFIX + i, defaultChannel.skipFactorFrame);
         Color c = new Color(r, g, b);
         acqEng_.addChannel(name, exp, doZStack, zOffset, skip, c, use);
      }
      acqEng_.setShouldDisplayImages(!getShouldHideMDADisplay());

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
              FileDialogs.MM_DATA_SET);
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
         }
         catch (IOException ex) {
            ReportingUtils.showError(ex, "Failed to load Acquisition setting file");
         }
      }
   }

   public void loadAcqSettingsFromFile(String path) throws IOException {
      acqFile_ = new File(path);
      final SequenceSettings settings = studio_.acquisitions().loadSequenceSettings(path);
      try {
         GUIUtils.invokeAndWait(new Runnable() {
            @Override
            public void run() {
               acqEng_.setSequenceSettings(settings);
               updateGUIContents();
               acqDir_ = acqFile_.getParent();
               if (acqDir_ != null) {
                  studio_.profile().setString(
                     this.getClass(), ACQ_FILE_DIR, acqDir_);
               }
            }
         });
      }
      catch (InterruptedException e) {
         ReportingUtils.logError(e, "Interrupted while updating GUI");
      }
      catch (InvocationTargetException e) {
         ReportingUtils.logError(e, "Error updating GUI");
      }
   }

   protected boolean saveAsAcqSettingsToFile() {
      saveAcqSettings();
      File file = FileDialogs.save(this, "Save the acquisition settings file", ACQ_SETTINGS_FILE);
      if (file != null) {
         try {
            SequenceSettings settings = acqEng_.getSequenceSettings();
            studio_.acquisitions().saveSequenceSettings(settings,
                  file.getAbsolutePath());
         }
         catch (IOException e) {
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
         numPositions = Math.max(1, studio_.positions().getPositionList().getNumberOfPositions());
      } catch (Exception ex) {
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
         return answer == JOptionPane.YES_OPTION;
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
            if (response != JOptionPane.YES_OPTION) {
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

   public static int search(int[] numbers, int key) {
      for (int index = 0; index < numbers.length; index++) {
         if (numbers[index] == key) {
            return index;
         }
      }
      return -1;
   }

   private void checkForCustomTimeIntervals() {
      boolean isCustom = acqEng_.customTimeIntervalsEnabled();
      defaultTimesPanel_.setVisible(!isCustom);
      customTimesPanel_.setVisible(isCustom);
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
      defaultTimesPanel_.setVisible(!framesEnabled);
      customTimesPanel_.setVisible(framesEnabled);

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

      acqEng_.setShouldDisplayImages(!getShouldHideMDADisplay());

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
      final boolean isEnabled = ((String) zValCombo_.getSelectedItem()).equals(ABSOLUTE_Z);
      // HACK: push this to a later call; even though this method should only
      // be called from the EDT, for some reason if we do this action
      // immediately, then the buttons don't visually become disabled.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            setTopButton_.setEnabled(isEnabled);
            setBottomButton_.setEnabled(isEnabled);
         }
      });

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

   private void showCustomTimesDialog() {
      if (customTimesWindow == null) {
         customTimesWindow = new CustomTimesDialog(acqEng_,studio_);
      }
      customTimesWindow.setVisible(true);
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

   public static void storeChannelExposure(String channelGroup,
         String channel, double exposure) {
      DefaultUserProfile.getInstance().setDouble(AcqControlDlg.class,
            "Exposure_" + channelGroup + "_" + channel, exposure);
   }

   public static Integer getChannelColor(String channelGroup,
         String channel, int defaultVal) {
      return RememberedChannelSettings.getColorForChannel(channel,
            channelGroup, new Color(defaultVal)).getRGB();
   }

   public static void setChannelColor(String channelGroup, String channel,
         int color) {
      // TODO: this is kind of an ugly way to do this.
      RememberedChannelSettings settings = RememberedChannelSettings.loadSettings(
            channel, channelGroup, Color.WHITE,
            new Integer[] {0}, new Integer[] {-1}, true);
      settings = new RememberedChannelSettings(channel, channelGroup,
            new Color(color), settings.getHistogramMins(),
            settings.getHistogramMaxes(), settings.getShouldAutoscale());
      settings.saveToProfile();
   }

   public static boolean getShouldSyncExposure() {
      return DefaultUserProfile.getInstance().getBoolean(AcqControlDlg.class,
            SHOULD_SYNC_EXPOSURE, true);
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
