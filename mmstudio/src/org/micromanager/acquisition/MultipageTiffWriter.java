package org.micromanager.acquisition;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
   
   private static final long BYTES_PER_GIG = 1073741824;
   private static final long MAX_FILE_SIZE = BYTES_PER_GIG;
   private static final int BUFFER_SIZE = 100* 1048576;
   
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
   
   private MappedByteBuffer currentBuffer_;
   private long bufferStart_ = 0;
   
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;     
   private final boolean bigEndian_ = false; 
   private HashMap<String, Long> indexMap_;
   private long valueOffset_;
   private long entryOffset_;
   private long nextIFDOffsetLocation_ = -1;
   
   private long resNumerator_, resDenomenator_;
   
   public MultipageTiffWriter(File f, JSONObject summaryMD) {
      try {
         createFileChannel(f);
         String summaryMDString = summaryMD.toString();
         writeHeader(summaryMDString);
         putInt(INDEX_MAP_OFFSET_HEADER);
         //place holder for index map offset
         putInt(0);
         writeSummaryMD(summaryMDString);
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }     
   }
   
   public void close() throws IOException {
      writeNullOffsetAfterLastImage();
      writeIndexMap();
      fileChannel_.close();
      raFile_.close();
      fileChannel_ = null;
      raFile_ = null;
      System.gc();
   }
   
   //TODO: check performance of this function (since it is called before each image is written)
   public boolean hasSpaceToWrite(TaggedImage img) {
      int mdLength = img.tags.toString().length();
      int indexMapSize = indexMap_.size()*24 + 8;
      int IFDSize = 13*12 + 4 + 16;
      int pixelSize = getImageByteDepth(img)*getImageHeight(img)*getImageWidth(img);
      int extraPadding = 1000000;
      if (mdLength+indexMapSize+IFDSize+pixelSize+extraPadding + entryOffset_ >= MAX_FILE_SIZE) {
         return false;
      }
      return true;
   }
   
   public boolean isClosed() {
      return raFile_ == null;
   }
      
   public long writeImage(TaggedImage img) throws IOException {
      long offset = entryOffset_;
      writeIFD(img);
      updateIndexMap(img.tags,offset);      
      return offset;
   }
   
   private void updateIndexMap(JSONObject tags, long offset) {
      String label = MDUtils.getLabel(tags);
      indexMap_.put(label, offset);
   }

   private void writeIndexMap() throws IOException {
      //Write 4 byte header, 4 byte number of entries, and 20 bytes for each entry
      int numMappings = indexMap_.size();
      putInt(INDEX_MAP_HEADER);
      putInt( numMappings );
      for (String label : indexMap_.keySet()) {
         String[] indecies = label.split("_");
         for(String index : indecies) {
            putInt(Integer.parseInt(index));
         }
         putInt(indexMap_.get(label).intValue());
      }      
      MappedByteBuffer address = makeBuffer(12, 4 );
      address.putInt((int)entryOffset_);
   }
   
   private void writeSummaryMD(String md) throws IOException {
      //4 byte summary md header code, 4 byte length of summary md
      putInt(SUMMARY_MD_HEADER);
      putInt(md.length());
      writeString(md);
   }

   private void writeIFD(TaggedImage img) throws IOException {     
     int imgHeight = getImageHeight(img);
     int imgWidth = getImageWidth(img);   
     int byteDepth = getImageByteDepth(img);
     char entryCount = 13;
     if (img.tags.has("Summary")) {
        img.tags.remove("Summary");
     }
     String mdString = img.tags.toString();
      
      //Write 2 bytes containing number of directory entries (13)
      putChar(entryCount);     
      entryOffset_ += 2;

      //Offset of this IFD + # of directory entries (2 bytes--already included in entryOffset_)
      //+ (12 bytes)*(# of entries)+ address of next IFD or 0 if last (4 bytes)
      valueOffset_ = entryOffset_ + 12*entryCount + 4;
      nextIFDOffsetLocation_ = entryOffset_ + 12*entryCount;
      
      
      writeIFDEntry(WIDTH,(char)3,1,imgWidth);
      writeIFDEntry(HEIGHT,(char)3,1,imgHeight);
      writeIFDEntry(BITS_PER_SAMPLE,(char)3,1,byteDepth*8);
      writeIFDEntry(COMPRESSION,(char)3,1,1);
      writeIFDEntry(PHOTOMETRIC_INTERPRETATION,(char)3,1,1);
      //Add 16 for x and y resolution and 1 byte for each metadata character
      writeIFDEntry(STRIP_OFFSETS,(char)4,1, (int) ( valueOffset_ + 16 + mdString.length() ) );
      writeIFDEntry(ROWS_PER_STRIP, (char) 3, 1, imgHeight);
      writeIFDEntry(STRIP_BYTE_COUNTS, (char) 4, 1, byteDepth*imgHeight*imgWidth );
      writeXAndYResolution(img);
      writeIFDEntry(RESOLUTION_UNIT, (char) 3,1,3);
      writeIFDEntry(MM_METADATA_LENGTH,(char) 3,1,mdString.length());
      writeIFDEntry(MM_METADATA,(char)3,1,valueOffset_+16);
      //NextIFDOffset
      putInt((int)(valueOffset_ + 16 + mdString.length() + imgWidth*imgHeight*byteDepth));

      writeResoltuionValues();
      writeMMMetadata(mdString);      
      writePixels(img, byteDepth);

      entryOffset_ = valueOffset_;
   }
   
   private void writeNullOffsetAfterLastImage() throws IOException {
      MappedByteBuffer next = makeBuffer(nextIFDOffsetLocation_, 4);
      next.putInt(0);
   }

   private void writePixels(TaggedImage img, int byteDepth) throws IOException {
     if (byteDepth == 1) {
        byte[] pixels = (byte[]) img.pix;
        for (byte b : pixels) {
           putByte(b);
        }
        valueOffset_ += pixels.length;
     } else if (byteDepth == 2) {
        short[] pixels = (short[]) img.pix;
        for (short s : pixels) {
           putChar((char)s);
        }
        valueOffset_ += pixels.length*2;
     } else {
        int[] pixels = (int[]) img.pix;
        for (int i : pixels) {
           putInt(i);
        }
        valueOffset_ += pixels.length*4; 
     }
   }
   
   private void writeMMMetadata(String mdString) throws IOException {      
      writeString(mdString);
      valueOffset_ += mdString.length();  
   }
   
   private void writeString(String s) throws IOException {
      char[] letters = s.toCharArray();
      for (int i = 0; i < letters.length; i++) {
         putByte((byte) letters[i]);
      }
   }
   
   private void writeXAndYResolution(TaggedImage img) throws IOException {
      if (img.tags.has("PixelSizeUm")) {
         double cmPerPixel = 0.0001;
         try {
            cmPerPixel = 0.0001 * img.tags.getDouble("PixelSizeUm");
         } catch (JSONException ex) {}
         double log = Math.log10(cmPerPixel);
         if (log >= 0) {
            resDenomenator_ = 1;
            resNumerator_ = (long) cmPerPixel;
         } else {            
            resDenomenator_ = (long) Math.pow(10, Math.ceil(Math.abs(log)+4) );
            resNumerator_ = (long) (cmPerPixel * resDenomenator_);
         }
      }
      
      writeIFDEntry(X_RESOLUTION,(char)5,1,valueOffset_);
      writeIFDEntry(Y_RESOLUTION,(char)5,1,valueOffset_+8);
   }
   
   private void writeResoltuionValues() throws IOException {
      putInt((int)resNumerator_);
      putInt((int)resDenomenator_);
      putInt((int)resNumerator_);
      putInt((int)resDenomenator_);
      valueOffset_ += 16;
}
 
   //Only use if value fits
   private void writeIFDEntry(char tag, char type, long count, long value) throws IOException {
      putChar(tag);
      putChar(type);
      putInt((int)count);
      putInt((int) value);
      entryOffset_ += 12;
   }

   private void writeHeader(String summaryMD) throws IOException {
      if (bigEndian_) {
         putChar((char) 0x4d4d);
      } else {
         putChar((char) 0x4949);
      }
      putChar((char) 42);
      //8 bytes for file header + 4 bytes for index map offset header + 
      //4 bytes for index map offset + 4 bytes for summaryMD header + 
      //4 bytes for summary md length = 24 + 1 byte for each character of summary md
      int firstImageOffset = 24 + summaryMD.length();
      putInt(firstImageOffset);
      entryOffset_ = firstImageOffset;
      valueOffset_ = firstImageOffset;
   }

   private void putInt(int val) throws IOException {
      if (currentBuffer_.capacity() - currentBuffer_.position() < 4 ) {
         bufferStart_ +=  currentBuffer_.position();
         currentBuffer_ = makeBuffer(bufferStart_, BUFFER_SIZE);
      }
      currentBuffer_.putInt(val);
   }
   
   private void putChar(char val) throws IOException {
      if ( currentBuffer_.capacity() - currentBuffer_.position() < 2  ) {
         bufferStart_ +=  currentBuffer_.position();
         currentBuffer_ = makeBuffer(bufferStart_, BUFFER_SIZE);
      }
      currentBuffer_.putChar(val);
   }
   
   private void putByte(byte val) throws IOException {
      if (currentBuffer_.position() == currentBuffer_.capacity() ) {
         bufferStart_ +=  currentBuffer_.position();
         currentBuffer_ = makeBuffer(bufferStart_, BUFFER_SIZE);
      }
      currentBuffer_.put(val);
   }
   
   private MappedByteBuffer makeBuffer(long byteOffset, int numBytes) throws IOException {
      MappedByteBuffer buffer = fileChannel_.map(FileChannel.MapMode.READ_WRITE, byteOffset, numBytes);
      buffer.order(bigEndian_ ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      return buffer;
   }

   private void createFileChannel(File f) throws FileNotFoundException, IOException {
      f.createNewFile();
      raFile_ = new RandomAccessFile(f, "rw");
      raFile_.setLength(BUFFER_SIZE);
      fileChannel_ = raFile_.getChannel();
      fileChannel_.size();
      indexMap_ = new HashMap<String, Long>();
      currentBuffer_ = makeBuffer(bufferStart_, BUFFER_SIZE);
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