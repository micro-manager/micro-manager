package org.micromanager.acqenginetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.internal.MMStudio;
import org.micromanager.testing.TestImageDecoder;
import org.micromanager.testing.TestImageDecoder.InfoPacket;

/**
 * Spike / smoke test that de-risks the whole approach: boot a headless
 * {@link MMStudio}, load SequenceTester devices, run a default (single-snap)
 * acquisition through AcqEngJ, and decode the embedded InfoPacket from the one
 * resulting image in the Datastore.
 *
 * <p>If this passes, the high-level acquisition engines can be driven from
 * JUnit and the rest of the scenario suite is straightforward.
 */
public class SmokeTest {
   private static StudioTestFixture fixture_;

   @BeforeClass
   public static void boot() {
      fixture_ = StudioTestFixture.getInstance();
   }

   @Before
   public void setUp() throws Exception {
      fixture_.reset();
   }

   @Test
   public void bootsStudioAndRunsTimeLapseWithAcqEngJ() throws Exception {
      fixture_.useAcqEngJ(true);
      MMStudio studio = fixture_.getStudio();

      // A minimal but VALID MDA: a short time series. (A completely empty
      // SequenceSettings is not a valid MDA -- the AcqEngJ event iterator
      // requires at least one acquisition axis.)
      final int numFrames = 3;
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(0.0)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      assertNotNull("Acquisition returned a null Datastore", store);

      assertEquals("Expected one image per frame",
            numFrames, store.getNumImages());

      Coords c = store.getUnorderedImageCoords().iterator().next();
      Image img = store.getImage(c);
      assertNotNull(img);

      byte[] pix = (byte[]) img.getRawPixels();
      InfoPacket packet = TestImageDecoder.decode(pix);
      assertEquals("TCamera", packet.camera.name);
      // The image carries a decodable InfoPacket from the SequenceTester
      // camera -- that is enough to prove the engine -> camera -> datastore
      // path works. (Counter ordering is exercised in the scenario tests.)
      assertTrue("counters should be non-negative",
            packet.currentCounter >= 0 && packet.startCounter >= 0);
   }

   @Test
   public void bootsStudioAndRunsTimeLapseWithLegacyEngine() throws Exception {
      fixture_.useAcqEngJ(false);
      MMStudio studio = fixture_.getStudio();

      final int numFrames = 3;
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(0.0)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      assertNotNull("Acquisition returned a null Datastore", store);
      assertEquals("Expected one image per frame",
            numFrames, store.getNumImages());

      Coords c = store.getUnorderedImageCoords().iterator().next();
      byte[] pix = (byte[]) store.getImage(c).getRawPixels();
      InfoPacket packet = TestImageDecoder.decode(pix);
      assertEquals("TCamera", packet.camera.name);
   }
}
