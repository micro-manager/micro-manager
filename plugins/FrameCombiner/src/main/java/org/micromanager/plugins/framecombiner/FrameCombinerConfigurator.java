package org.micromanager.plugins.framecombiner;

import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;
import org.micromanager.internal.utils.WindowPositioning;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.

public class FrameCombinerConfigurator extends JFrame implements ProcessorConfigurator {

   private static final String PROCESSOR_DIMENSION = "Dimension";
   private static final String USE_WHOLE_STACK = "useWholeStack";
   private static final String PROCESSOR_ALGO = "Algorithm to apply on stack images";
   private static final String NUMBER_TO_PROCESS = "Number of images to process";
   private static final String CHANNEL_TO_AVOID = "Avoid Channel(s) (eg. 1,2 or 1-5)";

   private final Studio studio_;
   private final PropertyMap settings_;
   private JComboBox<String> processorDimensionBox_;
   private JCheckBox useWholeStackCheckBox_;
   private JFormattedTextField numberOfImagesToProcessField_;
   private JLabel numberOfImagesToProcessLabel_;
   private JLabel useWholeStackLabel_;
   private JLabel sharpnessAlgoLabel_;
   private JLabel sharpnessShowGraphLabel_;
   private JComboBox<String> processorAlgoBox_;
   private JComboBox<String> sharpnessAlgoBox_;
   private JCheckBox sharpnessShowGraphCheckBox_;
   private JFormattedTextField channelsToAvoidField_;

   public FrameCombinerConfigurator(PropertyMap settings, Studio studio) {
      studio_ = studio;
      settings_ = settings;

      initComponents();
      loadSettingValue();

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(200, 200);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      super.setVisible(true);
   }

   private void initComponents() {
      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      super.setTitle(FrameCombinerPlugin.MENU_NAME);
      setTitle("FrameCombiner");

      final JPanel jPanel1 = new JPanel();
      final NumberFormat format = NumberFormat.getInstance();
      final NumberFormatter formatter = new NumberFormatter(format);
      formatter.setValueClass(Integer.class);
      formatter.setMinimum(1);
      formatter.setMaximum(Integer.MAX_VALUE);
      formatter.setCommitsOnValidEdit(true);
      formatter.setAllowsInvalid(false);
      processorDimensionBox_ = new JComboBox<>();
      useWholeStackCheckBox_ = new JCheckBox();
      numberOfImagesToProcessField_ = new JFormattedTextField(formatter);
      processorAlgoBox_ = new JComboBox<>();
      sharpnessAlgoBox_ = new JComboBox<>();
      sharpnessShowGraphCheckBox_ = new JCheckBox();

      setDefaultCloseOperation(DISPOSE_ON_CLOSE);

      jPanel1.setAutoscrolls(true);
      jPanel1.setLayout(new MigLayout("flowx, fill, insets 8, gapy 15"));

      jPanel1.add(new JLabel("Dimension to process"));
      useWholeStackLabel_ = new JLabel("Use whole stack");
      numberOfImagesToProcessLabel_ = new JLabel("Number of images to process");

      processorDimensionBox_.addItem(FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME);
      processorDimensionBox_.addItem(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z);
      processorDimensionBox_.addActionListener(e -> {
         updateUISelectedItems();
      });
      jPanel1.add(processorDimensionBox_, "wrap");

      jPanel1.add(useWholeStackLabel_);
      useWholeStackCheckBox_.addActionListener(e -> {
         updateUISelectedItems();
      });
      jPanel1.add(useWholeStackCheckBox_, "wrap");

      jPanel1.add(numberOfImagesToProcessLabel_);
      numberOfImagesToProcessField_.setName("_");
      jPanel1.add(numberOfImagesToProcessField_, "growx, wrap");

      jPanel1.add(new JLabel(
              "<html>Avoid Channel(s) (zero-based)<br/><p style=\"text-align: center;\">"
                      + "eg. 1,2 or 1-5 (no space)</p></html>"));
      channelsToAvoidField_ = new JFormattedTextField();
      channelsToAvoidField_.setName("_");
      jPanel1.add(channelsToAvoidField_, "growx, wrap");

      jPanel1.add(new JLabel("Algorithm to apply on image stack"));
      sharpnessAlgoLabel_ = new JLabel("Sharpness algorithm");
      sharpnessShowGraphLabel_ = new JLabel("Show graph");
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MEAN);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_SUM);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MAX);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MIN);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_SHARPEST);
      processorAlgoBox_.addActionListener(e -> {
         updateUISelectedItems();
      });
      jPanel1.add(processorAlgoBox_, "wrap");

      jPanel1.add(sharpnessAlgoLabel_);
      for (ImgSharpnessAnalysis.Method method : ImgSharpnessAnalysis.Method.values()) {
         sharpnessAlgoBox_.addItem(method.name());
      }
      jPanel1.add(sharpnessAlgoBox_, "wrap");

      jPanel1. add(sharpnessShowGraphLabel_);
      jPanel1.add(sharpnessShowGraphCheckBox_, "wrap");

      super.add(jPanel1, "wrap");
      pack();
   }

   private void updateUISelectedItems() {
      if (Objects.equals(processorDimensionBox_.getSelectedItem(),
              FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME)) {
         useWholeStackCheckBox_.setEnabled(false);
         useWholeStackLabel_.setEnabled(false);
         numberOfImagesToProcessField_.setEnabled(true);
         numberOfImagesToProcessLabel_.setEnabled(true);
      } else if (Objects.equals(processorDimensionBox_.getSelectedItem(),
              FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)) {
         useWholeStackCheckBox_.setEnabled(true);
         useWholeStackLabel_.setEnabled(true);
         numberOfImagesToProcessField_.setEnabled(!useWholeStackCheckBox_.isSelected());
         numberOfImagesToProcessLabel_.setEnabled(!useWholeStackCheckBox_.isSelected());
      }
      if (Objects.equals(processorAlgoBox_.getSelectedItem(),
              FrameCombinerPlugin.PROCESSOR_ALGO_SHARPEST)) {
         sharpnessAlgoBox_.setEnabled(true);
         sharpnessAlgoLabel_.setEnabled(true);
         sharpnessShowGraphCheckBox_.setEnabled(true);
         sharpnessShowGraphLabel_.setEnabled(true);
      } else {
         sharpnessAlgoBox_.setEnabled(false);
         sharpnessAlgoLabel_.setEnabled(false);
         sharpnessShowGraphCheckBox_.setEnabled(false);
         sharpnessShowGraphLabel_.setEnabled(false);
      }
   }


   private void loadSettingValue() {
      String processorDimension = studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .getString(PROCESSOR_DIMENSION, FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME);
      processorDimensionBox_.setSelectedItem(settings_.getString(
            "processorDimension", processorDimension));
      boolean useWholeStack = studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .getBoolean(USE_WHOLE_STACK, false);
      useWholeStackCheckBox_.setSelected(settings_.getBoolean(
            "useWholeStack", useWholeStack));
      int numberOfImagesToProcess =  studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .getInteger(NUMBER_TO_PROCESS, 10);
      numberOfImagesToProcessField_.setText(Integer.toString(settings_.getInteger(
            "numberOfImagesToProcess", numberOfImagesToProcess)));
      String channelsToAvoid = studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .getString(CHANNEL_TO_AVOID, "");
      channelsToAvoidField_.setText(settings_.getString(
            "channelsToAvoidField", channelsToAvoid));
      String processorAlgo = studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .getString(PROCESSOR_ALGO, FrameCombinerPlugin.PROCESSOR_ALGO_MEAN);
      processorAlgoBox_.setSelectedItem(settings_.getString(
              "processorAlgo", processorAlgo));
      String sharpnessAlgo = studio_.profile().getSettings(FrameCombinerConfigurator.class)
               .getString(FrameCombinerPlugin.PREF_SHARPNESS_ALGO,
                       ImgSharpnessAnalysis.Method.Redondo.name());
      sharpnessAlgoBox_.setSelectedItem(settings_.getString(
               FrameCombinerPlugin.PREF_SHARPNESS_ALGO, sharpnessAlgo));
      boolean sharpnessShowGraph = studio_.profile().getSettings(FrameCombinerConfigurator.class)
               .getBoolean(FrameCombinerPlugin.PREF_SHARPNESS_SHOW_GRAPH, false);
      sharpnessShowGraphCheckBox_.setSelected(settings_.getBoolean(
               FrameCombinerPlugin.PREF_SHARPNESS_SHOW_GRAPH, sharpnessShowGraph));
      updateUISelectedItems();
   }

   @Override
   public void showGUI() {
      pack();
      setVisible(true);
   }

   @Override
   public void cleanup() {
      dispose();
   }

   @Override
   public PropertyMap getSettings() {
      // Save preferences now.
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .putBoolean(USE_WHOLE_STACK, useWholeStackCheckBox_.isSelected());
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .putString(PROCESSOR_DIMENSION, (String ) processorDimensionBox_.getSelectedItem());
      Integer numberOfImagesToProcess = Integer.parseInt(numberOfImagesToProcessField_.getText());
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .putInteger(NUMBER_TO_PROCESS, numberOfImagesToProcess);
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .putString(CHANNEL_TO_AVOID, channelsToAvoidField_.getText());
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
              .putString(PROCESSOR_ALGO, (String) processorAlgoBox_.getSelectedItem());
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
               .putString(FrameCombinerPlugin.PREF_SHARPNESS_ALGO,
                       (String) sharpnessAlgoBox_.getSelectedItem());
      studio_.profile().getSettings(FrameCombinerConfigurator.class)
               .putBoolean(FrameCombinerPlugin.PREF_SHARPNESS_SHOW_GRAPH,
                       sharpnessShowGraphCheckBox_.isSelected());

      PropertyMap.Builder builder = PropertyMaps.builder();
      builder.putString(FrameCombinerPlugin.PREF_PROCESSOR_DIMENSION,
              (String) processorDimensionBox_.getSelectedItem());
      builder.putBoolean(FrameCombinerPlugin.PREF_USE_WHOLE_STACK,
              useWholeStackCheckBox_.isSelected());
      builder.putInteger(FrameCombinerPlugin.PREF_NUMBER_OF_IMAGES_TO_PROCESS,
            Integer.parseInt(numberOfImagesToProcessField_.getText()));
      builder.putString(FrameCombinerPlugin.PREF_CHANNELS_TO_AVOID,
              channelsToAvoidField_.getText());
      builder.putString(FrameCombinerPlugin.PREF_PROCESSOR_ALGO,
              (String) processorAlgoBox_.getSelectedItem());
      builder.putString(FrameCombinerPlugin.PREF_SHARPNESS_ALGO,
               (String) sharpnessAlgoBox_.getSelectedItem());
      builder.putBoolean(FrameCombinerPlugin.PREF_SHARPNESS_SHOW_GRAPH,
               sharpnessShowGraphCheckBox_.isSelected());
      return builder.build();
   }

   /**
    * Stores settings when the window is closing.
    *
    * @param evt the window event that is not used.
    */
   public void windowClosing(WindowEvent evt) {
      getSettings();
   }

}
