///////////////////////////////////////////////////////////////////////////////
//FILE:          MultipageTiffWriter.java
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


import ij.ImageJ;
import ij.io.TiffDecoder;
import ij.process.LUT;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class MultipageTiffWriter {
   
//   private static final long BYTES_PER_MEG = 1048576;
//   private static final long MAX_FILE_SIZE = 5*BYTES_PER_MEG;
   private static final long BYTES_PER_GIG = 1073741824;
   private static final long MAX_FILE_SIZE = 4 * BYTES_PER_GIG;
   public static final int DISPLAY_SETTINGS_BYTES_PER_CHANNEL = 256;
   //1 MB for now...might have to increase
   public static final long SPACE_FOR_COMMENTS = 1048576;
   public static final int INDEX_MAP_OFFSET_HEADER = 54773648;
   public static final int INDEX_MAP_HEADER = 3453623;
   public static final int DISPLAY_SETTINGS_OFFSET_HEADER = 483765892;
   public static final int DISPLAY_SETTINGS_HEADER = 347834724;
   public static final int COMMENTS_OFFSET_HEADER = 99384722;
   public static final int COMMENTS_HEADER = 84720485;
  
   public static final char ENTRIES_PER_IFD = 13;
   //Required tags
   public static final char WIDTH = 256;
   public static final char HEIGHT = 257;
   public static final char BITS_PER_SAMPLE = 258;
   public static final char COMPRESSION = 259;
   public static final char PHOTOMETRIC_INTERPRETATION = 262;
   public static final char IMAGE_DESCRIPTION = 270;
   public static final char STRIP_OFFSETS = 273;
   public static final char SAMPLES_PER_PIXEL = 277;
   public static final char ROWS_PER_STRIP = 278;
   public static final char STRIP_BYTE_COUNTS = 279;
   public static final char X_RESOLUTION = 282;
   public static final char Y_RESOLUTION = 283;
   public static final char RESOLUTION_UNIT = 296;
   public static final char IJ_METADATA_BYTE_COUNTS = TiffDecoder.META_DATA_BYTE_COUNTS;
   public static final char IJ_METADATA = TiffDecoder.META_DATA;
   public static final char MM_METADATA = 51123;
   
   public static final int SUMMARY_MD_HEADER = 2355492;
   
   public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
   
   
   final private boolean omeTiff_;
   
   private TaggedImageStorageMultipageTiff masterMPTiffStorage_;
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_; 
   private long filePosition_ = 0;
   private int bufferPosition_;
   private int numChannels_ = 1, numFrames_ = 1, numSlices_ = 1;
   private HashMap<String, Long> indexMap_;
   private long nextIFDOffsetLocation_ = -1;
   private boolean rgb_ = false;
   private int byteDepth_, imageWidth_, imageHeight_, bytesPerImagePixels_;
   private long resNumerator_ = 1, resDenomenator_ = 1;
   private LinkedList<ByteBuffer> buffers_;
   private boolean firstIFD_ = true;
   private long omeDescriptionTagPosition_;
   private long ijDescriptionTagPosition_;
   private long ijMetadataCountsTagPosition_;
   private long ijMetadataTagPosition_;
   //Reader associated with this file
   private MultipageTiffReader reader_;
   private long blankPixelsOffset_ = -1;

   public MultipageTiffWriter(String directory, String filename, 
           JSONObject summaryMD, TaggedImageStorageMultipageTiff mpTiffStorage) {
      masterMPTiffStorage_ = mpTiffStorage;
      omeTiff_ = mpTiffStorage.omeTiff_;        
      reader_ = new MultipageTiffReader(summaryMD);
      File f = new File(directory + "/" + filename); 
      
      try {
         processSummaryMD(summaryMD);
      } catch (MMScriptException ex1) {
         ReportingUtils.logError(ex1);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      
      //This is an overestimate of file size because file gets truncated at end
      long fileSize = Math.min(MAX_FILE_SIZE, summaryMD.toString().length() + 2000000
              + numFrames_ * numChannels_ * numSlices_ * ((long) bytesPerImagePixels_ + 2000));
      
      try {
         f.createNewFile();
         raFile_ = new RandomAccessFile(f, "rw");
         try {
            raFile_.setLength(fileSize);
         } catch (IOException e) {       
          new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {}
                    MMStudioMainFrame.getInstance().getAcquisitionEngine().abortRequest();
                } }).start();     
                ReportingUtils.showError("Insufficent space on disk: no room to write data");
         }
         fileChannel_ = raFile_.getChannel();
         indexMap_ = new HashMap<String, Long>();
         reader_.setFileChannel(fileChannel_);
         reader_.setIndexMap(indexMap_);
         buffers_ = new LinkedList<ByteBuffer>();
         
         writeMMHeaderAndSummaryMD(summaryMD);
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }
   
   public MultipageTiffReader getReader() {
      return reader_;
   }
   
   public FileChannel getFileChannel() {
      return fileChannel_;
   }
   
   public HashMap<String, Long> getIndexMap() {
      return indexMap_;
   }
   
   private void writeMMHeaderAndSummaryMD(JSONObject summaryMD) throws IOException {      
      if (summaryMD.has("Comment")) {
         summaryMD.remove("Comment");
      }
      String summaryMDString = summaryMD.toString();
      int mdLength = summaryMDString.length();
      ByteBuffer buffer = ByteBuffer.allocate(40).order(BYTE_ORDER);
      if (BYTE_ORDER.equals(ByteOrder.BIG_ENDIAN)) {
         buffer.asCharBuffer().put(0,(char) 0x4d4d);
      } else {
         buffer.asCharBuffer().put(0,(char) 0x4949);
      }
      buffer.asCharBuffer().put(1,(char) 42);
      buffer.putInt(4,40 + mdLength);
      //8 bytes for file header +
      //8 bytes for index map offset header and offset +
      //8 bytes for display settings offset header and display settings offset
      //8 bytes for comments offset header and comments offset
      //8 bytes for summaryMD header  summary md length + 
      //1 byte for each character of summary md     
      buffer.putInt(32,SUMMARY_MD_HEADER);
      buffer.putInt(36,mdLength);
      ByteBuffer[] buffers = new ByteBuffer[2];
      buffers[0] = buffer;
      buffers[1] = ByteBuffer.wrap(getBytesFromString(summaryMDString));
      fileChannel_.write(buffers);
      filePosition_ += buffer.position() + mdLength;
   }

   public void close(String omeXML) throws IOException {
      writeNullOffsetAfterLastImage();
      writeIndexMap();

      writeImageJMetadata( numChannels_);

      if (omeTiff_) {
         try {
            writeImageDescription(omeXML, omeDescriptionTagPosition_);                 
         } catch (Exception ex) {
            ReportingUtils.showError("Error writing OME metadata");
         }
      }
      writeImageDescription(getIJDescriptionString(), ijDescriptionTagPosition_); 
      
      writeDisplaySettings();
      writeComments();

      //extra byte of space, just to make sure nothing gets cut off
      raFile_.setLength(filePosition_ + 8);
      reader_.finishedWriting();
      //Dont close file channel and random access file becase Tiff reader still using them
      fileChannel_ = null;
      raFile_ = null;    
   }
   
   public boolean hasSpaceToWrite(TaggedImage img, int omeMDLength) {
      int mdLength = img.tags.toString().length();
      int indexMapSize = indexMap_.size()*20 + 8;
      int IFDSize = ENTRIES_PER_IFD*12 + 4 + 16;
      //5 MB extra padding
      int extraPadding = 5000000; 
      long size = mdLength+indexMapSize+IFDSize+bytesPerImagePixels_+SPACE_FOR_COMMENTS+
      numChannels_ * DISPLAY_SETTINGS_BYTES_PER_CHANNEL + extraPadding + filePosition_;
      if (omeTiff_) {
         size += omeMDLength;
      }
      
      if ( size >= MAX_FILE_SIZE) {
         return false;
      }
      return true;
   }
   
   public boolean isClosed() {
      return raFile_ == null;
   }
   
   public void writeBlankImage(String label) throws IOException {
      System.out.println("Writing blank: " + label);
      writeBlankIFD();
      writeBuffers();
   }
        
   public void writeImage(TaggedImage img) throws IOException {
      long offset = filePosition_;
      writeIFD(img);
      indexMap_.put(MDUtils.getLabel(img.tags), offset);
      writeBuffers();
   }
   
   private void writeBuffers() throws IOException {
      ByteBuffer[] buffs = new ByteBuffer[buffers_.size()];
      for (int i = 0; i < buffs.length; i++) {
         buffs[i] = buffers_.removeFirst();
      }
      fileChannel_.write(buffs);
   }

   private void writeIFD(TaggedImage img) throws IOException {
      char numEntries = (char) (((firstIFD_ && omeTiff_) ? ENTRIES_PER_IFD + 2 : ENTRIES_PER_IFD)
              + (firstIFD_ ? 2 : 0));
      if (img.tags.has("Summary")) {
         img.tags.remove("Summary");
      }
      String mdString = img.tags.toString() + " ";

      //2 bytes for number of directory entries, 12 bytes per directory entry, 4 byte offset of next IFD
     //6 bytes for bits per sample if RGB, 16 bytes for x and y resolution, 1 byte per character of MD string
     //number of bytes for pixels
     int totalBytes = 2 + numEntries*12 + 4 + (rgb_?6:0) + 16 + mdString.length() + bytesPerImagePixels_;
     int IFDandBitDepthBytes = 2+ numEntries*12 + 4 + (rgb_?6:0);
     
     ByteBuffer ifdBuffer = ByteBuffer.allocate(IFDandBitDepthBytes).order(BYTE_ORDER);
     CharBuffer charView = ifdBuffer.asCharBuffer();
         
     long tagDataOffset = filePosition_ + 2 + numEntries*12 + 4;
     nextIFDOffsetLocation_ = filePosition_ + 2 + numEntries*12;
     
     bufferPosition_ = 0;
      charView.put(bufferPosition_,numEntries);
      bufferPosition_ += 2;
      writeIFDEntry(ifdBuffer,charView, WIDTH,(char)4,1,imageWidth_);
      writeIFDEntry(ifdBuffer,charView,HEIGHT,(char)4,1,imageHeight_);
      writeIFDEntry(ifdBuffer,charView,BITS_PER_SAMPLE,(char)3,rgb_?3:1,  rgb_? tagDataOffset:byteDepth_*8);
      if (rgb_) {
         tagDataOffset += 6;
      }
      writeIFDEntry(ifdBuffer,charView,COMPRESSION,(char)3,1,1);
      writeIFDEntry(ifdBuffer,charView,PHOTOMETRIC_INTERPRETATION,(char)3,1,rgb_?2:1);
      
      if (firstIFD_ && omeTiff_) {
                  omeDescriptionTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer, charView, IMAGE_DESCRIPTION, (char) 2, 0, 0);
      }     
      if (firstIFD_) {
         ijDescriptionTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer, charView, IMAGE_DESCRIPTION, (char) 2, 0, 0);
      }
           
      writeIFDEntry(ifdBuffer,charView,STRIP_OFFSETS,(char)4,1, tagDataOffset );
      tagDataOffset += bytesPerImagePixels_;
      writeIFDEntry(ifdBuffer,charView,SAMPLES_PER_PIXEL,(char)3,1,(rgb_?3:1));
      writeIFDEntry(ifdBuffer,charView,ROWS_PER_STRIP, (char) 3, 1, imageHeight_);
      writeIFDEntry(ifdBuffer,charView,STRIP_BYTE_COUNTS, (char) 4, 1, bytesPerImagePixels_ );
      writeIFDEntry(ifdBuffer,charView,X_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(ifdBuffer,charView,Y_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(ifdBuffer,charView,RESOLUTION_UNIT, (char) 3,1,3);
      if (firstIFD_) {         
         ijMetadataCountsTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer,charView,IJ_METADATA_BYTE_COUNTS,(char)4,0,0);
         ijMetadataTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer,charView,IJ_METADATA,(char)1,0,0);
      }
      writeIFDEntry(ifdBuffer,charView,MM_METADATA,(char)2,mdString.length(),tagDataOffset);
      tagDataOffset += mdString.length();
      //NextIFDOffset
      ifdBuffer.putInt(bufferPosition_, (int)tagDataOffset);
      bufferPosition_ += 4;
      
      if (rgb_) {
         charView.put(bufferPosition_/2,(char) (byteDepth_*8));
         charView.put(bufferPosition_/2+1,(char) (byteDepth_*8));
         charView.put(bufferPosition_/2+2,(char) (byteDepth_*8));
      }
      buffers_.add(ifdBuffer);
      buffers_.add(getPixelBuffer(img));
      buffers_.add(getResolutionValuesBuffer());   
      buffers_.add(ByteBuffer.wrap(getBytesFromString(mdString)));
      
      filePosition_ += totalBytes;
      firstIFD_ = false;
   }

   private void writeIFDEntry(ByteBuffer buffer, CharBuffer cBuffer, char tag, char type, long count, long value) throws IOException {
      cBuffer.put(bufferPosition_ / 2, tag);
      cBuffer.put(bufferPosition_ / 2 + 1, type);
      buffer.putInt(bufferPosition_ + 4, (int) count);
      if (type ==3 && count == 1) {  //Left justify in 4 byte value field
         cBuffer.put(bufferPosition_/2 + 4, (char) value);
         cBuffer.put(bufferPosition_/2 + 5,(char) 0);
      } else {
         buffer.putInt(bufferPosition_ + 8, (int) value);
      }      
      bufferPosition_ += 12;
   }

   private ByteBuffer getResolutionValuesBuffer() throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(16).order(BYTE_ORDER);
      buffer.putInt(0,(int)resNumerator_);
      buffer.putInt(4,(int)resDenomenator_);
      buffer.putInt(8,(int)resNumerator_);
      buffer.putInt(12,(int)resDenomenator_);
      return buffer;
   }

   public void setAbortedNumFrames(int n) {
      numFrames_ = n;
   }

   private ByteBuffer getPixelBuffer(TaggedImage img) throws IOException {
      if (rgb_) {
         if (byteDepth_ == 1) {
            byte[] originalPix = (byte[]) img.pix;
            byte[] pix = new byte[originalPix.length * 3 / 4];
            int count = 0;
            for (int i = 0; i < originalPix.length; i++) {
               if ((i + 1) % 4 != 0) {
                  pix[count] = originalPix[i];
                  count++;
               }
            }
            return ByteBuffer.wrap(pix);
         } else {
            short[] originalPix = (short[]) img.pix;
            short[] pix = new short[originalPix.length * 3 / 4];
            int count = 0;
            for (int i = 0; i < originalPix.length; i++) {
               if ((i + 1) % 4 != 0) {
                  pix[count] = originalPix[i];
                  count++;
               }
            }
            ByteBuffer buffer = ByteBuffer.allocate(pix.length * 2).order(BYTE_ORDER);
            buffer.asShortBuffer().put(pix);
            return buffer;
         }
      } else {
         if (byteDepth_ == 1) {
            return ByteBuffer.wrap((byte[]) img.pix);
         } else {
            short[] pix = (short[]) img.pix;
            ByteBuffer buffer = ByteBuffer.allocate(pix.length * 2).order(BYTE_ORDER);
            buffer.asShortBuffer().put(pix);
            return buffer;
         }
      }
   }

   private void processSummaryMD(JSONObject summaryMD) throws MMScriptException, JSONException {
      rgb_ = MDUtils.isRGB(summaryMD);
      numChannels_ = MDUtils.getNumChannels(summaryMD);
      numFrames_ = MDUtils.getNumFrames(summaryMD);
      numSlices_ = MDUtils.getNumSlices(summaryMD);
      imageWidth_ = MDUtils.getWidth(summaryMD);
      imageHeight_ = MDUtils.getHeight(summaryMD);
      String pixelType = MDUtils.getPixelType(summaryMD);
      if (pixelType.equals("GRAY8") || pixelType.equals("RGB32") || pixelType.equals("RGB24")) {
         byteDepth_ = 1;
      } else if (pixelType.equals("GRAY16") || pixelType.equals("RGB64")) {
         byteDepth_ = 2;
      } else if (pixelType.equals("GRAY32")) {
         byteDepth_ = 3;
      } else {
         byteDepth_ = 2;
      }
      bytesPerImagePixels_ = imageHeight_ * imageWidth_ * byteDepth_ * (rgb_ ? 3 : 1);
      //Tiff resolution tag values
      if (summaryMD.has("PixelSizeUm")) {
         double cmPerPixel = 0.0001;
         try {
            cmPerPixel = 0.0001 * summaryMD.getDouble("PixelSizeUm");
         } catch (JSONException ex) {
         }
         double log = Math.log10(cmPerPixel);
         if (log >= 0) {
            resDenomenator_ = (long) cmPerPixel;
            resNumerator_ = 1;
         } else {
            resNumerator_ = (long) (1 / cmPerPixel);
            resDenomenator_ = 1;
         }
      }
   }

   /**
    * writes channel LUTs and display ranges for composite mode Could also be
    * expanded to write ROIs, file info, slice labels, and overlays
    */
   private void writeImageJMetadata(int numChannels) throws IOException {
      //size entry (4 bytes) + 4 bytes for channel display 
      //ranges length + 4 bytes per channel LUT
      int mdByteCountsBufferSize = 4 + 4 + 4 * numChannels;
      int bufferPosition = 0;

      ByteBuffer mdByteCountsBuffer = ByteBuffer.allocate(mdByteCountsBufferSize).order(BYTE_ORDER);

      //nTypes is number actually written among: fileInfo, slice labels, display ranges, channel LUTS,
      //slice labels, ROI, overlay, and # of extra metadata entries
      int nTypes = 2; //slice labels, display ranges, and channel LUTs
      int mdBufferSize = 4 + nTypes * 8;
      //4 bytes for magic number 8 bytes for label and count of each type

      mdByteCountsBuffer.putInt(bufferPosition, 4 + nTypes * 8);
      bufferPosition += 4;

      //display ranges written as array of doubles (min, max, min, max, etc)
      mdByteCountsBuffer.putInt(bufferPosition, numChannels * 2 * 8);
      bufferPosition += 4;
      mdBufferSize += numChannels * 2 * 8;

      for (int i = 0; i < numChannels; i++) {
         //768 bytes per LUT
         mdByteCountsBuffer.putInt(bufferPosition, 768);
         bufferPosition += 4;
         mdBufferSize += 768;
      }

      ByteBuffer ifdCountAndValueBuffer = ByteBuffer.allocate(8).order(BYTE_ORDER);
      ifdCountAndValueBuffer.putInt(0, mdByteCountsBufferSize);
      ifdCountAndValueBuffer.putInt(4, (int) filePosition_);
      fileChannel_.write(ifdCountAndValueBuffer, ijMetadataCountsTagPosition_ + 4);

      fileChannel_.write(mdByteCountsBuffer, filePosition_);
      filePosition_ += mdByteCountsBufferSize;


      //Write actual metadata
      ByteBuffer mdBuffer = ByteBuffer.allocate(mdBufferSize).order(BYTE_ORDER);
      bufferPosition = 0;

      //All the ints declared below are non public field in TiffDecoder
      int ijMagicNumber = 0x494a494a;
      mdBuffer.putInt(bufferPosition, ijMagicNumber);
      bufferPosition += 4;

      //Write ints for each IJ metadata field and its count
      int displayRanges = 0x72616e67;
      mdBuffer.putInt(bufferPosition, displayRanges);
      bufferPosition += 4;
      mdBuffer.putInt(bufferPosition, 1);
      bufferPosition += 4;

      int luts = 0x6c757473;
      mdBuffer.putInt(bufferPosition, luts);
      bufferPosition += 4;
      mdBuffer.putInt(bufferPosition, numChannels);
      bufferPosition += 4;


      try {
         JSONArray channels = masterMPTiffStorage_.getDisplayAndComments().getJSONArray("Channels");
         JSONObject channelSetting;
         for (int i = 0; i < numChannels; i++) {
            channelSetting = channels.getJSONObject(i);
            //For each channel, write min then max
            mdBuffer.putDouble(bufferPosition, channelSetting.getInt("Min"));
            bufferPosition += 8;
            mdBuffer.putDouble(bufferPosition, channelSetting.getInt("Max"));
            bufferPosition += 8;
         }

         for (int i = 0; i < numChannels; i++) {
            channelSetting = channels.getJSONObject(i);
            LUT lut = ImageUtils.makeLUT(new Color(channelSetting.getInt("Color")), channelSetting.getDouble("Gamma"));
            for (byte b : lut.getBytes()) {
               mdBuffer.put(bufferPosition, b);
               bufferPosition++;
            }
         }
      } catch (JSONException ex) {
         ReportingUtils.logError("Problem with displayAndComments: Couldn't write ImageJ display settings as a result");
      }

      ifdCountAndValueBuffer = ByteBuffer.allocate(8).order(BYTE_ORDER);
      ifdCountAndValueBuffer.putInt(0, mdBufferSize);
      ifdCountAndValueBuffer.putInt(4, (int) filePosition_);
      fileChannel_.write(ifdCountAndValueBuffer, ijMetadataTagPosition_ + 4);


      fileChannel_.write(mdBuffer, filePosition_);
      filePosition_ += mdBufferSize;
   }

   private String getIJDescriptionString() {
      StringBuffer sb = new StringBuffer();
      sb.append("ImageJ=" + ImageJ.VERSION + "\n");
      if (numChannels_ > 1) {
         sb.append("channels=" + numChannels_ + "\n");
      }
      if (numSlices_ > 1) {
         sb.append("slices=" + numSlices_ + "\n");
      }
      if (numFrames_ > 1) {
         sb.append("frames=" + numFrames_ + "\n");
      }
      if (numFrames_ > 1 || numSlices_ > 1 || numChannels_ > 1) {
         sb.append("hyperstack=true\n");
      }
      if (numChannels_ > 1 && numSlices_ > 1 && masterMPTiffStorage_.slicesFirst()) {
         sb.append("order=zct\n");
      }
      //cm so calibration unit is consistent with units used in Tiff tags
      sb.append("unit=cm\n");

      //write single channel contrast settings
      if (numChannels_ == 1) {
         try {
            JSONObject contrast = masterMPTiffStorage_.getDisplayAndComments().getJSONArray("Channels").getJSONObject(0);
            double min = contrast.getInt("Min");
            double max = contrast.getInt("Max");
            sb.append("min=" + min + "\n");
            sb.append("max=" + max + "\n");
         } catch (JSONException ex) {
         }
      }

      sb.append((char) 0);
      return new String(sb);
   }

   private void writeImageDescription(String value, long imageDescriptionTagOffset) throws IOException {
      //write first image IFD
      ByteBuffer ifdCountAndValueBuffer = ByteBuffer.allocate(8).order(BYTE_ORDER);
      ifdCountAndValueBuffer.putInt(0, value.length());
      ifdCountAndValueBuffer.putInt(4, (int) filePosition_);
      fileChannel_.write(ifdCountAndValueBuffer, imageDescriptionTagOffset + 4);

      //write String
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(value));
      fileChannel_.write(buffer, filePosition_);
      filePosition_ += buffer.capacity();
   }

   private byte[] getBytesFromString(String s) {
      try {
         return s.getBytes("US-ASCII");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError("Error encoding String to bytes");
         return null;
      }
   }

   private void writeNullOffsetAfterLastImage() throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.order(BYTE_ORDER);
      buffer.putInt(0, 0);
      fileChannel_.write(buffer, nextIFDOffsetLocation_);
   }

   private void writeComments() throws IOException {
      //Write 4 byte header, 4 byte number of bytes
      JSONObject comments;
      try {
         comments = masterMPTiffStorage_.getDisplayAndComments().getJSONObject("Comments");
      } catch (JSONException ex) {
         comments = new JSONObject();
      }
      String commentsString = comments.toString();
      ByteBuffer header = ByteBuffer.allocate(8).order(BYTE_ORDER);
      header.putInt(0, COMMENTS_HEADER);
      header.putInt(4, commentsString.length());
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(commentsString));
      fileChannel_.write(header, filePosition_);
      fileChannel_.write(buffer, filePosition_ + 8);

      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(BYTE_ORDER);
      offsetHeader.putInt(0, COMMENTS_OFFSET_HEADER);
      offsetHeader.putInt(4, (int) filePosition_);
      fileChannel_.write(offsetHeader, 24);
      filePosition_ += 8 + commentsString.length();
   }

   private void writeIndexMap() throws IOException {
      //Write 4 byte header, 4 byte number of entries, and 20 bytes for each entry
      int numMappings = indexMap_.size();
      ByteBuffer buffer = ByteBuffer.allocate(8 + 20 * numMappings).order(BYTE_ORDER);
      buffer.putInt(0, INDEX_MAP_HEADER);
      buffer.putInt(4, numMappings);
      int position = 2;
      for (String label : indexMap_.keySet()) {
         String[] indecies = label.split("_");
         for (String index : indecies) {
            buffer.putInt(4 * position, Integer.parseInt(index));
            position++;
         }
         buffer.putInt(4 * position, indexMap_.get(label).intValue());
         position++;
      }
      fileChannel_.write(buffer, filePosition_);

      ByteBuffer header = ByteBuffer.allocate(8).order(BYTE_ORDER);
      header.putInt(0, INDEX_MAP_OFFSET_HEADER);
      header.putInt(4, (int) filePosition_);
      fileChannel_.write(header, 8);
      filePosition_ += buffer.capacity();
   }

   private void writeDisplaySettings() throws IOException {
      JSONArray displaySettings;
      try {
         displaySettings = masterMPTiffStorage_.getDisplayAndComments().getJSONArray("Channels");
      } catch (JSONException ex) {
         displaySettings = new JSONArray();
      }
      int numReservedBytes = numChannels_ * DISPLAY_SETTINGS_BYTES_PER_CHANNEL;
      ByteBuffer header = ByteBuffer.allocate(8).order(BYTE_ORDER);
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(displaySettings.toString()));
      header.putInt(0, DISPLAY_SETTINGS_HEADER);
      header.putInt(4, numReservedBytes);
      fileChannel_.write(header, filePosition_);
      fileChannel_.write(buffer, filePosition_ + 8);

      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(BYTE_ORDER);
      offsetHeader.putInt(0, DISPLAY_SETTINGS_OFFSET_HEADER);
      offsetHeader.putInt(4, (int) filePosition_);
      fileChannel_.write(offsetHeader, 16);
      filePosition_ += numReservedBytes + 8;
   }
  
   private void writeBlankIFD() throws IOException {
//      boolean blankPixelsAlreadyWritten = blankPixelsOffset_ != -1;
      boolean blankPixelsAlreadyWritten = false;

      char numEntries = (char) (((firstIFD_ && omeTiff_) ? ENTRIES_PER_IFD + 2 : ENTRIES_PER_IFD)
              + (firstIFD_ ? 2 : 0));
     
      String mdString = "NULL ";

      //2 bytes for number of directory entries, 12 bytes per directory entry, 4 byte offset of next IFD
     //6 bytes for bits per sample if RGB, 16 bytes for x and y resolution, 1 byte per character of MD string
     //number of bytes for pixels
     int totalBytes = 2 + numEntries*12 + 4 + (rgb_?6:0) + 16 + mdString.length() 
             + (blankPixelsAlreadyWritten ? 0 : bytesPerImagePixels_);
     int IFDandBitDepthBytes = 2+ numEntries*12 + 4 + (rgb_?6:0);
     
     ByteBuffer ifdBuffer = ByteBuffer.allocate(IFDandBitDepthBytes).order(BYTE_ORDER);
     CharBuffer charView = ifdBuffer.asCharBuffer();
         
     long tagDataOffset = filePosition_ + 2 + numEntries*12 + 4;
     nextIFDOffsetLocation_ = filePosition_ + 2 + numEntries*12;
     
     bufferPosition_ = 0;
      charView.put(bufferPosition_,numEntries);
      bufferPosition_ += 2;
      writeIFDEntry(ifdBuffer,charView, WIDTH,(char)4,1,imageWidth_);
      writeIFDEntry(ifdBuffer,charView,HEIGHT,(char)4,1,imageHeight_);
      writeIFDEntry(ifdBuffer,charView,BITS_PER_SAMPLE,(char)3,rgb_?3:1,  rgb_? tagDataOffset:byteDepth_*8);
      if (rgb_) {
         tagDataOffset += 6;
      }
      writeIFDEntry(ifdBuffer,charView,COMPRESSION,(char)3,1,1);
      writeIFDEntry(ifdBuffer,charView,PHOTOMETRIC_INTERPRETATION,(char)3,1,rgb_?2:1);
      
      if (firstIFD_ && omeTiff_) {
                  omeDescriptionTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer, charView, IMAGE_DESCRIPTION, (char) 2, 0, 0);
      }     
      if (firstIFD_) {
         ijDescriptionTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer, charView, IMAGE_DESCRIPTION, (char) 2, 0, 0);
      }
           
      if (!blankPixelsAlreadyWritten) { //Write blank pixels
         writeIFDEntry(ifdBuffer, charView, STRIP_OFFSETS, (char) 4, 1, tagDataOffset);
         blankPixelsOffset_ = tagDataOffset;
         tagDataOffset += bytesPerImagePixels_;
      } else {
         writeIFDEntry(ifdBuffer, charView, STRIP_OFFSETS, (char) 4, 1, blankPixelsOffset_);
      }
      
      writeIFDEntry(ifdBuffer,charView,SAMPLES_PER_PIXEL,(char)3,1,(rgb_?3:1));
      writeIFDEntry(ifdBuffer,charView,ROWS_PER_STRIP, (char) 3, 1, imageHeight_);
      writeIFDEntry(ifdBuffer,charView,STRIP_BYTE_COUNTS, (char) 4, 1, bytesPerImagePixels_ );
      writeIFDEntry(ifdBuffer,charView,X_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(ifdBuffer,charView,Y_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(ifdBuffer,charView,RESOLUTION_UNIT, (char) 3,1,3);
      if (firstIFD_) {         
         ijMetadataCountsTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer,charView,IJ_METADATA_BYTE_COUNTS,(char)4,0,0);
         ijMetadataTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer,charView,IJ_METADATA,(char)1,0,0);
      }
      writeIFDEntry(ifdBuffer,charView,MM_METADATA,(char)2,mdString.length(),tagDataOffset);
      tagDataOffset += mdString.length();
      //NextIFDOffset
      ifdBuffer.putInt(bufferPosition_, (int)tagDataOffset);
      bufferPosition_ += 4;
      
      if (rgb_) {
         charView.put(bufferPosition_/2,(char) (byteDepth_*8));
         charView.put(bufferPosition_/2+1,(char) (byteDepth_*8));
         charView.put(bufferPosition_/2+2,(char) (byteDepth_*8));
      }
      buffers_.add(ifdBuffer);
      if (!blankPixelsAlreadyWritten) {
         buffers_.add(ByteBuffer.wrap(new byte[bytesPerImagePixels_]));
      }
      buffers_.add(getResolutionValuesBuffer());   
      buffers_.add(ByteBuffer.wrap(getBytesFromString(mdString)));
      
      filePosition_ += totalBytes;
      firstIFD_ = false;
   }
}
