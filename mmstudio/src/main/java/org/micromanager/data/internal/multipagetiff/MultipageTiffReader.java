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

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.CommentsHelper;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PixelType;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ProgressBar;
import org.micromanager.internal.utils.ReportingUtils;

public final class MultipageTiffReader {

   private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);
   private static final char STRIP_OFFSETS = MultipageTiffWriter.STRIP_OFFSETS;
   private static final char STRIP_BYTE_COUNTS = MultipageTiffWriter.STRIP_BYTE_COUNTS;

   private static final char MM_METADATA = MultipageTiffWriter.MM_METADATA;

   // Note: ordering of axes here matches that in MDUtils.getLabel().
   // If you change this, you will need to track down places where the size of
   // the position list is implicitly kept (e.g. in the size of a single index
   // map entry, 20 bytes) and update those locations.
   static final List<String> ALLOWED_AXES = ImmutableList.of("channel", "z", "time", "position");

   private ByteOrder byteOrder_;
   private File file_;
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;

   private StorageMultipageTiff masterStorage_;
   private SummaryMetadata summaryMetadata_;
   private PropertyMap imageFormatReadFromSummary_;

   private HashMap<Coords, Long> coordsToOffset_;

   /**
    * This constructor is used for a file that is currently being written.
    * @param masterStorage
    * @param summaryMD
    * @param summaryPmap
    * @param firstImage
    */
   public MultipageTiffReader(StorageMultipageTiff masterStorage,
         SummaryMetadata summaryMD, PropertyMap summaryPmap,
         Image firstImage) {
      masterStorage_ = masterStorage;
      summaryMetadata_ = summaryMD;
      byteOrder_ = MultipageTiffWriter.BYTE_ORDER;
   }

   public void setIndexMap(HashMap<Coords, Long> indexMap) {
      coordsToOffset_ = indexMap;
   }

   public void setFileChannel(FileChannel fc) {
      fileChannel_ = fc;
   }

   /**
    * This constructor is used for opening datasets that have already been saved
    */
   public MultipageTiffReader(StorageMultipageTiff masterStorage, File file)
         throws IOException, InvalidIndexMapException {
      masterStorage_ = masterStorage;
      file_ = file;
      try {
         createFileChannel(false);
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Cannot open file: " +  file_.getName());
         throw ex instanceof IOException ? (IOException) ex : new IOException(ex);
      }
      readHeader(); // Determine byte order
      readSummaryMD();

      try {
         readIndexMap();
      }
      catch (IOException e) {
         // Unlike other IOErrors, this is a potentially recoverable error.
         throw new InvalidIndexMapException(e);
      }

      readComments();
   }

   /**
    * HACK: this version is only used when fixing index maps.
    * Ideally said fixing would be done without needing to create a new
    * MultipageTiffReader object, but that would require disentangling the
    * file reading code that does some setup before fixIndexMap() is called.
    */
   public MultipageTiffReader(File file) throws IOException {
      file_ = file;
      try {
         createFileChannel(true);
      }
      catch (Exception ex) {
         ReportingUtils.showError(ex, "Cannot open file: " +  file_.getName());
         throw ex instanceof IOException ? (IOException) ex : new IOException(ex);
      }
      long firstIFD = readHeader();
      readSummaryMD();

      fixIndexMap(firstIFD, file.getName());
   }

   public static boolean isMMMultipageTiff(String directory) throws IOException {
      File dir = new File(directory);
      File[] children = dir.listFiles();
      if (children == null) {
         throw new IOException(directory + " does not appear to be a directory; is this a \u00B5Manager dataset?");
      }
      File testFile = null;
      for (File child : children) {
         if (child.isDirectory()) {
            File[] grandchildren = child.listFiles();
            for (File grandchild : grandchildren) {
               if ( (!grandchild.getName().startsWith("._")) && grandchild.getName().endsWith(".tif")) {
                  testFile = grandchild;
                  break;
               }
            }
         } else if ( (!child.getName().startsWith("._")) && 
                 child.getName().endsWith(".tif") || child.getName().endsWith(".TIF")) {
            testFile = child;
            break;
         }
      }
      if (testFile == null) {
         throw new IOException("Unable to find any .tif files in " + directory + "; is this a \u00B5Manager dataset?");
      }
      RandomAccessFile ra;
      try {
         ra = new RandomAccessFile(testFile,"r");
      } catch (FileNotFoundException ex) {
        ReportingUtils.logError(ex);
        return false;
      }
      FileChannel channel = ra.getChannel();
      ByteBuffer tiffHeader = ByteBuffer.allocate(36);
      ByteOrder bo;
      channel.read(tiffHeader,0);
      char zeroOne = tiffHeader.getChar(0);
      if (zeroOne == 0x4949 ) {
         bo = ByteOrder.LITTLE_ENDIAN;
      } else if (zeroOne == 0x4d4d ) {
         bo = ByteOrder.BIG_ENDIAN;
      } else {
         throw new IOException("Error reading TIFF header");
      }
      tiffHeader.order(bo);
      int summaryMDHeader = tiffHeader.getInt(32);
      channel.close();
      ra.close();
      if (summaryMDHeader == MultipageTiffWriter.SUMMARY_MD_HEADER) {
         return true;
      }
      return false;
   }

   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   public DefaultImage readImage(Coords coords) throws IOException {
      if (!coordsToOffset_.containsKey(coords)) {
         // Coordinates not in our map; maybe the writer hasn't finished
         // writing it?
         return null;
      }
      if (fileChannel_ == null) {
         ReportingUtils.logError("Attempted to read image on FileChannel that is null");
         return null;
      }
      long byteOffset = coordsToOffset_.get(coords);

      IFDData data = readIFD(byteOffset);
      return (DefaultImage) readImage(data);
   }

   public Set<Coords> getIndexKeys() {
      if (coordsToOffset_ == null)
         return null;
      return coordsToOffset_.keySet();
   }

   private void readSummaryMD() throws IOException {
      ByteBuffer mdInfo = ByteBuffer.allocate(8).order(byteOrder_);
      fileChannel_.read(mdInfo, 32);
      int header = mdInfo.getInt(0);
      int length = mdInfo.getInt(4);

      if (header != MultipageTiffWriter.SUMMARY_MD_HEADER) {
         ReportingUtils.logError("Summary Metadata Header Incorrect");
      }

      ByteBuffer mdBuffer = ByteBuffer.allocate(length).order(byteOrder_);
      fileChannel_.read(mdBuffer, 40);
      String summaryJSON = getString(mdBuffer);

      JsonParser parser = new JsonParser();
      JsonReader reader = new JsonReader(new StringReader(summaryJSON));
      reader.setLenient(true);
      JsonElement summaryGson = parser.parse(reader);

      imageFormatReadFromSummary_ = NonPropertyMapJSONFormats.imageFormat().
            fromGson(summaryGson);
      summaryMetadata_ = DefaultSummaryMetadata.fromPropertyMap(
            NonPropertyMapJSONFormats.summaryMetadata().fromGson(summaryGson));
   }

   /**
    * Read the comments block from the end of the file. This should be a
    * JSONObject containing two potential types of comment: a summary comment
    * and per-image comments. They're stored under the "Summary" key for the
    * summary comment, and under coordinate strings for the per-image comments.
    * In the event that we find comments here *and* there is no existing
    * Annotation storing comment data for this Datastore, we should convert
    * any comments we find here into an Annotation.
    */
   private void readComments() throws IOException {
      Datastore store = masterStorage_.getDatastore();
      if (CommentsHelper.hasAnnotation(store)) {
         // Already have a comments annotation set up; bail.
         return;
      }
      boolean didCreate = false;
      ByteBuffer buffer = null;
      try {
         long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, 24);
         ByteBuffer header = readIntoBuffer(offset, 8);
         if (header.getInt(0) != MultipageTiffWriter.COMMENTS_HEADER) {
            ReportingUtils.logError("Can't find image comments in file: " + file_.getName());
            return;
         }
         buffer = readIntoBuffer(offset + 8, header.getInt(4));
         JSONObject comments = new JSONObject(getString(buffer));
         Coords.CoordsBuilder builder = new DefaultCoords.Builder();
         for (String key : MDUtils.getKeys(comments)) {
            if (comments.getString(key).equals("")) {
               continue;
            }
            if (!didCreate) {
               // Have at least one comment, so create the annotation.
               CommentsHelper.createAnnotation(store);
               didCreate = true;
            }
            if (key.equals("Summary")) {
               CommentsHelper.setSummaryComment(store, comments.getString(key));
               continue;
            }
            // Generate a Coords object from the key string. The string is
            // formatted as channel_z_time_position.
            int[] indices = MDUtils.getIndices(key);
            builder.channel(indices[0]).z(indices[1]).time(indices[2])
               .stagePosition(indices[3]);
            CommentsHelper.setImageComment(store, builder.build(),
                  comments.getString(key));
         }
         if (didCreate) {
            CommentsHelper.saveComments(store);
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to generate JSON from buffer " + getString(buffer));
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error reading comments block");
      }
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
         throw new IOException("Offset header incorrect, expected: " + offsetHeaderVal +"   found: " + offsetHeader);
      }
      return unsignInt(buffer1.getInt(4));     
   }

   private void readIndexMap() throws IOException, InvalidIndexMapException {
      long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.INDEX_MAP_OFFSET_HEADER, 8);
      ByteBuffer header = readIntoBuffer(offset, 8);
      if (header.getInt(0) != MultipageTiffWriter.INDEX_MAP_HEADER) {
         throw new InvalidIndexMapException();
      }
      int numMappings = header.getInt(4);
      coordsToOffset_ = new HashMap<Coords, Long>();
      ByteBuffer mapBuffer = readIntoBuffer(offset+8, 20*numMappings);     
      for (int i = 0; i < numMappings; i++) {
         int channel = mapBuffer.getInt(i*20);
         int slice = mapBuffer.getInt(i*20+4);
         int frame = mapBuffer.getInt(i*20+8);
         int position = mapBuffer.getInt(i*20+12);
         long imageOffset = unsignInt(mapBuffer.getInt(i*20+16));
         if (imageOffset == 0) {
            break; // end of index map reached
         }
         //If a duplicate label is read, forget about the previous one
         //if data has been intentionally overwritten, this gives the most current version
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         builder.channel(channel)
                 .z(slice)
                 .t(frame)
                 .stagePosition(position);
         coordsToOffset_.put(builder.build(), imageOffset);
      }
   }

   private IFDData readIFD(long byteOffset) throws IOException {
      ByteBuffer buff = readIntoBuffer(byteOffset, 2);
      int numEntries = buff.getChar(0);

      ByteBuffer entries = readIntoBuffer(byteOffset + 2, numEntries * 12 + 4).order(byteOrder_);
      IFDData data = new IFDData();
      for (int i = 0; i < numEntries; i++) {
         IFDEntry entry = readDirectoryEntry(i * 12, entries);
         if (entry.tag == MM_METADATA) {
            data.mdOffset = entry.value;
            data.mdLength = entry.count;
         } else if (entry.tag == STRIP_OFFSETS) {
            data.pixelOffset = entry.value;
         } else if (entry.tag == STRIP_BYTE_COUNTS) {
            data.bytesPerImage = entry.value;
         }
      }
      data.nextIFD = unsignInt(entries.getInt(numEntries * 12));
      data.nextIFDOffsetLocation = byteOffset + 2 + numEntries * 12;
      if (data.pixelOffset == 0 || data.bytesPerImage == 0
              || data.mdOffset == 0 || data.mdLength == 0) {
         throw new IOException("Failed to read image from file at offset "
                 + byteOffset);
      }
      //ReportingUtils.logError("At " + byteOffset + " read data " + data);
      return data;
   }

   private String getString(ByteBuffer buffer) {
      try {
         return new String(buffer.array(), "UTF-8");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }

   private Image readImage(IFDData data) throws IOException {
      ByteBuffer pixelBuffer = ByteBuffer.allocate((int) data.bytesPerImage).order(byteOrder_);
      ByteBuffer mdBuffer = ByteBuffer.allocate((int) data.mdLength).order(byteOrder_);
      fileChannel_.read(pixelBuffer, data.pixelOffset);
      fileChannel_.read(mdBuffer, data.mdOffset);

      String mdJSON = getString(mdBuffer);
      JsonParser parser = new JsonParser();
      JsonReader reader = new JsonReader(new StringReader(mdJSON));
      reader.setLenient(true);
      JsonElement mdGson = parser.parse(reader);

      try {
         PropertyMap formatPmap = NonPropertyMapJSONFormats.imageFormat().
                 fromGson(mdGson);
         Coords coords = DefaultCoords.fromPropertyMap(
                 NonPropertyMapJSONFormats.coords().fromGson(mdGson));
         Metadata metadata = DefaultMetadata.fromPropertyMap(
                 NonPropertyMapJSONFormats.metadata().fromGson(mdGson));

         // Usually we get the width, height, and pixel type from the image (plane)
         // metadata. If it's not there, we use the values found in the summary
         // metadata.
         int width = formatPmap.getInteger(PropertyKey.WIDTH.key(), 0);
         int height = formatPmap.getInteger(PropertyKey.HEIGHT.key(), 0);
         if (width < 1 || height < 1) {
            width = imageFormatReadFromSummary_.getInteger(PropertyKey.WIDTH.key(), 0);
            height = imageFormatReadFromSummary_.getInteger(PropertyKey.HEIGHT.key(), 0);
            if (width < 1 || height < 1) {
               // TODO We should probably try the IFD before giving up
               throw new IOException("Cannot find image width and height");
            }
            formatPmap = formatPmap.copyBuilder().
                    putInteger(PropertyKey.WIDTH.key(), width).
                    putInteger(PropertyKey.HEIGHT.key(), height).
                    build();
         }

         PixelType pixelType = formatPmap.getStringAsEnum(
                 PropertyKey.PIXEL_TYPE.key(), PixelType.class, null);
         if (pixelType == null) {
            pixelType = imageFormatReadFromSummary_.getStringAsEnum(
                    PropertyKey.PIXEL_TYPE.key(), PixelType.class, null);
            if (pixelType == null) {
               // TODO We should probably try the IFD before giving up
               throw new IOException("Cannot find image width and height");
            }
            formatPmap = formatPmap.copyBuilder().putEnumAsString(
                    PropertyKey.PIXEL_TYPE.key(), pixelType).build();
         }

         // TODO We should avoid converting to Java array and back, instead using
         // a nio buffer directly as the Image storage (even better if memory
         // mapped).
         switch (pixelType) {
            case GRAY8:
               return new DefaultImage(pixelBuffer.array(), formatPmap,
                       coords, metadata);
            case GRAY16:
               short[] pixels16 = new short[pixelBuffer.capacity() / 2];
               for (int i = 0; i < pixels16.length; i++) {
                  pixels16[i] = pixelBuffer.getShort(i * 2);
               }
               return new DefaultImage(pixels16, formatPmap, coords, metadata);
            case RGB32:
               byte[] pixelsARGB = new byte[(int) (4 * data.bytesPerImage / 3)];
               int i = 0;
               for (byte b : pixelBuffer.array()) {
                  pixelsARGB[i] = b;
                  i++;
                  if ((i + 1) % 4 == 0) {
                     pixelsARGB[i] = 0;
                     i++;
                  }
               }
               return new DefaultImage(pixelsARGB, formatPmap, coords, metadata);
            default:
               throw new IOException("Unknown pixel type: " + pixelType.name());
         }
      } catch (IllegalStateException ise) {

         // can be thrown when meatadata are bad, todo: report
         return null;
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
   private long readHeader() throws IOException {           
      ByteBuffer tiffHeader = ByteBuffer.allocate(8);
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
      return unsignInt(tiffHeader.getInt(4));
   }

   private byte[] getBytesFromString(String s) {
      try {
         return s.getBytes("UTF-8");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError("Error encoding String to bytes");
         return null;
      }
   }

   private void createFileChannel(boolean isReadWrite) throws FileNotFoundException, IOException {      
      raFile_ = new RandomAccessFile(file_, isReadWrite ? "rw" : "r");
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

   // This code is intended for use in the scenario in which a datset
   // terminates before properly closing, thereby preventing the multipage tiff
   // writer from putting in the index map, comments, channels, and OME XML in
   // the ImageDescription tag location
   private void fixIndexMap(long firstIFD, String fileName) throws IOException {
      long filePosition = firstIFD;
      coordsToOffset_ = new HashMap<Coords, Long>();
      long progBarMax = (fileChannel_.size() / 2L);
      final ProgressBar progressBar = new ProgressBar(null, "Fixing " + fileName, 0, 
              progBarMax >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) progBarMax);
      progressBar.setRange(0, progBarMax >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) progBarMax);
      progressBar.setProgress(0);
      progressBar.setVisible(true);
      long nextIFDOffsetLocation = 0;
      IFDData data;
      while (filePosition > 0) {
         try {
            data = readIFD(filePosition);
            if (data.nextIFD == 0) {
               break;
            }
            Image image = readImage(data);
            if (((DefaultMetadata) image.getMetadata()).toPropertyMap().equals(
                  new DefaultMetadata.Builder().build())) {
               //Blank placeholder image, dont add to index map
               filePosition = data.nextIFD;
               nextIFDOffsetLocation = data.nextIFDOffsetLocation;
               continue;
            }
            coordsToOffset_.put(image.getCoords(), filePosition);

            final int progress = (int) (filePosition/2L);
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  progressBar.setProgress(progress);
               }
            });

            if (data.nextIFD <= filePosition || data.nextIFDOffsetLocation <= nextIFDOffsetLocation ) {
               break; //so no recoverable data is ever lost
            }
            filePosition = data.nextIFD;
            nextIFDOffsetLocation = data.nextIFDOffsetLocation;
         } catch (Exception e) {
            break;
         }
      }
      progressBar.setVisible(false);

      filePosition += writeIndexMap(filePosition);

      ByteBuffer buffer = ByteBuffer.allocate(4).order(byteOrder_);
      buffer.putInt(0, 0);
      fileChannel_.write(buffer, nextIFDOffsetLocation); 

      filePosition += writeDisplaySettings(
            DefaultDisplaySettings.builder().build(), filePosition);

      fileChannel_.close();
      raFile_.close();
      //reopen
      createFileChannel(false);
   }

   // TODO: There is a very similar but not identical method in the Writer
   private int writeDisplaySettings(DisplaySettings settings, long filePosition) throws IOException {
      String settingsJSON = ((DefaultDisplaySettings) settings).toPropertyMap().toJSON();
      int numReservedBytes = settingsJSON.length() * MultipageTiffWriter.DISPLAY_SETTINGS_BYTES_PER_CHANNEL;
      ByteBuffer header = ByteBuffer.allocate(8).order(MultipageTiffWriter.BYTE_ORDER);
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(settingsJSON));
      header.putInt(0, MultipageTiffWriter.DISPLAY_SETTINGS_HEADER);
      header.putInt(4, numReservedBytes);
      fileChannel_.write(header, filePosition);
      fileChannel_.write(buffer, filePosition + 8);

      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(MultipageTiffWriter.BYTE_ORDER);
      offsetHeader.putInt(0, MultipageTiffWriter.DISPLAY_SETTINGS_OFFSET_HEADER);
      offsetHeader.putInt(4, (int) filePosition);
      fileChannel_.write(offsetHeader, 16);
      return numReservedBytes + 8;
   }

   private int writeIndexMap(long filePosition) throws IOException {
      // TODO: this method presumes only four axes exist.
      //Write 4 byte header, 4 byte number of entries, and 20 bytes for each
      //entry
      int numMappings = coordsToOffset_.size();
      ByteBuffer buffer = ByteBuffer.allocate(8 + 20 * numMappings).order(byteOrder_);
      buffer.putInt(0, MultipageTiffWriter.INDEX_MAP_HEADER);
      buffer.putInt(4, numMappings);
      int position = 2;
      for (Coords coords : coordsToOffset_.keySet()) {
         for (String axis : ALLOWED_AXES) {
            buffer.putInt(4 * position, coords.getIndex(axis));
            position++;
         }
         // TODO: this probably doesn't help our performance any, but I want
         // the extra logging just in case.
         for (String axis : coords.getAxes()) {
            if (!ALLOWED_AXES.contains(axis)) {
               ReportingUtils.logError("Axis " + axis + " is ignored because it is not one of " + ALLOWED_AXES.toString());
            }
         }
         buffer.putInt(4 * position, coordsToOffset_.get(coords).intValue());
         position++;
      }
      fileChannel_.write(buffer, filePosition);

      ByteBuffer header = ByteBuffer.allocate(8).order(byteOrder_);
      header.putInt(0, MultipageTiffWriter.INDEX_MAP_OFFSET_HEADER);
      header.putInt(4, (int) filePosition);
      fileChannel_.write(header, 8);
      return buffer.capacity();
   }

   private class IFDData {
      public long pixelOffset;
      public long bytesPerImage;
      public long mdOffset;
      public long mdLength;
      public long nextIFD;
      public long nextIFDOffsetLocation;

      public IFDData() {}

      @Override
      public String toString() {
         return String.format("<IFDData offset %d, bytes %d, metadata offset %d, metadata length %d, next %d, next offset %d>",
               pixelOffset, bytesPerImage, mdOffset, mdLength, nextIFD,
               nextIFDOffsetLocation);
      }
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

      @Override
      public String toString() {
         return String.format("<IFDEntry tag 0x%s, type 0x%s, count %d, value %d>",
               Integer.toHexString((int) tag),
               Integer.toHexString((int) type), count, value);
      }
   }
}