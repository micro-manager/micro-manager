///////////////////////////////////////////////////////////////////////////////
// FILE:          CanonEDSDK.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
// ----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Canon EDSDK 
// COPYRIGHT:     USCF, 2010
// AUTHOR:        Nico Stuurman
// License:       LGPL
//
//

#include "CanonEDSDK.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes

CanonEDCamera::CanonEDCamera()
{
}


CanonEDCamera::~CanonEDCamera()
{
   Shutdown();
}

void CanonEDCamera::GetName(char* name) const
{
}

int CanonEDCamera::Shutdown()
{
}

int CanonEDCamera::Initialize()
{
   return DEVICE_OK;
}


      // MMCamera API
int CanonEDCamera::SnapImage()
{
   return DEVICE_OK;
}

const unsigned char* CanonEDCamera::GetImageBuffer()
{
   return 0;
}

unsigned CanonEDCamera::GetImageWidth() const
{
   return 0;
}

unsigned CanonEDCamera::GetImageHeight() const
{
   return 0;
}

unsigned CanonEDCamera::GetImageBytesPerPixel() const
{
   return 0;
}

unsigned CanonEDCamera::GetBitDepth() const
{
   return 0;
}

long CanonEDCamera::GetImageBufferSize() const
{
   return 0;
}

double CanonEDCamera::GetExposure() const
{
   return 0;
}

void CanonEDCamera::SetExposure(double exp)
{
}

int CanonEDCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   return DEVICE_OK;
}

int CanonEDCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   return DEVICE_OK;
}

int CanonEDCamera::ClearROI()
{
   return DEVICE_OK;
}

int CanonEDCamera::StartSequenceAcquisition(double interval)
{
   return DEVICE_OK;
}

int CanonEDCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   return DEVICE_OK;
}

int CanonEDCamera::StopSequenceAcquisition()
{
   return DEVICE_OK;
}

int CanonEDCamera::InsertImage()
{
   return DEVICE_OK;
}

/*
int CanonEDCamera::ThreadRun()
{
   return DEVICE_OK;
}
*/

bool CanonEDCamera::IsCapturing()
{
   return false;
}

int CanonEDCamera::GetBinning() const
{
   return 1;                                                   
}

int SetBinning(int bS)
{
   return DEVICE_OK;
} 



// action interface
int CanonEDCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType )
{
   return DEVICE_OK;
}

int CanonEDCamera::OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType )
{
   return DEVICE_OK;
}

