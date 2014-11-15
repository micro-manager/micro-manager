package org.micromanager.api;

/**
 * Implement this interface to create Micro-Manager DataProcessor plugins. 
 * Compiled jars may be dropped into Micro-Manager's mmplugin directory, and 
 * if correctly implemented, will appear in the Micro-Manager plugins menu.
 * You should look at the MMBasePlugin.java file as well for other functions
 * and member fields that should be implemented.
 */
public interface MMProcessorPlugin extends MMBasePlugin {
   /** 
    * Return the .class field of the DataProcessor child class that will 
    * be processing images. For example, if your plugin's processor class 
    * is named "FooProcessor", then this function should have the body:
    * 
    * return FooProcessor.class;
    * 
    * This method is required to be static and thus cannot be actually 
    * declared in this interface; you must implement it "manually" so to speak.
    */
// public static Class<? extends <DataProcessor<TaggedImage>> getProcessorClass();
}
