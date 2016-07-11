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

import org.micromanager.data.Datastore;

/**
 * ImageExporters are used to generate linear sequences of images-as-rendered
 * by a DisplayWindow. Thus they include the current image scaling, any
 * overlays, et cetera.
 */
public interface ImageExporter {
   /**
    * Set the display to use for image exporting. The same ImageExporter may
    * be re-used for exporting multiple images.
    * @param display Display to use for exporting images.
    */
   public void setDisplay(DisplayWindow display);

   public enum OutputFormat {
      OUTPUT_PNG,
      OUTPUT_JPG,
      OUTPUT_IMAGEJ
   }

   /**
    * Set the output format to use.
    * @param format The format to output in.
    */
   public void setOutputFormat(OutputFormat format);

   /**
    * Set the path to save images to, and the filename prefix to use when
    * saving images. These values are ignored if the output format is set to
    * OUTPUT_IMAGEJ.
    * @param path Directory in which images will be placed
    * @param prefix String to place at beginning of each output image's name.
    */
   public void setSaveInfo(String path, String prefix);

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
   public ImageExporter loop(String axis, int start, int stop);

   /**
    * Reset the current export loop parameters to empty.
    */
   public void resetLoops();
}
