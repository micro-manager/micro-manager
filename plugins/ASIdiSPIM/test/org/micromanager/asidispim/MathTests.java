
package org.micromanager.asidispim;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author nico
 */
public class MathTests {

   @Test
   public void checkRotation() {
      final Vector3D yAxis = new Vector3D(0.0, 1.0, 0.0);
      final Rotation camARotation = new Rotation(yAxis, Math.toRadians(45));
      final Rotation camBRotation = new Rotation(yAxis, Math.toRadians(-45));

      final Vector3D mov = new Vector3D(10.0, 0.0, 0.0);

      final Vector3D camAMov = camARotation.applyTo(mov);
      final Vector3D camBMov = camBRotation.applyTo(mov);

      System.out.println("Cam A rotated vector: " + camAMov.getX() + ", "
              + camAMov.getY() + ", " + camAMov.getZ());

      System.out.println("Cam B rotated vector: " + camBMov.getX() + ", "
              + camBMov.getY() + ", " + camBMov.getZ());
      
      Assert.assertEquals(7.071067811, camAMov.getX(), 0.000001);
      Assert.assertEquals(0.0, camAMov.getY(), 0.000001);
      Assert.assertEquals(-7.071067811, camAMov.getZ(), 0.000001);
      Assert.assertEquals(7.071067811, camBMov.getX(), 0.000001);
      Assert.assertEquals(0.0, camBMov.getY(), 0.000001);
      Assert.assertEquals(7.071067811, camBMov.getX(), 0.000001);

   }
   
}
