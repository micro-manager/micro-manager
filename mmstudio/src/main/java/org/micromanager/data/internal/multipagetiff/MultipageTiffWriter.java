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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.internal.MMStudio;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;


public class MultipageTiffWriter {

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
         
   public static final ByteOrder BYTE_ORDER = ByteOrder.nativeOrder();
   
   private StorageMultipageTiff masterStorage_;
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_; 
   private ThreadPoolExecutor writingExecutor_;
   private long filePosition_ = 0;
   private long indexMapPosition_; //current position of the dynamically written index map
   private long indexMapFirstEntry_; // mark position of first entry so that number of entries can be written at end
   private int bufferPosition_;
   private int numChannels_ = 1, numFrames_ = 1, numSlices_ = 1;
   private HashMap<Coords, Long> coordsToOffset_;
   private long nextIFDOffsetLocation_ = -1;
   private boolean rgb_ = false;
   private int byteDepth_, imageWidth_, imageHeight_, bytesPerImagePixels_;
   private long resNumerator_ = 1, resDenomenator_ = 1;
   private double zStepUm_ = 1;
   private LinkedList<ByteBuffer> buffers_;
   private boolean firstIFD_ = true;
   private long omeDescriptionTagPosition_;
   private long ijDescriptionTagPosition_;
   private long ijMetadataCountsTagPosition_;
   private long ijMetadataTagPosition_;
   //Reader associated with this file
   private MultipageTiffReader reader_;
   private long blankPixelsOffset_ = -1;
   
   public MultipageTiffWriter(StorageMultipageTiff masterStorage,
         JSONObject firstImageTags, String filename)
         throws IOException {
      masterStorage_ = masterStorage;
      // TODO: casting to DefaultSummaryMetadata here.
      DefaultSummaryMetadata summary = (DefaultSummaryMetadata) masterStorage.getSummaryMetadata();
      File f = new File(masterStorage.getDiskLocation() + "/" + filename);

      try {
         processSummaryMD(summary);
      } catch (MMScriptException ex1) {
         ReportingUtils.logError(ex1);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      
      // We need to convert the summary metadata into an extended version that
      // includes information that isn't in SummaryMetadata but that prior
      // versions of MicroManager used to include (that information is either
      // in per-image Metadata, part of the Image itself, or part of the
      // DisplaySettings now).
      // Additionally, in MM2.0 we store display settings in a separate file;
      // the settings we save here are solely to preserve backwards
      // compatibility with MM1.x.
      JSONObject summaryJSON = summary.toJSON();
      augmentWithImageMetadata(summaryJSON,
            (DefaultImage) masterStorage_.getAnyImage());
      augmentWithDisplaySettings(summaryJSON,
            DefaultDisplaySettings.getStandardSettings(DefaultDisplayWindow.DEFAULT_SETTINGS_KEY));
      reader_ = new MultipageTiffReader(summary, summaryJSON, firstImageTags);

      //This is an overestimate of file size because file gets truncated at end
      long fileSize = Math.min(MAX_FILE_SIZE,
            summaryJSON.toString().length() + 2000000 +
            numFrames_ * numChannels_ * numSlices_ * ((long) bytesPerImagePixels_ + 2000));

      f.createNewFile();
      raFile_ = new RandomAccessFile(f, "rw");
      try {
         raFile_.setLength(fileSize);
      } catch (IOException e) {       
       new Thread(new Runnable() {
             @Override
             public void run() {
                 try {
                     Thread.sleep(1000);
                 } catch (InterruptedException ex) {}
                 MMStudio.getInstance().getAcquisitionEngine().abortRequest();
             } }).start();     
             ReportingUtils.showError("Insufficent space on disk: no room to write data");
      }
      fileChannel_ = raFile_.getChannel();
      writingExecutor_ = masterStorage_.getWritingExecutor();
      coordsToOffset_ = new HashMap<Coords, Long>();
      reader_.setFileChannel(fileChannel_);
      reader_.setIndexMap(coordsToOffset_);
      buffers_ = new LinkedList<ByteBuffer>();
      
      writeMMHeaderAndSummaryMD(summaryJSON);
   }

   /**
    * Insert certain fields into the provided JSONObject that are stored in
    * the Image or its Metadata.
    */
   private void augmentWithImageMetadata(JSONObject summary,
         DefaultImage image) {
      try {
         MDUtils.setPixelType(summary, image.getImageJPixelType());
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't set image pixel type");
      }
      try {
         MDUtils.setWidth(summary, image.getWidth());
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't set image width");
      }
      try {
         MDUtils.setHeight(summary, image.getHeight());
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't set image height");
      }
   }

   /**
    * Insert certain fields into the provided JSONObject that are stored
    * in the given DisplaySettings.
    */
   private void augmentWithDisplaySettings(JSONObject summary,
         DefaultDisplaySettings settings) {
      try {
         summary.put("DisplaySettings", settings.toJSON());
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't add display settings to summary");
      }
   }

   private ByteBuffer allocateByteBuffer(int capacity) {
      return ByteBuffer.allocateDirect(capacity).order(BYTE_ORDER);
   }
   
   private BlockingQueue<ByteBuffer> currentImageByteBuffers_ = new LinkedBlockingQueue<ByteBuffer>(10);
   private int currentImageByteBufferCapacity_ = 0;
           
   private ByteBuffer allocateByteBufferMemo(int capacity) {
       // HACK: if we are running on 32-bit mode, then we don't want to cache
       // our direct buffers, due to increased memory constraints on 32-bit
       // systems.
       if (System.getProperty("sun.arch.data.model").equals("32")) {
           return allocateByteBuffer(capacity);
       }
       if (capacity != currentImageByteBufferCapacity_) {
           currentImageByteBuffers_.clear();
           currentImageByteBufferCapacity_ = capacity;
       }
       
       ByteBuffer cachedBuf = currentImageByteBuffers_.poll();
       return (cachedBuf != null) ? cachedBuf : allocateByteBuffer(capacity);
   }
   
   private void executeWritingTask(Runnable writingTask) {
      writingExecutor_.execute(writingTask);
   }
   
   private void fileChannelWrite(final ByteBuffer buffer, final long position) {
      executeWritingTask(
        new Runnable() {
           @Override
           public void run() {
             try {
                buffer.rewind();
                fileChannel_.write(buffer, position);
                if (buffer.limit() == currentImageByteBufferCapacity_) {
                    currentImageByteBuffers_.offer(buffer);
                }
              } catch (IOException e) {
                ReportingUtils.logError(e);
              }
           }
        });
   }
   
   private void fileChannelWrite(final ByteBuffer[] buffers) {
      executeWritingTask(
        new Runnable() {
           @Override
           public void run() {
             try {
                fileChannel_.write(buffers);
                for (ByteBuffer buffer:buffers) {
                    if (buffer.limit() == currentImageByteBufferCapacity_) {
                        currentImageByteBuffers_.offer(buffer);
                    }
                }
              } catch (IOException e) {
                ReportingUtils.logError(e);
              } 
           }
        });
   }
   
   public MultipageTiffReader getReader() {
      return reader_;
   }
   
   public HashMap<Coords, Long> getIndexMap() {
      return coordsToOffset_;
   }
   
   private void writeMMHeaderAndSummaryMD(JSONObject summaryMD) throws IOException {      
      if (summaryMD.has("Comment")) {
         ReportingUtils.logError("TODO: removing summary comment");
         summaryMD.remove("Comment");
      }
      byte[] summaryMDBytes = getBytesFromString(summaryMD.toString());
      int mdLength = summaryMDBytes.length;
      //20 bytes plus 8 header for index map
      long maxImagesInFile = MAX_FILE_SIZE / bytesPerImagePixels_;
      long indexMapSpace = 8 + 20 * maxImagesInFile;
      
      ByteBuffer headerBuffer = allocateByteBuffer(40);
      //8 bytes for file header
      if (BYTE_ORDER.equals(ByteOrder.BIG_ENDIAN)) {
         headerBuffer.asCharBuffer().put(0,(char) 0x4d4d);
      } else {
         headerBuffer.asCharBuffer().put(0,(char) 0x4949);
      }
      headerBuffer.asCharBuffer().put(1,(char) 42);
      headerBuffer.putInt(4,40 + (int) (mdLength + indexMapSpace));
      
      //8 bytes for index map offset header and offset
      headerBuffer.putInt(8,INDEX_MAP_OFFSET_HEADER);
      headerBuffer.putInt(12,headerBuffer.capacity() + mdLength);
      
      //8 bytes for display settings offset header and display settings offset--written later
      //8 bytes for comments offset header and comments offset--written later
      //8 bytes for summaryMD header  summary md length + 
      headerBuffer.putInt(32,SUMMARY_MD_HEADER);
      headerBuffer.putInt(36,mdLength);
      
      ByteBuffer indexMapBuffer = allocateByteBuffer((int) indexMapSpace);
      indexMapBuffer.putInt(0,INDEX_MAP_HEADER);
      indexMapBuffer.putInt(4,(int) maxImagesInFile);  
      indexMapPosition_ = headerBuffer.capacity() + mdLength + 8;
      indexMapFirstEntry_ = indexMapPosition_;

      //1 byte for each byte of UTF-8-encoded summary md
      ByteBuffer[] buffers = new ByteBuffer[3];
      buffers[0] = headerBuffer;
      buffers[1] = ByteBuffer.wrap(summaryMDBytes);
      buffers[2] = indexMapBuffer;
      
      fileChannelWrite(buffers);
      filePosition_ += headerBuffer.capacity() + mdLength +indexMapSpace;
   }
   
   /**
    * Called when there is no more data to be written. Write null offset after
    * last image in accordance with TIFF specification and set number of index
    * map entries for backwards reading capability A file that has been
    * finished should have everything it needs to be properly reopened in MM or
    * by a basic TIFF reader
    */
   public void finish() throws IOException {
      writeNullOffsetAfterLastImage();
      // go back to the index map header and change the number of entries from
      // the max value allotted early to the actual number written The
      // MultipageTiffReader no longer needs this because it interperets 0's as
      // the the end of the index map. It is added here for backwards
      // compatibility of reading using versions of MM before 6-6-2014. Without
      // it, old versions wouldn't correctly read image 0_0_0_0
      int numImages = (int) ((indexMapPosition_ - indexMapFirstEntry_) / 20);
      ByteBuffer indexMapNumEntries = allocateByteBuffer(4);
      indexMapNumEntries.putInt(0, numImages);
      fileChannelWrite(indexMapNumEntries, indexMapFirstEntry_ - 4);
   }

   /**
    * Called when entire set of files (i.e. acquisition) is finished. Adds in
    * all the extra (but nonessential) stuff--comments, display settings,
    * OME/IJ metadata, and truncates the file to a reasonable length
    */
   public void close(String omeXML) throws IOException {
      String summaryComment = "";
      try 
      {
         String comments = masterStorage_.getSummaryMetadata().getComments();
         if (comments != null) {
            summaryComment = comments;
         }    
      } catch (Exception e) {
         ReportingUtils.logError("Could't get acquisition summary comment");
      }
      writeImageJMetadata(numChannels_, summaryComment);

      try {
         writeImageDescription(omeXML, omeDescriptionTagPosition_);
      } catch (Exception ex) {
         ReportingUtils.showError("Error writing OME metadata");
      }
      writeImageDescription(getIJDescriptionString(),
            ijDescriptionTagPosition_); 
      
      writeDisplaySettings();
      writeComments();

      executeWritingTask(new Runnable() {
         @Override
         public void run() {
            try {
               // extra byte of space, just to make sure nothing gets cut off
               raFile_.setLength(filePosition_ + 8);
            } catch (IOException ex) {
               ReportingUtils.logError(ex);
            }
            reader_.finishedWriting();
            // Dont close file channel and random access file becase Tiff
            // reader still using them
            fileChannel_ = null;
            raFile_ = null;
         }
      });
   }
   
   public boolean hasSpaceForFullOMEMetadata(int length) {
      //5 MB extra padding..just to be safe
      int extraPadding = 5000000; 
      long size = length + SPACE_FOR_COMMENTS + numChannels_ * DISPLAY_SETTINGS_BYTES_PER_CHANNEL + extraPadding + filePosition_;
      if ( size >= MAX_FILE_SIZE) {
         return false;
      }
      return true;
   }
   
   public boolean hasSpaceToWrite(TaggedImage img, int omeMDLength) {
      int mdLength = img.tags.toString().length();
      int IFDSize = ENTRIES_PER_IFD*12 + 4 + 16;
      //5 MB extra padding...just to be safe...
      int extraPadding = 5000000; 
      long size = mdLength+IFDSize+bytesPerImagePixels_+SPACE_FOR_COMMENTS+
      numChannels_ * DISPLAY_SETTINGS_BYTES_PER_CHANNEL + extraPadding + filePosition_;
      size += omeMDLength;
      
      if ( size >= MAX_FILE_SIZE) {
         return false;
      }
      return true;
   }
   
   public boolean isClosed() {
      return raFile_ == null;
   }
   
   public void writeBlankImage() throws IOException {
      writeBlankIFD();
      writeBuffers();
   }
        
   public void writeImage(TaggedImage img) throws IOException {
      if (writingExecutor_ != null) {
         int queueSize = writingExecutor_.getQueue().size();
         int attemptCount = 0;
         while (queueSize > 20) {
            if (attemptCount == 0) {
               ReportingUtils.logMessage("Warning: writing queue behind by " + queueSize + " images.");
            }
            ++attemptCount;
            try {
               Thread.sleep(5);
               queueSize = writingExecutor_.getQueue().size();
            } catch (InterruptedException ex) {
               ReportingUtils.logError(ex);
            }
         }
      }
      long offset = filePosition_;
      writeIFD(img);
      addToIndexMap(DefaultCoords.legacyFromJSON(img.tags), offset);
      writeBuffers();
      //wait until image has finished writing to return
//      int size = writingExecutor_.getQueue().size();
//      while (size > 0) {
//         size = writingExecutor_.getQueue().size();
//      }
   }
   
   private void addToIndexMap(Coords coords, long offset) {
      //If a duplicate key is received, forget about the previous one
      //this allows overwriting of images without loss of data
      coordsToOffset_.put(coords, offset);
      ByteBuffer buffer = allocateByteBuffer(20);
      int bufOffset = 0;
      for (String axis : MultipageTiffReader.ALLOWED_AXES) {
         buffer.putInt(4 * bufOffset, coords.getIndex(axis));
         bufOffset++;
      }
      // TODO: this probably doesn't help our performance any, but I want
      // the extra logging just in case.
      for (String axis : coords.getAxes()) {
         if (!MultipageTiffReader.ALLOWED_AXES.contains(axis)) {
            ReportingUtils.logError("Axis " + axis + " is ignored because it is not one of " + MultipageTiffReader.ALLOWED_AXES.toString());
         }
      }

      buffer.putInt(16, new Long(offset).intValue());
      fileChannelWrite(buffer,indexMapPosition_);
      indexMapPosition_ += 20;  
   }
   
   private void writeBuffers() throws IOException {
      ByteBuffer[] buffs = new ByteBuffer[buffers_.size()];
      for (int i = 0; i < buffs.length; i++) {
         buffs[i] = buffers_.removeFirst();
      }
      fileChannelWrite(buffs);
   }
   
    private long unsignInt(int i) {
      long val = Integer.MAX_VALUE & i;
      if (i < 0) {
         val += (long) Math.pow(2, 31);
      }
      return val;
   }

   private void writeIFD(TaggedImage img) throws IOException {
      char numEntries = ((firstIFD_  ? ENTRIES_PER_IFD + 4 : ENTRIES_PER_IFD));
      if (img.tags.has("Summary")) {
         img.tags.remove("Summary");
      }
      byte[] mdBytes = getBytesFromString(img.tags.toString() + " ");
      // Null-terminate buffer.
      mdBytes[mdBytes.length - 1] = 0;

      //2 bytes for number of directory entries, 12 bytes per directory entry, 4 byte offset of next IFD
     //6 bytes for bits per sample if RGB, 16 bytes for x and y resolution, 1 byte per character of MD string
     //number of bytes for pixels
     int totalBytes = 2 + numEntries*12 + 4 + (rgb_?6:0) + 16 + mdBytes.length + bytesPerImagePixels_;
     int IFDandBitDepthBytes = 2+ numEntries*12 + 4 + (rgb_?6:0);
     
     ByteBuffer ifdBuffer = allocateByteBuffer(IFDandBitDepthBytes);
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
      
      if (firstIFD_ ) {
         omeDescriptionTagPosition_ = filePosition_ + bufferPosition_;
         writeIFDEntry(ifdBuffer, charView, IMAGE_DESCRIPTION, (char) 2, 0, 0);
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
      writeIFDEntry(ifdBuffer,charView,MM_METADATA,(char)2,mdBytes.length,tagDataOffset);
      tagDataOffset += mdBytes.length;
      //NextIFDOffset
      ifdBuffer.putInt(bufferPosition_, (int)tagDataOffset);
      bufferPosition_ += 4;
      
      if (rgb_) {
         charView.put(bufferPosition_/2,(char) (byteDepth_*8));
         charView.put(bufferPosition_/2+1,(char) (byteDepth_*8));
         charView.put(bufferPosition_/2+2,(char) (byteDepth_*8));
      }
      buffers_.add(ifdBuffer);
      buffers_.add(getPixelBuffer(img.pix));
      buffers_.add(getResolutionValuesBuffer());   
      buffers_.add(ByteBuffer.wrap(mdBytes));
      
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
      ByteBuffer buffer = allocateByteBuffer(16);
      buffer.putInt(0,(int)resNumerator_);
      buffer.putInt(4,(int)resDenomenator_);
      buffer.putInt(8,(int)resNumerator_);
      buffer.putInt(12,(int)resDenomenator_);
      return buffer;
   }

   public void setAbortedNumFrames(int n) {
      numFrames_ = n;
   }

   private ByteBuffer getPixelBuffer(Object pixels) throws IOException {
      if (rgb_) {
         if (byteDepth_ == 1) {
            byte[] originalPix = (byte[]) pixels;
            byte[] rgbaPix = new byte[originalPix.length * 3 / 4];
            int count = 0;
            for (int i = 0; i < originalPix.length; i++) {
               //skip alpha channel
               if ((i + 1) % 4 != 0) {
                  //swap R and B for correct format
                  if ((i + 1) % 4 == 1 ) {
                     rgbaPix[count] = originalPix[i + 2];
                  } else if ((i + 1) % 4 == 3) {
                     rgbaPix[count] = originalPix[i - 2];
                  } else {                      
                     rgbaPix[count] = originalPix[i];
                  }
                  count++;
               }
            }
            return ByteBuffer.wrap(rgbaPix);
         } else {
            short[] originalPix = (short[]) pixels;
            short[] rgbaPix = new short[originalPix.length * 3 / 4];
            int count = 0;
            for (int i = 0; i < originalPix.length; i++) {
               if ((i + 1) % 4 != 0) {
                  //swap R and B for correct format
                  if ((i + 1) % 4 == 1 ) {
                     rgbaPix[count] = originalPix[i + 2];
                  } else if ((i + 1) % 4 == 3) {
                     rgbaPix[count] = originalPix[i - 2];
                  } else {                      
                     rgbaPix[count] = originalPix[i];
                  }
                  count++;
               }
            }
            ByteBuffer buffer = allocateByteBufferMemo(rgbaPix.length * 2);
            buffer.rewind();
            buffer.asShortBuffer().put(rgbaPix);
            return buffer;
         }
      } else {
         if (byteDepth_ == 1) {
            return ByteBuffer.wrap((byte[]) pixels);
         } else {
            short[] pix = (short[]) pixels;
            ByteBuffer buffer = allocateByteBufferMemo(pix.length * 2);
            buffer.rewind();
            buffer.asShortBuffer().put(pix);
            return buffer;
         }
      }
   }

   private void processSummaryMD(SummaryMetadata summaryMD)
         throws MMScriptException, JSONException {
      Image repImage = masterStorage_.getAnyImage();
      Metadata repMetadata = repImage.getMetadata();
      rgb_ = repImage.getNumComponents() > 1;
      numChannels_ = masterStorage_.getIntendedSize(Coords.CHANNEL);
      numFrames_ = masterStorage_.getIntendedSize(Coords.TIME);
      numSlices_ = masterStorage_.getIntendedSize(Coords.Z);
      imageWidth_ = repImage.getWidth();
      imageHeight_ = repImage.getHeight();
      byteDepth_ = repImage.getBytesPerPixel() / repImage.getNumComponents();
      bytesPerImagePixels_ = imageHeight_ * imageWidth_ * byteDepth_ * repImage.getNumComponents();
      //Tiff resolution tag values
      double cmPerPixel = 0.0001;
      if (repMetadata.getPixelSizeUm() != null) {
         cmPerPixel = 0.0001 * repMetadata.getPixelSizeUm();
      }
      double log = Math.log10(cmPerPixel);
      if (log >= 0) {
         resDenomenator_ = (long) cmPerPixel;
         resNumerator_ = 1;
      } else {
         resNumerator_ = (long) (1 / cmPerPixel);
         resDenomenator_ = 1;
      }
      
      if (summaryMD.getZStepUm() != null) {
         zStepUm_ = summaryMD.getZStepUm();
      }
   }

   /**
    * writes channel LUTs and display ranges for composite mode Could also be
    * expanded to write ROIs, file info, slice labels, and overlays
    */
   private void writeImageJMetadata(int numChannels, String summaryComment) throws IOException {
      String infoString = masterStorage_.getSummaryMetadataString();
      if (summaryComment != null && summaryComment.length() > 0) {
         infoString = "Acquisition comments: \n" + summaryComment + "\n\n\n" + infoString;
      }
      char[] infoChars = infoString.toCharArray();
      int infoSize = 2 * infoChars.length;

      //size entry (4 bytes) + 4 bytes file info size + 4 bytes for channel display 
      //ranges length + 4 bytes per channel LUT
      int mdByteCountsBufferSize = 4 + 4 + 4 + 4 * numChannels;
      int bufferPosition = 0;

      ByteBuffer mdByteCountsBuffer = allocateByteBuffer(mdByteCountsBufferSize);

      //nTypes is number actually written among: fileInfo, slice labels, display ranges, channel LUTS,
      //slice labels, ROI, overlay, and # of extra metadata entries
      int nTypes = 3; //file info, display ranges, and channel LUTs
      int mdBufferSize = 4 + nTypes * 8;
      
      //Header size: 4 bytes for magic number + 8 bytes for label (int) and count (int) of each type
      mdByteCountsBuffer.putInt(bufferPosition, 4 + nTypes * 8);
      bufferPosition += 4;

      //2 bytes per a character of file info
      mdByteCountsBuffer.putInt(bufferPosition, infoSize);
      bufferPosition += 4;
      mdBufferSize += infoSize;
      
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

      //Header (1) File info (1) display ranges (1) LUTS (1 per channel)
      int numMDEntries = 3 + numChannels;
      ByteBuffer ifdCountAndValueBuffer = allocateByteBuffer(8);
      ifdCountAndValueBuffer.putInt(0, numMDEntries);
      ifdCountAndValueBuffer.putInt(4, (int) filePosition_);
      fileChannelWrite(ifdCountAndValueBuffer, ijMetadataCountsTagPosition_ + 4);

      fileChannelWrite(mdByteCountsBuffer, filePosition_);
      filePosition_ += mdByteCountsBufferSize;

      //Write metadata types and counts
      ByteBuffer mdBuffer = allocateByteBuffer(mdBufferSize);
      bufferPosition = 0;

      //All the ints declared below are non public field in TiffDecoder
      final int ijMagicNumber = 0x494a494a;
      mdBuffer.putInt(bufferPosition, ijMagicNumber);
      bufferPosition += 4;

      //Write ints for each IJ metadata field and its count
      final int fileInfo = 0x696e666f;
      mdBuffer.putInt(bufferPosition, fileInfo);
      bufferPosition += 4;
      mdBuffer.putInt(bufferPosition, 1);
      bufferPosition += 4;
      
      final int displayRanges = 0x72616e67;
      mdBuffer.putInt(bufferPosition, displayRanges);
      bufferPosition += 4;
      mdBuffer.putInt(bufferPosition, 1);
      bufferPosition += 4;

      final int luts = 0x6c757473;
      mdBuffer.putInt(bufferPosition, luts);
      bufferPosition += 4;
      mdBuffer.putInt(bufferPosition, numChannels);
      bufferPosition += 4;


      //write actual metadata
      //FileInfo
      for (char c : infoChars) {
         mdBuffer.putChar(bufferPosition, c);
         bufferPosition += 2;
      }

      DisplaySettings settings = DefaultDisplaySettings.getStandardSettings(
            DefaultDisplayWindow.DEFAULT_SETTINGS_KEY);
      DisplaySettings.ContrastSettings[] contrastSettings = settings.getChannelContrastSettings();
      if (contrastSettings != null && contrastSettings.length > numChannels) {
         for (int i = 0; i < numChannels; i++) {
            //Display Ranges: For each channel, write min then max
            // TODO: doesn't handle multi-component images.
            mdBuffer.putDouble(bufferPosition,
                  contrastSettings[i].getSafeContrastMin(0, 0));
            bufferPosition += 8;
            mdBuffer.putDouble(bufferPosition,
                  contrastSettings[i].getSafeContrastMax(0, 0));
            bufferPosition += 8;
         }
      }

      Color[] colors = settings.getChannelColors();
      if (colors != null && colors.length > numChannels &&
            contrastSettings != null &&
            contrastSettings.length > numChannels) {
         //LUTs
         for (int i = 0; i < numChannels; i++) {
            LUT lut = ImageUtils.makeLUT(colors[i],
                  settings.getSafeContrastGamma(i, 0, 1.0));
            for (byte b : lut.getBytes()) {
               mdBuffer.put(bufferPosition, b);
               bufferPosition++;
            }
         }
      }

      ifdCountAndValueBuffer = allocateByteBuffer(8);
      ifdCountAndValueBuffer.putInt(0, mdBufferSize);
      ifdCountAndValueBuffer.putInt(4, (int) filePosition_);
      fileChannelWrite(ifdCountAndValueBuffer, ijMetadataTagPosition_ + 4);


      fileChannelWrite(mdBuffer, filePosition_);
      filePosition_ += mdBufferSize;
   }

   private String getIJDescriptionString() {
      StringBuffer sb = new StringBuffer();
      sb.append("ImageJ=" + ImageJ.VERSION + "\n");
      if (numChannels_ > 1) {
         sb.append("channels=").append(numChannels_).append("\n");
      }
      if (numSlices_ > 1) {
         sb.append("slices=").append(numSlices_).append("\n");
      }
      if (numFrames_ > 1) {
         sb.append("frames=").append(numFrames_).append("\n");
      }
      if (numFrames_ > 1 || numSlices_ > 1 || numChannels_ > 1) {
         sb.append("hyperstack=true\n");
      }
      if (numChannels_ > 1 && numSlices_ > 1 && masterStorage_.slicesFirst()) {
         sb.append("order=zct\n");
      }
      //cm so calibration unit is consistent with units used in Tiff tags
      sb.append("unit=um\n");
      if (numSlices_ > 1) {
         sb.append("spacing=").append(zStepUm_).append("\n");
      }
      //write single channel contrast settings or display mode if multi channel
      DisplaySettings settings = DefaultDisplaySettings.getStandardSettings(
            DefaultDisplayWindow.DEFAULT_SETTINGS_KEY);
      if (numChannels_ == 1) {
         sb.append("min=").append(settings.getSafeContrastMin(0, 0, 0)).append("\n");
         sb.append("max=").append(settings.getSafeContrastMax(0, 0, 0)).append("\n");
      } else {
         DisplaySettings.ColorMode mode = settings.getChannelColorMode();
         if (mode == DisplaySettings.ColorMode.COMPOSITE) {
            sb.append("mode=composite\n");
         } else if (mode == DisplaySettings.ColorMode.COLOR) {
            sb.append("mode=color\n");
         } else if (mode == DisplaySettings.ColorMode.GRAYSCALE) {
            sb.append("mode=gray\n");
         }    
      }


      sb.append((char) 0);
      return new String(sb);
   }

   private void writeImageDescription(String text, long imageDescriptionTagOffset) throws IOException {
      byte[] bytes = getBytesFromString(text + " ");
      // Null-terminate string
      bytes[bytes.length - 1] = 0;
      //write first image IFD
      ByteBuffer ifdCountAndValueBuffer = allocateByteBuffer(8);
      ifdCountAndValueBuffer.putInt(0, bytes.length);
      ifdCountAndValueBuffer.putInt(4, (int) filePosition_);
      fileChannelWrite(ifdCountAndValueBuffer, imageDescriptionTagOffset + 4);

      //write String
      fileChannelWrite(ByteBuffer.wrap(bytes), filePosition_);
      filePosition_ += bytes.length;
   }

   private byte[] getBytesFromString(String s) {
      try {
         return s.getBytes("UTF-8");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError("Error encoding String to bytes");
         return null;
      }
   }

   private void writeNullOffsetAfterLastImage() throws IOException {
      ByteBuffer buffer = allocateByteBuffer(4);
      buffer.putInt(0, 0);
      fileChannelWrite(buffer, nextIFDOffsetLocation_);
   }

   private void writeComments() throws IOException {
      try {
         // Get the summary comments, then comments for each image.
         JSONObject comments = new JSONObject();
         String summaryComments = masterStorage_.getSummaryMetadata().getComments();
         if (summaryComments == null) {
            summaryComments = "";
         }
         comments.put("Summary", summaryComments);
         for (Coords coords : masterStorage_.getUnorderedImageCoords()) {
            Image image = masterStorage_.getImage(coords);
            String imageComments = image.getMetadata().getComments();
            if (imageComments == null) {
               // No comment for this image.
               continue;
            }
            // HACK: produce a 1.4-style "coordinate string" to use as a key.
            // See also MDUtils.getLabel(), though we can't use it directly.
            int channel = coords.getChannel() < 0 ? 0 : coords.getChannel();
            int z = coords.getZ() < 0 ? 0 : coords.getZ();
            int time = coords.getTime() < 0 ? 0 : coords.getTime();
            int stagePos = coords.getStagePosition() < 0 ? 0 : coords.getStagePosition();
            String key = String.format("%d_%d_%d_%d", channel, z, time, stagePos);
            comments.put(key, imageComments);
         }

         String commentStr = comments.toString();
         //Write 4 byte header, 4 byte number of bytes
         byte[] commentsBytes = getBytesFromString(commentStr);
         ByteBuffer header = allocateByteBuffer(8);
         header.putInt(0, COMMENTS_HEADER);
         header.putInt(4, commentsBytes.length);
         ByteBuffer buffer = ByteBuffer.wrap(commentsBytes);
         fileChannelWrite(header, filePosition_);
         fileChannelWrite(buffer, filePosition_ + 8);

         ByteBuffer offsetHeader = allocateByteBuffer(8);
         offsetHeader.putInt(0, COMMENTS_OFFSET_HEADER);
         offsetHeader.putInt(4, (int) filePosition_);
         fileChannelWrite(offsetHeader, 24);
         filePosition_ += 8 + commentsBytes.length;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to convert comments to JSON");
      }
   }

   // TODO: is this identical to a similar function in the Reader?
   private void writeDisplaySettings() throws IOException {
      DefaultDisplaySettings settings = DefaultDisplaySettings.getStandardSettings(
            DefaultDisplayWindow.DEFAULT_SETTINGS_KEY);
      int numReservedBytes = numChannels_ * DISPLAY_SETTINGS_BYTES_PER_CHANNEL;
      ByteBuffer header = allocateByteBuffer(8);
      ByteBuffer buffer = ByteBuffer.wrap(
            getBytesFromString(settings.toJSON().toString()));
      header.putInt(0, DISPLAY_SETTINGS_HEADER);
      header.putInt(4, numReservedBytes);
      fileChannelWrite(header, filePosition_);
      fileChannelWrite(buffer, filePosition_ + 8);

      ByteBuffer offsetHeader = allocateByteBuffer(8);
      offsetHeader.putInt(0, DISPLAY_SETTINGS_OFFSET_HEADER);
      offsetHeader.putInt(4, (int) filePosition_);
      fileChannelWrite(offsetHeader, 16);
      filePosition_ += numReservedBytes + 8;
   }
  
   private void writeBlankIFD() throws IOException {
//      boolean blankPixelsAlreadyWritten = blankPixelsOffset_ != -1;
      boolean blankPixelsAlreadyWritten = false;

      char numEntries = (char) ((firstIFD_ ? ENTRIES_PER_IFD + 2 : ENTRIES_PER_IFD)
              + (firstIFD_ ? 2 : 0));
     
      byte[] mdBytes = getBytesFromString("NULL ");

      //2 bytes for number of directory entries, 12 bytes per directory entry, 4 byte offset of next IFD
     //6 bytes for bits per sample if RGB, 16 bytes for x and y resolution, 1 byte per character of MD string
     //number of bytes for pixels
     int totalBytes = 2 + numEntries*12 + 4 + (rgb_?6:0) + 16 + mdBytes.length
             + (blankPixelsAlreadyWritten ? 0 : bytesPerImagePixels_);
     int IFDandBitDepthBytes = 2+ numEntries*12 + 4 + (rgb_?6:0);
     
     ByteBuffer ifdBuffer = allocateByteBuffer(IFDandBitDepthBytes);
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
      
      if (firstIFD_) {
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
      writeIFDEntry(ifdBuffer,charView,MM_METADATA,(char)2,mdBytes.length,tagDataOffset);
      tagDataOffset += mdBytes.length;
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
      buffers_.add(ByteBuffer.wrap(mdBytes));
      
      filePosition_ += totalBytes;
      firstIFD_ = false;
   }
}
