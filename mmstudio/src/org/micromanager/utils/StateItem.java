/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

/**
 *
 * @author arthur
 */
/**
 * Property descriptor, representing MMCore data
 */
public class StateItem extends PropertyItem {

    public String group;
    public String config;
    public String singlePropAllowed[];
    public String descr;
    public boolean singleProp = false;
    public boolean hasLimits = false;
}
