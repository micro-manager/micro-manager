///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    (c) 2016 Open Imaging, Inc.
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

package org.micromanager.display;

import java.io.IOException;

/**
 * ImageExporters are used to generate linear sequences of images-as-rendered
 * by a DisplayWindow. Thus they include the current image scaling, any
 * overlays, et cetera.
 */
public interface ImageExporter {
   /**
    * Set the display to use for image exporting. The same ImageExporter may
    * be re-used for multiple displays, if desired.
    * @param display Display to use for exporting images.
    */
   void setDisplay(DisplayWindow display);

   /**
    * Allowed export formats.
    */
   enum OutputFormat {
      /**
       * Output as a sequence of Portable Network Graphics (PNG) files.
       */
      OUTPUT_PNG,
      /**
       * Output as a sequence of Joint Photographic Experts Group (JPEG) files.
       */
      OUTPUT_JPG,
      /**
       * Output as an ImageJ stack, which will open in a new window; this
       * "format" does not create any files on disk.
       */
      OUTPUT_IMAGEJ
   }

   /**
    * Set the output format to use.
    * @param format The format to output in.
    */
   void setOutputFormat(OutputFormat format);

   /**
    * Set the image quality. This is currently only relevant if the output
    * format is OUTPUT_JPG.
    * @param quality An integer quality ranging from 1 through 100. The default
    *        value is 90.
    */
   void setOutputQuality(int quality);

   /**
    * Set the path to save images to, and the filename prefix to use when
    * saving images. These values are ignored if the output format is set to
    * OUTPUT_IMAGEJ.
    * @param path Directory in which images will be placed
    * @param prefix String to place at beginning of each output image's name.
    * @throws IllegalArgumentException if the directory does not exist.
    */
   void setSaveInfo(String path, String prefix) throws IOException;

   /**
    * Add a new "inner loop" to the export parameters. This determines what
    * order the images will be output in. For example:
    * exporter.loop(Coords.Z, 0, 10).loop(Coords.CHANNEL, 0, 2)
    * This will cause the channel axis to be the "inner loop", changing most
    * frequently, and the Z axis to be the "outer loop", changing least
    * frequently.
    * @param axis Axis name, like Coords.CHANNEL, Coords.Z, etc.
    * @param start Axis index to start exporting from, inclusive.
    * @param stop Axis index to stop exporting from, exclusive (i.e. one more
    *        than the index of the last image you want to export).
    * @return This instance, allowing calls to be chained together.
    */
   ImageExporter loop(String axis, int start, int stop);

   /**
    * Reset the current export loop parameters to empty.
    */
   void resetLoops();

   /**
    * Run the export process. This will generate the image sequence as
    * configured previously by calling setOutputFormat, setSaveInfo, and loop.
    * Only one export is allowed to run at a time; if a second export is
    * started, it will block until the first has finished. Otherwise, this
    * method will return immediately. If you want to wait for exporting to
    * finish, call the waitForExport() method.
    *
    * NOTE: the exporter will silently ignore the following "configuration
    * errors":
    * - Setting a save path when using the OUTPUT_IMAGEJ format
    * - Loops over axes that are not used by any images in the display's
    *   datastore
    * - Loops that have invalid start/end boundary conditions (starting or
    *   ending index is greater than largest index in the datastore).
    * In the latter two cases, image coordinates that do not refer to a valid
    * image will be ignored (for example, trying to access a Z coordinate of 1
    * in a dataset that is two-dimensional). It is therefore possible that this
    * method will not actually do anything, if none of the loops encompass
    * valid image coordinates.
    * @throws IOException if the export process would attempt to write to a
    *         file that already exists
    * @throws IllegalArgumentException if no output format has been set, or
    *         OUTPUT_PNG or OUTPUT_JPG formats are used but no save information
    *         has been set, or if no loops have been configured, or if no
    *         display has been set.
    */
   void export() throws IOException, IllegalArgumentException;

   /**
    * Block until a prior call to export() returns. Returns immediately if no
    * export is currently running.
    * @throws InterruptedException if the thread was interrupted while waiting.
    */
   void waitForExport() throws InterruptedException;
}
