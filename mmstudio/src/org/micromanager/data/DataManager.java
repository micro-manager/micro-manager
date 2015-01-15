package org.micromanager.data;

import java.util.List;

import mmcorej.TaggedImage;

import org.json.JSONException;

import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.UserData;

/**
 * This class provides general utility functions for working with
 * Micro-Manager data. You can access it via ScriptInterface's data() method
 * (for example, "gui.data().getCoordsBuilder()").
 */
public interface DataManager {
   /**
    * Generate a "blank" CoordsBuilder for use in constructing new Coords
    * instances.
    */
   public Coords.CoordsBuilder getCoordsBuilder();

   /**
    * Generate a new, "blank" Datastore with no Reader or subscribers, and
    * return it. This Datastore will not be tracked by MicroManager by
    * default (see the org.micromanager.api.display.DisplayManager.track()
    * method for more information).
    */
   public Datastore createNewDatastore();

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
    * Given a TaggedImage input, output an Image.
    */
   public Image convertTaggedImage(TaggedImage tagged) throws JSONException, MMScriptException;

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
   public UserData.UserDataBuilder getUserDataBuilder();
}
