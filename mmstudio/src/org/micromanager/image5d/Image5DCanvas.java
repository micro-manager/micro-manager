package org.micromanager.image5d;

import ij.gui.ImageCanvas;

/** Canvas compatible with Image5DLayout.
 * @author Joachim Walter
 */
public class Image5DCanvas extends ImageCanvas {
   private static final long serialVersionUID = 9003195219805747820L;

   /**
     * @param imp
     */
    public Image5DCanvas(Image5D imp) {
        super(imp);
    }


//    // No change so far
//    public void zoomIn(int x, int y) {
//		if (getMagnification()>=32)
//			return;
//		double newMag = getHigherZoomLevel(getMagnification());
//		int newWidth = (int)(imageWidth*newMag);
//		int newHeight = (int)(imageHeight*newMag);
//		if (canEnlargeI5D(newWidth, newHeight)) {
//			setDrawingSize(newWidth, newHeight);
//			imp.getWindow().pack();
//		} else {
//		    Dimension dim = getPreferredSize();
//			int w = (int)Math.round(dim.width/newMag);
//			if (w*newMag<dim.width) w++;
//			int h = (int)Math.round(dim.height/newMag);
//			if (h*newMag<dim.height) h++;
//			x = offScreenX(x);
//			y = offScreenY(y);
//			Rectangle r = new Rectangle(x-w/2, y-h/2, w, h);
//			if (r.x<0) r.x = 0;
//			if (r.y<0) r.y = 0;
//			if (r.x+w>imageWidth) r.x = imageWidth-w;
//			if (r.y+h>imageHeight) r.y = imageHeight-h;
//			srcRect = r;
//		}
//		setMagnification(newMag);
//		repaint();
//    }
//    
//    protected boolean canEnlargeI5D(int newWidth, int newHeight) {
//		if ((getModifiers()&Event.SHIFT_MASK)!=0 || IJ.shiftKeyDown())
//			return false;
//		Rectangle r1 = imp.getWindow().getBounds();
//		r1.width = newWidth + 20;
//		r1.height = newHeight + 50;
//		if (imp.getStackSize()>1)
//			r1.height += 20;
//		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
//		boolean fitsOnScreen = r1.x+r1.width<screen.width && r1.y+r1.height+30<screen.height;
//		return fitsOnScreen;
//	}
    
	/** Enlarge the canvas if the user enlarges the window. 
	 * 	Called from Image5DLayout.layoutContainer().*/
	void resizeCanvasI5D(int width, int height) {
		if (srcRect.width<imageWidth || srcRect.height<imageHeight) {
		    double magnification = getMagnification();
			if (width>imageWidth*magnification)
				width = (int)(imageWidth*magnification);
			if (height>imageHeight*magnification)
				height = (int)(imageHeight*magnification);
			setDrawingSize(width, height);
//			Dimension dim = getPreferredSize();
//			srcRect.width = (int)(dim.width/magnification);
//			srcRect.height = (int)(dim.height/magnification);
			srcRect.width = (int)(width/magnification);
			srcRect.height = (int)(height/magnification);
			if ((srcRect.x+srcRect.width)>imageWidth)
				srcRect.x = imageWidth-srcRect.width;
			if ((srcRect.y+srcRect.height)>imageHeight)
				srcRect.y = imageHeight-srcRect.height;
			repaint();
		}
	}
	
	public void zoomOut(int x, int y) {
	    
	    super.zoomOut(x, y);

	}
    
}
