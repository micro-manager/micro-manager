package de.embl.rieslab.emu.configuration.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.data.GlobalConfigurationWrapper;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;


/**
 * Reads/Writes a {@link GlobalConfigurationWrapper} from/to a file using jackson ObjectMapper.
 *
 * @author Joran Deschamps
 */
public class ConfigurationIO {

   /**
    * Reads a {@link GlobalConfigurationWrapper} object from the file {@code fileToReadFrom}.
    * It then instantiates and returns a @link GlobalConfiguration}.
    *
    * @param fileToReadFrom File to read the GlobalConfiguration from.
    * @return GlobalConfiguration from the file. null if no GlobalConfiguration could be read.
    * @see GlobalConfiguration
    */
   public static GlobalConfiguration read(File fileToReadFrom) {

      Gson gson = new GsonBuilder().serializeNulls().create();

      try {
         String json = new String(Files.readAllBytes(fileToReadFrom.toPath()));
         GlobalConfigurationWrapper data = gson.fromJson(json, GlobalConfigurationWrapper.class);
         return new GlobalConfiguration(data);
      } catch (IOException e) {
         e.printStackTrace();
      }

      return null;
   }

   /**
    * Writes a {@link GlobalConfigurationWrapper} object to the file {@code fileToWriteTo}.
    *
    * @param fileToWriteTo File in which to save the {@code configuration}.
    * @param configuration GlobalConfiguration to be saved.
    * @return True if the save was successful, false otherwise.
    */
   public static boolean write(File fileToWriteTo, GlobalConfiguration configuration) {

      Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
      String json = gson.toJson(configuration.getGlobalConfigurationWrapper());

      BufferedWriter writer;
      try {
         writer = new BufferedWriter(new FileWriter(fileToWriteTo));
         writer.write(json);
         writer.close();
         return true;
      } catch (IOException e) {
         e.printStackTrace();
      }

      return false;
   }

}
