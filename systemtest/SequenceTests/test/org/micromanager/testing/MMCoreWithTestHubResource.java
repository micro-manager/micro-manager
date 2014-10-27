package org.micromanager.testing;


@org.junit.Ignore
public class MMCoreWithTestHubResource extends MMCoreResource {
   @Override
   protected void before() throws Exception {
      super.before();

      mmc_.loadDevice("THub", "SequenceTester", "THub");
      mmc_.initializeDevice("THub");
   }

   public void prepareTestDevices(String... devices) throws Exception {
      for (String device : devices) {
         mmc_.loadDevice(device, "SequenceTester", device);
         mmc_.setParentLabel(device, "THub");
         if (device.startsWith("TCamera")) {
            mmc_.setProperty(device, "ImageMode", "MachineReadable");
            mmc_.setProperty(device, "ImageWidth", 128);
            mmc_.setProperty(device, "ImageHeight", 128);
         }
         mmc_.initializeDevice(device);
      }
   }
}
