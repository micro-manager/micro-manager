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
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffWriter {
   
   private static final long BYTES_PER_GIG = 1073741824;
   private static final long MAX_FILE_SIZE = 4*BYTES_PER_GIG;
   
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
   public static final char MM_METADATA = 51123;
   
   public static final int DISPLAY_SETTINGS_BYTES_PER_CHANNEL = 256;
   //1 MB for now...might have to increase
   private static final long SPACE_FOR_COMMENTS = 1048576;
   
   public static final int INDEX_MAP_OFFSET_HEADER = 54773648;
   public static final int INDEX_MAP_HEADER = 3453623;
   public static final int SUMMARY_MD_HEADER = 2355492;
   public static final int DISPLAY_SETTINGS_OFFSET_HEADER = 483765892;
   public static final int DISPLAY_SETTINGS_HEADER = 347834724;
   public static final int COMMENTS_OFFSET_HEADER = 99384722;
   public static final int COMMENTS_HEADER = 84720485;
   
   public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
   
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_; 
   private long filePosition_ = 0;
   private int numChannels_ = 1, numFrames_ = 1, numSlices_ = 1;
   private HashMap<String, Long> indexMap_;
   private long nextIFDOffsetLocation_ = -1;
   private JSONObject displayAndComments_;
   private boolean rgb_ = false;
   private int byteDepth_, imageWidth_, imageHeight_, bytesPerImagePixels_;
   private LinkedList<ByteBuffer> buffers_;

   public MultipageTiffWriter(File f, JSONObject summaryMD, TaggedImage firstImage) {
      try {
         readSummaryMD(summaryMD);
         displayAndComments_ = VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMD);
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
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
         buffers_ = new LinkedList<ByteBuffer>();

         writeMMHeaderAndSummaryMD(summaryMD);
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
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

   public void close() throws IOException {
      writeNullOffsetAfterLastImage();
      writeIndexMap();
      writeDisplaySettings();
      writeComments();
              
      raFile_.setLength(filePosition_ + 8);
      //Dont close file channel and random access file becase Tiff reader still using them
      fileChannel_ = null;
      raFile_ = null;    
   }
   
   //TODO: check performance of this function (since it is called before each image is written)
   public boolean hasSpaceToWrite(TaggedImage img) {
      int mdLength = img.tags.toString().length();
      int indexMapSize = indexMap_.size()*20 + 8;
      int IFDSize = ENTRIES_PER_IFD*12 + 4 + 16;
      int extraPadding = 1000000; 
      long size = mdLength+indexMapSize+IFDSize+bytesPerImagePixels_+SPACE_FOR_COMMENTS+
              numChannels_*DISPLAY_SETTINGS_BYTES_PER_CHANNEL +extraPadding + filePosition_;
      if ( size >= MAX_FILE_SIZE) {
         return false;
      }
      return true;
   }
   
   public boolean isClosed() {
      return raFile_ == null;
   }
      
   
   public long writeImage(TaggedImage img) throws IOException {
      long offset = filePosition_;
      writeIFD(img);
      updateIndexMap(img.tags,offset);
      writeBuffers();  
      return offset;
   }
   
   private void writeBuffers() throws IOException {
      ByteBuffer[] buffs = new ByteBuffer[buffers_.size()];
      for (int i = 0; i < buffs.length; i++) {
         buffs[i] = buffers_.removeFirst();
      }
      fileChannel_.write(buffs);
   }
   
   private void updateIndexMap(JSONObject tags, long offset) {
      String label = MDUtils.getLabel(tags);
      indexMap_.put(label, offset);
   }

   private void writeIFD(TaggedImage img) throws IOException {     
     char numEntries = ENTRIES_PER_IFD;
     if (img.tags.has("Summary")) {
        img.tags.remove("Summary");
     }
     String mdString = img.tags.toString() + " ";
      
     //2 bytes for number of directory entries
     //12 bytes per directory entry
     //4 byte offset of next IFD
     //6 bytes for bits per sample if RGB
     //16 bytes for x and y resolution
     //1 byte per character of MD string
     //number of bytes for pixels
     int totalBytes = 2 + numEntries*12 + 4 + (rgb_?6:0) + 16 + mdString.length() + bytesPerImagePixels_;
     int IFDandBitDepthBytes = 2+ numEntries*12 + 4 + (rgb_?6:0);
     
     ByteBuffer ifdBuffer = ByteBuffer.allocate(IFDandBitDepthBytes).order(BYTE_ORDER);
     CharBuffer charView = ifdBuffer.asCharBuffer();
     
     
     long tagDataOffset = filePosition_ + 2 + numEntries*12 + 4;
     nextIFDOffsetLocation_ = filePosition_ + 2 + numEntries*12;
     
      charView.put(0,numEntries);
      writeIFDEntry(2,ifdBuffer,charView, WIDTH,(char)4,1,imageWidth_);
      writeIFDEntry(14,ifdBuffer,charView,HEIGHT,(char)4,1,imageHeight_);
      writeIFDEntry(26,ifdBuffer,charView,BITS_PER_SAMPLE,(char)3,rgb_?3:1,  rgb_? tagDataOffset:byteDepth_*8);
      if (rgb_) {
         tagDataOffset += 6;
      }
      writeIFDEntry(38,ifdBuffer,charView,COMPRESSION,(char)3,1,1);
      writeIFDEntry(50,ifdBuffer,charView,PHOTOMETRIC_INTERPRETATION,(char)3,1,rgb_?2:1);
      writeIFDEntry(62,ifdBuffer,charView,STRIP_OFFSETS,(char)4,1, tagDataOffset );
      tagDataOffset += bytesPerImagePixels_;
      writeIFDEntry(74,ifdBuffer,charView,SAMPLES_PER_PIXEL,(char)3,1,(rgb_?3:1));
      writeIFDEntry(86,ifdBuffer,charView,ROWS_PER_STRIP, (char) 3, 1, imageHeight_);
      writeIFDEntry(98,ifdBuffer,charView,STRIP_BYTE_COUNTS, (char) 4, 1, bytesPerImagePixels_ );
      writeIFDEntry(110,ifdBuffer,charView,X_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(122,ifdBuffer,charView,Y_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(134,ifdBuffer,charView,RESOLUTION_UNIT, (char) 3,1,3);
      writeIFDEntry(146,ifdBuffer,charView,MM_METADATA,(char)2,mdString.length(),tagDataOffset);
      tagDataOffset += mdString.length();
      //NextIFDOffset
      ifdBuffer.putInt(158, (int)tagDataOffset);

      if (rgb_) {
         charView.put(81,(char) (byteDepth_*8));
         charView.put(82,(char) (byteDepth_*8));
         charView.put(83,(char) (byteDepth_*8));
      }
      buffers_.add(ifdBuffer);
      buffers_.add(getPixelBuffer(img));
      buffers_.add(getResolutionValuesBuffer(img));   
      buffers_.add(ByteBuffer.wrap(getBytesFromString(mdString)));
      
      filePosition_ += totalBytes;
   }

   private void writeIFDEntry(int position, ByteBuffer buffer, CharBuffer cBuffer, char tag, char type, long count, long value) throws IOException {
      cBuffer.put(position / 2, tag);
      cBuffer.put(position / 2 + 1, type);
      buffer.putInt(position + 4, (int) count);
      if (type ==3 && count == 1) {  //Left justify in 4 byte value field
         cBuffer.put(position/2 + 4, (char) value);
         cBuffer.put(position/2 + 5,(char) 0);
      } else {
         buffer.putInt(position + 8, (int) value);
      }      
   }

   private ByteBuffer getResolutionValuesBuffer(TaggedImage img) throws IOException {
      long resNumerator = 1, resDenomenator = 1;
      if (img.tags.has("PixelSizeUm")) {
         double cmPerPixel = 0.0001;
         try {
            cmPerPixel = 0.0001 * img.tags.getDouble("PixelSizeUm");
         } catch (JSONException ex) {}
         double log = Math.log10(cmPerPixel);
         if (log >= 0) {
            resDenomenator = (long) cmPerPixel;
            resNumerator = 1;
         } else {
            resNumerator = (long) (1 / cmPerPixel);
            resDenomenator = 1;
         }
      }
      ByteBuffer buffer = ByteBuffer.allocate(16).order(BYTE_ORDER);
      buffer.putInt(0,(int)resNumerator);
      buffer.putInt(4,(int)resDenomenator);
      buffer.putInt(8,(int)resNumerator);
      buffer.putInt(12,(int)resDenomenator);
      return buffer;
}

   
   private void writeNullOffsetAfterLastImage() throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.order(BYTE_ORDER);
      buffer.putInt(0,0);
      fileChannel_.write(buffer, nextIFDOffsetLocation_);
   }

   private ByteBuffer getPixelBuffer(TaggedImage img) throws IOException {
      if (rgb_) {
         if (byteDepth_ == 1) {
            byte[] originalPix = (byte[]) img.pix;
            byte[] pix = new byte[ originalPix.length * 3 / 4 ];
            int count = 0;
            for (int i = 0; i < originalPix.length; i++) {
               if ( (i +1) % 4 != 0 ) {
                  pix[count] = originalPix[i];
                  count++;
               }
            }
            return ByteBuffer.wrap(pix);
         } else  {
            short[] originalPix = (short[]) img.pix;
            short[] pix = new short[ originalPix.length * 3 / 4 ];
            int count = 0;
            for (int i = 0; i < originalPix.length; i++) {
               if ( (i +1) % 4 != 0 ) {
                  pix[count] = originalPix[i];
                  count++;
               }
            }
            ByteBuffer buffer = ByteBuffer.allocate(pix.length*2).order(BYTE_ORDER);
            buffer.asShortBuffer().put(pix);
            return buffer;
         }
      } else {
         if (byteDepth_ == 1) {
            return ByteBuffer.wrap((byte[]) img.pix);
         } else {
            short[] pix = (short[]) img.pix;
            ByteBuffer buffer = ByteBuffer.allocate(pix.length*2).order(BYTE_ORDER);
            buffer.asShortBuffer().put(pix);
            return buffer;
         }
      }
   }

   private byte[] getBytesFromString(String s) {
      try {
         return s.getBytes("US-ASCII");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError("Error encoding String to bytes");
         return null;
      }
   }
    
   private void writeComments() throws IOException {
      //Write 4 byte header, 4 byte number of bytes
      long commentsOffset = filePosition_;
      JSONObject comments;
      try {
         comments = displayAndComments_.getJSONObject("Comments");
      } catch (JSONException ex) {
         comments = new JSONObject();
      }
      String commentsString = comments.toString();
      ByteBuffer header = ByteBuffer.allocate(8).order(BYTE_ORDER);
      header.putInt(0,COMMENTS_HEADER);     
      header.putInt(4,commentsString.length());
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(commentsString));
      fileChannel_.write(header, filePosition_);
      fileChannel_.write(buffer, filePosition_+8);
      filePosition_ += 8 + commentsString.length();
      
      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(BYTE_ORDER);
      offsetHeader.putInt(0,COMMENTS_OFFSET_HEADER);
      offsetHeader.putInt(4,(int)commentsOffset);
      fileChannel_.write(offsetHeader, 24);
   }
   
   private void writeDisplaySettings() throws IOException {
      long displaySettingsOffset = filePosition_;
      JSONArray displaySettings;
      try {
         displaySettings = displayAndComments_.getJSONArray("Channels");
      } catch (JSONException ex) {
         displaySettings = new JSONArray();
      }      
      int numReservedBytes =  numChannels_*DISPLAY_SETTINGS_BYTES_PER_CHANNEL;
      ByteBuffer header = ByteBuffer.allocate(8).order(BYTE_ORDER);
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(displaySettings.toString()));
      header.putInt(0,DISPLAY_SETTINGS_HEADER);
      header.putInt(4,numReservedBytes);
      fileChannel_.write(header,filePosition_);
      fileChannel_.write(buffer, filePosition_ + 8);
      filePosition_ += numReservedBytes + 8;
        
      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(BYTE_ORDER);
      offsetHeader.putInt(0,DISPLAY_SETTINGS_OFFSET_HEADER);
      offsetHeader.putInt(4,(int)displaySettingsOffset);        
      fileChannel_.write(offsetHeader, 16);
   }

   private void writeIndexMap() throws IOException {
      //Write 4 byte header, 4 byte number of entries, and 20 bytes for each entry
      int numMappings = indexMap_.size();
      long indexMapOffset = filePosition_;
      ByteBuffer buffer = ByteBuffer.allocate(8 + 20*numMappings).order(BYTE_ORDER);
      buffer.putInt(0,INDEX_MAP_HEADER);
      buffer.putInt(4, numMappings );
      int position = 2;
      for (String label : indexMap_.keySet()) {
         String[] indecies = label.split("_");
         for(String index : indecies) {
            buffer.putInt(4*position,Integer.parseInt(index));
            position++;
         }
         buffer.putInt(4*position,indexMap_.get(label).intValue());
         position++;
      }
      fileChannel_.write(buffer, filePosition_);
      filePosition_ += buffer.capacity();
      
      ByteBuffer header = ByteBuffer.allocate(8).order(BYTE_ORDER);
      header.putInt(0,INDEX_MAP_OFFSET_HEADER);
      header.putInt(4,(int)indexMapOffset);
      fileChannel_.write(header, 8);
   }

   private void readSummaryMD(JSONObject summaryMD) throws MMScriptException, JSONException {
      rgb_ = MDUtils.isRGB(summaryMD);
      numChannels_ = MDUtils.getNumChannels(summaryMD);
      numFrames_ = MDUtils.getNumFrames(summaryMD);
      numSlices_ = MDUtils.getNumSlices(summaryMD);
      imageWidth_ = MDUtils.getWidth(summaryMD);
      imageHeight_ = MDUtils.getHeight(summaryMD);
      String pixelType = MDUtils.getPixelType(summaryMD);
      if (pixelType.equals("GRAY8") || pixelType.equals("RGB32") || pixelType.equals("RGB24")) {
            byteDepth_ =  1;
         } else if (pixelType.equals("GRAY16") || pixelType.equals("RGB64")) {
            byteDepth_ =  2;
         } else if (pixelType.equals("GRAY32")) {
            byteDepth_ =  3;
         } else {
            byteDepth_ =  2;
         }
      bytesPerImagePixels_ = imageHeight_*imageWidth_*byteDepth_*(rgb_?3:1);
   }
   
   
   private String getIJDescriptionString() {
		StringBuffer sb = new StringBuffer(100);
		sb.append("ImageJ="+ImageJ.VERSION+"\n");
		sb.append("images="+numFrames_*numChannels_*numSlices_+"\n");     
		if (numChannels_>1)
			sb.append("channels="+numChannels_+"\n");
		if (numSlices_>1)
			sb.append("slices="+numSlices_+"\n");
		if (numFrames_>1)
			sb.append("frames="+numFrames_+"\n");  
		if ( numFrames_ > 1 || numSlices_ > 1 || numChannels_ > 1 ) 
         sb.append("hyperstack=true\n");   
		if (numChannels_ > 1) 
			sb.append("mode=composite\n");
      
      //May need to change depending on calibtratuon
      sb.append("unit=um\n");

		if (numFrames_*numChannels_*numSlices_>1) {			
			sb.append("loop=false\n");
		}

      try {
         JSONObject contrast = displayAndComments_.getJSONArray("Channels").getJSONObject(0); 
         double min = contrast.getInt("Min");
         double max = contrast.getInt("Max");
         sb.append("min=" + min + "\n");
         sb.append("max=" + max + "\n");
      } catch (JSONException ex) {
      }

		sb.append((char)0);
		return new String(sb);
	}
   
   
}