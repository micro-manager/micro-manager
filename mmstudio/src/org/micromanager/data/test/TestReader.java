package org.micromanager.data.test;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import mmcorej.TaggedImage;

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

import org.micromanager.MMStudio;

import org.micromanager.utils.ReportingUtils;

/**
 * Dummy class that provides blank Images/Metadatas/etc. whenever asked.
 */
public class TestReader implements Reader {
   private HashMap<Coords, Image> coordsToImage_;
   private Coords maxIndex_;

   public TestReader() {
      coordsToImage_ = new HashMap<Coords, Image>();
      maxIndex_ = new DefaultCoords.Builder().build();
   }

   @Override
   public Image getImage(Coords coords) {
      if (coordsToImage_.containsKey(coords)) {
         return coordsToImage_.get(coords);
      }

      MMStudio studio = MMStudio.getInstance();
      try {
         studio.snapSingleImage();
         TaggedImage tagged = studio.getMMCore().getTaggedImage();
         Image result = new DefaultImage(tagged);
         coordsToImage_.put(coords, result);
         for (String axis : coords.getAxes()) {
            if (maxIndex_.getPositionAt(axis) < coords.getPositionAt(axis)) {
               // Either this image is further along on this axis, or we have
               // no position for this axis yet.
               maxIndex_ = maxIndex_.copy()
                     .position(axis, coords.getPositionAt(axis))
                     .build();
            }
         }
         return result;
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to generate a new image");
         return null;
      }
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      ArrayList<Image> results = new ArrayList<Image>();
      for (Image image : coordsToImage_.values()) {
         if (image.getCoords().matches(coords)) {
            results.add(image);
         }
      }
      return results;
   }

   @Override
   public Integer getMaxIndex(String axis) {
      return maxIndex_.getPositionAt(axis);
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
