
package org.micromanager.data.internal;

import java.util.Arrays;
import org.micromanager.internal.utils.ReportingUtils;

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
public final class BufferTools {
   
   public static ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();
   
   public static ByteBuffer directBufferFromBytes(byte[] bytes) {
      return ByteBuffer.allocateDirect(bytes.length).put(bytes);
   }
   
   public static ShortBuffer directBufferFromShorts(short[] shorts) {
      return ByteBuffer.allocateDirect(2*shorts.length).order(NATIVE_ORDER).asShortBuffer().put(shorts);
   }
   
   public static IntBuffer directBufferFromInts(int[] ints) {
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

   /**
    *  Convert a buffer to an array of `byte` regardless of the underlying data type of the buffer
    *  Useful for low-level data manipulation and for efficiently streaming data over a socket. The
    *  returned buffer is a copy of the original data.
    *  
    * @param rawPixels A buffer of pixel data. IntBuffer, ShortBuffer, and ByteBuffer are currently
    *                   supported.
    * @return An array of `byte` containing a copy of the raw data of the rawPixels buffer.
    */
   public static byte[] getByteArray(Buffer rawPixels) {
      if (rawPixels instanceof IntBuffer) {
         IntBuffer buf = (IntBuffer) rawPixels;
         ByteBuffer bb = ByteBuffer.allocate(rawPixels.remaining() * 4);
         while (buf.hasRemaining()) {
            bb.putInt(buf.get());
         }
         buf.rewind();
         byte[] arr = bb.array();
         return Arrays.copyOf(arr, arr.length);
      } else if (rawPixels instanceof ShortBuffer) {
         ShortBuffer buf = (ShortBuffer) rawPixels;
         ByteBuffer bb = ByteBuffer.allocate(rawPixels.remaining() * 2);
         while (buf.hasRemaining()) {
            bb.putShort(buf.get());
         }
         buf.rewind();
         byte[] arr = bb.array();
         return Arrays.copyOf(arr, arr.length);
      } else if (rawPixels instanceof ByteBuffer) {
         byte[] arr = ((ByteBuffer) rawPixels).array();
         return Arrays.copyOf(arr, arr.length);
      } else {
         throw new RuntimeException(("Unhandled Case."));
      }
   }
   
   public static Buffer directBufferFromArray(Object primitiveArray) {
      if (primitiveArray instanceof byte[]) {
         return directBufferFromBytes((byte []) primitiveArray);
      } else if (primitiveArray instanceof short[]) {
         return directBufferFromShorts((short []) primitiveArray);
      } else if (primitiveArray instanceof int[]) {
         return directBufferFromInts((int []) primitiveArray);
      }
      return null;
   }
   
   public static ByteBuffer directBufferFromString(String string) {
      try {
         return directBufferFromBytes(string.getBytes("UTF-8"));
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

   public static Buffer wrapArray(Object pixels, int bytesPerPixel) {
      Buffer buffer;
      switch (bytesPerPixel) {
         case 1:
            buffer = ByteBuffer.wrap((byte[]) pixels);
            break;
         case 2:
            buffer = ShortBuffer.wrap((short[]) pixels);
            break;
         default:
            throw new UnsupportedOperationException ("Unimplemented pixel component size");
      }
      return buffer;
   }
   
}
