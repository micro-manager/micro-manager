
package org.micromanager.data.internal;

import java.io.IOException;
import java.util.ArrayList;
import javax.swing.SwingWorker;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.internal.MMStudio;

/**
 *
 * @author NicoLocal
 */
public class DefaultDataSaver extends SwingWorker<Void, Void> {
      private final  MMStudio mmStudio_;
      private final DefaultDatastore store_;
      private final Datastore.SaveMode mode_;
      private final String path_;
      
   public DefaultDataSaver(MMStudio mmStudio, 
           DefaultDatastore store, 
           Datastore.SaveMode mode, 
           String path) {
      mmStudio_ = mmStudio;
      store_ = store;
      mode_ = mode;
      path_ = path;
   }
   
   @Override
   protected Void doInBackground() {
      SummaryMetadata summary = store_.getSummaryMetadata();
      if (summary == null) {
         // Create dummy summary metadata just for saving.
         summary = (new DefaultSummaryMetadata.Builder()).build();
      }
      // Insert intended dimensions if they aren't already present.
      if (summary.getIntendedDimensions() == null) {
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         for (String axis : store_.getAxes()) {
            builder.index(axis, store_.getAxisLength(axis));
         }
         summary = summary.copyBuilder().intendedDimensions(builder.build()).build();
      }

      DefaultDatastore duplicate = new DefaultDatastore(mmStudio_);

      try {
         Storage saver;
         if (mode_ == Datastore.SaveMode.MULTIPAGE_TIFF) {
            saver = new StorageMultipageTiff(MMStudio.getFrame(),
                    duplicate,
                    path_, true, true,
                    StorageMultipageTiff.getShouldSplitPositions());
         } else if (mode_ == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
            saver = new StorageSinglePlaneTiffSeries(duplicate, path_, true);
         } else {
            throw new IllegalArgumentException("Unrecognized mode parameter "
                    + mode_);
         }
         duplicate.setStorage(saver);
         duplicate.setSummaryMetadata(summary);
         // HACK HACK HACK HACK HACK
         // Copy images into the duplicate ordered by stage position index.
         // Doing otherwise causes errors when trying to write the OMEMetadata
         // (we get an ArrayIndexOutOfBoundsException when calling
         // MetadataTools.populateMetadata() in
         // org.micromanager.data.internal.multipagetiff.OMEMetadata).
         // Ideally we'd fix the OME metadata writer to be able to handle
         // images in arbitrary order, but that would require understanding
         // that code...
         // We also need to sort by frame (and while we're at it, sort by
         // z and channel as well), since FileSet.writeImage() assumes that
         // timepoints are written sequentially and can potentially cause
         // invalid metadata if they are not.
         ArrayList<Coords> tmp = new ArrayList<>();
         for (Coords coords : store_.getUnorderedImageCoords()) {
            tmp.add(coords);
         }
         java.util.Collections.sort(tmp, new java.util.Comparator<Coords>() {
            @Override
            public int compare(Coords a, Coords b) {
               int p1 = a.getStagePosition();
               int p2 = b.getStagePosition();
               if (p1 != p2) {
                  return p1 < p2 ? -1 : 1;
               }
               int t1 = a.getTime();
               int t2 = b.getTime();
               if (t1 != t2) {
                  return t1 < t2 ? -1 : 1;
               }
               int z1 = a.getZ();
               int z2 = b.getZ();
               if (z1 != z2) {
                  return z1 < z2 ? -1 : 1;
               }
               int c1 = a.getChannel();
               int c2 = b.getChannel();
               return c1 < c2 ? -1 : 1;
            }
         });
         int counter = 0;
         double multiplier = 100.0 / tmp.size();
         for (Coords coords : tmp) {
            duplicate.putImage(store_.getImage(coords));
            counter++;
            setProgress((int) (counter * multiplier));
         }

         // We set the save path and freeze *both* datastores; our own because
         // we should not be modified post-saving, and the other because it
         // may trigger side-effects that "finish" the process of saving.
         store_.setSavePath(path_);
         store_.freeze();
         duplicate.setSavePath(path_);
         duplicate.freeze();
         duplicate.close();
         // Save our annotations now.
         for (DefaultAnnotation annotation : store_.getAnnotations().values()) {
            annotation.save();
         }
      } catch (IOException ioe) {
         mmStudio_.logs().showError(ioe, "Failed to save to " + path_);
      }

      return null;
   }
   
   @Override
   protected void done() {
      setProgress(100);
      mmStudio_.alerts().postAlert("Finished saving", this.getClass(), path_);
   }


}
