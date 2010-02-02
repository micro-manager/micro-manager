package org.micromanager.image5d;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

import java.awt.Checkbox;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.TextEvent;
import java.awt.image.ColorModel;
import java.io.File;

/** Opens a folder of image stacks as one big stack ("Hypervolume").
 * 	Can be used with the Hypervolume Browser and Shuffler.  
 * 	Code mostly copied from ImageJs FolderOpener. 
 * */
public class Hypervolume_Opener implements PlugIn {  		

	private static boolean grayscale;
	private static double scale = 100.0;
	private int n, start, increment;
	private String filter;
	private FileInfo fi;
	private String info1;
//	String selectedFilename;

	public void run(String arg) {
	    // Get filename and directory and sort list of files in directory.
		OpenDialog od = new OpenDialog("Open Sequence of Image Stacks...", arg);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
//		selectedFilename = name;		
		String[] list = new File(directory).list();
		if (list==null)
			return;
		NumberedStringSorter.sort(list);
		if (IJ.debugMode) IJ.log("Hypervolume_Opener: "+directory+" ("+list.length+" files)");

		// some inits.
		int width=0,height=0,type=0, depth=0;
		ImageStack stack = null;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;				
		IJ.register(Hypervolume_Opener.class);
		
		try {
		    // Open selected image and show Dialog.
			ImagePlus imp = new Opener().openImage(directory, name);
			if (imp!=null) {
				width = imp.getWidth();
				height = imp.getHeight();
				depth = imp.getStackSize();
				type = imp.getType();
				fi = imp.getOriginalFileInfo();
			} else { // Selected file is no image. Try opening starting from first file.//			 Open first image in filelist and show Dialog.
				for (int i=0; i<list.length; i++) {
					if (list[i].endsWith(".txt"))
						continue;
					imp = new Opener().openImage(directory, list[i]);
					if (imp!=null) {
						width = imp.getWidth();
						height = imp.getHeight();
						depth = imp.getStackSize();
						type = imp.getType();
						fi = imp.getOriginalFileInfo();
						break;
					}
				}				    
			}
			
			if (imp != null && !showDialog(imp, list)) {
				return;
			}
	
			if (width==0) {
				IJ.showMessage("Import Sequence", "This folder does not appear to contain any TIFF,\n"
				+ "JPEG, BMP, DICOM, GIF, FITS or PGM files.");
				return;
			}

			// n: number of images. Given in dialog.
			if (n<1)
				n = list.length;
			if (start<1 || start>list.length)
				start = 1;
			if (start+n-1>list.length)
				n = list.length-start+1;
			int filteredImages = n;
			if (filter!=null && (filter.equals("") || filter.equals("*")))
				filter = null;
			if (filter!=null) {
				filteredImages = 0;
  				for (int i=start-1; i<list.length; i++) {
 					if (list[i].indexOf(filter)>=0)
 						filteredImages++;
 				}
  				if (filteredImages==0) {
  					IJ.error("None of the "+n+" files contain\n the string '"+filter+"' in their name.");
  					return;
  				}
  			}
			if (filteredImages<n)
			    n = filteredImages;
  			
			int count = 0;
			int counter = 0;
			for (int i=start-1; i<list.length; i++) {
				if (list[i].endsWith(".txt"))
					continue;
				if (filter!=null && (list[i].indexOf(filter)<0))
					continue;
				if ((counter++%increment)!=0)
					continue;
				imp = new Opener().openImage(directory, list[i]);  
				if (imp!=null && stack==null) {								
					width = imp.getWidth();
					height = imp.getHeight();
					type = imp.getType();
					ColorModel cm = imp.getProcessor().getColorModel();
					if (scale<100.0)						
						stack = new ImageStack((int)(width*scale/100.0), (int)(height*scale/100.0), cm);
					else
						stack = new ImageStack(width, height, cm);
					info1 = (String)imp.getProperty("Info");
				}
				
				if (imp==null) {
					if (!list[i].startsWith("."))
						IJ.log(list[i] + ": unable to open");
				} else if (imp.getWidth()!=width || imp.getHeight()!=height)
					IJ.log(list[i] + ": wrong dimensions");
				else if (imp.getType()!=type)
					IJ.log(list[i] + ": wrong type");
				else {
					count += 1;
					IJ.showStatus(count+"/"+n);
					IJ.showProgress((double)count/n);
					depth = imp.getStackSize();

					for (int iSlice=1; iSlice<=depth; iSlice++) {
						imp.setSlice(iSlice);
						ImageProcessor ip = imp.getProcessor();
						if (grayscale) {
							ImageConverter ic = new ImageConverter(imp);
							ic.convertToGray8();
							ip = imp.getProcessor();
						}
						if (scale<100.0)
						ip = ip.resize((int)(width*scale/100.0), (int)(height*scale/100.0));
						if (ip.getMin()<min) min = ip.getMin();
						if (ip.getMax()>max) max = ip.getMax();
						String label = imp.getTitle();
						String info = (String)imp.getProperty("Info");
						if (info!=null)
							label += "\n" + info;
						stack.addSlice(label, ip);
					}					
				}
				if (count>=n)
					break;
			} // for loop over files in directory
		} catch (OutOfMemoryError e) {
			IJ.outOfMemory("Hypervolume_Opener");
			if (stack!=null) stack.trim();
		}
		if (stack!=null && stack.getSize()>0) {
			ImagePlus imp2 = new ImagePlus("Stack", stack);
			if (imp2.getType()==ImagePlus.GRAY16 || imp2.getType()==ImagePlus.GRAY32)
				imp2.getProcessor().setMinAndMax(min, max);
			imp2.setFileInfo(fi); // saves FileInfo of the first image
			if (imp2.getStackSize()==1 && info1!=null)
				imp2.setProperty("Info", info1);
			imp2.show();
		}
		IJ.showProgress(1.0);

		System.gc();
	}
	
	boolean showDialog(ImagePlus imp, String[] list) {
		int fileCount = list.length;

		String name = imp.getTitle();
		if (name.length()>4 && 
		        (name.substring(name.length()-4, name.length())).equalsIgnoreCase(".tif") ) {
		    name = name.substring(0, name.length()-4);
		}
		int i = name.length()-1;
		while (i>1 && name.charAt(i)>='0' && name.charAt(i)<='9') {
		    name = name.substring(0, i);
		    i--;
		}
		
		HypervolumeOpenerDialog gd = new HypervolumeOpenerDialog("Sequence Options", imp, list);
		gd.addNumericField("Number of Images: ", fileCount, 0);
		gd.addNumericField("Starting Image: ", 1, 0);
		gd.addNumericField("Increment: ", 1, 0);
		gd.addStringField("File Name Contains: ", name);
		gd.addNumericField("Scale Images", scale, 0, 4, "%");
		gd.addCheckbox("Convert to 8-bit Grayscale", grayscale);
		gd.addMessage("10000 x 10000 x 1000 (100.3MB)");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		n = (int)gd.getNextNumber();
		start = (int)gd.getNextNumber();
		increment = (int)gd.getNextNumber();
		if (increment<1)
			increment = 1;
		scale = gd.getNextNumber();
		if (scale<5.0) scale = 5.0;
		if (scale>100.0) scale = 100.0;
		filter = gd.getNextString();
		grayscale = gd.getNextBoolean();

		return true;
	}
	
}

class HypervolumeOpenerDialog extends GenericDialog {
   private static final long serialVersionUID = -6457730808918434870L;
   ImagePlus imp;
	int fileCount;
 	boolean eightBits;
 	String saveFilter = "";
 	String[] list;

	public HypervolumeOpenerDialog(String title, ImagePlus imp, String[] list) {
		super(title);
		this.imp = imp;
		this.list = list;
		this.fileCount = list.length;
	}

	protected void setup() {
		setStackInfo();
	}
 	
	public void itemStateChanged(ItemEvent e) {
 		setStackInfo();
	}
	
	public void textValueChanged(TextEvent e) {
 		setStackInfo();
	}

	void setStackInfo() {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int bytesPerPixel = 1;
		int nSlices = imp.getStackSize();
 		eightBits = ((Checkbox)checkbox.elementAt(0)).getState();
 		int n = getNumber(numberField.elementAt(0));
		int start = getNumber(numberField.elementAt(1));
		int inc = getNumber(numberField.elementAt(2));
		double scale = getNumber(numberField.elementAt(3));
		if (scale<5.0) scale = 5.0;
		if (scale>100.0) scale = 100.0;
		
		if (n<1)
			n = fileCount;
		if (start<1 || start>fileCount)
			start = 1;
		if (start+n-1>fileCount) {
			n = fileCount-start+1;
			//TextField tf = (TextField)numberField.elementAt(0);
			//tf.setText(""+nImages);
		}
		if (inc<1)
			inc = 1;
 		TextField tf = (TextField)stringField.elementAt(0);
 		String filter = tf.getText();
		// IJ.write(nImages+" "+startingImage);
 		if (!filter.equals("") && !filter.equals("*")) {
 			int n2 = n;
 			n = 0;
 			for (int i=start-1; i<start-1+n2; i++)
 				if (list[i].indexOf(filter)>=0) {
 					n++;
 					//IJ.write(n+" "+list[i]);
				}
   			saveFilter = filter;
 		}
		switch (imp.getType()) {
			case ImagePlus.GRAY16:
				bytesPerPixel=2;break;
			case ImagePlus.COLOR_RGB:
			case ImagePlus.GRAY32:
				bytesPerPixel=4; break;
		}
		if (eightBits)
			bytesPerPixel = 1;
		width = (int)(width*scale/100.0);
		height = (int)(height*scale/100.0);
		int n2 = n/inc;
		if (n2<0)
			n2 = 0;
		double size = (double)(width*height*nSlices*n2*bytesPerPixel)/(1024*1024);
 		((Label)theLabel).setText(width+" x "+height+" x "+nSlices*n2+" ("+IJ.d2s(size,1)+"MB)");
	}

	public int getNumber(Object field) {
		TextField tf = (TextField)field;
		String theText = tf.getText();
		Double d;
		try {d = new Double(theText);}
		catch (NumberFormatException e){
			d = null;
		}
		if (d!=null)
			return (int)d.doubleValue();
		else
			return 0;
      }

}

/** Sorts an array of Strings treating any sequences of digits as numbers, not single digits.
 * 	(Windows XP Style) 
 * 	Essentially the ij.util.StringSorter with a new compare method.
 * 	Works nicely, when image names have two or more numbers (like name-<ch>-<z>-<t>).
 */
class NumberedStringSorter {
	
	/** Sorts the array. */
	public static void sort(String[] a) {
		if (!alreadySorted(a))
			sort(a, 0, a.length - 1);
	}
	
	static void sort(String[] a, int from, int to) {
		int i = from, j = to;
		String center = a[ (from + to) / 2 ];
		do {
			while ( i < to && compareNumberedStrings(center, a[i]) > 0 ) i++;
			while ( j > from && compareNumberedStrings(center, a[j]) < 0 ) j--;
			if (i < j) {String temp = a[i]; a[i] = a[j]; a[j] = temp; }
			if (i <= j) { i++; j--; }
		} while(i <= j);
		if (from < j) sort(a, from, j);
		if (i < to) sort(a,  i, to);
	}
		
	static boolean alreadySorted(String[] a) {
		for ( int i=1; i<a.length; i++ ) {
			if (compareNumberedStrings(a[i], a[i-1]) < 0 )
			return false;
		}
		return true;
	}
	
	/** Compares Strings treating any sequences of digits in them as numbers, not single digits.
	 *  Similar to the treatment of numbers in the Windows XP explorer
	 * 
	 * @param first
	 * @param second
	 * @return: -1 if first<second, 0 if equal, +1 if first>second
	 */
	static int compareNumberedStrings (String first, String second) {
	    char c1, c2;
	    int N1, N2;
	    int i1=0, i2=0;
	    String num1, num2;
	    int int1, int2;

	    N1 = first.length();
	    N2 = second.length();
	    
	    while (i1<N1 && i2<N2) {
	        c1=first.charAt(i1);
	        c2=second.charAt(i2);	
	        
	        // char1 is digit (between ASCII 48 and 57)
	        if (c1>='0' && c1<='9') {
	            if ('9'<c2) {
	                return -1;
	            } else if ('0'>c2) {
	                return +1;
	            // char1 and char2 are both digits. Get the full numbers and compare.
	            } else {
	                num1 = ""; num2 = "";
	                do {
	                    num1 += c1;	                    
	                    i1++;
	                    if (i1<N1) {
	                        c1 = first.charAt(i1);
	                    }
	                } while(i1<N1 && c1>='0' && c1<='9');
	                int1 = Integer.parseInt(num1);
	                do {
	                    num2 += c2;	                    
	                    i2++;
	                    if (i2<N2) {
	                        c2 = second.charAt(i2);
	                    }
	                } while(i2<N2 && c2>='0' && c2<='9');
	                int2 = Integer.parseInt(num2);
	                
		        	if (int1<int2) {
		        	    return -1;
		        	} else if (int1>int2) {
		        	    return +1;
		        	}  
	            } // if (c1>='0' && c1<='9')
	            
	        // char1 is not digit, compare as usual   
	        } else if (c1<c2) {
	            return -1;
	        } else if (c1>c2) {
	            return +1;
	        } else {
		        ++i1; ++i2;	            
	        }
	        
	    } // while (i1<N1 && i2<N2)
	    
	    // Got through loop without differences. Compare by stringlength.
	    if (N1<N2) {
	        return -1;
	    } else if (N1>N2) {
	        return +1;
	    }
	    return 0;
	} // compareNumberedStrings
	
}
