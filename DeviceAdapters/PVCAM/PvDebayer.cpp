///////////////////////////////////////////////////////////////////////////////
// MODULE:			PvDebayer.h
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

#include "PvDebayer.h"
#include <math.h>
#include <assert.h>
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Debayer class implementation
///////////////////////////////////////////////////////////////////////////////


PvDebayer::PvDebayer()
{
   orders.push_back("R-G-R-G");
   orders.push_back("B-G-B-G");
   orders.push_back("G-R-G-R");
   orders.push_back("G-B-G-B");

   algorithms.push_back("Replication");
   algorithms.push_back("Bilinear");
   algorithms.push_back("Smooth-Hue");
   algorithms.push_back("Adaptive-Smooth-Hue");
   
   // default settings
   orderIndex = CFA_RGGB; 
   algoIndex = ALG_REPLICATION;
}

PvDebayer::~PvDebayer()
{
}

int PvDebayer::Process(ImgBuffer& out, const ImgBuffer& input, int bitDepth)
{
   assert(sizeof(int) == 4);

   int byteDepth = input.Depth();
   if (bitDepth > byteDepth * 8)
   {
      assert(false);
      return DEVICE_INVALID_INPUT_PARAM;
   }

   out.Resize(input.Width(), input.Height(), 4);
   if (input.Depth() == 1)
   {
      const unsigned char* inBuf = input.GetPixels();
      return ProcessT(out, inBuf, input.Width(), input.Height(), bitDepth);
   }
   else if (input.Depth() == 2)
   {
      const unsigned short* inBuf = reinterpret_cast<const unsigned short*>(input.GetPixels());
      return ProcessT(out, inBuf, input.Width(), input.Height(), bitDepth);
   }
   else
      return DEVICE_UNSUPPORTED_DATA_FORMAT;

}

int PvDebayer::Process(ImgBuffer& out, const unsigned char* in, int width, int height, int bitDepth)
{ return ProcessT(out, in, width, height, bitDepth); }

int PvDebayer::Process(ImgBuffer& out, const unsigned short* in, int width, int height, int bitDepth)
{ return ProcessT(out, in, width, height, bitDepth); }

template <typename T>
int PvDebayer::ProcessT(ImgBuffer& out, const T* in, int width, int height, int bitDepth)
{
   assert(sizeof(int) == 4);
   out.Resize(width, height, 4);
   int* outBuf = reinterpret_cast<int*>(out.GetPixelsRW());

   // TODO: There seems to be bug somewhere in the conversion routine. When Binning is switched from 1x1 to 2x2 or other
   // the image cannot be debayered (obviously), however when switching back to 1x1 the blue channel seems to contain
   // some incorrect values from the 2x2 binned image. After adding the clear() calls below the issue disappeared.
   // This suggests that the debayering algorithm preserves some data from previous frames and tampers the current frame.
   // TODO: The algorithm itself is very inefficient, we should consider using OpenCV or at least revisit the algorithms,
   // at least the SetPixel(), GetPixel() and all the loops using vectors needs to be improved.
   // (remove conditions from Get,SetPixel, avoid using std::vector)
   r.clear();
   g.clear();
   b.clear();

   return Convert(in, outBuf, width, height, bitDepth, orderIndex, algoIndex);
}

template<typename T>
int PvDebayer::Convert(const T* input, int* output, int width, int height, int bitDepth, int rowOrder, int algorithm)
{			
    if (algorithm == ALG_REPLICATION)
       ReplicateDecode(input, output, width, height, bitDepth, rowOrder);
    else if (algorithm == ALG_BILINEAR)
      BilinearDecode(input, output, width, height, bitDepth, rowOrder);
	else if (algorithm == ALG_SMOOTH_HUE)
      SmoothDecode(input, output, width, height, bitDepth, rowOrder);
	else if (algorithm == ALG_ADAPTIVE_SMOOTH_HUE)
      AdaptiveSmoothDecode(input, output, width, height, bitDepth, rowOrder);
   else
      return DEVICE_NOT_SUPPORTED;


   return DEVICE_OK;
}

unsigned short PvDebayer::GetPixel(const unsigned short* v, int x, int y, int width, int height)
{
   if (x >= width || x < 0 || y >= height || y < 0)
      return 0;
   else
      return v[y*width + x];
}

void PvDebayer::SetPixel(std::vector<unsigned short>& v, unsigned short val, int x, int y, int width, int height)
{
   if (x < width && x >= 0 && y < height && y >= 0)
      v[y*width + x] = val;
}

unsigned short PvDebayer::GetPixel(const unsigned char* v, int x, int y, int width, int height)
{
   if (x >= width || x < 0 || y >= height || y < 0)
      return 0;
   else
      return v[y*width + x];
}

// Replication algorithm
template <typename T>
void PvDebayer::ReplicateDecode(const T* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   unsigned numPixels(width*height);
   if (r.size() != numPixels)
   {
      r.resize(numPixels);
      g.resize(numPixels);
      b.resize(numPixels);
   }

   int bitShift = bitDepth - 8;
	
   if (rowOrder == CFA_RGGB || rowOrder == CFA_BGGR) {
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
		
		if (rowOrder == CFA_RGGB) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
           
            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);
            //rgb.addSlice("red",b);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",r);
         }
		}
        else if (rowOrder == CFA_BGGR) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            
            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

			//rgb.addSlice("red",r);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",b);			
		 }
      }
	}
    else if (rowOrder == CFA_GRBG || rowOrder == CFA_GBRG) {
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
        if (rowOrder == CFA_GRBG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",b);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",r);
         }


		}
        else if (rowOrder == CFA_GBRG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
  
            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",r);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",b);
         }
		}
	}
}


// Bilinear algorithm
template <typename T>
void PvDebayer::BilinearDecode(const T* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   unsigned numPixels(width*height);
   if (r.size() != numPixels)
   {
      r.resize(numPixels);
      g.resize(numPixels);
      b.resize(numPixels);
   }

   int one, two, three, four;
   one = two = three = four = 0;

   int bitShift = bitDepth - 8;
	
   if (rowOrder == CFA_RGGB || rowOrder == CFA_BGGR) {
		for (int y=0; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x, y+2, width, height);
                four = GetPixel(input, x+2, y+2, width, height);
				                
                SetPixel(b, (unsigned short)one, x, y, width, height);
				SetPixel(b, (unsigned short)((one+two)/2.0), x+1, y, width, height);
				SetPixel(b, (unsigned short)((one+three)/2.0), x, y+1, width, height); 
				SetPixel(b, (unsigned short)((one+two+three+four)/4.0), x+1, y+1, width, height);
			}
		}
		
		for (int y=1; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x, y+2, width, height);
                four = GetPixel(input, x+2, y+2, width, height);

				SetPixel(r, (unsigned short)one, x, y, width, height);
				SetPixel(r, (unsigned short)((one+two)/2.0), x+1, y, width, height);
				SetPixel(r, (unsigned short)((one+three)/2.0), x, y+1, width, height); 
				SetPixel(r, (unsigned short)((one+two+three+four)/4.0), x+1, y+1, width, height);
			}
		}

        for (int y=0; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x+1, y+1, width, height);
                four = GetPixel(input, x+1, y-1, width, height);
				                
                SetPixel(g, (unsigned short)one, x, y, width, height);
				SetPixel(g, (unsigned short)((one+two+three+four)/4.0), x+1, y, width, height);
			}
		}

        for (int y=1; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x+1, y+1, width, height);
                four = GetPixel(input, x+1, y-1, width, height);
				                
                SetPixel(g, (unsigned short)one, x, y, width, height);
				SetPixel(g, (unsigned short)((one+two+three+four)/4.0), x+1, y, width, height);
			}
		}

        if (rowOrder == CFA_RGGB) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

        	//rgb.addSlice("red",b);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",r);
         }
		}
        else if (rowOrder == CFA_BGGR) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",r);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",b);			
		 }
      }
    }
    else if (rowOrder == CFA_GRBG || rowOrder == CFA_GBRG) {
		for (int y=1; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x, y+2, width, height);
                four = GetPixel(input, x+2, y+2, width, height);
				                
                SetPixel(b, (unsigned short)one, x, y, width, height);
				SetPixel(b, (unsigned short)((one+two)/2.0), x+1, y, width, height);
				SetPixel(b, (unsigned short)((one+three)/2.0), x, y+1, width, height); 
				SetPixel(b, (unsigned short)((one+two+three+four)/4.0), x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x, y+2, width, height);
                four = GetPixel(input, x+2, y+2, width, height);

				SetPixel(r, (unsigned short)one, x, y, width, height);
				SetPixel(r, (unsigned short)((one+two)/2.0), x+1, y, width, height);
				SetPixel(r, (unsigned short)((one+three)/2.0), x, y+1, width, height); 
				SetPixel(r, (unsigned short)((one+two+three+four)/4.0), x+1, y+1, width, height);
			}
		}

        for (int y=0; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x+1, y+1, width, height);
                four = GetPixel(input, x+1, y-1, width, height);
				                
                SetPixel(g, (unsigned short)one, x, y, width, height);
				SetPixel(g, (unsigned short)((one+two+three+four)/4.0), x+1, y, width, height);
			}
		}

        for (int y=1; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
                one = GetPixel(input, x, y, width, height);
                two = GetPixel(input, x+2, y, width, height);
                three = GetPixel(input, x+1, y+1, width, height);
                four = GetPixel(input, x+1, y-1, width, height);
				                
                SetPixel(g, (unsigned short)one, x, y, width, height);
				SetPixel(g, (unsigned short)((one+two+three+four)/4.0), x+1, y, width, height);
			}
		}

        if (rowOrder == CFA_GRBG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            
            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);
            
           	//rgb.addSlice("red",b);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",r);
         }
		}
        else if (rowOrder == CFA_GBRG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

			//rgb.addSlice("red",r);	
			//rgb.addSlice("green",g);
			//rgb.addSlice("blue",b);			
		 }
      }
    }





}






// Smooth Hue algorithm
template <typename T>
void PvDebayer::SmoothDecode(const T* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   double G1 = 0;
   double G2 = 0;
   double G3 = 0;
   double G4 = 0;
   double G5 = 0;
   double G6 = 0;
   //double G7 = 0;
   //double G8 = 0;
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

   if (rowOrder == CFA_RGGB || rowOrder == CFA_BGGR) {
      //Solve for green pixels first
      for (int y=0; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (unsigned short)G1, x, y, width, height);
            if (y==0)
               SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);

            if (x==1)
               SetPixel(g, (unsigned short)((G1 + G4 + GetPixel(input, x-1, y+1, width, height))/3.0), x-1, y, width, height);
         }
      }	

      for (int x=0; x<width; x+=2) {	
         for (int y=1; y<height; y+=2) {

            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (unsigned short)G1, x, y, width, height);
            if (x==0)
               SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
         }
      }	

      SetPixel(g, (unsigned short)((GetPixel(input, 0, 1, width, height) + GetPixel(input, 1, 0, width, height))/2.0), 0, 0, width, height);

      for (int y=0; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            B1 = GetPixel(input, x, y, width, height);
            B2 = GetPixel(input, x+2, y, width, height);
            B3 = GetPixel(input, x, y+2, width, height);
            B4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((unsigned short *)g.data(), x+2, y+2, width, height);;
            G5 = GetPixel((unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((unsigned short *)g.data(), x+1, y+1, width, height);
            if (G1==0) G1=1;
            if (G2==0) G2=1;
            if (G3==0) G3=1;
            if (G4==0) G4=1;

            SetPixel(b, (unsigned short)B1, x, y, width, height);
            //b.putPixel(x+1,y,(int)((G5/2 * ((B1/G1) + (B2/G2)) )) );
            SetPixel(b, (unsigned short)((G5/2 * ((B1/G1) + (B2/G2)) )), x+1, y, width, height);
            //b.putPixel(x,y+1,(int)(( G6/2 * ((B1/G1) + (B3/G3)) )) );
            SetPixel(b, (unsigned short)((G6/2 * ((B1/G1) + (B3/G3)) )), x, y+1, width, height);
            //b.putPixel(x+1,y+1, (int)((G9/4 *  ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )) );
            SetPixel(b, (unsigned short)((G9/4 * ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )), x+1, y+1, width, height);
         }
      }

      for (int y=1; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            R1 = GetPixel(input, x, y, width, height);
            R2 = GetPixel(input, x+2, y, width, height);
            R3 = GetPixel(input, x, y+2, width, height);
            R4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((unsigned short *)g.data(), x+2, y+2, width, height);
            G5 = GetPixel((unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((unsigned short *)g.data(), x+1, y+1, width, height);
            if(G1==0) G1=1;
            if(G2==0) G2=1;
            if(G3==0) G3=1;
            if(G4==0) G4=1;

            //r.putPixel(x,y,(int)(R1));
            SetPixel(r, (unsigned short)R1, x, y, width, height);
            //r.putPixel(x+1,y,(int)((G5/2 * ((R1/G1) + (R2/G2) )) ));
            SetPixel(r, (unsigned short)((G5/2 * ((R1/G1) + (R2/G2) )) ), x+1, y, width, height);
            //r.putPixel(x,y+1,(int)(( G6/2 * ((R1/G1) + (R3/G3) )) ));
            SetPixel(r, (unsigned short)(( G6/2 * ((R1/G1) + (R3/G3) )) ), x, y+1, width, height);
            //r.putPixel(x+1,y+1, (int)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ));
            SetPixel(r, (unsigned short)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ), x+1, y+1, width, height);
         }
      }


      if (rowOrder == CFA_RGGB) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            
            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",b);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",r);
         }
      }
      else if (rowOrder == CFA_BGGR) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",r);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",b);			
         }
      }
   }

   else if (rowOrder == CFA_GRBG || rowOrder == CFA_GBRG) {

      for (int y=0; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (unsigned short)G1, x, y, width, height);
            if (y==0)
               SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);

            if (x==1)
               SetPixel(g, (unsigned short)((G1+G4+GetPixel(input, x-1, y+1, width, height))/3.0), x-1, y, width, height);
         }
      }	

      for (int y=1; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);

            SetPixel(g, (unsigned short)G1, x, y, width, height);
            if (x==0)
               SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            else
               SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
         }
      }

      SetPixel(g, (unsigned short)((GetPixel(input, 0, 1, width, height) + GetPixel(input, 1, 0, width, height))/2.0), 0, 0, width, height);

      for (int y=1; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            B1 = GetPixel(input, x, y, width, height);
            B2 = GetPixel(input, x+2, y, width, height);
            B3 = GetPixel(input, x, y+2, width, height);
            B4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((const unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((const unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((const unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((const unsigned short *)g.data(), x+2, y+2, width, height);;
            G5 = GetPixel((const unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((const unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((const unsigned short *)g.data(), x+1, y+1, width, height);
            if (G1==0) G1=1;
            if (G2==0) G2=1;
            if (G3==0) G3=1;
            if (G4==0) G4=1;

            SetPixel(b, (unsigned short)B1, x, y, width, height);
            SetPixel(b, (unsigned short)((G5/2 * ((B1/G1) + (B2/G2)) )), x+1, y, width, height);
            SetPixel(b, (unsigned short)((G6/2 * ((B1/G1) + (B3/G3)) )), x, y+1, width, height);
            SetPixel(b, (unsigned short)((G9/4 * ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )), x+1, y+1, width, height);
         }
      }

      for (int y=0; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            R1 = GetPixel(input, x, y, width, height);
            R2 = GetPixel(input, x+2, y, width, height);
            R3 = GetPixel(input, x, y+2, width, height);
            R4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((const unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((const unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((const unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((const unsigned short *)g.data(), x+2, y+2, width, height);
            G5 = GetPixel((const unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((const unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((const unsigned short *)g.data(), x+1, y+1, width, height);
            if(G1==0) G1=1;
            if(G2==0) G2=1;
            if(G3==0) G3=1;
            if(G4==0) G4=1;

            //r.putPixel(x,y,(int)(R1));
            SetPixel(r, (unsigned short)R1, x, y, width, height);
            //r.putPixel(x+1,y,(int)((G5/2 * ((R1/G1) + (R2/G2) )) ));
            SetPixel(r, (unsigned short)((G5/2 * ((R1/G1) + (R2/G2) )) ), x+1, y, width, height);
            //r.putPixel(x,y+1,(int)(( G6/2 * ((R1/G1) + (R3/G3) )) ));
            SetPixel(r, (unsigned short)(( G6/2 * ((R1/G1) + (R3/G3) )) ), x, y+1, width, height);
            //r.putPixel(x+1,y+1, (int)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ));
            SetPixel(r, (unsigned short)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ), x+1, y+1, width, height);
         }
      }



      if (rowOrder == CFA_GRBG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",b);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",r);
         }
      }
      else if (rowOrder == CFA_GBRG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",r);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",b);			
         }
      }
   }
}


// Adaptive Smooth Hue algorithm (edge detecting)
template <typename T>
void PvDebayer::AdaptiveSmoothDecode(const T* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   double G1 = 0;
   double G2 = 0;
   double G3 = 0;
   double G4 = 0;
   double G5 = 0;
   double G6 = 0;
   //double G7 = 0;
   //double G8 = 0;
   double G9 = 0;
   double B1 = 0;
   double B2 = 0;
   double B3 = 0;
   double B4 = 0;
   //double B5 = 0;
   double R1 = 0;
   double R2 = 0;
   double R3 = 0;
   double R4 = 0;
   double R5 = 0;
   double N = 0;
   double S = 0;
   double E = 0;
   double W = 0;

   unsigned numPixels(width*height);
   if (r.size() != numPixels)
   {
      r.resize(numPixels);
      g.resize(numPixels);
      b.resize(numPixels);
   }

   int bitShift = bitDepth - 8;

   if (rowOrder == CFA_RGGB || rowOrder == CFA_BGGR) {
      //Solve for green pixels first
      for (int y=0; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);
            R1 = GetPixel(input, x-1, y, width, height);
            R2 = GetPixel(input, x+3, y, width, height);
            R3 = GetPixel(input, x+1, y+2, width, height);
            R4 = GetPixel(input, x+1, y-2, width, height);
            R5 = GetPixel(input, x+1, y+1, width, height);

            N = fabs(R4-R5)*2 + fabs(G4-G3);
            S = fabs(R5-R3)*2 + fabs(G4-G3);
			E = fabs(R5-R2)*2 + fabs(G1-G2);
			W = fabs(R1-R5)*2 + fabs(G1-G2);

            if(N<S && N<E && N<W) 
            {
                SetPixel(g, (unsigned short)((G4*3 + R5 + G3 - R4)/4.0), x+1, y, width, height);
            }
            else if(S<N && S<E && S<W) 
            {
                SetPixel(g, (unsigned short)((G3*3 + R5 + G4 - R3)/4.0), x+1, y, width, height);
			}
			else if(W<N && W<E && W<S) 
            {
			    SetPixel(g, (unsigned short)((G1*3 + R5 + G2 - R1)/4.0), x+1, y, width, height);
            }
            else if(E<N && E<S && E<W) 
            {
			    SetPixel(g, (unsigned short)((G2*3 + R5 + G1 - R2)/4.0), x+1, y, width, height);
		    }
					
		    SetPixel(g, (unsigned short)G1, x, y, width, height);

            if (y==0)
            {
                SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            }
            else
            {
                SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
            }

            if (x==1)
            {
                SetPixel(g, (unsigned short)((G1+G4+GetPixel(input, x-1,y+1, width, height))/3.0), x-1, y, width, height);
            }


         }
      }	

      for (int x=0; x<width; x+=2) {	
         for (int y=1; y<height; y+=2) {

            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);
            R1 = GetPixel(input, x-1, y, width, height);
            R2 = GetPixel(input, x+3, y, width, height);
            R3 = GetPixel(input, x+1, y+2, width, height);
            R4 = GetPixel(input, x+1, y-2, width, height);
            R5 = GetPixel(input, x+1, y+1, width, height);

            N = fabs(R4-R5)*2 + fabs(G4-G3);
            S = fabs(R5-R3)*2 + fabs(G4-G3);
			E = fabs(R5-R2)*2 + fabs(G1-G2);
			W = fabs(R1-R5)*2 + fabs(G1-G2);

            if(N<S && N<E && N<W) 
            {
                SetPixel(g, (unsigned short)((G4*3 + R5 + G3 - R4)/4.0), x+1, y, width, height);
            }
            else if(S<N && S<E && S<W) 
            {
                SetPixel(g, (unsigned short)((G3*3 + R5 + G4 - R3)/4.0), x+1, y, width, height);
			}
			else if(W<N && W<E && W<S) 
            {
			    SetPixel(g, (unsigned short)((G1*3 + R5 + G2 - R1)/4.0), x+1, y, width, height);
            }
            else if(E<N && E<S && E<W) 
            {
			    SetPixel(g, (unsigned short)((G2*3 + R5 + G1 - R2)/4.0), x+1, y, width, height);
		    }
					
		    SetPixel(g, (unsigned short)G1, x, y, width, height);

            if (x==0)
            {
                SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            }
            else
            {
                SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
            }
         }
      }
      SetPixel(g, (unsigned short)((GetPixel(input, 0, 1, width, height) + GetPixel(input, 1, 0, width, height))/2.0), 0, 0, width, height);


      for (int y=0; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            B1 = GetPixel(input, x, y, width, height);
            B2 = GetPixel(input, x+2, y, width, height);
            B3 = GetPixel(input, x, y+2, width, height);
            B4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((unsigned short *)g.data(), x+2, y+2, width, height);;
            G5 = GetPixel((unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((unsigned short *)g.data(), x+1, y+1, width, height);
            if (G1==0) G1=1;
            if (G2==0) G2=1;
            if (G3==0) G3=1;
            if (G4==0) G4=1;

            SetPixel(b, (unsigned short)B1, x, y, width, height);
            SetPixel(b, (unsigned short)((G5/2 * ((B1/G1) + (B2/G2)) )), x+1, y, width, height);
            SetPixel(b, (unsigned short)((G6/2 * ((B1/G1) + (B3/G3)) )), x, y+1, width, height);
            SetPixel(b, (unsigned short)((G9/4 * ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )), x+1, y+1, width, height);
         }
      }

      for (int y=1; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            R1 = GetPixel(input, x, y, width, height);
            R2 = GetPixel(input, x+2, y, width, height);
            R3 = GetPixel(input, x, y+2, width, height);
            R4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((unsigned short *)g.data(), x+2, y+2, width, height);
            G5 = GetPixel((unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((unsigned short *)g.data(), x+1, y+1, width, height);
            if(G1==0) G1=1;
            if(G2==0) G2=1;
            if(G3==0) G3=1;
            if(G4==0) G4=1;

            SetPixel(r, (unsigned short)R1, x, y, width, height);
            SetPixel(r, (unsigned short)((G5/2 * ((R1/G1) + (R2/G2) )) ), x+1, y, width, height);
            SetPixel(r, (unsigned short)((G6/2 * ((R1/G1) + (R3/G3) )) ), x, y+1, width, height);
            SetPixel(r, (unsigned short)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ), x+1, y+1, width, height);
         }
      }


      if (rowOrder == CFA_RGGB) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",b);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",r);
         }
      }
      else if (rowOrder == CFA_BGGR) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",r);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",b);			
         }
      }
   }
   else if (rowOrder == CFA_GRBG || rowOrder == CFA_GBRG) {
       for (int y=0; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);
            R1 = GetPixel(input, x-1, y, width, height);
            R2 = GetPixel(input, x+3, y, width, height);
            R3 = GetPixel(input, x+1, y+2, width, height);
            R4 = GetPixel(input, x+1, y-2, width, height);
            R5 = GetPixel(input, x+1, y+1, width, height);

            N = fabs(R4-R5)*2 + fabs(G4-G3);
            S = fabs(R5-R3)*2 + fabs(G4-G3);
			E = fabs(R5-R2)*2 + fabs(G1-G2);
			W = fabs(R1-R5)*2 + fabs(G1-G2);

            if(N<S && N<E && N<W) 
            {
                SetPixel(g, (unsigned short)((G4*3 + R5 + G3 - R4)/4.0), x+1, y, width, height);
            }
            else if(S<N && S<E && S<W) 
            {
                SetPixel(g, (unsigned short)((G3*3 + R5 + G4 - R3)/4.0), x+1, y, width, height);
			}
			else if(W<N && W<E && W<S) 
            {
			    SetPixel(g, (unsigned short)((G1*3 + R5 + G2 - R1)/4.0), x+1, y, width, height);
            }
            else if(E<N && E<S && E<W) 
            {
			    SetPixel(g, (unsigned short)((G2*3 + R5 + G1 - R2)/4.0), x+1, y, width, height);
		    }
					
		    SetPixel(g, (unsigned short)G1, x, y, width, height);

            if (y==0)
            {
                SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            }
            else
            {
                SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
            }

            if (x==1)
            {
                SetPixel(g, (unsigned short)((G1+G4+GetPixel(input, x-1,y+1, width, height))/3.0), x-1, y, width, height);
            }


         }
      }	

      for (int y=1; y<height; y+=2) {	
          for (int x=1; x<width; x+=2) {

            G1 = GetPixel(input, x, y, width, height);
            G2 = GetPixel(input, x+2, y, width, height);
            G3 = GetPixel(input, x+1, y+1, width, height);
            G4 = GetPixel(input, x+1, y-1, width, height);
            R1 = GetPixel(input, x-1, y, width, height);
            R2 = GetPixel(input, x+3, y, width, height);
            R3 = GetPixel(input, x+1, y+2, width, height);
            R4 = GetPixel(input, x+1, y-2, width, height);
            R5 = GetPixel(input, x+1, y+1, width, height);

            N = fabs(R4-R5)*2 + fabs(G4-G3);
            S = fabs(R5-R3)*2 + fabs(G4-G3);
			E = fabs(R5-R2)*2 + fabs(G1-G2);
			W = fabs(R1-R5)*2 + fabs(G1-G2);

            if(N<S && N<E && N<W) 
            {
                SetPixel(g, (unsigned short)((G4*3 + R5 + G3 - R4)/4.0), x+1, y, width, height);
            }
            else if(S<N && S<E && S<W) 
            {
                SetPixel(g, (unsigned short)((G3*3 + R5 + G4 - R3)/4.0), x+1, y, width, height);
			}
			else if(W<N && W<E && W<S) 
            {
			    SetPixel(g, (unsigned short)((G1*3 + R5 + G2 - R1)/4.0), x+1, y, width, height);
            }
            else if(E<N && E<S && E<W) 
            {
			    SetPixel(g, (unsigned short)((G2*3 + R5 + G1 - R2)/4.0), x+1, y, width, height);
		    }
					
		    SetPixel(g, (unsigned short)G1, x, y, width, height);

            if (x==0)
            {
                SetPixel(g, (unsigned short)((G1+G2+G3)/3.0), x+1, y, width, height);
            }
            else
            {
                SetPixel(g, (unsigned short)((G1+G2+G3+G4)/4.0), x+1, y, width, height);
            }
         }
      }
      SetPixel(g, (unsigned short)((GetPixel(input, 0, 1, width, height) + GetPixel(input, 1, 0, width, height))/2.0), 0, 0, width, height);


      for (int y=1; y<height; y+=2) {
         for (int x=0; x<width; x+=2) {
            B1 = GetPixel(input, x, y, width, height);
            B2 = GetPixel(input, x+2, y, width, height);
            B3 = GetPixel(input, x, y+2, width, height);
            B4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((unsigned short *)g.data(), x+2, y+2, width, height);
            G5 = GetPixel((unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((unsigned short *)g.data(), x+1, y+1, width, height);
            if (G1==0) G1=1;
            if (G2==0) G2=1;
            if (G3==0) G3=1;
            if (G4==0) G4=1;

            SetPixel(b, (unsigned short)B1, x, y, width, height);
            SetPixel(b, (unsigned short)((G5/2 * ((B1/G1) + (B2/G2)) )), x+1, y, width, height);
            SetPixel(b, (unsigned short)((G6/2 * ((B1/G1) + (B3/G3)) )), x, y+1, width, height);
            SetPixel(b, (unsigned short)((G9/4 * ((B1/G1) + (B3/G3) + (B2/G2) + (B4/G4)) )), x+1, y+1, width, height);
         }
      }

      for (int y=0; y<height; y+=2) {
         for (int x=1; x<width; x+=2) {
            R1 = GetPixel(input, x, y, width, height);
            R2 = GetPixel(input, x+2, y, width, height);
            R3 = GetPixel(input, x, y+2, width, height);
            R4 = GetPixel(input, x+2, y+2, width, height);
            G1 = GetPixel((unsigned short *)g.data(), x, y, width, height);
            G2 = GetPixel((unsigned short *)g.data(), x+2, y, width, height);
            G3 = GetPixel((unsigned short *)g.data(), x, y+2, width, height);
            G4 = GetPixel((unsigned short *)g.data(), x+2, y+2, width, height);
            G5 = GetPixel((unsigned short *)g.data(), x+1, y, width, height);
            G6 = GetPixel((unsigned short *)g.data(), x, y+1, width, height);
            G9 = GetPixel((unsigned short *)g.data(), x+1, y+1, width, height);
            if(G1==0) G1=1;
            if(G2==0) G2=1;
            if(G3==0) G3=1;
            if(G4==0) G4=1;

            SetPixel(r, (unsigned short)R1, x, y, width, height);
            SetPixel(r, (unsigned short)((G5/2 * ((R1/G1) + (R2/G2) )) ), x+1, y, width, height);
            SetPixel(r, (unsigned short)((G6/2 * ((R1/G1) + (R3/G3) )) ), x, y+1, width, height);
            SetPixel(r, (unsigned short)((G9/4 *  ((R1/G1) + (R3/G3) + (R2/G2) + (R4/G4)) ) ), x+1, y+1, width, height);
         }
      }


      if (rowOrder == CFA_GRBG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(r[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(b[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",b);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",r);
         }
      }
      else if (rowOrder == CFA_GBRG) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);

            unsigned short bValue = (unsigned short)(rgbScales.b_scale*((unsigned char)(b[i] >> bitShift)));
            unsigned short gValue = (unsigned short)(rgbScales.g_scale*((unsigned char)(g[i] >> bitShift)));
            unsigned short rValue = (unsigned short)(rgbScales.r_scale*((unsigned char)(r[i] >> bitShift)));
            
            *bytePix = ((bValue > 255) ? 255 : (unsigned char)bValue);
            *(bytePix+1) = ((gValue > 255) ? 255 : (unsigned char)gValue);
            *(bytePix+2) = ((rValue > 255) ? 255 : (unsigned char)rValue);

            //rgb.addSlice("red",r);	
            //rgb.addSlice("green",g);
            //rgb.addSlice("blue",b);			
         }
      }
   }
}
