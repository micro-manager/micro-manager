/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.bioformats;

import loci.common.services.ServiceFactory;
import loci.formats.ImageReader;
import loci.formats.ImageWriter;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import mmcorej.TaggedImage;
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
   ImageReader reader_ = null;

   public TaggedImageStorageOMETIFF(String location, Boolean newData, JSONObject summaryMetadata) {
      super(summaryMetadata);
      if (!newData) {
         loadImages();
      }
      location_ = location;
   }

   @Override
   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      if (reader_ == null) {
         return super.getImage(channel, slice, frame, position);
      } else {
         JSONObject tags = new JSONObject();
         reader_.setSeries(position);
         Object pix = reader_.getIndex(slice, channel, frame);
         TaggedImage image = new TaggedImage(pix, tags);
         return image;
      }
   }


   private void loadImages() {
      try {
         reader_ = new ImageReader();
         reader_.setId(location_);
         int nPositions = reader_.getSeriesCount();
         int nChannels = reader_.getSizeC();
         int nFrames = reader_.getSizeT();
         int nSlices = reader_.getSizeZ();
         int width = reader_.getSizeX();
         int height = reader_.getSizeY();
         
         MetadataStore metadata = reader_.getMetadataStore();

         for (int position = 0; position < nPositions; position++) {
            reader_.setSeries(position);

            byte[] pixels = reader_.openBytes(0);

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
