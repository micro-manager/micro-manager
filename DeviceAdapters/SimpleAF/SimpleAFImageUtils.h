///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAFImageUtils.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Image based autofocus module - Utilities
//                
// AUTHOR:        Prashanth Ravindran 
//                prashanth@100ximaging.com, February, 2009
//
// COPYRIGHT:     100X Imaging Inc, 2009, http://www.100ximaging.com 
//
// Redistribution and use in source and binary forms, with or without modification, are 
// permitted provided that the following conditions are met:
//
//     * Redistributions of source code must retain the above copyright 
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright 
//       notice, this list of conditions and the following disclaimer in 
//       the documentation and/or other materials provided with the 
//       distribution.
//     * Neither the name of 100X Imaging Inc nor the names of its 
//       contributors may be used to endorse or promote products 
//       derived from this software without specific prior written 
//       permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
// NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# ifndef _AFUTILS_H_
# define _AFUTILS_H_

#include <string>
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/ModuleInterface.h"
#include <limits>
#include <iostream>
#include <iterator>
#include <cmath>
//# include "bitmap.h"

# ifdef max
# undef max
# endif
# ifdef min
# undef min
# endif

/////////////////////////////////////////////////////////////
// AFHistogramStretcher:
// ---------------------
// Stretch the histogram based on cthe dynamic range of the 
// pixeltype.
/////////////////////////////////////////////////////////////
template <typename PixelDataType> class AFHistogramStretcher
{
	public:
		AFHistogramStretcher():fContentThreshold(0.001f),fStretchPercent(0.99f)
			,operationmodel_(INPLACE),stretchingmodel_(HISTOGRAMSTETCH){}
		typedef  PixelDataType PixelType;
		float fContentThreshold;
		float fStretchPercent;
		enum OperationType {INPLACE, OUTOFPLACE};
		enum StretchType   {HISTOGRAMSTETCH, HISTOGRAMEQUALIZATION};
		StretchType stretchingmodel_;
		OperationType operationmodel_;

	public:

		// NOTE:: This has to be declared in the header as declaring this in the cpp causes 
		//		  linking problems. This is to do with the fact that the code is templated
		//		  and that, all the temlpate arguments have to be available at compile time
		//		  for the code to work. There is an ugly hack that involves calling the .cpp
		//		  file by some other name, and explicitly including it at the end of the header
		//		  file eg. At the end of this file we write # include "SimpleAFImageUtils.txx"
		//		  but it is not practiced in the MM codebase, so defining inline. 

		int Stretch(PixelDataType * src, int nWidth, int nHeight, PixelDataType * returnimage = 0)
		{
			double * histogram = new double[std::numeric_limits<PixelDataType>::max() + 1];
			for(int i = 0; i <= std::numeric_limits<PixelDataType>::max(); ++i)
			{
				*(histogram + i) = 0.0f;
			}
			// Get the max and the minimum

			PixelType val_max = std::numeric_limits<PixelType>::min(), 
						  val_min = std::numeric_limits<PixelType>::max(),
						  typemax = val_min,
						  typemin = val_max;
			
			

			// Getting min and max in one pass
			for(long i = 0; i < nWidth * nHeight; ++i)
			{
				if(src[i] > val_max)
					val_max = src[i];
				if(src[i] < val_min)
					val_min = src[i]; 
				++histogram[src[i]];

			}

			// Go once through the histogram and get the x% content threshold
			double Observed = 0.0f;
			for(int i = std::numeric_limits<PixelDataType>::max(); i > 0 ; --i)
			{
				Observed+= histogram[i];
				if(Observed >= (1.0f - fStretchPercent)*(nWidth*nHeight))
				{
					val_max = i;
					break;
				}
			}

			// If the image has very low dynamic range.. do nothing, 
			// you might just be amplifying the noise

			if(((float)abs(val_min - val_max)/(float)typemax) < fContentThreshold)
			{
				if(operationmodel_ == OUTOFPLACE)
				{
					memcpy(returnimage,src,nWidth*nHeight*sizeof(PixelType));	
					delete [] histogram;
					histogram = 0;
				}
				return 0;
			}


			if(stretchingmodel_ == HISTOGRAMSTETCH)
			{

				if(operationmodel_ == INPLACE)
				{
					float fFactor = ((float)typemax)/((float)(2*(val_max-val_min)));
					// Setting the scaling again
					for(long i = 0; i < nWidth * nHeight; ++i)
					{
						float Pixel = (fFactor)*(src[i] - val_min);
						if(Pixel > std::numeric_limits<PixelDataType>::max())
						{
							Pixel = std::numeric_limits<PixelDataType>::max();
						}
						src[i] = static_cast<PixelType>(std::floor(Pixel + 0.5f));
					}
				}
				else if(operationmodel_ == OUTOFPLACE)
				{
					float fFactor = ((float)typemax)/((float)(2*(val_max-val_min)));
					// Setting the scaling again
					for(long i = 0; i < nWidth * nHeight; ++i)
					{
						float Pixel = (fFactor)*(src[i] - val_min);
						if(Pixel > std::numeric_limits<PixelDataType>::max())
						{
							Pixel = std::numeric_limits<PixelDataType>::max();
						}
						returnimage[i] = static_cast<PixelType>(std::floor(Pixel + 0.5f));
					}
				}
				return 1;
			}
			else
			if(stretchingmodel_ == HISTOGRAMEQUALIZATION)
			{
				// Come in from the end and identify the cutoff point of the 
				// histogram, which ensures that all hot pixels have been chucked out
				// Also the cdf (cumulative distribution function) is generated in the same pass


				long incidence  = 0;
				long thresh = (long)((float)nWidth*(float)nHeight*(fStretchPercent));
				long uppercutoff = 0;

				double * cdf = new double [std::numeric_limits<PixelType>::max()];

				for(long i = 0; i < std::numeric_limits<PixelType>::max(); ++i)
				{
					incidence += (long)histogram[i];
					if(incidence > thresh && uppercutoff == 0)
					{
						uppercutoff = i;						
					}
					// CDF is generated here
					if(i > 0)
						cdf[i] = histogram[i] + cdf[i-1];         // For the later indices
					else
						cdf[i] = histogram[i];			          // For the first index
				}
				for(long i = 0; i < nWidth * nHeight; ++i)
				{
					if(operationmodel_ == INPLACE)
					{
						src[i] = static_cast<PixelType>(cdf[src[i]]);
					}
					else if(operationmodel_ == OUTOFPLACE)
					{
						returnimage[i] = static_cast<PixelType>(cdf[src[i]]);
						
					}
				}
				delete[] cdf;
				cdf = 0;
				if(histogram != 0)
				{
					delete[] histogram; 
					histogram = 0;
				}

							
				return 1;
			}
			return 1;
		}
		
};

//////////////////////////////////////////////////////////////////
// Shutter Manager:
// ----------------
// Open Shutter and remember state 
class ShutterManager
{
	public:
		ShutterManager():initialized_(false),core_(0){}
		int OpenCoreShutter();
		int RestoreCoreShutter();
		void SetCore(MM::Core *);

	private:
		std::string shutterName_;
		std::string autoShutterState_;
		std::string shutterState_;
		bool initialized_;
		MM::Core * core_;
};

//////////////////////////////////////////////////////////
// Exposure Manager:
// -----------------
// Manages the exposure for autofocus
///////////////////////////////////////////////////////////

class ExposureManager
{
	public:
		ExposureManager():core_(0),working_(false){}
		void SetCore(MM::Core * );
		int SetExposureToAF(double afExp);
		int RestoreExposure();
		bool IsExposureManagerManagingExposure();

	private:
		double systemExposure_;
		double autofocusExposure_;	
		MM::Core * core_;
		bool working_;

};


class ImageSharpnessScorer
{
public:
	ImageSharpnessScorer();
	~ImageSharpnessScorer();
	void SetImage(ImgBuffer &);
	void MedianFilter(int xsize, int ysize);
	void MedianFilter(unsigned char * buffer, unsigned int imagesizex, unsigned int imagesizey, int kernelxsize, int kernelysize);
	double LaplacianFilter();
	double GetScore();
	double GetScore(ImgBuffer & );
	void SetCore(MM::Core * );
	void SetImage(unsigned char * buffer, int width, int height, int depth);


private:
	ImgBuffer buffer_;
	float * kernel_;
	int kernelx;
	int kernely;
	bool convolve2D(unsigned char* in, unsigned char* out, int dataSizeX, int dataSizeY, 
                float* kernel, int kernelSizeX, int kernelSizeY);
	bool convolve2D(unsigned short* in, unsigned short* out, int dataSizeX, int dataSizeY, 
                float* kernel, int kernelSizeX, int kernelSizeY);

};


/////////////////////////////////////
// Reporting Manager
//
///////////////////////////////////////

class ReportingManager
{
public:
	ReportingManager():core_(0),bufferinitialized_(false)
					  ,width_(0),height_(0),depth_(0){}
	void SetCore(MM::Core * );
	int InitializeDebugStack(MM::Device * callee = 0);
	int InsertCurrentImageInDebugStack(Metadata * IMd);

private:

	MM::Core * core_;
	MM::Device * callee_;
	bool bufferinitialized_;
	int width_;
	int height_;
	int depth_;	
};

///////////////////////////////////////////////////
// Simple Image handle: Always stores image as a 
// 8-bit type
//
struct ImageHandle{
	unsigned char * imagedatahandle;

	int m_nWidth;
	int m_nHeight;
	int m_nDepth;
	double dSharpness;
	double dZpositionAtCapture;
	bool bIsInitialized;
	bool bHasDynamicRange;
	ImageHandle():bIsInitialized(false),bHasDynamicRange(true){}
	void Initialize(int width, int height, int depth = 1);	
	void Reset(){memset (imagedatahandle,0,m_nWidth*m_nHeight);}
	~ImageHandle();

} ;

typedef std::vector<ImageHandle> ImageHandleArray;

// Camera pixel traits, encoding the 


 
# endif
