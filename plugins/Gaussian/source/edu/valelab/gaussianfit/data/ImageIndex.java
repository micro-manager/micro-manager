

package edu.valelab.gaussianfit.data;

/**
 * Holds the frame, slice, channel, and position index
 * Can be used to identify to which image a given spot belongs
 * @author nico
 */
public class ImageIndex {
   private final int frame_;
   private final int slice_;
   private final int channel_;
   private final int position_;
   
   public ImageIndex(int frame, int slice, int channel, int position) {
      frame_ = frame;
      slice_ = slice;
      channel_ = channel;
      position_ = position;
   }
   
   
   @Override
   public boolean equals(Object test) {
      if (! (test instanceof ImageIndex)) {
         return false;
      }
      ImageIndex t = (ImageIndex) test;
      if (t.frame_ == frame_ && t.slice_ == slice_ &&
          t.channel_ == channel_ && t.position_ == position_) {
         return true;
      }
      return false;
   }
   
    
    @Override
    public int hashCode() {
        int hash = frame_ + 256 * slice_ + 256*256 * channel_ + 
                256*256*256 * position_;
        return hash;
    }
}
