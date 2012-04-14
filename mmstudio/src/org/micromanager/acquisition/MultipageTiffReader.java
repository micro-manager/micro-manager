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
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffReader {
      
   private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);

   
   public static final char BITS_PER_SAMPLE = 258;
   public static final char STRIP_OFFSETS = 273;
   public static final char STRIP_BYTE_COUNTS = 279;
   
   public static final char MM_METADATA_LENGTH = 65122;
   public static final char MM_METADATA = 65123;
   

   
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;
   private FileLock lock_;
   private boolean bigEndian_;  
   
   private long firstIFD_;
   
   private HashMap<String,Long> indexMap_;
   
   public MultipageTiffReader(File file, boolean readIndexMap) {
      
      try {
         createFileChannel(file);
         firstIFD_ = readHeader();
         indexMap_ = new HashMap<String, Long>();
         if (readIndexMap) {
            readIndexMap();
         }
      } catch (Exception ex) {
         ex.printStackTrace();
      }
           
   }

   public void addToIndexMap(TaggedImage img, long offset) {
      indexMap_.put(MDUtils.getLabel(img.tags), offset);
   }

   public TaggedImage readImage(String label) {
      if (indexMap_.containsKey(label)) {
         try {
            long byteOffset = indexMap_.get(label);
            IFDData data = readIFD(byteOffset);
            Object pixels = readPixels(data);
            JSONObject md = readMMMetadata(data);  
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

   public JSONObject readSummaryMD() throws IOException {
      MappedByteBuffer mdInfo = makeReadOnlyBuffer(16, 8);
      if (mdInfo.getInt() != MultipageTiffWriter.SUMMARY_MD_HEADER) {
         ReportingUtils.logError("Summary Metadata Header Incorrect");
         return null;
      }
      long length = unsignInt(mdInfo.getInt());
      try {
         return readJSONObject(24, length);
      } catch (JSONException ex) {
         ReportingUtils.showError("Error reading summary metadata");
         return null;
      }
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
      MappedByteBuffer mapData = makeReadOnlyBuffer(offset+8, 24*numMappings);
      for (int i = 0; i < numMappings; i++) {
         int channel = mapData.getInt();
         int slice = mapData.getInt();
         int frame = mapData.getInt();
         int position = mapData.getInt();
         long imageOffset = mapData.getLong();
         indexMap_.put(MDUtils.generateLabel(channel, slice, frame, position), imageOffset);
      }
   }

   //return byte offset of next IFD
   private IFDData readIFD(long byteOffset) throws IOException {
      MappedByteBuffer entries = makeReadOnlyBuffer(byteOffset, 2);
      int numEntries = entries.getChar();
      IFDData data = new IFDData();
      for (int i = 0; i < numEntries; i++) {
         IFDEntry entry = readDirectoryEntry( 12*i + 2 + byteOffset);
         if (entry.tag == MM_METADATA) {
            data.mdOffset = entry.value;
         } else if (entry.tag == STRIP_OFFSETS) {
            data.pixelOffset = entry.value;
         } else if (entry.tag == STRIP_BYTE_COUNTS) {
            data.bytesPerImage = entry.value;
         } else if (entry.tag == BITS_PER_SAMPLE) {
            if (entry.value <= 8) {
               data.bytesPerPixel = 1;
            } else if (entry.value <= 16) {
               data.bytesPerPixel = 2;
            } else {
               data.bytesPerPixel = 3;
            }
         } else if (entry.tag == MM_METADATA_LENGTH) {
            data.mdLength = entry.value;
         }
      }
      MappedByteBuffer next = makeReadOnlyBuffer(byteOffset + 2 + 12*numEntries, 4);
      data.nextIFDAdress = unsignInt(next.getInt());
      return data;
   }

   private Object readPixels(IFDData data) throws IOException {
      MappedByteBuffer pixData = makeReadOnlyBuffer(data.pixelOffset, data.bytesPerImage);
      if (data.bytesPerPixel == 1) {
         byte[] pix = new byte[(int)data.bytesPerImage];
         for (int i = 0; i < pix.length; i++) {
            pix[i] = pixData.get();
         }
         return pix;
      } else if (data.bytesPerPixel == 2) {
         short[] pix = new short[(int)data.bytesPerImage/2];
         for (int i = 0; i < pix.length; i++) {
            pix[i] = pixData.getShort();
         }
         return pix;
      } else {
         int[] pix = new int[(int)data.bytesPerImage/4];
         for (int i = 0; i < pix.length; i++) {
            pix[i] = pixData.getInt();
         }
         return pix;
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
//      lock_ = fileChannel_.lock();
   }
   
   private void close() throws IOException {
      lock_.release();
      lock_ = null;    
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
      public long nextIFDAdress;
      public long bytesPerPixel;
      
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