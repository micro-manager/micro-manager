//#include "zlib.h"
//#include "Compressor.h"
//#include "memory.h"
//#include "stdlib.h"

#if 0

#define LZWBUFFERSIZE 262144
#define GZIPOPTION 16
// use the zlib lzw library to compress the stream of data
int Compressor::CompressData(char* pSource, unsigned long sourceLength, char** ppDestination, unsigned long& destinationLength)
{
   int ret;
   unsigned long totalOut = 0;
   unsigned long bytesPutOut;
   z_stream strm;

   *ppDestination = 0;
   unsigned char outBuffer[LZWBUFFERSIZE];
   /* allocate deflate state */
   strm.zalloc = Z_NULL;
   strm.zfree = Z_NULL;
   strm.opaque = Z_NULL;

   // create an gz - formatted archive in memory.
   ret = deflateInit2(&strm, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15+GZIPOPTION, 9, Z_DEFAULT_STRATEGY );
   if (ret != Z_OK)
      return ret;
       
   strm.avail_in = sourceLength;
   strm.next_in = (unsigned char*)pSource;

   /* run deflate() on input until output buffer not full, finish
   compression if all of source has been read in */
   do 
   {
      strm.avail_out = LZWBUFFERSIZE;
      strm.next_out = outBuffer;
      ret = deflate(&strm, Z_FINISH);    /* no bad return value */
      //assert(ret != Z_STREAM_ERROR);  /* state not clobbered */
      bytesPutOut = LZWBUFFERSIZE - strm.avail_out;
      unsigned long outOffset = totalOut;
      totalOut += bytesPutOut;
      *ppDestination = (char*)realloc(*ppDestination, totalOut);
      memcpy((*ppDestination) + outOffset, outBuffer, bytesPutOut);

     } while ( 0 == strm.avail_out );

   /* clean up and return */
   (void)deflateEnd(&strm);

   destinationLength = totalOut;
   return ret;

}
#endif
