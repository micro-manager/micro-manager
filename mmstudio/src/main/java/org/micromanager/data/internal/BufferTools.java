package org.micromanager.data.internal;

import java.io.UnsupportedEncodingException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Tools to work with Direct Byte Byffers.
 *
 * @author Arthur
 */
public final class BufferTools {

   public static ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();

   public static ByteBuffer directBufferFromBytes(byte[] bytes) {
      return ByteBuffer.allocateDirect(bytes.length).put(bytes);
   }

   public static ShortBuffer directBufferFromShorts(short[] shorts) {
      return ByteBuffer.allocateDirect(2 * shorts.length).order(NATIVE_ORDER).asShortBuffer()
            .put(shorts);
   }

   public static IntBuffer directBufferFromInts(int[] ints) {
      return ByteBuffer.allocateDirect(4 * ints.length).order(NATIVE_ORDER).asIntBuffer().put(ints);
   }

   public static FloatBuffer directBufferFromFloats(float[] floats) {
      return ByteBuffer.allocateDirect(4 * floats.length).order(NATIVE_ORDER).asFloatBuffer()
            .put(floats);
   }

   /**
    * Copy data from Direct Byte Buffer into a Java array.
    *
    * @param buffer Direct Buffer to be copied.
    * @return Copy of the ByteBuffer as a Java byte[]
    */
   public static byte[] bytesFromBuffer(ByteBuffer buffer) {
      synchronized (buffer) {
         byte[] bytes = new byte[buffer.capacity()];
         buffer.rewind();
         buffer.get(bytes);
         return bytes;
      }
   }

   /**
    * Copy data from Direct Buffer into a Java array.
    *
    * @param buffer Direct Buffer to be copied.
    * @return Copy of the Short Buffer as a Java short[]
    */
   public static short[] shortsFromBuffer(ShortBuffer buffer) {
      synchronized (buffer) {
         short[] shorts = new short[buffer.capacity()];
         buffer.rewind();
         buffer.get(shorts);
         return shorts;
      }
   }

   /**
    * Copy data from Direct Buffer into a Java array.
    *
    * @param buffer Direct Buffer to be copied.
    * @return Copy of the IntBuffer as a Java int[]
    */
   public static int[] intsFromBuffer(IntBuffer buffer) {
      synchronized (buffer) {
         int[] ints = new int[buffer.capacity()];
         buffer.rewind();
         buffer.get(ints);
         return ints;
      }
   }

   /**
    * Copy data from Direct Buffer into a Java array.
    *
    * @param buffer Direct Buffer to be copied.
    * @return Copy of the Buffer as a Java array
    */
   public static float[] floatsFromBuffer(FloatBuffer buffer) {
      synchronized (buffer) {
         float[] floats = new float[buffer.capacity()];
         buffer.rewind();
         buffer.get(floats);
         return floats;
      }
   }

   public static Object arrayFromBuffer(Buffer buffer) {
      synchronized (buffer) {
         if (buffer instanceof ByteBuffer) {
            return bytesFromBuffer((ByteBuffer) buffer);
         } else if (buffer instanceof ShortBuffer) {
            return shortsFromBuffer((ShortBuffer) buffer);
         } else if (buffer instanceof IntBuffer) {
            return intsFromBuffer((IntBuffer) buffer);
         } else if (buffer instanceof FloatBuffer) {
            return floatsFromBuffer((FloatBuffer) buffer);
         }
      }
      return null;
   }

   /**
    * Convert a buffer to an array of `byte` regardless of the underlying data type of the buffer
    * Useful for low-level data manipulation and for efficiently streaming data over a socket. The
    * returned buffer is a copy of the original data.
    *
    * @param rawPixels A buffer of pixel data. IntBuffer, ShortBuffer, and ByteBuffer are currently
    *                  supported.
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
      } else if (rawPixels instanceof FloatBuffer) {
         FloatBuffer buf = (FloatBuffer) rawPixels;
         ByteBuffer bb = ByteBuffer.allocate(buf.remaining() * 4);
         while (buf.hasRemaining()) {
            bb.putFloat(buf.get());
         }
         buf.rewind();
         byte[] arr = bb.array();
         return Arrays.copyOf(arr, arr.length);
      } else {
         throw new RuntimeException(("Unhandled Case."));
      }
   }

   /**
    * Copies a Java primitive Array to a Direct Buffer.
    *
    * @param primitiveArray Primitive Array to be copied
    * @return Direct Buffer copy of the primitive array.
    */
   public static Buffer directBufferFromArray(Object primitiveArray) {
      if (primitiveArray instanceof byte[]) {
         return directBufferFromBytes((byte[]) primitiveArray);
      } else if (primitiveArray instanceof short[]) {
         return directBufferFromShorts((short[]) primitiveArray);
      } else if (primitiveArray instanceof int[]) {
         return directBufferFromInts((int[]) primitiveArray);
      } else if (primitiveArray instanceof float[]) {
         return directBufferFromFloats((float[]) primitiveArray);
      }
      return null;
   }

   /**
    * Copies a Java String to a Direct Buffer.
    *
    * @param string Java String to be copied
    * @return Copy of the String in a Byte Buffer
    */
   public static ByteBuffer directBufferFromString(String string) {
      try {
         return directBufferFromBytes(string.getBytes("UTF-8"));
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   /**
    * Copies a ByteBuffer into a String.
    *
    * @param byteBuffer Buffer to be copied to a String.
    * @return String representation of the Byte Buffer
    */
   public static String stringFromBuffer(ByteBuffer byteBuffer) {
      try {
         return new String(bytesFromBuffer(byteBuffer), "UTF-8");
      } catch (UnsupportedEncodingException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   /**
    * Wraps a primitive array into a Buffer without copying.
    * Supported: byte[] (bpc=1), short[] (bpc=2), float[] (bpc=4).
    * For RGB32 images stored as int[], convert to byte[] first using
    * {@link #rgb32IntToBytes(int[])}, then call this method with bpc=1.
    *
    * @param pixels primitive array (byte[], short[], or float[])
    * @param bytesPerPixel 1 for byte[], 2 for short[], 4 for float[]
    * @return Buffer wrapping the input array (no copy for byte[]/short[]/float[])
    */
   public static Buffer wrapArray(Object pixels, int bytesPerPixel) {
      Buffer buffer;
      switch (bytesPerPixel) {
         case 1:
            buffer = ByteBuffer.wrap((byte[]) pixels);
            break;
         case 2:
            buffer = ShortBuffer.wrap((short[]) pixels);
            break;
         case 4:
            buffer = FloatBuffer.wrap((float[]) pixels);
            break;
         default:
            throw new UnsupportedOperationException("Unimplemented pixel component size");
      }
      return buffer;
   }

   /**
    * Converts an RGB32 int[] pixel array (ImageJ packed 0x00RRGGBB format) to a
    * byte[] in B-G-R-0 order (4 bytes per pixel) matching PixelType.RGB32's
    * component offsets {2, 1, 0}.
    * This is a copy, not a wrap — call once per image construction, not per frame.
    *
    * @param ints packed RGB pixels, one int per pixel (0x00RRGGBB)
    * @return interleaved byte array, 4 bytes per pixel in B, G, R, 0 order
    */
   public static byte[] rgb32IntToBytes(int[] ints) {
      byte[] bytes = new byte[ints.length * 4];
      for (int i = 0; i < ints.length; ++i) {
         bytes[i * 4]     = (byte) ( ints[i]        & 0xFF); // B (offset 0)
         bytes[i * 4 + 1] = (byte) ((ints[i] >>  8) & 0xFF); // G (offset 1)
         bytes[i * 4 + 2] = (byte) ((ints[i] >> 16) & 0xFF); // R (offset 2)
         bytes[i * 4 + 3] = 0;                                // pad
      }
      return bytes;
   }

}
