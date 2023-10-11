/**
 * MultiMDAFrame.java
 *
 * <p>Nico Stuurman, copyright Altos Labs 2023
 *
 * <p>LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.acquisition.internal.acqengjcompat.multimda;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.acqengjcompat.multimda.acqengj.MultiAcqEngJAdapter;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * Draws the UI for the Multi-MDA plugin.
 */
public class MultiMDAFrame extends JFrame {

   private final Studio studio_;
   private final JPanel acqPanel_;
   private final List<MDASettingData> acqs_ = new ArrayList<>();
   private final List<JLabel> acqLabels_ = new ArrayList<>();
   private final List<JLabel> acqExplanations_ = new ArrayList<>();
   private final List<JComboBox<String>> presetCombos_ = new ArrayList<>();
   private static final String USE_TIME_POINTS = "UseTimePoints";
   private static final String USE_AUTOFOCUS = "UseAutofocus";
   private static final String USE_PRESET = "UsePreset";
   private static final String PRESET_GROUP = "PresetGroup";
   private static final String NR_ACQ_SETTINGS = "NumberOfSettings";
   private static final String ACQ_PATHS = "AcquisitionPaths";
   private static final String POSITION_LIST_PATHS = "PositionListPaths";
   private final JSpinner nrSpinner_;
   private final CheckBoxPanel framesPanel_;
   private final CheckBoxPanel autoFocusPanel_;
   private final CheckBoxPanel presetPanel_;
   private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 10);
   private JSpinner numFrames_;
   private JFormattedTextField interval_;
   private JComboBox<String> timeUnitCombo_;
   private JSpinner afSkipInterval_;
   protected static final FileDialogs.FileType POSITION_LIST_FILE =
           new FileDialogs.FileType("POSITION_LIST_FILE", "Position list file",
                   System.getProperty("user.home") + "/PositionList.pos",
                   true, "pos");

   public static class JLabelC extends JLabel {

      public JLabelC() {
         super();
         this.setFont(DEFAULT_FONT);
      }

      public JLabelC(String text) {
         super(text);
         this.setFont(DEFAULT_FONT);
      }
   }

   /**
    * Constructor of the plugin, draws the UI and restores settings from profile.
    *
    * @param studio Always present Studio instance
    */
   public MultiMDAFrame(Studio studio) {
      super("Multi-MDA");
      studio_ = studio;

      super.setLayout(new MigLayout("fill, insets 2, gap 10, flowx"));
      acqPanel_ = new JPanel();
      acqPanel_.setLayout(new MigLayout("fill, insets 4, gap 8, flowx"));

      JLabel title = new JLabel("Use different MDA settings at different positions");
      title.setFont(new Font("Arial", Font.BOLD, 16));
      super.add(title, "span, alignx center, wrap");
      super.add(new JSeparator(), "span, growx, wrap");

      // create time point panel
      framesPanel_ = createTimePoints();
      super.add(framesPanel_, "span, split 3, gapx 30, align left");
      autoFocusPanel_ = createAutoFocus();
      super.add(autoFocusPanel_, "gapx 30");
      presetPanel_ = createPresetPanel();
      super.add(presetPanel_, "gapx 30, wrap");

      super.add(new JLabelC("NUmber of different settings"), "split 2, gap 10");
      nrSpinner_ = new JSpinner();
      nrSpinner_.setFont(DEFAULT_FONT);
      nrSpinner_.setValue(0);
      nrSpinner_.addChangeListener(e -> {
         int val = adjustNrSettings((int) nrSpinner_.getValue());
         nrSpinner_.setValue(val);
         super.pack();
      });
      super.add(nrSpinner_, "gapy 20, wrap");

      super.add(acqPanel_, "gapy 10, wrap");

      // Reload settings from disk, it would be nicer to auto-update whenever a file changes,
      // but that needs monitoring the file...
      JButton refreshButton = new JButton("Reload Settings");
      refreshButton.addActionListener(e -> {
         for (int i = 0; i < acqs_.size(); i++) {
            File f = acqs_.get(i).getAcqSettingFile();
            SequenceSettings seqSb;
            try {
               seqSb = studio_.acquisitions().loadSequenceSettings(f.getPath());
            } catch (IOException ex) {
               studio_.logs().logError(ex, "Failed to load Acquisition setting file");
               continue;
            }
            if (seqSb != null) {
               acqs_.get(i).setAcqSettings(f, seqSb);
            }
            File positionListFile = acqs_.get(i).getPositionListFile();
            if (positionListFile != null) {
               acqs_.get(i).setPositionListFile(positionListFile);
            }
            acqExplanations_.get(i).setText(oneLineSummary(acqs_.get(i)));
         }
         super.pack();
      });
      super.add(refreshButton, "span, split 3, align left, gapy 20");
      super.add(new JLabel(""), "growx");

      // Run an acquisition using the current MDA parameters.
      JButton acquireButton = new JButton("Run Multi MDA");
      acquireButton.addActionListener(e -> {
         // All GUI event handlers are invoked on the EDT (Event Dispatch
         // Thread). Acquisitions are not allowed to be started from the
         // EDT. Therefore, we must make a new thread to run this.
         Thread acqThread = new Thread(new Runnable() {
            @Override
            public void run() {
               final MultiAcqEngJAdapter acqj = new MultiAcqEngJAdapter(studio_);
               SequenceSettings.Builder sb = new SequenceSettings.Builder();
               double multiplier = 1;
               String token = (String) timeUnitCombo_.getSelectedItem();
               if (token != null && token.equals("s")) {
                  multiplier = 1000;
               } else if (token != null && token.equals("min")) {
                  multiplier = 60000;
               }
               try {
                  sb.useFrames(framesPanel_.isSelected())
                        .numFrames((Integer) numFrames_.getValue())
                        .intervalMs(NumberUtils.displayStringToDouble(
                                    interval_.getText()) * multiplier);
                  sb.useAutofocus(autoFocusPanel_.isSelected())
                        .skipAutofocusCount((Integer) afSkipInterval_.getValue());
               } catch (ParseException ex) {
                  studio_.logs().logError(ex);
               }
               SequenceSettings baseSettings = sb.build();
               acqj.runAcquisition(baseSettings, acqs_);
            }
         });
         acqThread.start();
      });

      super.add(acquireButton, "align right, push, gap 10, wrap");

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      // restore settings from previous session
      final MutablePropertyMapView settings = studio_.profile().getSettings(this.getClass());
      framesPanel_.setSelected(settings.getBoolean(USE_TIME_POINTS, false));
      autoFocusPanel_.setSelected(settings.getBoolean(USE_AUTOFOCUS, false));
      presetPanel_.setSelected(settings.getBoolean(USE_PRESET, false));
      int nrAcquisitions = settings.getInteger(NR_ACQ_SETTINGS, 0);
      if (nrAcquisitions > 0) {
         List<String> acqPaths = settings.getStringList(ACQ_PATHS);
         List<String> positionListFiles = settings.getStringList(POSITION_LIST_PATHS);
         int count = 0;
         for (String acqPath : acqPaths) {
            File f = new File(acqPath);
            SequenceSettings seqSb;
            try {
               seqSb = studio_.acquisitions().loadSequenceSettings(f.getPath());
            } catch (IOException ex) {
               studio_.logs().logError(ex, "Failed to load Acquisition setting file");
               continue;
            }
            acqs_.add(count, new MDASettingData(studio_, f, seqSb));
            if (positionListFiles.size() > count) {
               File posFile = new File(positionListFiles.get(count));
               acqs_.get(count).setPositionListFile(posFile);
            }
            count++;
         }
         nrSpinner_.setValue(count);
      }

      super.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs. You need to call the right registerForEvents() method
      // to get events; this one is for the application-wide event bus, but
      // there's also Datastore.registerForEvents() for events specific to one
      // Datastore, and DisplayWindow.registerForEvents() for events specific
      // to one image display window.
      studio_.events().registerForEvents(this);
   }

   /**
    * Updates the number of settings we are using based on UI request.
    *
    * @param nr Desired number of different settings.
    * @return Input number if successful, input minus 1 on failure
    */
   private int adjustNrSettings(int nr) {
      acqPanel_.removeAll();
      acqLabels_.clear();
      // add headers to the table
      acqPanel_.add(new JLabel("Acquisition Settings File"), "span 2, alignx center");
      acqPanel_.add(new JLabel("Preset"), "alignx center");
      acqPanel_.add(new JLabel("Position List File"), "span 2, alignx center");
      acqPanel_.add(new JLabel("Explanation"), "alignx center, wrap");
      for (int i = 0; i < nr; i++) {
         final int lineNr = i;
         if (acqs_.size() <= i) {
            File f = FileDialogs.openFile(this,
                  "Load acquisition settings", FileDialogs.ACQ_SETTINGS_FILE);
            if (f != null) {
               SequenceSettings seqSb;
               try {
                  seqSb = studio_.acquisitions().loadSequenceSettings(f.getPath());
               } catch (IOException ex) {
                  studio_.logs().showError(ex, "Failed to load Acquisition setting file");
                  return nr - 1;
               }
               acqs_.add(i, new MDASettingData(studio_, f, seqSb));
            } else {
               return nr - 1;
            }
         }
         JLabelC label = new JLabelC(acqs_.get(i).getAcqSettingFile().getName());
         acqLabels_.add(i, label);
         acqPanel_.add(acqLabels_.get(i));

         JButton selectAcqFile = new JButton("...");
         selectAcqFile.setFont(DEFAULT_FONT);
         selectAcqFile.addActionListener(e -> {
            File f = FileDialogs.openFile(this,
                  "Load acquisition settings", FileDialogs.ACQ_SETTINGS_FILE);
            if (f != null) {
               SequenceSettings seqSb = null;
               try {
                  seqSb = studio_.acquisitions().loadSequenceSettings(f.getPath());
               } catch (IOException ex) {
                  studio_.logs().showError(ex, "Failed to load Acquisition setting file");
               }
               if (seqSb != null) {
                  acqs_.get(lineNr).setAcqSettings(f, seqSb);
                  acqLabels_.get(lineNr).setText(acqs_.get(lineNr).getAcqSettingFile().getName());
                  JLabel explanation = acqExplanations_.get(lineNr);
                  if (explanation != null) {
                     explanation.setText(oneLineSummary(acqs_.get(lineNr)));
                  }
               }
            }
         });
         acqPanel_.add(selectAcqFile);

         final JComboBox<String> presetCombo = new JComboBox<>();
         presetCombo.setFont(DEFAULT_FONT);
         presetCombo.getEditor().getEditorComponent().setFont(DEFAULT_FONT);
         presetCombo.addItem("");
         final String presetGroup = studio_.profile().getSettings(
               this.getClass()).getString(PRESET_GROUP, "");
         if (presetGroup != null && !presetGroup.isEmpty()) {
            studio_.core().getAvailableConfigs(presetGroup).forEach(presetCombo::addItem);
         }
         acqs_.get(lineNr).setPresetGroup(presetGroup);
         presetCombo.addActionListener(e -> {
            String presetName = (String) presetCombo.getSelectedItem();
            if (lineNr < acqs_.size()) {
               studio_.profile().getSettings((this.getClass())).putString(
                       presetGroup + acqs_.get(lineNr).getAcqSettingFile().getPath(),
                       presetName);
               acqs_.get(lineNr).setPresetName(presetName);
            }
         });
         presetCombo.setSelectedItem(studio_.profile().getSettings(
               this.getClass()).getString(presetGroup
                         + acqs_.get(lineNr).getAcqSettingFile().getPath(),
                 ""));
         presetCombos_.add(presetCombo);
         acqPanel_.add(presetCombo, "gapx 20");

         String positionListText = "current position";
         if (acqs_.get(lineNr).getPositionList() != null) {
            positionListText = acqs_.get(lineNr).getPositionListFile().getName();
         }
         final JLabelC positionListLabel = new JLabelC(positionListText);
         acqPanel_.add(positionListLabel, "gapx 20");

         JButton selectPosFile = new JButton("...");
         selectPosFile.setFont(DEFAULT_FONT);
         selectPosFile.addActionListener(e -> {
            File f = FileDialogs.openFile(this,
                  "Load position list", POSITION_LIST_FILE);
            if (f != null) {
               acqs_.get(lineNr).setPositionListFile(f);
               if (acqs_.get(lineNr).getPositionList() != null) {
                  positionListLabel.setText(acqs_.get(lineNr).getPositionListFile().getName());
                  JLabel explanation = acqExplanations_.get(lineNr);
                  if (explanation != null) {
                     explanation.setText(oneLineSummary(acqs_.get(lineNr)));
                  }
               }
            }
         });
         acqPanel_.add(selectPosFile);

         acqExplanations_.add(new JLabelC(oneLineSummary(acqs_.get(lineNr))));
         acqPanel_.add(acqExplanations_.get(lineNr), "gapx 20, wrap");
      }
      while (nr < acqs_.size()) {
         acqs_.remove(acqs_.size() - 1);
      }

      return nr;
   }

   /**
    * Creates the panel for showing timepoints settings. This one can have
    * its contents overridden by the custom time intervals system, in which
    * case its normal contents get hidden.
    */
   private CheckBoxPanel createTimePoints() {
      CheckBoxPanel framesPanel = new CheckBoxPanel("Time Points");
      framesPanel.setLayout(new MigLayout("fillx, gap 2, insets 2" + ", hidemode 3",
            "[grow, fill]", "[grow, fill]"));

      JPanel defaultTimesPanel = new JPanel(
            new MigLayout("fill, gap 2, insets 0",
                  "push[][][]push", "[][][]"));
      defaultTimesPanel.setEnabled(framesPanel.isEnabled());
      framesPanel.add(defaultTimesPanel, "grow");

      final JLabelC numberLabel = new JLabelC("Count:");
      numberLabel.setEnabled(framesPanel.isEnabled());
      defaultTimesPanel.add(numberLabel, "alignx label");

      SpinnerModel sModel = new SpinnerNumberModel(1, 1, null, 1);
      numFrames_ = new JSpinner(sModel);
      JTextField field = ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField();
      field.setColumns(5);
      ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField().setFont(DEFAULT_FONT);
      numFrames_.setEnabled(framesPanel.isEnabled());
      defaultTimesPanel.add(numFrames_, "wrap");

      final JLabelC intervalLabel = new JLabelC("Interval:");
      intervalLabel.setToolTipText(
            "Interval between successive time points.  Setting an interval "
                  + "less than the exposure time will cause micromanager to acquire "
                  + "a 'burst' of images as fast as possible");
      intervalLabel.setEnabled(framesPanel.isEnabled());
      defaultTimesPanel.add(intervalLabel, "alignx label");

      interval_ = new JFormattedTextField(NumberFormat.getInstance());
      interval_.setColumns(5);
      interval_.setFont(DEFAULT_FONT);
      interval_.setValue(1.0);
      interval_.setEnabled(framesPanel.isEnabled());
      defaultTimesPanel.add(interval_);

      timeUnitCombo_ = new JComboBox<>();
      timeUnitCombo_.setModel(new DefaultComboBoxModel<>(new String[] {"ms", "s", "min"}));
      timeUnitCombo_.setFont(DEFAULT_FONT);
      // We shove this thing to the left a bit so that it takes up the same
      // vertical space as the spinner for the number of timepoints.
      timeUnitCombo_.setEnabled(framesPanel.isEnabled());
      defaultTimesPanel.add(timeUnitCombo_, "pad 0 -15 0 0, wrap");

      JLabelC overrideLabel = new JLabelC("Custom time intervals enabled");
      overrideLabel.setFont(new Font("Arial", Font.BOLD, 12));
      overrideLabel.setForeground(Color.red);

      return framesPanel;
   }

   private CheckBoxPanel createAutoFocus() {
      CheckBoxPanel afPanel = new CheckBoxPanel("Autofocus");
      afPanel.setLayout(new MigLayout("fillx, gap 2, insets 2" + ", hidemode 3",
            "[grow, fill]", "[grow, fill]"));

      JButton afButton = new JButton("Options...");
      afButton.setFont(DEFAULT_FONT);
      afButton.setToolTipText("Set autofocus options");
      afButton.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/wrench_orange.png"));
      afButton.setMargin(new Insets(2, 5, 2, 5));
      afButton.addActionListener((ActionEvent arg0) -> studio_.app().showAutofocusDialog());
      afPanel.add(afButton, "alignx center, wrap");

      final JLabelC afSkipFrame1 = new JLabelC("Skip frame(s):");
      afSkipFrame1.setToolTipText("How many frames to skip between running autofocus. "
            + "Autofocus is always run at new stage positions");
      afPanel.add(afSkipFrame1, "split, spanx, alignx center");

      afSkipInterval_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
      JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) afSkipInterval_.getEditor();
      editor.setFont(DEFAULT_FONT);
      editor.getTextField().setColumns(3);
      afSkipInterval_.setValue(0);
      afPanel.add(afSkipInterval_);

      return afPanel;
   }

   private CheckBoxPanel createPresetPanel() {
      CheckBoxPanel presetPanel = new CheckBoxPanel("Use Preset Before each MDA");
      presetPanel.setLayout(new MigLayout("fillx, gap 2, insets 2" + ", hidemode 3",
               "[grow, fill]", "[grow, fill]"));

      JLabel explanation = new JLabelC("Runs each time point ");
      presetPanel.add(explanation, "wrap");

      final JLabel presetLabel = new JLabelC("PresetGroup:");
      presetLabel.setToolTipText("A preset can be applied before running each MDA. "
               + "(at each time point). Select the Preset Group here.");
      presetPanel.add(presetLabel, "split, spanx, alignx center");

      JComboBox<String> configGroupCombo = new JComboBox<>();
      configGroupCombo.addItem("");
      studio_.core().getAvailableConfigGroups().forEach(configGroupCombo::addItem);
      configGroupCombo.setEnabled(presetPanel.isEnabled());
      configGroupCombo.addActionListener(e -> {
         String presetGroup = (String) configGroupCombo.getSelectedItem();
         studio_.profile().getSettings((this.getClass())).putString(PRESET_GROUP, presetGroup);
         for (JComboBox<String> combo : presetCombos_) {
            combo.removeAllItems();
            combo.addItem("");
            studio_.core().getAvailableConfigs(presetGroup).forEach(combo::addItem);
         }
         for (int counter = 0; counter < acqs_.size(); counter++) {
            acqs_.get(counter).setPresetGroup(presetGroup);
            String presetName = studio_.profile().getSettings(this.getClass())
                    .getString(presetGroup
                    + acqs_.get(counter).getAcqSettingFile().getPath(), "");
            if (counter < presetCombos_.size()) {
               presetCombos_.get(counter).setSelectedItem(presetName);
            }
         }
      });
      configGroupCombo.setSelectedItem(studio_.profile().getSettings(
               this.getClass()).getString(PRESET_GROUP, ""));
      presetPanel.add(configGroupCombo, "alignx center, wrap");

      return presetPanel;
   }

   private String oneLineSummary(MDASettingData mdaSettingData) {
      StringBuilder sb = new StringBuilder();
      if (mdaSettingData.getSequenceSettings().useSlices()) {
         sb.append("ZStack: ").append(MultiAcqEngJAdapter.getNumSlices(
               mdaSettingData.getSequenceSettings())).append(" slices. ");
      }
      if (mdaSettingData.getSequenceSettings().useChannels()) {
         sb.append("Channels: ");
         for (ChannelSpec channelSpec : mdaSettingData.getSequenceSettings().channels()) {
            if (channelSpec.useChannel()) {
               sb.append(channelSpec.config()).append(", ");
            }
         }
      }
      if (mdaSettingData.getSequenceSettings().usePositionList()
            && mdaSettingData.getPositionList() != null
            && mdaSettingData.getPositionList().getNumberOfPositions() > 0) {
         sb.append("Positions: ").append(mdaSettingData.getPositionList().getNumberOfPositions());
         sb.append(".");
      } else {
         sb.append("Positions: current only.");
      }
      if (mdaSettingData.getSequenceSettings().save()) {
         sb.append(" Saving.");
      } else {
         sb.append(" Not saving.");
      }
      return sb.toString();
   }

   /**
    * Event signalling that MM will shut down.  Our cue to store current settings to
    * the profile
    *
    * @param sce This event can be used to stop MM from shutting down, but we only use
    *            it as a signal.
    */
   @Subscribe
   public void onApplicationShuttingDown(ShutdownCommencingEvent sce) {
      MutablePropertyMapView settings = studio_.profile().getSettings(this.getClass());
      settings.putInteger(NR_ACQ_SETTINGS, (Integer) nrSpinner_.getValue());
      settings.putBoolean(USE_TIME_POINTS, framesPanel_.isSelected());
      settings.putBoolean(USE_AUTOFOCUS, autoFocusPanel_.isSelected());
      settings.putBoolean(USE_PRESET, presetPanel_.isSelected());

      List<String> acqPaths = new ArrayList<>(acqs_.size());
      List<String> posListPaths = new ArrayList<>(acqs_.size());
      for (MDASettingData acq : acqs_) {
         acqPaths.add(acq.getAcqSettingFile().getPath());
         if (acq.getPositionListFile() != null) {
            posListPaths.add(acq.getPositionListFile().getPath());
         } else {
            posListPaths.add("");
         }
      }
      settings.putStringList(ACQ_PATHS, acqPaths);
      settings.putStringList(POSITION_LIST_PATHS, posListPaths);
   }

}