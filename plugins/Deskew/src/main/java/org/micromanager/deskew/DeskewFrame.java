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
import org.micromanager.internal.dialogs.AcqControlDlg;
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
   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final CLIJ2 clij2_;
   private JComboBox<String> input_;

   /**
    * Generates the UI.
    *
    * @param configuratorSettings I am always confused about this propertymap
    * @param studio The Studio instance, usually a singleton.
    */
   public DeskewFrame(PropertyMap configuratorSettings, Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      clij2_ = CLIJ2.getInstance();
      studio_.logs().logMessage(CLIJ2.clinfo());
      studio_.logs().logMessage(clij2_.getGPUName());

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
      settings_.putBoolean(KEEP_ORIGINAL, true);
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
      final JButton copyDirButton = new JButton("from MDA");
      final JTextField outputName = new JTextField(15);
      final ActionListener listener = e -> {
         if (outputRam.isSelected()) {
            showDisplay.setSelected(true);
            settings_.putBoolean(SHOW, true);
            settings_.putString(OUTPUT_OPTION, OPTION_RAM);
         } else if (outputRewritableRam.isSelected()) {
            showDisplay.setSelected(true);
            settings_.putBoolean(SHOW, true);
            settings_.putString(OUTPUT_OPTION, OPTION_REWRITABLE_RAM);
         } else if (outputSingleplane.isSelected()) {
            settings_.putString(OUTPUT_OPTION, OPTION_SINGLE_TIFF);
         } else if (outputMultipage.isSelected()) {
            settings_.putString(OUTPUT_OPTION, OPTION_MULTI_TIFF);
         }
         outputPath.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
         browseButton.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
         copyDirButton.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
         outputName.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
      };

      outputPath.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
      browseButton.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
      copyDirButton.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
      outputName.setEnabled(!outputRam.isSelected() && !outputRewritableRam.isSelected());
      outputSingleplane.addActionListener(listener);
      outputMultipage.addActionListener(listener);
      outputRam.addActionListener(listener);
      outputRewritableRam.addActionListener(listener);
      add(outputSingleplane, "split, spanx");
      add(outputMultipage);
      add(outputRam);
      add(outputRewritableRam, "wrap");

      add(new JLabel("Save Directory: "), "split, spanx");
      outputPath.setToolTipText("Directory that will contain the new saved data");
      outputPath.setText(settings_.getString(OUTPUT_PATH, ""));
      outputPath.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.putString(OUTPUT_PATH, outputPath.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.putString(OUTPUT_PATH, outputPath.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.putString(OUTPUT_PATH, outputPath.getText());
         }
      });
      add(outputPath, "growx");
      copyDirButton.setToolTipText("Copy directory root from MDA Window");
      copyDirButton.addActionListener(e -> {
         String path = studio_.acquisitions().getAcquisitionSettings().root();
         if (path != null && !path.isEmpty()) {
            outputPath.setText(path);
            settings_.putString(OUTPUT_PATH, path);
         } else {
            studio_.logs().showError("No MDA directory set. Please run an MDA first.");
         }
      });
      add(copyDirButton);

      browseButton.setToolTipText("Browse for a directory to save to");
      browseButton.addActionListener(e -> {
         File result = FileDialogs.openDir(DeskewFrame.this,
                  "Please choose a directory to save to",
                  FileDialogs.MM_DATA_SET);
         if (result != null) {
            outputPath.setText(result.getAbsolutePath());
            settings_.putString(OUTPUT_PATH, result.getAbsolutePath());
         }
      });
      add(browseButton, "wrap");

      showDisplay.setToolTipText("Display the processed data in a new image window");
      showDisplay.setSelected(settings_.getBoolean(SHOW, true));
      add(showDisplay, "spanx, alignx right, wrap");
      showDisplay.addActionListener(e -> {
         settings_.putBoolean(SHOW, showDisplay.isSelected());
      });

      add(new JSeparator(), "span 5, growx, wrap");

      JButton processButton = new JButton("Process");
      processButton.addActionListener(e -> new Thread(this::processData).start());
      add(processButton, "split, spanx");
      // Users can choose between open Datastores (named based on their
      // displays) or loading a file from disk.
      input_ = new JComboBox<>();
      refreshInputOptions();
      add(input_, "wrap");


      pack();
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

      Datastore destination = studio_.data().createRAMDatastore();
      List<ProcessorFactory> factories = new ArrayList<>();

      settings_.putBoolean(KEEP_ORIGINAL, false);
      factories.add(new DeskewFactory(studio_, settings_.toPropertyMap()));
      Pipeline pipeline = studio_.data().createPipeline(factories, destination, true);
      try {
         pipeline.insertSummaryMetadata(source.getSummaryMetadata());
         Iterable<Coords> unorderedImageCoords = source.getUnorderedImageCoords();
         List<Coords> orderedImageCoords = new ArrayList<>();
         for (Coords c : unorderedImageCoords) {
            orderedImageCoords.add(c);
         }
         final List<String> axisOrder = source.getSummaryMetadata().getOrderedAxes();
         Collections.reverse(axisOrder);

         Collections.sort(orderedImageCoords, new Comparator<Coords>() {
            @Override
            public int compare(Coords o1, Coords o2) {
               for (String axis : axisOrder) {
                  if (o1.getIndex(axis)  < o2.getIndex(axis)) {
                     return -1;
                  }  else if (o1.getIndex(axis) > o2.getIndex(axis)) {
                     return 1;
                  }
               }
               return 0;
            }
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