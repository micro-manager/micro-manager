package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty.MMPropertyType;
import org.junit.Test;


public class MMPropertyTypeTest {

   @Test
   public void testMMPropertyType() {
      MMPropertyType inttype = MMProperty.MMPropertyType.INTEGER;
      assertTrue(MMProperty.MMPropertyType.INTEGER == inttype);
      assertEquals(MMProperty.MMPropertyType.INTEGER, inttype);
      assertNotEquals(MMProperty.MMPropertyType.FLOAT, inttype);
   }
}
