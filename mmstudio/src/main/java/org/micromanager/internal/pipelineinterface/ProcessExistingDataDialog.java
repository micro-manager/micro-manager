///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
// AUTHOR:        Mark Tsuchida, Chris Weisiger
// COPYRIGHT:     (c) 2016 Open Imaging, Inc
// LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.pipelineinterface;

import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.*;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/** This class allows users to process files that already exist on disk. */
public final class ProcessExistingDataDialog extends JDialog {
  private static final String LOAD_FROM_DISK = "Load From Disk...";
  private static final String OUTPUT_OPTION = "Output Option";
  private static final String OPTION_SINGLE_TIFF = "Option Single";
  private static final String OPTION_MULTI_TIFF = "Option Multi";
  private static final String OPTION_RAM = "Option RAM";
  private static final String OUTPUT_PATH = "Output path";
  private static final String SAVE_NAME = "Save name";
  private static final String SHOW = "Show";

  public static void makeDialog(Studio studio) {
    ProcessExistingDataDialog dialog = new ProcessExistingDataDialog(studio);
    studio.events().registerForEvents(dialog);
  }

  private final Studio studio_;
  private final JComboBox input_;
  private final JRadioButton outputSingleplane_;
  private final JRadioButton outputMultipage_;
  private JRadioButton outputRam_;
  private JTextField outputPath_;
  private JTextField outputName_;
  private JButton browseButton_;
  private final JCheckBox showDisplay_;
  private final MutablePropertyMapView settings_;

  private ProcessExistingDataDialog(Studio studio) {
    super();
    super.setTitle("Process Existing Data");
    studio_ = studio;
    settings_ = studio_.getUserProfile().getSettings(this.getClass());

    JPanel contents = new JPanel(new MigLayout("insets dialog"));
    contents.add(
        new JLabel(
            "<html>This dialog allows you to process an existing dataset, either<br>one that is currently loaded or one that is saved to disk.</html>"),
        "spanx, wrap");

    contents.add(new JLabel("Input Data:"), "split, spanx");
    // Users can choose between open Datastores (named based on their
    // displays) or loading a file from disk.
    input_ = new JComboBox();
    refreshInputOptions();
    contents.add(input_, "wrap");

    contents.add(new JLabel("Output format:"), "spanx, alignx left, wrap");
    outputSingleplane_ = new JRadioButton("Separate Image Files");
    outputMultipage_ = new JRadioButton("Image Stack File");
    outputRam_ = new JRadioButton("Hold in RAM");
    showDisplay_ = new JCheckBox("Show In New Window");
    ButtonGroup group = new ButtonGroup();
    group.add(outputSingleplane_);
    group.add(outputMultipage_);
    group.add(outputRam_);
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
      default:
        break;
    }
    ActionListener listener =
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (outputRam_.isSelected()) {
              showDisplay_.setSelected(true);
              settings_.putBoolean(SHOW, true);
              settings_.putString(OUTPUT_OPTION, OPTION_RAM);
            } else if (outputSingleplane_.isSelected()) {
              settings_.putString(OUTPUT_OPTION, OPTION_SINGLE_TIFF);
            } else if (outputMultipage_.isSelected()) {
              settings_.putString(OUTPUT_OPTION, OPTION_MULTI_TIFF);
            }
            outputPath_.setEnabled(!outputRam_.isSelected());
            browseButton_.setEnabled(!outputRam_.isSelected());
            outputName_.setEnabled(!outputRam_.isSelected());
          }
        };
    outputSingleplane_.addActionListener(listener);
    outputMultipage_.addActionListener(listener);
    outputRam_.addActionListener(listener);
    contents.add(outputSingleplane_, "split, spanx");
    contents.add(outputMultipage_);
    contents.add(outputRam_, "wrap");

    contents.add(new JLabel("Save Directory: "));
    outputPath_ = new JTextField(25);
    outputPath_.setToolTipText("Directory that will contain the new saved data");
    outputPath_.setText(settings_.getString(OUTPUT_PATH, ""));
    contents.add(outputPath_, "split, spanx");
    browseButton_ = new JButton("...");
    browseButton_.setToolTipText("Browse for a directory to save to");
    browseButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File result =
                FileDialogs.openDir(
                    ProcessExistingDataDialog.this,
                    "Please choose a directory to save to",
                    FileDialogs.MM_DATA_SET);
            if (result != null) {
              outputPath_.setText(result.getAbsolutePath());
            }
          }
        });
    contents.add(browseButton_, "wrap");

    contents.add(new JLabel("Save Name: "));
    outputName_ = new JTextField(15);
    outputName_.setToolTipText("Name to give to the processed data");
    outputName_.setText(settings_.getString(SAVE_NAME, ""));
    contents.add(outputName_, "wrap");

    showDisplay_.setToolTipText("Display the processed data in a new image window");
    showDisplay_.setSelected(settings_.getBoolean(SHOW, true));
    contents.add(showDisplay_, "spanx, alignx right, wrap");

    JButton okButton = new JButton("OK");
    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            new Thread(
                    new Runnable() {
                      @Override
                      public void run() {
                        processData();
                      }
                    })
                .start();
          }
        });

    JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dispose();
          }
        });

    if (JavaUtils.isMac()) {
      contents.add(cancelButton, "split 2, align right, flowx");
      contents.add(okButton);
    } else {
      contents.add(okButton, "split 2, align right, flowx");
      contents.add(cancelButton);
    }

    super.add(contents);
    super.pack();
    super.setIconImage(
        Toolkit.getDefaultToolkit()
            .getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
    super.setLocation(200, 200);
    WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
    super.setVisible(true);
  }

  @Override
  public void dispose() {
    settings_.putString(OUTPUT_PATH, outputPath_.getText());
    settings_.putString(SAVE_NAME, outputName_.getText());
    settings_.putBoolean(SHOW, showDisplay_.isSelected());
    studio_.events().unregisterForEvents(this);
    super.dispose();
  }

  // TODO
  /*
  @Subscribe
  public void onDisplayAboutToShow(DisplayAboutToShowEvent event) {
     // HACK: wait until DisplayManager knows about the display. Otherwise
     // refreshInputOptions() won't pick up the new display.
     SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
           refreshInputOptions();
        }
     });
  }
  */

  // TODO
  /*
  @Subscribe
  public void onDisplayDestroyed(GlobalDisplayDestroyedEvent event) {
     // HACK: wait until DisplayManager knows about the display. Otherwise
     // refreshInputOptions() will still have the old display.
     SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
           refreshInputOptions();
        }
     });
  }
  */

  /** Rebuild the options in the input_ dropdown menu. */
  private void refreshInputOptions() {
    String curSelection = null;
    if (input_.getItemCount() > 0) {
      curSelection = (String) (input_.getSelectedItem());
    }
    input_.removeAllItems();
    // Don't add the same datastore twice (because it has multiple open
    // windows).
    HashSet<DataProvider> addedProviders = new HashSet<DataProvider>();
    for (DisplayWindow display : studio_.displays().getAllImageWindows()) {
      if (!addedProviders.contains(display.getDataProvider())) {
        input_.addItem(display.getName());
        addedProviders.add(display.getDataProvider());
      }
    }
    input_.addItem(LOAD_FROM_DISK);
    if (studio_.displays().getActiveDataViewer() != null) {
      input_.setSelectedItem(studio_.displays().getActiveDataViewer().getName());
    }
    if (curSelection != null) {
      input_.setSelectedItem(curSelection);
    }
  }

  private void processData() {
    Datastore destination;
    if (outputRam_.isSelected()) {
      destination = studio_.data().createRAMDatastore();
    } else {
      String path = outputPath_.getText();
      if (path.contentEquals("")) {
        studio_.logs().showError("Please choose a location to save data to.");
        return;
      }
      path += "/" + outputName_.getText();
      try {
        if (outputSingleplane_.isSelected()) {
          destination = studio_.data().createSinglePlaneTIFFSeriesDatastore(path);
        } else {
          // TODO: we should imitate the source dataset when possible for
          // deciding whether to generate metadata.txt and whether to
          // split positions.
          destination = studio_.data().createMultipageTIFFDatastore(path, true, true);
        }
      } catch (IOException e) {
        studio_.logs().showError(e, "Unable to open " + path + " for writing");
        return;
      }
    }

    Datastore source = null;
    String input = (String) (input_.getSelectedItem());
    if (input.contentEquals(LOAD_FROM_DISK)) {
      try {
        source = studio_.data().promptForDataToLoad(this, true);
      } catch (IOException e) {
        studio_.logs().showError(e, "Error loading data");
        return;
      }
    } else {
      // Find the display with matching name.
      for (DisplayWindow display : studio_.displays().getAllImageWindows()) {
        if (display.getName().contentEquals(input)) {
          if (display.getDataProvider() instanceof Datastore) {
            source = (Datastore) display.getDataProvider();
          }
          break;
        }
      }
    }

    // All inputs validated; time to process data.
    dispose();

    if (source == null) {
      studio_.logs().showError("The data source named " + input + " is no longer available.");
      return;
    }

    if (showDisplay_.isSelected()) {
      destination.setName(source.getName() + "-Processed");
      studio_.displays().manage(destination);
      studio_.displays().createDisplay(destination);
    }

    ProgressMonitor monitor =
        new ProgressMonitor(this, "Processing images...", "", 0, source.getNumImages());

    Pipeline pipeline = studio_.data().copyApplicationPipeline(destination, false);
    try {
      pipeline.insertSummaryMetadata(source.getSummaryMetadata());
      int i = 0;
      // HACK: using arbitrary order to replay images, as the axisOrder
      // metadata property cannot be relied on (c.f. issue #151).
      for (Coords c : source.getUnorderedImageCoords()) {
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
      studio_
          .logs()
          .showError(
              e,
              "Error processing data: attempt to overwrite existing image in destination dataset.");
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
