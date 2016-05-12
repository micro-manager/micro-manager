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
#ifdef _MSC_VER
#undef restrict
#endif

#include <boost/shared_ptr.hpp>
#include <string>
#include <utility>


namespace IIDC {

class Camera;


/*
 * An adapter class for feature access
 */
class Feature
{
   boost::shared_ptr<Camera> camera_;
   dc1394camera_t* libdc1394camera_;
   dc1394feature_t libdc1394feature_;

   bool isPresent_, hasAbsolute_, isReadable_, isSwitchable_;

protected:
   dc1394camera_t* GetLibDC1394Camera() const { return libdc1394camera_; }
   dc1394feature_t GetLibDC1394Feature() const { return libdc1394feature_; }

public:
   Feature(boost::shared_ptr<Camera> camera, dc1394camera_t* libdc1394camera,
         dc1394feature_t libdc1394feature);
   virtual ~Feature() {}

   virtual std::string GetName() const { return dc1394_feature_get_string(libdc1394feature_); }

   virtual bool IsPresent() const { return isPresent_; }
   virtual bool HasAbsoluteControl() const { return hasAbsolute_; }
   virtual bool IsReadable() const { return isReadable_; }
   virtual bool IsSwitchable() const { return isSwitchable_; }

   virtual bool GetAbsoluteControl();
   virtual void SetAbsoluteControl(bool flag);
   virtual bool GetOnOff();
   virtual void SetOnOff(bool flag);
};


/*
 * Most features, but not TRIGGER_MODE
 */
class ModalFeature : public virtual Feature
{
   bool hasOnePush_, hasAuto_, hasManual_;

protected:
   ModalFeature();

public:
   virtual bool HasOnePushMode() const { return hasOnePush_; }
   virtual bool HasAutoMode() const { return hasAuto_; }
   virtual bool HasManualMode() const { return hasManual_; }

   virtual void SetOnePush();
   virtual bool GetAutoMode();
   virtual void SetAutoMode(bool flag); // false -> manual mode
};


/*
 * Features with a single "value"
 */
class ScalarFeature : public virtual Feature
{
protected:
   ScalarFeature() :
      Feature(boost::shared_ptr<Camera>(), NULL, dc1394feature_t(0)) // Actual args come from concrete subclass
   {}

public:
   virtual std::string GetAbsoluteUnits() const { return "(unspecified units)"; }

   // The range is _not_ memoized because it may change depending on the value
   // of other features.
   virtual std::pair<uint32_t, uint32_t> GetMinMax();
   virtual std::pair<float, float> GetAbsoluteMinMax();

   virtual uint32_t GetValue();
   virtual void SetValue(uint32_t value);
   virtual float GetAbsoluteValue();
   virtual void SetAbsoluteValue(float value);
};


/*
 * Concrete feature classes
 */


#define IIDC_DEFINE_FEATURE_CTOR(classname, featureEnum) \
   classname(boost::shared_ptr<Camera> camera, dc1394camera_t* libdc1394camera) : \
      Feature(camera, libdc1394camera, featureEnum) \
   {}


class BrightnessFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(BrightnessFeature, DC1394_FEATURE_BRIGHTNESS);
   virtual std::string GetAbsoluteUnits() const { return "%"; }
};


class AutoExposureFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(AutoExposureFeature, DC1394_FEATURE_EXPOSURE);
   virtual std::string GetName() const { return "Auto Exposure"; } // Default "Exposure" is unhelpful.
   virtual std::string GetAbsoluteUnits() const { return "EV"; }
};


class SharpnessFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(SharpnessFeature, DC1394_FEATURE_SHARPNESS);
};


// TODO WhiteBalanceFeature : public ModalFeature (or two separate classes?)


class HueFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(HueFeature, DC1394_FEATURE_HUE);
   virtual std::string GetAbsoluteUnits() const { return "deg"; }
};


class SaturationFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(SaturationFeature, DC1394_FEATURE_SATURATION);
   virtual std::string GetAbsoluteUnits() const { return "%"; }
};


class GammaFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(GammaFeature, DC1394_FEATURE_GAMMA);
   virtual std::string GetAbsoluteUnits() const { return "(dimensionless)"; }
};


class ShutterFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(ShutterFeature, DC1394_FEATURE_SHUTTER);
   virtual std::string GetAbsoluteUnits() const { return "s"; }
};


class GainFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(GainFeature, DC1394_FEATURE_GAIN);
   virtual std::string GetAbsoluteUnits() const { return "dB"; }
};


class IrisFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(IrisFeature, DC1394_FEATURE_IRIS);
   virtual std::string GetAbsoluteUnits() const { return "F"; }
};


class FocusFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(FocusFeature, DC1394_FEATURE_FOCUS);
   virtual std::string GetAbsoluteUnits() const { return "m"; }
};


// TODO TemperatureFeature : public ModalFeature, ScalarFeature (redirect value to target)
// TODO TriggerModeFeature (possibly multiple features)
// TODO TriggerDelayFeature
// TODO WhiteShadingFeature


class FrameRateFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(FrameRateFeature, DC1394_FEATURE_FRAME_RATE);
   virtual std::string GetAbsoluteUnits() const { return "fps"; }
};


class ZoomFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(ZoomFeature, DC1394_FEATURE_ZOOM);
   virtual std::string GetAbsoluteUnits() const { return "x"; }
};


class PanFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(PanFeature, DC1394_FEATURE_PAN);
   virtual std::string GetAbsoluteUnits() const { return "deg"; }
};


class TiltFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(TiltFeature, DC1394_FEATURE_TILT);
   virtual std::string GetAbsoluteUnits() const { return "deg"; }
};


class OpticalFilterFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(OpticalFilterFeature, DC1394_FEATURE_OPTICAL_FILTER);
};


class CaptureSizeFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(CaptureSizeFeature, DC1394_FEATURE_CAPTURE_SIZE);
};


class CaptureQualityFeature : public ModalFeature, public ScalarFeature
{
public:
   IIDC_DEFINE_FEATURE_CTOR(CaptureQualityFeature, DC1394_FEATURE_CAPTURE_QUALITY);
};


#undef IIDC_DEFINE_FEATURE_CTOR // Do not leak outside of this header

} // namespace IIDC
