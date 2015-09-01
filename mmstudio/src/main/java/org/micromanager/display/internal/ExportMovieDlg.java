///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, cweisiger@msg.ucsf.edu, June 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$

package org.micromanager.display.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.BorderFactory;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;

import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This dialog provides an interface for exporting (a portion of) a dataset
 * to an image sequence, complete with all MicroManager overlays.
 */
public class ExportMovieDlg extends JDialog {
   private static final Icon ADD_ICON =
               IconLoader.getIcon("/org/micromanager/icons/plus_green.png");
   private static final Icon DELETE_ICON =
               IconLoader.getIcon("/org/micromanager/icons/minus.png");
   private static final String DEFAULT_EXPORT_FORMAT = "default format to use for exporting image sequences";
   private static final String DEFAULT_FILENAME_PREFIX = "default prefix to use for files when exporting image sequences";

   private static final String FORMAT_PNG = "PNG";
   private static final String FORMAT_JPEG = "JPEG";
   private static final String FORMAT_IMAGEJ = "ImageJ stack window";
   private static final String[] OUTPUT_FORMATS = {
      FORMAT_PNG, FORMAT_JPEG, FORMAT_IMAGEJ
   };

   /**
    * A set of controls for selecting a range of values for a single axis of
    * the dataset. A recursive structure; each panel can contain a child
    * panel, to represent the nested-loop nature of the export process.
    */
   public static class AxisPanel extends JPanel {
      private Datastore store_;
      private DisplayWindow display_;
      private JComboBox axisSelector_;
      private JSpinner minSpinner_;
      private SpinnerNumberModel minModel_ = null;
      private JSpinner maxSpinner_;
      private SpinnerNumberModel maxModel_ = null;
      private JButton addButton_;
      private AxisPanel child_;
      private String oldAxis_;
      // Hacky method of coping with action events we don't care about.
      private boolean amInSetAxis_ = false;

      public AxisPanel(DisplayWindow display, final ExportMovieDlg parent) {
         super(new MigLayout("flowx"));
         setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
         display_ = display;
         store_ = display.getDatastore();
         ArrayList<String> axes = new ArrayList<String>(
               parent.getNonZeroAxes());
         Collections.sort(axes);
         axisSelector_ = new JComboBox(axes.toArray(new String[] {}));
         axisSelector_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               String newAxis = (String) axisSelector_.getSelectedItem();
               if (!amInSetAxis_) {
                  // this event was directly caused by the user.
                  parent.changeAxis(oldAxis_, newAxis);
                  setAxis(newAxis);
               }
            }
         });;
         minSpinner_ = new JSpinner();
         minSpinner_.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               // Ensure that the end point can't go below the start point.
               int newMin = (Integer) minSpinner_.getValue();
               int curMax = (Integer) maxSpinner_.getValue();
               int axisLen = store_.getAxisLength(getAxis());
               maxModel_.setMinimum(newMin + 1);
            }
         });
         maxSpinner_ = new JSpinner();
         maxSpinner_.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               // Ensure that the start point can't come after the end point.
               int newMax = (Integer) maxSpinner_.getValue();
               int curMin = (Integer) minSpinner_.getValue();
               int axisLen = store_.getAxisLength(getAxis());
               minModel_.setMaximum(newMax - 1);
            }
         });

         final AxisPanel localThis = this;
         addButton_ = new JButton("And at each of these...", ADD_ICON);
         addButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (addButton_.getText().equals("And at each of these...")) {
                  // Add a panel "under" us.
                  child_ = parent.createAxisPanel();
                  add(child_, "newline, span");
                  addButton_.setText("Delete inner loop");
                  addButton_.setIcon(DELETE_ICON);
                  parent.pack();
               }
               else {
                  remove(child_);
                  parent.deleteFollowing(localThis);
                  child_ = null;
                  addButton_.setText("And at each of these...");
                  addButton_.setIcon(ADD_ICON);
               }
            }
         });

         add(new JLabel("Export along "));
         add(axisSelector_);
         add(new JLabel(" from "));
         add(minSpinner_);
         add(new JLabel(" to "));
         add(maxSpinner_);
         // Only show the add button if there's an unused axis we can add.
         // HACK: the 1 remaining is us, because we're still in our
         // constructor.
         if (parent.getNumSpareAxes() > 1) {
            add(addButton_);
         }
      }

      public void setAxis(String axis) {
         int axisLen = store_.getAxisLength(axis);
         String curAxis = (String) axisSelector_.getSelectedItem();
         if (curAxis.equals(axis) && minModel_ != null) {
            // Already set properly and spinner models exist.
            return;
         }
         amInSetAxis_ = true;
         oldAxis_ = axis;
         if (minModel_ == null) {
            // Create the spinner models now.
            // Remember our indices here are 1-indexed.
            minModel_ = new SpinnerNumberModel(1, 1, axisLen - 1, 1);
            maxModel_ = new SpinnerNumberModel(axisLen, 2, axisLen, 1);
            minSpinner_.setModel(minModel_);
            maxSpinner_.setModel(maxModel_);
         }
         else {
            // Update their maxima according to the new axis.
            minModel_.setMaximum(axisLen - 1);
            maxModel_.setMaximum(axisLen);
            minModel_.setValue(1);
            maxModel_.setValue(axisLen);
         }
         axisSelector_.setSelectedItem(axis);
         amInSetAxis_ = false;
      }

      public String getAxis() {
         return (String) axisSelector_.getSelectedItem();
      }

      /**
       * Iterate over our specified axis, while running any inner loop(s),
       * drawing the image at each new coordinate set. Use the provided
       * base coordinates to cover for any coords we aren't iterating over.
       * @param drawFlag True when drawing, false when drawing is done and
       *        the next image can be requested. See the "exporter" object.
       * @param doneFlag True when all images are done drawing. Signals that
       *        exporting is done and cleanup can begin.
       */
      public void runLoop(Coords coords, AtomicBoolean drawFlag,
            AtomicBoolean doneFlag) {
         // Correct for the 1-indexed GUI values, since coords are 0-indexed.
         int minVal = (Integer) (minSpinner_.getValue()) - 1;
         int maxVal = (Integer) (maxSpinner_.getValue()) - 1;
         for (int i = minVal; i <= maxVal; ++i) {
            Coords newCoords = coords.copy().index(getAxis(), i).build();
            if (child_ == null) {
               drawFlag.set(true);
               display_.setDisplayedImageTo(newCoords);
               // Wait until drawing is done.
               while (drawFlag.get()) {
                  try {
                     Thread.sleep(10);
                  }
                  catch (InterruptedException e) {
                     ReportingUtils.logError("Interrupted while waiting for drawing to complete.");
                     return;
                  }
               }
            }
            else {
               // Recurse.
               child_.runLoop(newCoords, drawFlag, doneFlag);
            }
         }
         doneFlag.set(true);
      }

      @Override
      public String toString() {
         return "<AxisPanel for axis " + getAxis() + ">";
      }
   }

   /**
    * This object will get notifications of when the display is done
    * drawing, so the drawn images can be exported and the above thread
    * can be re-started (since AxisPanel.runLoop pauses itself after every
    * call to DisplayWindow.setDisplayedImageTo()).
    */
   private static class Exporter {
      private int sequenceNum_ = 0;
      private ImageStack stack_;
      private File outputDir_;
      private String prefix_;
      private String mode_;
      private AtomicBoolean drawFlag_;
      private AtomicBoolean doneFlag_;
      private boolean isSingleShot_;
      private int jpegQuality_;

      public Exporter(File outputDir, String prefix, String mode,
            AtomicBoolean drawFlag, AtomicBoolean doneFlag,
            boolean isSingleShot, int jpegQuality) {
         outputDir_ = outputDir;
         prefix_ = prefix;
         mode_ = mode;
         drawFlag_ = drawFlag;
         doneFlag_ = doneFlag;
         isSingleShot_ = isSingleShot;
         jpegQuality_ = jpegQuality;
      }

      @Subscribe
      public void onDrawComplete(CanvasDrawCompleteEvent event) {
         BufferedImage image = event.getBufferedImage();
         if (mode_.equals(FORMAT_IMAGEJ)) {
            if (stack_ == null) {
               // Create the ImageJ stack object to add images to.
               stack_ = new ImageStack(image.getWidth(), image.getHeight());
            }
            addToStack(stack_, image);
         }
         else {
            // Save the image to disk in appropriate format.
            exportImage(outputDir_, mode_, image, sequenceNum_++);
         }
         drawFlag_.set(false);
         if (isSingleShot_) {
            doneFlag_.set(true);
         }
      }

      /**
       * Save a single image to disk at the provided directory, with a filename
       * based on the provided sequence number, in the specified mode.
       */
      private void exportImage(File outputDir, String mode, BufferedImage image,
            int sequenceNum) {
         String filename = String.format("%s_%010d", prefix_, sequenceNum);
         if (isSingleShot_) {
            // No need to append sequence numbers.
            filename = prefix_;
         }
         if (mode.equals(FORMAT_PNG)) {
            File file = new File(outputDir, filename + ".png");
            try {
               ImageIO.write(image, "png", file);
            }
            catch (IOException e) {
               ReportingUtils.logError(e, "Error writing exported PNG image");
            }
         }
         else if (mode.equals(FORMAT_JPEG)) {
            File file = new File(outputDir, filename + ".jpg");
            // Set the compression quality.
            float quality = jpegQuality_ / ((float) 10.0);
            ImageWriter writer = ImageIO.getImageWritersByFormatName(
                  "jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try {
               ImageOutputStream stream = ImageIO.createImageOutputStream(file);
               writer.setOutput(stream);
               writer.write(image);
               stream.close();
            }
            catch (IOException e) {
               ReportingUtils.logError(e, "Error writing exported JPEG image");
            }
            writer.dispose();
         }
      }

      /**
       * Append the provided image onto the end of the given ImageJ ImageStack.
       */
      private void addToStack(ImageStack stack, BufferedImage image) {
         ColorProcessor processor = new ColorProcessor(image);
         stack.addSlice(processor);
      }

      public ImageStack getStack() {
         return stack_;
      }
   }

   private DisplayWindow display_;
   private Datastore store_;
   private ArrayList<AxisPanel> axisPanels_;
   private JPanel contentsPanel_;
   private JComboBox outputFormatSelector_;
   private JLabel prefixLabel_;
   private JTextField prefixText_;
   private JPanel jpegPanel_;
   private JSpinner jpegQualitySpinner_;

   /**
    * Show the dialog.
    */
   public ExportMovieDlg(DisplayWindow display) {
      super(display.getAsWindow());
      display_ = display;
      store_ = display.getDatastore();
      axisPanels_ = new ArrayList<AxisPanel>();

      setTitle("Export Image Series");

      contentsPanel_ = new JPanel(new MigLayout("flowy"));

      JLabel help = new JLabel("<html><body>Export a series of images from your dataset. The images will be exactly as currently<br>drawn on your display, including histogram scaling, zoom, overlays, etc. Note that<br>this does not preserve the raw data, nor any metadata.</body></html>");
      contentsPanel_.add(help, "align center");

      if (getIsComposite()) {
         contentsPanel_.add(new JLabel("<html><body>The \"channel\" axis is unavailable as the display is in composite mode.</body></html>"),
               "align center");
      }

      contentsPanel_.add(new JLabel("Output format: "),
            "split 4, flowx");
      outputFormatSelector_ = new JComboBox(OUTPUT_FORMATS);
      outputFormatSelector_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Show/hide the JPEG quality controls.
            String selection = (String) outputFormatSelector_.getSelectedItem();
            if (selection.equals(FORMAT_JPEG)) {
               jpegPanel_.add(new JLabel("JPEG quality: "));
               jpegPanel_.add(jpegQualitySpinner_);
            }
            else {
               jpegPanel_.removeAll();
            }
            prefixLabel_.setEnabled(!selection.equals(FORMAT_IMAGEJ));
            prefixText_.setEnabled(!selection.equals(FORMAT_IMAGEJ));
            pack();
         }
      });
      contentsPanel_.add(outputFormatSelector_);

      prefixLabel_ = new JLabel("Filename prefix: ");
      contentsPanel_.add(prefixLabel_);

      prefixText_ = new JTextField(getDefaultPrefix(), 20);
      contentsPanel_.add(prefixText_, "grow 0");

      // We want this panel to take up minimal space when it is not needed.
      jpegPanel_ = new JPanel(new MigLayout("flowx, gap 0", "0[]0[]0",
               "0[]0[]0"));
      jpegQualitySpinner_ = new JSpinner();
      jpegQualitySpinner_.setModel(new SpinnerNumberModel(10, 0, 10, 1));

      contentsPanel_.add(jpegPanel_);

      if (getNonZeroAxes().size() == 0) {
         // No iteration available.
         contentsPanel_.add(
               new JLabel("There is only one image available to export."),
               "align center");
      }
      else {
         contentsPanel_.add(createAxisPanel());
      }
      // Dropdown menu with all axes (except channel when in composite mode)
      // show channel note re: composite mode
      // show note about overlays
      // allow selecting range for each axis; "add axis" button which disables
      // when all axes are used
      // for single-axis datasets just auto-fill the one axis
      // Future req: add ability to export to ImageJ as RGB stack

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            export();
         }
      });
      contentsPanel_.add(cancelButton, "split 2, flowx, align right");
      contentsPanel_.add(exportButton);

      getContentPane().add(contentsPanel_);
      outputFormatSelector_.setSelectedItem(getDefaultExportFormat());
      pack();
      setVisible(true);
   }

   /**
    * Export images according to the user's setup. Iterate over each axis,
    * setting the displayed image to the desired coordinates, drawing it,
    * saving the drawn image to disk, and then moving on.
    */
   private void export() {
      File outputDir = null;
      String mode = (String) outputFormatSelector_.getSelectedItem();
      setDefaultExportFormat(mode);
      if (!mode.equals(FORMAT_IMAGEJ)) {
         // Prompt the user for a directory to save to.
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Please choose a directory to export to.");
         chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
         chooser.setAcceptAllFileFilterUsed(false);
         if (store_.getSavePath() != null) {
            // Default them to where their data was originally saved.
            File path = new File(store_.getSavePath());
            chooser.setCurrentDirectory(path);
            chooser.setSelectedFile(path);
            // HACK: on OSX if we don't do this, the "Choose" button will be
            // disabled until the user interacts with the dialog.
            // This may be related to a bug in the OSX JRE; see
            // http://stackoverflow.com/questions/31148021/jfilechooser-cant-set-default-selection/31148287
            // and in particular Madhan's reply.
            chooser.updateUI();
         }
         if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            // User cancelled.
            return;
         }
         outputDir = chooser.getSelectedFile();
         // HACK: for unknown reasons, on OSX at least we can get a
         // repetition of the final directory if the user clicks the "Choose"
         // button when inside the directory they want to use, resulting in
         // e.g. /foo/bar/baz/baz when only /foo/bar/baz exists.
         if (!outputDir.exists()) {
            outputDir = new File(outputDir.getParent());
            if (!outputDir.exists()) {
               ReportingUtils.showError("Unable to find directory at " + outputDir);
            }
         }
      }

      // This thread will handle telling the display window to display new
      // images.
      Thread loopThread;
      final AtomicBoolean drawFlag = new AtomicBoolean(false);
      final AtomicBoolean doneFlag = new AtomicBoolean(false);
      final boolean isSingleShot;
      if (axisPanels_.size() == 0) {
         // Only one image to draw.
         isSingleShot = true;
         loopThread = new Thread(new Runnable() {
            @Override
            public void run() {
               display_.requestRedraw();
            }
         }, "Image export thread");
      }
      else {
         isSingleShot = false;
         loopThread = new Thread(new Runnable() {
            @Override
            public void run() {
               Coords baseCoords = display_.getDisplayedImages().get(0).getCoords();
               axisPanels_.get(0).runLoop(baseCoords, drawFlag, doneFlag);
            }
         }, "Image export thread");
      }

      String prefix = prefixText_.getText();
      setDefaultPrefix(prefix);
      final Exporter exporter = new Exporter(outputDir, prefix, mode,
            drawFlag, doneFlag, isSingleShot,
            ((Integer) jpegQualitySpinner_.getValue()));
      display_.registerForEvents(exporter);

      // Create a thread to wait for the process to finish, and unsubscribe
      // the exporter at that time.
      Thread unsubscriber = new Thread(new Runnable() {
         @Override
         public void run() {
            while (!doneFlag.get()) {
               try {
                  Thread.sleep(100);
               }
               catch (InterruptedException e) {
                  ReportingUtils.logError("Interrupted while waiting for export to complete.");
                  return;
               }
            }
            display_.unregisterForEvents(exporter);
            if (exporter.getStack() != null) {
               // Show the ImageJ stack.
               ImagePlus plus = new ImagePlus(
                  "MM export results", exporter.getStack());
               plus.show();
            }
         }
      });

      unsubscriber.start();
      loopThread.start();
      dispose();
   }

   /**
    * Create a row of controls for iterating over an axis. Pick an axis from
    * those not yet being used.
    */
   public AxisPanel createAxisPanel() {
      HashSet<String> axes = new HashSet<String>(getNonZeroAxes());
      for (AxisPanel panel : axisPanels_) {
         axes.remove(panel.getAxis());
      }
      if (axes.size() == 0) {
         ReportingUtils.logError("Asked to create axis control when no more valid axes remain.");
         return null;
      }
      String axis = (new ArrayList<String>(axes)).get(0);

      AxisPanel panel = new AxisPanel(display_, this);
      panel.setAxis(axis);
      axisPanels_.add(panel);
      return panel;
   }

   /**
    * One of our panels is changing from the old axis to the new axis; if the
    * new axis is represented in any other panel, it must be swapped with the
    * old one.
    */
   public void changeAxis(String oldAxis, String newAxis) {
      for (AxisPanel panel : axisPanels_) {
         if (panel.getAxis().equals(newAxis)) {
            panel.setAxis(oldAxis);
         }
      }
   }

   /**
    * Remove all AxisPanels after the specified panel. Note that the AxisPanel
    * passed into this method is responsible for removing the following panels
    * from the GUI.
    */
   public void deleteFollowing(AxisPanel last) {
      boolean shouldRemove = false;
      HashSet<AxisPanel> defuncts = new HashSet<AxisPanel>();
      for (AxisPanel panel : axisPanels_) {
         if (shouldRemove) {
            defuncts.add(panel);
         }
         if (panel == last) {
            shouldRemove = true;
         }
      }
      // Remove them from the listing.
      for (AxisPanel panel : defuncts) {
         axisPanels_.remove(panel);
      }
      pack();
   }

   /**
    * Returns true if the display mode is composite.
    */
   private boolean getIsComposite() {
      ImagePlus displayPlus = display_.getImagePlus();
      if (displayPlus instanceof CompositeImage) {
         return ((CompositeImage) displayPlus).getMode() == CompositeImage.COMPOSITE;
      }
      return false;
   }

   /**
    * Return the available axes (that exist in the datastore and have nonzero
    * length).
    */
   public ArrayList<String> getNonZeroAxes() {
      ArrayList<String> result = new ArrayList<String>();
      for (String axis : store_.getAxes()) {
         // Channel axis is only available when in non-composite display modes.
         if (store_.getMaxIndex(axis) > 0 &&
               (!axis.equals(Coords.CHANNEL) || !getIsComposite())) {
            result.add(axis);
         }
      }
      return result;
   }

   /**
    * Return the number of axes that are not currently being used and that
    * have a nonzero length.
    */
   public int getNumSpareAxes() {
      return getNonZeroAxes().size() - axisPanels_.size();
   }

   /**
    * Get the default mode the user wants to use for exporting movies.
    */
   private static String getDefaultExportFormat() {
      return DefaultUserProfile.getInstance().getString(ExportMovieDlg.class,
            DEFAULT_EXPORT_FORMAT, FORMAT_PNG);
   }

   /**
    * Set the default mode to use for exporting movies.
    */
   private static void setDefaultExportFormat(String format) {
      DefaultUserProfile.getInstance().setString(ExportMovieDlg.class,
            DEFAULT_EXPORT_FORMAT, format);
   }

   /**
    * Get the default filename prefix.
    */
   private static String getDefaultPrefix() {
      return DefaultUserProfile.getInstance().getString(ExportMovieDlg.class,
            DEFAULT_FILENAME_PREFIX, "exported");
   }

   /**
    * Set a new default filename prefix.
    */
   private static void setDefaultPrefix(String prefix) {
      DefaultUserProfile.getInstance().setString(ExportMovieDlg.class,
            DEFAULT_FILENAME_PREFIX, prefix);
   }
}

