package de.embl.rieslab.emu.ui.uiproperties;

import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;

/**
 * Class used to pair a UIproperty with a MMProperty.
 *
 * @author Joran Deschamps
 */
@SuppressWarnings("rawtypes")
public class PropertyPair {
  public static boolean pair(UIProperty ui, MMProperty mm)
      throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
    if (ui == null) {
      throw new NullPointerException("Cannot pair a null UIProperty.");
    }

    if (mm == null) {
      throw new NullPointerException("Cannot pair a null MMProperty.");
    }

    if (ui.assignProperty(mm)) {
      mm.addListener(ui);
      return true;
    }
    return false;
  }
}
