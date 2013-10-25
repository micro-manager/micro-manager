package HDF;

//This class encapsulates all the data object IDs for a given timepoint
import ij.IJ;
import java.util.Arrays;
import java.util.LinkedList;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

public class TimePoint {

   private static final int HISTOGRAM_SIZE = 256;
   private ResolutionLevel[] resLevels_;
   //res index, channel index array of channel Groups
   private ChannelGroup[][] channelGroups_;
   private final boolean compressImageData_;

   //Constructor creates all data structures that are populated later
   public TimePoint(ResolutionLevel[] resLevels, int[] resLevelIDs, int numChannels, int frameIndex,
           int bitDepth, boolean compressImageData) throws HDF5LibraryException, HDF5Exception {
      compressImageData_ = compressImageData;
      resLevels_ = resLevels;
      channelGroups_ = new ChannelGroup[resLevels.length][numChannels];

      for (int resIndex = 0; resIndex < resLevels.length; resIndex++) {
         //Create time point
         int timePointID = H5.H5Gcreate(resLevelIDs[resIndex], "TimePoint " + frameIndex,
                 HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

         ResolutionLevel resLevel = resLevels[resIndex];
         for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
            channelGroups_[resIndex][channelIndex] = new ChannelGroup(timePointID, channelIndex, resLevel, bitDepth);
         }
         H5.H5Gclose(timePointID);
      }
   }

   //should be called by the addImage function of the HDFWriter
   public void writePixels(PipelineImage img) throws Exception {
      //This function recieves several slices processed into multiple resolutions, stored in a 
      //3 dimensional array with indices: resolution level, slice index, pixel index
      //At the lower resolutions, an image is stored at the lowest slice index of the higher resolution
      //images from which it is comprised
      int channel = img.channel;
            
      Object[][] imageData = (Object[][]) img.pixels;

      for (int resIndex = 0; resIndex < resLevels_.length; resIndex++) {
         //write histogram if last slice in channel
         if (img.histograms != null) {
            channelGroups_[resIndex][channel].writeHistogram(img,resIndex);
         }

         Object[] sliceArray = imageData[resIndex];
         for (int sliceIndex = 0; sliceIndex < sliceArray.length; sliceIndex++) {
            if (sliceArray[sliceIndex] != null) {
               //there is data in this slice at this resolution level
               int dataSlice = (img.slice + sliceIndex) / resLevels_[resIndex].getReductionFactorZ();
               channelGroups_[resIndex][channel].writeSlice(resLevels_[resIndex].getImageSizeX(),
                       resLevels_[resIndex].getImageSizeY(), dataSlice, sliceArray[sliceIndex]);
            }
         }
      }
   }


   //Close channel Group
   public void closeTimePoint() throws HDF5LibraryException, HDF5Exception {
      for (int res = 0; res < channelGroups_.length; res++) {
         for (int channel = 0; channel < channelGroups_[0].length; channel++) {
            channelGroups_[res][channel].close();
         }
      }
   }

   private class ChannelGroup {

      private ResolutionLevel resLevel_;
      private int[] histogramIDs_;
      private int[] imageDataIDs_;

      public ChannelGroup(int timePointID, int channelIndex, ResolutionLevel resLevel, int bitDepth) throws HDF5LibraryException, HDF5Exception {
         resLevel_ = resLevel;
         int id = H5.H5Gcreate(timePointID, "Channel " + channelIndex,
                 HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
         //Add channel attributes, image data, histogram
         HDFUtils.writeStringAttribute(id, "HistogramMax", ((int)Math.pow(2,bitDepth))+".000");
         HDFUtils.writeStringAttribute(id, "HistogramMin", "0.000");
         HDFUtils.writeStringAttribute(id, "ImageBlockSizeX", "" + resLevel_.getXBlockSize());
         HDFUtils.writeStringAttribute(id, "ImageBlockSizeY", "" + resLevel_.getYBlockSize());
         HDFUtils.writeStringAttribute(id, "ImageBlockSizeZ", "" + resLevel_.getZBlockSize());
         HDFUtils.writeStringAttribute(id, "ImageSizeX", "" + resLevel_.getImageSizeX());
         HDFUtils.writeStringAttribute(id, "ImageSizeY", "" + resLevel_.getImageSizeY());
         HDFUtils.writeStringAttribute(id, "ImageSizeZ", "" + resLevel_.getImageSizeZ());

//         Create histograms
         histogramIDs_ = HDFUtils.createDataSet(id, "Histogram", new long[]{HISTOGRAM_SIZE},
                 HDF5Constants.H5T_NATIVE_UINT64);


         //Create image datasets
         if (compressImageData_) {
            imageDataIDs_ = HDFUtils.createCompressedDataSet(id, "Data", new long[]{resLevel.getContainerSizeZ(),
                       resLevel.getContainerSizeY(), resLevel.getContainerSizeX()}, 
                    resLevel.getImageByteDepth() == 1 ? HDF5Constants.H5T_NATIVE_UCHAR : HDF5Constants.H5T_NATIVE_UINT16,
                    new long[]{resLevel.getZBlockSize(), resLevel.getYBlockSize(), resLevel.getXBlockSize()});
         } else {
            imageDataIDs_ = HDFUtils.createDataSet(id, "Data", new long[]{resLevel.getContainerSizeZ(),
                       resLevel.getContainerSizeY(), resLevel.getContainerSizeX()}, 
                    resLevel.getImageByteDepth() == 1 ? HDF5Constants.H5T_NATIVE_UCHAR : HDF5Constants.H5T_NATIVE_UINT16);
         }

         H5.H5Gclose(id);
      }

      private void writeHistogram(PipelineImage img, int resIndex) throws HDF5LibraryException, HDF5Exception {
//         Write and close histogram
         int memDataSpaceID = H5.H5Screate_simple(1, new long[]{HISTOGRAM_SIZE}, null);
         try {
            long[] histogram = img.histograms[resIndex];
            H5.H5Dwrite_long(histogramIDs_[2], histogramIDs_[1],
                    memDataSpaceID, histogramIDs_[0], HDF5Constants.H5P_DEFAULT, histogram);
         } catch (Exception e) {
            IJ.log("Couldn't write histogram: channel " + img.channel + " slice: " +
                    img.slice + " frame: " + img.frame + " resIndex: " + resIndex);
         }
         H5.H5Sclose(memDataSpaceID);
         closeHistograms();
         histogramIDs_ = null;
      }
      
      private void closeHistograms() throws HDF5LibraryException {
         H5.H5Sclose(histogramIDs_[0]);
         H5.H5Tclose(histogramIDs_[1]);
         H5.H5Dclose(histogramIDs_[2]);
      }

      private void close() throws HDF5LibraryException, HDF5Exception {
         if (histogramIDs_ != null) {
            //if writing cancelled
            closeHistograms();
         }
         
         //Close image data
         H5.H5Sclose(imageDataIDs_[0]);
         H5.H5Tclose(imageDataIDs_[1]);
         H5.H5Dclose(imageDataIDs_[2]);
         if (compressImageData_) {
            H5.H5Pclose(imageDataIDs_[3]);
         }
         imageDataIDs_ = null;

      }

      private void writeSlice(int width, int height, int dataSlice, Object pixels) {
         try {
            long[] start = new long[]{dataSlice, 0, 0};
            //count is total number of points in each dimension
            long[] count = new long[]{1, height, width};
            int ret = H5.H5Sselect_hyperslab(imageDataIDs_[0], HDF5Constants.H5S_SELECT_SET, start, null, count, null);

            //Create dataspace in memory to copy from
            int memDataSpaceID = H5.H5Screate_simple(1, new long[]{width * height}, null);
            ret = H5.H5Sselect_all(memDataSpaceID);

            ret = H5.H5Dwrite(imageDataIDs_[2], pixels instanceof byte[] ? HDF5Constants.H5T_NATIVE_UCHAR
                    : HDF5Constants.H5T_NATIVE_UINT16, memDataSpaceID, imageDataIDs_[0],
                    HDF5Constants.H5P_DEFAULT, pixels);

            H5.H5Sclose(memDataSpaceID);
         } catch (Exception e) {
            IJ.log("Couldnt write slice due to HDF exception");
         }
      }
   }
}