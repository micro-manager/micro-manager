
#include "stdio.h"
#include "math.h"
#include "stdlib.h"
#ifdef WIN32
#include "memory.h"
#endif

// very simple utility to write a tiff image, currently constrained to be
// a compact array of BGR pixels, each triple being 24 bits in length
// k.h
// can be useful to intercept stream of pixels directly from camera

int writeCompactTiffRGB( const int /* width */, const int /* height*/ , const unsigned char* /* address of array of BGR bytes*/, const char* const /* the output filename*/ );


// from somewhere
unsigned int htoi (const char *ptr)
{
    unsigned int value = 0;
    char ch = *ptr;

    /*--------------------------------------------------------------------------*/

    while (ch == ' ' || ch == '\t')
        ch = *(++ptr);

    for (;;) {

        if (ch >= '0' && ch <= '9')
            value = (value << 4) + (ch - '0');
        else if (ch >= 'A' && ch <= 'F')
            value = (value << 4) + (ch - 'A' + 10);
        else if (ch >= 'a' && ch <= 'f')
            value = (value << 4) + (ch - 'a' + 10);
        else
            return value;
        ch = *(++ptr);
    }
}

// always send in an even number of ASCII charaters!!
void WriteHexString (FILE* fpw, const char*const pdata)
{
    const char*  p = pdata;

    while( *p != 0)
    {

        char hexrep[3];
        hexrep[0] = *p;
        hexrep[1] = *(p+1);
        hexrep[2] = 0;
        p+=2;
        unsigned int theChar = htoi(hexrep);
        putc(theChar, fpw);
    }
}

int writeCompactTiffRGB( const int nx, const int ny, const unsigned char* pdata, const char* const fname )
{
   int offset;
	int retval = 0;

   FILE* fptr = fopen(fname,"wb");

	if( NULL != fptr)
	{

		/* Write the header */
		WriteHexString(fptr,"4d4d002a");    /* this is MM which means big-endian follwed by the TIFF identifier
														- the tags are all hard-coded below so this always writes TIFF's that
														look as though they are from big-endian machines.*/
		offset = nx * ny * 3 + 8;
		putc((offset & 0xff000000) / 16777216,fptr);
		putc((offset & 0x00ff0000) / 65536,fptr);
		putc((offset & 0x0000ff00) / 256,fptr);
		putc((offset & 0x000000ff),fptr);

		fwrite(pdata, sizeof(*pdata), nx*ny*3, fptr);

		/* Write the footer */
		WriteHexString(fptr,"000e");  /* The number of directory entries (14) */

		/* Width tag, short int */
		WriteHexString(fptr,"0100000300000001");
		fputc((nx & 0xff00) / 256,fptr);    /* Image width */
		fputc((nx & 0x00ff),fptr);
		WriteHexString(fptr,"0000");

		/* Height tag, short int */
		WriteHexString(fptr,"0101000300000001");
		fputc((ny & 0xff00) / 256,fptr);    /* Image height */
		fputc((ny & 0x00ff),fptr);
		WriteHexString(fptr,"0000");

		/* Bits per sample tag, short int */
		WriteHexString(fptr,"0102000300000003");
		offset = nx * ny * 3 + 182;
		putc((offset & 0xff000000) / 16777216,fptr);
		putc((offset & 0x00ff0000) / 65536,fptr);
		putc((offset & 0x0000ff00) / 256,fptr);
		putc((offset & 0x000000ff),fptr);

		/* Compression flag, short int */
		WriteHexString(fptr,"010300030000000100010000");

		/* Photometric interpolation tag, short int */
		WriteHexString(fptr,"010600030000000100020000");

		/* Strip offset tag, long int */
		WriteHexString(fptr,"011100040000000100000008");

		/* Orientation flag, short int */
		WriteHexString(fptr,"011200030000000100010000");

		/* Sample per pixel tag, short int */
		WriteHexString(fptr,"011500030000000100030000");

		/* Rows per strip tag, short int */
		WriteHexString(fptr,"0116000300000001");
		fputc((ny & 0xff00) / 256,fptr);
		fputc((ny & 0x00ff),fptr);
		WriteHexString(fptr,"0000");

		/* Strip byte count flag, long int */
		WriteHexString(fptr,"0117000400000001");
		offset = nx * ny * 3;
		putc((offset & 0xff000000) / 16777216,fptr);
		putc((offset & 0x00ff0000) / 65536,fptr);
		putc((offset & 0x0000ff00) / 256,fptr);
		putc((offset & 0x000000ff),fptr);

		/* Minimum sample value flag, short int */
		WriteHexString(fptr,"0118000300000003");
		offset = nx * ny * 3 + 188;
		putc((offset & 0xff000000) / 16777216,fptr);
		putc((offset & 0x00ff0000) / 65536,fptr);
		putc((offset & 0x0000ff00) / 256,fptr);
		putc((offset & 0x000000ff),fptr);

		/* Maximum sample value tag, short int */
		WriteHexString(fptr,"0119000300000003");
		offset = nx * ny * 3 + 194;
		putc((offset & 0xff000000) / 16777216,fptr);
		putc((offset & 0x00ff0000) / 65536,fptr);
		putc((offset & 0x0000ff00) / 256,fptr);
		putc((offset & 0x000000ff),fptr);

		/* Planar configuration tag, short int */
		WriteHexString(fptr,"011c00030000000100010000");

		/* Sample format tag, short int */
		WriteHexString(fptr,"0153000300000003");
		offset = nx * ny * 3 + 200;
		putc((offset & 0xff000000) / 16777216,fptr);
		putc((offset & 0x00ff0000) / 65536,fptr);
		putc((offset & 0x0000ff00) / 256,fptr);
		putc((offset & 0x000000ff),fptr);

		/* End of the directory entry */
		WriteHexString(fptr,"00000000");

		/* Bits for each colour channel */
		WriteHexString(fptr,"000800080008");

		/* Minimum value for each component */
		WriteHexString(fptr,"000000000000");

		/* Maximum value per channel */
		WriteHexString(fptr,"00ff00ff00ff");

		/* Samples per pixel for each channel */
		WriteHexString(fptr,"000100010001");

		fclose(fptr);
	}
	else
	{
		retval = 1; // failed to open the file
	}
	return retval;
}

// S = greyScale
// R 
// G 
// B

void GenerateRGBTestImage( const int nx, const int ny, const char color, unsigned char* pdata )
{
    int i,j;
    double irmax = 1./sqrt( double(nx*nx + ny*ny) );

    /* Write the binary data */
    for (j=0;j<ny;j++) {
        for (i=0;i<nx;i++) {

            double r = sqrt((double)( (nx-i)*(nx-i) + (ny-j)*(ny-j)));

            unsigned char value = (unsigned char)(0.5 + 255. * r * irmax );
            unsigned char red, green, blue;
            red = 0;
            green = 0;
            blue = 0;

            if ( 'S' == color)
            {
                red = value;
                green = value;
                blue = value;
            }
            else if( 'R' == color)
            {
                red = value;
            }
            else if( 'G' == color)
            {
                green = value;
            }
            else if ( 'B' == color)
            {
                blue = value;
            }
            *pdata++ = red;
            *pdata++ = green;
            *pdata++ = blue;
        }
    }
}



