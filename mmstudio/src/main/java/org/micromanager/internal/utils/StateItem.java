package org.micromanager.internal.utils;

/**
 * Property descriptor, representing MMCore data.
 *
 * @author arthur
 */
public final class StateItem extends PropertyItem {

   public String group;
   public String config;
   public String[] singlePropAllowed;
   public String descr;
   public boolean singleProp = false;
   public boolean hasLimits = false;
}
