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

#pragma once

#include <dc1394/dc1394.h>
#include <dc1394/vendor/avt.h>
#ifdef _MSC_VER
#undef restrict
#endif

#include <boost/enable_shared_from_this.hpp>
#include <boost/utility.hpp>


namespace IIDC {

class Camera;


const uint32_t VENDOR_ID_AVT = 0x000a47;


// Advanced features for Allied Vision Technologies cameras
class VendorAVT : boost::noncopyable, public boost::enable_shared_from_this<VendorAVT>
{
   bool isPresent_;
   Camera* camera_; // parent
   uint32_t microcontrollerVersion_;
   uint32_t avtCameraID_;
   uint32_t fpgaVersion_;
   dc1394_avt_adv_feature_info_t libdc1394AVTFeatureInfo_;

   uint32_t minExtendedShutterUs_;

public:
   VendorAVT(Camera* camera);
   ~VendorAVT();

   bool IsPresent() const { return isPresent_; }

   uint32_t GetMicrocontrollerVersion();
   uint32_t GetAVTCameraID();
   uint32_t GetFPGAVersion();

   bool HasExtendedShutter();
   uint32_t GetExtendedShutterMinUs();
   uint32_t GetExtendedShutterMaxUs();
   uint32_t GetExtendedShutterUs();
   void SetExtendedShutterUs(uint32_t shutterUs);
};

} // namespace IIDC
