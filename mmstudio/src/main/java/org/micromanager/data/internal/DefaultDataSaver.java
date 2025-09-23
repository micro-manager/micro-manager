package org.micromanager.data.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import org.micromanager.Studio;
import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.data.Datastore;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.data.internal.ndtiff.NDTiffAdapter;

/**
 * TODO: Not sure if Swingworker is the best implementation.
 * However, it has nice facilities to give user feedback about progress
 * of saving (using the setProgress function) *
 *
 * @author nico
 */
public class DefaultDataSaver extends SwingWorker<Void, Void> {
   private final Studio studio;
   private final DefaultDatastore store_;
   private final String path_;
   private final DefaultDatastore duplicate_;
   private final Storage saver_;
   private boolean closeOnSave_;

   /**
    * Takes care of most of the dirty work saving data to various targets.
    *
    * @param studio The ever present Studio object.
    * @param store  Datastore that is using this Datasaver.
    * @param mode   Mode (currently, RAM, Single TIffs or MultiPage Tiffs).
    * @param path   Path/directory for disk-based storage.
    * @throws IOException Thrown by disk-based mthods.
    */
   public DefaultDataSaver(Studio studio,
                           DefaultDatastore store,
                           Datastore.SaveMode mode,
                           String path) throws IOException {
      this.studio = studio;
      store_ = store;
      path_ = path;
      closeOnSave_ = false;

      duplicate_ = new DefaultDatastore(this.studio);

      if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
         saver_ = new StorageMultipageTiff(studio.app().getMainWindow(),
               duplicate_,
               path_, true, true,
               StorageMultipageTiff.getShouldSplitPositions());
      } else if (mode == Datastore.SaveMode.ND_TIFF) {
         saver_ = new NDTiffAdapter(duplicate_, path_, true);
         ((NDTiffAdapter) saver_).setSummaryMetadata(store.getSummaryMetadata());
      } else if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
         saver_ = new StorageSinglePlaneTiffSeries(duplicate_, path_, true);
      } else {
         throw new IllegalArgumentException("Unrecognized mode parameter "
               + mode);
      }

   }
   
   /**
    * Same as constructor above with closeOnSave parameter
    * 
    * @param closeOnSave when true, will close datastore after saving.
    */
   public DefaultDataSaver(Studio studio,
                           DefaultDatastore store,
                           Datastore.SaveMode mode,
                           String path,
                           boolean closeOnSave) throws IOException {
      this(studio, store, mode, path);
      closeOnSave_ = closeOnSave;
      }

   @Override
   protected Void doInBackground() throws IOException {
      SummaryMetadata summary = store_.getSummaryMetadata();
      if (summary == null) {
         // Create dummy summary metadata just for saving.
         summary = (new DefaultSummaryMetadata.Builder()).build();
      }
      // Insert intended dimensions if they aren't already present.
      if (summary.getIntendedDimensions() == null) {
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         for (String axis : store_.getAxes()) {
            builder.index(axis, store_.getNextIndex(axis));
         }
         summary = summary.copyBuilder().intendedDimensions(builder.build()).build();
      }

      final SummaryMetadata fSummary = summary;
      duplicate_.setStorage(saver_);
      duplicate_.setSummaryMetadata(fSummary);

      // Copy images ordered by stage position index.
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

      // To have data opened correctly in ImageJ, they need to be ordered 
      // in Time, Slice, Channel order (which ImageJ calls "xyctz" order)
      final List<String> orderedAxes = new ArrayList<>();
      String[] imageJOrderedAxes = new String[] {Coords.T, Coords.Z, Coords.C};
      for (String axis : imageJOrderedAxes) {
         if (store_.getAxes().contains(axis)) {
            orderedAxes.add(axis);
         }
      }

      ArrayList<Coords> tmp = new ArrayList<>();
      for (Coords coords : store_.getUnorderedImageCoords()) {
         tmp.add(coords);
      }
      Collections.sort(tmp, (Coords a, Coords b) -> {
         int p1 = a.getStagePosition();
         int p2 = b.getStagePosition();
         if (p1 != p2) {
            return p1 < p2 ? -1 : 1;
         }

         for (String axis : orderedAxes) {
            switch (axis) {
               case Coords.P:
                  break;
               case Coords.T:
                  int t1 = a.getT();
                  int t2 = b.getT();
                  if (t1 != t2) {
                     return t1 < t2 ? -1 : 1;
                  }
                  break;
               case Coords.Z:
                  int z1 = a.getZ();
                  int z2 = b.getZ();
                  if (z1 != z2) {
                     return z1 < z2 ? -1 : 1;
                  }
                  break;
               case Coords.C:
                  int c1 = a.getChannel();
                  int c2 = b.getChannel();
                  if (c1 != c2) {
                     return c1 < c2 ? -1 : 1;
                  }
                  break;
               default:
                  break;
            }
         }
         return 1;
      });
      int counter = 0;
      double multiplier = 100.0 / tmp.size();
      // Before we can put images into the new storage, we have to be sure that the SummaryMeta-
      // data are there.  We set it before, but that function is asynchronous internally.
      // I do not see ways other than polling.  Alternatively, the bus used to post
      // SummaryMetadata Events, could be made synchronous, but I can not oversee the
      // consequences.
      long startTime = System.currentTimeMillis();
      boolean timeOut = false;
      while (!timeOut && duplicate_.getSummaryMetadata() == null) {
         if (System.currentTimeMillis() - startTime > 10000) {
            timeOut = true;
         }
         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            timeOut = true;
            studio.logs().logError(e);
         }
      }
      if (timeOut) {
         studio.logs().showError("Failed to save data");
         return null;
      }
      for (Coords coords : tmp) {
         duplicate_.putImage(store_.getImage(coords));
         counter++;
         setProgress((int) (counter * multiplier));
      }

      // We set the save path and freeze *both* datastores; our own because
      // we should not be modified post-saving, and the other because it
      // may trigger side-effects that "finish" the process of saving.
      store_.setSavePath(path_);
      store_.freeze();
      if (closeOnSave_) {
          store_.close();
      }
      duplicate_.setSavePath(path_);
      duplicate_.freeze();
      duplicate_.close();
      // Save our annotations now.
      for (Annotation annotation : store_.getAnnotations().values()) {
         annotation.save();
      }

      return null;
   }

   @Override
   protected void done() {
      setProgress(100);
      try {
         get();
      } catch (ExecutionException | InterruptedException e) {
         studio.logs().showError(e, "Failed to save to " + path_);
      }

      studio.alerts().postAlert("Finished saving", this.getClass(), path_);
   }


}
