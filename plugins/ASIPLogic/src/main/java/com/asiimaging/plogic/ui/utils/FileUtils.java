package com.asiimaging.plogic.ui.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

   /**
    * A file reading helper.
    *
    * @param filePath the file path
    * @param fileExt the file extension
    * @return the contents of the file
    * @throws IOException could not read the file
    */
   public static String readFile(final String filePath, final String fileExt) throws IOException {
      return new String(Files.readAllBytes(Paths.get(filePath + "." + fileExt)));
   }

   /**
    * A file saving helper.
    *
    * @param data the data to save
    * @param filePath the file path
    * @param fileExt the file extension
    * @throws IOException could not save the file
    */
   public static void saveFile(final String data, final String filePath,
                               final String fileExt) throws IOException {
      Files.write(Paths.get(filePath + "." + fileExt), data.getBytes(StandardCharsets.UTF_8));
   }
}
