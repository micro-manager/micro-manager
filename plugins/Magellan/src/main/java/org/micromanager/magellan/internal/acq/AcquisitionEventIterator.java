package org.micromanager.magellan.internal.acq;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Takes a list of iterators representing levels of a tree, and provides a lazy iterator over the leaves
 * of the tree
 * @author henrypinkard
 */
public class AcquisitionEventIterator implements Iterator<AcquisitionEvent> {

   private List<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> functionList_;
   private IteratorTreeNode currentLeaf_;
   private boolean eventsExhausted_ = false;

   public AcquisitionEventIterator(AcquisitionEvent root, List<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> functionList) {
      functionList_ = functionList;
      currentLeaf_ = new IteratorTreeNode(null, Stream.of(root).iterator(), functionList.get(0));
      descendNewBranch();
   }

   @Override
   public boolean hasNext() {
      return !eventsExhausted_;
   }

   @Override
   public AcquisitionEvent next() {
      AcquisitionEvent next = currentLeaf_.iterator.next();
      //Move to new branch or determine that all branches are exhausted
      //May need to try ascent and descent multiple times in case a terminal iterator produces no events?
      while (!currentLeaf_.iterator.hasNext()) {
         //ascend to node where next valid branch can be found
         while (!currentLeaf_.iterator.hasNext()) {
            currentLeaf_ = currentLeaf_.parent;
            if (currentLeaf_ == null) {
               eventsExhausted_ = true;
               return next;
            }
         }
         descendNewBranch();
      }
      
      return next;
   }

   private void descendNewBranch() {
      while (currentLeaf_.branchIteratorFunction_ != null) { //the terminal node will not have a function for producing new branches
         AcquisitionEvent newBranch = currentLeaf_.iterator.next();
         Iterator<AcquisitionEvent> childIterator = currentLeaf_.branchIteratorFunction_.apply(newBranch);
         int depth = functionList_.indexOf(currentLeaf_.branchIteratorFunction_);
         currentLeaf_ = new IteratorTreeNode(currentLeaf_, childIterator,
                 depth == functionList_.size() - 1 ? null : functionList_.get(depth + 1));
      }
   }

   class IteratorTreeNode {
      //ree where each node is an iterator
      //Only stores links going up the tree, if you lose references to lower down they tree they get GCed

      IteratorTreeNode parent;
      Iterator<AcquisitionEvent> iterator;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> branchIteratorFunction_;

      public IteratorTreeNode(IteratorTreeNode p, Iterator<AcquisitionEvent> s, Function<AcquisitionEvent, Iterator<AcquisitionEvent>> f) {
         parent = p;
         iterator = s;
         branchIteratorFunction_ = f;
      }

   }

}
