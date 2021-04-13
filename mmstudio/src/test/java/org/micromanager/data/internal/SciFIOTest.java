
package org.micromanager.data.internal;


import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.data.Coords;

/**
 * tests opening data through the SciFIO library
 * @author nico
 */
public class SciFIOTest {
      final int nrZs_ = 6;
      final int nrChannels_ = 2; 
      final int nrTimePoints_ = 8;
      final int XSize_ = 250;
      final int YSize_ = 350;
   
   @Test
   public void test8bit() {
      SciFIODataProvider sdp = new SciFIODataProvider(null, 
              "8bit-unsigned&pixelType=uint8&lengths=" +
                      XSize_ + "," + YSize_ + "," + nrZs_ + "," + nrChannels_ + 
                      "," + nrTimePoints_ + "&axes=X,Y,Z,Channel,Time.fake");
      try {
         Assert.assertEquals("Image X Size is wrong", XSize_, sdp.getAnyImage().getWidth());
         Assert.assertEquals("Image Y Size is wrong", YSize_, sdp.getAnyImage().getHeight());
         Assert.assertEquals("Bytes Per Pixel is wrong", 1, sdp.getAnyImage().getBytesPerPixel());
         Assert.assertEquals("Nr Zs is wrong" , nrZs_, sdp.getNextIndex(Coords.Z));
         Assert.assertEquals("nrChannels is wrong", nrChannels_, sdp.getNextIndex(Coords.C));
         Assert.assertEquals("nrTime Points is wrong", nrTimePoints_, sdp.getNextIndex(Coords.T));
      } catch (IOException ex) {
         Assert.fail("IOException while testing SciFIODataProvider");
      }
   }
   
   @Test
   public void test16bit() {
      SciFIODataProvider sdp = new SciFIODataProvider(null, 
              "16bit-unsigned&pixelType=uint16&lengths=" +
                      XSize_ + "," + YSize_ + "," + nrZs_ + "," + nrChannels_ + 
                      "," + nrTimePoints_ + "&axes=X,Y,Z,Channel,Time.fake");
      try {
         Assert.assertEquals("Image X Size is wrong", XSize_, sdp.getAnyImage().getWidth());
         Assert.assertEquals("Image Y Size is wrong", YSize_, sdp.getAnyImage().getHeight());
         Assert.assertEquals("Bytes Per Pixel is wrong", 2, sdp.getAnyImage().getBytesPerPixel());
         Assert.assertEquals("Nr Zs is wrong" , nrZs_, sdp.getNextIndex(Coords.Z));
         Assert.assertEquals("nrChannels is wrong", nrChannels_, sdp.getNextIndex(Coords.C));
         Assert.assertEquals("nrTime Points is wrong", nrTimePoints_, sdp.getNextIndex(Coords.T));
      } catch (IOException ex) {
         Assert.fail("IOException while testing SciFIODataProvider");
      }
   }
   
}
