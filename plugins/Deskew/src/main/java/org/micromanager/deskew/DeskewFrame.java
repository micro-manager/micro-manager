package org.micromanager.deskew;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.haesleinhuepf.clij.clearcl.ClearCL;
import net.haesleinhuepf.clij.clearcl.ClearCLDevice;
import net.haesleinhuepf.clij.clearcl.backend.ClearCLBackendInterface;
import net.haesleinhuepf.clij.clearcl.backend.ClearCLBackends;
import net.haesleinhuepf.clij2.CLIJ2;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.internal.pipelineinterface.ProcessExistingDataDialog;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * UI part of the plugin to deskew OPM light sheet data.
 */
public class DeskewFrame extends JFrame implements ProcessorConfigurator {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String DIALOG_TITLE = "Deskew";

   // keys to store settings in MutablePropertyMap
   static final String THETA = "Theta";
   static final String FULL_VOLUME = "Create Full Volume";
   static final String XY_PROJECTION = "Do XY Projection";
   static final String XY_PROJECTION_MODE = "XY Projection Mode";
   static final String ORTHOGONAL_PROJECTIONS = "Do Orthogonal Projections";
   static final String ORTHOGONAL_PROJECTIONS_MODE = "Orthogonal Projections Mode";
   static final String KEEP_ORIGINAL = "KeepOriginal";
   static final String GPU = "GPU";
   static final String MAX = "Max";
   static final String AVG = "Avg";
   static final String MODE = "Mode";
   static final String FAST = "Fast";
   static final String QUALITY = "Quality";
   static final String NR_THREADS = "NrThreads";
   static final String OUTPUT_OPTION = "Output Option";
   static final String OPTION_SINGLE_TIFF = "Option Single";
   static final String OPTION_MULTI_TIFF = "Option Multi";
   static final String OPTION_RAM = "Option RAM";
   static final String OPTION_REWRITABLE_RAM = "Option Rewritable RAM";
   static final String OUTPUT_PATH = "Output path";
   static final String SAVE_NAME = "Save name";
   static final String SHOW = "Show";

   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final CLIJ2 clij2_;

   /**
    * Generates the UI.
    *
    * @param configuratorSettings I am always confused about this propertymap
    * @param studio The Studio instance, usually a singleton.
    */
   public DeskewFrame(PropertyMap configuratorSettings, Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      copySettings(settings_, configuratorSettings);
      clij2_ = CLIJ2.getInstance();
      studio_.logs().logMessage(CLIJ2.clinfo());
      studio_.logs().logMessage(clij2_.getGPUName());

      initComponents();

      super.setLocation(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
   }

   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public void cleanup() {

   }

   @Override
   public PropertyMap getSettings() {
      return settings_.toPropertyMap();
   }

   private void initComponents() {
      super.setTitle(DIALOG_TITLE);
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("flowx"));

      JRadioButton fastButton = new JRadioButton("CPU (Fast)");
      fastButton.setSelected(settings_.getString(MODE, FAST).equals(FAST));
      fastButton.addActionListener(e -> settings_.putString(MODE, FAST));
      JRadioButton qualityButton = new JRadioButton("GPU (Quality)");
      qualityButton.addActionListener(e -> settings_.putString(MODE, QUALITY));
      final ButtonGroup bg = new ButtonGroup();
      bg.add(fastButton);
      bg.add(qualityButton);
      qualityButton.setSelected(settings_.getString(MODE, FAST).equals(QUALITY));
      add(fastButton, "span 4, split 3");
      add(new JLabel("Threads:"), "align right");
      JSpinner threadsSpinner = new JSpinner();
      threadsSpinner.setValue(settings_.getInteger(
               NR_THREADS, Runtime.getRuntime().availableProcessors()));
      threadsSpinner.addChangeListener(e -> {
         settings_.putInteger(NR_THREADS, (Integer) threadsSpinner.getValue());
      });
      add(threadsSpinner, "wrap");
      JComboBox<String> gpuComboBox = new JComboBox<>();
      for (String device : openCLDevices()) {
         gpuComboBox.addItem(device);
      }
      gpuComboBox.addActionListener(e -> {
         settings_.putString(GPU, (String) gpuComboBox.getSelectedItem());
      });
      add(qualityButton, "span 4, split 3, push");
      add(new JLabel("GPU:"), "align right");
      add(gpuComboBox, "wrap");
      add(new JSeparator(), "span 4, growx, wrap");

      add(new JLabel("Sheet angle (_\\) in radians:"), "alignx left");
      final JTextField thetaTextField = new JTextField(5);
      thetaTextField.setText(settings_.getString(THETA, "20"));
      thetaTextField.getDocument().addDocumentListener(
              new TextFieldUpdater(thetaTextField, THETA, 0.0, settings_));
      settings_.putString(THETA, thetaTextField.getText());
      add(thetaTextField, "wrap");

      add(createCheckBox(FULL_VOLUME, true), "span 2, wrap");
      add(createCheckBox(XY_PROJECTION, true), "span 2");
      List<JComponent> buttons =  projectionModeUI(XY_PROJECTION_MODE);
      add(buttons.get(0));
      add(buttons.get(1), "wrap");
      add(createCheckBox(ORTHOGONAL_PROJECTIONS, true), "span 2");
      buttons = projectionModeUI(ORTHOGONAL_PROJECTIONS_MODE);
      add(buttons.get(0));
      add(buttons.get(1), "wrap");
      add(createCheckBox(KEEP_ORIGINAL, true), "span 2, wrap");

      add(new JSeparator(), "span 5, growx, wrap");
      add(new JLabel("Output format:"), "spanx, alignx left, wrap");

      JRadioButton outputSingleplane = new JRadioButton("Separate Image Files");
      JRadioButton outputMultipage = new JRadioButton("Image Stack File");
      JRadioButton outputRam = new JRadioButton("Hold in RAM");
      JRadioButton outputRewritableRam = new JRadioButton("Live");
      final JCheckBox showDisplay = new JCheckBox("Show In New Window");
      ButtonGroup group = new ButtonGroup();
      group.add(outputSingleplane);
      group.add(outputMultipage);
      group.add(outputRam);
      group.add(outputRewritableRam);
      group.clearSelection();
      String selectedItem = settings_.getString(OUTPUT_OPTION, OPTION_RAM);
      switch (selectedItem) {
         case OPTION_SINGLE_TIFF:
            outputSingleplane.setSelected(true);
            break;
         case OPTION_MULTI_TIFF:
            outputMultipage.setSelected(true);
            break;
         case OPTION_RAM:
            outputRam.setSelected(true);
            break;
         case OPTION_REWRITABLE_RAM:
            outputRewritableRam.setSelected(true);
            break;
         default:
            break;
      }
      final JTextField outputPath = new JTextField(25);
      final JButton browseButton = new JButton("...");
      final JTextField outputName = new JTextField(15);
      ActionListener listener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (outputRam.isSelected()) {
               showDisplay.setSelected(true);
               settings_.putBoolean(SHOW, true);
               settings_.putString(OUTPUT_OPTION, OPTION_RAM);
            } else if (outputSingleplane.isSelected()) {
               settings_.putString(OUTPUT_OPTION, OPTION_SINGLE_TIFF);
            } else if (outputMultipage.isSelected()) {
               settings_.putString(OUTPUT_OPTION, OPTION_MULTI_TIFF);
            }
            outputPath.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
            browseButton.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
            outputName.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
         }
      };
      outputSingleplane.addActionListener(listener);
      outputMultipage.addActionListener(listener);
      outputRam.addActionListener(listener);
      outputRewritableRam.addActionListener(listener);
      add(outputSingleplane, "split, spanx");
      add(outputMultipage);
      add(outputRam);
      add(outputRewritableRam, "wrap");

      add(new JLabel("Save Directory: "));
      outputPath.setToolTipText("Directory that will contain the new saved data");
      outputPath.setText(settings_.getString(OUTPUT_PATH, ""));
      add(outputPath, "split, spanx");
      browseButton.setToolTipText("Browse for a directory to save to");
      browseButton.addActionListener(e -> {
         File result = FileDialogs.openDir(DeskewFrame.this,
                  "Please choose a directory to save to",
                  FileDialogs.MM_DATA_SET);
         if (result != null) {
            outputPath.setText(result.getAbsolutePath());
         }
      });
      add(browseButton, "wrap");

      add(new JLabel("Save Name: "));
      outputName.setToolTipText("Name to give to the processed data");
      outputName.setText(settings_.getString(SAVE_NAME, ""));
      add(outputName, "wrap");

      showDisplay.setToolTipText("Display the processed data in a new image window");
      showDisplay.setSelected(settings_.getBoolean(SHOW, true));
      add(showDisplay, "spanx, alignx right, wrap");

      pack();
   }

   private void copySettings(MutablePropertyMapView settings, PropertyMap configuratorSettings) {
      settings.putString(THETA, configuratorSettings.getString(THETA, "0"));
   }

   private JCheckBox createCheckBox(String key, boolean initialValue) {
      JCheckBox checkBox = new JCheckBox(key);
      checkBox.setSelected(settings_.getBoolean(key, initialValue));
      checkBox.addChangeListener(e -> settings_.putBoolean(key, checkBox.isSelected()));
      return checkBox;
   }

   private List<JComponent> projectionModeUI(String key) {
      JRadioButton max = new JRadioButton(MAX);
      max.setSelected(settings_.getString(key, MAX).equals(MAX));
      max.addChangeListener(e -> settings_.putString(key, max.isSelected() ? MAX : AVG));
      JRadioButton avg = new JRadioButton(AVG);
      avg.setSelected(settings_.getString(key, MAX).equals(AVG));
      avg.addChangeListener(e -> settings_.putString(key, avg.isSelected() ? AVG : MAX));
      final ButtonGroup bg = new ButtonGroup();
      bg.add(max);
      bg.add(avg);
      List<JComponent> result = new ArrayList<>();
      result.add(max);
      result.add(avg);
      return result;

   }

   private List<String> openCLDevices() {
      List<String> result = new ArrayList<>();
      ClearCLBackendInterface lClearCLBackend = ClearCLBackends.getBestBackend();
      ClearCL lClearCL = new ClearCL(lClearCLBackend);
      for (ClearCLDevice lDevice : lClearCL.getAllDevices()) {
         result.add(lDevice.getName());
      }
      return result;
   }


   private class TextFieldUpdater implements DocumentListener {

      private final JTextField field_;
      private final String key_;
      private final MutablePropertyMapView settings_;
      private final Object type_;

      public TextFieldUpdater(JTextField field, String key, Object type,
                              MutablePropertyMapView settings) {
         field_ = field;
         key_ = key;
         settings_ = settings;
         type_ = type;
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
         processEvent();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
         processEvent();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
         processEvent();
      }

      private void processEvent() {
         if (type_ instanceof Double) {
            try {
               double factor = NumberUtils.displayStringToDouble(field_.getText());
               settings_.putString(key_, NumberUtils.doubleToDisplayString(factor));
            } catch (ParseException p) {
               studio_.logs().logError("Error parsing number in DeskewFrame.");
            }
         }
      }
   }

}