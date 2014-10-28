package org.micromanager.testing;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.Assert;
import org.msgpack.MessagePack;
import org.msgpack.annotation.Message;
import org.msgpack.type.Value;
import org.msgpack.unpacker.Converter;
import static org.msgpack.template.Templates.*;


/**
 * Decode the MessagePack data contained in a SequenceTester-produced image.
 *
 * No effort is made for nice API design here, as this is solely for testing.
 * The wire format (MessagePack schema) may change in backward-incompatible
 * ways as tests evlove.
 *
 * Note that order of fields in static nested classes is important, since
 * MessagePack deserializes these objects from an array, not a map.
 */
@org.junit.Ignore
public class TestImageDecoder {
   @Message
   public static class SettingValue {
      public String type;
      public Value value;

      public long asInteger() throws IOException {
         Assert.assertEquals("int", type);
         return new Converter(value).read(TLong);
      }

      public double asDouble() throws IOException {
         Assert.assertEquals("float", type);
         return new Converter(value).read(TDouble);
      }

      public String asString() throws IOException {
         Assert.assertEquals("string", type);
         return new Converter(value).read(TString);
      }

      public void asOneShot() throws IOException {
         Assert.assertEquals("one_shot", type);
      }
   }

   @Message
   public static class SettingKey {
      public String device;
      public String key;
   }

   @Message
   public static class SettingState {
      public SettingKey key;
      public SettingValue value;
   }

   @Message
   public static class SettingEvent {
      public SettingKey key;
      public SettingValue value;
      public long count;
   }

   @Message
   public static class CameraInfo {
      public String name;
      public long serialImageNr;
      public boolean isSequence;
      public long cumulativeImageNr;
      public long frameNr;
   }

   @Message
   public static class InfoPacket {
      public long hubGlobalPacketNr;
      public CameraInfo camera;
      public long startCounter;
      public long currentCounter;
      public ArrayList<SettingState> startState;
      public ArrayList<SettingState> currentState;
      public ArrayList<SettingEvent> history;

      public boolean hasBeenSetSincePreviousPacket(String device,
            String settingName)
      {
         for (SettingEvent event : history) {
            if (event.key.device.equals(device) &&
                  event.key.key.equals(settingName)) {
               return true;
            }
         }
         return false;
      }
   }

   public static InfoPacket decode(byte[] image) throws IOException {
      MessagePack msgpack = new MessagePack();
      try {
         return msgpack.read(image, InfoPacket.class);
      }
      catch (org.msgpack.MessageTypeException e) {
         // This might be an indication that the image was all zeros, which is
         // the case if the data didn't fit. Drop a hint in that case.
         for (byte b : image) {
            if (b != 0) {
               throw e;
            }
         }
         Assert.fail("SeqTester image is all-zero; make sure image size is "
               + "sufficient to fit MessagePack data");
         throw e; // Placate compiler
      }
   }

   public static void dumpJSON(byte[] image, java.io.OutputStream out)
      throws IOException
   {
      MessagePack msgpack = new MessagePack();
      Value v = msgpack.createBufferUnpacker(image).readValue();
      new org.msgpack.util.json.JSON().write(out, v);
   }
}
