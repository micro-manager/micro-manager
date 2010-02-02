#include <stdlib.h>
#include <limits.h>
#include <stdio.h>
#include <assert.h>

#include <iostream>
#include <ostream>
#include <strstream>
#include <sstream>


#include "SaveBmpImage.h"
#include "yyyyMMddhhmmss.h"


void SaveBmpImage(PBITMAPINFOHEADER pDIB)
{


	if(NULL != pDIB)
	{
		yyyyMMddhhmmss now;
		std::ostringstream fn;
		fn.str("");
		fn << "C:/MMImage." << now.ToString() << ".bmp";

		// Save the image to disk
		FILE *pFile = fopen( fn.str().c_str(), "wb");
		if(NULL != pFile)
		{
			DWORD dwPaletteSize = 0;

			switch(pDIB->biBitCount)
			{
			case 1:
				dwPaletteSize = 2;
				break;
			case 8:
				dwPaletteSize = 256;
				break;
			case 24:
				break;
			default:
				assert(0); //Not going to work!
				break;
			}

		  // If the driver did not fill in the biSizeImage field, then compute it
		  // Each scan line of the image is aligned on a DWORD (32bit) boundary
		  if( pDIB->biSizeImage == 0 )
		  {
			 pDIB->biSizeImage = ((((pDIB->biWidth * pDIB->biBitCount) + 31) & ~31) / 8) * pDIB->biHeight;

			 // If a compression scheme is used the result may infact be larger
			 // Increase the size to account for this.
			 if (pDIB->biCompression != 0)
			 {
				pDIB->biSizeImage = (pDIB->biSizeImage * 3) / 2;
			 }
		  }

		  int nImageSize = pDIB->biSizeImage + (sizeof(RGBQUAD)*dwPaletteSize)+sizeof(BITMAPINFOHEADER);

		  BITMAPFILEHEADER bmpFIH = {0};
		  bmpFIH.bfType = ( (WORD) ('M' << 8) | 'B');
		  bmpFIH.bfSize = nImageSize + sizeof(BITMAPFILEHEADER);
		  bmpFIH.bfOffBits = sizeof(BITMAPFILEHEADER)+sizeof(BITMAPINFOHEADER)+(sizeof(RGBQUAD)*dwPaletteSize);
  
		  fwrite(&bmpFIH, 1, sizeof(BITMAPFILEHEADER), pFile);
		  fwrite(pDIB, 1, nImageSize, pFile);
		  fclose(pFile);
		  pFile = 0;
		} // pfile != NULL
	} //pB != NULL

}




