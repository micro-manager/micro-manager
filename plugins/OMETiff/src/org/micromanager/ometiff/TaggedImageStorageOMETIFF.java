/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ometiff;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import loci.common.RandomAccessInputStream;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.ImageWriter;
import loci.formats.in.MinimalTiffReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import loci.formats.tiff.IFDList;
import loci.formats.tiff.TiffParser;
import mmcorej.TaggedImage;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class TaggedImageStorageOMETIFF implements TaggedImageStorage {
   public static String menuName_ = "OME TIFF";
   final private String location_;
   private boolean saved_ = true;
   private ImageWriter writer_ = null;
   private JSONObject summaryMetadata_ = null;
   private int planeIndex_;
   private JSONObject displayAndComments_;
   private final Boolean newData_;
   RandomAccessInputStream in_ = null;

   public TaggedImageStorageOMETIFF(String location, Boolean newData,
                                    JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      location_ = location;
      newData_ = newData;
   }

   @Override
   public TaggedImage getImage(int channel, int slice,
                               int frame, int position) {

      if (in_ == null) {
         try {
            in_ = new RandomAccessInputStream(location_ + ".ome.tiff");
         } catch (IOException ex) {
            in_ = null;
            return null;
         }
      }
      
      if (in_ != null) {
         try {
            TiffParser parser = new TiffParser(location_ + ".ome.tiff");
            long [] ifdOffsets = parser.getIFDOffsets();
            int no = getIndex(channel, slice, frame);
            if (no < ifdOffsets.length) {
               JSONObject tags = new JSONObject();
               MDUtils.setChannelIndex(tags, channel);
               MDUtils.setSliceIndex(tags, slice);
               MDUtils.setPositionIndex(tags, position);
               MDUtils.setFrameIndex(tags, frame);

               long offset = ifdOffsets[no];
               byte [] pix = new byte[512*512];
               in_.read(pix, (int) offset, 512*512);
               TaggedImage image = new TaggedImage(pix, tags);
               return image;
            }
            return null;
         } catch (Exception e) {
            ReportingUtils.logError(e);
            return null;
         }
      } else {
         return null;
      }
   }

   private int getIndex(int z, int c, int t) {
      final MetadataRetrieve md = writer_.getMetadataRetrieve();
      final int nZ = md.getPixelsSizeZ(0).getValue();
      final int nC = md.getPixelsSizeC(0).getValue();
      if (md.getPixelsDimensionOrder(0) == DimensionOrder.XYZCT) {
         return z + c * nZ + t * nC * nZ;
      } else {
         return c + z * nC + t * nZ * nC;
      }
   }

   private IFormatReader createOldDataReader() {
      ImageReader reader = new ImageReader();
      try {
         reader.setId(location_ + ".ome.tiff");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return reader;
   }

   private IFormatReader createNewDataReader() {
      try {
         if (writer_ == null) {
            return null;
         }
         final MetadataRetrieve md = writer_.getMetadataRetrieve();
         final int nZ = md.getPixelsSizeZ(0).getValue();
         final int nC = md.getPixelsSizeC(0).getValue();
         String filename = location_ + ".ome.tiff";
         if (new File(filename).exists()) {
            IFormatReader reader = new MinimalTiffReader() {
               @Override
               public int getImageCount() {
                  // Suppress error when image hasn't arrived yet.
                  return Integer.MAX_VALUE;
               }

               @Override
               public int getIndex(int z, int c, int t) {
                  updateIFDs();
                  if (md.getPixelsDimensionOrder(0) == DimensionOrder.XYZCT) {
                     return z + c * nZ + t * nC * nZ;
                  } else {
                     return c + z * nC + t * nZ * nC;
                  }
               }

               public byte[] openBytes(int no) throws IOException, FormatException {
                  updateIFDs();
                  return super.openBytes(no);
               }

               public void updateIFDs() {
                  try {
                     this.ifds = this.tiffParser.getNonThumbnailIFDs();
                  } catch (IOException ex) {
                     ReportingUtils.logError(ex);
                  }
               }
            };
            reader.setId(filename);
            return reader;
         } else {
            return null;
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
         return null;
      }

   }

   @Override
   public void finished() {
      if (writer_ != null)
         try {
            writer_.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
   }

   public void putImage(TaggedImage taggedImage) {
      try {
         if (writer_ == null) {
            setupWriter(taggedImage);
         }

         writer_.setSeries(MDUtils.getPositionIndex(taggedImage.tags));
         writer_.saveBytes(planeIndex_, (byte[]) taggedImage.pix);
         ++planeIndex_;
        
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   private void setupWriter(TaggedImage firstImage) throws Exception {
      writer_ = setupImageWriter(summaryMetadata_, firstImage);
      planeIndex_ = 0;
   }

   private ome.xml.model.enums.DimensionOrder
           computeDimensionOrder(JSONObject summaryMetadata) {
      try {
         boolean slicesFirst = summaryMetadata_.getBoolean("SlicesFirst");
         return slicesFirst ? DimensionOrder.XYZCT : DimensionOrder.XYCZT;
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         return DimensionOrder.XYCZT;
      }
   }

   private ImageWriter setupImageWriter(JSONObject summaryMetadata,
           TaggedImage firstImage) throws Exception {
      saved_ = true;
      int nPositions = Math.max(1, summaryMetadata.getInt("Positions"));
      int nChannels = Math.max(1, summaryMetadata.getInt("Channels"));
      int nFrames = Math.max(1, summaryMetadata.getInt("Frames"));
      int nSlices = Math.max(1, summaryMetadata.getInt("Slices"));
      OMEXMLMetadata metadata = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata();
      ImageWriter writer = new ImageWriter();
      
      for (int position = 0; position < nPositions; ++position) {
         String positionName = MDUtils.getPositionName(firstImage.tags);
         if (positionName == null) {
            positionName = "Single";
         }
         metadata.setImageID(positionName, position);
         metadata.setPixelsID("Pixels:" + position, position);
         metadata.setPixelsDimensionOrder(computeDimensionOrder(summaryMetadata), position);
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
      metadata.setUUID(MDUtils.getUUID(summaryMetadata_).toString());
      writer.setMetadataRetrieve(metadata);
      writer.setId(location_ + ".ome.tiff");
      
      return writer;
   }
   
   public String getDiskLocation() {
      if (saved_) {
         return location_ + ".ome.tiff";
      } else {
         return null;
      }
   }

   public void setSummaryMetadata(JSONObject md) {
      summaryMetadata_ = md;
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public void setDisplayAndComments(JSONObject settings) {
      displayAndComments_ = settings;
   }

   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }

   public void close() {
      try {
         in_.close();
         writer_.close();
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public int lastAcquiredFrame() {
      return 0;
   }

   public boolean isFinished() {
      throw new UnsupportedOperationException("Not supported yet.");
   }
}
