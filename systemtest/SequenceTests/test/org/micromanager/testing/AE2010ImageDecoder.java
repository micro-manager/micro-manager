package org.micromanager.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.TaggedImage;
import static org.micromanager.testing.TestImageDecoder.InfoPacket;
import static org.junit.Assert.*;
import static org.micromanager.acquisition.TaggedImageQueue.POISON;


@org.junit.Ignore
public class AE2010ImageDecoder {
   public static List<InfoPacket> collectImages(BlockingQueue<TaggedImage> q)
      throws InterruptedException, java.io.IOException
   {
      List<InfoPacket> packets = new ArrayList<InfoPacket>();
      int packetCount = 0;
      for (;;) {
         TaggedImage tim = q.poll(1, TimeUnit.SECONDS);
         assertNotNull(tim);
         if (tim == POISON) {
            assertTrue("images should not remain in queue after POISON",
                  q.isEmpty());
            break;
         }

         // For now, we always get 8-bit images.
         byte[] data = (byte[]) tim.pix;

         TestImageDecoder.dumpJSON(data, System.out);
         System.out.println();

         packets.add(TestImageDecoder.decode(data));
      }
      return packets;
   }
}
