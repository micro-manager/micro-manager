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

package org.micromanager.display.internal.gearmenu;

import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.GifWriter;
import ij.plugin.filter.AVI_Writer;
import ij.process.ColorProcessor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.micromanager.LogManager;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.DisplayDidShowImageEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.internal.displaywindow.DisplayController;


public final class DefaultImageExporter implements ImageExporter {

   /**
    * This recursive structure represents the "nested loop" approach to
    * exporting.
    */
   public static class ExporterLoop {
      private DataProvider store_;
      private final String axis_;
      private final int startIndex_;
      private final int stopIndex_;
      private ExporterLoop child_;

      /**
       * Outer Loop for the exporter.
       *
       * @param axis  Axis over which to iterate
       * @param start First coordinate to be exported
       * @param stop  Last coordinate to be exported (will be included!)
       */
      public ExporterLoop(String axis, int start, int stop) {
         axis_ = axis;
         startIndex_ = start;
         stopIndex_ = stop;
      }

      /**
       * Insert a new innermost ExporterLoop -- recursively propagates the
       * provided child to the end of the list.
       *
       * @param child ExporterLoop to be added as the innermost loop
       */
      public void setInnermostLoop(ExporterLoop child) {
         if (child_ == null) {
            child_ = child;
         } else {
            child_.setInnermostLoop(child);
         }
      }

      /**
       * Recursively propagate a display through the list.
       *
       * @param display DisplayWindow to be set for this ExporterLoop
       */
      public void setDisplay(DisplayWindow display) {
         if (display != null) {
            store_ = display.getDataProvider();
         }
         if (child_ != null) {
            child_.setDisplay(display);
         }
      }

      /**
       * Iterate over our specified axis, while running any inner loop(s),
       * determining which images will be exported. Use the provided
       * base coordinates to cover for any coords we aren't iterating over.
       *
       * @param baseCoords Base coordinates to use for this loop
       * @param result  List to which we will add the coordinates of images
       */
      public void selectImageCoords(Coords baseCoords,
                                    ArrayList<Coords> result) {
         for (int i = startIndex_; i <= stopIndex_; ++i) {
            Coords newCoords = baseCoords.copyBuilder().index(axis_, i).build();
            if (child_ == null) {
               // Add the corresponding image, if any.
               if (store_.hasImage(newCoords)) {
                  result.add(newCoords);
               }
            } else {
               // Recurse.
               child_.selectImageCoords(newCoords, result);
            }
         }
      }
   }

   private final LogManager logManager_;
   private DisplayController display_;
   private OutputFormat format_;
   private String directory_;
   private String prefix_;
   private String imageJName_;
   private ExporterLoop outerLoop_;
   private boolean useLabel_ = true;

   private int sequenceNum_ = 0;
   private ImageStack stack_;
   private final AtomicBoolean drawFlag_;
   private final AtomicBoolean doneFlag_;
   private boolean isSingleShot_;
   private int jpegQuality_ = 90;

   private BufferedImage currentImage_ = null;
   private Graphics currentGraphics_ = null;
   private Coords lastDrawnCoords_ = null;

   public DefaultImageExporter(LogManager logManager) {
      // Initialize to true so that waitForCompletion returns immediately.
      doneFlag_ = new AtomicBoolean(true);
      drawFlag_ = new AtomicBoolean(false);
      logManager_ = logManager;
   }

   @Override
   public void setDisplay(DisplayWindow display) {
      display_ = (DisplayController) display;
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
   public void setUseLabel(boolean useLabel) {
      useLabel_ = useLabel;
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
   public void setImageJName(String imageJName) {
      imageJName_ = imageJName;
   }

   @Override
   public ImageExporter loop(String axis, int startIndex, int stopIndex) {
      ExporterLoop exporter = new ExporterLoop(axis, startIndex, stopIndex);
      if (outerLoop_ == null) {
         outerLoop_ = exporter;
      } else {
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
    *
    * @param event DisplayDidShowImageEvent that contains the image that was just
    *                drawn.
    */
   @Subscribe
   public void onDrawComplete(DisplayDidShowImageEvent event) {
      if (event.getDataViewer() instanceof DisplayController) {
         DisplayController dc = (DisplayController) event.getDataViewer();
         try {
            if (dc.getUIController().getIJImageCanvas().getGraphics() != currentGraphics_) {
               // On some machines (Stefan's Mac for instance!), the same image
               // gets drawn twice, resulting in duplication of some images
               // Keep the Coords of the last drawn image, and make sure that  
               // the current image is differnt.
               if (lastDrawnCoords_ != null
                     && lastDrawnCoords_.equals(event.getPrimaryImage().getCoords())) {
                  return;
               }
               lastDrawnCoords_ = event.getPrimaryImage().getCoords();
               // We now know that the correct image is visible on the canvas, so
               // have it paint that image to our own Graphics object. This is
               // inefficient (having to paint the same image twice), but
               // unfortunately there's no way (so far as I'm aware) to get an
               // Image from a component except by painting.
               // The getCanvas() and paintImageWithGraphics methods aren't
               // exposed in DisplayWindow; hence why we need display_ to be
               // DefaultDisplayWindow.
               Dimension canvasSize = dc.getUIController().getIJImageCanvas().getSize();
               currentImage_ = new BufferedImage(canvasSize.width,
                     canvasSize.height, BufferedImage.TYPE_INT_RGB);
               currentGraphics_ = currentImage_.getGraphics();
               dc.getUIController().getIJImageCanvas().hideZoomIndicator(true);
               dc.getUIController().getIJImageCanvas().paint(currentGraphics_);
               dc.getUIController().getIJImageCanvas().hideZoomIndicator(false);

               // Display just finished painting to currentGraphics_, so export
               // now.
               if (format_ == OutputFormat.OUTPUT_IMAGEJ
                       || format_ == OutputFormat.OUTPUT_GIF
                       || format_ == OutputFormat.OUTPUT_AVI) {
                  if (stack_ == null) {
                     // Create the ImageJ stack object to add images to.
                     stack_ = new ImageStack(currentImage_.getWidth(),
                           currentImage_.getHeight());
                  }
                  addToStack(stack_, currentImage_);
               } else if (format_ == OutputFormat.OUTPUT_CLIPBOARD) {
                  TransferableImage transferable = new TransferableImage(currentImage_);
                  Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
               } else {
                  // Save the image to disk in appropriate format.
                  String label = createImageLabel(lastDrawnCoords_);
                  exportImage(currentImage_, label);
               }
               currentGraphics_.dispose();
               drawFlag_.set(false);
               if (isSingleShot_) {
                  doneFlag_.set(true);
               }
            }
         } catch (Exception e) {
            logManager_.logError(e, "Error handling draw complete");
         }
      }
   }

   /**
    * Save a single image to disk.
    */
   private void exportImage(BufferedImage image, String label) {
      String filename = getOutputFilename(label);
      File file = new File(filename);
      if (null != format_) {
         switch (format_) {
            case OUTPUT_PNG:
               try {
                  ImageIO.write(image, "png", file);
               } catch (IOException e) {
                  logManager_.logError(e, "Error writing exported PNG image");
               }
               break;
            case OUTPUT_JPG:
               // Set the compression quality.
               float quality = jpegQuality_ / ((float) 100.0);
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
               } catch (IOException e) {
                  logManager_.showError(e, "Error writing exported JPEG image",
                        display_.getWindow());
               }
               writer.dispose();
               break;
            default:
               logManager_.logError("Unrecognized save format " + format_);
               break;
         }
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
      ArrayList<Coords> coords = new ArrayList<>();
      List<Image> displayedImages = display_.getDisplayedImages();
      if (displayedImages.isEmpty()) {
         // TODO: fill in missing images
         // we are probably on a missing image
         return coords;
      }
      outerLoop_.selectImageCoords(
            displayedImages.get(0).getCoords(), coords);
      if (coords.isEmpty()) {
         // Nothing to do.
         return coords;
      }
      if (format_ == OutputFormat.OUTPUT_JPG || format_ == OutputFormat.OUTPUT_PNG) {
         if (directory_ == null || prefix_ == null) {
            // Can't save.
            throw new IllegalArgumentException(String.format(
                  "Save parameters for exporter were not properly set (directory %s, prefix %s)",
                  directory_, prefix_));
         }
         // Check for potential file overwrites.
         if (coords.size() == 1) {
            checkForOverwrite("");
         } else {
            for (int i = 0; i < coords.size(); ++i) {
               checkForOverwrite(createImageLabel(coords.get(i)));
            }
            sequenceNum_ = 0;
         }
      }
      return coords;
   }

   /**
    * Check to make certain that outputting the nth image will not overwrite
    * an existing file.
    */
   private void checkForOverwrite(String label) throws IOException {
      String path = getOutputFilename(label);
      if (new File(path).exists()) {
         throw new IOException("File at " + path + " would be overwritten during export");
      }
   }

   /**
    * Generate a filename to save image with given label.
    *
    * @param label Output will be directory/prefix label.suffix (suffix is png or jpg)
    * @return directory/prefixlabel.suffix
    */
   private String getOutputFilename(String label) {
      if (format_ == OutputFormat.OUTPUT_IMAGEJ || format_ == OutputFormat.OUTPUT_CLIPBOARD) {
         throw new RuntimeException("Asked for output filename when exporting in ImageJ format.");
      }
      String suffix = "jpg";
      suffix = (format_ == OutputFormat.OUTPUT_PNG) ? "png" : suffix;
      suffix = (format_ == OutputFormat.OUTPUT_AVI) ? "avi" : suffix;
      suffix = (format_ == OutputFormat.OUTPUT_GIF) ? "gif" : suffix;
      return String.format("%s/%s%s.%s", directory_, prefix_, label, suffix);
   }


   private String createImageLabel(Coords imageCoords) {
      StringBuilder sb = new StringBuilder("");
      DataProvider dp = display_.getDataProvider();;
      List<String> channels = dp.getSummaryMetadata().getChannelNameList();
      Coords dimensions = dp.getSummaryMetadata().getIntendedDimensions();
      try {
         if (dp.hasImage(imageCoords) && useLabel_) {
            Metadata  metadata = dp.getImage(imageCoords).getMetadata();
            for (String axis : dimensions.getAxes()) {
               if (dimensions.getIndex(axis) > 1) {
                  long index = imageCoords.getIndex(axis);
                  if (axis.equals(Coords.P)) {
                     if (metadata.hasPositionName()) {
                        sb.append("_")
                              .append(metadata.getPositionName(String.format("%06d", index)));
                     }
                  } else if (axis.equals(Coords.C) && display_.getDisplaySettings().getColorMode()
                        != DisplaySettings.ColorMode.COMPOSITE) {
                     sb.append("_").append(channels.get(imageCoords.getC()));
                  } else if (axis.equals(Coords.Z)) {
                     sb.append("_Z").append(String.format("%06d", index + 1));
                  } else if (axis.equals(Coords.T)) {
                     sb.append("_T").append(String.format("%06d", index + 1));
                  }
               }
            }
            return sb.toString();
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
      return String.format("_%010d", ++sequenceNum_);
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
      } catch (InterruptedException e) {
         // Give up.
         logManager_.logError(e, "Interrupted while waiting for other export to finish.");
         return;
      }
      final ArrayList<Coords> coords = prepAndSanityCheck();
      if (coords.isEmpty()) {
         // Nothing to do.
         return;
      }
      display_.registerForEvents(this);

      // This thread will handle telling the display window to display new
      // images.
      Thread loopThread;
      if (coords.size() == 1) {
         isSingleShot_ = true;
         // Only one image to draw.
         loopThread = new Thread(() -> {
            // force update, or the onDrawComplete callback will not be invoked
            display_.setDisplayPosition(coords.get(0), true);
         }, "Image export thread");
      } else {
         isSingleShot_ = false;
         loopThread = new Thread(() -> {
            for (Coords imageCoords : coords) {
               drawFlag_.set(true);
               // Setting the displayed image will result in our
               // CanvasDrawCompleteEvent handler being invoked, which
               // causes images to be exported.
               display_.setDisplayPosition(imageCoords, true);
               // Wait until drawing is done.
               while (drawFlag_.get()) {
                  try {
                     Thread.sleep(10);
                  } catch (InterruptedException e) {
                     logManager_
                           .logError("Interrupted while waiting for drawing to complete.");
                     return;
                  }
               }
            }
            doneFlag_.set(true);
         }, "Image export thread");
      }

      // Create a thread to wait for the process to finish, and unsubscribe
      // us at that time.
      Thread unsubscriber = new Thread(() -> {
         while (!doneFlag_.get()) {
            try {
               Thread.sleep(100);
            } catch (InterruptedException e) {
               logManager_.logError("Interrupted while waiting for export to complete.");
               return;
            }
         }
         display_.unregisterForEvents(DefaultImageExporter.this);
         if (stack_ != null) {
            File f = new File(display_.getName());
            String shortName = f.getName();
            // Show the ImageJ stack.
            if (format_ == OutputFormat.OUTPUT_IMAGEJ) {
               ImagePlus plus = new ImagePlus(imageJName_, stack_);
               plus.show();
            } else if (format_ == OutputFormat.OUTPUT_AVI || format_ == OutputFormat.OUTPUT_GIF) {
               try {
                  if (directory_ == null || prefix_ == null) {
                     // Can't save.
                     throw new IllegalArgumentException(String.format(
                              "Save parameters for exporter were not properly set "
                              + "(directory %s, prefix %s)",
                              directory_, prefix_));
                  }
                  // Check for potential file overwrites.
                  if (coords.size() == 1) {
                     checkForOverwrite("");
                  }
                  ImagePlus imp = new ImagePlus(shortName + "MM-export", stack_);
                  imp.getCalibration().fps = display_.getPlaybackSpeedFps();
                  if (format_ == OutputFormat.OUTPUT_AVI) {
                     AVI_Writer writer = new AVI_Writer();
                     writer.writeImage(imp, getOutputFilename(""),
                             AVI_Writer.JPEG_COMPRESSION, jpegQuality_);
                  } else if (format_ == OutputFormat.OUTPUT_GIF) {
                     GifWriter.save(imp, getOutputFilename(""));
                  }
               } catch (IllegalArgumentException e) {
                  logManager_.showError(e.getMessage());
               } catch (IOException e) {
                  logManager_.showError(e, "Error writing file");
               }
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

   private class TransferableImage implements Transferable {
      private java.awt.Image img;

      public TransferableImage(java.awt.Image i) {
         this.img = i;
      }

      public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
         if (flavor.equals(DataFlavor.imageFlavor) && img != null)  {
            return img;
         } else {
            throw new UnsupportedFlavorException(flavor);
         }
      }

      public DataFlavor[] getTransferDataFlavors() {
         DataFlavor[] flavors = new DataFlavor[ 1 ];
         flavors[ 0 ] = DataFlavor.imageFlavor;
         return flavors;
      }

      public boolean isDataFlavorSupported(DataFlavor flavor) {
         DataFlavor[] flavors = getTransferDataFlavors();
         for (int i = 0; i < flavors.length; i++) {
            if (flavor.equals(flavors[i])) {
               return true;
            }
         }

         return false;
      }
   }
}

