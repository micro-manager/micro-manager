package imagedisplay;

// This class provides information when the mouse moves over an image.
public class MouseIntensityEvent {
   public int x_;
   public int y_;
   public int[] intensities_;
   public MouseIntensityEvent(int x, int y, int[] intensities) {
      x_ = x;
      y_ = y;
      intensities_ = intensities;
   }
}
