///////////////////////////////////////////////////////////////////////////////
//FILE:          OMEMetadata.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
//
package org.micromanager.acquisition.internal.multipagetiff;

import java.nio.ByteOrder;
import java.util.TreeMap;
import loci.common.DateTools;
import loci.common.services.ServiceFactory;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import ome.xml.model.primitives.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;

public class OMEMetadata {

   private IMetadata metadata_;
   private StorageMultipageTiff mptStorage_;
   private TreeMap<Integer, Indices> seriesIndices_ = new TreeMap<Integer, Indices>();
   private int numSlices_, numChannels_;
   private TreeMap<String, Integer> tiffDataIndexMap_;
   
   private class Indices {
      //specific to each series independent of file
      int tiffDataIndex_ = -1;
      //specific to each series indpeendent of file
      int planeIndex_ = 0;
   }
   
   public OMEMetadata(StorageMultipageTiff mpt) {
      mptStorage_ = mpt;
      tiffDataIndexMap_ = new TreeMap<String,Integer>();
      metadata_ = MetadataTools.createOMEXMLMetadata();
   }
   
   public static String getOMEStringPointerToMasterFile(String filename, String uuid)  {
      try {
         IMetadata md = MetadataTools.createOMEXMLMetadata();
         md.setBinaryOnlyMetadataFile(filename);
         md.setBinaryOnlyUUID(uuid);
         return new ServiceFactory().getInstance(OMEXMLService.class).getOMEXML(md) + " ";
      } catch (Exception ex) {
         ReportingUtils.logError("Couldn't generate partial OME block");
         return " ";
      }
   }

   @Override
   public String toString() {
      try {
         OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
         return service.getOMEXML(metadata_) + " ";
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }

   public void setNumFrames(int seriesIndex, int numFrames) {
      metadata_.setPixelsSizeT(new PositiveInteger(numFrames), seriesIndex);
   }

   private void startSeriesMetadata(JSONObject firstImageTags, int seriesIndex, String baseFileName) 
           throws JSONException, MMScriptException {
      Indices indices = new Indices();
      indices.planeIndex_ = 0;
      indices.tiffDataIndex_ = 0;
      seriesIndices_.put(seriesIndex, indices);  
      // We need to know bytes per pixel, which requires having an Image handy.
      // TODO: there's an implicit assumption here that all images in the
      // file have the same bytes per pixel.
      numSlices_ = mptStorage_.getIntendedSize("z");
      numChannels_ = mptStorage_.getIntendedSize("channel");
      Image repImage = mptStorage_.getAnyImage();
      //Last one is samples per pixel
      MetadataTools.populateMetadata(metadata_, seriesIndex, baseFileName,
            MultipageTiffWriter.BYTE_ORDER.equals(ByteOrder.LITTLE_ENDIAN),
            mptStorage_.slicesFirst() ? "XYZCT" : "XYCZT",
            "uint" + repImage.getBytesPerPixel() * 8,
            repImage.getWidth(), repImage.getHeight(),
            numSlices_, numChannels_, mptStorage_.getIntendedSize("time"), 1);

      Metadata repMetadata = repImage.getMetadata();
      if (repMetadata.getPixelSizeUm() != null) {
         double pixelSize = repMetadata.getPixelSizeUm();
         if (pixelSize > 0) {
            metadata_.setPixelsPhysicalSizeX(
                  new PositiveFloat(pixelSize), seriesIndex);
            metadata_.setPixelsPhysicalSizeY(
                  new PositiveFloat(pixelSize), seriesIndex);
         }
      }

      SummaryMetadata summaryMD = mptStorage_.getSummaryMetadata();
      if (summaryMD.getZStepUm() != null) {
         double zStep = summaryMD.getZStepUm();
         if (zStep != 0) {
            metadata_.setPixelsPhysicalSizeZ(
                  new PositiveFloat(Math.abs(zStep)), seriesIndex);
         }
      }

      if (summaryMD.getWaitInterval() != null) {
         double interval = summaryMD.getWaitInterval();
         if (interval > 0) { //don't write it for burst mode because it won't be true
            metadata_.setPixelsTimeIncrement(interval / 1000.0, seriesIndex);
         }
      }

      String positionName = "pos" + repImage.getCoords().getPositionAt("position");
      if (repMetadata.getPositionName() != null) {
         positionName = repMetadata.getPositionName();
      }
      metadata_.setStageLabelName(positionName, seriesIndex);

      String instrumentID = MetadataTools.createLSID("Microscope");
      metadata_.setInstrumentID(instrumentID, 0);
      // link Instrument and Image
      metadata_.setImageInstrumentRef(instrumentID, seriesIndex);

      SummaryMetadata summary = mptStorage_.getSummaryMetadata();
      if (summary.getComments() != null) {
         metadata_.setImageDescription(summary.getComments(), seriesIndex);
      }

      DisplaySettings displaySettings = mptStorage_.getDisplaySettings();
      for (int channel = 0; channel < mptStorage_.getIntendedSize("channel");
            channel++) {
         java.awt.Color color = displaySettings.getChannelColors()[channel];
         metadata_.setChannelColor(new ome.xml.model.primitives.Color(
                  color.getRed(), color.getGreen(), color.getBlue(), 1),
               seriesIndex, channel);
         if (displaySettings.getChannelNames() != null &&
               displaySettings.getChannelNames().length > channel) {
            metadata_.setChannelName(displaySettings.getChannelNames()[channel],
                  seriesIndex, channel);
         }
      }
   }
   
   /*
    * Method called when numC*numZ*numT != total number of planes
    */
   public void fillInMissingTiffDatas(int frame, int position) {
      try {
      for (int slice = 0; slice < numSlices_; slice++) {
         for (int channel = 0; channel < numChannels_; channel++) {
            //make sure each tiffdata entry is present. If it is missing, link Tiffdata entry
            //to a a preveious IFD
            Integer tiffDataIndex =  tiffDataIndexMap_.get(MDUtils.generateLabel(channel, slice, frame, position));
            if (tiffDataIndex == null) {
               //this plane was never added, so link to another IFD
               //find substitute channel, frame, slice
               int s = slice;
               int backIndex = slice - 1, forwardIndex = slice + 1;
               int frameSearchIndex = frame;
               //If some but not all channels have z stacks, find the closest slice for the given
               //channel that has an image.  Also if time point missing, go back until image is found
               while (tiffDataIndex == null) {
                  
                              
                  tiffDataIndex = tiffDataIndexMap_.get(MDUtils.generateLabel(channel, s, frameSearchIndex, position));
                  if (tiffDataIndex != null) {
                     break;
                  }

                  if (backIndex >= 0) {
                     tiffDataIndex = tiffDataIndexMap_.get(MDUtils.generateLabel(channel, backIndex, frameSearchIndex, position));
                     if (tiffDataIndex != null) {                   
                        break;
                     }
                     backIndex--;
                  }
                  if (forwardIndex < numSlices_) {
                     tiffDataIndex = tiffDataIndexMap_.get(MDUtils.generateLabel(channel, forwardIndex, frameSearchIndex, position));
                     if (tiffDataIndex != null) {                  
                        break;
                     }
                     forwardIndex++;
                  }

                  if (backIndex < 0 && forwardIndex >= numSlices_) {
                     frameSearchIndex--;
                     backIndex = slice - 1;
                     forwardIndex = slice + 1;
                     if (frameSearchIndex < 0) {
                        break;
                     }
                  }
               }
               NonNegativeInteger ifd = metadata_.getTiffDataIFD(position, tiffDataIndex);
               String filename = metadata_.getUUIDFileName(position, tiffDataIndex);
               String uuid = metadata_.getUUIDValue(position, tiffDataIndex);
               Indices indices = seriesIndices_.get(position);

               metadata_.setTiffDataFirstZ(new NonNegativeInteger(slice), position, indices.tiffDataIndex_);
               metadata_.setTiffDataFirstC(new NonNegativeInteger(channel), position, indices.tiffDataIndex_);
               metadata_.setTiffDataFirstT(new NonNegativeInteger(frame), position, indices.tiffDataIndex_);

               metadata_.setTiffDataIFD(ifd, position, indices.tiffDataIndex_);
               metadata_.setUUIDFileName(filename, position, indices.tiffDataIndex_);
               metadata_.setUUIDValue(uuid, position, indices.tiffDataIndex_);
               metadata_.setTiffDataPlaneCount(new NonNegativeInteger(1), position, indices.tiffDataIndex_);

               indices.tiffDataIndex_++;
            }
         }
      }
      } catch (Exception e) {
         ReportingUtils.logError("Couldn't fill in missing tiffdata entries in ome metadata");
      }
   }

   public void addImageTagsToOME(JSONObject tags, int ifdCount, String baseFileName, String currentFileName,
           String uuid)
           throws JSONException, MMScriptException {
      int position;
      try {
         position = MDUtils.getPositionIndex(tags);
      } catch (Exception e) {
         position = 0;
      }
      if (!seriesIndices_.containsKey(position)) {
         startSeriesMetadata(tags, position, baseFileName);
         try {
            //Add these tags in only once, but need to get them from image rather than summary metadata
            setOMEDetectorMetadata(tags);
            if (MDUtils.hasImageTime(tags)) {
               // Alas, the metadata "Time" field is in one of two formats.
               String imageDate = MDUtils.getImageTime(tags);
               String reformattedDate = DateTools.formatDate(imageDate,
                       "yyyy-MM-dd HH:mm:ss Z", true);
               if (reformattedDate == null) {
                  reformattedDate = DateTools.formatDate(imageDate,
                          "yyyy-MM-dd E HH:mm:ss Z", true);
               }
               if (reformattedDate != null) {
                  metadata_.setImageAcquisitionDate(
                          new Timestamp(reformattedDate), position);
               }
            }
         } catch (Exception e) {
            ReportingUtils.logError(e, "Problem adding System state cache metadata to OME Metadata: " + e);
         }
      }

      Indices indices = seriesIndices_.get(position);

      //Required tags: Channel, slice, and frame index
      try {
         int slice = MDUtils.getSliceIndex(tags);
         int frame = MDUtils.getFrameIndex(tags);
         int channel = MDUtils.getChannelIndex(tags);

         // ifdCount is 0 when a new file started, tiff data plane count is 0 at a new position
         metadata_.setTiffDataFirstZ(new NonNegativeInteger(slice), position, indices.tiffDataIndex_);         
         metadata_.setTiffDataFirstC(new NonNegativeInteger(channel), position, indices.tiffDataIndex_);
         metadata_.setTiffDataFirstT(new NonNegativeInteger(frame), position, indices.tiffDataIndex_);
         metadata_.setTiffDataIFD(new NonNegativeInteger(ifdCount), position, indices.tiffDataIndex_);
         metadata_.setUUIDFileName(currentFileName, position, indices.tiffDataIndex_);
         metadata_.setUUIDValue(uuid, position, indices.tiffDataIndex_);
         tiffDataIndexMap_.put(MDUtils.generateLabel(channel, slice, frame, position), indices.tiffDataIndex_);
         metadata_.setTiffDataPlaneCount(new NonNegativeInteger(1), position, indices.tiffDataIndex_);

         metadata_.setPlaneTheZ(new NonNegativeInteger(slice), position, indices.planeIndex_);
         metadata_.setPlaneTheC(new NonNegativeInteger(channel), position, indices.planeIndex_);
         metadata_.setPlaneTheT(new NonNegativeInteger(frame), position, indices.planeIndex_);
      } catch (JSONException ex) {
         ReportingUtils.showError("Image Metadata missing ChannelIndex, SliceIndex, or FrameIndex");
      } catch (Exception e) {
         ReportingUtils.logError("Couldn't add to OME metadata");
      }

      //Optional tags
      try {

         if (MDUtils.hasExposureMs(tags)) {
            metadata_.setPlaneExposureTime(MDUtils.getExposureMs(tags) / 1000.0,
                  position, indices.planeIndex_);
         }
         if (MDUtils.hasXPositionUm(tags)) {
            metadata_.setPlanePositionX(MDUtils.getXPositionUm(tags), 
                  position, indices.planeIndex_);
            if (indices.planeIndex_ == 0) { //should be set at start, but dont have position coordinates then
               metadata_.setStageLabelX(MDUtils.getXPositionUm(tags), position);
            }
         }
         if (MDUtils.hasYPositionUm(tags)) {
            metadata_.setPlanePositionY(MDUtils.getYPositionUm(tags), 
                  position, indices.planeIndex_);
            if (indices.planeIndex_ == 0) {
               metadata_.setStageLabelY(MDUtils.getYPositionUm(tags), position);
            }
         }
         if (MDUtils.hasZPositionUm(tags)) {
            metadata_.setPlanePositionZ(MDUtils.getZPositionUm(tags), 
                  position, indices.planeIndex_);
         }
         if (MDUtils.hasElapsedTimeMs(tags)) {
            metadata_.setPlaneDeltaT(MDUtils.getElapsedTimeMs(tags) / 1000.0, 
                  position, indices.planeIndex_);
         }

      } catch (JSONException e) {
         ReportingUtils.logError("Problem adding tags to OME Metadata");
      }

      indices.planeIndex_++;
      indices.tiffDataIndex_++;
   }

   private void setOMEDetectorMetadata(JSONObject tags) throws JSONException {
      if (!MDUtils.hasCoreCamera(tags)) {
         return;
      }
      String coreCam = MDUtils.getCoreCamera(tags);
      String[] cameras;
      if (tags.has(coreCam + "-Physical Camera 1")) {       //Multicam mode
         int numCams = 1;
         if (!tags.getString(coreCam + "-Physical Camera 3").equals("Undefined")) {
            numCams = 3;
         } else if (!tags.getString(coreCam + "-Physical Camera 2").equals("Undefined")) {
            numCams = 2;
         }
         cameras = new String[numCams];
         for (int i = 0; i < numCams; i++) {
            cameras[i] = tags.getString(coreCam + "-Physical Camera " + (1 + i));
         }
      } else { //Single camera mode
         cameras = new String[1];
         cameras[0] = coreCam;
      }

      for (int detectorIndex = 0; detectorIndex < cameras.length; detectorIndex++) {
         String camera = cameras[detectorIndex];
         String detectorID = MetadataTools.createLSID(camera);
         //Instrument index, detector index
         metadata_.setDetectorID(detectorID, 0, detectorIndex);
         if (tags.has(camera + "-Name") && !tags.isNull(camera + "-Name")) {
            metadata_.setDetectorManufacturer(tags.getString(camera + "-Name"), 0, detectorIndex);
         }
         if (tags.has(camera + "-CameraName") && !tags.isNull(camera + "-CameraName")) {
            metadata_.setDetectorModel(tags.getString(camera + "-CameraName"), 0, detectorIndex);
         }
         if (tags.has(camera + "-Offset") && !tags.isNull(camera + "-Offset")) {
            metadata_.setDetectorOffset(Double.parseDouble(tags.getString(camera + "-Offset")), 0, detectorIndex);
         }
         if (tags.has(camera + "-CameraID") && !tags.isNull(camera + "-CameraID")) {
            metadata_.setDetectorSerialNumber(tags.getString(camera + "-CameraID"), 0, detectorIndex);
         }

      }
   }
}

