/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import java.util.List;
import org.micromanager.acquisition.TaggedImageQueue;

/**
 *
 * @author arthur
 */
public class ProcessorStack {
   private final List<TaggedImageProcessor> processors_;
   private final TaggedImageQueue input_;
   private final TaggedImageQueue output_;
   public ProcessorStack(TaggedImageQueue input,
           List<TaggedImageProcessor> processors) {
      processors_ = processors;
      input_ = input;

      TaggedImageQueue left = input_;
      TaggedImageQueue right = left;
      if (processors != null) {
         for (TaggedImageProcessor processor:processors) {
            right = new TaggedImageQueue();
            processor.setInput(left);
            processor.setOutput(right);
            left = right;
         }
      }
      output_ = right;
   }

   public TaggedImageQueue getOutputChannel() {
      return output_;
   }
   

}
