package org.micromanager.acqenginetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.testing.TestImageDecoder;
import org.micromanager.testing.TestImageDecoder.InfoPacket;

/**
 * Convenience wrapper around the frozen {@link Datastore} returned by an
 * acquisition, with assertions tailored to the SequenceTester-backed engine
 * tests.
 */
public final class AcqResult {
   private final Datastore store_;

   public AcqResult(Datastore store) {
      assertNotNull("Acquisition returned a null Datastore", store);
      store_ = store;
      waitUntilFrozen(store);
   }

   /**
    * runAcquisitionWithSettings(..., true) blocks until the acquisition ENGINE
    * finishes, but the images flow to the Datastore on a separate sink thread
    * and the store is frozen slightly afterwards. Reading the image count too
    * early can miss the last image(s). Wait (bounded) for the store to freeze.
    */
   private static void waitUntilFrozen(Datastore store) {
      final long deadline = System.currentTimeMillis() + 10_000L;
      try {
         while (!store.isFrozen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
         }
         // Give any final in-flight image a moment to be added after freeze.
         Thread.sleep(50);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   public Datastore store() {
      return store_;
   }

   public int numImages() {
      return store_.getNumImages();
   }

   /** Number of indices used along an axis (Coords.CHANNEL, .Z, .T, .P). */
   public int axisLength(String axis) {
      return store_.getNextIndex(axis);
   }

   public List<String> axes() {
      return store_.getAxes();
   }

   /**
    * Asserts that exactly the expected hyper-rectangle of coordinates is
    * present: one image for every (frame, channel, slice, position) in range,
    * no missing coordinates and no duplicates / extras.
    *
    * <p>Pass 1 for any axis that is not used (a single index at 0).
    */
   public void assertHasAllCoords(int frames, int channels, int slices,
                                  int positions) throws IOException {
      assertEquals("total image count",
            frames * channels * slices * positions, numImages());

      Set<Coords> seen = new HashSet<>();
      for (Coords c : store_.getUnorderedImageCoords()) {
         assertTrue("duplicate coords: " + c, seen.add(c));
      }
      assertEquals("unique image count", numImages(), seen.size());

      // The engine omits value-0 singleton axes (DefaultCoords gotcha), so we
      // match by per-axis index (treating an absent axis as index 0) rather
      // than building an exact Coords and calling getImage().
      for (int t = 0; t < frames; t++) {
         for (int ch = 0; ch < channels; ch++) {
            for (int z = 0; z < slices; z++) {
               for (int p = 0; p < positions; p++) {
                  assertTrue("missing expected coords " + describe(t, ch, z, p),
                        hasImageAt(t, ch, z, p));
               }
            }
         }
      }
   }

   private boolean hasImageAt(int t, int ch, int z, int p) {
      for (Coords c : store_.getUnorderedImageCoords()) {
         if (idx(c, Coords.T) == t && idx(c, Coords.CHANNEL) == ch
               && idx(c, Coords.Z) == z && idx(c, Coords.P) == p) {
            return true;
         }
      }
      return false;
   }

   private static int idx(Coords c, String axis) {
      return c.hasAxis(axis) ? c.getIndex(axis) : 0;
   }

   private static String describe(int t, int ch, int z, int p) {
      return "t=" + t + " ch=" + ch + " z=" + z + " p=" + p;
   }

   /** Decodes the SequenceTester InfoPacket embedded in every image. */
   public List<InfoPacket> infoPackets() throws IOException {
      List<InfoPacket> packets = new ArrayList<>();
      for (Coords c : store_.getUnorderedImageCoords()) {
         Image img = store_.getImage(c);
         packets.add(TestImageDecoder.decode((byte[]) img.getRawPixels()));
      }
      return packets;
   }

   /** The InfoPacket for one specific coordinate. */
   public InfoPacket infoPacketAt(Coords c) throws IOException {
      Image img = store_.getImage(c);
      assertNotNull("no image at " + c, img);
      return TestImageDecoder.decode((byte[]) img.getRawPixels());
   }

   /**
    * Reads the value of a device setting from a packet's {@code currentState}
    * (the full device state captured at the moment the image was produced).
    *
    * @return the value as a String, or null if the device/key is not present
    */
   public static String currentStateString(InfoPacket p, String device,
                                            String key) throws IOException {
      for (TestImageDecoder.SettingState s : p.currentState) {
         if (s.key.device.equals(device) && s.key.key.equals(key)) {
            // SettingValue carries a type tag; render whatever it holds.
            if ("string".equals(s.value.type)) {
               return s.value.asString();
            } else if ("int".equals(s.value.type)) {
               return Long.toString(s.value.asInteger());
            } else if ("float".equals(s.value.type)) {
               return Double.toString(s.value.asDouble());
            }
            return String.valueOf(s.value.value);
         }
      }
      return null;
   }
}
