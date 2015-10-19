///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package imagedisplay;

import com.sun.java.swing.plaf.windows.WindowsScrollBarUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JScrollBar;

/**
 *
 * @author Henry
 */
public class ColorableScrollbarUI extends WindowsScrollBarUI {

   private static final Color DARK_GREEN = new Color(0,70,0);
   
   private int displayedSliceIndex_, minSliceIndex_, maxSliceIndex_;
   
   public void setHighlightedIndices(int currentIndex, int min, int max) {
      displayedSliceIndex_ = currentIndex;
      minSliceIndex_ = min;
      maxSliceIndex_ = max;
   }
   
   @Override
   protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
      super.paintThumb(g, c, thumbBounds);
      if (((JScrollBar) c).getValue() == displayedSliceIndex_) { 
         g.setColor(DARK_GREEN);
         g.drawRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
      }
   }

   @Override
   protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
      super.paintTrack(g, c, trackBounds);
      int numPositions = ((JScrollBar) c).getMaximum() - ((JScrollBar) c).getMinimum();
      //show range of z scrollbar inlight green
      g.setColor(new Color(180,220,180));
        
      int rangeStart = (int) ((minSliceIndex_- ((JScrollBar) c).getMinimum()) / (double) numPositions * trackBounds.width) + trackBounds.x;
      int rangeWidth = (int) ((maxSliceIndex_ - minSliceIndex_ + 1) / (double) numPositions * trackBounds.width);
      g.fillRect(rangeStart, trackBounds.y, rangeWidth, trackBounds.height);

      
      //show the position in dark green
      g.setColor(DARK_GREEN);
      int start = (int) ((displayedSliceIndex_- ((JScrollBar) c).getMinimum()) / (double) numPositions * trackBounds.width) + trackBounds.x;
      int width = (int) (1 / (double) numPositions * trackBounds.width);
      g.fillRect(start, trackBounds.y, width, trackBounds.height);

   }
}
