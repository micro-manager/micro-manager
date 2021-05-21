
package org.micromanager.data.internal;

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

   public static byte[] getByteArray(Buffer rawPixels_) {
      if (rawPixels_ instanceof IntBuffer) {
         IntBuffer buf = (IntBuffer) rawPixels_;
         ByteBuffer bb = ByteBuffer.allocate(rawPixels_.remaining() * 4);
         while (buf.hasRemaining()) {
            bb.putInt(buf.get());
         }
         buf.rewind();
         return bb.array();
      } else if (rawPixels_ instanceof ShortBuffer) {
         ShortBuffer buf = (ShortBuffer) rawPixels_;
         ByteBuffer bb = ByteBuffer.allocate(rawPixels_.remaining() * 2);
         while (buf.hasRemaining()) {
            bb.putShort(buf.get());
         }
         buf.rewind();
         return bb.array();
      } else if (rawPixels_ instanceof ByteBuffer) {
         return ((ByteBuffer) rawPixels_).array();
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
