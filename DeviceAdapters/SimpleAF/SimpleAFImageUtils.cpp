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


# include "SimpleAFImageUtils.h"
// For numeric limits, to work with the different pixel types

# include <vector>
# include <cmath>
# include <algorithm>



# ifdef max
# undef max
# endif
# ifdef min
# undef min
# endif

//template <typename PixelDataType>
//int AFHistogramStretcher<PixelDataType>::
//Stretch(PixelDataType *src, int nWidth, int nHeight, PixelDataType *returnimage = 0)
// NOTE: Implemented in the .h see the note there

////////////////////////////////////////////////////////////////
// Shutter Manager class:
// ----------------------
// Open shutter and close shutter, while setting auto-shutter
// to what it was. Designed such that it can be stateless i.e
// the callee does not need to know the state of the shutter to
// call it
/////////////////////////////////////////////////////////////////

void ShutterManager::SetCore(MM::Core * core)
{
	core_ = core;
}

int ShutterManager::OpenCoreShutter()
{
	if(core_ == 0)
		return DEVICE_ERR;

   // first get the current state of the auto-shutter and shutter
   char * value = new char[MM::MaxStrLength];
   core_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, value);
   autoShutterState_ = value;;

   // then get the current state of the shutter
   core_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreShutter, value);
   shutterName_ = value;;
   core_->GetDeviceProperty(shutterName_.c_str(), MM::g_Keyword_State, value);
   shutterState_ = value;;

   // now we can open the shutter
   core_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, "0"); // disable auto-shutter
   core_->SetDeviceProperty(shutterName_.c_str(), MM::g_Keyword_State, "1"); // open shutter

   initialized_ = true;

   delete [] value; value = 0;

   return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////////////
// Restore Shutter Settings to what they were before
//
//////////////////////////////////////////////////////////////////////////

int ShutterManager::RestoreCoreShutter()
{
	if(!initialized_ || core_ == 0)
		return DEVICE_ERR;
	// restore shutter and auto-shutter settings
	// TODO: make sure to restore this in case there is an error and the flow never reaches this point
	core_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, autoShutterState_.c_str());
	core_->SetDeviceProperty(shutterName_.c_str(), MM::g_Keyword_State, shutterState_.c_str()); // open/close shutter

	// Now state is not remembered, so set it back
	initialized_ = false;
	shutterName_ = "";
	autoShutterState_ = "";
	shutterState_ = "";

	return DEVICE_OK;
}


//
// Image sharpness scorer
//
ImageSharpnessScorer::ImageSharpnessScorer()
{
	kernel_ = new float[9];
	
	kernel_[0] = 2.0; kernel_[1] =  1.0; kernel_[2] =  0.0;
	kernel_[3] = 1.0; kernel_[4] =  0.0; kernel_[5] = -1.0;
	kernel_[6] = 0.0; kernel_[7] = -1.0; kernel_[8] = -2.0;

	kernelx = 3;
	kernely = 3;
}

ImageSharpnessScorer::~ImageSharpnessScorer()
{
	delete[] kernel_;
	kernel_ = 0;
}


double ImageSharpnessScorer::LaplacianFilter()
{
	long double score = 0.0;
	unsigned char * outbuffer = new unsigned char [buffer_.Width() * buffer_.Height()];
	unsigned char * inputbuffer = buffer_.GetPixelsRW();
	bool ret = convolve2D(inputbuffer,outbuffer,buffer_.Width(), buffer_.Height(), kernel_,kernelx, kernely);

	int imwidth = buffer_.Width();
	int imheight = buffer_.Height();
	for(long int i = 0; i < imwidth*imheight; ++i)
	{
		score += *(outbuffer + i);
	}

	delete [] outbuffer;
	outbuffer = 0;

	score/= (double)(imwidth*imheight);
	return score;
}

bool ImageSharpnessScorer::convolve2D(unsigned char* in, unsigned char* out, int dataSizeX, int dataSizeY, 
                float* kernel, int kernelSizeX, int kernelSizeY)
{
    int i, j, m, n;
    unsigned char *inPtr, *inPtr2, *outPtr;
    float *kPtr;
    int kCenterX, kCenterY;
    int rowMin, rowMax;                             // to check boundary of input array
    int colMin, colMax;                             //
    float sum;                                      // temp accumulation buffer

    // check validity of params
    if(!in || !out || !kernel) return false;
    if(dataSizeX <= 0 || kernelSizeX <= 0) return false;

    // find center position of kernel (half of kernel size)
    kCenterX = kernelSizeX >> 1;
    kCenterY = kernelSizeY >> 1;

    // init working  pointers
    inPtr = inPtr2 = &in[dataSizeX * kCenterY + kCenterX];  // note that  it is shifted (kCenterX, kCenterY),
    outPtr = out;
    kPtr = kernel;

    // start convolution
    for(i= 0; i < dataSizeY; ++i)                   // number of rows
    {
        // compute the range of convolution, the current row of kernel should be between these
        rowMax = i + kCenterY;
        rowMin = i - dataSizeY + kCenterY;

        for(j = 0; j < dataSizeX; ++j)              // number of columns
        {
            // compute the range of convolution, the current column of kernel should be between these
            colMax = j + kCenterX;
            colMin = j - dataSizeX + kCenterX;

            sum = 0;                                // set to 0 before accumulate

            // flip the kernel and traverse all the kernel values
            // multiply each kernel value with underlying input data
            for(m = 0; m < kernelSizeY; ++m)        // kernel rows
            {
                // check if the index is out of bound of input array
                if(m <= rowMax && m > rowMin)
                {
                    for(n = 0; n < kernelSizeX; ++n)
                    {
                        // check the boundary of array
                        if(n <= colMax && n > colMin)
                            sum += *(inPtr - n) * *kPtr;

                        ++kPtr;                     // next kernel
                    }
                }
                else
                    kPtr += kernelSizeX;            // out of bound, move to next row of kernel

                inPtr -= dataSizeX;                 // move input data 1 raw up
            }

            // convert negative number to positive
            *outPtr = (unsigned char)((float)fabs(sum) + 0.5f);

            kPtr = kernel;                          // reset kernel to (0,0)
            inPtr = ++inPtr2;                       // next input
            ++outPtr;                               // next output
        }
    }

    return true;
}

bool ImageSharpnessScorer::convolve2D(unsigned short* in, unsigned short* out, int dataSizeX, int dataSizeY, 
                float* kernel, int kernelSizeX, int kernelSizeY)
{
    int i, j, m, n;
    unsigned short *inPtr, *inPtr2, *outPtr;
    float *kPtr;
    int kCenterX, kCenterY;
    int rowMin, rowMax;                             // to check boundary of input array
    int colMin, colMax;                             //
    float sum;                                      // temp accumulation buffer

    // check validity of params
    if(!in || !out || !kernel) return false;
    if(dataSizeX <= 0 || kernelSizeX <= 0) return false;

    // find center position of kernel (half of kernel size)
    kCenterX = kernelSizeX >> 1;
    kCenterY = kernelSizeY >> 1;

    // init working  pointers
    inPtr = inPtr2 = &in[dataSizeX * kCenterY + kCenterX];  // note that  it is shifted (kCenterX, kCenterY),
    outPtr = out;
    kPtr = kernel;

    // start convolution
    for(i= 0; i < dataSizeY; ++i)                   // number of rows
    {
        // compute the range of convolution, the current row of kernel should be between these
        rowMax = i + kCenterY;
        rowMin = i - dataSizeY + kCenterY;

        for(j = 0; j < dataSizeX; ++j)              // number of columns
        {
            // compute the range of convolution, the current column of kernel should be between these
            colMax = j + kCenterX;
            colMin = j - dataSizeX + kCenterX;

            sum = 0;                                // set to 0 before accumulate

            // flip the kernel and traverse all the kernel values
            // multiply each kernel value with underlying input data
            for(m = 0; m < kernelSizeY; ++m)        // kernel rows
            {
                // check if the index is out of bound of input array
                if(m <= rowMax && m > rowMin)
                {
                    for(n = 0; n < kernelSizeX; ++n)
                    {
                        // check the boundary of array
                        if(n <= colMax && n > colMin)
                            sum += *(inPtr - n) * *kPtr;

                        ++kPtr;                     // next kernel
                    }
                }
                else
                    kPtr += kernelSizeX;            // out of bound, move to next row of kernel

                inPtr -= dataSizeX;                 // move input data 1 raw up
            }

            // convert negative number to positive
            *outPtr = (unsigned short)((float)fabs(sum) + 0.5f);

            kPtr = kernel;                          // reset kernel to (0,0)
            inPtr = ++inPtr2;                       // next input
            ++outPtr;                               // next output
        }
    }

    return true;
}
void ImageSharpnessScorer::MedianFilter(unsigned char * buffer, unsigned int imagesizex, unsigned int imagesizey, int xsize, int ysize)
{
	// If the kernel sizes are even, do nothing, 
	// thats a kettle of long dead herrings!! - 
	// Just request a kernel of odd dimensions -- mmkay!!

	if(xsize%2 == 0 || ysize%2 == 0 )
		return;
	
	double * kernelmask_ = new double[xsize*ysize];
	long lIndex = 0;

	// Navigating through the buffer
	for(unsigned int j = 0; j < imagesizey; ++j)
	{
		for(unsigned int i = 0; i < imagesizex; ++i)
		{
			// Initialize your kernel array to zero
			memset(kernelmask_,0,(xsize*ysize*sizeof(double)));
			// Declare the loop variable for the kernel accumulator
			int count  = 0;
			for(int y_off = (-ysize/2); y_off <= (ysize/2); ++y_off)
			{
				for(int x_off = (-xsize/2); x_off <= (xsize/2); ++x_off)
				{
					if( (((int)j+y_off) >= 0) && (((int)i+x_off) >= 0))
					{
						lIndex = imagesizex*(j + y_off) + (i + x_off);
						*(kernelmask_ + count) = *(buffer+lIndex);	
					}
					
					++count;
				}
			}
			// Sort the kernel array
			std::sort<double *>(kernelmask_, (kernelmask_+xsize*ysize));
			int index = (int)(((float)(xsize * ysize )/2.0f) + 0.5);
			unsigned char medianvalue = (unsigned char)kernelmask_[index];
			long setIndex = imagesizex*j + i;
			*(buffer + setIndex) = medianvalue;
		}
	}
	
	/* //Navigating through the buffer
	for (unsigned int j=0; j<imagesizey; j++)
	{
		for (unsigned int k=0; k< imagesizex; k++)
		{
			memset(kernelmask_,0,(xsize*ysize*sizeof(double)));

			 For getting the central array
			int count = 0;
			for(int  y_off = -ysize/2 ; y_off <= ysize/2; ++y_off)
			{
				for(int  x_off = -xsize/2 ; x_off <= xsize/2; ++x_off)
				{
					long lIndex = imagesizex*(j + y_off) + (k + x_off);
					if(lIndex >= 0 && 
						lIndex < (long)(imagesizex*imagesizey)&&
						(j + y_off) >= 0 && (k + x_off) >= 0)
					{
						*(kernelmask_ + count) = *(buffer+lIndex);						
					}
					++count;
				}
			}*/
			// sort the kernel mask array			
			
			// Get the median
			// if odd - set the central value

			//unsigned char * pBuf = buffer;
			//if((xsize * ysize) % 2 == 1)
			//{
			//	int index = (int)(((float)(xsize * ysize )/2.0f) + 0.5);
			//	unsigned char medianvalue_ = (unsigned char)kernelmask_[index];
			//	long setIndex = imagesizex*j + k;
			//	*(pBuf + setIndex) = medianvalue_;
			//}
			//else
			//// if even - set the average of the two central values
			//{
			//	int index = (int)((float)(xsize * ysize )/2.0f);
			//	unsigned char medianvalue_ = (unsigned char)((float)(kernelmask_[(int)(index - 0.5f)] + kernelmask_[(int)(index + 0.5f)])/2.0f);
			//	long setIndex = imagesizex*j + k;
			//	*(pBuf + setIndex) = medianvalue_;
			//}
		
			
	//	}
	//}
	delete [] kernelmask_;
	kernelmask_ = 0;
}

void ImageSharpnessScorer::MedianFilter(int xsize, int ysize)
{
	ImgBuffer filtimage_(buffer_);
	// Navigating through the buffer
	for (unsigned int j=0; j<buffer_.Height(); j++)
	{
		for (unsigned int k=0; k< buffer_.Width(); k++)
		{
			std::vector<double> kernelmask_(xsize * ysize);
			for(int i = 0; i < xsize*ysize; ++i)
				kernelmask_[i] = 0;
			// For getting the central array
			int count = 0;
			for(int  y_off = -ysize/2 ; y_off <= ysize/2; ++y_off)
			{
				for(int  x_off = -xsize/2 ; x_off <= xsize/2; ++x_off)
				{
					long lIndex = buffer_.Width()*(j + y_off) + (k + x_off);
					if(lIndex >= 0 && 
						lIndex < (long)(buffer_.Width()*buffer_.Height())&&
						(j + y_off) >= 0 && (k + x_off) >= 0
						)
					{
						kernelmask_[count] = buffer_.GetPixels()[lIndex];						
					}
					++count;
				}
			}
			// sort the kernel mask array

			double min = kernelmask_[0];
			double temp = kernelmask_[0];
			for(int i = 0; i < (ysize * xsize) - 1; ++i)
			{
				for(int j = i; j < xsize*ysize  ; ++j)
				{
					if(kernelmask_[j] < min)
					{
						min = kernelmask_[j];
						temp = kernelmask_[i];
						kernelmask_[i] = kernelmask_[j];
						kernelmask_[i+1] = temp;
					}
				}
			}
	
			// Get the median
			// if odd - set the central value

			unsigned char * pBuf = const_cast<unsigned char *>(filtimage_.GetPixels());
			if((xsize * ysize) % 2 == 1)
			{
				int index = (int)(((float)(xsize * ysize )/2.0f) + 0.5);
				unsigned char medianvalue_ = (unsigned char)kernelmask_[index];
				long setIndex = buffer_.Width()*j + k;
				*(pBuf + setIndex) = medianvalue_;
			}
			else
			// if even - set the average of the two central values
			{
				int index = (int)((float)(xsize * ysize )/2.0f);
				unsigned char medianvalue_ = (unsigned char)((float)(kernelmask_[(int)(index - 0.5f)] + kernelmask_[(int)(index + 0.5f)])/2.0f);
				long setIndex = buffer_.Width()*j + k;
				*(pBuf + setIndex) = medianvalue_;
			}

			
		}
	}
	buffer_ = filtimage_;	
}

double ImageSharpnessScorer::GetScore()
{
  // 1. Get the median filtering done
	this->MedianFilter(3,3);
  // 2. Run the laplacian filter
	double score = this->LaplacianFilter();
	return score;
}

double ImageSharpnessScorer::GetScore(ImgBuffer & buffer)
{
	buffer_.Copy(buffer);
	return GetScore();

}

void ImageSharpnessScorer::SetImage(ImgBuffer & inputbuffer)
{
	buffer_.Copy(inputbuffer);
}

void ImageSharpnessScorer::SetImage(unsigned char * buffer, int width, int height, int depth)
{
	ImgBuffer newbuffer(width,height,depth);
	newbuffer.SetPixels(buffer);
	buffer_ = newbuffer;
}


////////////////////////////////////////////////////////////
// Exposure Manager
//


void ExposureManager::SetCore(MM::Core * core)
{
	core_ = core;	
}

bool ExposureManager::IsExposureManagerManagingExposure()
{
	return working_;
}

int ExposureManager::SetExposureToAF(double afExp)
{
	if(afExp <= 0.0f || core_ == 0)
		return DEVICE_ERR;
	autofocusExposure_ = afExp;
	// 1. Get the current exposure and persist it
	
	int ret = core_->GetExposure(systemExposure_);
	if(ret != DEVICE_OK)
		return ret;
	// 2. Set the expsoure to the requested AF exposure
	ret = core_->SetExposure(autofocusExposure_);
	if (ret != DEVICE_OK)		 
		return ret;
	// 3. Set the state flag to true
	working_ = true;

	return DEVICE_OK;
}

int ExposureManager::RestoreExposure()
{
	int ret = core_->SetExposure(systemExposure_);
	if(ret != DEVICE_OK)
		return ret;
	working_ = false;

	return DEVICE_OK;
}

/////////////////////////////////
// Reporting Manager
/////////////////////////////////

void ReportingManager::SetCore(MM::Core * core)
{
	core_ = core;
}

int ReportingManager::InitializeDebugStack(MM::Device * callee)
{
	if(core_ == 0 || callee == 0)
	{
		return DEVICE_ERR;
	}
	
	int ret = core_->GetImageDimensions(width_,height_,depth_);

	if(ret != DEVICE_OK)
	{
		return ret;
	}

	if(!core_->InitializeImageBuffer(1,1,width_,height_,depth_))
	{
		return DEVICE_ERR;
	}

	core_->ClearImageBuffer(callee);

	bufferinitialized_ = true;
	callee_ = callee;

	
	return DEVICE_OK;
}

int ReportingManager::InsertCurrentImageInDebugStack(Metadata * IMd)
{
	if(bufferinitialized_ == false || core_ == 0 || callee_ == 0|| IMd == 0)
	{
		return DEVICE_ERR;
	}

	// Get the image buffer

	const unsigned char * iBuf = const_cast<unsigned char *>((unsigned char *)core_->GetImage()); 

	try
	{
		int ret  = core_->InsertImage(callee_,iBuf,width_,height_,depth_,const_cast<Metadata *>(IMd));
		if(ret == DEVICE_BUFFER_OVERFLOW)
		{
			core_->ClearImageBuffer(callee_);
			ret  = core_->InsertImage(callee_,iBuf,width_,height_,depth_,const_cast<Metadata *>(IMd));
			return ret;
		}
	}
	catch(...)
	{
		core_->LogMessage(callee_,"AF-Error in inserting images into the debug buffer",false);
	}

	return DEVICE_OK;
}

void ImageHandle::Initialize(int width,int height,int depth)
{
	try{
		imagedatahandle = new unsigned char[width*height];
		memset (imagedatahandle,0,width*height);
		m_nWidth = width;
		m_nHeight = height;
		m_nDepth = depth;			
	}
	catch(std::bad_alloc & e)
	{
		std::cerr << e.what();
		return;
	}
	bIsInitialized = true;
}

ImageHandle::~ImageHandle()
{
	if(bIsInitialized && imagedatahandle != 0)
	{
		delete [] imagedatahandle;
		imagedatahandle = 0;
	}
}

