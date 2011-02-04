/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.bioformats;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.ImageWriter;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageRam;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class TaggedImageStorageOMETIFF extends TaggedImageStorageRam {

   final private String location_;
   private boolean saved_ = true;

   public TaggedImageStorageOMETIFF(String location, Boolean newData, JSONObject summaryMetadata) {
      super(summaryMetadata);
      if (!newData) {
         loadImages();
      }
      location_ = location;
   }

   private void loadImages() {
      try {
         ImageReader reader = new ImageReader();
         reader.setId(location_);
         int nPositions = reader.getSeriesCount();
         int nChannels = reader.getSizeC();
         int nFrames = reader.getSizeT();
         int nSlices = reader.getSizeZ();
         int width = reader.getSizeX();
         int height = reader.getSizeY();

         for (int position = 0; position < nPositions; position++) {
            reader.setSeries(position);

            byte[] pixels = reader.openBytes(0);

            // this.putImage(null)
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }

   }

   @Override
   public void finished() {
      saveImages(super.getSummaryMetadata());
      super.finished();
   }

   private void saveImages(JSONObject summaryMetadata) {
      try {
         saved_ = true;
         int nPositions = Math.max(1, summaryMetadata.getInt("Positions"));
         int nChannels = Math.max(1, summaryMetadata.getInt("Channels"));
         int nFrames = Math.max(1, summaryMetadata.getInt("Frames"));
         int nSlices = Math.max(1, summaryMetadata.getInt("Slices"));

         OMEXMLMetadata metadata = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata();
         ImageWriter writer = new ImageWriter();

         for (int position = 0; position < nPositions; ++position) {
            String positionName = MDUtils.getPositionName(super.getImage(0, 0, 0, position).tags);
            if (positionName == null) {
               positionName = "Single";
            }
            metadata.setImageID(positionName, position);
            metadata.setPixelsID("Pixels:" + position, position);
            metadata.setPixelsDimensionOrder(DimensionOrder.XYZCT, position);
            metadata.setPixelsBinDataBigEndian(true, position, 0);
            metadata.setPixelsSizeX(new PositiveInteger(MDUtils.getWidth(summaryMetadata)), position);
            metadata.setPixelsSizeY(new PositiveInteger(MDUtils.getHeight(summaryMetadata)), position);
            metadata.setPixelsSizeZ(new PositiveInteger(nSlices), position);
            metadata.setPixelsSizeC(new PositiveInteger(nChannels), position);
            metadata.setPixelsSizeT(new PositiveInteger(nFrames), position);
            metadata.setPixelsType(PixelType.UINT8, position);
            for (int channel = 0; channel < nChannels; ++channel) {
               metadata.setChannelID("Channel:" + position + ":" + channel, position, channel);
               metadata.setChannelSamplesPerPixel(new PositiveInteger(1), position, channel);
            }
         }

         writer.setMetadataRetrieve(metadata);
         writer.setId(location_ + ".ome.tiff");

         for (int position = 0; position < nPositions; ++position) {
            int planeIndex = 0;
            writer.setSeries(position);
            for (int frame = 0; frame < nFrames; ++frame) {
               for (int channel = 0; channel < nChannels; ++channel) {
                  for (int slice = 0; slice < nSlices; ++slice) {
                     Object pix = super.getImage(channel, slice, frame, position).pix;
                     writer.saveBytes(planeIndex, (byte[]) pix);
                     ++planeIndex;
                  }
               }
            }
         }

         writer.close();
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }

   }

   @Override
   public String getDiskLocation() {
      if (saved_) {
         return location_ + ".ome.tiff";
      } else {
         return null;
      }
   }
}
