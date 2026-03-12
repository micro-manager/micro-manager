package org.micromanager.exporttiles;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ShortProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.pyramidalstorage.PyramidalStorageAPI;

/**
 * Retrieves tiles from a MultiresNDTiffAPI storage, composites channel data
 * with per-channel color/contrast settings, and writes the result to disk.
 */
public class ExportImageExporter {

   private final PyramidalStorageAPI storage_;
   private final JSONObject displaySettings_;

   public ExportImageExporter(PyramidalStorageAPI storage, JSONObject displaySettings) {
      storage_ = storage;
      displaySettings_ = displaySettings;
   }

   /**
    * Export a region of interest at a chosen resolution level.
    *
    * @param baseAxes      Non-channel axes (e.g. current Z position).
    * @param channelNames  List of channel names to composite.
    * @param roiX          Left edge of ROI in full-resolution pixels.
    * @param roiY          Top edge of ROI in full-resolution pixels.
    * @param roiW          Width of ROI in full-resolution pixels.
    * @param roiH          Height of ROI in full-resolution pixels.
    * @param resLevel      Resolution level (0 = full res, 1 = half res, ...).
    * @param format        "TIFF", "JPEG", "PNG", or "GIF".
    * @param outputPath    Destination file path (extension appended if absent).
    */
   public void export(HashMap<String, Object> baseAxes, List<String> channelNames,
                      int roiX, int roiY, int roiW, int roiH,
                      int resLevel, String format, String outputPath, boolean blend, boolean align)
           throws Exception {
      export(baseAxes, channelNames, roiX, roiY, roiW, roiH,
              resLevel, format, outputPath, blend, align, pct -> {});
   }

   public void export(HashMap<String, Object> baseAxes, List<String> channelNames,
                      int roiX, int roiY, int roiW, int roiH,
                      int resLevel, String format, String outputPath, boolean blend, boolean align,
                      IntConsumer progress)
           throws Exception {
      if (blend || align) {
         JSONObject summaryMD = storage_.getSummaryMetadata();
         if (summaryMD != null) {
            Map<Point, Point2D.Float> origins = null;
            if (align) {
               progress.accept(0);
               origins = new TileAligner(storage_, baseAxes, channelNames, summaryMD)
                       .computeAlignedOrigins(resLevel, pct -> progress.accept(pct / 2));
               progress.accept(50);
            }
            BufferedImage blended = new TileBlender(storage_, displaySettings_,
                    baseAxes, channelNames, summaryMD)
                    .composite(roiX, roiY, roiW, roiH, resLevel, origins,
                            pct -> progress.accept(align ? 50 + pct / 2 : pct));
            progress.accept(100);
            writeImage(blended, format, outputPath);
            return;
         }
      }

      int scale = 1 << resLevel;
      int dsX = roiX / scale;
      int dsY = roiY / scale;
      int dsW = Math.max(1, roiW / scale);
      int dsH = Math.max(1, roiH / scale);

      int numChannels = channelNames.size();

      // Special case: single channel -> 16-bit TIFF
      if (numChannels == 1 && "TIFF".equals(format)) {
         HashMap<String, Object> axes = new HashMap<>(baseAxes);
         if (channelNames.get(0) != null) {
            axes.put("channel", channelNames.get(0));
         }
         TaggedImage img = storage_.getDisplayImage(axes, resLevel, dsX, dsY, dsW, dsH);
         if (img != null && img.pix instanceof short[]) {
            writeSingleChannel16BitTiff((short[]) img.pix, dsW, dsH, outputPath);
            return;
         }
      }

      // Composite path: additive float accumulators
      float[] rAcc = new float[dsW * dsH];
      float[] gAcc = new float[dsW * dsH];
      float[] bAcc = new float[dsW * dsH];

      for (int c = 0; c < numChannels; c++) {
         String chName = channelNames.get(c);
         String displayKey = (chName != null) ? chName : "NO_CHANNEL_PRESENT";
         int color = 0xFFFFFF;
         int cMin  = 0;
         int cMax  = 65535;
         if (displaySettings_ != null) {
            try {
               JSONObject chSettings = displaySettings_.optJSONObject(displayKey);
               if (chSettings != null) {
                  color = chSettings.optInt("Color", 0xFFFFFF) & 0xFFFFFF;
                  cMin  = chSettings.optInt("Min", 0);
                  cMax  = chSettings.optInt("Max", 65535);
               }
            } catch (Exception e) {
               // use defaults
            }
         }
         float range = Math.max(1, cMax - cMin);

         float chR = ((color >> 16) & 0xFF) / 255f;
         float chG = ((color >>  8) & 0xFF) / 255f;
         float chB = (color         & 0xFF) / 255f;

         HashMap<String, Object> axes = new HashMap<>(baseAxes);
         if (chName != null) {
            axes.put("channel", chName);
         }
         TaggedImage img = storage_.getDisplayImage(axes, resLevel, dsX, dsY, dsW, dsH);
         if (img == null || img.pix == null) {
            continue;
         }

         short[] pixels = (short[]) img.pix;
         for (int i = 0; i < pixels.length; i++) {
            float norm = Math.min(1f, Math.max(0f,
                    ((pixels[i] & 0xFFFF) - cMin) / range));
            rAcc[i] += norm * chR;
            gAcc[i] += norm * chG;
            bAcc[i] += norm * chB;
         }
      }

      // Build 8-bit RGB BufferedImage
      BufferedImage out = new BufferedImage(dsW, dsH, BufferedImage.TYPE_INT_RGB);
      for (int i = 0; i < dsW * dsH; i++) {
         int r = Math.min(255, (int) (rAcc[i] * 255));
         int g = Math.min(255, (int) (gAcc[i] * 255));
         int b = Math.min(255, (int) (bAcc[i] * 255));
         out.setRGB(i % dsW, i / dsW, (r << 16) | (g << 8) | b);
      }
      writeImage(out, format, outputPath);
   }

   private void writeSingleChannel16BitTiff(short[] pixels, int w, int h, String path) {
      ShortProcessor sp = new ShortProcessor(w, h, pixels, null);
      ImagePlus imp = new ImagePlus("export", sp);
      String p = (path.endsWith(".tif") || path.endsWith(".tiff")) ? path : path + ".tif";
      new FileSaver(imp).saveAsTiff(p);
   }

   private void writeImage(BufferedImage img, String format, String path) throws IOException {
      String ext;
      String ioFormat;
      switch (format) {
         case "JPEG":
            ext = ".jpg";
            ioFormat = "jpg";
            break;
         case "GIF":
            ext = ".gif";
            ioFormat = "gif";
            break;
         case "PNG":
            ext = ".png";
            ioFormat = "png";
            break;
         default: // TIFF composite as 8-bit RGB via ImageIO
            ext = ".tif";
            ioFormat = "tif";
            break;
      }
      File f = new File(path.endsWith(ext) ? path : path + ext);
      ImageIO.write(img, ioFormat, f);
   }
}
