///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;
import javax.swing.filechooser.FileFilter;
import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.PrioritizedEventBus;
import org.micromanager.internal.utils.ReportingUtils;


public class DefaultDatastore implements Datastore {
   // Simple customization of the FileFilter class for choosing the save
   // file format.
   private static class SaveFileFilter extends FileFilter {
      private String desc_;
      public SaveFileFilter(String desc) {
         desc_ = desc;
      }

      public boolean accept(File f) {
         return true;
      }

      public String getDescription() {
         return desc_;
      }
   }

   private static final String SINGLEPLANE_TIFF_SERIES = "Separate Image Files";
   private static final String MULTIPAGE_TIFF = "Image Stack File";
   // FileFilters for saving.
   private static final FileFilter singleplaneFilter_ = new SaveFileFilter(
         SINGLEPLANE_TIFF_SERIES);
   private static final FileFilter multipageFilter_ = new SaveFileFilter(
         MULTIPAGE_TIFF);

   private static final String PREFERRED_SAVE_FORMAT = "default format for saving data";
   protected Storage storage_ = null;
   protected HashMap<String, DefaultAnnotation> annotations_ = new HashMap<String, DefaultAnnotation>();
   protected PrioritizedEventBus bus_;
   protected boolean isFrozen_ = false;
   private String savePath_ = null;
   private boolean haveSetSummary_ = false;

   public DefaultDatastore() {
      bus_ = new PrioritizedEventBus();
   }

   /**
    * Copy all data from the provided other Datastore into ourselves. The
    * optional ProgressMonitor can be used to keep callers appraised of our
    * progress.
    */
   public void copyFrom(Datastore alt, ProgressMonitor monitor) {
      int imageCount = 0;
      try {
         setSummaryMetadata(alt.getSummaryMetadata());
         for (Coords coords : alt.getUnorderedImageCoords()) {
            putImage(alt.getImage(coords));
            imageCount++;
            if (monitor != null) {
               monitor.setProgress(imageCount);
            }
         }
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.logError("Can't copy from datastore: we're frozen");
      }
      catch (DatastoreRewriteException e) {
         ReportingUtils.logError("Can't copy from datastore: we already have an image at one of its coords.");
      }
      catch (IllegalArgumentException e) {
         ReportingUtils.logError("Inconsistent image coordinates in datastore");
      }
   }

   @Override
   public void setStorage(Storage storage) {
      storage_ = storage;
   }

   /**
    * Registers objects at default priority levels.
    */
   @Override
   public void registerForEvents(Object obj) {
      registerForEvents(obj, 100);
   }

   public void registerForEvents(Object obj, int priority) {
      bus_.register(obj, priority);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      bus_.unregister(obj);
   }

   @Override
   public void publishEvent(Object obj) {
      bus_.post(obj);
   }

   @Override
   public Image getImage(Coords coords) {
      if (storage_ != null) {
         return storage_.getImage(coords);
      }
      return null;
   }

   @Override
   public Image getAnyImage() {
      if (storage_ != null) {
         return storage_.getAnyImage();
      }
      return null;
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      if (storage_ != null) {
         return storage_.getImagesMatching(coords);
      }
      return null;
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      if (storage_ != null) {
         return storage_.getUnorderedImageCoords();
      }
      return null;
   }

   @Override
   public boolean hasImage(Coords coords) {
      return storage_ != null && storage_.hasImage(coords);
   }

   @Override
   public void putImage(Image image) throws DatastoreFrozenException, DatastoreRewriteException, IllegalArgumentException {
      if (isFrozen_) {
         throw new DatastoreFrozenException();
      }
      if (hasImage(image.getCoords())) {
         throw new DatastoreRewriteException();
      }
      // Check for validity of axes.
      Coords coords = image.getCoords();
      List<String> ourAxes = getAxes();
      // Can get null axes if we have no storage yet, which we should handle
      // gracefully.
      if (ourAxes != null && ourAxes.size() > 0) {
         for (String axis : coords.getAxes()) {
            if (!ourAxes.contains(axis)) {
               throw new IllegalArgumentException("Invalid image coordinate axis " + axis + "; allowed axes are " + ourAxes);
            }
         }
      }

      if (storage_ != null) {
         storage_.putImage(image);
      }
      bus_.post(new NewImageEvent(image, this));
   }

   @Override
   public Integer getMaxIndex(String axis) {
      if (storage_ != null) {
         return storage_.getMaxIndex(axis);
      }
      return -1;
   }

   @Override
   public Integer getAxisLength(String axis) {
      return getMaxIndex(axis) + 1;
   }

   @Override
   public List<String> getAxes() {
      if (storage_ != null) {
         return storage_.getAxes();
      }
      return null;
   }

   @Override
   public Coords getMaxIndices() {
      if (storage_ != null) {
         return storage_.getMaxIndices();
      }
      return null;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      if (storage_ == null) {
         return null;
      }
      SummaryMetadata result = storage_.getSummaryMetadata();
      if (result == null) {
         // Provide an empty summary metadata instead.
         result = (new DefaultSummaryMetadata.Builder()).build();
      }
      return result;
   }
   
   @Override
   public synchronized void setSummaryMetadata(SummaryMetadata metadata) throws DatastoreFrozenException, DatastoreRewriteException {
      if (isFrozen_) {
         throw new DatastoreFrozenException();
      }
      if (haveSetSummary_) {
         throw new DatastoreRewriteException();
      }
      haveSetSummary_ = true;
      bus_.post(new NewSummaryMetadataEvent(metadata));
   }

   @Override
   public Annotation loadAnnotation(String filename) throws IOException {
      if (annotations_.containsKey(filename)) {
         // We already have an Annotation for this store/filename combo.
         return annotations_.get(filename);
      }
      DefaultAnnotation result = new DefaultAnnotation(this, filename);
      annotations_.put(filename, result);
      return result;
   }

   @Override
   public boolean hasAnnotation(String filename) {
      return (annotations_.containsKey(filename) ||
            DefaultAnnotation.isAnnotationOnDisk(this, filename));
   }

   @Override
   public Annotation createNewAnnotation(String filename) {
      if (hasAnnotation(filename)) {
         throw new IllegalArgumentException("Annotation \"" + filename +
               "\" for datastore at " + savePath_ + " already exists");
      }
      try {
         DefaultAnnotation result = new DefaultAnnotation(this, filename);
         annotations_.put(filename, result);
         return result;
      }
      catch (IOException e) {
         // This should never happen.
         throw new IllegalArgumentException("Annotation \"" + filename +
               "\" for datastore at " + savePath_ +
               " nominally does not exist but we couldn't create a new one anyway.");
      }
   }

   @Override
   public synchronized void freeze() {
      if (!isFrozen_) {
         isFrozen_ = true;
         if (storage_ != null) {
            storage_.freeze();
         }
         bus_.post(new DefaultDatastoreFrozenEvent());
      }
   }

   @Override
   public boolean getIsFrozen() {
      return isFrozen_;
   }

   @Override
   public void close() {
      DefaultEventManager.getInstance().post(
            new DefaultDatastoreClosingEvent(this));
      if (storage_ != null) {
         storage_.close();
      }
   }

   @Override
   public void setSavePath(String path) {
      savePath_ = path;
      bus_.post(new DefaultDatastoreSavePathEvent(path));
   }

   @Override
   public String getSavePath() {
      return savePath_;
   }

   @Override
   public boolean save(Window window) {
      // This replicates some logic from the FileDialogs class, but we want to
      // use non-file-extension-based "filters" to let the user select the
      // savefile format to use, and FileDialogs doesn't play nicely with that.
      JFileChooser chooser = new JFileChooser();
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.addChoosableFileFilter(singleplaneFilter_);
      chooser.addChoosableFileFilter(multipageFilter_);
      if (getPreferredSaveMode().equals(Datastore.SaveMode.MULTIPAGE_TIFF)) {
         chooser.setFileFilter(multipageFilter_);
      }
      else {
         chooser.setFileFilter(singleplaneFilter_);
      }
      chooser.setSelectedFile(
            new File(FileDialogs.getSuggestedFile(FileDialogs.MM_DATA_SET)));
      int option = chooser.showSaveDialog(window);
      if (option != JFileChooser.APPROVE_OPTION) {
         // User cancelled.
         return false;
      }
      File file = chooser.getSelectedFile();
      FileDialogs.storePath(FileDialogs.MM_DATA_SET, file);

      // Determine the mode the user selected.
      FileFilter filter = chooser.getFileFilter();
      Datastore.SaveMode mode = null;
      if (filter == singleplaneFilter_) {
         mode = Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
      }
      else if (filter == multipageFilter_) {
         mode = Datastore.SaveMode.MULTIPAGE_TIFF;
      }
      else {
         ReportingUtils.logError("Unrecognized file format filter " +
               filter.getDescription());
      }
      setPreferredSaveMode(mode);
      return save(mode, file.getAbsolutePath());
   }

   // TODO: re-use existing file-based storage if possible/relevant (i.e.
   // if our current Storage is a file-based Storage).
   @Override
   public boolean save(Datastore.SaveMode mode, String path) {
      SummaryMetadata summary = getSummaryMetadata();
      if (summary == null) {
         // Create dummy summary metadata just for saving.
         summary = (new DefaultSummaryMetadata.Builder()).build();
      }
      // Insert intended dimensions if they aren't already present.
      if (summary.getIntendedDimensions() == null) {
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         for (String axis : getAxes()) {
            builder.index(axis, getAxisLength(axis));
         }
         summary = summary.copy().intendedDimensions(builder.build()).build();
      }
      try {
         DefaultDatastore duplicate = new DefaultDatastore();

         Storage saver;
         if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
            saver = new StorageMultipageTiff(duplicate,
               path, true, true,
               StorageMultipageTiff.getShouldSplitPositions());
         }
         else if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
            saver = new StorageSinglePlaneTiffSeries(duplicate, path, true);
         }
         else {
            throw new IllegalArgumentException("Unrecognized mode parameter " + mode);
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
         ArrayList<Coords> tmp = new ArrayList<Coords>();
         for (Coords coords : getUnorderedImageCoords()) {
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
         for (Coords coords : tmp) {
            duplicate.putImage(getImage(coords));
         }
         // We set the save path and freeze *both* datastores; our own because
         // we should not be modified post-saving, and the other because it
         // may trigger side-effects that "finish" the process of saving.
         setSavePath(path);
         freeze();
         duplicate.setSavePath(path);
         duplicate.freeze();
         duplicate.close();
         // Save our annotations now.
         for (DefaultAnnotation annotation : annotations_.values()) {
            annotation.save();
         }
         return true;
      }
      catch (java.io.IOException e) {
         ReportingUtils.showError(e, "Failed to save image data");
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.logError("Couldn't modify newly-created datastore; this should never happen!");
      }
      catch (DatastoreRewriteException e) {
         ReportingUtils.logError("Couldn't insert an image into newly-created datastore; this should never happen!");
      }
      return false;
   }

   @Override
   public int getNumImages() {
      if (storage_ != null) {
         return storage_.getNumImages();
      }
      return -1;
   }

   public static Datastore.SaveMode getPreferredSaveMode() {
      String modeStr = MMStudio.getInstance().profile().getString(
            DefaultDatastore.class,
            PREFERRED_SAVE_FORMAT, MULTIPAGE_TIFF);
      if (modeStr.equals(MULTIPAGE_TIFF)) {
         return Datastore.SaveMode.MULTIPAGE_TIFF;
      }
      else if (modeStr.equals(SINGLEPLANE_TIFF_SERIES)) {
         return Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
      }
      else {
         ReportingUtils.logError("Unrecognized save mode " + modeStr);
         return null;
      }
   }

   public static void setPreferredSaveMode(Datastore.SaveMode mode) {
      String modeStr = "";
      if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
         modeStr = MULTIPAGE_TIFF;
      }
      else if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
         modeStr = SINGLEPLANE_TIFF_SERIES;
      }
      else {
         ReportingUtils.logError("Unrecognized save mode " + mode);
      }
      MMStudio.getInstance().profile().setString(DefaultDatastore.class,
            PREFERRED_SAVE_FORMAT, modeStr);
   }
}
