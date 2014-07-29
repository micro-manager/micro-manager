package org.micromanager.data.test;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Reader;
import org.micromanager.api.data.SummaryMetadata;

import org.micromanager.data.DefaultImage;
import org.micromanager.data.DefaultDisplaySettings;
import org.micromanager.data.DefaultSummaryMetadata;

/**
 * Dummy class that provides blank Images/Metadatas/etc. whenever asked.
 */
public class TestReader implements Reader {
   @Override
   public Image getImage(Coords coords) {
      short[] pixelData = new short[128*128];
      for (short i = 0; i < 128*128; ++i) {
         pixelData[i] = i;
      }
      return new DefaultImage(pixelData, 128, 128, 2);
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
