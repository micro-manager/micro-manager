
package HDF;

import ij.IJ;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.TreeMap;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;


public class HDFPreprocessor   {
       
   private int batchSize_;
   private ResolutionLevel[] resLevels_;
   private TreeMap<Integer, long[][]> histograms_;
   private int bitDepth_;
   private int width_,height_;
   private int startS_ = -1, startMin_ = -1, startHour_ = -1;
   private String acqStartDate_;
   private String acqDate_;
   private int numChannels_;
   
   public HDFPreprocessor(int width, int height, ResolutionLevel[] resLevels, int numChannels) {   
      bitDepth_ = 8;

      resLevels_ = resLevels;
      batchSize_ = resLevels_[resLevels_.length - 1].getReductionFactorZ();
      width_ = width;
      height_ = height;
      numChannels_ = numChannels;
      histograms_ = new TreeMap<Integer, long[][]>();
   }
   
   public PipelineImage process(LinkedList<TaggedImage> slices) throws Exception  {           
      if (startS_ == -1) {
         //first image
         String[] timeInfo = slices.get(0).tags.getString("Time").split(" ");
         startHour_ = Integer.parseInt(timeInfo[1].split(":")[0]);
         startMin_ = Integer.parseInt(timeInfo[1].split(":")[1]);
         startS_ = Integer.parseInt(timeInfo[1].split(":")[2]);
         acqStartDate_ = timeInfo[0];
         acqDate_ = timeInfo[0] + " " + timeInfo[1] + ".000";
      }
      int channel = MDUtils.getChannelIndex(slices.getFirst().tags);

      if (MDUtils.getSliceIndex( slices.getFirst().tags ) == 0) {
         histograms_.put(channel, new long[resLevels_.length][256]);
      }
      
      //Images is a list of slices with a size corresponding to the minumum number of slices
      //needed to write one slice of the lowest resolution level
      int numSlicesInChunk = slices.size();
      
      //Calculate downsampled resolutions
      //These are arrays of pixels used in downsampling, organized by resolution level index and slice index
      Object[][] downsampledPixSum = new Object[resLevels_.length][numSlicesInChunk];
      Object[][] pixelsToWrite = new Object[resLevels_.length][numSlicesInChunk];
      //copy over pixels for highest resolution
      for (int i = 0; i < numSlicesInChunk; i++) {
         pixelsToWrite[0][i] = slices.get(i).pix;
         if (pixelsToWrite[0][i] == null ) {
            break;
            //only occurs if incomplete set of slices gets sent to fill out a frame
         }
         if (bitDepth_ > 8) {
            for (short s : (short[]) slices.get(i).pix) {
                histograms_.get(channel)[0][(int)(255*((s & 0xffff) / Math.pow(2,bitDepth_)))]++;
            }
         } else {
            for (byte b : (byte[]) slices.get(i).pix) {
               histograms_.get(channel)[0][b & 0xff]++;
            }
         }
      } 
      //calculate and add pixels for lower resolutions
      for (int resLevel = 1; resLevel < resLevels_.length; resLevel++) {
         for (int i = 0; i < numSlicesInChunk; i++) {
            if ( i % resLevels_[resLevel].getReductionFactorZ() == 0) {
               //only create arrays when the slice index is a multiple of the resolution level's z downsample factor
               //these arrays are used to sum up all appropriate pixels values and then average them into
               //the new value at a lower resolution
               downsampledPixSum[resLevel][i] = 
                       new long[resLevels_[resLevel].getImageSizeX() * resLevels_[resLevel].getImageSizeY()];
               if (bitDepth_ > 8) {
                  pixelsToWrite[resLevel][i] =
                          new short[resLevels_[resLevel].getImageSizeX() * resLevels_[resLevel].getImageSizeY()];
               } else {
                  pixelsToWrite[resLevel][i] =
                          new byte[resLevels_[resLevel].getImageSizeX() * resLevels_[resLevel].getImageSizeY()];
               }
            }
         }
      }
      
      //This block sums up all pixel values from higher resolutions needed to create average values at lower
      //resolutions and then averages them
      int res0Width = width_;
      int numPixelsPerSlice = res0Width * height_;
      for  (int sliceIndex = 0; sliceIndex < numSlicesInChunk; sliceIndex++) {
         for ( int i = 0; i < numPixelsPerSlice; i++ ) {
            int x = i % res0Width;
            int y = i / res0Width;
            for (int resLevel = 1; resLevel < resLevels_.length; resLevel++ ) {
               int resLevelSizeX = resLevels_[resLevel].getImageSizeX();
               int resLevelSizeY = resLevels_[resLevel].getImageSizeY();
               int zDSFactor = resLevels_[resLevel].getReductionFactorZ();
               int xDSFactor = resLevels_[resLevel].getReductionFactorX();
               int yDSFactor = resLevels_[resLevel].getReductionFactorY();
               //dsX and dsY are the x and y coordinates of the pixel in the downsampled image
               int dsX = x/xDSFactor;
               int dsY = y/yDSFactor;
               if (dsX >= resLevelSizeX || dsY >= resLevelSizeY) {
                  //these pixels are cropped off at this resolution level, so skip them
                  continue;
               }
               
               //downsampled slice index is 
               int downsampledSliceIndex = sliceIndex - (sliceIndex % zDSFactor);
               int val;
               if (slices.get(sliceIndex).pix == null) {
                  val = 0;
                  //this should only occur in the rare situation in which the a blank slice has to be passed
                  //to fill out the end of stack that has been downsampled in z. Use the first slice in the 
                  //slice group (which must exist) to calculate the summed value. This way, the bottom slice
                  //in lower resolutions is not half as bright as others
                  if (bitDepth_ > 8) {
                     val = (((short[]) slices.get(0).pix)[i] & 0xffff);
                  } else {
                     val = (((byte[]) slices.get(0).pix)[i] & 0xff);
                  }
               } else if (bitDepth_ > 8) {
                  if (slices.get(sliceIndex) == null) {
                     System.out.println("null slice index");
                  } else if (slices.get(sliceIndex).pix == null) {
                     System.out.println("null pix");
                  }
                  val = (((short[]) slices.get(sliceIndex).pix)[i] & 0xffff);
                  histograms_.get(channel)[resLevel][(int)(255*(val / Math.pow(2,bitDepth_)))]++;
               } else {
                  val = (((byte[]) slices.get(sliceIndex).pix)[i] & 0xff);
                  //add pixel value to histogram
                  histograms_.get(channel)[resLevel][val]++;
               }
               ((long[])downsampledPixSum[resLevel][downsampledSliceIndex])[dsY*resLevelSizeX + dsX]  += val;        
               
               if (x % xDSFactor == xDSFactor - 1 && y % yDSFactor == yDSFactor - 1 && sliceIndex % zDSFactor == zDSFactor - 1) {
                  //all pixels for reslevel have been filled
                  int pixelIndex = (x / xDSFactor) + (y / yDSFactor) * resLevelSizeX;
                  //calculate average pixel value
                  if (bitDepth_ > 8) {
                     ((short[]) pixelsToWrite[resLevel][downsampledSliceIndex])[pixelIndex] =
                             (short) (((long[]) downsampledPixSum[resLevel][downsampledSliceIndex])[pixelIndex]
                             / (xDSFactor * yDSFactor * zDSFactor));
                  } else {
                     ((byte[]) pixelsToWrite[resLevel][downsampledSliceIndex])[pixelIndex] =
                             (byte) (((long[]) downsampledPixSum[resLevel][downsampledSliceIndex])[pixelIndex]
                             / (xDSFactor * yDSFactor * zDSFactor));
                  }
               }
            }
         }
      }  
      
      PipelineImage img = new PipelineImage(pixelsToWrite, MDUtils.getChannelIndex(slices.getFirst().tags),
              MDUtils.getSliceIndex(slices.getFirst().tags), MDUtils.getFrameIndex(slices.getFirst().tags),
              width_, height_, convertMMToImsTime(slices.getFirst().tags), acqDate_);
      
      //If this is the last slice in the frame, histograms are finished, so send them for writing
      if (MDUtils.getSliceIndex(slices.getFirst().tags) + batchSize_ >= resLevels_[0].getImageSizeZ()) {
         img.histograms = histograms_.get(channel);
         histograms_.put(channel, null);
      }  
      return img;
   }
   
   private String convertMMToImsTime(JSONObject tags) {
      int elapsedMs = 0; 
      try {
         elapsedMs = tags.getInt("ElapsedTime-ms") + startS_ * 1000 
              + startMin_ * 1000 * 60 + startHour_ * 60 * 60 * 1000;
      } catch (JSONException e) {}
      int h = elapsedMs / (60 * 60 * 1000);
      int min = (elapsedMs / (60 * 1000)) % 60;
      int s = (elapsedMs / 1000) % 60;
      int ms = elapsedMs % 1000;

      String timeMD = "";
      try {
         timeMD = tags.getString("Time");
      } catch (JSONException e) {}
      String date = timeMD.split(" ")[0];
      if (!date.equals(acqStartDate_)) {
         //for data sets spanning multiple days, only use number of hours into this day
         h = h % 24;
      }
      return date + " " + twoDigitFormat(h) + ":" + twoDigitFormat(min) + ":"
              + twoDigitFormat(s) + "." + threeDigitFormat(ms);
   }
   
   private String threeDigitFormat(int i) {
      String ret = i + "";
      if (ret.length() == 1) {
         ret = "00" + ret;
      } else if (ret.length() == 2) {
         ret = "0" + ret;
      }
      return ret;
   }

   private String twoDigitFormat(int i) {
      String ret = i + "";
      if (ret.length() == 1) {
         ret = "0" + ret;
      }
      return ret;
   }
}
