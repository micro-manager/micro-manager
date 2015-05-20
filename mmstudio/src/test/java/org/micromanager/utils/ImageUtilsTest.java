package org.micromanager.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageUtilsTest {
   @Test
   public void unsignedByteValueIsCorrect() {
      assertEquals(0x00, ImageUtils.unsignedValue((byte) 0x00));
      assertEquals(0x01, ImageUtils.unsignedValue((byte) 0x01));
      assertEquals(0xff, ImageUtils.unsignedValue((byte) 0xff));
      assertEquals(0xfe, ImageUtils.unsignedValue((byte) 0xfe));
      assertEquals(0x7f, ImageUtils.unsignedValue((byte) 0x7f));
      assertEquals(0x80, ImageUtils.unsignedValue((byte) 0x80));
   }

   @Test
   public void unsignedShortValueIsCorrect() {
      assertEquals(0x0000, ImageUtils.unsignedValue((short) 0x0000));
      assertEquals(0x0001, ImageUtils.unsignedValue((short) 0x0001));
      assertEquals(0xffff, ImageUtils.unsignedValue((short) 0xffff));
      assertEquals(0xfffe, ImageUtils.unsignedValue((short) 0xfffe));
      assertEquals(0x7fff, ImageUtils.unsignedValue((short) 0x7fff));
      assertEquals(0x8000, ImageUtils.unsignedValue((short) 0x8000));
   }
}
