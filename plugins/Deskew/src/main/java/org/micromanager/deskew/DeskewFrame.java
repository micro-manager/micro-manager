package org.micromanager.deskew;

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
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
import org.micromanager.acquisition.AcquisitionSettingsChangedEvent;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
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
   static final String DEGREE = "Degree";
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
   static final String SHOW = "Show";
   static final String SYNC_WITH_MDA = "Sync with MDA";
   public static final String EXPLORE_MODE = "ExploreMode";
   private final Studio studio_;
   private final DeskewFactory deskewFactory_;
   private final MutablePropertyMapView settings_;
   private final CLIJ2 clij2_;
   private final DeskewExploreManager exploreManager_;
   private JComboBox<String> input_;
   private JRadioButton outputSingleplane_;
   private JRadioButton outputMultipage_;
   private JRadioButton outputRam_;
   private JRadioButton outputRewritableRam_;
   private JCheckBox keepOriginal_;
   private JCheckBox showDisplay_;
   private JTextField outputPath_;
   private JButton browseButton_;
   private JButton copyDirButton_;
   private boolean eventsRegistered_ = false;

   /**
    * Generates the UI.
    *
    * @param configuratorSettings I am always confused about this propertymap
    * @param studio The Studio instance, usually a singleton.
    */
   public DeskewFrame(PropertyMap configuratorSettings, Studio studio,
                      DeskewFactory deskewFactory) {
      studio_ = studio;
      deskewFactory_ = deskewFactory;
      settings_ = studio_.profile().getSettings(this.getClass());
      clij2_ = CLIJ2.getInstance();
      studio_.logs().logMessage(CLIJ2.clinfo());
      studio_.logs().logMessage(clij2_.getGPUName());
      exploreManager_ = new DeskewExploreManager(studio, this, deskewFactory);

      initComponents();

      studio_.displays().registerForEvents(this);

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

   /**
    * Returns the mutable settings view for direct modification.
    * Used by DeskewExploreManager to temporarily set explore mode.
    *
    * @return The mutable property map view
    */
   MutablePropertyMapView getMutableSettings() {
      return settings_;
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
      threadsSpinner.setMinimumSize(new Dimension(50, 12));
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
      String selectedGPU = "";
      if (gpuComboBox.getItemCount() > 0) {
         selectedGPU = (String) gpuComboBox.getSelectedItem();
         String savedGPU = settings_.getString(GPU, selectedGPU);
         // Only set if the saved value exists in the combo box items
         boolean found = false;
         for (int i = 0; i < gpuComboBox.getItemCount(); i++) {
            if (gpuComboBox.getItemAt(i).equals(savedGPU)) {
               found = true;
               break;
            }
         }
         if (found) {
            gpuComboBox.setSelectedItem(savedGPU);
         }
         // else: leave the default selection unchanged
      }
      gpuComboBox.addActionListener(e -> {
         settings_.putString(GPU, (String) gpuComboBox.getSelectedItem());
      });
      add(qualityButton, "span 4, split 3, push");
      add(new JLabel("GPU:"), "align right");
      add(gpuComboBox, "wrap");
      add(new JSeparator(), "span 4, growx, wrap");

      add(new JLabel("Sheet angle dy/dz (_\\) in degrees:"), "alignx left");
      final JTextField degreeTextField = new JTextField(5);
      // fetch tan(theta) from settings, convert to degrees and display
      // if tan(theta) is 0, default to previously set value or 20 degrees if not set.
      String defaultAngle = "20";
      String displayAngle = settings_.getString(DEGREE, defaultAngle);
      try {
         double tanAngle = studio_.core().getPixelSizedydz(true);
         double angle = Math.toDegrees(Math.atan(tanAngle));
         if (angle == 0.0) {
            throw new Exception("Angle in configuration is 0.0");
         }
         displayAngle = NumberUtils.doubleToDisplayString(angle);
      } catch (Exception ex) {
         studio_.logs().logError("Error fetching dy/dz from core. Using " + displayAngle
                  + " degrees.");
      }
      degreeTextField.setText(displayAngle);
      degreeTextField.getDocument().addDocumentListener(
              new TextFieldUpdater(degreeTextField, DEGREE, 0.0, settings_));
      settings_.putString(DEGREE, degreeTextField.getText());
      add(degreeTextField, "wrap");

      add(createCheckBox(FULL_VOLUME, true), "span 2, wrap");
      add(createCheckBox(XY_PROJECTION, true), "span 2");
      List<JComponent> buttons =  projectionModeUI(XY_PROJECTION_MODE);
      add(buttons.get(0));
      add(buttons.get(1), "wrap");
      add(createCheckBox(ORTHOGONAL_PROJECTIONS, true), "span 2");
      buttons = projectionModeUI(ORTHOGONAL_PROJECTIONS_MODE);
      add(buttons.get(0));
      add(buttons.get(1), "wrap");

      add(new JSeparator(), "span 5, growx, wrap");
      add(new JLabel("Output format:"), "span 4, alignx left");
      final JCheckBox syncWithMDA =
            createCheckBox(SYNC_WITH_MDA, true);
      add(syncWithMDA, "alignx right, wrap");

      outputSingleplane_ = new JRadioButton("Separate Image Files");
      outputMultipage_ = new JRadioButton("Image Stack File");
      outputRam_ = new JRadioButton("Hold in RAM");
      outputRewritableRam_ = new JRadioButton("Live");
      showDisplay_ = new JCheckBox(SHOW);
      keepOriginal_ = new JCheckBox("Keep Original Images");
      ButtonGroup group = new ButtonGroup();
      group.add(outputSingleplane_);
      group.add(outputMultipage_);
      group.add(outputRam_);
      group.add(outputRewritableRam_);
      group.clearSelection();
      String selectedItem = settings_.getString(OUTPUT_OPTION, OPTION_RAM);
      switch (selectedItem) {
         case OPTION_SINGLE_TIFF:
            outputSingleplane_.setSelected(true);
            break;
         case OPTION_MULTI_TIFF:
            outputMultipage_.setSelected(true);
            break;
         case OPTION_RAM:
            outputRam_.setSelected(true);
            break;
         case OPTION_REWRITABLE_RAM:
            outputRewritableRam_.setSelected(true);
            break;
         default:
            break;
      }
      outputPath_ = new JTextField(25);
      browseButton_ = new JButton("...");
      copyDirButton_ = new JButton("from MDA");
      final ActionListener listener = e -> {
         updateDisplayControls();
      };

      outputPath_.setEnabled(!outputRam_.isSelected() && !outputRewritableRam_.isSelected());
      browseButton_.setEnabled(!outputRam_.isSelected() && !outputRewritableRam_.isSelected());
      copyDirButton_.setEnabled(!outputRam_.isSelected() && !outputRewritableRam_.isSelected());
      outputSingleplane_.addActionListener(listener);
      outputMultipage_.addActionListener(listener);
      outputRam_.addActionListener(listener);
      outputRewritableRam_.addActionListener(listener);
      add(outputSingleplane_, "split, spanx");
      add(outputMultipage_);
      add(outputRam_);
      add(outputRewritableRam_, "wrap");

      add(new JLabel("Save Directory: "), "split, spanx");
      outputPath_.setToolTipText("Directory that will contain the new saved data");
      outputPath_.setText(settings_.getString(OUTPUT_PATH, ""));
      outputPath_.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.putString(OUTPUT_PATH, outputPath_.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.putString(OUTPUT_PATH, outputPath_.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.putString(OUTPUT_PATH, outputPath_.getText());
         }
      });
      add(outputPath_, "growx");
      copyDirButton_.setToolTipText("Copy directory root from MDA Window");
      copyDirButton_.addActionListener(e -> {
         String path = studio_.acquisitions().getAcquisitionSettings().root();
         if (path != null && !path.isEmpty()) {
            outputPath_.setText(path);
            settings_.putString(OUTPUT_PATH, path);
         } else {
            studio_.logs().showError("No MDA directory set. Please run an MDA first.");
         }
      });
      add(copyDirButton_);

      browseButton_.setToolTipText("Browse for a directory to save to");
      browseButton_.addActionListener(e -> {
         File result = FileDialogs.openDir(DeskewFrame.this,
                  "Please choose a directory to save to",
                  FileDialogs.MM_DATA_SET);
         if (result != null) {
            outputPath_.setText(result.getAbsolutePath());
            settings_.putString(OUTPUT_PATH, result.getAbsolutePath());
         }
      });
      add(browseButton_, "wrap");

      keepOriginal_.setToolTipText("Keep the original images in the output dataset");
      keepOriginal_.setSelected(settings_.getBoolean(KEEP_ORIGINAL, true));
      keepOriginal_.addActionListener(e -> {
         settings_.putBoolean(KEEP_ORIGINAL, keepOriginal_.isSelected());
      });
      add(keepOriginal_);

      showDisplay_.setToolTipText("Display the processed data in a new image window");
      showDisplay_.setSelected(settings_.getBoolean(SHOW, true));
      add(showDisplay_, "spanx, alignx right, wrap");
      showDisplay_.addActionListener(e -> {
         settings_.putBoolean(SHOW, showDisplay_.isSelected());
      });

      syncWithMDA.addActionListener(e -> {
         settings_.putBoolean(SYNC_WITH_MDA, syncWithMDA.isSelected());
         manageMDASync(syncWithMDA.isSelected());
      });
      syncWithMDA.setSelected(settings_.getBoolean(SYNC_WITH_MDA, false));
      if (syncWithMDA.isSelected()) {
         updateUIBasedOnAcquisitionSettings(studio_.acquisitions().getAcquisitionSettings());
      }
      manageMDASync(syncWithMDA.isSelected());

      add(new JSeparator(), "span 5, growx, wrap");

      JButton processButton = new JButton("Process");
      processButton.addActionListener(e -> new Thread(this::processData).start());
      add(processButton, "split, spanx");
      // Users can choose between open Datastores (named based on their
      // displays) or loading a file from disk.
      input_ = new JComboBox<>();
      refreshInputOptions();
      add(input_, "wrap");

      // Explore Section
      add(new JSeparator(), "span 5, growx, wrap");
      JPanel explorePanel = new JPanel(new MigLayout("insets 4"));
      explorePanel.setBorder(BorderFactory.createTitledBorder("Explore"));
      JButton startExploreButton = new JButton("Start");
      startExploreButton.setToolTipText(
              "Start explore mode with tiled NDViewer. Click tiles to acquire deskewed projections.");
      startExploreButton.addActionListener(e -> startExplore());
      explorePanel.add(startExploreButton);
      add(explorePanel, "span, growx, wrap");

      pack();
   }

   /**
    * This is called when the acquisition settings change, e.g. when the
    * user changes the MDA directory.
    *
    * @param event The event containing the new acquisition settings.
    */
   @Subscribe
   public void onAcquisitionSettingsChanged(AcquisitionSettingsChangedEvent event) {
      updateUIBasedOnAcquisitionSettings(event.getNewSettings());
   }

   private void updateUIBasedOnAcquisitionSettings(SequenceSettings sequenceSettings) {
      String newRoot = sequenceSettings.root();
      if (newRoot != null) {
         outputPath_.setText(newRoot);
         settings_.putString(OUTPUT_PATH, newRoot);
      }
      if (sequenceSettings.save()) {
         switch (sequenceSettings.saveMode()) {
            case SINGLEPLANE_TIFF_SERIES:
               outputSingleplane_.setSelected(true);
               settings_.putString(OUTPUT_OPTION, OPTION_SINGLE_TIFF);
               break;
            case MULTIPAGE_TIFF:
               outputMultipage_.setSelected(true);
               settings_.putString(OUTPUT_OPTION, OPTION_MULTI_TIFF);
               break;
            default:
               outputRam_.setSelected(true);
               settings_.putString(OUTPUT_OPTION, OPTION_RAM);
               showDisplay_.setSelected(true);
               break;
         }
      } else {
         outputRam_.setSelected(true);
         settings_.putString(OUTPUT_OPTION, OPTION_RAM);
         showDisplay_.setSelected(true);
      }
   }

   private void updateDisplayControls() {
      if (outputRam_.isSelected()) {
         showDisplay_.setSelected(true);
         settings_.putBoolean(SHOW, true);
         settings_.putString(OUTPUT_OPTION, OPTION_RAM);
      } else if (outputRewritableRam_.isSelected()) {
         showDisplay_.setSelected(true);
         settings_.putBoolean(SHOW, true);
         settings_.putString(OUTPUT_OPTION, OPTION_REWRITABLE_RAM);
      } else if (outputSingleplane_.isSelected()) {
         settings_.putString(OUTPUT_OPTION, OPTION_SINGLE_TIFF);
      } else if (outputMultipage_.isSelected()) {
         settings_.putString(OUTPUT_OPTION, OPTION_MULTI_TIFF);
      }
      outputPath_.setEnabled(!outputRam_.isSelected() && !outputRewritableRam_.isSelected());
      browseButton_.setEnabled(!outputRam_.isSelected() && !outputRewritableRam_.isSelected());
      copyDirButton_.setEnabled(!outputRam_.isSelected() && !outputRewritableRam_.isSelected());
   }

   private void manageMDASync(boolean syncEnabled) {
      if (syncEnabled) {
         if (!eventsRegistered_) {
            eventsRegistered_ = true;
            studio_.events().registerForEvents(this);
         }
         outputPath_.setEnabled(false);
         browseButton_.setEnabled(false);
         copyDirButton_.setEnabled(false);
         outputMultipage_.setEnabled(false);
         outputSingleplane_.setEnabled(false);
         outputRam_.setEnabled(false);
         outputRewritableRam_.setEnabled(false);
      } else {
         if (eventsRegistered_) {
            studio_.events().unregisterForEvents(this);
            eventsRegistered_ = false;
         }
         outputPath_.setEnabled(true);
         browseButton_.setEnabled(true);
         copyDirButton_.setEnabled(true);
         outputMultipage_.setEnabled(true);
         outputSingleplane_.setEnabled(true);
         outputRam_.setEnabled(true);
         outputRewritableRam_.setEnabled(true);
         updateDisplayControls();
      }
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

   private void startExplore() {
      exploreManager_.startExplore();
   }

   @Subscribe
   public void onDisplayAboutToShow(DataViewerAddedEvent event) {
      SwingUtilities.invokeLater(() -> refreshInputOptions());
   }

   @Subscribe
   public void onDisplayDestroyed(DataViewerWillCloseEvent event) {
      SwingUtilities.invokeLater(() -> refreshInputOptions());
   }

   /**
    * Rebuild the options in the input_ dropdown menu.
    */
   private void refreshInputOptions() {
      String curSelection = null;
      if (input_.getItemCount() > 0) {
         curSelection = (String) (input_.getSelectedItem());
      }
      input_.removeAllItems();
      // Don't add the same datastore twice (because it has multiple open
      // windows).
      HashSet<DataProvider> addedProviders = new HashSet<>();
      for (DisplayWindow display : studio_.displays().getAllImageWindows()) {
         if (!addedProviders.contains(display.getDataProvider())) {
            input_.addItem(display.getName());
            addedProviders.add(display.getDataProvider());
         }
      }
      if (studio_.displays().getActiveDataViewer() != null) {
         input_.setSelectedItem(studio_.displays().getActiveDataViewer().getName());
      }
      if (curSelection != null) {
         input_.setSelectedItem(curSelection);
      }
   }

   private void processData() {
      Datastore source = null;
      String input = (String) (input_.getSelectedItem());
      // Find the display with matching name.
      for (DisplayWindow display : studio_.displays().getAllImageWindows()) {
         if (display.getName().contentEquals(input)) {
            if (display.getDataProvider() instanceof Datastore) {
               source = (Datastore) display.getDataProvider();
            }
            break;
         }
      }

      // All inputs validated; time to process data.
      if (source == null) {
         studio_.logs().showError("The data source named " + input + " is no longer available.");
         return;
      }

      ProgressMonitor monitor = new ProgressMonitor(this,
               "Processing images...                                    \t",
                "", 0, source.getNumImages());

      final Datastore destination = studio_.data().createRAMDatastore();
      final List<ProcessorFactory> factories = new ArrayList<>();

      // Elaborate way to deal with KeepOriginal when processing existing data.
      // We do not want to reproduce the originals, but we want to keep the
      // KEEP_ORIGINAL setting in the settings_ object.
      boolean keepOriginal = settings_.getBoolean(KEEP_ORIGINAL, false);
      settings_.putBoolean(KEEP_ORIGINAL, false);
      deskewFactory_.setSettings(settings_.toPropertyMap());
      settings_.putBoolean(KEEP_ORIGINAL, keepOriginal);
      factories.add(deskewFactory_);
      Pipeline pipeline = studio_.data().createPipeline(factories, destination, true);
      try {
         pipeline.insertSummaryMetadata(source.getSummaryMetadata());
         Iterable<Coords> unorderedImageCoords = source.getUnorderedImageCoords();
         List<Coords> orderedImageCoords = new ArrayList<>();
         for (Coords c : unorderedImageCoords) {
            orderedImageCoords.add(c);
         }
         final List<String> axisOrder = source.getSummaryMetadata().getOrderedAxes();
         // We need to ensure that the z axis is last:
         if (!axisOrder.get(axisOrder.size() - 1).equals(Coords.Z)) {
            if (axisOrder.get(0).equals(Coords.Z)) {
               Collections.reverse(axisOrder);
            } else {
               if (axisOrder.contains(Coords.Z)) {
                  int zIndex = axisOrder.indexOf(Coords.Z);
                  // Move Z to the end of the axis order
                  axisOrder.remove(zIndex);
                  // Always add Z to the end of the axis order after removing it
                  axisOrder.add(Coords.Z);
               }
            }
         }

         orderedImageCoords.sort((Coords o1, Coords o2) -> {
            for (String axis : axisOrder) {
               if (o1.getIndex(axis) < o2.getIndex(axis)) {
                  return -1;
               } else if (o1.getIndex(axis) > o2.getIndex(axis)) {
                  return 1;
               }
            }
            return 0;
         });

         int i = 0;
         for (Coords c : orderedImageCoords) {
            i++;
            monitor.setProgress(i);
            monitor.setNote("Processing image " + c.toString());
            pipeline.insertImage(source.getImage(c));
            if (monitor.isCanceled()) {
               break;
            }
         }
      } catch (DatastoreFrozenException e) {
         // This should be impossible!
         studio_.logs().showError(e, "Error processing data: datastore is frozen.");
      } catch (DatastoreRewriteException e) {
         // Indicates a fault in one of the Processors in the pipeline.
         studio_.logs().showError(e,
                  "Error processing data: can not overwrite existing image "
                  + "in destination dataset.");
      } catch (PipelineErrorException e) {
         studio_.logs().showError("Error processing data:" + pipeline.getExceptions());
      } catch (IOException e) {
         studio_.logs().showError(e, "Error saving data");
      }
      monitor.close();
      pipeline.halt();
      try {
         destination.freeze();
      } catch (IOException e) {
         studio_.logs().showError(e, "Error saving data");
      }
   }

}