package org.micromanager.acquisition;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffWriter {
   
   //Required tags
   public static final char WIDTH = 256;
   public static final char HEIGHT = 257;
   public static final char BITS_PER_SAMPLE = 258;
   public static final char COMPRESSION = 259;
   public static final char PHOTOMETRIC_INTERPRETATION = 262;
   public static final char STRIP_OFFSETS = 273;
   public static final char ROWS_PER_STRIP = 278;
   public static final char STRIP_BYTE_COUNTS = 279;
   public static final char X_RESOLUTION = 282;
   public static final char Y_RESOLUTION = 283;
   public static final char RESOLUTION_UNIT = 296;
   
   public static final char MM_METADATA_LENGTH = 65122;
   public static final char MM_METADATA = 65123;
   
   public static final int INDEX_MAP_OFFSET_HEADER = 54773648;
   public static final int INDEX_MAP_HEADER = 3453623;
   public static final int SUMMARY_MD_HEADER = 2355492;
   
   private File file_;
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;     
   private final boolean bigEndian_ = false; 
   private HashMap<String, Long> indexMap_;
   private long valueOffset_;
   private long entryOffset_;
   
   public MultipageTiffWriter(String path, String filename, JSONObject summaryMD) {
      try {
         createFile(path, filename);
         String summaryMDString = summaryMD.toString();
         writeHeader(summaryMDString);
         writeSummaryMD(summaryMDString);
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }     
   }
   
   private void createFile(String path, String filename) throws FileNotFoundException {
      file_ = new File(path + filename);
      raFile_ = new RandomAccessFile(file_, "rw");
      fileChannel_ = raFile_.getChannel();  
      indexMap_ = new HashMap<String,Long>();
   }
   
   private void close() throws IOException {
      fileChannel_.close();
      raFile_.close();
      file_ = null;
      fileChannel_ = null;
      raFile_ = null;
      System.gc();
   }
   
   public void writeImage(TaggedImage img, boolean last) throws IOException {
      updateIndexMap(img.tags);
      writeIFD(img,last);     
      if(last) {
         writeIndexMap();
         close();
      }
   }
   
   private void updateIndexMap(JSONObject tags) {
      int channel = 0, slice = 0, frame = 0, position = 0;
      try {
         channel = MDUtils.getChannelIndex(tags);
         slice = MDUtils.getSliceIndex(tags);
         frame = MDUtils.getFrameIndex(tags);
         position = MDUtils.getPositionIndex(tags);
      } catch (JSONException ex) {
         ReportingUtils.logError("Problem finding channel, slice, frame, or position index in metadata");
         return;
      }
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      indexMap_.put(label, entryOffset_);
   }

   private void writeIndexMap() throws IOException {
      //Write 4 byte header, 4 byte number of entries, and 24 bytes for each entry
      int numMappings = indexMap_.size();
      MappedByteBuffer buffer = makeBuffer(entryOffset_, numMappings*24 + 8  );
      buffer.putInt(INDEX_MAP_HEADER);
      buffer.putInt( numMappings );
      for (String label : indexMap_.keySet()) {
         String[] indecies = label.split("_");
         for(String index : indecies) {
            buffer.putInt(Integer.parseInt(index));
         }
         buffer.putLong(indexMap_.get(label));
      }      
      MappedByteBuffer address = makeBuffer(8, 8 );
      address.putInt(INDEX_MAP_OFFSET_HEADER);
      address.putInt((int)entryOffset_);
   }
   
   private void writeSummaryMD(String md) throws IOException {
      //4 byte summary md header code, 4 byte length of summary md
      MappedByteBuffer buffer = makeBuffer(16, md.length() + 8 );
      buffer.putInt(SUMMARY_MD_HEADER);
      buffer.putInt(md.length());
      writeString(md,buffer);
   }

   private void writeIFD(TaggedImage img, boolean last) throws IOException {
     int imgHeight = getImageHeight(img);
     int imgWidth = getImageWidth(img);   
     int byteDepth = getImageByteDepth(img);
     char entryCount = 13;
     String mdString = img.tags.toString();
      
      //Write 2 bytes containing number of directory entries (12)
      MappedByteBuffer numEntries = makeBuffer( entryOffset_, 2);
      numEntries.putChar(entryCount);     
      entryOffset_ += 2;

      //Offset of this IFD + # of directory entries (2 bytes--already included in entryOffset_)
      //+ (12 bytes)*(# of entries)+ address of next IFD or 0 if last (4 bytes)
      valueOffset_ = entryOffset_ + 12*entryCount + 4;
      
      writeIFDEntry(WIDTH,(char)3,1,imgWidth);
      writeIFDEntry(HEIGHT,(char)3,1,imgHeight);
      writeIFDEntry(BITS_PER_SAMPLE,(char)3,1,byteDepth*8);
      writeIFDEntry(COMPRESSION,(char)3,1,1);
      writeIFDEntry(PHOTOMETRIC_INTERPRETATION,(char)3,1,1);

      //Add 16 for x and y resolution and 1 byte for each metadata character
      long stripOffset = valueOffset_ + 16 + mdString.length();
      writeIFDEntry(STRIP_OFFSETS,(char)4,1, (int)stripOffset);
      writeIFDEntry(ROWS_PER_STRIP, (char) 3, 1, imgHeight);
      writeIFDEntry(STRIP_BYTE_COUNTS, (char) 4, 1, byteDepth*imgHeight*imgWidth );
      writeXAndYResolution(img);
      writeIFDEntry(RESOLUTION_UNIT, (char) 3,1,3);
      writeIFDEntry(MM_METADATA_LENGTH,(char) 3,1,mdString.length());
      writeMMMetadata(mdString); 
      
      writePixels(img, byteDepth);

      writeNextIFDOffset(last);
      //Start next IFD at end of pixel value
      entryOffset_ = valueOffset_;
   }
   
   private void writeNextIFDOffset(boolean last) throws IOException {
      MappedByteBuffer next = makeBuffer(entryOffset_, 4);
      if (last) {
         next.putInt(0);
      } else {
         next.putInt((int)valueOffset_);
      }
      entryOffset_ += 4;
   }

   private void writePixels(TaggedImage img, int byteDepth) throws IOException {
     if (byteDepth == 1) {
        byte[] pixels = (byte[]) img.pix;
        MappedByteBuffer buffer = makeBuffer(valueOffset_, pixels.length);
        buffer.put(pixels);
        valueOffset_ += pixels.length;
     } else if (byteDepth == 2) {
        short[] pixels = (short[]) img.pix;
        MappedByteBuffer buffer = makeBuffer(valueOffset_, pixels.length*2);
        for (short s : pixels) {
           buffer.putShort(s);
        }
        valueOffset_ += pixels.length*2;
     } else {
        int[] pixels = (int[]) img.pix;
        MappedByteBuffer buffer = makeBuffer(valueOffset_, pixels.length*4);
        for (int i : pixels) {
           buffer.putInt(i);
        }
        valueOffset_ += pixels.length*4; 
     }
   }
   
   private void writeMMMetadata(String mdString) throws IOException {
      writeIFDEntry(MM_METADATA,(char)3,1,valueOffset_);
      
      MappedByteBuffer mdBuffer = makeBuffer(valueOffset_, mdString.length());
      writeString(mdString, mdBuffer);
      valueOffset_ += mdString.length();  
   }
   
   private void writeString(String s, MappedByteBuffer buffer) {
      char[] letters = s.toCharArray();
      for (int i = 0; i < letters.length; i++) {
         buffer.put((byte) letters[i]);
      }
   }
   
   private void writeXAndYResolution(TaggedImage img) throws IOException {
      long numerator = 100, denominator = 1;
      if (img.tags.has("PixelSizeUm")) {
         double cmPerPixel = 0.0001;
         try {
            cmPerPixel = 0.0001 * img.tags.getDouble("PixelSizeUm");
         } catch (JSONException ex) {}
         double log = Math.log10(cmPerPixel);
         if (log >= 0) {
            denominator = 1;
            numerator = (long) cmPerPixel;
         } else {            
            denominator = (long) Math.pow(10, Math.ceil(Math.abs(log)+4) );
            numerator = (long) (cmPerPixel * denominator);
         }
      }
      
      writeIFDEntry(X_RESOLUTION,(char)5,1,valueOffset_);
      writeIFDEntry(Y_RESOLUTION,(char)5,1,valueOffset_+8);
      MappedByteBuffer resolutionValues = makeBuffer(valueOffset_,16);
      resolutionValues.putInt((int)numerator);
      resolutionValues.putInt((int)denominator);
      resolutionValues.putInt((int)numerator);
      resolutionValues.putInt((int)denominator);
      valueOffset_ += 16;
   }
 
   //Only use if value fits
   private void writeIFDEntry(char tag, char type, long count, long value) throws IOException {
      MappedByteBuffer entry = makeBuffer(entryOffset_,12);
      entry.putChar(tag);
      entry.putChar(type);
      entry.putInt((int)count);
      entry.putInt((int) value);
      entryOffset_ += 12;
   }

   private void writeHeader(String summaryMD) throws IOException {
      MappedByteBuffer header = makeBuffer(0, 8);
      if (bigEndian_) {
         header.putChar((char) 0x4d4d);
      } else {
         header.putChar((char) 0x4949);
      }
      header.putChar((char) 42);
      //8 bytes for file header + 4 bytes for index map offset header + 
      //4 bytes for index map offset + 4 bytes for summaryMD header + 
      //4 bytes for summary md length = 24 + 1 byte for each character of summary md
      int firstImageOffset = 24 + summaryMD.length();
      header.putInt(firstImageOffset);
      entryOffset_ = firstImageOffset;
      valueOffset_ = firstImageOffset;
   }

   private MappedByteBuffer makeBuffer(long byteOffset, int numBytes) throws IOException {
       MappedByteBuffer buffer = fileChannel_.map(FileChannel.MapMode.READ_WRITE, byteOffset, numBytes);
       buffer.order(bigEndian_ ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
       return buffer;
   }
   
   private int getImageHeight(TaggedImage img) {
      try {
         return MDUtils.getHeight(img.tags);
      } catch (JSONException ex) {
         ReportingUtils.showError("Error saving image: Image height not in metadata");
         return 0;
      }
   }
   
   private int getImageWidth(TaggedImage img) {
      try {
         return MDUtils.getWidth(img.tags);
      } catch (JSONException ex) {
         ReportingUtils.showError("Error saving image: Image width not in metadata");
         return 0;
      }
   }

   private int getImageByteDepth(TaggedImage img) {
      try {
         String pixelType = MDUtils.getPixelType(img.tags);
         if (pixelType.equals("GRAY8") || pixelType.equals("RGB32") || pixelType.equals("RGB24")) {
            return 1;
         } else if (pixelType.equals("GRAY16") || pixelType.equals("RGB64")) {
            return 2;
         } else if (pixelType.equals("GRAY32")) {
            return 3;
         } else {
            return 2;
         }
      } catch (Exception ex) {
         ReportingUtils.showError("Error saving image: Image width not in metadata");
         return 2;
      }
   }

   
}