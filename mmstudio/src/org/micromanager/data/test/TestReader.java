package org.micromanager.data.test;

import java.util.HashMap;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;
import org.micromanager.api.data.Reader;
import org.micromanager.api.data.SummaryMetadata;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultDisplaySettings;
import org.micromanager.data.DefaultImage;
import org.micromanager.data.DefaultMetadata;
import org.micromanager.data.DefaultSummaryMetadata;

import org.micromanager.utils.ReportingUtils;

/**
 * Dummy class that provides blank Images/Metadatas/etc. whenever asked.
 */
public class TestReader implements Reader {
   private HashMap<Coords, Image> coordsToImage_;

   public TestReader() {
      coordsToImage_ = new HashMap<Coords, Image>();
   }

   @Override
   public Image getImage(Coords coords) {
      ReportingUtils.logError("Asked for image at " + coords);
      if (coordsToImage_.containsKey(coords)) {
         ReportingUtils.logError("Already have it");
         return coordsToImage_.get(coords);
      }
      short[] pixelData = new short[4*4];
      int index = coordsToImage_.size();
      for (short i = 0; i < 4*4; ++i) {
         pixelData[i] = (short) index;
      }
      Metadata metadata = (new DefaultMetadata.Builder())
            .imageNumber(index)
            .build();
      Image result = new DefaultImage(pixelData, 4, 4, 2,
            coords, metadata);
      coordsToImage_.put(coords, result);
      ReportingUtils.logError("Generated it");
      return result;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return (new DefaultSummaryMetadata.Builder()).build();
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return (new DefaultDisplaySettings.Builder()).build();
   }
}
