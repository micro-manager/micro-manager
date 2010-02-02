package org.micromanager.image5d;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Scrollbar;

/** Quick hack to add labels to the dimension sliders of Image5DWindow
 * @author Joachim Walter
 */
public class ScrollbarWithLabel extends Panel {
   private static final long serialVersionUID = -2305926709210039775L;
   /**
	 * @param orientation
	 * @param value
	 * @param visible
	 * @param minimum
	 * @param maximum
	 */
	private Scrollbar bar;
	private Label label;
	
	private int orientation;
	
	public ScrollbarWithLabel(int orientation, int value, int visible,
			int minimum, int maximum, String label) {
		super(new BorderLayout(2, 0));
		this.orientation = orientation;
		bar = new Scrollbar(orientation, value, visible, minimum, maximum);
		if (label != null) {
			this.label = new Label(label);
		} else {
			this.label = new Label("");
		}
		if (orientation == Scrollbar.HORIZONTAL)
			add(this.label, BorderLayout.WEST);
		else if (orientation == Scrollbar.VERTICAL)
			add(this.label, BorderLayout.NORTH);
		else
			throw new IllegalArgumentException("invalid orientation");
		
		add(bar, BorderLayout.CENTER);
	}
	
	/* (non-Javadoc)
	 * @see java.awt.Component#getPreferredSize()
	 */
	public Dimension getPreferredSize() {
		Dimension dim = new Dimension(0,0);

		if (orientation == Scrollbar.HORIZONTAL){
			int width = bar.getPreferredSize().width+label.getPreferredSize().width;
			Dimension minSize = getMinimumSize();
			if (width<minSize.width) width = minSize.width;		
			int height = bar.getPreferredSize().height;
			dim = new Dimension(width, height);
		} else {
			int height = bar.getPreferredSize().height+label.getPreferredSize().height;
			Dimension minSize = getMinimumSize();
			if (height<minSize.height) height = minSize.height;	
//			int width = Math.max(bar.getPreferredSize().width, label.getPreferredSize().width);
			int width = bar.getPreferredSize().width;
			dim = new Dimension(width, height);			
		}
		return dim;
	}
	
	public Dimension getMinimumSize() {
		if(orientation==Scrollbar.HORIZONTAL) {
			return new Dimension(80, 15);
		} else {
			return new Dimension(15, 80);
		}
	}
	
	public Scrollbar getScrollbar() {
		return bar;
	}

}
