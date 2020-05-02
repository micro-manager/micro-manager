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
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coords;
import org.micromanager.data.DataManager;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.ImageJConverter;
import org.micromanager.data.Metadata;
import org.micromanager.data.NewPipelineEvent;
import org.micromanager.data.Pipeline;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.multipagetiff.MultipageTiffReader;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.data.internal.pipeline.DefaultPipeline;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.UserCancelledException;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This implementation of the DataManager interface provides general utility
 * access to Micro-Manager's data objects.
 */
public final class DefaultDataManager implements DataManager {
   private static final String CANCEL_OPTION = "Cancel";
   private static final String CONTINUE_OPTION = "Continue";
   private static final String VIRTUAL_OPTION = "Use Virtual";
   
   private final MMStudio studio_;
   private final ImageJConverter ijConverter_;
   
   public DefaultDataManager(MMStudio studio) {
      studio_ = studio;
      ijConverter_ = new DefaultImageJConverter(studio);
   }

   @Override
   @Deprecated
   public Coords.Builder getCoordsBuilder() {
      return new DefaultCoords.Builder();
   }

   @Override
   @Deprecated
   public Coords createCoords(String def) throws IllegalArgumentException {
      return DefaultCoords.fromNormalizedString(def);
   }

   @Override
   public Datastore createRAMDatastore() {
      Datastore result = new DefaultDatastore(studio_);
      result.setStorage(new StorageRAM(result));
      return result;
   }

   @Override
   public Datastore createRAMDatastore(Datastore storeToCopy) {
      throw new UnsupportedOperationException();
   }

   @Override
   public RewritableDatastore createRewritableRAMDatastore() {
      RewritableDatastore result = new DefaultRewritableDatastore(studio_);
      result.setStorage(new StorageRAM(result));
      return result;
   }

   @Override
   public RewritableDatastore createRewritableRAMDatastore(
         Datastore storeToCopy) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Datastore createMultipageTIFFDatastore(String directory,
         boolean shouldGenerateSeparateMetadata, boolean shouldSplitPositions)
         throws IOException {
      DefaultDatastore result = new DefaultDatastore(studio_);
      result.setStorage(new StorageMultipageTiff(null, result, directory, true,
               shouldGenerateSeparateMetadata, shouldSplitPositions));
      return result;
   }

   @Override
   public Datastore createMultipageTIFFDatastore(Datastore storeToCopy,
         String directory, boolean shouldGenerateSeparateMetadata,
         boolean shouldSplitPositions) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Datastore createSinglePlaneTIFFSeriesDatastore(String directory) throws IOException {
      DefaultDatastore result = new DefaultDatastore(studio_);
      result.setStorage(new StorageSinglePlaneTiffSeries(result, directory,
            true));
      return result;
   }

   @Override
   public Datastore createSinglePlaneTIFFSeriesDatastore(Datastore storeToCopy,
         String directory) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getUniqueSaveDirectory(String path) {
      if (path == null) {
         return null;
      }
      File dir = new File(path);
      if (!(dir.exists())) {
         // Path is already unique
         return path;
      }
      // Not unique; figure out what suffix to apply.
      int maxSuffix = 1;
      String name = dir.getName();
      for (String item : (new File(dir.getParent())).list()) {
         if (item.startsWith(name)) {
            try {
               String[] fields = item.split("_");
               maxSuffix = Math.max(maxSuffix,
                     Integer.parseInt(fields[fields.length - 1]));
            }
            catch (NumberFormatException e) {
               // No suffix available to use.
            }
         }
      }
      String result = path + "_" + (maxSuffix + 1);
      if (new File(result).exists()) {
         ReportingUtils.logError("Unable to find unique save path at " + path);
         return null;
      }
      return result;
   }

   @Override
   public Datastore promptForDataToLoad(Window parent, boolean isVirtual) throws IOException {
      File file = FileDialogs.openDir(parent,
            "Please select an image data set", FileDialogs.MM_DATA_SET);
      if (file == null) {
         return null;
      }
      return loadData(parent, file.getPath(), isVirtual);
   }
   
    @Override
   public Datastore loadData(String directory, boolean isVirtual) throws IOException {
      return loadData(null, directory, isVirtual);
   }

   @Override
   public Datastore loadData(Component parent, String directory, boolean isVirtual) throws IOException {
      // If the user selected a TIFF file, select the directory the file is
      // in.
      File dirFile = new File(directory);
      if (!dirFile.isDirectory()) {
         directory = dirFile.getParent();
      }
      DefaultDatastore result = new DefaultDatastore(studio_);
      // TODO: future additional file formats will need to be handled here.
      // For now we just choose between StorageMultipageTiff and
      // StorageSinglePlaneTiffSeries.
      boolean isMultipageTiff = MultipageTiffReader.isMMMultipageTiff(directory);
      if (isMultipageTiff) {
         result.setStorage(new StorageMultipageTiff(parent, result, directory, false));
      }
      else {
         result.setStorage(new StorageSinglePlaneTiffSeries(result, directory,
                  false));
      }
      if (!isVirtual) {
         // Check for available RAM. I'm fairly certain that we should only
         // need to consider the RAM that will be used as direct memory, i.e.
         // not the memory used by the metadata.
         Image image = result.getAnyImage();
         // Must cast a value to long in the arithmetic or else we end up with
         // an overflow situation before converting to (signed) long in the
         // assignment.
         long bytes = ((long) result.getNumImages()) * image.getWidth() *
            image.getHeight() * image.getBytesPerPixel();
         if (bytes > JavaUtils.getAvailableUnusedMemory() * 0.9) {
            // Allow the user to back out of loading to RAM.
            String[] options = new String[] {CONTINUE_OPTION, CANCEL_OPTION,
               VIRTUAL_OPTION};
            int selection = JOptionPane.showOptionDialog(null,
                  "There may not be enough memory to load this data into RAM. Continue anyway, or open as a virtual dataset?",
                  "Insufficient Memory Warning", 0,
                  JOptionPane.WARNING_MESSAGE, null, options, VIRTUAL_OPTION);
            if (options[selection].equals(CANCEL_OPTION)) {
               // Give up.
               return null;
            }
            else if (options[selection].equals(VIRTUAL_OPTION)) {
               // We already have the virtual dataset ready.
               return result;
            }
         }
         // Copy into a StorageRAM.
         ProgressMonitor monitor = new ProgressMonitor(parent,
               "Loading images into RAM...", null, 0, result.getNumImages());
         DefaultDatastore tmp = (DefaultDatastore) createRAMDatastore();
         try {
            tmp.copyFrom(result, monitor);
         }
         catch (UserCancelledException uce) {
            // HACK: to not add an exception to the api, add a message to the 
            // exception that is in the api.  It would be much better to 
            // extend the api....
            throw new IOException ("User Canceled");
         }
         catch (OutOfMemoryError e) {
            // Unable to allocate enough direct memory for our images.
            ReportingUtils.showError("Insufficient memory to open dataset. Try opening in virtual mode instead.");
            return null;
         }
         finally {
            result.close();
            result = tmp;
            monitor.close();
         }
      }
      result.setSavePath(directory);
      result.freeze();
      return result;
   }

   @Override
   public Datastore.SaveMode getPreferredSaveMode() {
      return DefaultDatastore.getPreferredSaveMode(studio_);
   }

   @Override
   public Image createImage(Object pixels, int width, int height, int bytesPerPixel,
                            int numComponents, Coords coords, Metadata metadata) {
      Object pixelClone;
      if (pixels instanceof byte[]) {
         pixelClone = ((byte[]) pixels).clone();
      } else if (pixels instanceof short[]) {
         pixelClone = ((short[]) pixels).clone();
      } else {
         throw new IllegalArgumentException("Pixel type is not supported.  It could not be cloned");
      }
      return new DefaultImage(pixelClone, width, height, bytesPerPixel,
            numComponents, coords, metadata);
   }

   @Override
   public Image wrapImage(Object pixels, int width, int height, int bytesPerPixel,
                             int numComponents, Coords coords, Metadata metadata) {
      return new DefaultImage(pixels, width, height, bytesPerPixel,
              numComponents, coords, metadata);
   }

   @Override
   public Image convertTaggedImage(TaggedImage tagged) throws JSONException {
      return new DefaultImage(tagged);
   }

   @Override
   public Image convertTaggedImage(TaggedImage tagged, Coords coords,
         Metadata metadata) throws JSONException {
      return new DefaultImage(tagged, coords, metadata);
   }

   @Override
   public Metadata.Builder getMetadataBuilder() {
      return new DefaultMetadata.Builder();
   }

   @Override
   public SummaryMetadata.Builder getSummaryMetadataBuilder() {
      return new DefaultSummaryMetadata.Builder();
   }

   @Override
   @Deprecated
   public PropertyMap.Builder getPropertyMapBuilder() {
      return PropertyMaps.builder();
   }

   @Override
   @Deprecated
   public PropertyMap loadPropertyMap(String path) throws IOException {
      return PropertyMaps.loadJSON(new File(path));
   }

   @Override
   public Pipeline createPipeline(List<ProcessorFactory> factories,
         Datastore store, boolean isSynchronous) {
      ArrayList<Processor> processors = new ArrayList<>();
      for (ProcessorFactory factory : factories) {
         processors.add(factory.createProcessor());
      }
      return new DefaultPipeline(processors, store, isSynchronous);
   }

   @Override
   public Pipeline copyApplicationPipeline(Datastore store,
         boolean isSynchronous) {
      return createPipeline(studio_.getPipelineFrame().getPipelineFactories(),
            store, isSynchronous);
   }

   @Override
   public Pipeline copyLivePipeline(Datastore store, boolean isSynchronous) {
      return createPipeline(studio_.getPipelineFrame().getLivePipelineFactories(),
            store, isSynchronous);
   }

   @Override
   public List<ProcessorConfigurator> getApplicationPipelineConfigurators(boolean includeDisabled) {
       if (includeDisabled) {
        return studio_.getPipelineFrame().getPipelineConfigurators();
       } else {
           return studio_.getPipelineFrame().getEnabledPipelineConfigurators();
       }
   }
   
     @Override
    public List<ProcessorConfigurator> getLivePipelineConfigurators(boolean includeDisabled) {
        if (includeDisabled) {
            return studio_.getPipelineFrame().getPipelineConfigurators();
        } else {
           return studio_.getPipelineFrame().getEnabledLivePipelineConfigurators();
        }
    }
    

   @Override
   public void clearPipeline() {
      studio_.getPipelineFrame().clearPipeline();
   }

   @Override
   public void addAndConfigureProcessor(ProcessorPlugin plugin) {
      studio_.getPipelineFrame().addAndConfigureProcessor(
            plugin);
   }

   @Override
   public void addConfiguredProcessor(ProcessorConfigurator config,
         ProcessorPlugin plugin) {
      studio_.getPipelineFrame().addConfiguredProcessor(config, plugin);
   }

   @Override
   public void setApplicationPipeline(List<ProcessorPlugin> plugins) {
      clearPipeline();
      for (ProcessorPlugin plugin : plugins) {
         addAndConfigureProcessor(plugin);
      }
   }

    @Override
    public boolean isApplicationPipelineStepEnabled(int index) {
        return studio_.getPipelineFrame().getConfiguratorEnabled(index);
    }
    
    @Override
    public void setApplicationPipelineStepEnabled(int index, boolean enabled) {
        studio_.getPipelineFrame().setConfiguratorEnabled(index, enabled);
    }
    
    @Override
    public boolean isLivePipelineStepEnabled(int index) {
        return studio_.getPipelineFrame().getConfiguratorEnabledLive(index);
    }
    
    @Override
    public void setLivePipelineStepEnabled(int index, boolean enabled) {
        studio_.getPipelineFrame().setConfiguratorEnabledLive(index, enabled);
    }

   @Override
   public void notifyPipelineChanged() {
      studio_.events().post(new NewPipelineEvent());
   }

   @Override
   public ImageJConverter ij() {
      return ijConverter_;
   }

   @Override
   public ImageJConverter getImageJConverter() {
      return ij();
   }
}