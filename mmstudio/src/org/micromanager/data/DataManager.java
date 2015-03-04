///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data;

import java.io.IOException;
import java.util.List;

import mmcorej.TaggedImage;

import org.json.JSONException;

import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.PropertyMap;

/**
 * This class provides general utility functions for working with
 * Micro-Manager data. You can access it via ScriptInterface's data() method
 * (for example, "mm.data().getCoordsBuilder()").
 */
public interface DataManager {
   /**
    * Generate a "blank" CoordsBuilder for use in constructing new Coords
    * instances.
    */
   public Coords.CoordsBuilder getCoordsBuilder();

   /**
    * Generate a new, "blank" Datastore with RAM-based Storage and return it.
    * This Datastore will not be managed by Micro-Manager by default (see the
    * org.micromanager.api.display.DisplayManager.manage() method for more
    * information).
    * @return an empty Datastore backed by the appropriate Storage
    */
   public Datastore createRAMDatastore();

   /**
    * Generate a new, "blank" Datastore with multipage TIFF-based Storage and
    * return it. This format stores multiple 2D image planes in the same file,
    * up to 4GB per file. This Datastore will not be managed by Micro-Manager
    * by default (see the org.micromanager.api.display.DisplayManager.manage()
    * method for more information). Be certain to call the save() method of
    * the Datastore when you have finished adding data to it, as the Storage
    * must finalize the dataset before it is properly completed.
    * @param directory Location on disk to store the file(s).
    * @param shouldGenerateSeparateMetadata if true, a separate metadata.txt
    *        file will be generated.
    * @param shouldSplitPositions if true, then each stage position (per
    *        Coords.STAGE_POSITION) will be in a separate file.
    * @return an empty Datastore backed by the appropriate Storage
    * @throws IOException if any errors occur while opening files for writing.
    */
   public Datastore createMultipageTIFFDatastore(String directory,
         boolean shouldGenerateSeparateMetadata, boolean shouldSplitPositions)
         throws IOException;

   /**
    * Generate a new, "blank" Datastore whose Storage is a series of
    * single-plane TIFF files. This Datastore will not be managed by
    * Micro-Manager by default (see the
    * org.micromanager.api.display.DisplayManager.manage() method for more
    * information).  Be certain to call the save() method of the Datastore when
    * you have finished adding data to it, as the Storage must finalize the
    * dataset before it is properly completed.
    * @param directory Location on disk to store the files.
    * @return an empty Datastore backed by the appropriate Storage
    */
   public Datastore createSinglePlaneTIFFSeriesDatastore(String directory);

   /**
    * Load the image data at the specified location on disk, and return a
    * Datastore for that data. This Datastore will not be managed by
    * Micro-Manager by default (see the
    * org.micromanager.api.display.DisplayManager.manage() method for more
    * information).
    * TODO: replace all uses of ScriptInterface.openAcquisitionData with this.
    * @param directory Location on disk from which to pull image data.
    * @param isVirtual If true, then only load images into RAM as they are
    *        requested. This reduces RAM utilization and has a lesser delay
    *        at the start of image viewing, but has worse performance if the
    *        entire dataset needs to be viewed or manipulated.
    * @return A Datastore backed by appropriate Storage that provides access
    *        to the images.
    * @throws IOException if there was any error in reading the data.
    */
   public Datastore loadData(String directory, boolean isVirtual) throws IOException;

   /**
    * Retrieve the Datastore associated with the current open album, or null
    * if there is no album.
    */
   public Datastore getAlbumDatastore();

   /**
    * Generate a new Image with the provided pixel data, rules for interpreting
    * that pixel data, coordinates, and metadata.
    * @param width Width of the image, in pixels
    * @param height Height of the image, in pixels
    * @param bytesPerPixel How many bytes are allocated to each pixel in the
    *        image. Currently only 1, 2, and 4-byte images are supported.
    * @param numComponents How many colors are encoded into each pixel.
    *        Currently only 1-component (grayscale) and 3-component (RGB)
    *        images are supported.
    */
   public Image createImage(Object pixels, int width, int height,
         int bytesPerPixel, int numComponents, Coords coords,
         Metadata metadata);

   /**
    * Given a TaggedImage input, output an Image based on the TaggedImage.
    * @param tagged TaggedImage to be converted
    * @return An Image based on the TaggedImage
    * @throws JSONException if the TaggedImage's metadata cannot be read
    * @throws MMScriptException if portions of the TaggedImage's metadata are
    *         malformed.
    */
   public Image convertTaggedImage(TaggedImage tagged) throws JSONException, MMScriptException;

   /**
    * Given a TaggedImage input, output an Image based on the TaggedImage,
    * but with the Coords and/or Metadata optionally overridden.
    * @param tagged TaggedImage to be converted
    * @param coords Coords at which the new image is located. If null, then
    *        the coordinate information in the TaggedImage will be used.
    * @param metadata Metadata for the new image. If null, then the metadata
    *        will be derived from the TaggedImage instead.
    * @return An Image based on the TaggedImage
    * @throws JSONException if the TaggedImage's metadata cannot be read
    * @throws MMScriptException if portions of the TaggedImage's metadata are
    *         malformed.
    */
   public Image convertTaggedImage(TaggedImage tagged, Coords coords,
         Metadata metadata) throws JSONException, MMScriptException;

   /**
    * Add the specified image to the current album datastore. If the current
    * album doesn't exist or has been locked, a new album will be created.
    */
   public void addToAlbum(Image image);

   /**
    * Generate a "blank" MetadataBuilder for use in constructing new
    * Metadata instances.
    */
   public Metadata.MetadataBuilder getMetadataBuilder();

   /**
    * Generate a "blank" SummaryMetadataBuilder for use in constructing new
    * SummaryMetadata instances.
    */
   public SummaryMetadata.SummaryMetadataBuilder getSummaryMetadataBuilder();

   /**
    * Generate a "blank" DisplaySettings.Builder with all null values.
    */
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder();
}
