package org.micromanager.internal.utils;

import java.io.File;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the path sanitizing that protects the file choosers from unusable
 * remembered paths (issue #2232).
 */
public class FileDialogsTest {

   // Deliberately not JavaUtils.isWindows(), so that this test does not drag
   // in the look and feel classes that JavaUtils refers to.
   private static boolean isWindows() {
      return System.getProperty("os.name").toLowerCase().contains("win");
   }

   @Test
   public void testNullAndEmptyAreNotUsable() {
      Assert.assertFalse(FileDialogs.isUsablePath(null));
      Assert.assertFalse(FileDialogs.isUsablePath(""));
      Assert.assertFalse(FileDialogs.isUsablePath("   "));
   }

   @Test
   public void testOrdinaryPathIsUsable() {
      Assert.assertTrue(FileDialogs.isUsablePath(System.getProperty("user.home")));
   }

   /**
    * On Windows a trailing space makes the path unusable, even though the File
    * constructor accepts it.  This is the case reported in issue #2232.  On
    * other platforms a trailing space is a legal file name.
    */
   @Test
   public void testTrailingSpaceRejectedOnWindows() {
      if (!isWindows()) {
         return;
      }
      Assert.assertFalse(FileDialogs.isUsablePath(System.getProperty("user.home") + " "));
   }

   @Test
   public void testExistingPathIsReturnedUnchanged() {
      String home = System.getProperty("user.home");
      File result = FileDialogs.safeStartFile(home);
      Assert.assertNotNull(result);
      Assert.assertEquals(new File(home), result);
   }

   @Test
   public void testMissingPathWalksUpToExistingAncestor() {
      File home = new File(System.getProperty("user.home"));
      File missing = new File(home, "no-such-dir-2232/nor-this-one/nor-this");
      File result = FileDialogs.safeStartFile(missing.getPath());
      Assert.assertNotNull(result);
      Assert.assertTrue("expected an existing directory, got " + result, result.exists());
      Assert.assertEquals(home, result);
   }

   @Test
   public void testNullFallsBackToSomethingUsable() {
      File result = FileDialogs.safeStartFile(null);
      Assert.assertNotNull(result);
      Assert.assertTrue(result.exists());
   }

   /**
    * An unusable path must never leave the caller holding something that will
    * blow up later inside the file chooser.
    */
   @Test
   public void testUnusablePathYieldsUsableResult() {
      File result = FileDialogs.safeStartFile(System.getProperty("user.home") + " ");
      Assert.assertNotNull(result);
      Assert.assertTrue(FileDialogs.isUsablePath(result.getPath()));
   }
}
