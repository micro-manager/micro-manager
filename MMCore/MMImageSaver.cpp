#include "MMImageSaver.h"
#include "libtiff/tiffio.h"
#include "../MMDevice/ImageMetadata.h"
#include <iostream>
#include <iomanip>
#include "boost/foreach.hpp"
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

   metadataStream_.seekp(-3,ios::end);
   metadataStream_ << endl << "}" << endl;
   metadataStream_.close();
}

void MMImageSaver::Start()
{
   metadataStream_.open("metadata.txt");
   metadataStream_ << "{" << endl;

   activate();
}


void MMImageSaver::WriteNextImage(string filestem)
{
   Metadata md;
   void * img = core_->popNextImageMD(0,0,md);
   
   int width = core_->getImageWidth();
   int height = core_->getImageHeight();
   int depth = core_->getBytesPerPixel();

   string filename = CreateFileName(filestem, md);
   WriteImage(filename, img, width, height, depth, md);
   WriteMetadata(md);
}

string MMImageSaver::CreateFileName(string filestem, Metadata md)
{
   stringstream tiffFileName;
   tiffFileName << setfill('0');
   tiffFileName << filestem << "_" 
      << setw(9) << atoi(md.frameData["Frame"].c_str()) << "_" 
      << md.frameData["Channel"].c_str() << "_" 
      << setw(3) << atoi(md.frameData["Slice"].c_str())
      << ".tif";
   return tiffFileName.str();
}

void MMImageSaver::WriteMetadata(Metadata md)
{
   metadataStream_ << "\t\"FrameKey-" << atoi(md.frameData["Frame"].c_str())
      << "-" << atoi(md.frameData["ChannelIndex"].c_str())
      << "-" << atoi(md.frameData["Slice"].c_str())
      << "\": {" << endl;
   
   pair<string, string> p;
   BOOST_FOREACH(p, md.frameData)
   {
      metadataStream_ << "\t\t\"" << p.first << "\": \"" << p.second << "\"," << endl;
   }

   metadataStream_.seekp(-3, ios::end);
   metadataStream_ << endl << "\t}," << endl;
   metadataStream_.flush();
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