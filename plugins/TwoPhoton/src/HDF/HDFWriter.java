package HDF;

import ij.IJ;
import java.awt.Color;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.ReportingUtils;

public class HDFWriter {

   private static final Color[] DEFAULT_CHANNEL_COLORS =
           new Color[]{new Color(75, 0, 130), Color.blue, Color.green, Color.yellow,
      Color.red, Color.pink, Color.orange, Color.magenta};
   private static final String VERSION = "7.6";
   private int bitDepth_;
   private String acqDate_ = "2012-11-08 16:14:17.000";
   private int numChannels_, numFrames_;
   private int imageWidth_, imageHeight_, numSlices_;
   private double pixelSize_, pixelSizeZ_;
   private int fileID_;
   private int timeInfoID_;
   private DecimalFormat numberFormat_ = new DecimalFormat("#.###");
   private ResolutionLevel[] resLevels_;
   private int[] resLevelIDs_;
   private String directory_, filename_;
   private TimePoint currentTimePoint_;
   private int timePointImageCount_ = 0;
   private final boolean compressImageData_;
   private int slicesPerWrite_;
   private Color[] channelColors_;
   private boolean initialized_ = false;
   private boolean finished_ = false;

   public HDFWriter(String directory, String filename, int numChannels,
           int numFrames, int numSlices, double pixelSize, double pixelSizeZ, Color[] channelColors,
           int width, int height, ResolutionLevel[] resLevels) {
      compressImageData_ = true;
      directory_ = directory;
      filename_ = filename;
      numChannels_ = numChannels;
      numFrames_ = numFrames;
      numSlices_ = numSlices;
      pixelSize_ = pixelSize;
      pixelSizeZ_ = pixelSizeZ;
      if (channelColors == null) {
         channelColors_ = DEFAULT_CHANNEL_COLORS;
      } else {
         channelColors_ = channelColors;
      }
      bitDepth_ = 8;

      imageWidth_ = width;
      imageHeight_ = height;
      resLevels_ = resLevels;
      slicesPerWrite_ = resLevels_[resLevels_.length - 1].getReductionFactorZ();
   }

   public boolean isFinished() {
      return finished_;
   }

   public void finish() {
      try {
         //if canceled
         if (currentTimePoint_ != null) {
            currentTimePoint_.closeTimePoint();
         }

         H5.H5Gclose(timeInfoID_);
         for (int id : resLevelIDs_) {
            H5.H5Gclose(id);
         }
         finished_ = true;
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't finish Imaris file");
         e.printStackTrace();
      }
   }

   public void close() {
      try {
         //close file
         H5.H5Fclose(fileID_);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   //this function is not writing one image, but rather the minimum number of slices needed to 
   //write one image at the lowest resolution level
   public void writeImage(PipelineImage img) throws Exception {
      if (!initialized_) {
         acqDate_ = img.acqDate;
         createFile();
         initialized_ = true;
      }
      //if new timepoint
      if (timePointImageCount_ == 0) {
         currentTimePoint_ = new TimePoint(resLevels_, resLevelIDs_, numChannels_, img.frame,
                 bitDepth_, compressImageData_);
         HDFUtils.writeStringAttribute(timeInfoID_, "TimePoint" + (1 + img.frame), img.time);
      }

      currentTimePoint_.writePixels(img);


      if (numSlices_ % slicesPerWrite_ != 0 && img.slice + slicesPerWrite_ - 1 >= numSlices_) {
         //dont want to overcount extra slices that don't exist in original data
         timePointImageCount_ += numSlices_ % slicesPerWrite_;
      } else {
         timePointImageCount_ += slicesPerWrite_;
      }

      //close channels if full
      if (timePointImageCount_ == numChannels_ * numSlices_) {
         if (img.histograms == null) {
            IJ.log("histogram not created correctly");
            img.histograms = new long[resLevels_.length][256];
         }
         currentTimePoint_.closeTimePoint();
         currentTimePoint_ = null;
         timePointImageCount_ = 0;
      }
   }

   private void createFile() {
      try {
         if (!directory_.endsWith(File.separator)) {
            directory_ = directory_ + File.separator;
         }
         new File(directory_ + filename_).exists();
         fileID_ = H5.H5Fcreate(directory_ + filename_, HDF5Constants.H5P_DEFAULT,
                 HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
         addRootAttributes();
         makeDataSetInfo();
         makeDataSet();
      } catch (Exception e) {
         IJ.log("Couldn't create Imaris file");
         e.printStackTrace();
      }
   }

   private void addRootAttributes() throws HDF5LibraryException, HDF5Exception {
      HDFUtils.writeStringAttribute(fileID_, "DataSetDirectoryName", "DataSet");
      HDFUtils.writeStringAttribute(fileID_, "DataSetInfoDirectoryName", "DataSetInfo");
      HDFUtils.writeStringAttribute(fileID_, "ImarisDataSet", "ImarisDataSet");
      HDFUtils.writeStringAttribute(fileID_, "ImarisVersion", "5.5.0");
      HDFUtils.writeStringAttribute(fileID_, "ThumbnailDirectoryName", "Thumbnail");
      //Create number of datasets attribute
      int dataspaceID = H5.H5Screate_simple(1, new long[]{1}, null);
      int attID = H5.H5Acreate(fileID_, "NumberOfDataSets", HDF5Constants.H5T_NATIVE_UINT32, dataspaceID,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      H5.H5Awrite(attID, HDF5Constants.H5T_NATIVE_UINT32, new byte[]{1, 0, 0, 0});
      //Close dataspace and attribute
      H5.H5Sclose(dataspaceID);
      H5.H5Aclose(attID);
   }

   private void makeDataSetInfo() throws NullPointerException, HDF5LibraryException, HDF5Exception {
      int dataSetGroupID = H5.H5Gcreate(fileID_, "/DataSetInfo", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      //Channels
      for (int c = 0; c < numChannels_; c++) {
         int channelID = H5.H5Gcreate(dataSetGroupID, "Channel " + c, HDF5Constants.H5P_DEFAULT,
                 HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
         float[] rgb = channelColors_[c].getRGBColorComponents(null);
         HDFUtils.writeStringAttribute(channelID, "Color", numberFormat_.format(rgb[0]) + " "
                 + numberFormat_.format(rgb[1]) + " " + numberFormat_.format(rgb[2]));
         HDFUtils.writeStringAttribute(channelID, "ColorMode", "BaseColor");
         HDFUtils.writeStringAttribute(channelID, "ColorOpacity", "1.000");
         HDFUtils.writeStringAttribute(channelID, "ColorRange", "0 " + ((int) Math.pow(2, bitDepth_)));
         HDFUtils.writeStringAttribute(channelID, "Description", "(description not specified)");
         HDFUtils.writeStringAttribute(channelID, "GammaCorrection", "1.000");
         HDFUtils.writeStringAttribute(channelID, "Name", "(name not specified)");
         H5.H5Gclose(channelID);
      }

      //Image
      int imageID = H5.H5Gcreate(dataSetGroupID, "Image", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      HDFUtils.writeStringAttribute(imageID, "Description", "(description not specified)");
      HDFUtils.writeStringAttribute(imageID, "ExtMax0", numberFormat_.format(imageWidth_ * pixelSize_));
      HDFUtils.writeStringAttribute(imageID, "ExtMax1", numberFormat_.format(imageHeight_ * pixelSize_));
      HDFUtils.writeStringAttribute(imageID, "ExtMax2", numberFormat_.format(numSlices_ * pixelSizeZ_));
      HDFUtils.writeStringAttribute(imageID, "ExtMin0", "0");
      HDFUtils.writeStringAttribute(imageID, "ExtMin1", "0");
      HDFUtils.writeStringAttribute(imageID, "ExtMin2", "0");
      HDFUtils.writeStringAttribute(imageID, "Name", "(name not specified)");
      HDFUtils.writeStringAttribute(imageID, "RecordingDate", acqDate_);
      HDFUtils.writeStringAttribute(imageID, "Unit", "um");
      HDFUtils.writeStringAttribute(imageID, "X", imageWidth_ + "");
      HDFUtils.writeStringAttribute(imageID, "Y", imageHeight_ + "");
      HDFUtils.writeStringAttribute(imageID, "Z", numSlices_ + "");
      H5.H5Gclose(imageID);

      //Imaris
      int imarisID = H5.H5Gcreate(dataSetGroupID, "Imaris", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      HDFUtils.writeStringAttribute(imarisID, "Version", VERSION);
      H5.H5Gclose(imarisID);

      //ImarisDataSet
      int imarisDSID = H5.H5Gcreate(dataSetGroupID, "ImarisDataSet", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      HDFUtils.writeStringAttribute(imarisDSID, "Creator", "Imaricumpiler");
      HDFUtils.writeStringAttribute(imarisDSID, "NumberOfImages", "1");
      HDFUtils.writeStringAttribute(imarisDSID, "Version", VERSION);
      H5.H5Gclose(imarisDSID);

      //Log
      int logID = H5.H5Gcreate(dataSetGroupID, "Log", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      HDFUtils.writeStringAttribute(logID, "Entries", "0");
      H5.H5Gclose(logID);

      //TimeInfo
      timeInfoID_ = H5.H5Gcreate(dataSetGroupID, "TimeInfo", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      HDFUtils.writeStringAttribute(timeInfoID_, "DatasetTimePoints", numFrames_ + "");
      HDFUtils.writeStringAttribute(timeInfoID_, "FileTimePoints", numFrames_ + "");
      //close this at the end after all time points added

      H5.H5Gclose(dataSetGroupID);
   }

   private void makeDataSet() throws NullPointerException, HDF5LibraryException, HDF5Exception {
      resLevelIDs_ = new int[resLevels_.length];

      int dataSetGroupID = H5.H5Gcreate(fileID_, "/DataSet", HDF5Constants.H5P_DEFAULT,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

      //Make resolution levels
      for (int level = 0; level < resLevels_.length; level++) {
         resLevelIDs_[level] = H5.H5Gcreate(dataSetGroupID, "ResolutionLevel " + level,
                 HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      }
      H5.H5Gclose(dataSetGroupID);
   }
   
//    public TaggedImage readAsTaggedImage(int channel, int slice, int frame) throws HDF5LibraryException, HDF5Exception {
//      byte[] pixels = new byte[imageHeight_ * imageWidth_];
//
//      long t1 = System.currentTimeMillis();
//      int datasetID = H5.H5Dopen(fileID_, "/DataSet/ResolutionLevel 0/TimePoint " + frame + "/Channel " + channel + "/Data",
//              HDF5Constants.H5P_DEFAULT);
//      int filespaceID = H5.H5Screate_simple(3, new long[]{1, imageHeight_, imageWidth_}, null);
//      int memspaceID = H5.H5Screate_simple(1, new long[]{imageWidth_ * imageHeight_}, null);
//      long t2 = System.currentTimeMillis();
//
//      H5.H5Dread(datasetID, HDF5Constants.H5T_NATIVE_UCHAR, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, pixels);
//
//      long t3 = System.currentTimeMillis();
//      H5.H5Dclose(datasetID);
//      H5.H5Sclose(filespaceID);
//      H5.H5Sclose(memspaceID);
//      long t4 = System.currentTimeMillis();
//
//      System.out.println((t2 - t1) + "\t\t" + (t3 - t2) + "\t\t" + (t4 - t3));
//
//      //convert to Imaris format
//      JSONObject tags = new JSONObject();
//      try {
//         tags.put("ChannelIndex", channel);
//         tags.put("FrameIndex", frame);
//         tags.put("SliceIndex", slice);
//         tags.put("PositionIndex", 0);
//         tags.put("PixelType", "GRAY8");
//         tags.put("Width", imageWidth_);
//         tags.put("Height", imageHeight_);
//      } catch (JSONException ex) {
//         ReportingUtils.showError("Couldn't add image tags after reading from HDF");
//      }
//      return new TaggedImage(pixels, tags);
//   }
}
