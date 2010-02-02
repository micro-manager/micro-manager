import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class Time_Stamper implements PlugInFilter {
	ImagePlus imp;
	double time;
	static int x = 2;
	static int y = 15;
	static int size = 12;
	int maxWidth;
	Font font;
	static double start = 0;
	static double interval = 1;
	static String suffix = "sec";
	static int decimalPlaces = 0;
	boolean firstSlice = true;
	boolean canceled;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Time_Stamper.class);
		return DOES_ALL+DOES_STACKS+STACK_REQUIRED;
	}

	public void run(ImageProcessor ip) {
		if (firstSlice)
			showDialog(ip);
		if (canceled)
			return;
		ip.setFont(font);
		ip.setColor(Toolbar.getForegroundColor());
		String s = getString(time);
		ip.moveTo(x+maxWidth-ip.getStringWidth(s), y);
		ip.drawString(s);
		time += interval;
	}
	
	String getString(double time) {
		if (interval==0.0)
			return suffix;
		else
			return (decimalPlaces==0?""+(int)time:IJ.d2s(time, decimalPlaces))+" "+suffix;
	}

	void showDialog(ImageProcessor ip) {
		firstSlice = false;
		Rectangle roi = ip.getRoi();
		if (roi.width<ip.getWidth() || roi.height<ip.getHeight()) {
			x = roi.x;
			y = roi.y+roi.height;
			size = (int) ((roi.height - 1.10526)/0.934211);	
			if (size<7) size = 7;
			if (size>80) size = 80;
		}
		GenericDialog gd = new GenericDialog("Time Stamper");
		gd.addNumericField("Starting Time:", start, 2);
		gd.addNumericField("Time Between Frames:", interval, 2);
		gd.addNumericField("X Location:", x, 0);
		gd.addNumericField("Y Location:", y, 0);
		gd.addNumericField("Font Size:", size, 0);
		gd.addNumericField("Decimal Places:", decimalPlaces, 0);
		gd.addStringField("Suffix:", suffix);
		gd.showDialog();
		if (gd.wasCanceled())
			{canceled = true; return;}
		start = gd.getNextNumber();
 		interval = gd.getNextNumber();
		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		size = (int)gd.getNextNumber();
		decimalPlaces = (int)gd.getNextNumber();
		suffix = gd.getNextString();
		font = new Font("SansSerif", Font.PLAIN, size);
		ip.setFont(font);
		time = start;
		if (y<size)
			y = size;
		maxWidth = ip.getStringWidth(getString(start+interval*imp.getStackSize()));
		imp.startTiming();
	}

}
