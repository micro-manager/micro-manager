import mmcorej.CMMCore;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.ImagePropertyKeys;
import org.micromanager.metadata.MMAcqDataException;

/* Test_AcquisitionDataInMem.java
 * Created on May 06, 2008
 * Copyright 100XImaging Inc, 2008
 * 
 * MicroManager acquisition data test and sample code
 */

/**
 * Example program to demonstrate creating Micro-Manager acquisition data object.
 * 
 * Requires MMJ_.jar and ij.jar on the classpath.
 */
public class Test_AcquisitionDataInMem {

   public static void main(String[] args) {
      
      final String acqDir = "c:/AcquisitionData/test";
      final String acqName = "in-memory_test";
      final String configFile = "MMConfig_demo_stream_proc.cfg";
      
      // we want to create z stack in a single channel so we'll
      // define parameters accordingly
      final int frames = 1;      // time dimension
      final int channels = 1;    // wavelength dimension
      final int slices = 10;     // focus (Z) dimension
      
      double expMs = 100.0;
      
      // instantiate 5d image object
      AcquisitionData ad = new AcquisitionData();
      
      // instantiate Micro-Manager core
      CMMCore core = new CMMCore();
     
      try {
         // initialize core with the "demo" configuration
         core.loadSystemConfiguration(configFile);
         
         // initialize acquisition data as in-memory
         ad.createNew();
         
         // obtain basic image parameters
         // we need to snap at least once so that micro-manager figures out image physical dimensions
         core.snapImage();
         // we don't need this image but we'll get it anyway, since some cameras get confused if 
         // snapped image does not eventually get retrieved
         Object img = core.getImage();
         
         // get basic acquisition parameters
         int height = (int)core.getImageHeight();
         int width = (int)core.getImageWidth();
         int depth = (int)core.getBytesPerPixel();
         
         // size the acquisition data
         ad.setImagePhysicalDimensions(width, height, depth);
         ad.setDimensions(frames, channels, slices);
         
         // set the meaningful name to the channel
         ad.setChannelName(0, "Z-stack");
         
         // start acquiring
         double startZ = 0.0;
         double stepZ = 1.0;
         String nameZ = core.getFocusDevice();
         core.setExposure(expMs);
         ad.setComment("Test z-stack");
         
         for (int i=0; i<slices; i++) {
            double zPos = startZ + i * stepZ;
            core.setPosition(nameZ, zPos);
            core.waitForDevice(nameZ);
            core.snapImage();
            img = core.getImage();
            ad.insertImage(img, 0, 0, i);
            ad.setImageValue(0, 0, i, ImagePropertyKeys.Z_UM, zPos);
         }
         
         // we're done so let's check out one image from the stack
         // and verify we are getting what we expect
         Object sliceImg = ad.getPixels(0, 0, 4);
         int length = 0;
         if (depth == 1) {
            byte[] byteImg = (byte[])sliceImg;
            length = byteImg.length;
         } else if (depth == 2) {
            short[] shortImg = (short[])sliceImg;
            length = shortImg.length;
         }
         System.out.println("Image contains " + length + " pixels.");
         
         // save data to file
         ad.save(acqName, acqDir, true, null);
         
      } catch (MMAcqDataException e) {
         e.printStackTrace();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}
