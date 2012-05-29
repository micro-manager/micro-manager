///////////////////////////////////////////////////////////////////////////////
//FILE:          MultipageTiffReader.java
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffReader {
      
   private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);

   
   public static final char BITS_PER_SAMPLE = 258;
   public static final char STRIP_OFFSETS = 273;    
   public static final char SAMPLES_PER_PIXEL = 277;
   public static final char STRIP_BYTE_COUNTS = 279;
   
   public static final char MM_METADATA = MultipageTiffWriter.MM_METADATA;
   
   private  ByteOrder byteOrder_;  
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;
      
   private JSONObject displayAndComments_;
   private JSONObject summaryMetadata_;
   private int byteDepth_;
   private boolean rgb_;
   private boolean writingFinished_;
   
   private HashMap<String,Long> indexMap_;
   
   /**
    * This constructor is used for a file that is currently being written
    */
   public MultipageTiffReader(JSONObject summaryMD, MultipageTiffWriter writer) {
      displayAndComments_ = new JSONObject();
      fileChannel_ = writer.getFileChannel();
      summaryMetadata_ = summaryMD;
      indexMap_ = writer.getIndexMap();
      byteOrder_ = writer.BYTE_ORDER;
      getRGBAndByteDepth();
      writingFinished_ = false;
   }

   /**
    * This constructor is used for opening datasets that have already been saved
    */
   public MultipageTiffReader(File file) {
      try {
         displayAndComments_ = new JSONObject();
         createFileChannel(file);
         writingFinished_ = true;
         
         readHeader();
         readIndexMap();
         displayAndComments_.put("Channels", readDisplaySettings());
         displayAndComments_.put("Comments", readComments());
         summaryMetadata_ = readSummaryMD();
         getRGBAndByteDepth();
         
      } catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   public void finishedWriting() {
      writingFinished_ = true;
   }

   private void getRGBAndByteDepth() {
      try {
         rgb_ = MDUtils.isRGB(summaryMetadata_);
         byteDepth_ = (int) Math.ceil(MDUtils.getBitDepth(summaryMetadata_) / 8.0);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void addToIndexMap(TaggedImage img, long offset) {
      indexMap_.put(MDUtils.getLabel(img.tags), offset);
   }
   
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }
   
   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }
   
   public TaggedImage readImage(String label) {
      if (indexMap_.containsKey(label)) {
         try {
            long byteOffset = indexMap_.get(label);
            
            IFDData data = readIFD(byteOffset);
            return readTaggedImage(data);
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
            return null;
         }
         
      } else {
         //label not in map--either writer hasnt finished writing it 
         return null;
      }
   }  
   
   public Set<String> getIndexKeys() {
      if (indexMap_ == null)
         return null;
      return indexMap_.keySet();
   }

   private JSONObject readSummaryMD() throws IOException, JSONException {
      ByteBuffer mdInfo = ByteBuffer.allocate(8).order(byteOrder_);
      fileChannel_.read(mdInfo, 32);
      int header = mdInfo.getInt(0);
      int length = mdInfo.getInt(4);

      if (header != MultipageTiffWriter.SUMMARY_MD_HEADER) {
         ReportingUtils.logError("Summary Metadata Header Incorrect");
         return null;
      }

      ByteBuffer mdBuffer = ByteBuffer.allocate(length);
      fileChannel_.read(mdBuffer, 40);
      JSONObject summaryMD = new JSONObject(getString(mdBuffer));

      //Summary MD written start of acquisition and never changed, this code makes sure acquisition comment
      //field is current
      if (displayAndComments_.has("Comments") && displayAndComments_.getJSONObject("Comments").has("Summary")) {
         summaryMD.put("Comment", displayAndComments_.getJSONObject("Comments").getString("Summary"));
      }
      return summaryMD;
   }
   
   private JSONObject readComments() throws IOException, JSONException, MMException {
      long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, 24);
      ByteBuffer header = readIntoBuffer(offset, 8);
      if (header.getInt(0) != MultipageTiffWriter.COMMENTS_HEADER) {
         throw new MMException("Error reading comments header");
      }
      ByteBuffer buffer = readIntoBuffer(offset + 8, header.getInt(4));
      return new JSONObject(getString(buffer));
   }
   
   public void rewriteComments(JSONObject comments) throws IOException, JSONException {
      if (writingFinished_) {
         byte[] bytes = getBytesFromString(comments.toString());
         ByteBuffer byteCount = ByteBuffer.allocate(4).order(byteOrder_).putInt(bytes.length);
         ByteBuffer buffer = ByteBuffer.wrap(bytes);
         long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, 24);
         fileChannel_.write(byteCount,offset + 4);
         fileChannel_.write(buffer, offset +8);
      }
      displayAndComments_.put("Comments", comments);
   }

   public void rewriteDisplaySettings(JSONArray settings) throws IOException, JSONException {
      if (writingFinished_) {
         long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.DISPLAY_SETTINGS_OFFSET_HEADER, 16);        
         int numReservedBytes = readIntoBuffer(offset + 4, 4).getInt(0);
         byte[] bytes = getBytesFromString(settings.toString());
         ByteBuffer buffer = ByteBuffer.allocate(numReservedBytes);
         buffer.put(bytes);
         while (buffer.position() < buffer.capacity()) {
            buffer.put((byte) 0);
         }
         fileChannel_.write(buffer, offset+8);
      }
      displayAndComments_.put("Channels", settings);
   }

   private JSONArray readDisplaySettings() throws IOException, JSONException, MMException {
     long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.DISPLAY_SETTINGS_OFFSET_HEADER,16);
     ByteBuffer header = readIntoBuffer(offset, 8);
     if (header.getInt(0) != MultipageTiffWriter.DISPLAY_SETTINGS_HEADER) {
         throw new MMException("Error reading display settings header");
      }
     ByteBuffer buffer = readIntoBuffer(offset + 8, header.getInt(4));
     return new JSONArray(getString(buffer));
   }
   
   private ByteBuffer readIntoBuffer(long position, int length) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(length).order(byteOrder_);
      fileChannel_.read(buffer, position);
      return buffer;
   }
   
   private long readOffsetHeaderAndOffset(int offsetHeaderVal, int startOffset) throws IOException  {
      ByteBuffer buffer1 = readIntoBuffer(startOffset,8);
      int offsetHeader = buffer1.getInt(0);
      if ( offsetHeader != offsetHeaderVal) {
         ReportingUtils.logError("Offset header incorrect, expected: " + offsetHeaderVal +"   found: " + offsetHeader);
         return -1;
      }
      return unsignInt(buffer1.getInt(4));     
   }

   private void readIndexMap() throws IOException, MMException {
      long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.INDEX_MAP_OFFSET_HEADER, 8);
      ByteBuffer header = readIntoBuffer(offset, 8);
      if (header.getInt(0) != MultipageTiffWriter.INDEX_MAP_HEADER) {
         throw new MMException("Error reading index map header");
      }
      int numMappings = header.getInt(4);
      indexMap_ = new HashMap<String, Long>();
      ByteBuffer mapBuffer = readIntoBuffer(offset+8, 20*numMappings);     
      for (int i = 0; i < numMappings; i++) {
         int channel = mapBuffer.getInt(i*20);
         int slice = mapBuffer.getInt(i*20+4);
         int frame = mapBuffer.getInt(i*20+8);
         int position = mapBuffer.getInt(i*20+12);
         long imageOffset = unsignInt(mapBuffer.getInt(i*20+16));
         indexMap_.put(MDUtils.generateLabel(channel, slice, frame, position), imageOffset);
      }
   }

   private IFDData readIFD(long byteOffset) throws IOException {
      ByteBuffer buff = readIntoBuffer(byteOffset,2);
      int numEntries = buff.getChar(0);
     
      ByteBuffer entries = readIntoBuffer(byteOffset + 2, numEntries*12 + 4).order(byteOrder_);
      IFDData data = new IFDData();
      for (int i = 0; i < numEntries; i++) {
         IFDEntry entry = readDirectoryEntry(i*12, entries);
         if (entry.tag == MM_METADATA) {
            data.mdOffset = entry.value;
            data.mdLength = entry.count;
         } else if (entry.tag == STRIP_OFFSETS) {
            data.pixelOffset = entry.value;
         } else if (entry.tag == STRIP_BYTE_COUNTS) {
            data.bytesPerImage = entry.value;
         }     
      }
      return data;
   }

   private String getString(ByteBuffer buffer) {
      try {
         return new String(buffer.array(), "US-ASCII");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }
   
   private TaggedImage readTaggedImage(IFDData data) throws IOException {
      ByteBuffer pixelBuffer = ByteBuffer.allocate( (int) data.bytesPerImage);
      ByteBuffer mdBuffer = ByteBuffer.allocate((int) data.mdLength);
      fileChannel_.read(pixelBuffer, data.pixelOffset);
      fileChannel_.read(mdBuffer, data.mdOffset);
      JSONObject md = null;
      try {
         md = new JSONObject(getString(mdBuffer));
      } catch (JSONException ex) {
         ReportingUtils.logError("Error reading image metadata from file");
      }
      if (rgb_) {
         if (byteDepth_ == 1) {
            byte[] pixels = new byte[(int) (4 * data.bytesPerImage / 3)];
            int i = 0;
            for (byte b : pixelBuffer.array()) {
               pixels[i] = b;
               i++;
               if ((i + 1) % 4 == 0) {
                  pixels[i] = 0;
                  i++;
               }
            }
            return new TaggedImage(pixels, md);
         } else {
             short[] pixels = new short[(int) (2 * (data.bytesPerImage/3))];
            int i = 0;           
            while ( i < pixels.length) {                
               pixels[i] = pixelBuffer.getShort( 2*((i/4)*3 + (i%4)) );        
               i++;
               if ((i + 1) % 4 == 0) {
                  pixels[i] = 0;
                  i++;
               }
            }
            return new TaggedImage(pixels, md);
         }
      } else {
         if (byteDepth_ == 1) {
            return new TaggedImage(pixelBuffer.array(), md);
         } else {
            short[] pix = new short[pixelBuffer.capacity()/2];
            for (int i = 0; i < pix.length; i++ ) {
               pix[i] = pixelBuffer.getShort(i*2);
            }
            return new TaggedImage(pix, md);
         }
      }
   }

   private IFDEntry readDirectoryEntry(int offset, ByteBuffer buffer) throws IOException {
      char tag =  buffer.getChar(offset); 
      char type = buffer.getChar(offset + 2);
      long count = unsignInt( buffer.getInt(offset + 4) );
      long value;
      if ( type == 3 && count == 1) {
         value = buffer.getChar(offset + 8);
      } else {
         value = unsignInt(buffer.getInt(offset + 8));
      }
      return (new IFDEntry(tag,type,count,value));
   }

   //returns byteoffset of first IFD
   private void readHeader() throws IOException {           
      ByteBuffer tiffHeader = ByteBuffer.allocate(40);
      fileChannel_.read(tiffHeader,0);
      char zeroOne = tiffHeader.getChar(0);
      if (zeroOne == 0x4949 ) {
         byteOrder_ = ByteOrder.LITTLE_ENDIAN;
      } else if (zeroOne == 0x4d4d ) {
         byteOrder_ = ByteOrder.BIG_ENDIAN;
      } else {
         throw new IOException("Error reading Tiff header");
      }
      tiffHeader.order( byteOrder_ );  
      short twoThree = tiffHeader.getShort(2);
      if (twoThree != 42) {
         throw new IOException("Tiff identifier code incorrect");
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
   
   private void createFileChannel(File file) throws FileNotFoundException, IOException {      
      raFile_ = new RandomAccessFile(file,"rw");
      fileChannel_ = raFile_.getChannel();
   }
   
   public void close() throws IOException {
      if (fileChannel_ != null) {
         fileChannel_.close();
         fileChannel_ = null;
      }
      if (raFile_ != null) {
         raFile_.close();
         raFile_ = null;
      }
   }

   private long unsignInt(int i) {
      long val = Integer.MAX_VALUE & i;
      if (i < 0) {
         val += BIGGEST_INT_BIT;
      }
      return val;
   }

   private class IFDData {
      public long pixelOffset;
      public long bytesPerImage;
      public long mdOffset;
      public long mdLength;
      
      public IFDData() {}
   }
   
   private class IFDEntry {
      public char tag, type;
      public long count, value;
      
      public IFDEntry(char tg, char typ, long cnt, long val) {
         tag = tg;
         type = typ;
         count = cnt;
         value = val;
      }
   }
 
   
}