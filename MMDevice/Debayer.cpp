///////////////////////////////////////////////////////////////////////////////
// MODULE:			Debayer.h
// SYSTEM:        ImageBase subsystem
// AUTHOR:			Jennifer West, jennifer_west@umanitoba.ca,
//                Nenad Amodaj, nenad@amodaj.com
//
// DESCRIPTION:	Debayer algorithms, adapted from:
//                http://www.umanitoba.ca/faculties/science/astronomy/jwest/plugins.html
//                
//
// COPYRIGHT:     Jennifer West (University of Manitoba),
//                Exploratorium http://www.exploratorium.edu
//
// LICENSE:       This file is free for use, modification and distribution and
//                is distributed under terms specified in the BSD license
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
///////////////////////////////////////////////////////////////////////////////

#include "Debayer.h"
#include <math.h>
#include <assert.h>
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Debayer class implementation
//
///////////////////////////////////////////////////////////////////////////////
Debayer::Debayer()
{

}

Debayer::~Debayer()
{
}

int Debayer::Process(ImgBuffer& out, const ImgBuffer& input, int bitDepth)
{
   // TODO: create better error message
   if (input.Depth() != 2)
      return DEVICE_UNSUPPORTED_DATA_FORMAT;

   out.Resize(input.Width(), input.Height(), 4);
   assert(sizeof(int) == 4);
   const unsigned short* inBuf = reinterpret_cast<const unsigned short*>(input.GetPixels());
   int* outBuf = reinterpret_cast<int*>(out.GetPixelsRW());

   return Convert(inBuf, outBuf, input.Width(), input.Height(), bitDepth, 0, 0);
}

int Debayer::Process(ImgBuffer& out, const unsigned short* in, int width, int height, int bitDepth)
{
   out.Resize(width, height, 4);
   assert(sizeof(int) == 4);
   const unsigned short* inBuf = reinterpret_cast<const unsigned short*>(in);
   int* outBuf = reinterpret_cast<int*>(out.GetPixelsRW());

   return Convert(inBuf, outBuf, width, height, bitDepth, 0, 0);
}


int Debayer::Convert(const unsigned short* input, int* output, int width, int height, int bitDepth, int rowOrder, int algorithm)
{
   //const char* orders[] = {"R-G-R-G", "B-G-B-G", "G-R-G-R", "G-B-G-B"};
	//const char* algorithms[] = {"Replication", "Bilinear", "Smooth-Hue", "Adaptive-Smooth-Hue"};
				
	if (algorithm == 0)
      ReplicateDecode(input, output, width, height, bitDepth, rowOrder);
	//else if (algorithm == 1) return average_decode(input, output, width, height, rowOrder);
	//else if (algorithm == 2) return smooth_decode(input, output, width, height, rowOrder);
	//else if (algorithm == 3) return adaptive_decode(input, output, width, height, rowOrder);

   return DEVICE_OK;
}

unsigned short Debayer::GetPixel(const unsigned short* v, int x, int y, int width, int height)
{
   if (x >= width || x < 0 || y >= height || y < 0)
      return 0;
   else
      return v[y*width + x];
}

void Debayer::SetPixel(std::vector<unsigned short>& v, unsigned short val, int x, int y, int width, int height)
{
   if (x < width && x >= 0 && y < height && y >= 0)
      v[y*width + x] = val;
}


// Replication algorithm
void Debayer::ReplicateDecode(const unsigned short* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   unsigned numPixels(width*height);
   if (r.size() != numPixels)
   {
      r.resize(numPixels);
      g.resize(numPixels);
      b.resize(numPixels);
   }

   int bitShift = bitDepth - 8;
	
	if (rowOrder == 0 || rowOrder == 1) {
		for (int y=0; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(b, one, x, y, width, height);
				SetPixel(b, one, x+1, y, width, height);
				SetPixel(b, one, x, y+1, width, height); 
				SetPixel(b, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=1; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(r, one, x, y, width, height);
				SetPixel(r, one, x+1, y, width, height);
				SetPixel(r, one, x, y+1, width, height); 
				SetPixel(r, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
			
		for (int y=1; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
		
		if (rowOrder == 0) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(b[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(r[i] >> bitShift);

			   //rgb.addSlice("red",b);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",r);
         }
		}
		else if (rowOrder == 1) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(r[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(b[i] >> bitShift);

			   //rgb.addSlice("red",r);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",b);			
		   }
      }
	}

	else if (rowOrder == 2 || rowOrder == 3) {
		for (int y=1; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(b, one, x, y, width, height);
				SetPixel(b, one, x+1, y, width, height);
				SetPixel(b, one, x, y+1, width, height); 
				SetPixel(b, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(r, one, x, y, width, height);
				SetPixel(r, one, x+1, y, width, height);
				SetPixel(r, one, x, y+1, width, height); 
				SetPixel(r, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
			
		for (int y=1; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
		
		if (rowOrder == 2) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(b[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(r[i] >> bitShift);

            //rgb.addSlice("red",b);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",r);
         }
		}
		else if (rowOrder == 3) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(r[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(b[i] >> bitShift);

            //rgb.addSlice("red",r);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",b);
         }
		}
	}
}

// Smooth Hue algorithm
void Debayer::SmoothDecode(const unsigned short* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   double G1 = 0;
   double G2 = 0;
   double G3 = 0;
   double G4 = 0;
   double G5 = 0;
   double G6 = 0;
   double G7 = 0;
   double G8 = 0;
   double G9 = 0;
   double B1 = 0;
   double B2 = 0;
   double B3 = 0;
   double B4 = 0;
   double R1 = 0;
   double R2 = 0;
   double R3 = 0;
   double R4 = 0;

   unsigned numPixels(width*height);
   if (r.size() != numPixels)
   {
      r.resize(numPixels);
      g.resize(numPixels);
      b.resize(numPixels);
   }

   int bitShift = bitDepth - 8;

   if (rowOrder == 0 || rowOrder == 1) {
      //Solve for green pixels first
      for (int y=0; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (int)G1, x, y, width, height);
            if (y==0)
               SetPixel(g, (int)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (int)((G1+G2+G3+G4)/4.0), x+1, y, width, height);

            if (x==1)
               SetPixel(g, (int)((G1 + G4 + GetPixel(input, x-1, y+1, width, height))/3.0), x-1, y, width, height);
         }
      }	

      for (int x=0; x<width; x+=2) {	
         for (int y=1; y<height; y+=2) {

            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (int)G1, x, y, width, height);
            if (x==0)
               SetPixel(g, (int)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (int)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
         }
      }	

      SetPixel(g, (int)((GetPixel(input, 0, 1, width, height) + GetPixel(input, 1, 0, width, height))/2.0), 0, 0, width, height);

      for (int y=0; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            B1 = GetPixel(input, x, y, width, height);
            B2 = GetPixel(input, x+2, y, width, height);
            B3 = GetPixel(input, x, y+2, width, height);
            B4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x, y+2, width, height);
            G4 = GetPixel(input, x+2, y+2, width, height);;
            G5 = GetPixel(input, x+1, y, width, height);
            G6 = GetPixel(input, x, y+1, width, height);
            G9 = GetPixel(input, x+1, y+1, width, height);
            if (G1==0) G1=1;
            if (G2==0) G2=1;
            if (G3==0) G3=1;
            if (G4==0) G4=1;

            SetPixel(b, (int)B1, x, y, width, height);
            //b.putPixel(x+1,y,(int)((G5/2 * ((B1/G1) + (B2/G2)) )) );
            SetPixel(b, (int)((G5/2 * ((B1/G1) + (B2/G2)) )), x+1, y, width, height);
            //b.putPixel(x,y+1,(int)(( G6/2 * ((B1/G1) + (B3/G3)) )) );
            SetPixel(b, (int)((G6/2 * ((B1/G1) + (B3/G3)) )), x, y+1, width, height);
            //b.putPixel(x+1,y+1, (int)((G9/4 *  ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )) );
            SetPixel(b, (int)((G9/4 * ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )), x+1, y+1, width, height);
         }
      }

      for (int y=1; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            R1 = GetPixel(input, x, y, width, height);
            R2 = GetPixel(input, x+2, y, width, height);
            R3 = GetPixel(input, x, y+2, width, height);
            R4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x, y+2, width, height);
            G4 = GetPixel(input, x+2, y+2, width, height);
            G5 = GetPixel(input, x+1, y, width, height);
            G6 = GetPixel(input, x, y+1, width, height);
            G9 = GetPixel(input, x+1, y+1, width, height);
            if(G1==0) G1=1;
            if(G2==0) G2=1;
            if(G3==0) G3=1;
            if(G4==0) G4=1;

            //r.putPixel(x,y,(int)(R1));
            SetPixel(r, (int)R1, x, y, width, height);
            //r.putPixel(x+1,y,(int)((G5/2 * ((R1/G1) + (R2/G2) )) ));
            SetPixel(r, (int)((G5/2 * ((R1/G1) + (R2/G2) )) ), x+1, y, width, height);
            //r.putPixel(x,y+1,(int)(( G6/2 * ((R1/G1) + (R3/G3) )) ));
            SetPixel(r, (int)(( G6/2 * ((R1/G1) + (R3/G3) )) ), x, y+1, width, height);
            //r.putPixel(x+1,y+1, (int)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ));
            SetPixel(r, (int)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ), x+1, y+1, width, height);
         }
      }


      if (rowOrder == 0) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(b[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(r[i] >> bitShift);

            //rgb.addSlice("red",b);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",r);
         }
      }
      else if (rowOrder == 1) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(r[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(b[i] >> bitShift);

            //rgb.addSlice("red",r);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",b);			
         }
      }
   }

   else if (rowOrder == 2 || rowOrder == 3) {

      for (int y=0; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (int)G1, x, y, width, height);
            if (y==0)
               SetPixel(g, (int)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (int)((G1+G2+G3+G4)/4.0), x+1, y, width, height);

            if (x==1)
               SetPixel(g, (int)((G1+G4+GetPixel(input, x-1, y+1, width, height))/3.0), x-1, y, width, height);
         }
      }	

      for (int y=1; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (int)G1, x, y, width, height);
            if (x==0)
               SetPixel(g, (int)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (int)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
         }
      }

      SetPixel(g, (int)((GetPixel(input, 0, 1, width, height) + GetPixel(input, 1, 0, width, height))/2.0), 0, 0, width, height);

      for (int y=1; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            B1 = GetPixel(input, x, y, width, height);
            B2 = GetPixel(input, x+2, y, width, height);
            B3 = GetPixel(input, x, y+2, width, height);
            B4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x, y+2, width, height);
            G4 = GetPixel(input, x+2, y+2, width, height);;
            G5 = GetPixel(input, x+1, y, width, height);
            G6 = GetPixel(input, x, y+1, width, height);
            G9 = GetPixel(input, x+1, y+1, width, height);
            if (G1==0) G1=1;
            if (G2==0) G2=1;
            if (G3==0) G3=1;
            if (G4==0) G4=1;

            SetPixel(b, (int)B1, x, y, width, height);
            SetPixel(b, (int)((G5/2 * ((B1/G1) + (B2/G2)) )), x+1, y, width, height);
            SetPixel(b, (int)((G6/2 * ((B1/G1) + (B3/G3)) )), x, y+1, width, height);
            SetPixel(b, (int)((G9/4 * ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )), x+1, y+1, width, height);
         }
      }

      for (int y=0; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            R1 = GetPixel(input, x, y, width, height);
            R2 = GetPixel(input, x+2, y, width, height);
            R3 = GetPixel(input, x, y+2, width, height);
            R4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x, y+2, width, height);
            G4 = GetPixel(input, x+2, y+2, width, height);
            G5 = GetPixel(input, x+1, y, width, height);
            G6 = GetPixel(input, x, y+1, width, height);
            G9 = GetPixel(input, x+1, y+1, width, height);
            if(G1==0) G1=1;
            if(G2==0) G2=1;
            if(G3==0) G3=1;
            if(G4==0) G4=1;

            //r.putPixel(x,y,(int)(R1));
            SetPixel(r, (int)R1, x, y, width, height);
            //r.putPixel(x+1,y,(int)((G5/2 * ((R1/G1) + (R2/G2) )) ));
            SetPixel(r, (int)((G5/2 * ((R1/G1) + (R2/G2) )) ), x+1, y, width, height);
            //r.putPixel(x,y+1,(int)(( G6/2 * ((R1/G1) + (R3/G3) )) ));
            SetPixel(r, (int)(( G6/2 * ((R1/G1) + (R3/G3) )) ), x, y+1, width, height);
            //r.putPixel(x+1,y+1, (int)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ));
            SetPixel(r, (int)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ), x+1, y+1, width, height);
         }
      }



      if (rowOrder == 2) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(b[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(r[i] >> bitShift);

            //rgb.addSlice("red",b);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",r);
         }
      }
      else if (rowOrder == 3) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(r[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(b[i] >> bitShift);

            //rgb.addSlice("red",r);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",b);			
         }
      }
   }
}
