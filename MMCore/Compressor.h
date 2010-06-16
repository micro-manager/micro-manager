// 0 is sucess, non-0 is an error
// CALLER MUST free ppDestination
//  source is an arbitrary buffer, <sourceLength> in length
// destination will contain an LZW - compressed data stream <destinationLength> in length pre-pended with a
// .gz header

#ifndef _COMPRESSOR_H_
#define _COMPRESSOR_H_

int CompressData(char* /*pSource*/, unsigned long /*sourceLength*/, char** /*ppDestination*/, unsigned long& /*destinationLength*/);


#endif