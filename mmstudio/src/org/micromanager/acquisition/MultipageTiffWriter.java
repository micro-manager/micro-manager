package org.micromanager.acquisition;

import ij.ImageJ;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffWriter {
   
   private static final long BYTES_PER_GIG = 1073741824;
   private static final long MAX_FILE_SIZE = BYTES_PER_GIG;
   
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
   
   //1 MB for now...might have to increase
   public static final long SPACE_FOR_DISPLAY_SETTINGS = 1048576; 
   //1 MB for now...might have to increase
   private static final long SPACE_FOR_COMMENTS = 1048576;
   
   
   public static final int INDEX_MAP_OFFSET_HEADER = 54773648;
   public static final int INDEX_MAP_HEADER = 3453623;
   public static final int SUMMARY_MD_HEADER = 2355492;
   public static final int DISPLAY_SETTINGS_OFFSET_HEADER = 483765892;
   public static final int DISPLAY_SETTINGS_HEADER = 347834724;
   public static final int DISPLAY_SETTINGS_BYTES_PER_CHANNEL = 256;
   public static final int COMMENTS_OFFSET_HEADER = 99384722;
   public static final int COMMENTS_HEADER = 84720485;
   
   private MappedByteBuffer currentBuffer_;
   private long bufferStart_ = 0;
   
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;     
   private final boolean bigEndian_ = false; 
   private int numChannels_ = 1, numFrames_ = 1, numSlices_ = 1;
   private HashMap<String, Long> indexMap_;
   private long byteOffset_;
   private long nextIFDOffsetLocation_ = -1;
   private JSONObject displayAndComments_;
   private boolean rgb_ = false;
   private String ijDescription_;
   private int byteDepth_, imageWidth_, imageHeight_, bytesPerImagePixels_;
   
   
   public MultipageTiffWriter(File f, JSONObject summaryMD) {
      try {
         try {
            byteOffset_ = 0;
            readSummaryMD(summaryMD); 
            displayAndComments_ = VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMD);
            ijDescription_ = getIJDescriptionString();
         } catch (MMScriptException ex) {
             ReportingUtils.logError(ex);
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
         //TODO: possibly truncate file length at end of writing
         createFileChannel(f,Math.min(numFrames_*numChannels_*numSlices_*(bytesPerImagePixels_+1500), MAX_FILE_SIZE));
         writeMMHeaderAndSummaryMD(summaryMD);
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }
   }

   private void writeMMHeaderAndSummaryMD(JSONObject summaryMD) throws IOException {
      if (summaryMD.has("Comment")) {
         summaryMD.remove("Comment");
      }
      String summaryMDString = summaryMD.toString();
      int mdLength = summaryMDString.length();
     
      MappedByteBuffer buffer = makeBuffer(8 + 32 + mdLength );
      if (bigEndian_) {
         buffer.putChar((char) 0x4d4d);
      } else {
         buffer.putChar((char) 0x4949);
      }
      buffer.putChar((char) 42);
      //8 bytes for file header + 4 bytes for index map offset header + 
      //4 bytes for index map offset + 4 bytes for display settings offset header + 
      //4 bytes for display settings offset+ 4 bytes for comments offset header + 
      //4 bytes for comments offset+4 bytes for summaryMD header + 
      //4 bytes for summary md length = 24 + 1 byte for each character of summary md
      buffer.putInt(40 + mdLength);
      //place holders for index map, display settings, and comments offset headers and headers
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.putInt(0);
      buffer.putInt(SUMMARY_MD_HEADER);
      buffer.putInt(mdLength);
      writeString(buffer,summaryMDString);
   }

   public void close() throws IOException {
     //TODO: truncate empty space at end of file?
      writeNullOffsetAfterLastImage();
      writeIndexMap();
      writeDisplaySettings();
      writeComments();
      fileChannel_.close();
      raFile_.close();
      fileChannel_ = null;
      raFile_ = null;
   }
   
   //TODO: check performance of this function (since it is called before each image is written)
   public boolean hasSpaceToWrite(TaggedImage img) {
      int mdLength = img.tags.toString().length();
      int indexMapSize = indexMap_.size()*20 + 8;
      int IFDSize = 13*12 + 4 + 16;
      int extraPadding = 1000000;
      if (mdLength+indexMapSize+IFDSize+bytesPerImagePixels_+SPACE_FOR_COMMENTS+
              numChannels_*DISPLAY_SETTINGS_BYTES_PER_CHANNEL +extraPadding + byteOffset_ >= MAX_FILE_SIZE) {
         return false;
      }
      return true;
   }
   
   public boolean isClosed() {
      return raFile_ == null;
   }
      
   public long writeImage(TaggedImage img) throws IOException {
      long offset = byteOffset_;
      writeIFD(img);
      updateIndexMap(img.tags,offset);      
      return offset;
   }
   
   private void updateIndexMap(JSONObject tags, long offset) {
      String label = MDUtils.getLabel(tags);
      indexMap_.put(label, offset);
   }
   
   private void writeComments() throws IOException {
      //Write 4 byte header, 4 byte number of reserved bytes
      long commentsOffset = byteOffset_;
      JSONObject comments;
      try {
         comments = displayAndComments_.getJSONObject("Comments");
      } catch (JSONException ex) {
         comments = new JSONObject();
      }
      String commentsString = comments.toString();
      int commentsLength = commentsString.length();
      MappedByteBuffer buffer = makeBuffer(8 + commentsLength );
      buffer.putInt(COMMENTS_HEADER);     
      buffer.putInt(commentsLength);
      writeString(buffer,commentsString);
      
      MappedByteBuffer address = makeBuffer(24, 8 );
      address.putInt(COMMENTS_OFFSET_HEADER);
      address.putInt((int)commentsOffset);
   }
   
   private void writeDisplaySettings() throws IOException {
      JSONArray displaySettings;
      try {
         displaySettings = displayAndComments_.getJSONArray("Channels");
      } catch (JSONException ex) {
         displaySettings = new JSONArray();
      }      
      int numReservedBytes =  numChannels_*DISPLAY_SETTINGS_BYTES_PER_CHANNEL; 
      String displayString = displaySettings.toString();
      long displaySettingsOffset = byteOffset_;
      MappedByteBuffer buffer = makeBuffer(8 + numReservedBytes);
      //Write 4 byte header, 4 byte number of reserved bytes
      buffer.putInt(DISPLAY_SETTINGS_HEADER);
      buffer.putInt(numReservedBytes);
      writeString(buffer,displayString);
      
      MappedByteBuffer address = makeBuffer(16, 8 );
      address.putInt(DISPLAY_SETTINGS_OFFSET_HEADER);
      address.putInt((int)displaySettingsOffset);
   }

   private void writeIndexMap() throws IOException {
      //Write 4 byte header, 4 byte number of entries, and 20 bytes for each entry
      int numMappings = indexMap_.size();
      long indexMapOffset = byteOffset_;
      MappedByteBuffer buffer = makeBuffer(8 + 20*numMappings);
      buffer.putInt(INDEX_MAP_HEADER);
      buffer.putInt( numMappings );
      for (String label : indexMap_.keySet()) {
         String[] indecies = label.split("_");
         for(String index : indecies) {
            buffer.putInt(Integer.parseInt(index));
         }
         buffer.putInt(indexMap_.get(label).intValue());
      }
      
      MappedByteBuffer address = makeBuffer(8, 8 );
      address.putInt(INDEX_MAP_OFFSET_HEADER);
      address.putInt((int)indexMapOffset);
   }

   private void writeIFD(TaggedImage img) throws IOException {     

     char numEntries = (char) 13;
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
     int numBytes = 2 + numEntries*12 + 4 + (rgb_?6:0) + 16 + mdString.length() + bytesPerImagePixels_;
     long tagDataOffset = byteOffset_ + 2 + numEntries*12 + 4;
     nextIFDOffsetLocation_ = byteOffset_ + 2 + numEntries*12;
     MappedByteBuffer buffer = makeBuffer(numBytes);
     
      buffer.putChar(numEntries);
      writeIFDEntry(buffer,WIDTH,(char)3,1,imageWidth_);
      writeIFDEntry(buffer,HEIGHT,(char)3,1,imageHeight_);
      writeIFDEntry(buffer,BITS_PER_SAMPLE,(char)3,rgb_?3:1,  rgb_? tagDataOffset:byteDepth_*8);
      if (rgb_) {
         tagDataOffset += 6;
      }
      writeIFDEntry(buffer,COMPRESSION,(char)3,1,1);
      writeIFDEntry(buffer,PHOTOMETRIC_INTERPRETATION,(char)3,1,rgb_?2:1);
      writeIFDEntry(buffer,STRIP_OFFSETS,(char)4,1, tagDataOffset );
      tagDataOffset += bytesPerImagePixels_;
      writeIFDEntry(buffer,SAMPLES_PER_PIXEL,(char)3,1,rgb_?3:1);
      writeIFDEntry(buffer,ROWS_PER_STRIP, (char) 3, 1, imageHeight_);
      writeIFDEntry(buffer,STRIP_BYTE_COUNTS, (char) 4, 1, bytesPerImagePixels_ );
      writeIFDEntry(buffer,X_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(buffer,Y_RESOLUTION, (char)5, 1, tagDataOffset);
      tagDataOffset += 8;
      writeIFDEntry(buffer,RESOLUTION_UNIT, (char) 3,1,3);
      writeIFDEntry(buffer,MM_METADATA,(char)2,mdString.length(),tagDataOffset);
      tagDataOffset += mdString.length();
      
      //NextIFDOffset
      buffer.putInt((int)tagDataOffset);

      if (rgb_) {
         buffer.putChar((char) (byteDepth_*8));
         buffer.putChar((char) (byteDepth_*8));
         buffer.putChar((char) (byteDepth_*8));
      }
      writePixels(buffer, img);
      writeResoltuionValues(buffer, img);
      writeString(buffer,mdString);    
   }
   
   private void writeResoltuionValues(MappedByteBuffer buffer, TaggedImage img) throws IOException {
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
      buffer.putInt((int)resNumerator);
      buffer.putInt((int)resDenomenator);
      buffer.putInt((int)resNumerator);
      buffer.putInt((int)resDenomenator);
}

   
   private void writeIJDescriptionString() throws IOException {
//      writeString(ijDescription_);
//      tagDataOffset_ += ijDescription_.length();
   }
   
   private void writeNullOffsetAfterLastImage() throws IOException {
      MappedByteBuffer next = makeBuffer(nextIFDOffsetLocation_, 4);
      next.putInt(0);
   }

   private void writePixels(MappedByteBuffer buffer, TaggedImage img) throws IOException {
      if (rgb_) {
         if (byteDepth_ == 1) {
            byte[] pixels = (byte[]) img.pix;
            for (int i = 0; i < pixels.length; i++) {
               if ((i+1)%4 != 0) {
                  buffer.put(pixels[i]);
               }
            }
         } else if (byteDepth_ == 2) {
            short[] pixels = (short[]) img.pix;
            for (int i = 0; i < pixels.length; i++) {
               if ((i+1)%4 != 0) {
                  buffer.putChar((char) pixels[i]);
               }
            }
         }
      } else {
         if (byteDepth_ == 1) {
            byte[] pixels = (byte[]) img.pix;
            for (byte b : pixels) {
               buffer.put(b);
            }
         } else if (byteDepth_ == 2) {
            short[] pixels = (short[]) img.pix;
            for (short s : pixels) {
               buffer.putChar((char) s);
            }
         } 
      }
   }
   
   private void writeString(MappedByteBuffer buffer, String s) throws IOException {
      char[] letters = s.toCharArray();
      for (int i = 0; i < letters.length; i++) {
         buffer.put((byte) letters[i]);
      }
   }
 
   private void writeIFDEntry(MappedByteBuffer buffer, char tag, char type, long count, long value) throws IOException {
      buffer.putChar(tag);
      buffer.putChar(type);
      buffer.putInt((int)count);
      buffer.putInt((int) value);
   }   
   
   private MappedByteBuffer makeBuffer(int numBytes) throws IOException {
      MappedByteBuffer buffer = fileChannel_.map(FileChannel.MapMode.READ_WRITE, byteOffset_, numBytes);
      buffer.order(bigEndian_ ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      byteOffset_ += numBytes;
      return buffer;
   }
   
   private MappedByteBuffer makeBuffer(long offset, int numBytes) throws IOException {
      MappedByteBuffer buffer = fileChannel_.map(FileChannel.MapMode.READ_WRITE, offset, numBytes);
      buffer.order(bigEndian_ ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      return buffer;
   }

   private void createFileChannel(File f, long fileSize) throws FileNotFoundException, IOException {
      f.createNewFile();
      raFile_ = new RandomAccessFile(f, "rw");
      raFile_.setLength(fileSize);
      fileChannel_ = raFile_.getChannel();
      fileChannel_.size();
      indexMap_ = new HashMap<String, Long>();
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