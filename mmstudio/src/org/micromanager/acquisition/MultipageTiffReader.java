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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffReader {
      
   private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);

   
   public static final char BITS_PER_SAMPLE = 258;
   public static final char STRIP_OFFSETS = 273;    
   public static final char SAMPLES_PER_PIXEL = 277;
   public static final char STRIP_BYTE_COUNTS = 279;
   
   public static final char MM_METADATA = MultipageTiffWriter.MM_METADATA;
   

   
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;
   private boolean bigEndian_;  
      
   private long displaySettingsStartOffset_ = -1;
   private long displaySettingsReservedBytes_;
   //This flag keeps the reader from reading things that dont exist until the writer is closed
   private boolean fileFinished_;
   private JSONObject displayAndComments_;
   private JSONObject summaryMetadata_;
   
   private HashMap<String,Long> indexMap_;
   
   public MultipageTiffReader(File file, boolean fileFinishedWriting, JSONObject summaryMD) {   
      try {
         displayAndComments_ = new JSONObject();
         fileFinished_ = fileFinishedWriting;
         createFileChannel(file);   
         indexMap_ = new HashMap<String, Long>();
         if (fileFinished_) {
            readHeader();
            readIndexMap();
            displayAndComments_.put("Channels", readDisplaySettings());
            displayAndComments_.put("Comments", readComments());
            summaryMetadata_ = readSummaryMD();
         } else {
            summaryMetadata_ = summaryMD;
         }
         
      } catch (Exception ex) {
         ex.printStackTrace();
      }       
   }


   public void addToIndexMap(TaggedImage img, long offset) {
      indexMap_.put(MDUtils.getLabel(img.tags), offset);
   }

   public void fileFinished() {
      fileFinished_ = true;
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
            Object pixels = readPixels(data);
            JSONObject md = readMMMetadata(data); 
            try {
               md.put("Summary", summaryMetadata_);
            } catch (JSONException ex) {
               ReportingUtils.logError("Problem adding summary metadata to image metadata");
            }
            return new TaggedImage(pixels,md);
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
            return null;
         }
         
      } else {
         //label not in map--maybe parse whole file?
         return null;
      }
   }
   
   
   
   public Set<String> getIndexKeys() {
      if (indexMap_ == null)
         return null;
      return indexMap_.keySet();
   }

   private JSONObject readSummaryMD() throws IOException {
      MappedByteBuffer mdInfo = makeReadOnlyBuffer(32, 8);
      if (mdInfo.getInt() != MultipageTiffWriter.SUMMARY_MD_HEADER) {
         ReportingUtils.logError("Summary Metadata Header Incorrect");
         return null;
      }
      long length = unsignInt(mdInfo.getInt());
      try {
         JSONObject summaryMD = readJSONObject(40, length);
         if (displayAndComments_.has("Comments") && displayAndComments_.getJSONObject("Comments").has("Summary") ) {
            summaryMD.put("Comment", displayAndComments_.getJSONObject("Comments").getString("Summary"));
         }
         return summaryMD;
      } catch (JSONException ex) {
         ReportingUtils.showError("Error reading summary metadata");
         return null;
      }
   }
   
   private JSONObject readComments() throws IOException, JSONException {     
      long offset = readOffsetHeaderAndHeader(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, MultipageTiffWriter.COMMENTS_HEADER,24);
      int numBytes = makeReadOnlyBuffer(offset,4).getInt();
      MappedByteBuffer buffer = makeReadOnlyBuffer(offset+4, numBytes);
      StringBuffer sb = new StringBuffer();
      for (int k = 0; k < numBytes; k++) {
        sb.append((char) buffer.get() );
     }
     return new JSONObject( sb.toString());    
   }
   
   public void rewriteComments(JSONObject comments) throws IOException, JSONException {
      if (!fileFinished_ ) {
         return;
      }
      String commentString = comments.toString();      
      long offset = readOffsetHeaderAndHeader(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, MultipageTiffWriter.COMMENTS_HEADER,24);
      MappedByteBuffer writeBuffer = makeBuffer(FileChannel.MapMode.READ_WRITE, offset, 4+commentString.length());
      char[] letters = commentString.toCharArray();
      writeBuffer.putInt(letters.length);
      for (int i = 0; i < letters.length; i++) { 
         writeBuffer.put((byte) letters[i]);        
      }   
      displayAndComments_.put("Comments", comments);
   }
   
   public void rewriteDisplaySettings(JSONArray settings) throws IOException, JSONException {
      if (!fileFinished_ ) {
         return;
      }
      if (displaySettingsStartOffset_ == -1) {
         long offset = readOffsetHeaderAndHeader(MultipageTiffWriter.DISPLAY_SETTINGS_OFFSET_HEADER, 
                 MultipageTiffWriter.DISPLAY_SETTINGS_HEADER,16);
         displaySettingsStartOffset_ = offset + 4;
         displaySettingsReservedBytes_ = makeReadOnlyBuffer(offset, 4).getInt();
      }
      MappedByteBuffer writeBuffer = makeBuffer(FileChannel.MapMode.READ_WRITE, displaySettingsStartOffset_, displaySettingsReservedBytes_);
      char[] letters = settings.toString().toCharArray();
      for (int i = 0; i < displaySettingsReservedBytes_; i++) { 
         writeBuffer.put((byte) (i < letters.length ? letters[i] : 0));        
      } 
      displayAndComments_.put("Channels", settings);
   }
   
   private JSONArray readDisplaySettings() throws IOException, JSONException {
     long offset = readOffsetHeaderAndHeader(MultipageTiffWriter.DISPLAY_SETTINGS_OFFSET_HEADER, 
             MultipageTiffWriter.DISPLAY_SETTINGS_HEADER,16);
     int reservedBytes = makeReadOnlyBuffer(offset, 4).getInt();
     MappedByteBuffer dsData = makeReadOnlyBuffer(offset+4, reservedBytes);
     StringBuffer sb = new StringBuffer();
     for (int k = 0; k < reservedBytes; k++) {
        sb.append((char) dsData.get() );
     }
     String dsString = sb.toString().trim();
     return new JSONArray(dsString);
   }
   
   //read mmHeader and offset, read header, return offset of first byte after header
   private long readOffsetHeaderAndHeader(int offsetHeaderVal, int headerVal, int startOffset) throws IOException  {
      MappedByteBuffer buffer1 = makeReadOnlyBuffer(startOffset, 8);
      int offsetHeader = buffer1.getInt();
      if ( offsetHeader != offsetHeaderVal) {
         ReportingUtils.logError("Offset header incorrect, expected: " + offsetHeaderVal +"   found: " + offsetHeader);
         return -1;
      }
      long offset = unsignInt(buffer1.getInt());
      MappedByteBuffer buffer2 = makeReadOnlyBuffer(offset, 4);
      int header = buffer2.getInt();
      if (header != headerVal) {
         ReportingUtils.logError("Header incorrect, expected: " + headerVal + "   found: " +header);
         return -1;
      }
      return offset+4;
   }

   private void readIndexMap() throws IOException {
      MappedByteBuffer mapOffset = makeReadOnlyBuffer(8,8);
      if (mapOffset.getInt() != MultipageTiffWriter.INDEX_MAP_OFFSET_HEADER) {
         ReportingUtils.logError("Image map offset header incorrect");
         indexMap_ = null;
         return;
      }  
      long offset = unsignInt(mapOffset.getInt());
      MappedByteBuffer mapHeader = makeReadOnlyBuffer(offset, 8);
      int headerCode = mapHeader.getInt();
      if (headerCode != MultipageTiffWriter.INDEX_MAP_HEADER) {
         ReportingUtils.logError("Image index map header incorrect");
         indexMap_ = null;
         return;
      }
      int numMappings = mapHeader.getInt();
      MappedByteBuffer mapData = makeReadOnlyBuffer(offset+8, 20*numMappings);
      for (int i = 0; i < numMappings; i++) {
         int channel = mapData.getInt();
         int slice = mapData.getInt();
         int frame = mapData.getInt();
         int position = mapData.getInt();
         long imageOffset = unsignInt(mapData.getInt());
         indexMap_.put(MDUtils.generateLabel(channel, slice, frame, position), imageOffset);
      }
   }

   //return byte offset of next IFD
   private IFDData readIFD(long byteOffset) throws IOException {
      MappedByteBuffer entries = makeReadOnlyBuffer(byteOffset, 2);
      int numEntries = entries.getChar();
      IFDData data = new IFDData();
      for (int i = 0; i < numEntries; i++) {
         IFDEntry entry = readDirectoryEntry(MultipageTiffWriter.ENTRIES_PER_IFD * i + 2 + byteOffset);
         if (entry.tag == MM_METADATA) {
            data.mdOffset = entry.value;
            data.mdLength = entry.count;
         } else if (entry.tag == STRIP_OFFSETS) {
            data.pixelOffset = entry.value;
         } else if (entry.tag == STRIP_BYTE_COUNTS) {
            data.bytesPerImage = entry.value;
         } else if (entry.tag == BITS_PER_SAMPLE) {
            data.bitsPerSample = entry.value;
         } else if (entry.tag == SAMPLES_PER_PIXEL) {
            if (entry.value == 1) {
               data.rgb = false;
            } else {
               data.bitsPerSample = makeReadOnlyBuffer(data.bitsPerSample,2).getChar();
               data.rgb = true;
            }
         } 
      }
      return data;
   }

   private Object readPixels(IFDData data) throws IOException {
      if (data.rgb) {
         MappedByteBuffer pixData = makeReadOnlyBuffer(data.pixelOffset, data.bytesPerImage);
         if (data.bitsPerSample <= 8) {
            byte[] pix = new byte[(int) (4*data.bytesPerImage/3)];
            for (int i = 0; i < pix.length; i++) {
               pix[i] = (i+1)%4==0?0:pixData.get();
            }
            return pix;
         } else {
            short[] pix = new short[(int) (4*data.bytesPerImage/3 / 2)];
            for (int i = 0; i < pix.length; i++) {
               pix[i] = (i+1)%4==0?0:pixData.getShort();
            }
            return pix;
         }
      } else {
         MappedByteBuffer pixData = makeReadOnlyBuffer(data.pixelOffset, data.bytesPerImage);
         if (data.bitsPerSample <= 8) {
            byte[] pix = new byte[(int) data.bytesPerImage];
            for (int i = 0; i < pix.length; i++) {
               pix[i] = pixData.get();
            }
            return pix;
         } else if (data.bitsPerSample <= 16) {
            short[] pix = new short[(int) data.bytesPerImage / 2];
            for (int i = 0; i < pix.length; i++) {
               pix[i] = pixData.getShort();
            }
            return pix;
         } else {
            int[] pix = new int[(int) data.bytesPerImage / 4];
            for (int i = 0; i < pix.length; i++) {
               pix[i] = pixData.getInt();
            }
            return pix;
         }
      }
   }

   private JSONObject readMMMetadata(IFDData data) throws IOException {
      try {
         return readJSONObject(data.mdOffset, data.mdLength);
      } catch (JSONException ex) {
         ReportingUtils.showError("Error reading image metadata");
         return new JSONObject();
      }
   }

   private JSONObject readJSONObject(long offset, long length) throws IOException, JSONException {
      MappedByteBuffer mdBuffer = makeReadOnlyBuffer(offset, length);
      StringBuffer sBuffer = new StringBuffer();
      for (int i = 0; i < length; i++) {
         sBuffer.append((char) mdBuffer.get(i));
      }
      return new JSONObject(sBuffer.toString());
   }

   private IFDEntry readDirectoryEntry(long byteOffset) throws IOException {
      MappedByteBuffer ifd = makeReadOnlyBuffer( byteOffset, 12);
      char tag =  ifd.getChar(); 
      char type = ifd.getChar();
      long count = unsignInt(ifd.getInt());
      long value = unsignInt(ifd.getInt());
      
      return (new IFDEntry(tag,type,count,value));
   }

   //returns byteoffset of first IFD
   private long readHeader() throws IOException {           
      MappedByteBuffer buffer = fileChannel_.map(FileChannel.MapMode.READ_ONLY, 0, 8);
      short zeroOne = buffer.getShort();
      if (zeroOne == 0x4949 ) {
         bigEndian_ = false;
      } else if (zeroOne == 0x4d4d ) {
         bigEndian_ = true;
      } else {
         throw new IOException("Error reading Tiff header");
      }
      buffer.order( bigEndian_ ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );  
      short twoThree = buffer.getShort();
      if (twoThree != 42) {
         throw new IOException("Tiff identifier code incorrect");
      }
      return unsignInt(buffer.getInt());
   }
   
   private void createFileChannel(File file) throws FileNotFoundException, IOException {      
      raFile_ = new RandomAccessFile(file,"rw");
      fileChannel_ = raFile_.getChannel();
   }
   
   public void close() throws IOException {
      fileChannel_.close();
      raFile_.close();
      fileChannel_ = null;
      raFile_ = null;
   }
   
   private MappedByteBuffer makeReadOnlyBuffer(long byteOffset, long numBytes) throws IOException {
       return makeBuffer(FileChannel.MapMode.READ_ONLY, byteOffset, numBytes);
   }
   
   private MappedByteBuffer makeBuffer(FileChannel.MapMode mode, long byteOffset, long numBytes) throws IOException {
      MappedByteBuffer buffer = fileChannel_.map(mode, byteOffset, numBytes);
      buffer.order(bigEndian_ ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      return buffer;
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
      public long bitsPerSample;
      public boolean rgb;
      
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