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
import java.util.TreeSet;
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
   private TreeMap<Integer, Indices> series_ = new TreeMap<Integer, Indices>();
   private int omeXMLBaseLength_ = -1;
   private int omeXMLImageLength_ = -1;

   private class Indices {
      //specific to each series independent of file
      int tiffDataIndex_ = -1;
      //count for each tiffData
      int tiffDataPlaneCount_ = 0;
      //specific to each series indpeendent of file
      int planeIndex_ = 0;
   }
   
   public OMEMetadata(TaggedImageStorageMultipageTiff mpt) {
      mptStorage_ = mpt;
      metadata_ = MetadataTools.createOMEXMLMetadata();
   }

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
      series_.put(seriesIndex, new Indices());  
      //Last one is samples per pixel
      JSONObject summaryMD = mptStorage_.getSummaryMetadata();
      MetadataTools.populateMetadata(metadata_, seriesIndex, baseFileName, MultipageTiffWriter.BYTE_ORDER.equals(ByteOrder.LITTLE_ENDIAN),
              mptStorage_.slicesFirst() ? "XYZCT" : "XYCZT", "uint" + (MDUtils.isGRAY8(summaryMD) ? "8" : "16"),
              MDUtils.getWidth(summaryMD), MDUtils.getHeight(summaryMD),
              MDUtils.getNumSlices(summaryMD), MDUtils.getNumChannels(summaryMD),
              MDUtils.getNumFrames(summaryMD), 1);

      if (summaryMD.has("PixelSize_um") && !summaryMD.isNull("PixelSize_um")) {
         double pixelSize = summaryMD.getDouble("PixelSize_um");
         if (pixelSize > 0) {
            metadata_.setPixelsPhysicalSizeX(new PositiveFloat(pixelSize), seriesIndex);
            metadata_.setPixelsPhysicalSizeY(new PositiveFloat(pixelSize), seriesIndex);
         }
      }
      if (summaryMD.has("z-step_um") && !summaryMD.isNull("z-step_um")) {
         double zStep = summaryMD.getDouble("z-step_um");
         if (zStep > 0) {
            metadata_.setPixelsPhysicalSizeZ(new PositiveFloat(zStep), seriesIndex);
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

   public void addImageTagsToOME(JSONObject tags, int ifdCount, String baseFileName, String currentFileName)
           throws JSONException, MMScriptException {
      int seriesIndex;
      try {
         seriesIndex = MDUtils.getPositionIndex(tags);
      } catch (Exception e) {
         seriesIndex = 0;
      }
      if (!series_.containsKey(seriesIndex)) {
         startSeriesMetadata(tags, seriesIndex, baseFileName);
         try {
            //Add these tags in only once, but need to get them from image rather than summary metadata
            setOMEDetectorMetadata(tags);
            if (tags.has("Time") && !tags.isNull("Time")) {
               metadata_.setImageAcquisitionDate(new Timestamp(
                       DateTools.formatDate(tags.getString("Time"), "yyyy-MM-dd HH:mm:ss")), seriesIndex);
            }
         } catch (Exception e) {
            ReportingUtils.logError("Problem adding System state cahce metadata to OME Metadata");
         }
      }

      Indices indices = series_.get(seriesIndex);

      //Required tags: Channel, slice, and frame index
      try {
         int slice = MDUtils.getSliceIndex(tags);
         int frame = MDUtils.getFrameIndex(tags);
         int channel = MDUtils.getChannelIndex(tags);

         //New tiff data if unexpected index, or new file
         boolean newTiffData = !mptStorage_.hasExpectedImageOrder() || ifdCount == 0 || 
                 indices.tiffDataPlaneCount_ == 0;
         // ifdCount is 0 when a new file started, tiff data plane count is 0 at a new position
         if (newTiffData ) {   //create new tiff data element
            indices.tiffDataIndex_++;
            metadata_.setTiffDataFirstZ(new NonNegativeInteger(slice), seriesIndex, indices.tiffDataIndex_);
            metadata_.setTiffDataFirstC(new NonNegativeInteger(channel), seriesIndex, indices.tiffDataIndex_);
            metadata_.setTiffDataFirstT(new NonNegativeInteger(frame), seriesIndex, indices.tiffDataIndex_);
            metadata_.setTiffDataIFD(new NonNegativeInteger(ifdCount), seriesIndex, indices.tiffDataIndex_);
            metadata_.setUUIDFileName(currentFileName, seriesIndex, indices.tiffDataIndex_);
            indices.tiffDataPlaneCount_ = 1;
         } else {   //continue adding to previous tiffdata element
            indices.tiffDataPlaneCount_++;
         }
         metadata_.setTiffDataPlaneCount(new NonNegativeInteger(indices.tiffDataPlaneCount_),
                 seriesIndex, indices.tiffDataIndex_);


         metadata_.setPlaneTheZ(new NonNegativeInteger(slice), seriesIndex, indices.planeIndex_);
         metadata_.setPlaneTheC(new NonNegativeInteger(channel), seriesIndex, indices.planeIndex_);
         metadata_.setPlaneTheT(new NonNegativeInteger(frame), seriesIndex, indices.planeIndex_);
      } catch (JSONException ex) {
         ReportingUtils.showError("Image Metadata missing ChannelIndex, SliceIndex, or FrameIndex");
      } catch (Exception e) {
         ReportingUtils.logError("Couldn't add to OME metadata");
      }

      //Optional tags
      try {

         if (tags.has("Exposure-ms") && !tags.isNull("Exposure-ms")) {
            metadata_.setPlaneExposureTime(tags.getDouble("Exposure-ms") / 1000.0, seriesIndex, indices.planeIndex_);
         }
         if (tags.has("XPositionUm") && !tags.isNull("XPositionUm")) {
            metadata_.setPlanePositionX(tags.getDouble("XPositionUm"), seriesIndex, indices.planeIndex_);
            if (indices.planeIndex_ == 0) { //should be set at start, but dont have position coordinates then
               metadata_.setStageLabelX(tags.getDouble("XPositionUm"), seriesIndex);
            }
         }
         if (tags.has("YPositionUm") && !tags.isNull("YPositionUm")) {
            metadata_.setPlanePositionY(tags.getDouble("YPositionUm"), seriesIndex, indices.planeIndex_);
            if (indices.planeIndex_ == 0) {
               metadata_.setStageLabelY(tags.getDouble("YPositionUm"), seriesIndex);
            }
         }
         if (tags.has("ZPositionUm") && !tags.isNull("ZPositionUm")) {
            metadata_.setPlanePositionZ(tags.getDouble("ZPositionUm"), seriesIndex, indices.planeIndex_);
         }
         if (tags.has("ElapsedTime-ms") && !tags.isNull("ElapsedTime-ms")) {
            metadata_.setPlaneDeltaT(tags.getDouble("ElapsedTime-ms") / 1000.0, seriesIndex, indices.planeIndex_);
         }

      } catch (JSONException e) {
         ReportingUtils.logError("Problem adding tags to OME Metadata");
      }

      indices.planeIndex_++;

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

