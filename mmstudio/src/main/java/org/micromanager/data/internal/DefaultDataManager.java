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
import java.util.List;

import javax.swing.JFileChooser;

import mmcorej.TaggedImage;

import org.json.JSONException;

import org.micromanager.data.DataManager;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.ImageJConverter;
import org.micromanager.data.Metadata;
import org.micromanager.data.NewPipelineEvent;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.data.Coords;
import org.micromanager.data.internal.multipagetiff.MultipageTiffReader;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.data.internal.pipeline.DefaultPipeline;
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.data.internal.StorageSinglePlaneTiffSeries;
import org.micromanager.data.Pipeline;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
// TODO: this should be moved into the API.
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.PropertyMap;

/**
 * This implementation of the DataManager interface provides general utility
 * access to Micro-Manager's data objects.
 */
public class DefaultDataManager implements DataManager {
   private static final DefaultDataManager staticInstance_;
   static {
      staticInstance_ = new DefaultDataManager();
   }

   @Override
   public Coords.CoordsBuilder getCoordsBuilder() {
      return new DefaultCoords.Builder();
   }

   @Override
   public Datastore createRAMDatastore() {
      Datastore result = new DefaultDatastore();
      result.setStorage(new StorageRAM(result));
      return result;
   }

   @Override
   public Datastore createMultipageTIFFDatastore(String directory,
         boolean shouldGenerateSeparateMetadata, boolean shouldSplitPositions)
         throws IOException {
      DefaultDatastore result = new DefaultDatastore();
      result.setStorage(new StorageMultipageTiff(result, directory, true,
               shouldGenerateSeparateMetadata, shouldSplitPositions));
      return result;
   }

   @Override
   public Datastore createSinglePlaneTIFFSeriesDatastore(String directory) {
      DefaultDatastore result = new DefaultDatastore();
      result.setStorage(new StorageSinglePlaneTiffSeries(result, directory,
            true));
      return result;
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
            "Please select an image data set", MMStudio.MM_DATA_SET);
      if (file == null) {
         return null;
      }
      return loadData(file.getPath(), isVirtual);
   }

   @Override
   public Datastore loadData(String directory, boolean isVirtual) throws IOException {
      // If the user selected a TIFF file, select the directory the file is
      // in.
      File dirFile = new File(directory);
      if (!dirFile.isDirectory()) {
         directory = dirFile.getParent();
      }
      DefaultDatastore result = new DefaultDatastore();
      // TODO: future additional file formats will need to be handled here.
      // For now we just choose between StorageMultipageTiff and
      // StorageSinglePlaneTiffSeries.
      boolean isMultipageTiff = MultipageTiffReader.isMMMultipageTiff(directory);
      if (isMultipageTiff) {
         result.setStorage(new StorageMultipageTiff(result, directory, false));
      }
      else {
         result.setStorage(new StorageSinglePlaneTiffSeries(result, directory,
                  false));
      }
      if (!isVirtual) {
         // Copy into a StorageRAM.
         DefaultDatastore tmp = (DefaultDatastore) createRAMDatastore();
         tmp.copyFrom(result);
         result = tmp;
      }
      result.setSavePath(directory);
      result.freeze();
      return result;
   }

   @Override
   public Datastore.SaveMode getPreferredSaveMode() {
      return DefaultDatastore.getPreferredSaveMode();
   }

   @Override
   public Image createImage(Object pixels, int width, int height,
         int bytesPerPixel, int numComponents, Coords coords,
         Metadata metadata) {
      return new DefaultImage(pixels, width, height, bytesPerPixel,
            numComponents, coords, metadata);
   }

   @Override
   public Image convertTaggedImage(TaggedImage tagged) throws JSONException, MMScriptException {
      return new DefaultImage(tagged);
   }

   @Override
   public Image convertTaggedImage(TaggedImage tagged, Coords coords,
         Metadata metadata) throws JSONException, MMScriptException {
      return new DefaultImage(tagged, coords, metadata);
   }

   @Override
   public Metadata.MetadataBuilder getMetadataBuilder() {
      return new DefaultMetadata.Builder();
   }

   @Override
   public SummaryMetadata.SummaryMetadataBuilder getSummaryMetadataBuilder() {
      return new DefaultSummaryMetadata.Builder();
   }

   @Override
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder() {
      return new DefaultPropertyMap.Builder();
   }

   @Override
   public Pipeline createPipeline(List<ProcessorFactory> factories,
         Datastore store, boolean isSynchronous) {
      ArrayList<Processor> processors = new ArrayList<Processor>();
      for (ProcessorFactory factory : factories) {
         processors.add(factory.createProcessor());
      }
      return new DefaultPipeline(processors, store, isSynchronous);
   }

   @Override
   public Pipeline copyApplicationPipeline(Datastore store,
         boolean isSynchronous) {
      return createPipeline(
            MMStudio.getInstance().getPipelineFrame().getPipelineFactories(),
            store, isSynchronous);
   }

   @Override
   public void clearPipeline() {
      MMStudio.getInstance().getPipelineFrame().clearPipeline();
   }

   @Override
   public void addAndConfigureProcessor(ProcessorPlugin plugin) {
      MMStudio.getInstance().getPipelineFrame().addAndConfigureProcessor(
            plugin);
   }

   @Override
   public void setApplicationPipeline(List<ProcessorPlugin> plugins) {
      clearPipeline();
      for (ProcessorPlugin plugin : plugins) {
         addAndConfigureProcessor(plugin);
      }
   }

   @Override
   public void notifyPipelineChanged() {
      MMStudio.getInstance().events().post(new NewPipelineEvent());
   }

   @Override
   public ImageJConverter ij() {
      return DefaultImageJConverter.getInstance();
   }

   @Override
   public ImageJConverter getImageJConverter() {
      return ij();
   }

   public static DataManager getInstance() {
      return staticInstance_;
   }
}
