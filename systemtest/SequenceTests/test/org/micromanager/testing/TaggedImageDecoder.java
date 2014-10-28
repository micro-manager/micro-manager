package org.micromanager.testing;

import java.io.IOException;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import static org.micromanager.testing.TestImageDecoder.InfoPacket;


@org.junit.Ignore
public class TaggedImageDecoder {
   public static InfoPacket decode(TaggedImage image)
      throws IOException
   {
      byte[] bytes = (byte[]) image.pix;
      return TestImageDecoder.decode(bytes);
   }

   public static void dumpJSON(TaggedImage image, java.io.OutputStream out)
      throws IOException
   {
      byte[] bytes = (byte[]) image.pix;
      TestImageDecoder.dumpJSON(bytes, out);
   }

   public static InfoPacket snapAndDecode(CMMCore mmc) throws Exception {
      mmc.snapImage();
      TaggedImage image = mmc.getTaggedImage();

      dumpJSON(image, System.out);
      System.out.println();

      return decode(image);
   }
}
