package org.micromanager.data.internal.multipagetiff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A simple class to hold a ByteBuffer and its size.
 */
public final class ImageByteBuffer {
   private final ByteBuffer buffer_;
   private final int size_;
   private final ByteOrder byteOrder_;

   /**
    * Create a new ImageByteBuffer.
    *
    * @param size The size of the buffer.
    * @param byteOrder The byte order of the buffer.
    */
   public ImageByteBuffer(int size, ByteOrder byteOrder) {
      buffer_ = ByteBuffer.allocateDirect(size).order(byteOrder);
      size_ = size;
      byteOrder_ = byteOrder;
   }

   /**
    * Get the ByteBuffer.
    *
    * @return The ByteBuffer.
    */
   public ByteBuffer getBuffer() {
      return buffer_;
   }

   /**
    * Get the size of the buffer.
    *
    * @return The size of the buffer.
    */
   public int getSize() {
      return size_;
   }

   /**
    * Get the byte order of the buffer.
    *
    * @return The byte order of the buffer.
    */
   public ByteOrder getByteOrder() {
      return byteOrder_;
   }
}
