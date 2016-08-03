
package org.micromanager.internal.utils;

import java.io.UnsupportedEncodingException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 *
 * @author Arthur
 */
public final class DirectBuffers {
   
   public static ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();
   
   public static ByteBuffer bufferFromBytes(byte[] bytes) {
      return ByteBuffer.allocateDirect(bytes.length).put(bytes);
   }
   
   public static ShortBuffer bufferFromShorts(short[] shorts) {
      return ByteBuffer.allocateDirect(2*shorts.length).order(NATIVE_ORDER).asShortBuffer().put(shorts);
   }
   
   public static IntBuffer bufferFromInts(int[] ints) {
      return ByteBuffer.allocateDirect(4*ints.length).order(NATIVE_ORDER).asIntBuffer().put(ints);
   }
      
   public static byte[] bytesFromBuffer(ByteBuffer buffer) {
      synchronized(buffer) {
         byte[] bytes = new byte[buffer.capacity()];
         buffer.rewind();
         buffer.get(bytes);
         return bytes;
      }
   }
   
   public static short[] shortsFromBuffer(ShortBuffer buffer) {
      synchronized(buffer) {
         short[] shorts = new short[buffer.capacity()];
         buffer.rewind();
         buffer.get(shorts);
         return shorts;
      }
   }

   public static int[] intsFromBuffer(IntBuffer buffer) {
      synchronized(buffer) {
         int[] ints = new int[buffer.capacity()];
         buffer.rewind();
         buffer.get(ints);
         return ints;
      }
   }

   public static Object arrayFromBuffer(Buffer buffer) {
      synchronized(buffer) {
         if (buffer instanceof ByteBuffer) {
            return bytesFromBuffer((ByteBuffer) buffer);
         } else if (buffer instanceof ShortBuffer) {
            return shortsFromBuffer((ShortBuffer) buffer);
         } else if (buffer instanceof IntBuffer) {
            return intsFromBuffer((IntBuffer) buffer);
         }
      }
      return null;
   }
   
   public static Buffer bufferFromArray(Object primitiveArray) {
      if (primitiveArray instanceof byte[]) {
         return bufferFromBytes((byte []) primitiveArray);
      } else if (primitiveArray instanceof short[]) {
         return bufferFromShorts((short []) primitiveArray);
      } else if (primitiveArray instanceof int[]) {
         return bufferFromInts((int []) primitiveArray);
      }
      return null;
   }
   
   public static ByteBuffer bufferFromString(String string) {
      try {
         return bufferFromBytes(string.getBytes("UTF-8"));
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public static String stringFromBuffer(ByteBuffer byteBuffer) {
      try {
         return new String(bytesFromBuffer(byteBuffer), "UTF-8");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }
   
}
