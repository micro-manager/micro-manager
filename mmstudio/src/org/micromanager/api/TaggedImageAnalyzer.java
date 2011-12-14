/**
 * Example implementation of DataProcessor
 */

package org.micromanager.api;

import mmcorej.TaggedImage;

/**
 *
 * @author arthur
 */
public abstract class TaggedImageAnalyzer extends DataProcessor<TaggedImage> {

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
