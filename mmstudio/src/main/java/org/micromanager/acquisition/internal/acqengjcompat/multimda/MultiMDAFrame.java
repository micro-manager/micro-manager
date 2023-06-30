/**
 * MultiMDAFrame.java
 *
 * <p>This module shows an example of creating a GUI (Graphical User Interface).
 * There are many ways to do this in Java; this particular example uses the
 * MigLayout layout manager, which has extensive documentation online.
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
import javax.swing.event.ChangeEvent;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PositionList;
import org.micromanager.Studio;
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
   private static final String NR_ACQ_SETTINGS = "NumberOfSettings";
   private static final String ACQ_PATHS = "AcquisitionPaths";
   private final JSpinner nrSpinner_;
   private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 10);
   private JSpinner numFrames_;
   private JFormattedTextField interval_;
   private JComboBox<String> timeUnitCombo_;
   private final CheckBoxPanel framesPanel_;
   private final CheckBoxPanel afPanel_;
   private JSpinner afSkipInterval_;

   /**
    * Constructor of the plugin, draws the UI and restores settings from profile.
    *
    * @param studio Always present Studio instance
    */
   public MultiMDAFrame(Studio studio) {
      super("Multi-MDA");
      studio_ = studio;

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));
      acqPanel_ = new JPanel();
      acqPanel_.setLayout(new MigLayout("fill, insets 4, gap 8, flowx"));

      JLabel title = new JLabel("Use different MDA settings at different positions");
      title.setFont(new Font("Arial", Font.BOLD, 20));
      super.add(title, "span, alignx center, wrap");
      super.add(new JSeparator(), "span, wrap");

      // create time point panel
      framesPanel_ = createTimePoints();
      super.add(framesPanel_, "span, split 2, gap 12, align center");
      afPanel_ = createAutoFocus();
      super.add(afPanel_, "wrap");

      // Create a text field for the user to customize their alerts.
      super.add(new JLabel("Number of different settings: "));
      nrSpinner_ = new JSpinner();
      nrSpinner_.setValue(0);
      nrSpinner_.addChangeListener(e -> {
         int val = adjustNrSettings((int) nrSpinner_.getValue());
         nrSpinner_.setValue(val);
         super.pack();
      });
      super.add(nrSpinner_, "wrap");

      super.add(acqPanel_, "span 2, wrap");


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
               List<SequenceSettings> seqs = new ArrayList<>();
               List<PositionList> positionLists = new ArrayList<>();
               SequenceSettings.Builder sb = new SequenceSettings.Builder();
               double multiplier = 1;
               if (timeUnitCombo_.getSelectedItem().equals("s")) {
                  multiplier = 1000;
               } else if (timeUnitCombo_.getSelectedItem().equals("min")) {
                  multiplier = 60000;
               }
               try {
                  sb.useFrames(framesPanel_.isSelected())
                        .numFrames((Integer) numFrames_.getValue())
                        .intervalMs(NumberUtils.displayStringToDouble(
                                    interval_.getText()) * multiplier);
                  sb.useAutofocus(afPanel_.isSelected())
                        .skipAutofocusCount((Integer) afSkipInterval_.getValue());
               } catch (ParseException ex) {
                  ex.printStackTrace();
               }
               SequenceSettings baseSettings = sb.build();
               for (MDASettingData acq : acqs_) {
                  seqs.add(acq.getSequenceSettings());
                  positionLists.add(acq.getPositionList());
               }
               acqj.runAcquisition(baseSettings, seqs, positionLists);
            }
         });
         acqThread.start();
      });

      super.add(acquireButton, "span 2, align right, wrap");

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      // restore settings from previous session
      MutablePropertyMapView settings = studio_.profile().getSettings(this.getClass());
      int nrAcquisitions = settings.getInteger(NR_ACQ_SETTINGS, 0);
      if (nrAcquisitions > 0) {
         List<String> acqPaths = settings.getStringList(ACQ_PATHS);
         int count = 0;
         for (String acqPath : acqPaths) {
            File f = new File(acqPath);
            SequenceSettings seqSb = null;
            try {
               SequenceSettings seqS = studio_.acquisitions().loadSequenceSettings(f.getPath());
               seqSb = seqS;
            } catch (IOException ex) {
               studio_.logs().logError(ex, "Failed to load Acquisition setting file");
               continue;
            }
            acqs_.add(count, new MDASettingData(studio_, f, seqSb));
            JLabel label = new JLabel(acqs_.get(count).getAcqSettingFile().getName());
            acqLabels_.add(count, label);
            acqPanel_.add(acqLabels_.get(count));

            JButton selectAcqFile = new JButton("...");
            final int lineNr = count;
            selectAcqFile.addActionListener(e -> {
               File ff = FileDialogs.openFile(this,
                     "Load acquisition settings", FileDialogs.ACQ_SETTINGS_FILE);
               if (ff != null) {
                  SequenceSettings seqSbb = null;
                  try {
                     seqSbb = studio_.acquisitions().loadSequenceSettings(f.getPath());
                  } catch (IOException ex) {
                     studio_.logs().showError(ex, "Failed to load Acquisition setting file");
                  }
                  if (seqSbb != null) {
                     acqs_.get(lineNr).setAcqSettings(f, seqSbb);
                     acqLabels_.get(lineNr).setText(
                           acqs_.get(lineNr).getAcqSettingFile().getName());
                  }
               }
            });
            acqPanel_.add(selectAcqFile);

            final JButton posListButton = new JButton("Use current PositionList");
            posListButton.addActionListener(e -> {
               acqs_.get(lineNr).setPositionList(studio_.positions().getPositionList());
               posListButton.setText("PositionList set");
            });
            acqPanel_.add(posListButton, "wrap");

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
      for (int i = 0; i < nr; i++) {
         if (acqs_.size() <= i) {
            File f = FileDialogs.openFile(this,
                  "Load acquisition settings", FileDialogs.ACQ_SETTINGS_FILE);
            if (f != null) {
               SequenceSettings seqSb = null;
               try {
                  SequenceSettings seqS = studio_.acquisitions().loadSequenceSettings(f.getPath());
                  seqSb = seqS;
               } catch (IOException ex) {
                  studio_.logs().showError(ex, "Failed to load Acquisition setting file");
                  return nr - 1;
               }
               acqs_.add(i, new MDASettingData(studio_, f, seqSb));
            } else {
               return nr - 1;
            }
         }
         JLabel label = new JLabel(acqs_.get(i).getAcqSettingFile().getName());
         acqLabels_.add(i, label);
         acqPanel_.add(acqLabels_.get(i));

         JButton selectAcqFile = new JButton("...");
         final int lineNr = i;
         selectAcqFile.addActionListener(e -> {
            File f = FileDialogs.openFile(this,
                  "Load acquisition settings", FileDialogs.ACQ_SETTINGS_FILE);
            if (f != null) {
               SequenceSettings seqSb = null;
               try {
                  SequenceSettings seqS =
                        studio_.acquisitions().loadSequenceSettings(f.getPath());
                  seqSb = seqS;
               } catch (IOException ex) {
                  studio_.logs().showError(ex, "Failed to load Acquisition setting file");
               }
               if (seqSb != null) {
                  acqs_.get(lineNr).setAcqSettings(f, seqSb);
                  acqLabels_.get(lineNr).setText(acqs_.get(lineNr).getAcqSettingFile().getName());
               }
            }
         });
         acqPanel_.add(selectAcqFile);

         final JButton posListButton = new JButton("Use current PositionList");
         posListButton.addActionListener(e -> {
            acqs_.get(lineNr).setPositionList(studio_.positions().getPositionList());
            posListButton.setText("PositionList set");
         });
         acqPanel_.add(posListButton, "wrap");

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
      framesPanel.add(defaultTimesPanel, "grow");

      final JLabel numberLabel = new JLabel("Count:");
      numberLabel.setFont(DEFAULT_FONT);

      defaultTimesPanel.add(numberLabel, "alignx label");

      SpinnerModel sModel = new SpinnerNumberModel(1, 1, null, 1);

      numFrames_ = new JSpinner(sModel);
      JTextField field = ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField();
      field.setColumns(5);
      ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField().setFont(DEFAULT_FONT);

      defaultTimesPanel.add(numFrames_, "wrap");

      final JLabel intervalLabel = new JLabel("Interval:");
      intervalLabel.setFont(DEFAULT_FONT);
      intervalLabel.setToolTipText(
            "Interval between successive time points.  Setting an interval "
                  + "less than the exposure time will cause micromanager to acquire "
                  + "a 'burst' of images as fast as possible");
      defaultTimesPanel.add(intervalLabel, "alignx label");

      interval_ = new JFormattedTextField(NumberFormat.getInstance());
      interval_.setColumns(5);
      interval_.setFont(DEFAULT_FONT);
      interval_.setValue(1.0);
      defaultTimesPanel.add(interval_);

      timeUnitCombo_ = new JComboBox<>();
      timeUnitCombo_.setModel(new DefaultComboBoxModel<>(new String[] {"ms", "s", "min"}));
      timeUnitCombo_.setFont(DEFAULT_FONT);
      // We shove this thing to the left a bit so that it takes up the same
      // vertical space as the spinner for the number of timepoints.
      defaultTimesPanel.add(timeUnitCombo_, "pad 0 -15 0 0, wrap");

      JLabel overrideLabel = new JLabel("Custom time intervals enabled");
      overrideLabel.setFont(new Font("Arial", Font.BOLD, 12));
      overrideLabel.setForeground(Color.red);

      // framesPanel_.addActionListener((ActionEvent e) -> applySettingsFromGUI());
      return framesPanel;
   }

   private CheckBoxPanel createAutoFocus() {
      CheckBoxPanel afPanel = new CheckBoxPanel("Autofocus");
      afPanel.setLayout(new MigLayout("fillx, gap 2, insets 2" + ", hidemode 3",
            "[grow, fill]", "[grow, fill]"));

      JButton afButton = new JButton("Options...");
      afButton.setToolTipText("Set autofocus options");
      afButton.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/wrench_orange.png"));
      afButton.setMargin(new Insets(2, 5, 2, 5));
      afButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      afButton.addActionListener((ActionEvent arg0) -> studio_.app().showAutofocusDialog());
      afPanel.add(afButton, "alignx center, wrap");

      final JLabel afSkipFrame1 = new JLabel("Skip frame(s):");
      afSkipFrame1.setFont(new Font("Dialog", Font.PLAIN, 10));
      afSkipFrame1.setToolTipText("How many frames to skip between running autofocus. "
            + "Autofocus is always run at new stage positions");

      afPanel.add(afSkipFrame1, "split, spanx, alignx center");

      afSkipInterval_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
      JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) afSkipInterval_.getEditor();
      editor.setFont(DEFAULT_FONT);
      editor.getTextField().setColumns(3);
      afSkipInterval_.setValue(0);
      // afSkipInterval_.setValue(getSequenceSettings().skipAutofocusCount());
      // afSkipInterval_.addChangeListener((ChangeEvent e) -> {
      //   applySettingsFromGUI();
      //   afSkipInterval_.setValue(getAcquisitionEngine().getSequenceSettings()
      //         .skipAutofocusCount());
      // });
      afPanel.add(afSkipInterval_);

      // afPanel.addActionListener((ActionEvent arg0) -> applySettingsFromGUI());
      return afPanel;
   }

   /**
    * Event signalling that MM will shut down.  Out cue to store current settings to
    * the profile
    *
    * @param sce This event can be used to stop MM from shutting down, but we only use
    *            it as a signal.
    */
   @Subscribe
   public void onApplicationShuttingDown(ShutdownCommencingEvent sce) {
      MutablePropertyMapView settings = studio_.profile().getSettings(this.getClass());
      settings.putInteger(NR_ACQ_SETTINGS, (Integer) nrSpinner_.getValue());

      List<String> acqPaths = new ArrayList<>(acqs_.size());
      for (MDASettingData acq : acqs_) {
         acqPaths.add(acq.getAcqSettingFile().getPath());
      }
      settings.putStringList(ACQ_PATHS, acqPaths);
   }

}