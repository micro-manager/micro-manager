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

   protected abstract void analyze(TaggedImage taggedImage);

}
