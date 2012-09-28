///////////////////////////////////////////////////////////////////////////////
//FILE:          MPTiffUtils.java
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

import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.MetadataTools;
import loci.formats.in.MicromanagerReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import ome.xml.model.primitives.NonNegativeInteger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

class MPTiffUtils {

   public static final int DISPLAY_SETTINGS_BYTES_PER_CHANNEL = 256;
   //1 MB for now...might have to increase
   public static final long SPACE_FOR_COMMENTS = 1048576;
   public static final int INDEX_MAP_OFFSET_HEADER = 54773648;
   public static final int INDEX_MAP_HEADER = 3453623;
   public static final int DISPLAY_SETTINGS_OFFSET_HEADER = 483765892;
   public static final int DISPLAY_SETTINGS_HEADER = 347834724;
   public static final int COMMENTS_OFFSET_HEADER = 99384722;
   public static final int COMMENTS_HEADER = 84720485;

   /**
    * @return the number of bytes written
    */
   static int writeIndexMap(FileChannel fileChannel, HashMap<String, Long> indexMap, long indexMapOffset, ByteOrder byteOrder) throws IOException {
      //Write 4 byte header, 4 byte number of entries, and 20 bytes for each entry
      int numMappings = indexMap.size();
      ByteBuffer buffer = ByteBuffer.allocate(8 + 20 * numMappings).order(byteOrder);
      buffer.putInt(0, INDEX_MAP_HEADER);
      buffer.putInt(4, numMappings);
      int position = 2;
      for (String label : indexMap.keySet()) {
         String[] indecies = label.split("_");
         for (String index : indecies) {
            buffer.putInt(4 * position, Integer.parseInt(index));
            position++;
         }
         buffer.putInt(4 * position, indexMap.get(label).intValue());
         position++;
      }
      fileChannel.write(buffer, indexMapOffset);

      ByteBuffer header = ByteBuffer.allocate(8).order(byteOrder);
      header.putInt(0, INDEX_MAP_OFFSET_HEADER);
      header.putInt(4, (int) indexMapOffset);
      fileChannel.write(header, 8);
      return buffer.capacity();
   }

   static int writeDisplaySettings(FileChannel fileChannel, JSONObject displayAndComments,
           int numChannels, long displaySettingsOffset, ByteOrder byteOrder) throws IOException {
      JSONArray displaySettings;
      try {
         displaySettings = displayAndComments.getJSONArray("Channels");
      } catch (JSONException ex) {
         displaySettings = new JSONArray();
      }
      int numReservedBytes = numChannels * DISPLAY_SETTINGS_BYTES_PER_CHANNEL;
      ByteBuffer header = ByteBuffer.allocate(8).order(byteOrder);
      ByteBuffer buffer = ByteBuffer.wrap(getBytesFromString(displaySettings.toString()));
      header.putInt(0, DISPLAY_SETTINGS_HEADER);
      header.putInt(4, numReservedBytes);
      fileChannel.write(header, displaySettingsOffset);
      fileChannel.write(buffer, displaySettingsOffset + 8);

      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(byteOrder);
      offsetHeader.putInt(0, DISPLAY_SETTINGS_OFFSET_HEADER);
      offsetHeader.putInt(4, (int) displaySettingsOffset);
      fileChannel.write(offsetHeader, 16);
      return numReservedBytes + 8;
   }

   static int writeComments(FileChannel fileChannel, JSONObject displayAndComments,
           long commentsOffset, ByteOrder byteOrder) throws IOException {
      //Write 4 byte header, 4 byte number of bytes
      JSONObject comments;
      try {
         comments = displayAndComments.getJSONObject("Comments");
      } catch (JSONException ex) {
         comments = new JSONObject();
      }
      String commentsString = comments.toString();
      ByteBuffer header = ByteBuffer.allocate(8).order(byteOrder);
      header.putInt(0, COMMENTS_HEADER);
      header.putInt(4, commentsString.length());
      ByteBuffer buffer = ByteBuffer.wrap(MPTiffUtils.getBytesFromString(commentsString));
      fileChannel.write(header, commentsOffset);
      fileChannel.write(buffer, commentsOffset + 8);

      ByteBuffer offsetHeader = ByteBuffer.allocate(8).order(byteOrder);
      offsetHeader.putInt(0, COMMENTS_OFFSET_HEADER);
      offsetHeader.putInt(4, (int) commentsOffset);
      fileChannel.write(offsetHeader, 24);
      return 8 + commentsString.length();
   }

   static int writeOMEMetadata(FileChannel fileChannel, String omeXML, long omeMetadataOffset,
           long imageDescriptionOffset, ByteOrder byteOrder) throws FormatException, IOException {
      //write first image IFD
      ByteBuffer ifdCountAndValueBuffer = ByteBuffer.allocate(8).order(byteOrder);
      ifdCountAndValueBuffer.putInt(0, omeXML.length());
      ifdCountAndValueBuffer.putInt(4, (int) omeMetadataOffset);
      fileChannel.write(ifdCountAndValueBuffer, imageDescriptionOffset + 4);

      //write OME XML String
      ByteBuffer buffer = ByteBuffer.wrap(MPTiffUtils.getBytesFromString(omeXML));
      fileChannel.write(buffer, omeMetadataOffset);
      return buffer.capacity();
   }

   static void writeNullOffsetAfterLastImage(FileChannel fileChannel, long nexIFDOffsetLocation, ByteOrder byteOrder) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.order(byteOrder);
      buffer.putInt(0,0);
      fileChannel.write(buffer, nexIFDOffsetLocation);
   }

   static byte[] getBytesFromString(String s) {
      try {
         return s.getBytes("US-ASCII");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError("Error encoding String to bytes");
         return null;
      }
   }
}
