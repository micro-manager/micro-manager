///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    (c) 2016 Open Imaging, Inc.
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

package org.micromanager.display.internal;

import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.ImageExporter.OutputFormat;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;

import org.micromanager.internal.utils.ReportingUtils;


public class DefaultImageExporter implements ImageExporter {

   /**
    * This recursive structure represents the "nested loop" approach to
    * exporting.
    */
   public static class ExporterLoop {
      private Datastore store_;
      private String axis_;
      private int startIndex_;
      private int stopIndex_;
      private ExporterLoop child_;

      public ExporterLoop(String axis, int start, int stop) {
         axis_ = axis;
         startIndex_ = start;
         stopIndex_ = stop;
      }

      /**
       * Insert a new innermost ExporterLoop -- recursively propagates the
       * provided child to the end of the list.
       */
      public void setInnermostLoop(ExporterLoop child) {
         if (child_ == null) {
            child_ = child;
         }
         else {
            child_.setInnermostLoop(child);
         }
      }

      /**
       * Recursively propagate a display through the list.
       */
      public void setDisplay(DisplayWindow display) {
         if (display != null) {
            store_ = display.getDatastore();
         }
         if (child_ != null) {
            child_.setDisplay(display);
         }
      }

      /**
       * Iterate over our specified axis, while running any inner loop(s),
       * determining which images will be exported. Use the provided
       * base coordinates to cover for any coords we aren't iterating over.
       */
      public void selectImageCoords(Coords baseCoords,
            ArrayList<Coords> result) {
         for (int i = startIndex_; i < stopIndex_; ++i) {
            Coords newCoords = baseCoords.copy().index(axis_, i).build();
            if (child_ == null) {
               // Add the corresponding image, if any.
               if (store_.hasImage(newCoords)) {
                  result.add(newCoords);
               }
            }
            else {
               // Recurse.
               child_.selectImageCoords(newCoords, result);
            }
         }
      }
   }

   private DefaultDisplayWindow display_;
   private OutputFormat format_;
   private String directory_;
   private String prefix_;
   private ExporterLoop outerLoop_;

   private int sequenceNum_ = 0;
   private ImageStack stack_;
   private String mode_;
   private AtomicBoolean drawFlag_;
   private AtomicBoolean doneFlag_;
   private boolean isSingleShot_;
   private int jpegQuality_ = 10;

   private BufferedImage currentImage_ = null;
   private Graphics currentGraphics_ = null;

   public DefaultImageExporter() {
      // Initialize to true so that waitForCompletion returns immediately.
      doneFlag_ = new AtomicBoolean(true);
      drawFlag_ = new AtomicBoolean(false);
   }

   @Override
   public void setDisplay(DisplayWindow display) {
      display_ = (DefaultDisplayWindow) display;
      if (outerLoop_ != null) {
         outerLoop_.setDisplay(display);
      }
   }

   @Override
   public void setOutputFormat(OutputFormat format) {
      format_ = format;
   }

   @Override
   public void setOutputQuality(int quality) {
      jpegQuality_ = quality;
   }

   @Override
   public void setSaveInfo(String directory, String prefix) throws IOException {
      if (!(new File(directory).exists())) {
         throw new IOException("Directory " + directory + " does not exist");
      }
      directory_ = directory;
      prefix_ = prefix;
   }

   @Override
   public ImageExporter loop(String axis, int startIndex, int stopIndex) {
      ExporterLoop exporter = new ExporterLoop(axis, startIndex, stopIndex);
      if (outerLoop_ == null) {
         outerLoop_ = exporter;
      }
      else {
         outerLoop_.setInnermostLoop(exporter);
      }
      // Ensure loops have displays set.
      outerLoop_.setDisplay(display_);
      return this;
   }

   @Override
   public void resetLoops() {
      outerLoop_ = null;
   }

   /**
    * This method gets called twice for each image we export: once for the
    * display responding to our request to set the image coordinates, and
    * then once for the display painting to our provided Graphics object.
    */
   @Subscribe
   public void onDrawComplete(CanvasDrawCompleteEvent event) {
      try {
         if (event.getGraphics() != currentGraphics_) {
            // We now know that the correct image is visible on the canvas, so
            // have it paint that image to our own Graphics object. This is
            // inefficient (having to paint the same image twice), but
            // unfortunately there's no way (so far as I'm aware) to get an
            // Image from a component except by painting.
            // HACK: the getCanvas() and paintImageWithGraphics methods aren't
            // exposed in DisplayWindow; hence why we need display_ to be
            // DefaultDisplayWindow.
            Dimension canvasSize = display_.getCanvas().getSize();
            currentImage_ = new BufferedImage(canvasSize.width,
                  canvasSize.height, BufferedImage.TYPE_INT_RGB);
            currentGraphics_ = currentImage_.getGraphics();
            display_.paintImageWithGraphics(currentGraphics_);
         }
         else {
            // Display just finished painting to currentGraphics_, so export
            // now.
            if (format_ == OutputFormat.OUTPUT_IMAGEJ) {
               if (stack_ == null) {
                  // Create the ImageJ stack object to add images to.
                  stack_ = new ImageStack(currentImage_.getWidth(),
                        currentImage_.getHeight());
               }
               addToStack(stack_, currentImage_);
            }
            else {
               // Save the image to disk in appropriate format.
               exportImage(currentImage_, sequenceNum_++);
            }
            currentGraphics_.dispose();
            drawFlag_.set(false);
            if (isSingleShot_) {
               doneFlag_.set(true);
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error handling draw complete");
      }
   }

   /**
    * Save a single image to disk.
    */
   private void exportImage(BufferedImage image, int sequenceNum) {
      String filename = getOutputFilename(isSingleShot_ ? -1 : sequenceNum);
      File file = new File(filename);
      if (format_ == OutputFormat.OUTPUT_PNG) {
         try {
            ImageIO.write(image, "png", file);
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Error writing exported PNG image");
         }
      }
      else if (format_ == OutputFormat.OUTPUT_JPG) {
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
      else {
         ReportingUtils.logError("Unrecognized save format " + format_);
      }
   }

   /**
    * Append the provided image onto the end of the given ImageJ ImageStack.
    */
   private void addToStack(ImageStack stack, BufferedImage image) {
      ColorProcessor processor = new ColorProcessor(image);
      stack.addSlice(processor);
   }

   /**
    * Run sanity checks prior to exporting images. Determine how many images
    * will be exported. Ensure that each image will not overwrite any existing
    * file. Return the list of image coordinates to be exported.
    */
   private ArrayList<Coords> prepAndSanityCheck()
      throws IOException, IllegalArgumentException {
      if (format_ == null) {
         throw new IllegalArgumentException("No output format was selected");
      }
      if (outerLoop_ == null) {
         throw new IllegalArgumentException("No loops have been configured");
      }
      if (display_ == null) {
         throw new IllegalArgumentException("No display has been set");
      }
      ArrayList<Coords> coords = new ArrayList<Coords>();
      outerLoop_.selectImageCoords(
            display_.getDisplayedImages().get(0).getCoords(), coords);
      if (coords.size() == 0) {
         // Nothing to do.
         return coords;
      }
      if (format_ != OutputFormat.OUTPUT_IMAGEJ) {
         if (directory_ == null || prefix_ == null) {
            // Can't save.
            throw new IllegalArgumentException(String.format("Save parameters for exporter were not properly set (directory %s, prefix %s)", directory_, prefix_));
         }
         // Check for potential file overwrites.
         if (coords.size() == 1) {
            checkForOverwrite(-1);
         }
         else {
            for (int i = 0; i < coords.size(); ++i) {
               checkForOverwrite(i);
            }
         }
      }
      return coords;
   }

   /**
    * Check to make certain that outputting the nth image will not overwrite
    * an existing file.
    */
   private void checkForOverwrite(int n) throws IOException {
      String path = getOutputFilename(n);
      if (new File(path).exists()) {
         throw new IOException("File at " + path + " would be overwritten during export");
      }
   }

   /**
    * Generate a filename to save the nth image.
    * @param n image index, or -1 for a single-shot export (of only one image).
    */
   private String getOutputFilename(int n) {
      if (format_ == OutputFormat.OUTPUT_IMAGEJ) {
         throw new RuntimeException("Asked for output filename when exporting in ImageJ format.");
      }
      String suffix = (format_ == OutputFormat.OUTPUT_PNG) ? "png" : "jpg";
      if (n == -1) {
         return String.format("%s/%s.%s", directory_, prefix_, suffix);
      }
      return String.format("%s/%s_%010d.%s", directory_, prefix_, n, suffix);
   }

   /**
    * Export images according to the user's setup. Iterate over each axis,
    * setting the displayed image to the desired coordinates, drawing it,
    * saving the drawn image to disk, and then moving on.
    * This method is synchronized, which doesn't mean a whole lot because
    * the actual export process happens on separate threads. However, it calls
    * waitForExport() as its first action, which will block if another export
    * is in progress.
    */
   @Override
   public synchronized void export() throws IOException, IllegalArgumentException {
      // Don't run two exports at the same time.
      try {
         waitForExport();
      }
      catch (InterruptedException e) {
         // Give up.
         ReportingUtils.logError(e, "Interrupted while waiting for other export to finish.");
         return;
      }
      final ArrayList<Coords> coords = prepAndSanityCheck();
      if (coords.size() == 0) {
         // Nothing to do.
         return;
      }
      display_.registerForEvents(this);
      File outputDir = null;

      // This thread will handle telling the display window to display new
      // images.
      Thread loopThread;
      if (coords.size() == 1) {
         isSingleShot_ = true;
         // Only one image to draw.
         loopThread = new Thread(new Runnable() {
            @Override
            public void run() {
               display_.requestRedraw();
            }
         }, "Image export thread");
      }
      else {
         isSingleShot_ = false;
         loopThread = new Thread(new Runnable() {
            @Override
            public void run() {
               for (Coords imageCoords : coords) {
                  drawFlag_.set(true);
                  // Setting the displayed image will result in our
                  // CanvasDrawCompleteEvent handler being invoked, which
                  // causes images to be exported.
                  display_.setDisplayedImageTo(imageCoords);
                  // Wait until drawing is done.
                  while (drawFlag_.get()) {
                     try {
                        Thread.sleep(10);
                     }
                     catch (InterruptedException e) {
                        ReportingUtils.logError("Interrupted while waiting for drawing to complete.");
                        return;
                     }
                  }
               }
               doneFlag_.set(true);
            }
         }, "Image export thread");
      }

      // Create a thread to wait for the process to finish, and unsubscribe
      // us at that time.
      Thread unsubscriber = new Thread(new Runnable() {
         @Override
         public void run() {
            while (!doneFlag_.get()) {
               try {
                  Thread.sleep(100);
               }
               catch (InterruptedException e) {
                  ReportingUtils.logError("Interrupted while waiting for export to complete.");
                  return;
               }
            }
            display_.unregisterForEvents(DefaultImageExporter.this);
            if (stack_ != null) {
               // Show the ImageJ stack.
               ImagePlus plus = new ImagePlus("MM export results", stack_);
               plus.show();
            }
         }
      });

      doneFlag_.set(false);
      unsubscriber.start();
      loopThread.start();
   }

   @Override
   public void waitForExport() throws InterruptedException {
      while (!doneFlag_.get()) {
         Thread.sleep(100);
      }
   }
}

