// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#include "LoggedSetting.h"

#include "SequenceTester.h" // For InterDevice; TODO make separate header

#include <algorithm>
#include <boost/lexical_cast.hpp>
#include <boost/utility.hpp>
#include <iterator>
#include <string>
#include <vector>


LoggedSetting::LoggedSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name) :
   logger_(logger),
   device_(device),
   name_(name)
{
}


void
LoggedSetting::MarkBusy()
{
   if (busySetting_)
      busySetting_->Set();
}


int
LoggedSetting::GetSequenceMaxLength(long& len) const
{
   len = GetSequenceMaxLength();
   return DEVICE_OK;
}


long
LoggedSetting::GetSequenceMaxLength() const
{
   if (!sequenceMaxLengthSetting_)
      return 0;
   return sequenceMaxLengthSetting_->Get();
}


void
LoggedSetting::ReceiveEdgeTrigger()
{
   GetLogger()->FireOneShot(device_->GetDeviceName(), "trig-in:" + GetName());
   if (sequenceMaxLengthSetting_->Get() > 0)
      HandleEdgeTrigger();
}


void
LoggedSetting::ConnectToEdgeTriggerSource(EdgeTriggerSignal& source)
{
   edgeTriggerConnection_ = source.connect(
         EdgeTriggerSignal::slot_type(&Self::ReceiveEdgeTrigger, this).
         track(shared_from_this()));
}


void
LoggedSetting::DisconnectEdgeTriggerSource()
{
   edgeTriggerConnection_.disconnect();
}


BoolSetting::BoolSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      bool initialValue) :
   LoggedSetting(logger, device, name),
   sequenceRunning_(false),
   nextTriggerIndex_(0)
{
   GetLogger()->SetBool(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
BoolSetting::Set(bool newValue)
{
   GetLogger()->SetBool(GetDevice()->GetDeviceName(), GetName(), newValue);
   FirePostSetSignal();
   return DEVICE_OK;
}


int
BoolSetting::Get(bool& value) const
{
   value = Get();
   return DEVICE_OK;
}


bool
BoolSetting::Get() const
{
   return GetLogger()->GetBool(GetDevice()->GetDeviceName(), GetName());
}


int
BoolSetting::SetTriggerSequence(const std::vector<uint8_t>& sequence)
{
   if (sequence.size() > GetSequenceMaxLength())
      return DEVICE_SEQUENCE_TOO_LARGE;
   triggerSequence_ = sequence;
   return DEVICE_OK;
}


int
BoolSetting::StartTriggerSequence()
{
   if (GetSequenceMaxLength() == 0)
      return DEVICE_ERR;
   nextTriggerIndex_ = 0;
   sequenceRunning_ = true;
   return DEVICE_OK;
}


int
BoolSetting::StopTriggerSequence()
{
   sequenceRunning_ = false;
   return DEVICE_OK;
}


void
BoolSetting::HandleEdgeTrigger()
{
   if (!sequenceRunning_ || triggerSequence_.empty())
      return;

   uint8_t newValue = triggerSequence_[nextTriggerIndex_++];
   if (nextTriggerIndex_ >= triggerSequence_.size())
      nextTriggerIndex_ = 0;

   GetLogger()->SetBool(GetDevice()->GetDeviceName(), GetName(),
         static_cast<bool>(newValue));
   FirePostSetSignal();
}


namespace
{
   struct BoolMapper
   {
      std::string onString_;
      uint8_t operator()(const std::string& src)
      { return static_cast<uint8_t>(src == onString_); }
   };

   std::vector<uint8_t>
   MapBoolVector(const std::vector<std::string>& strVector,
         const std::string& onString)
   {
      BoolMapper mapper;
      mapper.onString_ = onString;

      std::vector<uint8_t> ret;
      ret.reserve(strVector.size());
      std::transform(strVector.begin(), strVector.end(),
            std::back_inserter(ret), mapper);
      return ret;
   }
}


MM::ActionFunctor*
BoolSetting::NewPropertyAction(PropertyDisplay displayMode)
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      BoolSetting& setting_;
      PropertyDisplay displayMode_;

   public:
      Functor(BoolSetting& setting, PropertyDisplay displayMode) :
         setting_(setting),
         displayMode_(displayMode)
      {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            bool v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            switch (displayMode_)
            {
               case ON_OFF:
                  pProp->Set(v ? "On" : "Off");
                  break;
               case YES_NO:
                  pProp->Set(v ? "Yes" : "No");
                  break;
               case ONE_ZERO:
                  pProp->Set(v ? 1L : 0L);
                  break;
            }
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            std::string strVal;
            long intVal;
            switch (displayMode_)
            {
               case ON_OFF:
                  pProp->Get(strVal);
                  return setting_.Set(strVal == "On");
               case YES_NO:
                  pProp->Get(strVal);
                  return setting_.Set(strVal == "Yes");
               case ONE_ZERO:
                  pProp->Get(intVal);
                  return setting_.Set(intVal != 0);
            }
         }
         else if (eAct == MM::IsSequenceable)
         {
            long len;
            int err = setting_.GetSequenceMaxLength(len);
            if (err != DEVICE_OK)
               return err;
            pProp->SetSequenceable(len);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterLoadSequence)
         {
            std::vector<std::string> strSeq = pProp->GetSequence();
            switch (displayMode_)
            {
               case ON_OFF:
                  return setting_.
                     SetTriggerSequence(MapBoolVector(strSeq, "On"));
               case YES_NO:
                  return setting_.
                     SetTriggerSequence(MapBoolVector(strSeq, "Yes"));
               case ONE_ZERO:
                  return setting_.
                     SetTriggerSequence(MapBoolVector(strSeq, "1"));
            }
            return DEVICE_OK;
         }
         else if (eAct == MM::StartSequence)
         {
            return setting_.StartTriggerSequence();
         }
         else if (eAct == MM::StopSequence)
         {
            return setting_.StopTriggerSequence();
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this, displayMode);
}


IntegerSetting::IntegerSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      long initialValue, bool hasMinMax, long minimum, long maximum) :
   LoggedSetting(logger, device, name),
   hasMinMax_(hasMinMax),
   min_(minimum),
   max_(maximum),
   sequenceRunning_(false),
   nextTriggerIndex_(0)
{
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
IntegerSetting::Set(long newValue)
{
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(), newValue);
   FirePostSetSignal();
   return DEVICE_OK;
}


int
IntegerSetting::Get(long& value) const
{
   value = Get();
   return DEVICE_OK;
}


long
IntegerSetting::Get() const
{
   return GetLogger()->GetInteger(GetDevice()->GetDeviceName(), GetName());
}


int
IntegerSetting::SetTriggerSequence(const std::vector<long>& sequence)
{
   if (sequence.size() > GetSequenceMaxLength())
      return DEVICE_SEQUENCE_TOO_LARGE;
   triggerSequence_ = sequence;
   return DEVICE_OK;
}


int
IntegerSetting::StartTriggerSequence()
{
   if (GetSequenceMaxLength() == 0)
      return DEVICE_ERR;
   nextTriggerIndex_ = 0;
   sequenceRunning_ = true;
   return DEVICE_OK;
}


int
IntegerSetting::StopTriggerSequence()
{
   sequenceRunning_ = false;
   return DEVICE_OK;
}


void
IntegerSetting::HandleEdgeTrigger()
{
   if (!sequenceRunning_ || triggerSequence_.empty())
      return;

   long newValue = triggerSequence_[nextTriggerIndex_++];
   if (nextTriggerIndex_ >= triggerSequence_.size())
      nextTriggerIndex_ = 0;

   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(), newValue);
   FirePostSetSignal();
}


namespace
{
   long IntegerMapper(const std::string& src)
   { return boost::lexical_cast<long>(src); }

   // Throws boost::bad_lexical_cast
   std::vector<long>
   MapIntegerVector(const std::vector<std::string>& strVector)
   {
      std::vector<long> ret;
      ret.reserve(strVector.size());
      std::transform(strVector.begin(), strVector.end(),
            std::back_inserter(ret), IntegerMapper);
      return ret;
   }
}


MM::ActionFunctor*
IntegerSetting::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      IntegerSetting& setting_;

   public:
      Functor(IntegerSetting& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            long v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            pProp->Set(v);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            long v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         else if (eAct == MM::IsSequenceable)
         {
            long len;
            int err = setting_.GetSequenceMaxLength(len);
            if (err != DEVICE_OK)
               return err;
            pProp->SetSequenceable(len);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterLoadSequence)
         {
            try
            {
               return setting_.
                  SetTriggerSequence(MapIntegerVector(pProp->GetSequence()));
            }
            catch (const boost::bad_lexical_cast&)
            {
               return DEVICE_ERR;
            }
         }
         else if (eAct == MM::StartSequence)
         {
            return setting_.StartTriggerSequence();
         }
         else if (eAct == MM::StopSequence)
         {
            return setting_.StopTriggerSequence();
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


FloatSetting::FloatSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      double initialValue, bool hasMinMax, double minimum, double maximum) :
   LoggedSetting(logger, device, name),
   hasMinMax_(hasMinMax),
   min_(minimum),
   max_(maximum),
   sequenceRunning_(false),
   nextTriggerIndex_(0)
{
   GetLogger()->SetFloat(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
FloatSetting::Set(double newValue)
{
   GetLogger()->SetFloat(GetDevice()->GetDeviceName(), GetName(), newValue);
   FirePostSetSignal();
   return DEVICE_OK;
}


int
FloatSetting::Get(double& value) const
{
   value = Get();
   return DEVICE_OK;
}


double
FloatSetting::Get() const
{
   return GetLogger()->GetFloat(GetDevice()->GetDeviceName(), GetName());
}


int
FloatSetting::SetTriggerSequence(const std::vector<double>& sequence)
{
   if (sequence.size() > GetSequenceMaxLength())
      return DEVICE_SEQUENCE_TOO_LARGE;
   triggerSequence_ = sequence;
   return DEVICE_OK;
}


int
FloatSetting::StartTriggerSequence()
{
   if (GetSequenceMaxLength() == 0)
      return DEVICE_ERR;
   nextTriggerIndex_ = 0;
   sequenceRunning_ = true;
   return DEVICE_OK;
}


int
FloatSetting::StopTriggerSequence()
{
   sequenceRunning_ = false;
   return DEVICE_OK;
}


void
FloatSetting::HandleEdgeTrigger()
{
   if (!sequenceRunning_ || triggerSequence_.empty())
      return;

   double newValue = triggerSequence_[nextTriggerIndex_++];
   if (nextTriggerIndex_ >= triggerSequence_.size())
      nextTriggerIndex_ = 0;

   GetLogger()->SetFloat(GetDevice()->GetDeviceName(), GetName(), newValue);
   FirePostSetSignal();
}


namespace
{
   double FloatMapper(const std::string& src)
   { return boost::lexical_cast<double>(src); }

   // Throws boost::bad_lexical_cast
   std::vector<double>
   MapFloatVector(const std::vector<std::string>& strVector)
   {
      std::vector<double> ret;
      ret.reserve(strVector.size());
      std::transform(strVector.begin(), strVector.end(),
            std::back_inserter(ret), FloatMapper);
      return ret;
   }
}


MM::ActionFunctor*
FloatSetting::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      FloatSetting& setting_;

   public:
      Functor(FloatSetting& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            double v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            pProp->Set(v);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            double v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         else if (eAct == MM::IsSequenceable)
         {
            long len;
            int err = setting_.GetSequenceMaxLength(len);
            if (err != DEVICE_OK)
               return err;
            pProp->SetSequenceable(len);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterLoadSequence)
         {
            try
            {
               return setting_.
                  SetTriggerSequence(MapFloatVector(pProp->GetSequence()));
            }
            catch (const boost::bad_lexical_cast&)
            {
               return DEVICE_ERR;
            }
         }
         else if (eAct == MM::StartSequence)
         {
            return setting_.StartTriggerSequence();
         }
         else if (eAct == MM::StopSequence)
         {
            return setting_.StopTriggerSequence();
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


StringSetting::StringSetting(SettingLogger* logger, InterDevice* device,
      const std::string& name, const std::string& initialValue) :
   LoggedSetting(logger, device, name)
{
   GetLogger()->SetString(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
StringSetting::Set(const std::string& newValue)
{
   GetLogger()->SetString(GetDevice()->GetDeviceName(), GetName(), newValue);
   FirePostSetSignal();
   return DEVICE_OK;
}


int
StringSetting::Get(std::string& value) const
{
   value = Get();
   return DEVICE_OK;
}


std::string
StringSetting::Get() const
{
   return GetLogger()->GetString(GetDevice()->GetDeviceName(), GetName());
}


MM::ActionFunctor*
StringSetting::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      StringSetting& setting_;

   public:
      Functor(StringSetting& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            std::string v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            pProp->Set(v.c_str());
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            std::string v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


OneShotSetting::OneShotSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name) :
   LoggedSetting(logger, device, name)
{
   GetLogger()->FireOneShot(GetDevice()->GetDeviceName(), GetName(), false);
}


int
OneShotSetting::Set()
{
   GetLogger()->FireOneShot(GetDevice()->GetDeviceName(), GetName());
   FirePostSetSignal();
   return DEVICE_OK;
}


CountDownSetting::CountDownSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name, long initialCount,
      long defaultIncrement) :
   LoggedSetting(logger, device, name),
   defaultIncrement_(defaultIncrement)
{
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(),
         initialCount, false);
}


int
CountDownSetting::Set(long increment)
{
   long oldCount =
      GetLogger()->GetInteger(GetDevice()->GetDeviceName(), GetName());
   long newCount = oldCount + increment;
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(), newCount);
   FirePostSetSignal();
   return DEVICE_OK;
}


int
CountDownSetting::Get(long& value)
{
   value = Get();
   return DEVICE_OK;
}


long CountDownSetting::Get()
{
   long count =
      GetLogger()->GetInteger(GetDevice()->GetDeviceName(), GetName());
   if (count > 0)
   {
      GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(),
            count - 1);
   }
   // Return the value _before_ the decrement. Otherwise a unit increment would
   // have no effect.
   return count;
}
