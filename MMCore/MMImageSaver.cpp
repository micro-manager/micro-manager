#include "MMImageSaver.h"
#include "libtiff/tiffio.h"
#include "../MMDevice/ImageMetadata.h"
#include <iostream>
#include <iomanip>
//#include "boost/property_tree/ptree.hpp"



MMImageSaver::MMImageSaver(CMMCore * core)
{
   core_ = core; 
   buffer_ = new unsigned char[0];
}

void MMImageSaver::Run()
{
   do {
      while (core_->getRemainingImageCount() > 0)
      {
         string filestem("img");
         WriteNextImage(filestem);
      }
      core_->sleep(30);
   } while (!core_->acquisitionIsFinished() || core_->getRemainingImageCount() > 0);
}

void MMImageSaver::Start()
{
   activate();
}


void MMImageSaver::WriteNextImage(string filestem)
{
   Metadata md;
   void * img = core_->popNextImageMD(0,0,md);
   
   stringstream debugString;
   unsigned char * imgChars = (unsigned char *) img;
   debugString << "four bytes of image: " 
      << (unsigned int) imgChars[4*512*256 + 0] << "," 
      << (unsigned int) imgChars[4*512*256 + 1] << "," 
      << (unsigned int) imgChars[4*512*256 + 2] << "," 
      << (unsigned int) imgChars[4*512*256 + 3];

   core_->logMessage(debugString.str().c_str());

   int width = core_->getImageWidth();
   int height = core_->getImageHeight();
   int depth = core_->getBytesPerPixel();

   stringstream tiffFileName;
   tiffFileName << setfill('0');
   tiffFileName << filestem << "_" 
      << setw(10) << md.frameIndex << "_" 
      << setw(1) << md.channelIndex << "_" 
      << setw(3) << md.sliceIndex 
      << ".tif";

   WriteImage(tiffFileName.str(), img, width, height, depth, md);
}

void MMImageSaver::WriteImage(string filename, void * img, int width, int height, int depth, Metadata md)
{

   TIFF * tif = TIFFOpen(filename.c_str(),"w");


   if (tif != NULL)
   {

      TIFFSetField(tif, TIFFTAG_IMAGEWIDTH, width); 
      TIFFSetField(tif, TIFFTAG_IMAGELENGTH, height);

      TIFFSetField(tif, TIFFTAG_COMPRESSION, COMPRESSION_NONE);
      TIFFSetField(tif, TIFFTAG_PLANARCONFIG, PLANARCONFIG_CONTIG);

      if (depth == 4 || depth == 8)
      {
         TIFFSetField(tif, TIFFTAG_PHOTOMETRIC, PHOTOMETRIC_RGB);
         TIFFSetField(tif, TIFFTAG_BITSPERSAMPLE, 8*depth/4);
         TIFFSetField(tif, TIFFTAG_SAMPLESPERPIXEL, 4);

         img = SwapRedAndBlue((unsigned char *) img, width, height, depth);
      }
      else
      {
         TIFFSetField(tif, TIFFTAG_PHOTOMETRIC, PHOTOMETRIC_MINISBLACK);
         TIFFSetField(tif, TIFFTAG_BITSPERSAMPLE, 8*depth);
         TIFFSetField(tif, TIFFTAG_SAMPLESPERPIXEL, 1);
      }

      if(TIFFWriteEncodedStrip(tif, 0, img, width * height * depth) == 0)
      {
         core_->logMessage("Image writing failed.");
      }

      TIFFClose(tif);
   }
}


unsigned char * MMImageSaver::SwapRedAndBlue(unsigned char * img, int width, int height, int depth)
{
   long n = width * height * depth;
   if (bufferLength_ != n)
   {
      delete buffer_;
      buffer_ = new unsigned char[width * height * depth];
      bufferLength_ = n;
   }

   for (long i=0; i<n; i+=depth)
   {
      buffer_[i] = img[i+2]; // Red to blue
      buffer_[i+2] = img[i]; // Blue to red
      buffer_[i+1] = img[i+1]; // Green to green
      buffer_[i+3] = img[i+3]; // Alpha to alpha
   }

   return buffer_;
}