import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class SanityTest {
   @Test
   public void testClassLoads() {
      assertNotNull(org.micromanager.internal.AcquisitionEngine2010J.class);
   }
}
