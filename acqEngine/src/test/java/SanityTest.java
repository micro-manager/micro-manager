import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class SanityTest {
   @Test
   public void testClassLoads() {
      assertNotNull(org.micromanager.internal.jacque.Jacque2010.class);
   }
}
