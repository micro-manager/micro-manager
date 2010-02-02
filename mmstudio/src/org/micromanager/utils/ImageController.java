/**
 * 
 */
package org.micromanager.utils;

import ij.ImagePlus;

/**
 * @author OD
 *
 */
public interface ImageController {
	public void setImagePlus(ImagePlus ip, ContrastSettings cs8bit,
			ContrastSettings cs16bit);
	public void setContrastSettings(ContrastSettings contrastSettings8_,
			ContrastSettings contrastSettings16_);
	public void update();
}
