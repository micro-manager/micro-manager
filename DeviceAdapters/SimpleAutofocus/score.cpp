#include "SimpleAutofocus.h"

unsigned short FindMedian(unsigned short* arr, const int lengthMinusOne)
{
   unsigned short tmp;
   // n.b. this was ported from java, looks like a bubble sort....
   // todo use qsort
   for(int i=0; i<lengthMinusOne; ++i)
   {
      for(int j=0; j<lengthMinusOne-i; ++j)
      {
         if (arr[j+1]<arr[j])
         {
            tmp = arr[j];
            arr[j]=arr[j+1];
            arr[j+1]=tmp;
         }
      }
   }
   return arr[lengthMinusOne/2 +1];
}


double GetScore(unsigned short* img, int w0, int h0, double cropFactor)
{
   unsigned short windo[9];
   int width =  (int)(cropFactor * w0);
   int height = (int)(cropFactor * h0);
   int ow = (int)(((1-cropFactor)/2)*w0);
   int oh = (int)(((1-cropFactor)/2)*h0);
   unsigned short* smoothedImage = new unsigned short[width*height];
   // calculate the standard deviation & mean
   long nPts = 0;
   double mean = 0;
   double M2 = 0;
   double delta;
   // one-pass algorithm for mean and std from Welford / Knuth  - KH
   for (int i=0; i<width; i++)
   {
      for (int j=0; j<height; j++)
      {
         ++nPts;
         long value = img[ow+i+ width*(oh+j)];
         delta = value - mean;
         mean = mean + delta/nPts;
         M2 = M2 + delta*(value - mean); // #This expression uses the new value of mean_
      }
   }
   //double variance_n = M2/nPts;
   double variance = M2/(nPts - 1);
   double stdOverMean = 0.;
   double meanScaling = 1.;
   if( 0. != mean)
   {
      stdOverMean = pow(variance,0.5)/mean;
      meanScaling = 1./mean;
   }
   //LogMessage("N " + boost::lexical_cast<std::string,long>(nPts) + " mean " +  boost::lexical_cast<std::string,float>((float)mean_) + " nrmlzd std " +  boost::lexical_cast<std::string,float>((float)standardDeviationOverMean_) );
   // ToDO -- eliminate copy above.
   int x[9];
   int y[9];
   /*Apply 3x3 median filter to reduce shot noise*/
   for (int i=0; i<width; i++)
   {
      for (int j=0; j<height; j++)
      {
         float theValue;
         x[0]=ow+i-1;
         y[0]= (oh+j-1);
         x[1]=ow+i;
         y[1]= (oh+j-1);
         x[2]=ow+i+1;
         y[2]= (oh+j-1);
         x[3]=ow+i-1;
         y[3]=(oh+j);
         x[4]=ow+i;
         y[4]=(oh+j);
         x[5]=ow+i+1;
         y[5]=(oh+j);
         x[6]=ow+i-1;
         y[6]=(oh+j+1);
         x[7]=ow+i;
         y[7]=(oh+j+1);
         x[8]=ow+i+1;
         y[8]=(oh+j+1);
         // truncate the median filter window  -- duplicate edge points
         // this could be more efficient, we could fill in the interior image [1,w0-1]x[1,h0-1] then explicitly fill in the edge pixels.
         for(int ij =0; ij < 9; ++ij)
         {
            if( x[ij] < 0)
               x[ij] = 0;
            else if( w0-1 < x[ij])
               x[ij] = w0-1;
            if( y[ij] < 0)
               y[ij] = 0;
            else if( h0-1 < y[ij])
               y[ij] = h0-1;
         }
         for(int ij = 0; ij < 9; ++ij)
         {
            windo[ij] = img[x[ij] + w0*y[ij]];
         }
         // N.B. this window filler as ported from java needs to have a pad guaranteed around the cropped image!!!! KH
         //windo[0] = pShort_[ow+i-1 + width*(oh+j-1)];
         //windo[1] = pShort_[ow+i+ width*(oh+j-1)];
         //windo[2] = pShort_[ow+i+1+ width*(oh+j-1)];
         //windo[3] = pShort_[ow+i-1+ width*(oh+j)];
         //windo[4] = pShort_[ow+i+ width*(oh+j)];
         //windo[5] = pShort_[ow+i+1+ width*(oh+j)];
         //windo[6] = pShort_[ow+i-1+ width*(oh+j+1)];
         //windo[7] = pShort_[ow+i+ width*(oh+j+1)];
         //windo[8] = pShort_[ow+i+1+ width*(oh+j+1)];
         // to reduce effect of bleaching on the high-pass sharpness measurement, i use the image normalized by the mean - KH.
         theValue = (float)((double)FindMedian(windo,8)*meanScaling);
         smoothedImage[i + j*width] = (unsigned short)theValue;
         // the dynamic range of the normalized image is a very strong function of the image sharpness, also  - KH
         // here I'm using dynamic range of the median-filter image
         // a faster measure could skip the median filter, but use the sum of the 5 - 10 highest and 5 - 10 lowest normalized pixels
         // average over a couple of points to lessen effect of fluctuations & noise
         // todo - make the active measure of image sharpness user-selectable
         // save the  max points and the min points
         double min1 = 1.e8;
         double min2 = 1.e8;
         double max1 = -1.e8;
         double max2 = -1.e8;
         if( theValue < min1 )
         {
            min2 = min1;
            min1 = theValue;
         }
         else if (theValue < min2)
         {
            min2 = theValue;
         }
         if( max1 < theValue)
         {
            max2 = max1;
            max1 = theValue;
         }
         else if (max2 < theValue )
         {
            max2 = theValue;
         }
      }
   }
   /*Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp*/
   double sharpness(0.0);
   for (int k=1; k<width-1; k++) {
      for (int l=1; l<height-1; l++)
      {
         double convolvedValue = -2.0*smoothedImage[k-1 + width*(l-1)] - smoothedImage[k+ width*(l-1)]-smoothedImage[k-1 + width*l]+smoothedImage[k+1 + width*l]+smoothedImage[k+ width*(l+1)]+2.0*smoothedImage[k+1+ width*(l+1)];
         sharpness = sharpness + convolvedValue*convolvedValue;
      }
   }
   delete[] smoothedImage;
   return sharpness;
}
