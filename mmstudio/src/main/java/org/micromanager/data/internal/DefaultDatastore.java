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

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;
import javax.swing.filechooser.FileFilter;
import org.micromanager.Studio;
import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.UserCancelledException;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.PrioritizedEventBus;
import org.micromanager.internal.utils.ProgressBar;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Default implementaton of the Datastore interface.
 */
public class DefaultDatastore implements Datastore {

   // Simple customization of the FileFilter class for choosing the save
   // file format.
   private static class SaveFileFilter extends FileFilter {
      private final String desc_;

      public SaveFileFilter(String desc) {
         desc_ = desc;
      }

      @Override
      public boolean accept(File f) {
         return true;
      }

      @Override
      public String getDescription() {
         return desc_;
      }
   }

   private static final String SINGLEPLANE_TIFF_SERIES = "Separate Image Files";
   private static final String MULTIPAGE_TIFF = "Image Stack File";
   // FileFilters for saving.
   private static final FileFilter SINGLEPLANEFILTER = new SaveFileFilter(
         SINGLEPLANE_TIFF_SERIES);
   private static final FileFilter MULTIPAGEFILTER = new SaveFileFilter(
         MULTIPAGE_TIFF);
   private static final String PREFERRED_SAVE_FORMAT = "default format for saving data";
   
   protected Storage storage_ = null;
   protected Datastore copiedFromStore_ = null;
   protected String name_ = "Untitled";
   protected Map<String, Annotation> annotations_ = new HashMap<>();
   protected PrioritizedEventBus bus_;
   protected boolean isFrozen_ = false;
   protected final Studio studio_;
   
   private String savePath_ = null;
   private boolean haveSetSummary_ = false;

   public DefaultDatastore(Studio mmStudio) {
      studio_ = mmStudio;
      bus_ = new PrioritizedEventBus(true);
   }

   /**
    * Copy all data from the source Datastore into ourselves. The
    * optional ProgressMonitor can be used to keep callers appraised of our
    * progress.
    *
    * @param alt Source Datastore
    * @param monitor can be used to keep callers appraised of our progress.
    * @throws java.io.IOException expected only for disk-backed Datastores
    * @throws UserCancelledException when the users cancels this action
    */
   public void copyFrom(Datastore alt, ProgressMonitor monitor)
         throws IOException, UserCancelledException {
      copiedFromStore_ = alt;
      int imageCount = 0;
      try {
         setSummaryMetadata(alt.getSummaryMetadata());
         setName(alt.getName() + " - Copy");
         for (Coords coords : alt.getUnorderedImageCoords()) {
            putImage(alt.getImage(coords));
            imageCount++;
            if (monitor != null) {
               if (monitor.isCanceled()) {
                  throw new UserCancelledException();
               }
               monitor.setProgress(imageCount);
            }
         }
      } catch (DatastoreFrozenException e) {
         studio_.logs().logError("Can't copy from datastore: we're frozen");
      } catch (DatastoreRewriteException e) {
         studio_.logs().logError(
               "Can't copy from datastore: we already have an image at one of its coords.");
      } catch (IllegalArgumentException e) {
         studio_.logs().logError("Inconsistent image coordinates in datastore");
      }
   }
   
   @Override
   public void setName(String name) {
      name_ = name;
      bus_.post(new DefaultHasNewNameEvent(name_));
   }

   @Override
   public String getName() {
      return name_;
   }

   @Override
   public void setStorage(Storage storage) {
      storage_ = storage;
   }

   /**
    * Registers objects at default priority levels.
    *
    * @param obj object to be registered.
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
   public Image getImage(Coords coords) throws IOException {
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
   public List<Image> getImagesMatching(Coords coords) throws IOException {
      if (storage_ != null) {
         return storage_.getImagesMatching(coords);
      }
      return null;
   }

   @Override
   public List<Image> getImagesIgnoringAxes(Coords coords, String... ignoreTheseAxes)
           throws IOException {
      Coords testCoords = coords.copyRemovingAxes(ignoreTheseAxes);
      if (storage_ != null) {
         return storage_.getImagesIgnoringAxes(testCoords, ignoreTheseAxes);
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
   public void putImage(Image image) throws IOException {
      if (isFrozen_) {
         throw new DatastoreFrozenException();
      }
      if (image == null) {
         // TODO: log? throw exception?  just crashing is not an option...
         return;
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
            if (!ourAxes.contains(axis) && coords.getIndex(axis) > 0) {
               throw new IllegalArgumentException("Invalid image coordinate axis "
                     + axis + "; allowed axes are " + ourAxes);
            }
         }
      }

      if (storage_ != null) {
         storage_.putImage(image);
      }
      // Note: the store may be very busy saving data, so consumers of this message
      // should use as few resources as possible.  Note that the bus is asynchronous,
      // so we do not have to wait for processing to finish.
      bus_.post(new DefaultNewImageEvent(image, this));
   }

   @Override
   @Deprecated
   public int getAxisLength(String axis) {
      return getNextIndex(axis);
   }

   @Override
   public int getNextIndex(String axis) {
      if (storage_ != null) {
         if (storage_.getAxes().contains(axis)) {
            return storage_.getMaxIndex(axis) + 1;
         }
         return 0;
      }
      return 0;
   }

   @Override
   public List<String> getAxes() {
      if (storage_ != null) {
         return storage_.getAxes();
      }
      return null;
   }

   @Override
   @Deprecated
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
   public synchronized void setSummaryMetadata(SummaryMetadata metadata) 
           throws DatastoreFrozenException, DatastoreRewriteException {
      if (isFrozen_) {
         throw new DatastoreFrozenException();
      }
      if (haveSetSummary_ || getNumImages() > 0) {
         throw new DatastoreRewriteException();
      }
      haveSetSummary_ = true;
      bus_.post(new DefaultNewSummaryMetadataEvent(metadata));
   }

   @Override
   public Annotation getAnnotation(String tag) throws IOException {
      if (annotations_.containsKey(tag)) {
         // We already have an Annotation for this store/filename combo.
         return annotations_.get(tag);
      }
      DefaultAnnotation result;
      result = new DefaultAnnotation(this, tag);
      annotations_.put(tag, result);
      return result;
   }

   public void setAnnotation(String tag, Annotation annotation) {
      annotations_.put(tag, annotation);
   }

   /**
    * Loads annotation from a string and adds to this stores annotations.
    *
    * @param tag String to add to the annotations.
    * @return Annotations object that was added to this datastore.
    * @throws IOException Possible whith disk-backed datastores.
    */
   public Annotation loadAnnotation(String tag) throws IOException {
      if (annotations_.containsKey(tag)) {
         // We already have an Annotation for this store/filename combo.
         return annotations_.get(tag);
      }
      DefaultAnnotation result;
      result = new DefaultAnnotation(this, tag);
      annotations_.put(tag, result);
      return result;
   }

   @Override
   public boolean hasAnnotation(String tag) {
      return (annotations_.containsKey(tag)
            || DefaultAnnotation.isAnnotationOnDisk(this, tag));
   }

   /**
    * I am not sure what this is supposed to do....
    *
    * @param filename ???
    * @return Annotation object
    */
   public Annotation createNewAnnotation(String filename) {
      if (hasAnnotation(filename)) {
         throw new IllegalArgumentException("Annotation \"" + filename
               + "\" for datastore at " + savePath_ + " already exists");
      }
      try {
         DefaultAnnotation result = new DefaultAnnotation(this, filename);
         annotations_.put(filename, result);
         return result;
      } catch (IOException e) {
         // This should never happen.
         throw new IllegalArgumentException("Annotation \"" + filename
               + "\" for datastore at " + savePath_
               + " nominally does not exist but we couldn't create a new one anyway.");
      }
   }

   @Override
   public synchronized void freeze() throws IOException {
      if (!isFrozen_) {
         isFrozen_ = true;
         if (storage_ != null) {
            storage_.freeze();
         }
         bus_.post(new DefaultDatastoreFrozenEvent());
      }
   }

   @Override
   public boolean isFrozen() {
      return isFrozen_;
   }

   @Override
   public void close() throws IOException {
      freeze();
      studio_.events().post(
            new DefaultDatastoreClosingEvent(this));
      if (copiedFromStore_ != null) {
         try {
            CommentsHelper.copyComments(this, copiedFromStore_);
            CommentsHelper.saveComments(copiedFromStore_);
         } catch (IOException ioe) {
            ReportingUtils.logError(ioe, "Failed to write comments for " + copiedFromStore_.getName());
         }
      }
      if (storage_ != null) {
         storage_.close();
         // since we call the gc, make sure that storage, which contains the first
         // image amongst other things, actually may get collected.
         storage_ = null;
         System.gc();
      }
      bus_.shutDown();
   }

   @Override
   public void setSavePath(String path) {
      savePath_ = path;
   }

   @Override
   public String getSavePath() {
      return savePath_;
   }
   
   @Override
   @Deprecated
   public boolean save(Component parent) throws IOException {
      return save(parent, true) != null;
   }

   @Override
   public String save(Component parent, boolean blocking) throws IOException {
      // This replicates some logic from the FileDialogs class, but we want to
      // use non-file-extension-based "filters" to let the user select the
      // savefile format to use, and FileDialogs doesn't play nicely with that.
      JFileChooser chooser = new JFileChooser();
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.addChoosableFileFilter(SINGLEPLANEFILTER);
      chooser.addChoosableFileFilter(MULTIPAGEFILTER);
      if (Objects.equals(getPreferredSaveMode(studio_), SaveMode.MULTIPAGE_TIFF)) {
         chooser.setFileFilter(MULTIPAGEFILTER);
      } else {
         chooser.setFileFilter(SINGLEPLANEFILTER);
      }
      chooser.setSelectedFile(
            new File(FileDialogs.getSuggestedFile(FileDialogs.MM_DATA_SET)));
      int option = chooser.showSaveDialog(parent);
      if (option != JFileChooser.APPROVE_OPTION) {
         // User cancelled.
         return null;
      }
      File file = chooser.getSelectedFile();
      FileDialogs.storePath(FileDialogs.MM_DATA_SET, file);

      // Determine the mode the user selected.
      FileFilter filter = chooser.getFileFilter();
      Datastore.SaveMode mode;
      if (filter == SINGLEPLANEFILTER) {
         mode = Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
      } else if (filter == MULTIPAGEFILTER) {
         mode = Datastore.SaveMode.MULTIPAGE_TIFF;
      }  else {
         studio_.logs().showError("Unrecognized file format filter "
               + filter.getDescription());
         return null;
      }
      setPreferredSaveMode(studio_, mode);
      // the DefaultDataSave constructor creates the directory
      // so that displaysettings can be saved there even before we finish
      // saving all the data
      DefaultDataSaver ds = new DefaultDataSaver(studio_, this, mode,
              file.getAbsolutePath());
      if (!blocking) {
         final ProgressBar pb = new ProgressBar(parent, "Saving..", 0, 100);
         ds.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if ("progress".equals(evt.getPropertyName())) {
               pb.setProgress((Integer) evt.getNewValue());
               if ((Integer) evt.getNewValue() == 100) {
                  pb.setVisible(false);
               }
            }
         });
         ds.execute();
      } else {
         // blocking.  No way to give feedback since we are blocking the EDT
         ds.doInBackground();
      }

      return file.getAbsolutePath();
   }


   @Override
   public void save(SaveMode mode, String path) throws IOException {
      save(mode, path, true);
   }

   // TODO: re-use existing file-based storage if possible/relevant (i.e.
   // if our current Storage is a file-based Storage).
   @Override
   public void save(Datastore.SaveMode mode, String path, boolean blocking) throws IOException {
      DefaultDataSaver ds = new DefaultDataSaver(studio_, this, mode, path);
      if (blocking) {
         ds.doInBackground();
      } else {
         ds.execute();
      }
   }
   
   protected Map<String, Annotation> getAnnotations() {
      return annotations_;
   }

   @Override
   public int getNumImages() {
      if (storage_ != null) {
         return storage_.getNumImages();
      }
      return -1;
   }

   /**
    * Returns the save method set in the user's profile.
    *
    * @param studio Studio object.
    * @return Preferred SaveMode/Format.
    */
   public static Datastore.SaveMode getPreferredSaveMode(Studio studio) {
      String modeStr = studio.profile().getSettings(
            DefaultDatastore.class).getString(
                  PREFERRED_SAVE_FORMAT, MULTIPAGE_TIFF);
      if (modeStr.equals(MULTIPAGE_TIFF)) {
         return Datastore.SaveMode.MULTIPAGE_TIFF;
      } else if (modeStr.equals(SINGLEPLANE_TIFF_SERIES)) {
         return Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
      } else {
         ReportingUtils.logError("Unrecognized save mode " + modeStr);
         return null;
      }
   }

   /**
    * Saves the preferresed DataFormat in the User Profile.
    *
    * @param studio Studio object to get access to the User Profile.
    * @param mode DateMode/Format
    */
   public static void setPreferredSaveMode(Studio studio, Datastore.SaveMode mode) {
      String modeStr = "";
      if (null == mode) {
         ReportingUtils.logError("Unrecognized save mode " + mode);
      } else {
         switch (mode) {
            case MULTIPAGE_TIFF:
               modeStr = MULTIPAGE_TIFF;
               break;
            case SINGLEPLANE_TIFF_SERIES:
               modeStr = SINGLEPLANE_TIFF_SERIES;
               break;
            default:
               ReportingUtils.logError("Unrecognized save mode " + mode);
               break;
         }
      }
      studio.profile().getSettings(DefaultDatastore.class)
              .putString(PREFERRED_SAVE_FORMAT, modeStr);
   }
}