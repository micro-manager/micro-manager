package org.micromanager.ndviewer.overlay;

import java.awt.*;

/** Oval region of interest */
public class OvalRoi extends Roi {

	/** Creates an OvalRoi.*/
	public OvalRoi(int x, int y, int width, int height) {
		super(x, y, width, height);
		type = OVAL;
	}

	/** Creates an OvalRoi using double arguments.*/
	public OvalRoi(double x, double y, double width, double height) {
		super(x, y, width, height);
		type = OVAL;
	}


	public void draw(Graphics g) {
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		if (fillColor!=null) color = fillColor;
		g.setColor(color);
		mag = 1;
		int sw = (int)(width*mag);
		int sh = (int)(height*mag);
		int sx1 = (x);
		int sy1 = (y);
		if (subPixelResolution() && bounds!=null) {
			sw = (int)(bounds.width*mag);
			sh = (int)(bounds.height*mag);
			sx1 = (int) (bounds.x);
			sy1 = (int) (bounds.y);
		}
		int sw2 = (int)(0.14645*width*mag);
		int sh2 = (int)(0.14645*height*mag);
		int sx2 = sx1+sw/2;
		int sy2 = sy1+sh/2;
		int sx3 = sx1+sw;
		int sy3 = sy1+sh;
		Graphics2D g2d = (Graphics2D)g;
		if (stroke!=null) 
			g2d.setStroke(stroke);
		if (fillColor!=null) {
			if (!overlay ) {
				g.setColor(Color.cyan);
				g.drawOval(sx1, sy1, sw, sh);
			} else
				g.fillOval(sx1, sy1, sw, sh);
		} else
			g.drawOval(sx1, sy1, sw, sh);
	
		drawPreviousRoi(g);
		
	}


	/** Tests if the specified point is inside the boundary of this OvalRoi.
	* Authors: Barry DeZonia and Michael Schmid
	*/
	public boolean contains(int ox, int oy) {
		double a = width*0.5;
		double b = height*0.5;
		double cx = x + a - 0.5;
		double cy = y + b - 0.5;
		double dx = ox - cx;
		double dy = oy - cy;
		return ((dx*dx)/(a*a) + (dy*dy)/(b*b)) <= 1.0;
	}
		
		
}
