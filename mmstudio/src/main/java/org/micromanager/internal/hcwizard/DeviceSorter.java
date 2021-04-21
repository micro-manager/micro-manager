package org.micromanager.internal.hcwizard;

import java.util.Comparator;

/** @author nico */
public class DeviceSorter implements Comparator<Device> {

  @Override
  public int compare(Device a, Device b) {
    return a.getLibrary().compareToIgnoreCase(b.getLibrary());
  }
}
