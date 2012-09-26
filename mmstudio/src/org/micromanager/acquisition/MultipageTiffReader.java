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
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import loci.formats.FormatException;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ProgressBar;
import org.micromanager.utils.ReportingUtils;


public class MultipageTiffReader {
      
   private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);

   
   public static final char BITS_PER_SAMPLE = MultipageTiffWriter.BITS_PER_SAMPLE;
   public static final char STRIP_OFFSETS = MultipageTiffWriter.STRIP_OFFSETS;    
   public static final char SAMPLES_PER_PIXEL = MultipageTiffWriter.SAMPLES_PER_PIXEL;
   public static final char STRIP_BYTE_COUNTS = MultipageTiffWriter.STRIP_BYTE_COUNTS;
   public static final char IMAGE_DESCRIPTION = MultipageTiffWriter.IMAGE_DESCRIPTION;
   
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
      byteOrder_ = MultipageTiffWriter.BYTE_ORDER;
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
         
         long firstIFD = readHeader();
         try {
            readIndexMap();         
            displayAndComments_.put("Channels", readDisplaySettings());
            displayAndComments_.put("Comments", readComments());        
            summaryMetadata_ = readSummaryMD();
         } catch (Exception e) {
            fixInterruptedFile(firstIFD);
         }
         
         getRGBAndByteDepth();
         
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
   
   //This code is intended for use in the scenario in which a datset terminates before properly closing,
   //thereby preventing the multipage tiff writer from putting in the index map, comments, channels, and OME
   //XML in the ImageDescription tag location 
   private void fixInterruptedFile(long firstIFD) throws IOException, JSONException {  
      int choice = JOptionPane.showConfirmDialog(null, "This dataset cannot be opened bcause it appears to have \n"
              + "been improperly saved.  Would you like Micro-Manger to attempt to fix it?"
              , "Micro-Manager", JOptionPane.YES_NO_OPTION);
      if (choice == JOptionPane.NO_OPTION) {
         return;
      }
      summaryMetadata_ = readSummaryMD();
      long nextIFD = firstIFD;
      long firstImageDescriptionOffset = 0;
      indexMap_ = new HashMap<String, Long>();
      final ProgressBar progressBar = new ProgressBar("Fixing dataset", 0, (int) (fileChannel_.size() / 2L));
      progressBar.setRange(0, (int) (fileChannel_.size() / 2L));
      progressBar.setProgress(0);
      progressBar.setVisible(true);
      ArrayList<Integer> zIndices = new ArrayList<Integer>(), tIndices = new ArrayList<Integer>(), cIndices = new ArrayList<Integer>();
      long nextIFDOffsetLocation = 0;
      ArrayList<JSONObject> imgMetadata = new ArrayList<JSONObject>();
      int tMaxIndex =0, zMaxIndex = 0, cMaxIndex = 0;
      while (nextIFD > 0) {
         try {
            IFDData data = readIFD(nextIFD);
            if (nextIFD == firstIFD) {
               firstImageDescriptionOffset = data.imageDescriptionOffset;
            }
            nextIFDOffsetLocation = data.nextIFDOffsetLocation;
            TaggedImage ti = readTaggedImage(data);
            String label = null;
            label = MDUtils.getLabel(ti.tags);
            if (label == null) {
               break;
            }
            
            //OME Tiff only
            if (firstImageDescriptionOffset > 0) {
               try {
                  int z = MDUtils.getSliceIndex(ti.tags), c = MDUtils.getChannelIndex(ti.tags), t = MDUtils.getFrameIndex(ti.tags);
                  tMaxIndex = Math.max(t, tMaxIndex);
                  cMaxIndex = Math.max(c, cMaxIndex);
                  zMaxIndex = Math.max(z, zMaxIndex);
                  zIndices.add(z);
                  cIndices.add(c);
                  tIndices.add(t);
               } catch (JSONException ex) {
                  ReportingUtils.showError("Problem with image metadata: channel, slice, or frame index missing");
               }
               imgMetadata.add(ti.tags);
            }
            
            indexMap_.put(label, nextIFD);
            final int progress = (int) (nextIFD/2L);
            SwingUtilities.invokeLater(new Runnable() {
               public void run() {
                  progressBar.setProgress(progress);
               }
            });
            nextIFD = data.nextIFD;
         } catch (Exception e) {
            break;
         }
      }
      progressBar.setVisible(false);
      
      long filePosition = nextIFD;
      MPTiffUtils.writeNullOffsetAfterLastImage(fileChannel_, nextIFDOffsetLocation, byteOrder_);
      filePosition += MPTiffUtils.writeIndexMap(fileChannel_, indexMap_, filePosition, byteOrder_);
      if (firstImageDescriptionOffset > 0) {
         //This dataset is meant to be an OMETiff, so write it as such
         summaryMetadata_.put("Frames", tMaxIndex+1);   
         summaryMetadata_.put("Slices", zMaxIndex+1);    
         summaryMetadata_.put("Channels", cMaxIndex+1);    
         StringBuffer mdBuffer = MPTiffUtils.startBufferingMetadataFile(summaryMetadata_);
         
         for (JSONObject tags : imgMetadata) {
            MPTiffUtils.bufferImageMetadata(tags, mdBuffer);
         }
               
         MPTiffUtils.finishBufferedMetadata(mdBuffer);
         try {
            filePosition += MPTiffUtils.writeOMEMetadata(fileChannel_, mdBuffer.toString(), filePosition, zIndices, 
                       tIndices, cIndices, firstImageDescriptionOffset, byteOrder_);
         } catch (FormatException ex) {
            ReportingUtils.showError("Problem writing OME XML Metadata");
         }
      }
      JSONObject displayAndComments = VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMetadata_);
      int numChannels;
      try {
         numChannels = MDUtils.getNumChannels(summaryMetadata_);
      } catch (Exception ex) {
         numChannels = 7;
      }
      filePosition += MPTiffUtils.writeDisplaySettings(fileChannel_, displayAndComments, numChannels, filePosition, byteOrder_);
      filePosition += MPTiffUtils.writeComments(fileChannel_, displayAndComments, filePosition, byteOrder_);
      raFile_.setLength(filePosition + 8);
   }
   
   public static boolean isMMMultipageTiff(String directory) throws IOException {
      File dir = new File(directory);
      File[] children = dir.listFiles();
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
         } else if (child.getName().endsWith(".tif")) {
            testFile = child;
            break;
         }
      }
      if (testFile == null) {
         throw new IOException("Unexpected file structure: is this an MM dataset?");
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

   private void getRGBAndByteDepth() {
      try {
         String pixelType = MDUtils.getPixelType(summaryMetadata_);
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
      if (displayAndComments_ != null && displayAndComments_.has("Comments") 
              && displayAndComments_.getJSONObject("Comments").has("Summary")) {
         summaryMD.put("Comment", displayAndComments_.getJSONObject("Comments").getString("Summary"));
      }
      return summaryMD;
   }
   
   private JSONObject readComments() throws IOException, JSONException, MMException {
      long offset = readOffsetHeaderAndOffset(MPTiffUtils.COMMENTS_OFFSET_HEADER, 24);
      ByteBuffer header = readIntoBuffer(offset, 8);
      if (header.getInt(0) != MPTiffUtils.COMMENTS_HEADER) {
         throw new MMException("Error reading comments header");
      }
      ByteBuffer buffer = readIntoBuffer(offset + 8, header.getInt(4));
      return new JSONObject(getString(buffer));
   }
   
   public void rewriteComments(JSONObject comments) throws IOException, JSONException {
      if (writingFinished_) {
         byte[] bytes = getBytesFromString(comments.toString());
         ByteBuffer byteCount = ByteBuffer.wrap(new byte[4]).order(byteOrder_).putInt(0,bytes.length);
         ByteBuffer buffer = ByteBuffer.wrap(bytes);
         long offset = readOffsetHeaderAndOffset(MPTiffUtils.COMMENTS_OFFSET_HEADER, 24);
         fileChannel_.write(byteCount,offset + 4);
         fileChannel_.write(buffer, offset +8);
      }
      displayAndComments_.put("Comments", comments);
   }

   public void rewriteDisplaySettings(JSONArray settings) throws IOException, JSONException {
      if (writingFinished_) {
         long offset = readOffsetHeaderAndOffset(MPTiffUtils.DISPLAY_SETTINGS_OFFSET_HEADER, 16);        
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
      displayAndComments_.put("Channels", settings);
   }

   private JSONArray readDisplaySettings() throws IOException, JSONException, MMException {
     long offset = readOffsetHeaderAndOffset(MPTiffUtils.DISPLAY_SETTINGS_OFFSET_HEADER,16);
     ByteBuffer header = readIntoBuffer(offset, 8);
     if (header.getInt(0) != MPTiffUtils.DISPLAY_SETTINGS_HEADER) {
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
         throw new IOException("Offset header incorrect, expected: " + offsetHeaderVal +"   found: " + offsetHeader);
      }
      return unsignInt(buffer1.getInt(4));     
   }

   private void readIndexMap() throws IOException, MMException {
      long offset = readOffsetHeaderAndOffset(MPTiffUtils.INDEX_MAP_OFFSET_HEADER, 8);
      ByteBuffer header = readIntoBuffer(offset, 8);
      if (header.getInt(0) != MPTiffUtils.INDEX_MAP_HEADER) {
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
         } else if (entry.tag == IMAGE_DESCRIPTION) {
            data.imageDescriptionOffset = byteOffset + 2 + 12*i;
         } 
      }
      data.nextIFD = unsignInt(entries.getInt(numEntries*12));
      data.nextIFDOffsetLocation = byteOffset + 2 + numEntries*12;
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
      public long nextIFD;
      public long nextIFDOffsetLocation;
      public long imageDescriptionOffset;
      
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