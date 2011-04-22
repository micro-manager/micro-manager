/*
 * 
 * Karl Bellve
 * Biomedical Imaging Group
 * University of Massachusetts Medical School
 * Karl.Bellve@umassmed.edu
 * http://big.umassmed.edu/
 *
 */

package edu.umassmed.big;

import java.io.*;
import java.lang.System;
import java.lang.Integer;

import edu.umassmed.big.Utils;

public class I2I {

	private String history[] = new String[15];
	private short min, max, xOrg, yOrg;
	private Integer X, Y, Z, T = 1;
	private Integer data_size, image_size;
	private int file_endian;
	private byte[] buffer = new byte[64];
	short[] data;

	/// Creates an blank I2I image with dimensions X,Y,Z
	public I2I(long x, long y, long z) {
		try { 
			// creates a header file for a new I2I image
			initializeI2I((int)x,(int)y, (int)z, 0);
			data = new short[data_size];
		} catch (Exception e) {
			System.out.println("Failed to create blank I2I: "+e.getMessage());
		}
	}
	/// Creates an blank I2I image with dimensions X,Y,Z
	public I2I(int x, int y, int z) {
		try { 
			// creates a header file for a new I2I image
			initializeI2I(x, y, z, 0);
			data = new short[data_size];
		} catch (Exception e) {
			System.out.println("Failed to create blank I2I: "+e.getMessage());
		}
	}
	
	/// Creates an I2I 16bit image from a 16 bit image with dimensions X,Y,Z.
	public I2I(short[] image,int x, int y, int z) {
		try {
			// creates a header file for a new I2I image
			initializeI2I(x, y, z, 0);
			data = new short[data_size];
			System.arraycopy(image,0,data, 0,image.length);
			//for (x = 0; x < data_size; x++) 
			//{
			//	data[x] = image[x];
			//}
		} catch (Exception e) {
			System.out.println("Failed to create I2I: "+e.getMessage());
		}
	}
	/// Creates an I2I 16bit image from an 8 bit image with dimensions X,Y,Z.
	public I2I(byte[] image, int x, int y, int z) {
		try {
			// creates a header file for a new I2I image
			initializeI2I(x, y, z, 0);
			data = new short[data_size];
			
			System.arraycopy(image,0,data, 0,image.length);
			//for (x = 0; x < data_size; x++) 
			//{
			//	data[x] = image[x];
			//}
		} catch (Exception e) {
			System.out.println("Failed to create I2I: "+e.getMessage());
		}
	}
	
////// BELOW DEPRECATED	
	// deprecated, use the function that has the array first
	/// Creates an I2I 16bit image from a 16 bit image with dimensions X,Y,Z.
	public I2I(int x, int y, int z, short[] image) {
		try {
			// creates a header file for a new I2I image
			initializeI2I(x, y, z, 0);
			data = new short[data_size];
			System.arraycopy(image,0,data, 0,image.length);
			//for (x = 0; x < data_size; x++) 
			//{
			//	data[x] = image[x];
			//}
		} catch (Exception e) {
			System.out.println("Failed to create I2I: "+e.getMessage());
		}
	}
	
	// deprecated, use the function that has the array first
	public I2I(int x, int y, int z, byte[] image) {
		try {
			// creates a header file for a new I2I image
			initializeI2I(x, y, z, 0);
			data = new short[data_size];
			
			System.arraycopy(image,0,data, 0,image.length);
			//for (x = 0; x < data_size; x++) 
			//{
			//	data[x] = image[x];
			//}
		} catch (Exception e) {
			System.out.println("Failed to create I2I: "+e.getMessage());
		}
	}
	
////// ABOVE DEPRECATED
	/// Opens an I2I file
	public I2I(String file) {

		DataInputStream in = null;

		try {
			in = new DataInputStream(new BufferedInputStream(
					new FileInputStream(file)));

			// Are we an I2I file?
			int c = in.readByte();
			if (c == 'I') {
				// READ HEADER
				in.read(buffer, 0, 6);
				String _X = new String(buffer, 0, 6);
				X = Integer.parseInt(_X.trim());

				in.read(buffer, 0, 6);
				String _Y = new String(buffer, 0, 6);
				Y = Integer.parseInt(_Y.trim());

				in.read(buffer, 0, 6);
				String _Z = new String(buffer, 0, 6);
				Z = Integer.parseInt(_Z.trim());

				in.skip(1); // skip space
				file_endian = in.readByte();
				if (file_endian == 'L') { // must swap bytes if data is little
											// endian
					min = swap(in.readShort());
					max = swap(in.readShort());
					xOrg = swap(in.readShort());
					yOrg = swap(in.readShort());
					setT(swap(in.readShort()));

				} else {
					// Java is big endian, most significant byte first
					min = in.readShort();
					max = in.readShort();
					xOrg = in.readShort();
					yOrg = in.readShort();
					setT(in.readShort());
				}

				// Load History file
				in.skip(33);
				for (int x = 0; x < 15; x++) {
					in.read(buffer, 0, 64);
					history[x] = new String(buffer);
				}

				// READ DATA
				data_size = getX() * getY() * getZ();
				data = new short[data_size];

				System.out.println(file + " Data Size: " + data_size);
				for (int x = 0; x < data_size; x++) {
					if (file_endian == 'B')
						data[x] = in.readShort();
					else
						data[x] = swap(in.readShort());
				}
			}
			in.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/// Initializer method
	private void initializeI2I(int x, int y, int z, int t)
	{
		
		setX(x);
		setY(y);
		setZ(z);
		setT(t);
		min = 0; max = 0; xOrg = 0; yOrg = 0;
		data_size = getX() * getY() * getZ();
		image_size = getX() * getY();
      Utils u = new Utils();
		history[0] = ("* Created on " + u.generateDate() + " by " + System.getenv("HOSTNAME"));
	}
	/// Saves an I2I image 
	public void saveImage(String file) {

		DataOutputStream out = null;
		try {
			
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			// write header
			System.out.println("Writing Header");
			String header = String.format("I %5d %5d %5d B", getX(), getY(),getZ());
			out.writeBytes(header);
			out.writeShort(getMin());
			out.writeShort(getMax());
			out.writeShort(getxOrg());
			out.writeShort(getyOrg());
			out.writeShort(getT());
			
			for (int x = 0; x < 33; x++)
				out.writeByte(32);
			// write history
			System.out.println("Writing History");
			for (int x = 0; x < 15; x++) {
				if (history[x] != null) {
					out.writeBytes(history[x]);
					if (history[x].length() < 64) {
						for (int y = history[x].length(); y < 64; y++)
							out.writeByte(32); // fill with spaces
					}
				}
				else {
					for (int y = 0; y < 64;y++) out.writeByte(32); // fill with spaces
				}
			}
			
			System.out.println("Writing Image Data");
			// write data
			for (int x = 0; x < data_size; x++) {
				out.writeShort(data[x]);
			}
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public short getX() {
		return (X.shortValue());
	}
	public short getY() {
		return (Y.shortValue());
	}
	public short getZ() {
		return (Z.shortValue());
	}
	public short getT() {
		if (T < 1)
			return (1);
		else
			return (T.shortValue());
	}
	public void setX(int x) {
		if (x < 0) x = 1; 
		X = x;
		return;
	}
	public void setY(int y) {
		if (y < 0) y = 1;
		Y = y;
		return;
	}
	// number of total planes
	public void setZ(int z) {
		if (z < 0) z = 1;
		Z = z;
		return;
	}
	// number of time points. Divide Z/T to get the number of Z per time point
	public void setT(int t) {
		if (t < 1) t = 1;
		if (t == 8224) t = 1;
		T = t;
		return;
	}
	// full array
	public short[] getImage() {	
		return (data); // pointer to all the data
	}
	// return 1 2D plane
	public short[] getImage2D(int z) {
		Utils u = new Utils();
      return data;
		// function is missing return (Utils.getarray(data, z * image_size,image_size));

	}
	// return 1 3D plane
	public short[] getImage3D(int z) {
		/*
		if (T.intValue() > 1) {
			int foo = image_size * Z.intValue()/T.intValue();
			return (Utils.getarray(data, z * foo,foo));
		}
		else*/ return (data); // only one 3D time point, just return the entire array
	}
	public void addImage(int[] image, int z) {
		// expand array to add
		try {
			int foo = X * Y * z;
			//System.arraycopy(image,0,data,foo,image.length);
			for (int x = 0; x < X * Y; x++) 
			{
				data[foo + x] = (short) (image[x]);
			}
		}
		catch  (Exception e){
			System.out.println("Exception: " + e.getMessage());
		}
		return;
	}
	// micromanager uses unsigned shorts, but we store as signed shorts.
	public void addUImage(short[] image, int z) {
		try {
		// expand array to add
			int value = 0;
			int foo = X * Y * z;
			for (int x = 0; x < X * Y; x++) 
			{   
				value = (int)(char)image[x];
				data[foo + x] = (short)(value>>1);  // divide by 2 to avoid out of bounds
			}

		}
		catch  (Exception e){
			System.out.println("Exception: " + e.getMessage());
		}
		return;
	}
	public void addImage(short[] image, int z) {
		// expand array to add

		try { 
			int foo = X * Y * z;
				for (int x = 0; x < X * Y; x++) 
				{   
					data[foo + x] = image[x];  
				}
		}
		catch (Exception e){ 
			System.out.println("Exception: " + e.getMessage());
			
		}
		return;
	}
	public void addImage(byte[] image, int z) {
		try {
			// expand array to add
			int foo = X * Y * z;
			for (int x = 0; x < X * Y; x++) 
			{
				data[foo + x] = (short)image[x];
			}
		}
		catch (Exception e){
			System.out.println("Exception: " + e.getMessage());
		}
		
		return;
	}
	public static short swap(short value) {
		int b1 = value & 0xff;
		int b2 = (value >> 8) & 0xff;

		return (short) (b1 << 8 | b2 << 0);
	}

	public short getMin() {
		return min;
	}

	public void setMin(short min) {
		if (min == 8224) min = 0;
		this.min = min;
	}

	public short getMax() {
		return max;
	}

	public void setMax(short max) {
		if (max == 8224) max = 0;
		this.max = max;
	}

	public short getxOrg() {
		return xOrg;
	}

	public void setxOrg(short xOrg) {
		if (xOrg == 8224) xOrg = 0;
		this.xOrg = xOrg;
	}

	public short getyOrg() {
		return yOrg;
	}

	public void setyOrg(short yOrg) {
		if (yOrg == 8224) yOrg = 0;
		this.yOrg = yOrg;
	}
}
