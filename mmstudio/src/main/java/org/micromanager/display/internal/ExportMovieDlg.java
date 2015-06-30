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
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;

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

   private static final String FORMAT_PNG = "PNG";
   private static final String FORMAT_JPEG = "JPEG";
   // ImageJ stack format isn't yet available.
   private static final String FORMAT_IMAGEJ = "ImageJ stack";
   private static final String[] OUTPUT_FORMATS = {
      FORMAT_PNG, FORMAT_JPEG
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
      private JSpinner maxSpinner_;
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
         maxSpinner_ = new JSpinner();

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
         if (curAxis.equals(axis)) {
            // Already set properly.
            return;
         }
         amInSetAxis_ = true;
         oldAxis_ = axis;
         axisSelector_.setSelectedItem(axis);
         minSpinner_.setModel(new SpinnerNumberModel(0, 0, axisLen - 1, 1));
         maxSpinner_.setModel(new SpinnerNumberModel(axisLen, 1, axisLen, 1));
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
         int minVal = (Integer) (minSpinner_.getValue());
         int maxVal = (Integer) (maxSpinner_.getValue());
         for (int i = minVal; i < maxVal; ++i) {
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

   private DisplayWindow display_;
   private Datastore store_;
   private ArrayList<AxisPanel> axisPanels_;
   private JPanel contentsPanel_;
   private JComboBox outputFormatSelector_;
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

      setName("Export Image Series");

      contentsPanel_ = new JPanel(new MigLayout("flowy"));

      JLabel help = new JLabel("Export a series of PNG or JPG images from your dataset, with all overlays included.");
      contentsPanel_.add(help, "align center");

      contentsPanel_.add(new JLabel("Output format: "),
            "split 2, flowx");
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
            pack();
         }
      });
      contentsPanel_.add(outputFormatSelector_);

      jpegPanel_ = new JPanel(new MigLayout("flowx"));
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
      pack();
      setVisible(true);
   }

   /**
    * Export images according to the user's setup. Iterate over each axis,
    * setting the displayed image to the desired coordinates, drawing it,
    * saving the drawn image to disk, and then moving on.
    */
   private void export() {
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
      final File outputDir = chooser.getSelectedFile();

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

      final String mode = (String) outputFormatSelector_.getSelectedItem();
      // This object will get notifications of when the display is done
      // drawing, so the drawn images can be exported and the above thread
      // can be re-started (since AxisPanel.runLoop pauses itself after every
      // call to DisplayWindow.setDisplayedImageTo()).
      final Object exporter = new Object() {
         private int sequenceNum_ = 0;
         @Subscribe
         public void onDrawComplete(CanvasDrawCompleteEvent event) {
            // TODO: add support for exporting to an ImageJ stack.
            exportImage(outputDir, mode,
                  event.getBufferedImage(), sequenceNum_++);
            drawFlag.set(false);
            if (isSingleShot) {
               doneFlag.set(true);
            }
         }
      };
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
         }
      });

      loopThread.start();
   }

   /**
    * Save a single image to disk at the provided directory, with a filename
    * based on the provided sequence number, in the specified mode.
    */
   private void exportImage(File outputDir, String mode, BufferedImage image,
         int sequenceNum) {
      if (mode.equals(FORMAT_PNG)) {
         File file = new File(outputDir,
               String.format("%010d.png", sequenceNum));
         try {
            ImageIO.write(image, "png", file);
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Error writing exported PNG image");
         }
      }
      else if (mode.equals(FORMAT_JPEG)) {
         File file = new File(outputDir,
               String.format("%010d.jpg", sequenceNum));
         // Set the compression quality.
         float quality = ((Integer) jpegQualitySpinner_.getValue()) / ((float) 10.0);
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
    * Return the available axes (that exist in the datastore and have nonzero
    * length).
    */
   public ArrayList<String> getNonZeroAxes() {
      ArrayList<String> result = new ArrayList<String>();
      for (String axis : store_.getAxes()) {
         if (store_.getMaxIndex(axis) > 0) {
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
}

