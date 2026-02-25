package org.micromanager.magellan.internal.explore;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ShortProcessor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Retrieves tiles from a MultiresNDTiffAPI storage, composites channel data
 * with per-channel color/contrast settings, and writes the result to disk.
 */
public class ExploreImageExporter {

   private final MultiresNDTiffAPI storage_;
   private final JSONObject displaySettings_;

   public ExploreImageExporter(MultiresNDTiffAPI storage, JSONObject displaySettings) {
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
    * @param resLevel      Resolution level (0 = full res, 1 = half res, …).
    * @param format        "TIFF", "JPEG", "PNG", or "GIF".
    * @param outputPath    Destination file path (extension appended if absent).
    */
   public void export(HashMap<String, Object> baseAxes, List<String> channelNames,
                      int roiX, int roiY, int roiW, int roiH,
                      int resLevel, String format, String outputPath) throws Exception {
      int scale = 1 << resLevel;
      int dsX = roiX / scale;
      int dsY = roiY / scale;
      int dsW = Math.max(1, roiW / scale);
      int dsH = Math.max(1, roiH / scale);

      JSONArray colors = displaySettings_.getJSONArray("ChColors");
      JSONArray mins   = displaySettings_.getJSONArray("ChContrastMin");
      JSONArray maxes  = displaySettings_.getJSONArray("ChContrastMax");

      int numChannels = channelNames.size();

      // Special case: single channel → 16-bit TIFF
      if (numChannels == 1 && "TIFF".equals(format)) {
         HashMap<String, Object> axes = new HashMap<>(baseAxes);
         axes.put("channel", channelNames.get(0));
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
         int color = colors.getInt(c);
         int cMin  = mins.getInt(c);
         int cMax  = maxes.getInt(c);
         float range = Math.max(1, cMax - cMin);

         float chR = ((color >> 16) & 0xFF) / 255f;
         float chG = ((color >>  8) & 0xFF) / 255f;
         float chB = ( color        & 0xFF) / 255f;

         HashMap<String, Object> axes = new HashMap<>(baseAxes);
         axes.put("channel", channelNames.get(c));
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
