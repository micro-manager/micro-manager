package org.micromanager.internal;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests the parsing contract of the crop presets file.
 *
 * <p>These drive the public {@link CropPreset#loadPresets()} rather than the parser
 * directly, so that the file lookup is covered too.  {@code loadPresets} finds its file
 * relative to {@code user.dir}, which each test points at a temporary directory.
 */
public class CropPresetTest {
   @Rule
   public final TemporaryFolder folder = new TemporaryFolder();

   private String originalUserDir_;

   @Before
   public void setUp() {
      originalUserDir_ = System.getProperty("user.dir");
      System.setProperty("user.dir", folder.getRoot().getAbsolutePath());
   }

   @After
   public void tearDown() {
      if (originalUserDir_ != null) {
         System.setProperty("user.dir", originalUserDir_);
      }
   }

   /** Writes the presets file that loadPresets() will find. */
   private void writePresetsFile(String contents) throws IOException {
      File file = new File(folder.getRoot(), CropPreset.PRESETS_FILE_NAME);
      try (Writer writer = new OutputStreamWriter(
            new FileOutputStream(file), Charset.defaultCharset())) {
         writer.write(contents);
      }
   }

   private static void assertRectangle(CropPreset preset, String name,
         int x, int y, int width, int height) {
      Assert.assertEquals(name, preset.getName());
      Assert.assertEquals(new Rectangle(x, y, width, height), preset.toRectangle());
   }

   @Test
   public void missingFileYieldsNoPresets() {
      Assert.assertTrue(CropPreset.loadPresets().isEmpty());
   }

   @Test
   public void readsWellFormedPresetsInFileOrder() throws IOException {
      writePresetsFile("Center half, 128, 128, 256, 256\n"
            + "Line scan, 0, 240, 512, 32\n");

      List<CropPreset> presets = CropPreset.loadPresets();

      Assert.assertEquals(2, presets.size());
      assertRectangle(presets.get(0), "Center half", 128, 128, 256, 256);
      assertRectangle(presets.get(1), "Line scan", 0, 240, 512, 32);
   }

   @Test
   public void trimsWhitespaceAroundEveryField() throws IOException {
      writePresetsFile("   Spaced out  ,  10 ,  20 , 30 , 40   \n");

      List<CropPreset> presets = CropPreset.loadPresets();

      Assert.assertEquals(1, presets.size());
      assertRectangle(presets.get(0), "Spaced out", 10, 20, 30, 40);
   }

   @Test
   public void ignoresBlankAndCommentLines() throws IOException {
      writePresetsFile("# a leading comment\n"
            + "\n"
            + "   \n"
            + "   # an indented comment\n"
            + "Only one, 1, 2, 3, 4\n"
            + "\n");

      List<CropPreset> presets = CropPreset.loadPresets();

      Assert.assertEquals(1, presets.size());
      assertRectangle(presets.get(0), "Only one", 1, 2, 3, 4);
   }

   @Test
   public void skipsMalformedLinesButKeepsTheRest() throws IOException {
      writePresetsFile("Good one, 0, 0, 64, 64\n"
            + "too few fields\n"
            + "Trailing, 1, 2, 3, 4, 5\n"
            + "Not a number, 0, 0, abc, 100\n"
            + "Zero width, 0, 0, 0, 100\n"
            + "Negative height, 0, 0, 100, -1\n"
            + "Negative origin, -5, 0, 10, 10\n"
            + " , 0, 0, 10, 10\n"
            + "Good two, 8, 8, 16, 16\n");

      List<CropPreset> presets = CropPreset.loadPresets();

      Assert.assertEquals(2, presets.size());
      assertRectangle(presets.get(0), "Good one", 0, 0, 64, 64);
      assertRectangle(presets.get(1), "Good two", 8, 8, 16, 16);
   }

   @Test
   public void acceptsAZeroOrigin() throws IOException {
      writePresetsFile("Corner, 0, 0, 1, 1\n");

      List<CropPreset> presets = CropPreset.loadPresets();

      Assert.assertEquals(1, presets.size());
      assertRectangle(presets.get(0), "Corner", 0, 0, 1, 1);
   }

   @Test
   public void handlesAFileWithoutATrailingNewline() throws IOException {
      writePresetsFile("No newline, 1, 2, 3, 4");

      List<CropPreset> presets = CropPreset.loadPresets();

      Assert.assertEquals(1, presets.size());
      assertRectangle(presets.get(0), "No newline", 1, 2, 3, 4);
   }

   @Test
   public void createsATemplateThatDefinesNoPresets() throws IOException {
      File created = CropPreset.createTemplateFile();

      Assert.assertTrue(created.isFile());
      Assert.assertEquals(CropPreset.PRESETS_FILE_NAME, created.getName());
      // Every example in the template is commented out, so a freshly created file must
      // parse cleanly and contribute nothing to the menu.
      Assert.assertTrue(CropPreset.loadPresets().isEmpty());
   }

   @Test
   public void templateCreationLeavesAnExistingFileAlone() throws IOException {
      writePresetsFile("Precious, 1, 2, 3, 4\n");

      CropPreset.createTemplateFile();

      List<CropPreset> presets = CropPreset.loadPresets();
      Assert.assertEquals(1, presets.size());
      assertRectangle(presets.get(0), "Precious", 1, 2, 3, 4);
   }
}
