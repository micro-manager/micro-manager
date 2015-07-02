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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ProgressBar;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.VersionUtils;


public class MultipageTiffReader {

   private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);
   public static final char BITS_PER_SAMPLE = MultipageTiffWriter.BITS_PER_SAMPLE;
   public static final char STRIP_OFFSETS = MultipageTiffWriter.STRIP_OFFSETS;    
   public static final char SAMPLES_PER_PIXEL = MultipageTiffWriter.SAMPLES_PER_PIXEL;
   public static final char STRIP_BYTE_COUNTS = MultipageTiffWriter.STRIP_BYTE_COUNTS;
   public static final char IMAGE_DESCRIPTION = MultipageTiffWriter.IMAGE_DESCRIPTION;

   public static final char MM_METADATA = MultipageTiffWriter.MM_METADATA;
   // Note: ordering of axes here matches that in MDUtils.getLabel().
   // If you change this, you will need to track down places where the size of
   // the position list is implicitly kept (e.g. in the size of a single index
   // map entry, 20 bytes) and update those locations.
   public static final ImmutableList<String> ALLOWED_AXES = ImmutableList.of("channel", "z", "time", "position");

   private ByteOrder byteOrder_;  
   private File file_;
   private RandomAccessFile raFile_;
   private FileChannel fileChannel_;

   private SummaryMetadata summaryMetadata_;
   private JSONObject summaryJSON_;
   private int byteDepth_ = 0;;
   private boolean rgb_;
   private boolean writingFinished_;
   public static boolean fixIndexMapWithoutPrompt_ = false;

   private HashMap<Coords, Long> coordsToOffset_;

   /**
    * This constructor is used for a file that is currently being written.
    * \param summaryJSON As per DefaultSummaryMetadat.toJSON(), except
    *        augmented with display settings and values that are normally
    *        only stored in image metadata. See the
    *        MultipageTiffWriter.augmentWithImageMetadata() method.
    */
   public MultipageTiffReader(SummaryMetadata summaryMD,
         JSONObject summaryJSON, JSONObject firstImageTags) {
      summaryMetadata_ = summaryMD;
      summaryJSON_ = summaryJSON;
      byteOrder_ = MultipageTiffWriter.BYTE_ORDER;
      getRGBAndByteDepth(firstImageTags);
      writingFinished_ = false;
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
   public MultipageTiffReader(File file) throws IOException {
      file_ = file;
      try {
         createFileChannel();
      } catch (Exception ex) {
         ReportingUtils.showError("Can't successfully open file: " +  file_.getName());
      }
      writingFinished_ = true;
      long firstIFD = readHeader();
      summaryJSON_ = readSummaryMD();
      summaryMetadata_ = DefaultSummaryMetadata.legacyFromJSON(summaryJSON_);

      try {
         readIndexMap();
      } catch (Exception e) {
         try {
            fixIndexMap(firstIFD, file.getName());
         } catch (JSONException ex) {
            ReportingUtils.showError("Fixing of dataset unsuccessful for file: " + file_.getName());
         }
      }
      try {
         if (summaryMetadata_ != null) {
            if (summaryMetadata_.getComments() == null) {
               // Copy out the primary comment into the summary metadata.
               summaryMetadata_ = summaryMetadata_.copy().comments(readComments()).build();
            }
         }
         else {
            ReportingUtils.logError("No SummaryMetadata available to copy comments into.");
         }
      } catch (Exception ex) {
         ReportingUtils.logError("Problem with JSON Representation of DisplayAndComments");
      }

      if (summaryMetadata_ != null) {
         getRGBAndByteDepth(summaryJSON_);
      }
   }

   public static boolean isMMMultipageTiff(String directory) throws IOException {
      File dir = new File(directory);
      File[] children = dir.listFiles();
      if (children == null) {
         throw new IOException("No subfiles within TIFF structure of " + directory + "; is this an MM dataset?");
      }
      File testFile = null;
      for (File child : children) {
         if (child.isDirectory()) {
            File[] grandchildren = child.listFiles();
            for (File grandchild : grandchildren) {
               if (grandchild.getName().endsWith(".tif")) {
                  testFile = grandchild;
                  break;
               }
            }
         } else if (child.getName().endsWith(".tif") || child.getName().endsWith(".TIF")) {
            testFile = child;
            break;
         }
      }
      if (testFile == null) {
         throw new IOException("Unable to find any TIFs in " + directory + "; is this an MM dataset?");
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
         throw new IOException("Error reading Tiff header");
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


   public void finishedWriting() {
      writingFinished_ = true;
   }

   private void getRGBAndByteDepth(JSONObject md) {
      try {
         String pixelType = MDUtils.getPixelType(md);
         rgb_ = pixelType.startsWith("RGB");
         if (pixelType.equals("RGB32") || pixelType.equals("GRAY8")) {
            byteDepth_ = 1;
         } else {
            byteDepth_ = 2;
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   public DefaultImage readImage(Coords coords) {
      if (coordsToOffset_.containsKey(coords)) {
         if (fileChannel_ == null) {
            ReportingUtils.logError("Attempted to read image on FileChannel that is null");
            return null;
         }
         try {
            long byteOffset = coordsToOffset_.get(coords);

            IFDData data = readIFD(byteOffset);
            TaggedImage tagged = readTaggedImage(data);
            // The metadata in the TaggedImage needs to be augmented with
            // fields from the summary JSON, or else we won't be able to
            // construct a DefaultImage from it.
            augmentWithSummaryMetadata(tagged.tags);
            // Manually create new Metadata for the image we're about to
            // create. Just passing the bare TaggedImage in would make
            // Micro-Manager assume that the image was created by the scope
            // this instance of the program is running, which has ramifications
            // for the scope data properties.
            Metadata metadata = DefaultMetadata.legacyFromJSON(tagged.tags);
            // All keys that are part of the scope data cannot be part of
            // the user data.
            // TODO: assumes knowledge of how DefaultMetadata serializes
            // scope data.
            HashSet<String> blockedKeys = new HashSet<String>();
            if (metadata.getScopeData() != null) {
               blockedKeys.add("scopeDataKeys");
               for (String key : ((DefaultPropertyMap) metadata.getScopeData()).getKeys()) {
                  blockedKeys.add(key);
               }
            }
            if (summaryMetadata_.getMetadataVersion() != null &&
                  metadata.getUserData() == null &&
                  VersionUtils.isOlderVersion(
                     summaryMetadata_.getMetadataVersion(), "11")) {
               // These older versions of the metadata don't have a separate
               // location for scope data or user data, so we just stuff all
               // unused tags into the userData section.
               ReportingUtils.logDebugMessage("Importing \"flat\" miscellaneous metadata into the userData structure");
               metadata = metadata.copy().userData(
                     MDUtils.extractUserData(tagged.tags, blockedKeys)).build();
            }
            return new DefaultImage(tagged, null, metadata);
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Couldn't convert TaggedImage to DefaultImage");
         }
         catch (MMScriptException e) {
            ReportingUtils.logError(e, "Couldn't convert TaggedImage to DefaultImage");
         }
         return null;
      } else {
         // Coordinates not in our map; maybe the writer hasn't finished
         // writing it?
         return null;
      }
   }

   /**
    * Given the metadata for a TaggedImage, augment it with fields from the
    * summary JSON that are needed for our DefaultImage class to parse the
    * metadata and image data properly.
    */
   private void augmentWithSummaryMetadata(JSONObject tags) {
      try {
         MDUtils.setWidth(tags, MDUtils.getWidth(summaryJSON_));
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Failed to get image width from summary JSON");
      }
      try {
         MDUtils.setHeight(tags, MDUtils.getHeight(summaryJSON_));
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Failed to get image height from summary JSON");
      }
      try {
         MDUtils.setPixelType(tags, MDUtils.getSingleChannelType(summaryJSON_));
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Failed to get image pixel type from summary JSON");
      }
      catch (MMScriptException e) {
         ReportingUtils.logError(e, "Failed to get image pixel type from summary JSON");
      }
   }

   public Set<Coords> getIndexKeys() {
      if (coordsToOffset_ == null)
         return null;
      return coordsToOffset_.keySet();
   }

   private JSONObject readSummaryMD() {
      try {
         ByteBuffer mdInfo = ByteBuffer.allocate(8).order(byteOrder_);
         fileChannel_.read(mdInfo, 32);
         int header = mdInfo.getInt(0);
         int length = mdInfo.getInt(4);

         if (header != MultipageTiffWriter.SUMMARY_MD_HEADER) {
            ReportingUtils.logError("Summary Metadata Header Incorrect");
            return null;
         }

         ByteBuffer mdBuffer = ByteBuffer.allocate(length).order(byteOrder_);
         fileChannel_.read(mdBuffer, 40);
         JSONObject summaryMD = new JSONObject(getString(mdBuffer));

         return summaryMD;
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't read summary Metadata from file: " + file_.getName());
         return null;
      }
   }

   private String readComments()  {
      try {
         long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, 24);
         ByteBuffer header = readIntoBuffer(offset, 8);
         if (header.getInt(0) != MultipageTiffWriter.COMMENTS_HEADER) {
            ReportingUtils.logError("Can't find image comments in file: " + file_.getName());
            return null;
         }
         ByteBuffer buffer = readIntoBuffer(offset + 8, header.getInt(4));
         return getString(buffer);
      } catch (Exception ex) {
         ReportingUtils.logError("Can't find image comments in file: " + file_.getName());
            return null;
      }
   }

   public void rewriteComments(String comments) throws IOException {
      if (writingFinished_) {
         byte[] bytes = getBytesFromString(comments.toString());
         ByteBuffer byteCount = ByteBuffer.wrap(new byte[4]).order(byteOrder_).putInt(0,bytes.length);
         ByteBuffer buffer = ByteBuffer.wrap(bytes);
         long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.COMMENTS_OFFSET_HEADER, 24);
         fileChannel_.write(byteCount,offset + 4);
         fileChannel_.write(buffer, offset +8);
      }
      summaryMetadata_ = summaryMetadata_.copy().comments(comments).build();
   }

   public void rewriteDisplaySettings(DisplaySettings settings) throws IOException, JSONException {
      if (writingFinished_) {
         long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.DISPLAY_SETTINGS_OFFSET_HEADER, 16);
         int numReservedBytes = readIntoBuffer(offset + 4, 4).getInt(0);
         byte[] blank = new byte[numReservedBytes];
         for (int i = 0; i < blank.length; i++) {
            blank[i] = 0;
         }
         fileChannel_.write(ByteBuffer.wrap(blank), offset+8);
         byte[] bytes = getBytesFromString(settings.toString());
         ByteBuffer buffer = ByteBuffer.wrap(bytes);
         fileChannel_.write(buffer, offset+8);
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

   private void readIndexMap() throws IOException, MMException {
      long offset = readOffsetHeaderAndOffset(MultipageTiffWriter.INDEX_MAP_OFFSET_HEADER, 8);
      ByteBuffer header = readIntoBuffer(offset, 8);
      if (header.getInt(0) != MultipageTiffWriter.INDEX_MAP_HEADER) {
         throw new MMException("Error reading index map header");
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
               .time(frame)
               .stagePosition(position);
         coordsToOffset_.put(builder.build(), imageOffset);
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
      data.nextIFD = unsignInt(entries.getInt(numEntries*12));
      data.nextIFDOffsetLocation = byteOffset + 2 + numEntries*12;
      if (data.pixelOffset == 0 || data.bytesPerImage == 0 ||
            data.mdOffset == 0 || data.mdLength == 0) {
         throw new IOException("Failed to read image from file at offset " +
               byteOffset);
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

   private TaggedImage readTaggedImage(IFDData data) throws IOException {
      ByteBuffer pixelBuffer = ByteBuffer.allocate( (int) data.bytesPerImage).order(byteOrder_);
      ByteBuffer mdBuffer = ByteBuffer.allocate((int) data.mdLength).order(byteOrder_);
      fileChannel_.read(pixelBuffer, data.pixelOffset);
      fileChannel_.read(mdBuffer, data.mdOffset);
      JSONObject md = new JSONObject();
      try {
         md = new JSONObject(getString(mdBuffer));
      } catch (JSONException ex) {
         ReportingUtils.logError(ex, "Couldn't convert file image metadata to JSON");
      }

      if (byteDepth_ == 0) {
         getRGBAndByteDepth(md);
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

   private void createFileChannel() throws FileNotFoundException, IOException {      
      raFile_ = new RandomAccessFile(file_,"rw");
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
   private void fixIndexMap(long firstIFD, String fileName) throws IOException, JSONException {  
      if (!fixIndexMapWithoutPrompt_) {
         ReportingUtils.showError("Can't read index map in file: " + file_.getName());
         int choice = JOptionPane.showConfirmDialog(null, "This file cannot be opened bcause it appears to have \n"
                 + "been improperly saved.  Would you like Micro-Manger to attempt to fix it?", "Micro-Manager", JOptionPane.YES_NO_OPTION);
         if (choice == JOptionPane.NO_OPTION) {
            return;
         }
      }
      fixIndexMapWithoutPrompt_ = true;
      long filePosition = firstIFD;
      coordsToOffset_ = new HashMap<Coords, Long>();
      long progBarMax = (fileChannel_.size() / 2L);
      final ProgressBar progressBar = new ProgressBar("Fixing " + fileName, 0, 
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
            TaggedImage ti = readTaggedImage(data);
            if (ti.tags == null || ti.tags.length() == 0) {  //Blank placeholder image, dont add to index map
               filePosition = data.nextIFD;
               nextIFDOffsetLocation = data.nextIFDOffsetLocation;
               continue;
            }
            coordsToOffset_.put(DefaultCoords.legacyFromJSON(ti.tags),
                  filePosition);

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
            DefaultDisplaySettings.getStandardSettings(), filePosition);

      fileChannel_.close();
      raFile_.close();
      //reopen
      createFileChannel();
   }

   private int writeDisplaySettings(DefaultDisplaySettings settings, long filePosition) throws IOException {
      JSONObject settingsJSON = settings.toJSON();
      int numReservedBytes = settingsJSON.length() * MultipageTiffWriter.DISPLAY_SETTINGS_BYTES_PER_CHANNEL;
      ByteBuffer header = ByteBuffer.allocate(8).order(MultipageTiffWriter.BYTE_ORDER);
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(settingsJSON.toString()));
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

      public String toString() {
         return String.format("<IFDEntry tag 0x%s, type 0x%s, count %d, value %d>",
               Integer.toHexString((int) tag),
               Integer.toHexString((int) type), count, value);
      }
   }
}



