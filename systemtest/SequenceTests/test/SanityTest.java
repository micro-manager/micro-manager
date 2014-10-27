import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.PositionList;
import org.micromanager.api.SequenceSettings;
import org.micromanager.testing.TestImageDecoder;
import static org.junit.Assert.*;
import static org.micromanager.acquisition.TaggedImageQueue.POISON;
import static org.micromanager.testing.TestImageDecoder.InfoPacket;


public class SanityTest {
   CMMCore mmc;

   @Rule
   public ExternalResource coreResource = new ExternalResource() {
      @Override protected void before() throws Exception {
         String jniLibPath = System.getenv("MMCOREJ_LIBRARY_PATH");
         String adapterPath = System.getenv("MMTEST_ADAPTER_PATH");

         System.setProperty("mmcorej.library.loading.stderr.log", "yes");
         if (jniLibPath != null) {
            System.setProperty("mmcorej.library.path", jniLibPath);
         }

         mmc = new CMMCore();

         mmc.enableStderrLog(true);
         mmc.enableDebugLog(true);

         StrVector paths = new StrVector();
         if (adapterPath != null) {
            paths.add(adapterPath);
         }
         mmc.setDeviceAdapterSearchPaths(paths);

         mmc.loadDevice("THub", "SequenceTester", "THub");
         mmc.initializeDevice("THub");
      }

      @Override protected void after() {
         // Ensure CoreLog is finished
         mmc.delete();
      }
   };

   @Test
   public void sequenceTestCameraWorks() throws Exception {
      // Prefix matching allows us to create arbitrary devices.
      // Device name and label always match, to simplify things.
      String camera = "TCamera-0";
      prepareTestDevices(mmc, camera);
      mmc.setCameraDevice(camera);

      // Default SequenceSettings should do a single snap.
      SequenceSettings mdaSeq = new SequenceSettings();
      IAcquisitionEngine2010 ae = new AcquisitionEngine2010(mmc);
      BlockingQueue<TaggedImage> outQ = ae.run(mdaSeq, true, null, null);

      List<InfoPacket> packets = collectImagesFromAE2010(outQ);

      assertEquals(1, packets.size());

      InfoPacket packet = packets.get(0);
      assertEquals(0, packet.packetNumber);
      assertEquals(camera, packet.camera.name);
      assertFalse(packet.camera.isSequence);
      assertEquals(0, packet.camera.serialNumber);
      assertEquals(0, packet.camera.frameNumber);
      assertTrue(packet.startCounter < packet.currentCounter);
   }

   public void prepareTestDevices(CMMCore mmc, String... devices) throws Exception {
      for (String device : devices) {
         mmc.loadDevice(device, "SequenceTester", device);
         mmc.initializeDevice(device);
      }
   }

   public List<InfoPacket>
      collectImagesFromAE2010(BlockingQueue<TaggedImage> q)
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

         System.out.print("Packet " + packetCount + ": ");
         TestImageDecoder.dumpJSON(data, System.out);
         System.out.println();

         packets.add(TestImageDecoder.decode(data));
      }
      return packets;
   }
}
