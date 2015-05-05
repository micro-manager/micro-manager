// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     University of California, San Francisco, 2014
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

#include "IIDCFeature.h"

#include "IIDCError.h"

#include <boost/lexical_cast.hpp>


namespace IIDC {

Feature::Feature(boost::shared_ptr<Camera> camera, dc1394camera_t* libdc1394camera,
      dc1394feature_t libdc1394feature) :
   camera_(camera), libdc1394camera_(libdc1394camera), libdc1394feature_(libdc1394feature)
{
   {
      dc1394bool_t flag;
      dc1394error_t err;
      err = dc1394_feature_is_present(GetLibDC1394Camera(), GetLibDC1394Feature(), &flag);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot check if " + GetName() + " feature is present");
      isPresent_ = flag != DC1394_FALSE;
   }

   if (!isPresent_)
   {
      hasAbsolute_ = false;
      isReadable_ = false;
      isSwitchable_ = false;
      return;
   }

   {
      dc1394bool_t flag;
      dc1394error_t err;
      err = dc1394_feature_has_absolute_control(GetLibDC1394Camera(), GetLibDC1394Feature(), &flag);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot check if " + GetName() + " feature allows absolute value control");
      hasAbsolute_ = flag != DC1394_FALSE;
   }

   {
      dc1394bool_t flag;
      dc1394error_t err;
      err = dc1394_feature_is_readable(GetLibDC1394Camera(), GetLibDC1394Feature(), &flag);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot check if " + GetName() + " feature supports readout");
      isReadable_ = flag != DC1394_FALSE;
   }

   {
      dc1394bool_t flag;
      dc1394error_t err;
      err = dc1394_feature_is_switchable(GetLibDC1394Camera(), GetLibDC1394Feature(), &flag);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot check if " + GetName() + " feature is switchable");
      isSwitchable_ = flag != DC1394_FALSE;
   }
}


bool
Feature::GetAbsoluteControl()
{
   if (!HasAbsoluteControl())
      return false;

   dc1394switch_t flag;
   dc1394error_t err;
   err = dc1394_feature_get_absolute_control(GetLibDC1394Camera(), GetLibDC1394Feature(), &flag);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot check if absolute value control is enabled for " + GetName() + " feature");
   return flag != DC1394_OFF;
}


void
Feature::SetAbsoluteControl(bool flag)
{
   if (!HasAbsoluteControl())
      throw Error("Absolute value control is not supported for " + GetName() + " feature");

   dc1394error_t err;
   err = dc1394_feature_set_absolute_control(GetLibDC1394Camera(), GetLibDC1394Feature(),
         flag ? DC1394_ON : DC1394_OFF);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot " + std::string(flag ? "enable" : "disable") +
            " absolute value control for " + GetName() + " feature");
}


bool
Feature::GetOnOff()
{
   if (!IsSwitchable())
      return true;

   dc1394switch_t flag;
   dc1394error_t err;
   err = dc1394_feature_get_power(GetLibDC1394Camera(), GetLibDC1394Feature(), &flag);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot check if " + GetName() + " feature is enabled");
   return flag != DC1394_OFF;
}


void
Feature::SetOnOff(bool flag)
{
   if (!IsSwitchable())
      throw Error("Switching on or off is not supported for " + GetName() + " feature");

   dc1394error_t err;
   err = dc1394_feature_set_power(GetLibDC1394Camera(), GetLibDC1394Feature(),
         flag ? DC1394_ON : DC1394_OFF);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot " + std::string(flag ? "enable" : "disable") + " " +
            GetName() + " feature");
}


ModalFeature::ModalFeature() :
   Feature(boost::shared_ptr<Camera>(), NULL, dc1394feature_t(0)), // Actual args come from concrete subclass
   hasOnePush_(false), hasAuto_(false), hasManual_(false)
{
   dc1394feature_modes_t modes;
   dc1394error_t err;
   err = dc1394_feature_get_modes(GetLibDC1394Camera(), GetLibDC1394Feature(), &modes);
   for (unsigned i = 0; i < modes.num; ++i)
   {
      dc1394feature_mode_t mode = modes.modes[i];
      switch (mode)
      {
         case DC1394_FEATURE_MODE_MANUAL: hasManual_ = true; break;
         case DC1394_FEATURE_MODE_AUTO: hasAuto_ = true; break;
         case DC1394_FEATURE_MODE_ONE_PUSH_AUTO: hasOnePush_ = true; break;
      }
   }
}


void
ModalFeature::SetOnePush()
{
   if (!HasOnePushMode() || !HasManualMode())
      throw Error("One-push mode is not supported for " + GetName() + " feature");
   if (GetAutoMode())
      throw Error("Cannot enable one-push control for " + GetName() +
            " feature because it is in auto mode");

   dc1394error_t err;
   err = dc1394_feature_set_mode(GetLibDC1394Camera(), GetLibDC1394Feature(), DC1394_FEATURE_MODE_ONE_PUSH_AUTO);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot enable one-push control for " + GetName() + " feature");
}


bool
ModalFeature::GetAutoMode()
{
   if (!HasAutoMode())
      return false;
   if (!HasManualMode())
      return true;

   dc1394feature_mode_t mode;
   dc1394error_t err;
   err = dc1394_feature_get_mode(GetLibDC1394Camera(), GetLibDC1394Feature(), &mode);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get mode (manual/auto/one-push) for " + GetName() + " feature");

   // Consider one-push to be part of manual mode.
   return (mode == DC1394_FEATURE_MODE_AUTO);
}


void
ModalFeature::SetAutoMode(bool flag)
{
   if (!HasAutoMode() && flag)
      throw Error("Auto mode is not supported for " + GetName() + " feature");
   if (!HasManualMode() && flag)
      throw Error("Manual mode is not supported for " + GetName() + " feature");

   dc1394feature_mode_t mode = flag ? DC1394_FEATURE_MODE_AUTO : DC1394_FEATURE_MODE_MANUAL;
   dc1394error_t err;
   err = dc1394_feature_set_mode(GetLibDC1394Camera(), GetLibDC1394Feature(), mode);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot enable " + std::string(flag ? "auto" : "manual") +
            " mode for " + GetName() + " feature");
}


std::pair<uint32_t, uint32_t>
ScalarFeature::GetMinMax()
{
   uint32_t min, max;
   dc1394error_t err;
   err = dc1394_feature_get_boundaries(GetLibDC1394Camera(), GetLibDC1394Feature(), &min, &max);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get available range for " + GetName() + " feature");
   return std::make_pair(min, max);
}


std::pair<float, float>
ScalarFeature::GetAbsoluteMinMax()
{
   if (!HasAbsoluteControl())
      throw Error("Absolute value control is not supported for " + GetName() + " feature");

   float min, max;
   dc1394error_t err;
   err = dc1394_feature_get_absolute_boundaries(GetLibDC1394Camera(), GetLibDC1394Feature(), &min, &max);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get available absolute value range for " + GetName() + " feature");
   return std::make_pair(min, max);
}


uint32_t
ScalarFeature::GetValue()
{
   uint32_t value;
   dc1394error_t err;
   err = dc1394_feature_get_value(GetLibDC1394Camera(), GetLibDC1394Feature(), &value);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get value of " + GetName() + " feature");
   return value;
}


void
ScalarFeature::SetValue(uint32_t value)
{
   std::pair<uint32_t, uint32_t> range = GetMinMax();
   if (value < range.first || value > range.second)
      throw Error("Value " + boost::lexical_cast<std::string>(value) +
            " is not in the current allowed range (" +
            boost::lexical_cast<std::string>(range.first) + "-" +
            boost::lexical_cast<std::string>(range.second) + ") for " +
            GetName() + " feature");

   dc1394error_t err;
   err = dc1394_feature_set_value(GetLibDC1394Camera(), GetLibDC1394Feature(), value);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot set value of " + GetName() + " feature to " +
            boost::lexical_cast<std::string>(value));
}


float
ScalarFeature::GetAbsoluteValue()
{
   if (!HasAbsoluteControl())
      throw Error("Absolute value control is not supported for " + GetName() + " feature");

   float value;
   dc1394error_t err;
   err = dc1394_feature_get_absolute_value(GetLibDC1394Camera(), GetLibDC1394Feature(), &value);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get absolute value of " + GetName() + " feature");
   return value;
}


void
ScalarFeature::SetAbsoluteValue(float value)
{
   if (!HasAbsoluteControl())
      throw Error("Absolute value control is not supported for " + GetName() + " feature");

   std::pair<float, float> range = GetAbsoluteMinMax();
   if (value < range.first || value > range.second)
      throw Error("Value " + boost::lexical_cast<std::string>(value) + " " +
            GetAbsoluteUnits() + " is not in the current allowed range (" +
            boost::lexical_cast<std::string>(range.first) + "-" +
            boost::lexical_cast<std::string>(range.second) + ") for " +
            GetName() + " feature");

   dc1394error_t err;
   err = dc1394_feature_set_absolute_value(GetLibDC1394Camera(), GetLibDC1394Feature(), value);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot set value of " + GetName() + " feature to " +
            boost::lexical_cast<std::string>(value));
}

} // namespace IIDC
