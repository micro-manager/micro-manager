///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//AUTHOR:        Mark Tsuchida, Chris Weisiger
//COPYRIGHT:     (c) 2016 Open Imaging, Inc
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.pipelineinterface;

import com.google.common.eventbus.Subscribe;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.display.DisplayWindow;
// import org.micromanager.events.DisplayAboutToShowEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MMDialog;

/**
 * This class allows users to process files that already exist on disk.
 */
public final class ReplayDialog extends MMDialog {
   private static final String LOAD_FROM_DISK = "Load From Disk...";

   public static void makeDialog(Studio studio) {
      ReplayDialog dialog = new ReplayDialog(studio);
      studio.events().registerForEvents(dialog);
   }

   private Studio studio_;
   private JComboBox input_;
   private JRadioButton outputSingleplane_;
   private JRadioButton outputMultipage_;
   private JRadioButton outputRam_;
   private JTextField outputPath_;
   private JTextField outputName_;
   private JButton browseButton_;
   private JCheckBox showDisplay_;

   private ReplayDialog(Studio studio) {
      super("Process Existing Data");
      setTitle("Process Existing Data");
      studio_ = studio;

      JPanel contents = new JPanel(new MigLayout("insets dialog"));
      contents.add(new JLabel("<html>This dialog allows you to process an existing dataset, either<br>one that is currently loaded or one that is saved to disk.</html>"),
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
      ButtonGroup group = new ButtonGroup();
      group.add(outputSingleplane_);
      group.add(outputMultipage_);
      group.add(outputRam_);
      ActionListener listener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean amUsingFile = !outputRam_.isSelected();
            outputPath_.setEnabled(amUsingFile);
            browseButton_.setEnabled(amUsingFile);
            outputName_.setEnabled(amUsingFile);
         }
      };
      outputSingleplane_.addActionListener(listener);
      outputMultipage_.addActionListener(listener);
      outputRam_.addActionListener(listener);
      outputMultipage_.setSelected(true);
      contents.add(outputSingleplane_, "split, spanx");
      contents.add(outputMultipage_);
      contents.add(outputRam_, "wrap");

      contents.add(new JLabel("Save Directory: "));
      outputPath_ = new JTextField(20);
      outputPath_.setToolTipText("Directory that will contain the new saved data");
      contents.add(outputPath_, "split, spanx");
      browseButton_ = new JButton("...");
      browseButton_.setToolTipText("Browse for a directory to save to");
      browseButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            File result = FileDialogs.openDir(ReplayDialog.this,
                    "Please choose a directory to save to",
                    FileDialogs.MM_DATA_SET);
            if (result != null) {
               outputPath_.setText(result.getAbsolutePath());
            }
         }
      });
      contents.add(browseButton_, "wrap");

      contents.add(new JLabel("Save Name: "));
      outputName_ = new JTextField(10);
      outputName_.setToolTipText("Name to give to the processed data");
      contents.add(outputName_, "wrap");

      showDisplay_ = new JCheckBox("Show In New Window");
      showDisplay_.setToolTipText("Display the processed data in a new image window");
      contents.add(showDisplay_, "spanx, alignx right, wrap");

      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new Thread(new Runnable() {
               @Override
               public void run() {
                  processData();
               }
            }).start();
         }
      });

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });

      if (JavaUtils.isMac()) {
         contents.add(cancelButton, "split 2, align right, flowx");
         contents.add(okButton);
      }
      else {
         contents.add(okButton, "split 2, align right, flowx");
         contents.add(cancelButton);
      }

      add(contents);
      pack();
      setVisible(true);
   }

   @Override
   public void dispose() {
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
      HashSet<Datastore> addedStores = new HashSet<Datastore>();
      for (DisplayWindow display : studio_.displays().getAllImageWindows()) {
         if (!addedStores.contains(display.getDatastore())) {
            input_.addItem(display.getName());
            addedStores.add(display.getDatastore());
         }
      }
      input_.addItem(LOAD_FROM_DISK);
      if (curSelection != null) {
         input_.setSelectedItem(curSelection);
      }
   }

   private void processData() {
      Datastore destination;
      if (outputRam_.isSelected()) {
         destination = studio_.data().createRAMDatastore();
      }
      else {
         String path = outputPath_.getText();
         if (path.contentEquals("")) {
            studio_.logs().showError("Please choose a location to save data to.");
            return;
         }
         path += "/" + outputName_.getText();
         try {
            if (outputSingleplane_.isSelected()) {
               destination = studio_.data().createSinglePlaneTIFFSeriesDatastore(path);
            }
            else {
               // TODO: we should imitate the source dataset when possible for
               // deciding whether to generate metadata.txt and whether to
               // split positions.
               destination = studio_.data().createMultipageTIFFDatastore(
                     path, true, true);
            }
         }
         catch (IOException e) {
            studio_.logs().showError(e, "Unable to open " + path + " for writing");
            return;
         }
      }

      Datastore source = null;
      String input = (String) (input_.getSelectedItem());
      if (input.contentEquals(LOAD_FROM_DISK)) {
         try {
            source = studio_.data().promptForDataToLoad(this, true);
         }
         catch (IOException e) {
            studio_.logs().showError(e, "Error loading data");
            return;
         }
      }
      else {
         // Find the display with matching name.
         for (DisplayWindow display : studio_.displays().getAllImageWindows()) {
            if (display.getName().contentEquals(input)) {
               source = display.getDatastore();
               break;
            }
         }
         if (source == null) {
            studio_.logs().showError("The data source named " + input + " is no longer available.");
         }
      }

      // All inputs validated; time to process data.
      dispose();

      if (showDisplay_.isSelected()) {
         studio_.displays().manage(destination);
         studio_.displays().createDisplay(destination);
      }

      ProgressMonitor monitor = new ProgressMonitor(this,
            "Processing images...", "", 0, source.getNumImages());
      Pipeline pipeline = studio_.data().copyApplicationPipeline(
            destination, false);
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
      }
      catch (DatastoreFrozenException e) {
         // This should be impossible!
         studio_.logs().showError(e, "Error processing data: datastore is frozen.");
      }
      catch (DatastoreRewriteException e) {
         // Indicates a fault in one of the Processors in the pipeline.
         studio_.logs().showError(e, "Error processing data: attempt to overwrite existing image in destination dataset.");
      }
      catch (PipelineErrorException e) {
         studio_.logs().showError("Error processing data:" + pipeline.getExceptions());
      }
      catch (IOException e) {
         studio_.logs().showError(e, "Error saving data");
      }
      monitor.close();
      pipeline.halt();
      try {
         destination.freeze();
      } 
      catch (IOException e) {
         studio_.logs().showError(e, "Error saving data");
      }
   }
}
