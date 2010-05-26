#include "MMImageSaver.h"
#include "libtiff/tiffio.h"
#include "../MMDevice/ImageMetadata.h"
#include <iostream>
#include <iomanip>

MMImageSaver::MMImageSaver(CMMCore * core)
{
   core_ = core; 
}

void MMImageSaver::Run()
{
   do {
      while (core_->getRemainingImageCount() > 0)
      {
         string filestem("img");
         WriteNextImage(filestem);
      }
      Sleep(30);
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
   TIFF * tif = TIFFOpen(tiffFileName.str().c_str(),"w");

   if (tif != NULL)
   {

      TIFFSetField(tif, TIFFTAG_IMAGEWIDTH, width); 
      TIFFSetField(tif, TIFFTAG_IMAGELENGTH, height);
      TIFFSetField(tif, TIFFTAG_BITSPERSAMPLE, 8*depth);
      TIFFSetField(tif, TIFFTAG_SAMPLESPERPIXEL, 1);

      TIFFSetField(tif, TIFFTAG_COMPRESSION, COMPRESSION_NONE);
      TIFFSetField(tif, TIFFTAG_PLANARCONFIG, PLANARCONFIG_CONTIG);
      TIFFSetField(tif, TIFFTAG_PHOTOMETRIC, PHOTOMETRIC_MINISBLACK);


      if(TIFFWriteEncodedStrip(tif, 0, img, width * height * depth) == 0)
      {
         core_->logMessage("Image writing failed.");
      }

      TIFFClose(tif);
   }
}
