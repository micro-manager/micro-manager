///////////////////////////////////////////////////////////////////////////////
// FILE:          MUCamSource.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Motic camera device adapter for OS X
// COPYRIGHT:     2014 Motic China Group Co., Ltd.
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Motic

#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDeviceConstants.h"
#include "MUCamSource.h"
#include <algorithm>
#include <iterator>
#include "dlfcn.h"
//#define _LOG_OUT_

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
const char* g_CameraName = "MoticCam";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bitRGB = "32bitRGB";
const char* g_PixelType_64bitRGB = "64bitRGB";

const int Camera_Name_Len = 32;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
    RegisterDevice(g_CameraName, MM::CameraDevice, "Motic Camera Adapter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
        return 0;
    
    // decide which device class to create based on the deviceName parameter
    if (strcmp(deviceName, g_CameraName) == 0)
    {
        // create camera
        return new MUCamSource();
    }
    
    // ...supplied name not recognized
    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}

inline void CoverImage8( unsigned char* pSour, unsigned char* pDest, int sz, int bytesPerPixel )
{
    switch(bytesPerPixel)
    {
        case 1://8->8
        {
            memcpy(pDest, pSour, sz);
        }
            break;
        case 2://8->16
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i <sz; i++)
            {
                *pD++  = ((*pSour++)<<8);
            }
        }
            break;
        case 4://8->32
        {
            for(int i = 0; i < sz; i++)
            {
                *pDest ++ = *pSour;
                *pDest ++ = *pSour;
                *pDest ++ = *pSour;
                pDest++;
                pSour++;
            }
        }
            break;
        case 8://8->64
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i < sz; i++)
            {
                *pD ++ = ((*pSour)<<8);
                *pD ++ = ((*pSour)<<8);
                *pD ++ = ((*pSour)<<8);
                pD ++;
                pSour++;
            }
        }
            break;
    }
    
}

inline void CoverImage16( unsigned char* pSour, unsigned char* pDest, int sz, int bytesPerPixel )
{
    unsigned short*pS = (unsigned short*)pSour;
    switch(bytesPerPixel)
    {
        case 1://16->8
        {
            for(int i = 0; i <sz; i++)
            {
                *pDest++  = ((*pS++)>>8);
            }
        }
            break;
        case 2://16->16
        {
            memcpy(pDest, pSour, sz*2);
        }
            break;
        case 4://16->32
        {
            for(int i = 0; i < sz; i++)
            {
                unsigned char val = ((*pS)>>8);
                *pDest ++ = val;
                *pDest ++ = val;
                *pDest ++ = val;
                pDest++;
                pS++;
            }
        }
            break;
        case 8://16->64
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i < sz; i++)
            {
                *pD ++ = *pS;
                *pD ++ = *pS;
                *pD ++ = *pS;
                pD ++;
                pS++;
            }
        }
            break;
    }
}

inline void CoverImage24( unsigned char* pSour, unsigned char* pDest, int sz, int bytesPerPixel )
{
    switch(bytesPerPixel)
    {
        case 1://24->8
        {
            for(int i = 0; i < sz; i++)
            {
                *pDest++ = (pSour[2]*299 + pSour[1]*587 + pSour[0]*114)/1000;
                pSour += 3;
            }
        }
            break;
        case 2://24->16
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i <sz; i++)
            {
                *pD++  = (((pSour[2]*299 + pSour[1]*587 + pSour[0]*114)/1000)<<8);
                pSour += 3;
            }
        }
            break;
        case 4://24->32
        {
            for(int i = 0; i < sz; i++)
            {
                *pDest ++ = pSour[2];
                *pDest ++ = pSour[1];
                *pDest ++ = pSour[0];
                pSour += 3;
                pDest++;
            }
        }
            break;
        case 8://24->64
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i < sz; i++)
            {
                *pD ++ = ((pSour[2])<<8);
                *pD ++ = ((pSour[1])<<8);
                *pD ++ = ((pSour[0])<<8);
                pSour += 3;
                pD ++;
            }
        }
            break;
    }
}

inline void CoverImage48( unsigned char* pSour, unsigned char* pDest, int sz, int m_iBytesPerPixel )
{
    unsigned short* pS = (unsigned short*)pSour;
    switch(m_iBytesPerPixel)
    {
        case 1://48->8
        {
            for(int i = 0; i < sz; i++)
            {
                *pDest++ = (((pS[2]*299 + pS[1]*587 + pS[0]*114)/1000)>>8);
                pS += 3;
            }
        }
            break;
        case 2://48->16
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i <sz; i++)
            {
                *pD++  = (pS[2]*299 + pS[1]*587 + pS[0]*114)/1000;
                pS += 3;
            }
        }
            break;
        case 4://48->32
        {
            for(int i = 0; i < sz; i++)
            {
                *pDest ++ = ((pS[2])>>8);
                *pDest ++ = ((pS[1])>>8);
                *pDest ++ = ((pS[0])>>8);
                pS += 3;
                pDest++;
            }
        }
            break;
        case 8://48->64
        {
            unsigned short* pD = (unsigned short*)pDest;
            for(int i = 0; i < sz; i++)
            {
                *pD ++ = pS[2];
                *pD ++ = pS[1];
                *pD ++ = pS[0];
                pS += 3;
                pD ++;     
            }
        }
            break;
    }
}

inline int ConvertInnerBinning(int binF)
{
    int binning = 0;
    while ((binF=(binF>>1))>0)
    {
        binning++;
    }
    
    return binning;
}

///////////////////////////////////////////////////////////////////////////////
// MUCamSource implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
 * MUCamSource constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
MUCamSource::MUCamSource() :
binning_ (0),
gain_(1.0),
gains_(0),
bytesPerPixel_(4),
colorChannel_(3),
initialized_(false),
exposureMs_(10.0),
exposureMin_(0),
exposureMax_(10.0),
roiX_(0),
roiY_(0),
//thd_(0),
cameraCnt_(0),
currentCam_(-1),
bitDepth_(8),
bitCnt_(0),
tmpbuf_(0),
handle_(0),
bufferSize_(0)
{
    // call the base class method to set-up default error codes/messages
    InitializeDefaultErrorMessages();
    
    // add custom messages
    SetErrorText(ERR_NO_CAMERAS_FOUND, "No cameras found.  Please connect a Motic camera and turn it on");
    SetErrorText(ERR_BUSY_ACQUIRING,   "Motic camera is already acquiring images.");
    SetErrorText(ERR_SOFTWARE_TRIGGER_FAILED, "Motic camera is not in software trigger mode.");

    // create live video thread
    //thd_ = new MSequenceThread(this);
}

/**
 * MUCamSource destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
MUCamSource::~MUCamSource()
{
    if (initialized_)
        Shutdown();
    //delete thd_;
    if (gains_)
    {
        delete [] gains_;
        gains_ = 0;
    }
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void MUCamSource::GetName(char* name) const
{
    // We just return the name we use for referring to this
    // device adapter.
    CDeviceUtils::CopyLimitedString(name, g_CameraName);
}

/**
 * Intializes the hardware.
 * Typically we access and initialize hardware at this point.
 * Device properties are typically created here as well.
 * Required by the MM::Device API.
 */
int MUCamSource::Initialize()
{
    if (!LoadFunctions())
        return DEVICE_ERR;
    if (initialized_)
        return DEVICE_OK;

    // set property list
    // -----------------
    FindMoticCameras();
    if(cameraCnt_ == 0)
    {
        return ERR_NO_CAMERAS_FOUND;
    }

    currentCam_ = 0;//MIDP_GetCurCameraIndex();
    int ret;
    CPropertyAction* pAct;
    if (!OpenCamera(hCameras_[currentCam_])) return DEVICE_ERR;
    char sName[Camera_Name_Len];
    GetMotiCamNAME(hCameras_[currentCam_], sName);
    //memcpy(sName, GetMotiCamNAME(hCameras_[currentCam_]), Camera_Name_Len);
    pAct = new CPropertyAction(this, &MUCamSource::OnDevice);
    ret = CreateProperty(g_Keyword_Cameras, sName, MM::String, true, pAct);
    ret = SetAllowedValues(g_Keyword_Cameras, DevicesVec_);
    assert(ret == DEVICE_OK);
    
    ret = InitDevice();
    if(ret == DEVICE_OK)
    {
        initialized_ = true;
    }

    return ret;
}

/**
 * Shuts down (unloads) the device.
 * Ideally this method will completely unload the device and release all resources.
 * Shutdown() may be called multiple times in a row.
 * Required by the MM::Device API.
 */
int MUCamSource::Shutdown()
{
    if (initialized_)
    {
        MUCam_closeCamera(hCameras_[currentCam_]);
        for (int i = 0; i<cameraCnt_; i++)
        {
            if (hCameras_[i])
            {
                MUCam_releaseCamera(hCameras_[i]);
                hCameras_[i] = 0;
            }
        }
        cameraCnt_ = 0;
    }
    initialized_ = false;
    UnloadFunctions();
    return DEVICE_OK;
}

/**
 * Performs exposure and grabs a single image.
 * This function should block during the actual exposure and return immediately afterwards
 * (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
 * Required by the MM::Camera API.
 */
int MUCamSource::SnapImage()
{
    GenerateImage();
    return DEVICE_OK;
}

/**
 * Returns pixel data.
 * Required by the MM::Camera API.
 * The calling program will assume the size of the buffer based on the values
 * obtained from GetImageBufferSize(), which in turn should be consistent with
 * values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
 * The calling program allso assumes that camera never changes the size of
 * the pixel buffer on its own. In other words, the buffer can change only if
 * appropriate properties are set (such as binning, pixel type, etc.)
 */
const unsigned char* MUCamSource::GetImageBuffer()
{
    return const_cast<unsigned char*>(img_.GetPixels());
}

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned MUCamSource::GetImageWidth() const
{
    return img_.Width();
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned MUCamSource::GetImageHeight() const
{
    return img_.Height();
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned MUCamSource::GetImageBytesPerPixel() const
{
    return img_.Depth();
}

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned MUCamSource::GetBitDepth() const
{
    //return img_.Depth() == 1 ? 8 : MAX_BIT_DEPTH;
    if (bytesPerPixel_ == 1 || bytesPerPixel_ == 4)
        return 8;
    else
        return 16;
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long MUCamSource::GetImageBufferSize() const
{
    return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

/**
 * Sets the camera Region Of Interest.
 * Required by the MM::Camera API.
 * This command will change the dimensions of the image.
 * Depending on the hardware capabilities the camera may not be able to configure the
 * exact dimensions requested - but should try do as close as possible.
 * If the hardware does not have this capability the software should simulate the ROI by
 * appropriately cropping each frame.
 * This demo implementation ignores the position coordinates and just crops the buffer.
 * @param x - top-left corner coordinate
 * @param y - top-left corner coordinate
 * @param xSize - width
 * @param ySize - height
 */
int MUCamSource::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
    if (xSize == 0 && ySize == 0)
    {
        // effectively clear ROI
        ResizeImageBuffer();
        roiX_ = 0;
        roiY_ = 0;
    }
    else
    {
        // apply ROI
        img_.Resize(xSize, ySize);
        roiX_ = x;
        roiY_ = y;
    }
    return DEVICE_OK;
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int MUCamSource::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
    x = roiX_;
    y = roiY_;
    
    xSize = img_.Width();
    ySize = img_.Height();
    
    return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int MUCamSource::ClearROI()
{
    ResizeImageBuffer();
    roiX_ = 0;
    roiY_ = 0;
    
    return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double MUCamSource::GetExposure() const
{
    return exposureMs_;
}

/**
 * Sets exposure in milliseconds.
 * Required by the MM::Camera API.
 */
void MUCamSource::SetExposure(double exp)
{
    exposureMs_ = exp;
    MUCam_setExposure(hCameras_[currentCam_], exp);
}

/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int MUCamSource::GetBinning() const
{
    return 1<<binning_;
}

/**
 * Sets binning factor.
 * Required by the MM::Camera API.
 */
int MUCamSource::SetBinning(int binF)
{
    binning_ = ConvertInnerBinning(binF);
    MUCam_setBinningIndex(hCameras_[currentCam_], binning_);
    ResizeImageBuffer();
    return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}


bool MUCamSource::IsCapturing()
{
    if(CCameraBase<MUCamSource>::IsCapturing())
    {
        return true;
    }
    
    return false;
}

unsigned MUCamSource::GetNumberOfComponents() const
{
    if(bytesPerPixel_ == 1 || bytesPerPixel_ == 2)return 1;
    return 4;
}

int MUCamSource::GetComponentName(unsigned channel, char* name)
{
    if(bytesPerPixel_ == 1 || bytesPerPixel_ == 2)
    {
        CDeviceUtils::CopyLimitedString(name, "Grayscale");
    }
    else if(channel == 0)
    {
        CDeviceUtils::CopyLimitedString(name, "Blue");
    }
    else if(channel == 1)
    {
        CDeviceUtils::CopyLimitedString(name, "Green");
    }
    else if(channel == 2)
    {
        CDeviceUtils::CopyLimitedString(name, "Red");
    }
    else if(channel == 3)
    {
        CDeviceUtils::CopyLimitedString(name, "Alpha");
    }
    else
    {
        return DEVICE_NONEXISTENT_CHANNEL;
    }
    return DEVICE_OK;
}

const unsigned int* MUCamSource::GetImageBufferAsRGB32()
{
    return (unsigned int*)img_.GetPixels();
}


///////////////////////////////////////////////////////////////////////////////
// MUCamSource Action handlers
///////////////////////////////////////////////////////////////////////////////

int MUCamSource::OnDevice( MM::PropertyBase* pProp, MM::ActionType eAct )
{
#ifdef _LOG_OUT_
    OutputDebugString("OnDevice");
#endif
    if (eAct == MM::AfterSet)
    {
        string strName;
        pProp->Get(strName);
        
        vector<string>::const_iterator begin = DevicesVec_.begin();
        vector<string>::const_iterator end = DevicesVec_.end();
        vector<string>::const_iterator it = find(begin, end, strName);
        vector<string>::difference_type idx = distance(begin, it);
        if (it != end && idx != currentCam_)
        {
            StopSequenceAcquisition();
            if (!OpenCamera(hCameras_[idx])) return DEVICE_ERR;
            currentCam_ = idx;// MIDP_GetCurCameraIndex();
            InitDevice();
        }
    }
    else if (eAct == MM::BeforeGet)
    {
        if(currentCam_ >= 0 && currentCam_ < cameraCnt_)
        {
            string strName = DevicesVec_[currentCam_];
            pProp->Set(strName.c_str());
        }
    }
    
    return DEVICE_OK;
}

/**
 * Handles "Binning" property.
 */
int MUCamSource::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::AfterSet)
    {
        long binSize;
        pProp->Get(binSize);
        binning_ = ConvertInnerBinning(binSize);
        MUCam_setBinningIndex(hCameras_[currentCam_], binning_);
        return ResizeImageBuffer();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set((long)1<<binning_);
    }
    
    return DEVICE_OK;
}

/**
 * Handles "PixelType" property.
 */
int MUCamSource::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::AfterSet)
    {
        string val;
        pProp->Get(val);
        if (val.compare(g_PixelType_8bit) == 0)
        {
            MUCam_setBitCount(hCameras_[currentCam_], 8);
            bytesPerPixel_ = 1;
            bitCnt_ = 8;
        }
        else if (val.compare(g_PixelType_16bit) == 0)
        {
            MUCam_setBitCount(hCameras_[currentCam_], 16);
            bytesPerPixel_ = 2;
            bitCnt_ = 16;
        }
        else if (val.compare(g_PixelType_32bitRGB) == 0)
        {
            MUCam_setBitCount(hCameras_[currentCam_], 8);
            bytesPerPixel_ = 4;
            bitCnt_ = 24;
        }
        else if (val.compare(g_PixelType_64bitRGB) == 0)
        {
            MUCam_setBitCount(hCameras_[currentCam_], 16);
            bytesPerPixel_ = 8;
            bitCnt_ = 48;
        }
        else
            assert(false);
        ////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        
        char buf[MM::MaxStrLength];
        GetProperty(MM::g_Keyword_PixelType, buf);
        std::string pixelType(buf);
        if (pixelType.compare(g_PixelType_8bit) == 0)
        {
            if(bytesPerPixel_ == 2)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
            }
            else if(bytesPerPixel_ == 4)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
            }
            else if(bytesPerPixel_ == 8)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
            }
        }
        else if (pixelType.compare(g_PixelType_16bit) == 0)
        {
            if(bytesPerPixel_ == 1)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
            }
            else if(bytesPerPixel_ == 4)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
            }
            else if(bytesPerPixel_ == 8)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
            }
        }
        else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
        {
            if(bytesPerPixel_ == 1)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
            }
            else if(bytesPerPixel_ == 2)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
            }
            else if(bytesPerPixel_ == 8)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
            }
        }
        else if (pixelType.compare(g_PixelType_64bitRGB) == 0)
        {
            if(bytesPerPixel_ == 1)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
            }
            else if(bytesPerPixel_ == 2)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
            }
            else if(bytesPerPixel_ == 4)
            {
                SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
            }        
        }
        // save to plist
        
        ResizeImageBuffer();
    }
    else if (eAct == MM::BeforeGet)
    {
        if (bytesPerPixel_ == 1)
            pProp->Set(g_PixelType_8bit);
        else if (bytesPerPixel_ == 2)
            pProp->Set(g_PixelType_16bit);
        else if (bytesPerPixel_ == 4)
            pProp->Set(g_PixelType_32bitRGB);
        else if (bytesPerPixel_ == 8)
            pProp->Set(g_PixelType_64bitRGB);
        else
            assert(false); // this should never happen
    }
    
    return DEVICE_OK;
}

/**
 * Handles "Gain" property.
 */
int MUCamSource::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::AfterSet)
    {
        int gainRed, gainGreen, gainBlue;
        double gain;
        pProp->Get(gain);
        if(MUCam_setRGBGainValue(hCameras_[currentCam_], gain, gain, gain, &gainRed, &gainGreen, &gainBlue))
            gain_ = gains_[gainRed];
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(gain_);
    }
    
    return DEVICE_OK;
}

/**
 * Handles "Exposure" property.
 */
int MUCamSource::OnExposure( MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::AfterSet)
    {
        pProp->Get(exposureMs_);
        MUCam_setExposure(hCameras_[currentCam_], exposureMs_);
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(exposureMs_);
    }

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Private MUCamSource methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sync internal image buffer size to the chosen property values.
 */
int MUCamSource::ResizeImageBuffer()
{
    width_= BinningsVec_[2 * binning_];
    height_ = BinningsVec_[2 * binning_ + 1];
    
    img_.Resize(width_, height_, bytesPerPixel_);
    ReAllocalBuffer(width_ * height_ * bitCnt_);
    
    return DEVICE_OK;
}

void MUCamSource::ReAllocalBuffer(int size)
{
    if (size <= bufferSize_) return;
    if (tmpbuf_)
    {
        delete [] tmpbuf_;
    }
    bufferSize_ = size;
    tmpbuf_ = new unsigned char[size];
}

/**
 * Generate an image with fixed value for all pixels.
 */
void MUCamSource::GenerateImage()
{
    if (tmpbuf_ != NULL && MUCam_getFrame(hCameras_[currentCam_], tmpbuf_, 0))
    {
        unsigned int size = width_ * height_;
        switch(bitDepth_ * colorChannel_)
        {
            case 8:
                CoverImage8(tmpbuf_, img_.GetPixelsRW(), size, bytesPerPixel_);
                break;
            case 16:
                CoverImage16(tmpbuf_, img_.GetPixelsRW(), size, bytesPerPixel_);
                break;
            case 24:
                CoverImage24(tmpbuf_, img_.GetPixelsRW(), size, bytesPerPixel_);
                break;
            case 48:
                CoverImage48(tmpbuf_, img_.GetPixelsRW(), size, bytesPerPixel_);
                break;
            default:
                break;
        }
    }
}

/**
 * Load Motic cameras.
 */
int MUCamSource::FindMoticCameras()
{
    cameraCnt_ = 0;
    DevicesVec_.clear();
    char sName[Camera_Name_Len];
    MUCam_Handle hCamera = MUCam_findCamera();
    while (hCamera)
    {
        memset(sName, 0, Camera_Name_Len);
        hCameras_[cameraCnt_++] = hCamera;
        GetMotiCamNAME(hCamera, sName);
        //memcpy(sName, GetMotiCamNAME(hCamera), Camera_Name_Len);
        DevicesVec_.push_back(sName);
        hCamera = MUCam_findCamera();
    }
    
    return 0;
}

/**
 * Get motic camera name.
 */
void MUCamSource::GetMotiCamNAME(MUCam_Handle hCamera, char* sName)
{
    if (hCamera == 0) {
       return;
    }
    memset(sName, 0, Camera_Name_Len);
    switch (MUCam_getType(hCamera))
    {
        case MUCAM_TYPE_MC1001:
        case MUCAM_TYPE_MC1002:
        case MUCAM_TYPE_MC2001B:
        case MUCAM_TYPE_MC3111:
            strncpy(sName, "Motic 1.3MP", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MC2001:
        case MUCAM_TYPE_MC2002:
        case MUCAM_TYPE_MC3222:
        case MUCAM_TYPE_MC3022:
            strncpy(sName, "Motic 2.0MP", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MC3001:
            strncpy(sName, "Motic 3.0MP", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MC5001:
            strncpy(sName, "Motic 5.0MP", Camera_Name_Len);
            break;
        case MUCAM_TYPE_SWIFT_MC1002:
            strncpy(sName, "SwiftCam 2", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MA205:
            strncpy(sName, "Motic MA205", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MA285:
            strncpy(sName, "Motic MA285", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MA252:
            strncpy(sName, "Motic MA252", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MA282:
            strncpy(sName, "Motic MA282", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MC10M:
            strncpy(sName, "Moticam 10", Camera_Name_Len);
            break;
        case MUCAM_TYPE_MC580:
            strncpy(sName, "Moticam 580", Camera_Name_Len);
            break;
        case MUCAM_TYPE_SWIFT_MC3001:
            strncpy(sName, "Swift HR3", Camera_Name_Len);
            break;
        case MUCAM_TYPE_SWIFT_MC3222:
            strncpy(sName, "Swift HR2", Camera_Name_Len);
            break;
        case MUCAM_TYPE_SWIFT_MC3111:
            strncpy(sName, "Swift-Cam 2", Camera_Name_Len);
            break;
        default:
            strncpy(sName, "Unknow Device", Camera_Name_Len);
            break;
    }
    
}

int MUCamSource::InitDevice()
{
    //Bining
    InitBinning();
    
    InitPixelType();
    
    InitGain();
    
    InitExposure();
    
    
    // synchronize all properties
    // --------------------------
    
    int ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;
    
    // setup the buffer
    // ----------------
    ret = ResizeImageBuffer();
    if (ret != DEVICE_OK)
        return ret;
    
    
    return DEVICE_OK;
}

void MUCamSource::InitBinning()
{
    BinningsVec_.clear();
    int cnt = MUCam_getBinningCount(hCameras_[currentCam_]);
    int x[6], y[6];
    MUCam_getBinningList(hCameras_[currentCam_], x, y);
    for(int i = 0; i < cnt; i++)
    {
        BinningsVec_.push_back(x[i]);
        BinningsVec_.push_back(y[i]);
    }
    binning_ = 1;
    
    // binning
    CPropertyAction *pAct = new CPropertyAction (this, &MUCamSource::OnBinning);
    int ret = CreateProperty(MM::g_Keyword_Binning,
                             CDeviceUtils::ConvertToString(1<<binning_), MM::Integer, false, pAct);
    SetBinning(1<<binning_);
    //assert(ret == DEVICE_OK);
    
    vector<string> binningValues;
    for(int i = 0; i < cnt; i++)
    {
        binningValues.push_back(CDeviceUtils::ConvertToString(1<<i));
    }
    
    ret = SetAllowedValues(MM::g_Keyword_Binning, binningValues);

    assert(ret == DEVICE_OK);
}

void MUCamSource::InitPixelType()
{
    bitCnt_ = bitDepth_ * bytesPerPixel_;
    // pixel type
    CPropertyAction*pAct = new CPropertyAction (this, &MUCamSource::OnPixelType);
    int ret;
    vector<string> pixelTypeValues;
    if(colorChannel_ == 1)
    {
        ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
        pixelTypeValues.push_back(g_PixelType_8bit);
        // if(MIDP_Has16Bits() == 0)
        {
            pixelTypeValues.push_back(g_PixelType_16bit);
        }
    }
    else if(colorChannel_ == 3)
    {
        ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, MM::String, false, pAct);
        pixelTypeValues.push_back(g_PixelType_8bit);
        pixelTypeValues.push_back(g_PixelType_32bitRGB);
        // if(MIDP_Has16Bits() == 0)
        {
            pixelTypeValues.push_back(g_PixelType_16bit);
            pixelTypeValues.push_back(g_PixelType_64bitRGB);
        }
        bytesPerPixel_ = 4;
    }
    ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
}

void MUCamSource::InitGain()
{
    int cnt = MUCam_getGainCount(hCameras_[currentCam_]);
    if (cnt <= 0) return;
    if (gains_ != 0)
    {
        delete [] gains_;
        gains_ = 0;
    }
    gains_ = new float[cnt];
    if (MUCam_getGainList(hCameras_[currentCam_], gains_))
    {
        int gainRed, gainGreen, gainBlue;
        CPropertyAction *pAct = new CPropertyAction (this, &MUCamSource::OnGain);
        CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, false, pAct);
        if(MUCam_setRGBGainValue(hCameras_[currentCam_], 1.0, 1.0, 1.0, &gainRed, &gainGreen, &gainBlue))
            gain_ = gains_[gainRed];
        SetPropertyLimits(MM::g_Keyword_Gain, gains_[0], gains_[cnt - 1]);
    }
}

void MUCamSource::InitExposure()
{
    MUCam_getExposureRange(hCameras_[currentCam_], &exposureMin_, &exposureMax_);
    exposureMs_ = 10.0;
    MUCam_setExposure(hCameras_[currentCam_], (float)exposureMs_);
    char buf[10];
    sprintf(buf, "%0.1f", (float)exposureMs_);
    CPropertyAction *pAct = new CPropertyAction (this, &MUCamSource::OnExposure);
    int ret = CreateProperty(MM::g_Keyword_Exposure, buf, MM::Float, false, pAct);
    assert(ret == DEVICE_OK);
    SetPropertyLimits(MM::g_Keyword_Exposure, (double)exposureMin_, (double)exposureMax_);
    // m_dExposurems = 10.0;
}

bool MUCamSource::OpenCamera(MUCam_Handle hCamera)
{
    if(hCamera == 0) return false;
    if(MUCam_openCamera(hCamera))
    {
        int binCount = MUCam_getBinningCount(hCamera);
        if(binCount <= 0)
        {
            MUCam_closeCamera(hCamera);
            return false;
        }
        
        // by default we select binning 0 to display
        if(MUCam_setBinningIndex(hCamera, 0))
        {
            binning_ = 0;
        }
        // by default we set the image color depth 8bits
        bitDepth_ = 8;
        if(MUCam_setBitCount(hCamera, 8))
        {
            bitDepth_ = 8;
        }
        // Frame format
        colorChannel_ = 3;
        MUCam_Format fmt = MUCam_getFrameFormat(hCamera);
        if(fmt == MUCAM_FORMAT_MONOCHROME)
        {
            // Monochrome camera
            colorChannel_ = 1;
        }
        //@Exposure
        MUCam_getExposureRange(hCamera, &exposureMin_, &exposureMax_);
        float fval = 10.0f;
        MUCam_setExposure(hCamera, fval);
        exposureMs_ = fval;
        return true;
    }
    return false;
}



bool MUCamSource::LoadFunctions()
{
    if (!handle_) handle_ = dlopen("/usr/local/lib/libMUCam.dylib",RTLD_NOW);
    if (!handle_) return false;
    MUCam_findCamera = (MUCam_findCameraUPP)dlsym(handle_, "MUCam_findCamera");
    MUCam_getType = (MUCam_getTypeUPP)dlsym(handle_, "MUCam_getType");
    MUCam_openCamera = (MUCam_openCameraUPP)dlsym(handle_, "MUCam_openCamera");
    MUCam_getBinningCount = (MUCam_getBinningCountUPP)dlsym(handle_, "MUCam_getBinningCount");
    MUCam_getBinningList = (MUCam_getBinningListUPP)dlsym(handle_, "MUCam_getBinningList");
    MUCam_getBinningType = (MUCam_getBinningTypeUPP)dlsym(handle_, "MUCam_getBinningType");
    MUCam_setBinningIndex = (MUCam_setBinningIndexUPP)dlsym(handle_, "MUCam_setBinningIndex");
    MUCam_getFrame = (MUCam_getFrameUPP)dlsym(handle_, "MUCam_getFrame");
    MUCam_setExposure = (MUCam_setExposureUPP)dlsym(handle_, "MUCam_setExposure");
    MUCam_setBitCount = (MUCam_setBitCountUPP)dlsym(handle_, "MUCam_setBitCount");
    MUCam_getExposureRange = (MUCam_getExposureRangeUPP)dlsym(handle_, "MUCam_getExposureRange");
    MUCam_closeCamera = (MUCam_closeCameraUPP)dlsym(handle_, "MUCam_closeCamera");
    MUCam_getFrameFormat = (MUCam_getFrameFormatUPP)dlsym(handle_, "MUCam_getFrameFormat");
    MUCam_getGainCount = (MUCam_getGainCountUPP)dlsym(handle_, "MUCam_getGainCount");
    MUCam_getGainList = (MUCam_getGainListUPP)dlsym(handle_, "MUCam_getGainList");
    MUCam_setRGBGainIndex = (MUCam_setRGBGainIndexUPP)dlsym(handle_, "MUCam_setRGBGainIndex");
    MUCam_setRGBGainValue = (MUCam_setRGBGainValueUPP)dlsym(handle_, "MUCam_setRGBGainValue");
    MUCam_releaseCamera = (MUCam_releaseCameraUPP)dlsym(handle_, "MUCam_releaseCamera");
    return true;
}

void MUCamSource::UnloadFunctions()
{
    if (handle_)
    {
        dlclose(handle_);
        handle_ = 0;
    }
}

