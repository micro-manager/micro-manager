/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import mmcorej.TaggedImage;

/**
 *
 * @author arthur
 */
public abstract class TaggedImageAnalyzer extends TaggedImageProcessor {

   @Override
   protected void process() {
      final TaggedImage taggedImage = poll();
      produce(taggedImage);
      analyze(taggedImage);
   }

   /*
    * Override this method to analyze images as they arrive.
    * 
    */
   protected abstract void analyze(TaggedImage taggedImage);

}
