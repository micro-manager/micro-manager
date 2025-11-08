package org.micromanager.ae2010tests;

import java.util.List;
import mmcorej.CMMCore;
import org.junit.Rule;
import org.junit.Test;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.SequenceSettings;
import org.micromanager.testing.AE2010ImageDecoder;
import org.micromanager.testing.MMCoreWithTestHubResource;
import static org.junit.Assert.*;
import static org.micromanager.testing.TestImageDecoder.InfoPacket;


public class EmptySettingsTest {
   @Rule
   public MMCoreWithTestHubResource coreResource =
      new MMCoreWithTestHubResource();

   @Test
   public void sequenceTestCameraWorks() throws Exception {
      // Prefix matching allows us to create arbitrary devices.
      // Device name and label always match, to simplify things.
      String camera = "TCamera-0";
      coreResource.prepareTestDevices(camera);

      CMMCore mmc = coreResource.getMMCore();
      mmc.setCameraDevice(camera);

      IAcquisitionEngine2010 ae2010 = new AcquisitionEngine2010(mmc);

      // Default SequenceSettings should do a single snap.
      SequenceSettings mdaSeq = new SequenceSettings();

      List<InfoPacket> packets = AE2010ImageDecoder.collectImages(
            ae2010.run(mdaSeq, true, null, null));

      assertEquals(1, packets.size());

      InfoPacket packet = packets.get(0);
      assertEquals(0, packet.hubGlobalPacketNr);
      assertEquals(camera, packet.camera.name);
      assertEquals(0, packet.camera.serialImageNr);
      assertFalse(packet.camera.isSequence);
      assertEquals(0, packet.camera.cumulativeImageNr);
      assertTrue(packet.startCounter < packet.currentCounter);
   }
}
