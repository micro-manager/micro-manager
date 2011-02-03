/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.bioformats;

import loci.common.services.ServiceFactory;
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
   final private String dir_;

   public TaggedImageStorageOMETIFF(String dir, Boolean newData, JSONObject summaryMetadata) {
      super(summaryMetadata);
      dir_ = dir;
   }

   @Override
   public void finished() {
      saveImages(super.getSummaryMetadata());
      super.finished();
   }

   private void saveImages(JSONObject summaryMetadata) {
      try {
         int nPositions = Math.max(1,summaryMetadata.getInt("Positions"));
         int nChannels = Math.max(1,summaryMetadata.getInt("Channels"));
         int nFrames = Math.max(1,summaryMetadata.getInt("Frames"));
         int nSlices = Math.max(1,summaryMetadata.getInt("Slices"));

         OMEXMLMetadata metadata = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata();
         ImageWriter writer = new ImageWriter();
         
         for (int position = 0; position < nPositions; ++position) {
            String positionName = MDUtils.getPositionName(super.getImage(0,0,0, position).tags);
            if (positionName == null)
               positionName = "Single";
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
               metadata.setChannelID("Channel:"+position+":"+channel, position, channel);
               metadata.setChannelSamplesPerPixel(new PositiveInteger(1), position, channel);
            }
         }

         writer.setMetadataRetrieve(metadata);
         writer.setId(dir_ + "/img.ome.tiff");

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
}
