/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import org.micromanager.MMStudio;

/**
 * Means for plugin classes to access the core, to support tricky things
 */
public class CoreCommunicator {

   public static int getImageWidth() {
      return (int) MMStudio.getInstance().getCore().getImageWidth();
   }

   public static int getImageHeight() {
      return (int) MMStudio.getInstance().getCore().getImageHeight();
   }

   public static void main(String[] args) {
      int[] lut = getCosineWarpLUT();
      System.out.println();
   }

   public static int[] getCosineWarpLUT() {
      //on gen3, 1524 pixels per line, throw away 40 at beginning and 44 at the end giving a double wide image width of 1440
      int pixPerLine = 1524;
      int doubleWideWidth = 1440;
      int interlacedWidth = doubleWideWidth / 2;
      double degreesPerPix = 360 / (double) pixPerLine;

      int[] lut = new int[doubleWideWidth / 2];

      int centerPixel = 0, oldPixel; //what is this?
      double lowestFactor = 2.0; //set comparison factor at greater value
      for (int unwarpedPixIndex = 0; unwarpedPixIndex < interlacedWidth; unwarpedPixIndex++) {
         oldPixel = interlacedWidth - unwarpedPixIndex;
         // Convert the image pixel location to a degree location. This is done from the right side of the
         // image as this is the only location for which the degree position (90) is known
         double anglePosition = 90.0 - (unwarpedPixIndex * degreesPerPix);
         // Calculate radian position from the right edge of the image.
         double radianPosition = (Math.PI / 2) - (anglePosition * Math.PI / 180.0);
         // Determine the cosine value of the radian position.
         double cosine = Math.cos(radianPosition);
         // Determine the linear position in radians with respect to the image center
         double linearPosition = Math.PI / 2.0 - radianPosition;
         // Determine the correction factor, linear position/cosine position relatative to image center
         double correctionFactor = linearPosition / cosine;
         // Find pixel = to image center based on mirror rotation, where correction factor = 1.00000
         if (correctionFactor < lowestFactor) {
            lowestFactor = correctionFactor;
            centerPixel = interlacedWidth - unwarpedPixIndex;
         }
      }

      //Repeat steps 1 - 5 in 2nd loop
      for (int unwarpedPixIndex = 0; unwarpedPixIndex < interlacedWidth; unwarpedPixIndex++) {
         oldPixel = interlacedWidth - unwarpedPixIndex;
         double anglePosition = 90.0 - (unwarpedPixIndex * degreesPerPix);
         double radianPosition = (Math.PI / 2) - (anglePosition * Math.PI / 180.0);
         double cosine = Math.cos(radianPosition);
         double linearPosition = Math.PI / 2.0 - radianPosition;
         double correctionFactor = linearPosition / cosine;
         if (correctionFactor == 0.0) {
            correctionFactor = 1.0;
         }
         lut[oldPixel] = (int) (centerPixel + Math.floor((oldPixel - centerPixel) / correctionFactor));
      }
      int first = lut[0];
      for (int i = 0; i < lut.length; i++) {
         lut[i] = lut[i] - first;
      }
      return lut;
   }
//   void BitFlowCamera::GetCosineWarpLUT(vector<int> &new_pixel, int image_width, int raw_width)
//{
//   const double PIE(3.141582);
//   vector<int> real_pixel;
//   real_pixel.resize(image_width);
//   new_pixel.resize(image_width);
//
//   double cosine;
//	double angle_factor;
//	double angle_position;
//	double radian_position;
//	double linear_position;
//	double correction_factor;
//	double lowest_factor;
//	//double correct_pixel;
//   int old_pixel;
//	int pixel;
//	int center_pixel;
//   //char ch;
//
//   /* image width in pixels after pixel reversal routine
//      half value in H_Max_Capture_size in MU Tech Driver file
//      Could be an option in the Stream Filter
//      May also need to be adjusted by the offset in the Pixel reversal process
//   */
//
//   /*calculate the angle per clock interval for H scan line
//   360.0  = 360 degrees for forward and vbackward scan,
//   1400 = number of clock intervals for each line
//   (from the Mu-Tech driver file: [PPL Control],  Pixel_Per_Line = 1400)
//   This variable could be automatically read or be part of the
//   stream filter options*/
//
//   angle_factor=360.0/raw_width;
//   angle_factor=360.0/1524;
//
//   /*
//   Dec 2006
//   PixelsPerLine = 127.0 * FREQ – FREQ can be varied by the user 
//   127 = usecond for the line scan and FREQ = the acquisition H clock frequency. 
//   Thus for the Raven and CRS mirror this = 127 x 12 = 1524 ppl
//
//   angle_factor= 360/PixelsPerline – this is what is used now 
//
//   1. First convert the image pixel location to a degree location
//   This is done from the right side of the image as this is the
//   only location for which the degree position (90) is known.
//   2. Calculate radian position (linear equivalent of angle) from the right edge
//   of the image.
//   3. Determine the cosine value of the radian position.
//   4. Determine the linear position in radians with respect to the image center
//   5. Determine the correction factor, linear position/cosine position
//   relatative to image center
//   6. Find pixel = to image center based on mirror rotation,
//   where correction factor = 1.0000000000
//   7. Repeat steps 1 - 5 in 2nd loop
//   8. Calculate corrected pixel location from center: Center position +
//   pixel distance from center divide by correction factor */
//
//   /*6. First loop to find center pixel*/
//   
//   lowest_factor=2.0; /*set comparison factor at greater value*/
//   for(pixel=0;pixel<image_width;pixel++)
//	{
//	   old_pixel = image_width - pixel;
//	   angle_position = 90.0 - (pixel * angle_factor);         /*1*/
//	   radian_position =(90.0*PIE/180.0) - (angle_position * PIE/180.0);  /*2*/
//	   cosine=cos(radian_position);                           /*3*/
//	   linear_position = (PIE/2.0) - radian_position;         /*4*/
//	   correction_factor = linear_position/cosine;                /*5*/
//	   /*printf("\n %d  %d %f",pixel,old_pixel,correction_factor);*/    /*line to check values*/
//	   if(correction_factor<lowest_factor)                                /*6*/
//		{
//		   lowest_factor=correction_factor;
//		   center_pixel=image_width - pixel;
//		}
//	}
//   /*
//   This code in blue is in the original dll and was used to find where the correction factor went to 1.0 
//   but I believe it can be replaced with a much simpler code
//
//   The pixel of the image where the distortion factor = 1 is at angle time 0 (or phase 0) and is 
//	= (FREQx127.0)/4  (pixels per line/4)
//   However this must be expressed relative to position where the distortion is the greatest at time, T or Phase Pie/2)
//	Correct Image Center position = PPL/2 – FREQx127/4
//   */
//
//   //printf("\nCenter pixel = %d, correlation factor = %f",center_pixel,lowest_factor);
//
//   /*7. Loop to calculated corrected pixel position*/
//   for(pixel=0;pixel<image_width;pixel++)
//	{
//	   old_pixel = image_width - pixel - 1;
//	   angle_position = 90.0 - (pixel * angle_factor);         /*1*/
//	   radian_position =(90.0*PIE/180.0) - (angle_position * PIE/180.0);  /*2*/
//	   cosine=cos(radian_position);                           /*3*/
//	   linear_position = (PIE/2.0) - radian_position;         /*4*/
//	   correction_factor = linear_position/cosine;           /*5*/
//      if (correction_factor == 0.0)
//         correction_factor = 1.0;
//	   real_pixel[old_pixel] = center_pixel + (int)((double)(old_pixel - center_pixel)/correction_factor); /*8*/
//	}
//   
//   /*
//   Again this code can be replaced by simpler code 
//   The correction factor = ø /sin ø where  ø = pixel number (from the correct center pixel) * ?ø 
//   ?ø = 2?/freq*127. The corretion factor is calculated for each pixel and applied to the pixel
//   */
//
//   /*9 Loop to shift pixels to new image LUT*/
//   for(pixel=0;pixel<image_width;pixel++)
//	{
//	   new_pixel[pixel]=real_pixel[pixel]-real_pixel[0];
//	   //printf("\nold pixel %d, new pixel %d", pixel, new_pixel[pixel]);
//	}
//}
}
