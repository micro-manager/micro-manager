package org.micromanager.image5d;

import java.awt.*;

import ij.gui.*;

/** Extended ImageLayout: compatible with two scrollbars for z and t below the image
 * and a channelControl panel to the right of the image. 
 * 
 * NOTE: extended to accomodate button panel below slice/frame scrollbars
 *       Micro-Manager project
 *       Nenad Amodaj, Feb 2006
 * */
public class Image5DLayout extends ImageLayout implements LayoutManager2 {

    int hgap;
    int vgap;
	Image5DCanvas ic5d;
	
	public static final String MAIN_CANVAS="main", SLICE_SELECTOR="slice", FRAME_SELECTOR="frame", 
	CHANNEL_SELECTOR="channel", PANEL_SELECTOR="panel";
	
	private Component main, slice, frame, channel, panel;

    /** Creates a new ImageLayout with center alignment and 5 pixel horizontal and vertical gaps. */
    public Image5DLayout(Image5DCanvas ic5d) {
    	super(ic5d);
    	this.ic5d = ic5d;
		this.hgap = 5;
		this.vgap = 5;
    }


    /** Returns the preferred dimensions for this layout. */
    public Dimension preferredLayoutSize(Container target) {
		Dimension dim = new Dimension(0,0);
    	Insets insets = target.getInsets();

		dim.width = getHorizontalMainSize();	
		if (channel != null) {
			dim.width += hgap + channel.getPreferredSize().width;
		}		
		dim.width += insets.left + insets.right + hgap*2;
		
		dim.height = getVerticalCoreSize();		
		dim.height += insets.top + insets.bottom + vgap*2;
		
		return dim;
    }

    /** Returns the minimum dimensions for this layout. */
    public Dimension minimumLayoutSize(Container target) {
		return preferredLayoutSize(target);
    }

    /** Lays out the container. Calls super.layoutContainer only to 
     * have call to ImageCanvas.resizeCanvas(), which has "default" access.
     */
    public void layoutContainer(Container target) {
    	// Call super to call ImageCanvas.resizeCanvas().
//    	super.layoutContainer(target);
    	
    	// Do layout completely anew.
    	// Remember: getInsets() is overridden in Image5DWindow.
    	Dimension d = target.getSize();
    	Insets insets = target.getInsets();
		Dimension psize = preferredLayoutSize(target);
		
		int offsX = insets.left + hgap + (d.width - psize.width)/2;
		int offsY = insets.top + vgap + (d.height - psize.height)/2;
		int x = 0;
		int y = 0;

		// Place main canvas in center of area spanned by the controls.
		Dimension dimMain = main.getPreferredSize();
		ic5d.resizeCanvasI5D(dimMain.width, dimMain.height);
		int mainAreaWidth = getHorizontalMainSize();
		int mainAreaHeight = getVerticalMainSize();
		main.setSize(dimMain);
		main.setLocation(offsX + (mainAreaWidth-dimMain.width)/2, 
					offsY + (mainAreaHeight-dimMain.height)/2);
		
		x = offsX + mainAreaWidth;
		y = offsY + mainAreaHeight;
		
		if (slice != null) {
			slice.setSize(mainAreaWidth, slice.getPreferredSize().height);
			y += vgap;
			slice.setLocation(offsX, y);
			y += slice.getPreferredSize().height;
		}		
		if (frame != null) {
			frame.setSize(mainAreaWidth, frame.getPreferredSize().height);
			y += vgap;
			frame.setLocation(offsX, y);
			y += frame.getPreferredSize().height;
		}
		
      if (panel != null) {
         panel.setSize(mainAreaWidth, panel.getPreferredSize().height);
         y += vgap;
         panel.setLocation(offsX, y);
         y += panel.getPreferredSize().height;
      }
      
		if (channel != null) {
			channel.setSize(channel.getPreferredSize().width, getVerticalCoreSize());
			x += hgap;
			channel.setLocation(x, offsY);
			x += channel.getPreferredSize().width;
		}      
    }
   
    public void addLayoutComponent(Component comp, Object constraints) {
    	synchronized (comp.getTreeLock()) {
    		if ((constraints != null) && (constraints instanceof String)) {
    			addLayoutComponent((String)constraints, comp);
    		} else {
    			throw new IllegalArgumentException("cannot add to layout: constraint must be a string");
    		}
        }
    }
    
    public void addLayoutComponent(String name, Component comp) {
		if (MAIN_CANVAS.equals(name)) {
			main = comp;
  	  	} else if (CHANNEL_SELECTOR.equals(name)) {
  	  	    channel = comp;
  	  	} else if (SLICE_SELECTOR.equals(name)) {
  	  	    slice = comp;
  	  	} else if (FRAME_SELECTOR.equals(name)) {
  	  	    frame = comp;
      } else if (PANEL_SELECTOR.equals(name)) {
          panel = comp;
  	  	} else {
  	  	    throw new IllegalArgumentException("cannot add to layout: unknown constraint: " + name);
  	  	}  	
    }
    
    public void removeLayoutComponent(Component comp) {
        synchronized (comp.getTreeLock()) {
		  	if (comp == main) {
		  	    main = null;
		  	} else if (comp == channel) {
		  	    channel = null;
		  	} else if (comp == slice) {
		  	    slice = null;
		  	} else if (comp == frame) {
		  		frame = null;
		  	} else if (comp == panel) {
		  	   panel = null;
         }
        }
    }


	/* (non-Javadoc)
	 * @see java.awt.LayoutManager2#maximumLayoutSize(java.awt.Container)
	 */
	public Dimension maximumLayoutSize(Container target) {
		return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}


	/* (non-Javadoc)
	 * @see java.awt.LayoutManager2#getLayoutAlignmentX(java.awt.Container)
	 */
	public float getLayoutAlignmentX(Container target) {
		return 0.5f;
	}


	/* (non-Javadoc)
	 * @see java.awt.LayoutManager2#getLayoutAlignmentY(java.awt.Container)
	 */
	public float getLayoutAlignmentY(Container target) {
		return 0.5f;
	}


	/* (non-Javadoc)
	 * @see java.awt.LayoutManager2#invalidateLayout(java.awt.Container)
	 */
	public void invalidateLayout(Container target) {
		
	}
	
	int getHorizontalMainSize() {
		// Take width of ImageCanvas.
		// Give enough room for horizontal controls.
		int width = 0;
		width = main.getPreferredSize().width;
		if (slice != null) {
			width = Math.max(width, slice.getMinimumSize().width);
		}
		if (frame != null) {
			width = Math.max(width, frame.getMinimumSize().width);
		}	
      if (panel != null) {
         width = Math.max(width, panel.getMinimumSize().width);
      }  
		return width;
	}
	
	int getVerticalMainSize() {
		// Take height of ImageCanvas
		// Add horizontal controls.
		// Check, whether enough room for vertical control(s) and correct, if necessary.
		// Subtract height of horizontal controls.
		int height = 0;
		int controlsHeight = 0;
		height = main.getPreferredSize().height;
		if (slice != null) {
			height 			+= vgap + slice.getPreferredSize().height;
			controlsHeight 	+= vgap + slice.getPreferredSize().height;
		}
		if (frame != null) {
			height 			+= vgap + frame.getPreferredSize().height;
			controlsHeight 	+= vgap + frame.getPreferredSize().height;
		}
      
      if (panel != null) {
         height         += vgap + panel.getPreferredSize().height;
         controlsHeight    += vgap + panel.getPreferredSize().height;
      }
		
		if (channel != null) {
			height = Math.max(height, channel.getMinimumSize().height);
		}
		
		height -= controlsHeight;
		
		return height;
	}	
	
	int getVerticalCoreSize() {
		// Take height of ImageCanvas
		// Add horizontal controls.
		// Check, whether enough room for vertical control(s) and correct, if necessary.		
		int height = 0;
		height = main.getPreferredSize().height;
		if (slice != null) {
			height 			+= vgap + slice.getPreferredSize().height;
		}
		if (frame != null) {
			height 			+= vgap + frame.getPreferredSize().height;
		}
      if (panel != null) {
         height         += vgap + panel.getPreferredSize().height;
      }
		
		if (channel != null) {
			height = Math.max(height, channel.getMinimumSize().height);
		}
		
		return height;
	}
}
