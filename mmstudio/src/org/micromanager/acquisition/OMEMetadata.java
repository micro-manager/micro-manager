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
package org.micromanager.acquisition;

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
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class OMEMetadata {

   private IMetadata metadata_;
   private TaggedImageStorageMultipageTiff mptStorage_;
   private TreeMap<Integer, Indices> seriesIndices_ = new TreeMap<Integer, Indices>();
   private int omeXMLBaseLength_ = -1;
   private int omeXMLImageLength_ = -1;
   private int numSlices_, numChannels_;
   private TreeMap<String, Integer> tiffDataIndexMap_;
   
   private class Indices {
      //specific to each series independent of file
      int tiffDataIndex_ = -1;
      //specific to each series indpeendent of file
      int planeIndex_ = 0;
   }
   
   public OMEMetadata(TaggedImageStorageMultipageTiff mpt) {
      mptStorage_ = mpt;
      tiffDataIndexMap_ = new TreeMap<String,Integer>();
      metadata_ = MetadataTools.createOMEXMLMetadata();
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
   
   public int getOMEMetadataBaseLenght() {
      return omeXMLBaseLength_;  
   }
   
   public int getOMEMetadataImageLength() {
      return omeXMLImageLength_;
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
      //Last one is samples per pixel
      JSONObject summaryMD = mptStorage_.getSummaryMetadata();
      numSlices_ = MDUtils.getNumSlices(summaryMD);
      numChannels_ = MDUtils.getNumChannels(summaryMD);
      MetadataTools.populateMetadata(metadata_, seriesIndex, baseFileName, MultipageTiffWriter.BYTE_ORDER.equals(ByteOrder.LITTLE_ENDIAN),
              mptStorage_.slicesFirst() ? "XYZCT" : "XYCZT", "uint" + (MDUtils.isGRAY8(summaryMD) ? "8" : "16"),
              MDUtils.getWidth(summaryMD), MDUtils.getHeight(summaryMD),
              numSlices_, MDUtils.getNumChannels(summaryMD), MDUtils.getNumFrames(summaryMD), 1);

      if (summaryMD.has("PixelSize_um") && !summaryMD.isNull("PixelSize_um")) {
         double pixelSize = summaryMD.getDouble("PixelSize_um");
         if (pixelSize > 0) {
            metadata_.setPixelsPhysicalSizeX(new PositiveFloat(pixelSize), seriesIndex);
            metadata_.setPixelsPhysicalSizeY(new PositiveFloat(pixelSize), seriesIndex);
         }
      }
      if (summaryMD.has("z-step_um") && !summaryMD.isNull("z-step_um")) {
         double zStep = summaryMD.getDouble("z-step_um");
	 if (zStep != 0) {
            metadata_.setPixelsPhysicalSizeZ(new PositiveFloat(Math.abs(zStep)), seriesIndex);
         }
      }

      if (summaryMD.has("Interval_ms")) {
         double interval = summaryMD.getDouble("Interval_ms");
         if (interval > 0) { //don't write it for burst mode because it won't be true
            metadata_.setPixelsTimeIncrement(interval / 1000.0, seriesIndex);
         }
      }
      
      String positionName;
      try {
         positionName = MDUtils.getPositionName(firstImageTags);
      } catch (JSONException ex) {
         ReportingUtils.logError("Couldn't find position name in image metadata");
         positionName = "pos" + MDUtils.getPositionIndex(firstImageTags);
      }
      metadata_.setStageLabelName(positionName, seriesIndex);

      String instrumentID = MetadataTools.createLSID("Microscope");
      metadata_.setInstrumentID(instrumentID, 0);
      // link Instrument and Image
      metadata_.setImageInstrumentRef(instrumentID, seriesIndex);

      JSONObject comments = mptStorage_.getDisplayAndComments().getJSONObject("Comments");
      if (comments.has("Summary") && !comments.isNull("Summary")) {
         metadata_.setImageDescription(comments.getString("Summary"), seriesIndex);
      }

      JSONArray channels = mptStorage_.getDisplayAndComments().getJSONArray("Channels");
      for (int channelIndex = 0; channelIndex < channels.length(); channelIndex++) {
         JSONObject channel = channels.getJSONObject(channelIndex);
         metadata_.setChannelColor(new Color(channel.getInt("Color")), seriesIndex, channelIndex);
         metadata_.setChannelName(channel.getString("Name"), seriesIndex, channelIndex);
      }
      //used to estimate the final length of the OME xml string
      if (omeXMLBaseLength_ == -1) {
         try {
            OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
            omeXMLBaseLength_ = service.getOMEXML(metadata_).length();
         } catch (Exception ex) {
            ReportingUtils.logError("Unable to calculate OME XML Base length");
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
            if (tags.has("Time") && !tags.isNull("Time")) {
               metadata_.setImageAcquisitionDate(new Timestamp(
                       DateTools.formatDate(tags.getString("Time"), "yyyy-MM-dd HH:mm:ss")), position);
            }
         } catch (Exception e) {
            ReportingUtils.logError("Problem adding System state cache metadata to OME Metadata: " + e);
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

         if (tags.has("Exposure-ms") && !tags.isNull("Exposure-ms")) {
            metadata_.setPlaneExposureTime(tags.getDouble("Exposure-ms") / 1000.0, position, indices.planeIndex_);
         }
         if (tags.has("XPositionUm") && !tags.isNull("XPositionUm")) {
            metadata_.setPlanePositionX(tags.getDouble("XPositionUm"), position, indices.planeIndex_);
            if (indices.planeIndex_ == 0) { //should be set at start, but dont have position coordinates then
               metadata_.setStageLabelX(tags.getDouble("XPositionUm"), position);
            }
         }
         if (tags.has("YPositionUm") && !tags.isNull("YPositionUm")) {
            metadata_.setPlanePositionY(tags.getDouble("YPositionUm"), position, indices.planeIndex_);
            if (indices.planeIndex_ == 0) {
               metadata_.setStageLabelY(tags.getDouble("YPositionUm"), position);
            }
         }
         if (tags.has("ZPositionUm") && !tags.isNull("ZPositionUm")) {
            metadata_.setPlanePositionZ(tags.getDouble("ZPositionUm"), position, indices.planeIndex_);
         }
         if (tags.has("ElapsedTime-ms") && !tags.isNull("ElapsedTime-ms")) {
            metadata_.setPlaneDeltaT(tags.getDouble("ElapsedTime-ms") / 1000.0, position, indices.planeIndex_);
         }

      } catch (JSONException e) {
         ReportingUtils.logError("Problem adding tags to OME Metadata");
      }

      indices.planeIndex_++;
      indices.tiffDataIndex_++;

      //This code is used is estimating the length of OME XML to be added in, so
      //images arent written into file space reserved for it
       if (omeXMLImageLength_ == -1) {
         //This is the first image plane to be written, so calculate the change in length from the base OME
         //XML length to estimate the approximate memory needed per an image plane
         try {
            OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
            omeXMLImageLength_ = (int) (1.1 * (service.getOMEXML(metadata_).length() - omeXMLBaseLength_));
         } catch (Exception ex) {
            ReportingUtils.logError("Unable to calculate OME XML Image length");
         }
      }
   }

   private void setOMEDetectorMetadata(JSONObject tags) throws JSONException {
      if (!tags.has("Core-Camera") || tags.isNull("Core-Camera")) {
         return;
      }
      String coreCam = tags.getString("Core-Camera");
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

