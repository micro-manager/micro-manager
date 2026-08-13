///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco, 2026
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

package org.micromanager.internal;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * A named, user-defined camera ROI expressed in absolute full-chip coordinates.
 *
 * <p>Presets are read from a plain text file named {@value #PRESETS_FILE_NAME} in the
 * Micro-Manager application directory, alongside the CoreLog.  Each line holds one
 * preset in the form:
 *
 * <pre>
 * name, x, y, width, height
 * </pre>
 *
 * <p>Blank lines, and lines whose first non-whitespace character is '#', are ignored.
 * The file is optional; when it is absent no presets are offered and the crop button
 * keeps its default behavior.
 */
public final class CropPreset {
   public static final String PRESETS_FILE_NAME = "CropPresets.txt";

   private static final int NUM_FIELDS = 5;

   private final String name_;
   private final int x_;
   private final int y_;
   private final int width_;
   private final int height_;

   /**
    * Constructs a preset.
    *
    * @param name   name shown in the crop menu
    * @param x      left edge, in full-chip coordinates
    * @param y      top edge, in full-chip coordinates
    * @param width  width in pixels, must be positive
    * @param height height in pixels, must be positive
    */
   public CropPreset(String name, int x, int y, int width, int height) {
      name_ = name;
      x_ = x;
      y_ = y;
      width_ = width;
      height_ = height;
   }

   public String getName() {
      return name_;
   }

   public Rectangle toRectangle() {
      return new Rectangle(x_, y_, width_, height_);
   }

   /**
    * Returns the file presets are read from: {@value #PRESETS_FILE_NAME} in the
    * Micro-Manager application directory.
    *
    * @return the presets file, or null if the application directory is unknown
    */
   public static File getPresetsFile() {
      String appDirectory = System.getProperty("user.dir");
      if (appDirectory == null || appDirectory.isEmpty()) {
         return null;
      }
      return new File(appDirectory, PRESETS_FILE_NAME);
   }

   /**
    * Returns the contents written by {@link #createTemplateFile()}: a commented example
    * that documents the file format and defines no presets.
    */
   private static String getTemplateContents() {
      return "# Micro-Manager crop presets." + System.lineSeparator()
            + "#" + System.lineSeparator()
            + "# One preset per line, in absolute full-chip coordinates:" + System.lineSeparator()
            + "#" + System.lineSeparator()
            + "#     name, x, y, width, height" + System.lineSeparator()
            + "#" + System.lineSeparator()
            + "# x and y give the top left corner and may not be negative; width and"
            + System.lineSeparator()
            + "# height must be positive.  All four are whole numbers of pixels,"
            + System.lineSeparator()
            + "# measured on the full sensor rather than on the current ROI, so a"
            + System.lineSeparator()
            + "# preset always selects the same area no matter what ROI is in force."
            + System.lineSeparator()
            + "#" + System.lineSeparator()
            + "# Blank lines and lines starting with '#' are ignored.  A line that"
            + System.lineSeparator()
            + "# cannot be read is skipped and noted in the CoreLog; the rest of the"
            + System.lineSeparator()
            + "# file is still used.  Preset names cannot contain a comma."
            + System.lineSeparator()
            + "#" + System.lineSeparator()
            + "# Remove the '#' from the examples below to try them, adjusting the"
            + System.lineSeparator()
            + "# numbers to suit your camera." + System.lineSeparator()
            + "#" + System.lineSeparator()
            + "# Center half, 128, 128, 256, 256" + System.lineSeparator()
            + "# Line scan, 0, 240, 512, 32" + System.lineSeparator();
   }

   /**
    * Creates the presets file, filled with a commented template describing the format.
    *
    * <p>Does nothing if the file already exists, so that this can never destroy presets
    * the user has written.
    *
    * @return the file, whether newly created or already present
    * @throws IOException if the file could not be created
    */
   public static File createTemplateFile() throws IOException {
      File file = getPresetsFile();
      if (file == null) {
         throw new IOException(
               "Unable to determine the Micro-Manager application directory.");
      }
      if (file.exists()) {
         return file;
      }

      try (Writer writer = new OutputStreamWriter(
            new FileOutputStream(file), Charset.defaultCharset())) {
         writer.write(getTemplateContents());
      }
      return file;
   }

   /**
    * Reads the crop presets file.
    *
    * <p>Never returns null and never throws: a missing file yields an empty list, and a
    * malformed line is logged and skipped so that one bad line does not discard the rest
    * of the file.
    *
    * @return the presets found, in file order; empty if there are none
    */
   public static List<CropPreset> loadPresets() {
      File file = getPresetsFile();
      if (file == null || !file.isFile()) {
         return Collections.emptyList();
      }

      List<CropPreset> presets = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line;
         int lineNumber = 0;
         while ((line = reader.readLine()) != null) {
            lineNumber++;
            CropPreset preset = parseLine(line, file, lineNumber);
            if (preset != null) {
               presets.add(preset);
            }
         }
      } catch (IOException e) {
         ReportingUtils.logError(e, "Unable to read crop presets from " + file.getPath());
         return Collections.emptyList();
      }
      return presets;
   }

   /**
    * Parses a single line, returning null (after logging) when it is a comment, is blank,
    * or cannot be understood.
    */
   private static CropPreset parseLine(String line, File file, int lineNumber) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
         return null;
      }

      String[] fields = trimmed.split(",");
      if (fields.length != NUM_FIELDS) {
         logSkipped(file, lineNumber, "expected " + NUM_FIELDS
               + " comma-separated fields (name, x, y, width, height), found " + fields.length);
         return null;
      }

      String name = fields[0].trim();
      if (name.isEmpty()) {
         logSkipped(file, lineNumber, "the preset name is empty");
         return null;
      }

      int[] values = new int[NUM_FIELDS - 1];
      for (int i = 0; i < values.length; i++) {
         try {
            values[i] = Integer.parseInt(fields[i + 1].trim());
         } catch (NumberFormatException e) {
            logSkipped(file, lineNumber, "'" + fields[i + 1].trim() + "' is not a whole number");
            return null;
         }
      }

      int x = values[0];
      int y = values[1];
      int width = values[2];
      int height = values[3];
      if (width <= 0 || height <= 0) {
         logSkipped(file, lineNumber, "width and height must both be positive");
         return null;
      }
      if (x < 0 || y < 0) {
         logSkipped(file, lineNumber, "x and y must not be negative");
         return null;
      }

      return new CropPreset(name, x, y, width, height);
   }

   private static void logSkipped(File file, int lineNumber, String reason) {
      ReportingUtils.logError("Skipping line " + lineNumber + " of "
            + file.getPath() + ": " + reason);
   }
}
