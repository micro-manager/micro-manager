// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     2014-2015, Regents of the University of California
//                2016, Open Imaging, Inc.
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#include "IIDCVendorAVT.h"

#include "IIDCCamera.h"
#include "IIDCError.h"


namespace IIDC {

VendorAVT::VendorAVT(Camera* camera) :
   isPresent_(false),
   camera_(camera)
{
   if (camera_->GetVendorID() != VENDOR_ID_AVT)
      return;
   isPresent_ = true;

   dc1394error_t err;
   uint32_t microcontrollerType;
   err = dc1394_avt_get_version(camera_->libdc1394camera_,
         &microcontrollerType, &microcontrollerVersion_,
         &avtCameraID_, &fpgaVersion_);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot read AVT version info");
   // Note: libdc1394 only reads the older, 16-bit BCD version numbers. Newer
   // AVT cameras also have 32-bit version numbers for the uC and FPGA.

   err = dc1394_avt_get_advanced_feature_inquiry(camera_->libdc1394camera_,
         &libdc1394AVTFeatureInfo_);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot read AVT acvanced feature availability");

   // The minimum possible shutter time is camera-dependent, but constant for a
   // given camera. So we determine it once here.
   // Note: 'extended' is misspelled by libdc1394 here.
   err = dc1394_avt_set_extented_shutter(camera_->libdc1394camera_, 1);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot set AVT extended shutter value");
   err = dc1394_avt_get_extented_shutter(camera_->libdc1394camera_,
         &minExtendedShutterUs_);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get AVT extended shutter value");
}


VendorAVT::~VendorAVT()
{
   // Nothing to do so far
}


uint32_t
VendorAVT::GetMicrocontrollerVersion()
{
   if (!isPresent_)
      throw Error("AVT advanced features not available");
   return microcontrollerVersion_;
}


uint32_t
VendorAVT::GetAVTCameraID()
{
   if (!isPresent_)
      throw Error("AVT advanced features not available");
   return avtCameraID_;
}


uint32_t
VendorAVT::GetFPGAVersion()
{
   if (!isPresent_)
      throw Error("AVT advanced features not available");
   return fpgaVersion_;
}


bool
VendorAVT::HasExtendedShutter()
{
   if (!isPresent_)
      return false;
   return libdc1394AVTFeatureInfo_.ExtdShutter != DC1394_FALSE;
}


uint32_t
VendorAVT::GetExtendedShutterMinUs()
{
   if (!HasExtendedShutter())
      throw Error("AVT extended shutter feature not available");
   return minExtendedShutterUs_;
}


uint32_t
VendorAVT::GetExtendedShutterMaxUs()
{
   if (!HasExtendedShutter())
      throw Error("AVT extended shutter feature not available");
   return 0x3FFFFFF; // 26 bits
}


uint32_t
VendorAVT::GetExtendedShutterUs()
{
   if (!HasExtendedShutter())
      throw Error("AVT extended shutter feature not available");
   uint32_t shutterUs;
   dc1394error_t err;
   err = dc1394_avt_get_extented_shutter(camera_->libdc1394camera_, &shutterUs);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get AVT extended shutter value");
   return shutterUs;
}


void
VendorAVT::SetExtendedShutterUs(uint32_t shutterUs)
{
   if (!HasExtendedShutter())
      throw Error("AVT extended shutter feature not available");
   if (shutterUs > GetExtendedShutterMaxUs())
      throw Error("AVT extended shutter value above allowed maximum");
   if (shutterUs < GetExtendedShutterMinUs())
      throw Error("AVT extended shutter value below allowed minimum");
   dc1394error_t err;
   err = dc1394_avt_set_extented_shutter(camera_->libdc1394camera_, shutterUs);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot set AVT extended shutter value");
}

} // namespace IIDC
