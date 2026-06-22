package org.micromanager.acqenginetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.testing.TestImageDecoder.InfoPacket;

/**
 * Autofocus acquisition tests, run through both engines.
 *
 * <p>A {@link TestAutofocusPlugin} is injected as the active autofocus method;
 * its fullFocus() drives the SequenceTester TAutofocus device, which records a
 * "FullFocus" one-shot in the image InfoPacket. The plugin also counts its own
 * invocations, so we can assert that autofocus ran on the expected frames and
 * was skipped otherwise.
 */
@RunWith(Parameterized.class)
public class AutofocusTest {
   private static StudioTestFixture fixture_;
   private TestAutofocusPlugin afPlugin_;

   @Parameterized.Parameter
   public boolean useAcqEngJ_;

   @Parameterized.Parameters(name = "acqEngJ={0}")
   public static List<Object[]> engines() {
      return Arrays.asList(new Object[]{false}, new Object[]{true});
   }

   @BeforeClass
   public static void boot() {
      fixture_ = StudioTestFixture.getInstance();
   }

   @Before
   public void setUp() throws Exception {
      fixture_.reset();
      fixture_.useAcqEngJ(useAcqEngJ_);

      // Make TAutofocus the Core's autofocus device and inject our plugin as the
      // active autofocus method.
      MMStudio studio = fixture_.getStudio();
      studio.core().setAutoFocusDevice("TAutofocus");
      afPlugin_ = new TestAutofocusPlugin();
      afPlugin_.setContext(studio);
      studio.getAutofocusManager().setAutofocusMethod(afPlugin_);
      StudioTestFixture.drainEdt();
   }

   @Test
   public void autofocusRunsEveryFrame() throws Exception {
      MMStudio studio = fixture_.getStudio();
      afPlugin_.resetCount();

      final int numFrames = 4;
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(1.0)
            .useAutofocus(true)
            .skipAutofocusCount(0)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(numFrames, 1, 1, 1);

      // Autofocus should run once per frame.
      assertEquals("autofocus should run on every frame",
            numFrames, afPlugin_.getFullFocusCount());

      // And the TAutofocus device should have its FullFocus recorded.
      boolean anyFullFocus = false;
      for (InfoPacket p : result.infoPackets()) {
         if (p.hasBeenSetSincePreviousPacket("TAutofocus", "FullFocus")) {
            anyFullFocus = true;
         }
      }
      assertTrue("TAutofocus FullFocus should appear in the image history",
            anyFullFocus);
   }

   @Test
   public void skipAutofocusCountReducesRuns() throws Exception {
      MMStudio studio = fixture_.getStudio();
      afPlugin_.resetCount();

      // NOTE on engine semantics of skipAutofocusCount:
      //   AcqEngJ skips a frame when (tIndex % skipAutofocusCount != 0), so
      //   skip=N means "autofocus every Nth frame" and skip=1 means EVERY frame.
      //   The legacy engine interprets the value differently. To assert a
      //   reduction that holds on both engines, use skip=2: AcqEngJ then runs on
      //   frames 0 and 2 (2 of 6), and the legacy engine likewise runs less than
      //   every frame.
      final int numFrames = 6;
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(1.0)
            .useAutofocus(true)
            .skipAutofocusCount(2)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(numFrames, 1, 1, 1);

      int runs = afPlugin_.getFullFocusCount();
      assertTrue("autofocus should run at least once but fewer than every frame "
                  + "when skipping (skip=2, frames=" + numFrames + ", was "
                  + runs + ")",
            runs >= 1 && runs < numFrames);
   }
}
