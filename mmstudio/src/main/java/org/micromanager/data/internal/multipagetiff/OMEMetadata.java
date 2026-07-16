///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Multipage TIFF
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//               Chris Weisiger, cweisiger@msg.ucsf.edu
//
// COPYRIGHT:    University of California, San Francisco, 2012-2015
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

package org.micromanager.data.internal.multipagetiff;

// Note: java.awt.Color and ome.xml.model.primitives.Color used with
// fully-qualified class names

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import loci.common.DateTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mmcorej.org.json.JSONException;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.NamingConvention;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.MultiWellPlate;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.CommentsHelper;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

public final class OMEMetadata {

   private final IMetadata metadata_;
   private final StorageMultipageTiff mptStorage_;
   private final TreeMap<Integer, Indices> seriesIndices_ = new TreeMap<Integer, Indices>();
   private final TreeMap<String, Integer> tiffDataIndexMap_;
   private int numSlices_;
   private int numChannels_;

   private class Indices {
      //specific to each series independent of file
      int tiffDataIndex_ = -1;
      //specific to each series independent of file
      int planeIndex_ = 0;
   }

   public OMEMetadata(StorageMultipageTiff mpt) {
      mptStorage_ = mpt;
      tiffDataIndexMap_ = new TreeMap<String, Integer>();
      metadata_ = MetadataTools.createOMEXMLMetadata();
      if (mptStorage_.getSummaryMetadata() != null && mptStorage_.getSummaryMetadata()
               .getStagePositionList() != null) {
         addPlateMetadata(mptStorage_.getSummaryMetadata().getStagePositionList());
      }
   }

   private void addPlateMetadata(List<MultiStagePosition> positions) {
      if (positions.isEmpty()) {
         return;
      }
      SummaryMetadata summaryMD = mptStorage_.getSummaryMetadata();
      if (summaryMD == null) {
         return;
      }
      MultiWellPlate plate = summaryMD.getMultiWellPlate();
      if (plate == null) {
         return;
      }

      final int plateIdx = 0;
      // The OME PlateID attribute is a structural LSID and must match the
      // "Plate:<n>" pattern; it is not a free-form identifier. (MM's
      // MultiWellPlate.getPlateID() is documented as "any String that uniquely
      // identifies the plate", e.g. a UUID, which violates that pattern.) Always
      // emit a schema-valid LSID here and preserve MM's identifier as the
      // free-form PlateExternalIdentifier instead.
      metadata_.setPlateID(MetadataTools.createLSID("Plate", plateIdx), plateIdx);
      if (plate.getPlateName() != null && !plate.getPlateName().isEmpty()) {
         metadata_.setPlateName(plate.getPlateName(), plateIdx);
      }
      if (plate.getPlateDescription() != null && !plate.getPlateDescription().isEmpty()) {
         metadata_.setPlateDescription(plate.getPlateDescription(), plateIdx);
      }
      // Prefer an explicitly supplied external identifier; otherwise fall back to
      // MM's plate ID (typically a UUID) so it is not lost.
      if (plate.getPlateExternalIdentifier() != null
            && !plate.getPlateExternalIdentifier().isEmpty()) {
         metadata_.setPlateExternalIdentifier(plate.getPlateExternalIdentifier(), plateIdx);
      } else if (plate.getPlateID() != null && !plate.getPlateID().isEmpty()) {
         metadata_.setPlateExternalIdentifier(plate.getPlateID(), plateIdx);
      }
      if (plate.getPlateStatus() != null && !plate.getPlateStatus().isEmpty()) {
         metadata_.setPlateStatus(plate.getPlateStatus(), plateIdx);
      }
      if (plate.getPlateRows() != null && plate.getPlateRows() > 0) {
         metadata_.setPlateRows(new PositiveInteger(plate.getPlateRows()), plateIdx);
      }
      if (plate.getPlateColumns() != null && plate.getPlateColumns() > 0) {
         metadata_.setPlateColumns(new PositiveInteger(plate.getPlateColumns()), plateIdx);
      }
      // Default rows to LETTER and columns to NUMBER when unspecified; these same
      // values drive both the OME metadata and the well label parsing below, so
      // the two cannot disagree.
      final MultiWellPlate.WellNamingConvention mmRowConvention =
            plate.getPlateRowNamingConvention() == MultiWellPlate.WellNamingConvention.NUMBER
            ? MultiWellPlate.WellNamingConvention.NUMBER
            : MultiWellPlate.WellNamingConvention.LETTER;
      final MultiWellPlate.WellNamingConvention mmColConvention =
            plate.getPlateColumnNamingConvention() == MultiWellPlate.WellNamingConvention.LETTER
            ? MultiWellPlate.WellNamingConvention.LETTER
            : MultiWellPlate.WellNamingConvention.NUMBER;
      NamingConvention rowConvention =
            mmRowConvention == MultiWellPlate.WellNamingConvention.NUMBER
            ? NamingConvention.NUMBER : NamingConvention.LETTER;
      NamingConvention colConvention =
            mmColConvention == MultiWellPlate.WellNamingConvention.LETTER
            ? NamingConvention.LETTER : NamingConvention.NUMBER;
      metadata_.setPlateRowNamingConvention(rowConvention, plateIdx);
      metadata_.setPlateColumnNamingConvention(colConvention, plateIdx);
      if (plate.getPlateWellOriginXUm() != null) {
         metadata_.setPlateWellOriginX(new Length(plate.getPlateWellOriginXUm(), UNITS.MICROMETER),
                  plateIdx);
      }
      if (plate.getPlateWellOriginYUm() != null) {
         metadata_.setPlateWellOriginY(new Length(plate.getPlateWellOriginYUm(), UNITS.MICROMETER),
                  plateIdx);
      }

      // Group positions into OME Wells. When HCS metadata is present each
      // MultiStagePosition carries a "Well" property (e.g. "A1") that names the
      // physical well; gridRow/gridCol on the MSP store the *site* index within
      // the well (not the well address). Use the "Well" property when available
      // to determine well identity; fall back to gridRow/gridCol otherwise.
      Map<String, Integer> wellKeyToWellIdx = new HashMap<String, Integer>();
      Map<String, Integer> wellKeySampleCount = new HashMap<String, Integer>();

      for (int positionIdx = 0; positionIdx < positions.size(); positionIdx++) {
         MultiStagePosition pos = positions.get(positionIdx);
         String wellProp = pos.getProperty("Well");
         int row;
         int col;
         String wellKey;
         if (wellProp != null && !wellProp.isEmpty()) {
            // Well property is present (e.g. "A1"): parse row/col from it using
            // the plate's declared naming conventions.
            int parsedRow = pos.getGridRow();
            int parsedCol = pos.getGridColumn();
            boolean parsed = false;
            try {
               int[] rowCol = MultiWellPlate.parseWellLabel(wellProp,
                     mmRowConvention, mmColConvention);
               parsedRow = rowCol[0];
               parsedCol = rowCol[1];
               parsed = true;
            } catch (IllegalArgumentException e) {
               // malformed Well label - fall back to gridRow/gridCol
               ReportingUtils.logError("Could not parse well label \"" + wellProp
                     + "\"; falling back to grid coordinates");
            }
            row = parsedRow;
            col = parsedCol;
            wellKey = parsed ? wellProp : row + "," + col;
         } else {
            row = pos.getGridRow();
            col = pos.getGridColumn();
            wellKey = row + "," + col;
         }

         if (!wellKeyToWellIdx.containsKey(wellKey)) {
            int wellIdx = wellKeyToWellIdx.size();
            wellKeyToWellIdx.put(wellKey, wellIdx);
            wellKeySampleCount.put(wellKey, 0);
            metadata_.setWellID(MetadataTools.createLSID("Well", plateIdx, wellIdx), plateIdx,
                     wellIdx);
            metadata_.setWellRow(new NonNegativeInteger(row), plateIdx, wellIdx);
            metadata_.setWellColumn(new NonNegativeInteger(col), plateIdx, wellIdx);
         }

         int wellIdx = wellKeyToWellIdx.get(wellKey);
         int sampleIdx = wellKeySampleCount.get(wellKey);
         wellKeySampleCount.put(wellKey, sampleIdx + 1);

         metadata_.setWellSampleID(
               MetadataTools.createLSID("WellSample", plateIdx, wellIdx, sampleIdx),
               plateIdx, wellIdx, sampleIdx);
         metadata_.setWellSampleIndex(new NonNegativeInteger(sampleIdx), plateIdx, wellIdx,
                  sampleIdx);
         // Use well-relative site offsets when available (set by HCS plugin);
         // fall back to absolute stage coordinates for non-HCS acquisitions.
         String offsetXStr = pos.getProperty("WellSiteOffsetXUm");
         String offsetYStr = pos.getProperty("WellSiteOffsetYUm");
         double sampleX = (offsetXStr != null && !offsetXStr.isEmpty())
               ? Double.parseDouble(offsetXStr) : pos.getX();
         double sampleY = (offsetYStr != null && !offsetYStr.isEmpty())
               ? Double.parseDouble(offsetYStr) : pos.getY();
         metadata_.setWellSamplePositionX(new Length(sampleX, UNITS.MICROMETER), plateIdx, wellIdx,
                  sampleIdx);
         metadata_.setWellSamplePositionY(new Length(sampleY, UNITS.MICROMETER), plateIdx, wellIdx,
                  sampleIdx);
         metadata_.setWellSampleImageRef(
               MetadataTools.createLSID("Image", positionIdx), plateIdx, wellIdx, sampleIdx);
      }
   }

   public static String getOMEStringPointerToMasterFile(String filename, String uuid) {
      try {
         IMetadata md = MetadataTools.createOMEXMLMetadata();
         md.setBinaryOnlyMetadataFile(filename);
         md.setBinaryOnlyUUID(uuid);
         return new ServiceFactory().getInstance(OMEXMLService.class).getOMEXML(md) + " ";
      } catch (DependencyException ex) {
         ReportingUtils.logError("Couldn't generate partial OME block");
         return " ";
      } catch (ServiceException ex) {
         ReportingUtils.logError("Couldn't generate partial OME block");
         return " ";
      }
   }

   @Override
   public String toString() {
      try {
         OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
         return service.getOMEXML(metadata_) + " ";
      } catch (DependencyException ex) {
         ReportingUtils.logError(ex);
         return "";
      } catch (ServiceException ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }

   public void setNumFrames(int seriesIndex, int numFrames) {
      metadata_.setPixelsSizeT(new PositiveInteger(numFrames), seriesIndex);
   }

   private void startSeriesMetadata(int seriesIndex, String baseFileName) {
      Indices indices = new Indices();
      indices.planeIndex_ = 0;
      indices.tiffDataIndex_ = 0;
      seriesIndices_.put(seriesIndex, indices);
      numSlices_ = mptStorage_.getIntendedSize(Coords.Z);
      numChannels_ = mptStorage_.getIntendedSize(Coords.CHANNEL);
      // We need to know bytes per pixel, which requires having an Image handy.
      // TODO: there's an implicit assumption here that all images in the
      // file have the same bytes per pixel.
      Image repImage = mptStorage_.getAnyImage();
      // Get the axis order.
      // TODO: Note that OME metadata *only* allows axis orders that contain
      // the letters XYZCT (and as far as I can tell it must contain each
      // letter once).
      String axisOrder = "XY";
      if (mptStorage_.getSummaryMetadata().getOrderedAxes() != null) {
         List<String> order = mptStorage_.getSummaryMetadata().getOrderedAxes();
         axisOrder += (order.indexOf(Coords.Z) < order.indexOf(Coords.CHANNEL))
               ? "ZCT" : "CZT";
      } else {
         // Make something up to have a valid string.
         axisOrder += "CZT";
      }
      //Last one is samples per pixel
      MetadataTools.populateMetadata(metadata_, seriesIndex, baseFileName,
            MultipageTiffWriter.BYTE_ORDER.equals(ByteOrder.LITTLE_ENDIAN),
            axisOrder,
            "uint" + repImage.getBytesPerPixel() * 8,
            repImage.getWidth(), repImage.getHeight(),
            numSlices_, numChannels_,
            mptStorage_.getIntendedSize(Coords.T), 1);

      Metadata repMetadata = repImage.getMetadata();
      if (repMetadata.getPixelSizeUm() != null) {
         double pixelSize = repMetadata.getPixelSizeUm();
         if (pixelSize > 0) {
            metadata_.setPixelsPhysicalSizeX(
                  new Length(pixelSize, UNITS.MICROMETER), seriesIndex);
            metadata_.setPixelsPhysicalSizeY(
                  new Length(pixelSize, UNITS.MICROMETER), seriesIndex);
         }
      }

      SummaryMetadata summaryMD = mptStorage_.getSummaryMetadata();
      if (summaryMD.getZStepUm() != null) {
         double zStep = summaryMD.getZStepUm();
         if (zStep != 0) {
            metadata_.setPixelsPhysicalSizeZ(
                  new Length(Math.abs(zStep), UNITS.MICROMETER), seriesIndex);
         }
      }

      if (summaryMD.getWaitInterval() != null) {
         double interval = summaryMD.getWaitInterval();
         if (interval > 0) { //don't write it for burst mode because it won't be true
            metadata_.setPixelsTimeIncrement(new Time(interval, UNITS.MILLISECOND), seriesIndex);
         }
      }

      String positionName = "pos" + repImage.getCoords().getStagePosition();
      if (!repMetadata.getPositionName("").equals("")) {
         positionName = repMetadata.getPositionName("");
      }
      metadata_.setStageLabelName(positionName, seriesIndex);

      String instrumentID = MetadataTools.createLSID("Instrument", 0);
      metadata_.setInstrumentID(instrumentID, 0);
      // link Instrument and Image
      metadata_.setImageInstrumentRef(instrumentID, seriesIndex);

      try {
         String summaryComment = CommentsHelper.getSummaryComment(
               mptStorage_.getDatastore());
         metadata_.setImageDescription(summaryComment, seriesIndex);
      } catch (IOException e) {
         // TODO Report error (not severe)
      }

      // TODO Also save channel colors (need display settings...)
      List<String> names = mptStorage_.getSummaryMetadata().getChannelNameList();
      for (int channel = 0; channel < mptStorage_.getIntendedSize(Coords.CHANNEL);
            channel++) {
         if (names != null && names.size() > channel) {
            metadata_.setChannelName(names.get(channel), seriesIndex, channel);
         }
      }
   }

   /**
    * Method called when numC*numZ*numT != total number of planes.
    */
   public void fillInMissingTiffDatas(int frame, int position) {
      try {
         for (int slice = 0; slice < numSlices_; slice++) {
            for (int channel = 0; channel < numChannels_; channel++) {
               //make sure each tiffdata entry is present. If it is missing, link Tiffdata entry
               //to a a preveious IFD
               Integer tiffDataIndex =
                     tiffDataIndexMap_.get(generateLabel(channel, slice, frame, position));
               if (tiffDataIndex == null) {
                  // this plane was never added, so link to another IFD
                  // find substitute channel, frame, slice
                  int s = slice;
                  int backIndex = slice - 1;
                  int forwardIndex = slice + 1;
                  int frameSearchIndex = frame;
                  // If some but not all channels have z stacks, find the closest
                  // slice for the given channel that has an image.  Also if time
                  // point missing, go back until image is found
                  while (tiffDataIndex == null) {
                     tiffDataIndex = tiffDataIndexMap_
                           .get(generateLabel(channel, s, frameSearchIndex, position));
                     if (tiffDataIndex != null) {
                        break;
                     }

                     if (backIndex >= 0) {
                        tiffDataIndex = tiffDataIndexMap_.get(generateLabel(
                                 channel, backIndex, frameSearchIndex, position));
                        if (tiffDataIndex != null) {
                           break;
                        }
                        backIndex--;
                     }
                     if (forwardIndex < numSlices_) {
                        tiffDataIndex = tiffDataIndexMap_.get(generateLabel(
                                 channel, forwardIndex, frameSearchIndex, position));
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

                  metadata_.setTiffDataFirstZ(new NonNegativeInteger(slice), position,
                        indices.tiffDataIndex_);
                  metadata_.setTiffDataFirstC(new NonNegativeInteger(channel), position,
                        indices.tiffDataIndex_);
                  metadata_.setTiffDataFirstT(new NonNegativeInteger(frame), position,
                        indices.tiffDataIndex_);

                  metadata_.setTiffDataIFD(ifd, position, indices.tiffDataIndex_);
                  metadata_.setUUIDFileName(filename, position, indices.tiffDataIndex_);
                  metadata_.setUUIDValue(uuid, position, indices.tiffDataIndex_);
                  metadata_.setTiffDataPlaneCount(new NonNegativeInteger(1), position,
                        indices.tiffDataIndex_);

                  indices.tiffDataIndex_++;
               }
            }
         }
      } catch (Exception e) {
         ReportingUtils.logError("Couldn't fill in missing tiffdata entries in ome metadata");
      }
   }

   public void addImageTagsToOME(Coords coords, Metadata metadata, int ifdCount,
                                 String baseFileName, String currentFileName, String uuid) {
      int position = coords.getStagePosition();
      if (!seriesIndices_.containsKey(position)) {
         startSeriesMetadata(position, baseFileName);
         try {
            // Add these tags in only once, but need to get them from image rather
            // than summary metadata
            setOMEDetectorMetadata(metadata);

            String imageTime = metadata.getReceivedTime();
            if (imageTime != null) {
               // Alas, the metadata "Time" field is in one of two formats.
               String reformattedDate = DateTools.formatDate(imageTime,
                     "yyyy-MM-dd HH:mm:ss Z", true);
               if (reformattedDate == null) {
                  reformattedDate = DateTools.formatDate(imageTime,
                        "yyyy-MM-dd E HH:mm:ss Z", true);
               }
               if (reformattedDate != null) {
                  metadata_.setImageAcquisitionDate(
                        new Timestamp(reformattedDate), position);
               }
            }
         } catch (IllegalArgumentException e) {
            ReportingUtils
                  .logError(e, "Problem adding System state cache metadata to OME Metadata: " + e);
         } catch (UnsupportedOperationException e) {
            ReportingUtils
                  .logError(e, "Problem adding System state cache metadata to OME Metadata: " + e);
         } catch (JSONException e) {
            ReportingUtils
                  .logError(e, "Problem adding System state cache metadata to OME Metadata: " + e);
         }
      }

      Indices indices = seriesIndices_.get(position);

      //Required tags: Channel, slice, and frame index
      int slice = coords.getZSlice();
      int frame = coords.getTimePoint();
      int channel = coords.getChannel();

      // ifdCount is 0 when a new file started, tiff data plane count is 0 at a new position
      try {
         metadata_
               .setTiffDataFirstZ(new NonNegativeInteger(slice), position, indices.tiffDataIndex_);
         metadata_.setTiffDataFirstC(new NonNegativeInteger(channel), position,
               indices.tiffDataIndex_);
         metadata_
               .setTiffDataFirstT(new NonNegativeInteger(frame), position, indices.tiffDataIndex_);
         metadata_
               .setTiffDataIFD(new NonNegativeInteger(ifdCount), position, indices.tiffDataIndex_);
         metadata_.setUUIDFileName(currentFileName, position, indices.tiffDataIndex_);
         metadata_.setUUIDValue(uuid, position, indices.tiffDataIndex_);
      } catch (IndexOutOfBoundsException ioe) {
         ReportingUtils.logError(ioe, "Error in OMEMData class");
         throw (new UnsupportedOperationException(
               "Multipage Tiff storage only supports images in increasing order, t=0, t=2, etc.."));
      }
      tiffDataIndexMap_
            .put(generateLabel(channel, slice, frame, position), indices.tiffDataIndex_);
      metadata_.setTiffDataPlaneCount(new NonNegativeInteger(1), position, indices.tiffDataIndex_);

      metadata_.setPlaneTheZ(new NonNegativeInteger(slice), position, indices.planeIndex_);
      metadata_.setPlaneTheC(new NonNegativeInteger(channel), position, indices.planeIndex_);
      metadata_.setPlaneTheT(new NonNegativeInteger(frame), position, indices.planeIndex_);

      //Optional tags
      Double exposureMs = metadata.getExposureMs();
      if (exposureMs != null) {
         metadata_.setPlaneExposureTime(new Time(exposureMs, UNITS.MILLISECOND),
               position, indices.planeIndex_);
      }
      Double xPositionUm = metadata.getXPositionUm();
      if (xPositionUm != null) {
         final Length xPosition =
               new Length(xPositionUm, UNITS.MICROMETER);
         metadata_.setPlanePositionX(xPosition, position, indices.planeIndex_);
         if (indices.planeIndex_
               == 0) { //should be set at start, but don't have position coordinates then
            metadata_.setStageLabelX(xPosition, position);
         }
      }
      Double yPositionUm = metadata.getYPositionUm();
      if (yPositionUm != null) {
         final Length yPosition =
               new Length(yPositionUm, UNITS.MICROMETER);
         metadata_.setPlanePositionY(yPosition, position, indices.planeIndex_);
         if (indices.planeIndex_ == 0) {
            metadata_.setStageLabelY(yPosition, position);
         }
      }
      Double zPositionUm = metadata.getZPositionUm();
      if (zPositionUm != null) {
         metadata_.setPlanePositionZ(new Length(zPositionUm, UNITS.MICROMETER),
               position, indices.planeIndex_);
      }
      double elapsedTimeMs = metadata.getElapsedTimeMs(-1.0);
      if (elapsedTimeMs >= 0.0) {
         metadata_.setPlaneDeltaT(new Time(elapsedTimeMs, UNITS.MILLISECOND),
               position, indices.planeIndex_);
      }
      String positionName = metadata.getPositionName("");
      if (!positionName.isEmpty()) {
         metadata_.setStageLabelName(positionName, position);
      }

      indices.planeIndex_++;
      indices.tiffDataIndex_++;
   }

   private void setOMEDetectorMetadata(Metadata metadata) throws JSONException {
      PropertyMap scopeData = metadata.getScopeData();
      if (scopeData == null || !scopeData.containsString("Core-Camera")) {
         return;
      }
      String coreCam = scopeData.getString("Core-Camera", null);
      List<String> cameras = new ArrayList<String>();
      // TODO This is really fragile and incorrect!
      for (int i = 0; i < 32; ++i) {
         String multiCamKey = coreCam + "-Physical Camera " + i;
         if (scopeData.containsKey(multiCamKey)) {
            String physCam = scopeData.getString(multiCamKey, null);
            if ("Undefined".equals(physCam)) {
               continue;
            }
            cameras.add(physCam);
         } else {
            break;
         }
      }
      if (cameras.isEmpty()) {
         cameras.add(coreCam);
      }

      for (int i = 0; i < cameras.size(); i++) {
         String camera = cameras.get(i);
         String detectorID = MetadataTools.createLSID("Detector", i);

         //Instrument index, detector index
         metadata_.setDetectorID(detectorID, 0, i);

         String name = scopeData.getString(camera + "-Name", "");
         if (!name.isEmpty()) {
            metadata_.setDetectorManufacturer(name, 0, i);
         }
         String cameraName = scopeData.getString(camera + "-CameraName", "");
         if (!cameraName.isEmpty()) {
            metadata_.setDetectorModel(cameraName, 0, i);
         }
         String offset = scopeData.getValueAsString(camera + "-Offset", "");
         if (!offset.isEmpty()) {
            metadata_.setDetectorOffset(Double.parseDouble(offset), 0, i);
         }
         String cameraId = scopeData.getString(camera + "-CameraID", "");
         if (!cameraId.isEmpty()) {
            metadata_.setDetectorSerialNumber(cameraId, 0, i);
         }
      }
   }

   public static String generateLabel(int channel, int slice, int frame, int position) {
      return NumberUtils.intToCoreString(channel) + "_"
               + NumberUtils.intToCoreString(slice) + "_"
               + NumberUtils.intToCoreString(frame) + "_"
               + NumberUtils.intToCoreString(position);
   }

}