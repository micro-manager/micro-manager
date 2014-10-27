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

#include "SettingLogger.h"

#include <msgpack.hpp>

#include <string>


// This file effectively defines the MessagePack wire format for our test
// images. Unfortunately, there is no automatic mechanism to keep the Java (and
// possibly other) decoder in sync, so be careful! Field order is crucial.


void
BoolSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(2);
   // type
   pk.pack(std::string("bool"));
   // value
   pk.pack(value_);
}


void
IntegerSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(2);
   // type
   pk.pack(std::string("int"));
   // value
   pk.pack(value_);
}


void
FloatSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(2);
   // type
   pk.pack(std::string("float"));
   // value
   pk.pack(value_);
}


void
StringSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(2);
   // type
   pk.pack(std::string("string"));
   // value
   pk.pack(value_);
}


void
OneShotSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(2);
   // type
   pk.pack(std::string("one_shot"));
   // value
   pk.pack_nil();
}


void
SettingKey::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(2);
   // device
   pk.pack(device_);
   // key
   pk.pack(key_);
}


void
SettingEvent::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(3);
   // key
   key_.Write(sbuf);
   // value
   value_->Write(sbuf);
   // count
   pk.pack(count_);
}


void
CameraInfo::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(4);
   // name
   pk.pack(camera_);
   // isSequence
   pk.pack(isSequence_);
   // serialNumber
   pk.pack(serialNum_);
   // frameNumber
   pk.pack(frameNum_);
}


void
SettingLogger::SetBool(const std::string& device, const std::string& key,
      bool value, bool logEvent)
{
   SettingKey keyRecord = SettingKey(device, key);
   boost::shared_ptr<SettingValue> valueRecord =
      boost::make_shared<BoolSettingValue>(value);
   settingValues_[keyRecord] = valueRecord;

   if (logEvent)
   {
      SettingEvent event =
         SettingEvent(keyRecord, valueRecord, GetNextCount());
      settingEvents_.push_back(event);
   }
}


bool
SettingLogger::GetBool(const std::string& device,
      const std::string& key) const
{
   SettingKey keyRecord = SettingKey(device, key);
   SettingConstIterator found = settingValues_.find(keyRecord);
   if (found == settingValues_.end())
      return false;
   return found->second->GetBool();
}


void
SettingLogger::SetInteger(const std::string& device, const std::string& key,
      long value, bool logEvent)
{
   SettingKey keyRecord = SettingKey(device, key);
   boost::shared_ptr<SettingValue> valueRecord =
      boost::make_shared<IntegerSettingValue>(value);
   settingValues_[keyRecord] = valueRecord;

   if (logEvent)
   {
      SettingEvent event =
         SettingEvent(keyRecord, valueRecord, GetNextCount());
      settingEvents_.push_back(event);
   }
}


long
SettingLogger::GetInteger(const std::string& device,
      const std::string& key) const
{
   SettingKey keyRecord = SettingKey(device, key);
   SettingConstIterator found = settingValues_.find(keyRecord);
   if (found == settingValues_.end())
      return 0;
   return found->second->GetInteger();
}


void
SettingLogger::SetFloat(const std::string& device, const std::string& key,
      double value, bool logEvent)
{
   SettingKey keyRecord = SettingKey(device, key);
   boost::shared_ptr<SettingValue> valueRecord =
      boost::make_shared<FloatSettingValue>(value);
   settingValues_[keyRecord] = valueRecord;

   if (logEvent)
   {
      SettingEvent event =
         SettingEvent(keyRecord, valueRecord, GetNextCount());
      settingEvents_.push_back(event);
   }
}


double
SettingLogger::GetFloat(const std::string& device,
      const std::string& key) const
{
   SettingKey keyRecord = SettingKey(device, key);
   SettingConstIterator found = settingValues_.find(keyRecord);
   if (found == settingValues_.end())
      return 0.0;
   return found->second->GetFloat();
}


void
SettingLogger::SetString(const std::string& device, const std::string& key,
      const std::string& value, bool logEvent)
{
   SettingKey keyRecord = SettingKey(device, key);
   boost::shared_ptr<SettingValue> valueRecord =
      boost::make_shared<StringSettingValue>(value);
   settingValues_[keyRecord] = valueRecord;

   if (logEvent)
   {
      SettingEvent event =
         SettingEvent(keyRecord, valueRecord, GetNextCount());
      settingEvents_.push_back(event);
   }
}


std::string
SettingLogger::GetString(const std::string& device,
      const std::string& key) const
{
   SettingKey keyRecord = SettingKey(device, key);
   SettingConstIterator found = settingValues_.find(keyRecord);
   if (found == settingValues_.end())
      return std::string();
   return found->second->GetString();
}


void
SettingLogger::FireOneShot(const std::string& device, const std::string& key,
      bool logEvent)
{
   SettingKey keyRecord = SettingKey(device, key);
   boost::shared_ptr<SettingValue> valueRecord =
      boost::make_shared<OneShotSettingValue>();
   settingValues_[keyRecord] = valueRecord;

   if (logEvent)
   {
      SettingEvent event =
         SettingEvent(keyRecord, valueRecord, GetNextCount());
      settingEvents_.push_back(event);
   }
}


void
SettingLogger::MarkBusy(const std::string& device, bool logEvent)
{
   bool becameBusy = false;

   std::map<std::string, unsigned>::iterator busy =
      busyPoints_.find(device);
   if (busy == busyPoints_.end())
   {
      busyPoints_[device] = 1;
   }
   else
   {
      if (busy->second == 0)
         becameBusy = true;
      ++(busy->second);
   }

   if (becameBusy)
      SetInteger(device, "sys:Busy", 1, logEvent);
}


bool
SettingLogger::IsBusy(const std::string& device, bool queryNonDestructively)
{
   std::map<std::string, unsigned>::iterator busy =
      busyPoints_.find(device);

   if (busy == busyPoints_.end())
   {
      // Devices that have never been queried are initially busy
      if (!queryNonDestructively)
      {
         busyPoints_.insert(std::make_pair(device, 1U));
      }
      return true;
   }

   bool ret = busy->second > 0;
   bool becameNonBusy = (busy->second == 1 && !queryNonDestructively);

   if (busy->second > 0 && !queryNonDestructively)
      --(busy->second);
   if (becameNonBusy)
      SetInteger(device, "sys:Busy", 0);

   return ret;
}


bool
SettingLogger::PackAndReset(char* dest, size_t destSize,
      const std::string& camera, bool isSequenceImage,
      size_t cameraSeqNum, size_t acquisitionSeqNum)
{
   CameraInfo cameraInfo(camera, isSequenceImage,
         cameraSeqNum, acquisitionSeqNum);

   msgpack::sbuffer sbuf;
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);

   typedef std::string s;

   pk.pack_array(8);
   // packetNumber
   pk.pack(GetNextGlobalImageCount());
   // camera
   cameraInfo.Write(sbuf);
   // startCounter
   pk.pack(counterAtLastReset_);
   // currentCounter
   pk.pack(counter_);
   // busyDevices
   WriteBusyDevices(sbuf);
   // startState
   WriteSettingMap(sbuf, startingValues_);
   // currentState
   WriteSettingMap(sbuf, settingValues_);
   // history
   WriteHistory(sbuf);

   Reset();

   if (sbuf.size() <= destSize)
   {
      memcpy(dest, sbuf.data(), sbuf.size());
      memset(dest + sbuf.size(), 0, destSize - sbuf.size());
      return true;
   }
   else
   {
      memset(dest, 0, destSize);
      return false;
   }
}


void
SettingLogger::WriteBusyDevices(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);

   std::vector<std::string> busyDevices;
   for (std::map<std::string, unsigned>::const_iterator
         it = busyPoints_.begin(), end = busyPoints_.end(); it != end; ++it)
   {
      if (it->second > 0)
         busyDevices.push_back(it->first);
   }
   pk.pack_array(busyDevices.size());
   for (std::vector<std::string>::const_iterator it = busyDevices.begin(),
         end = busyDevices.end(); it != end; ++it)
   {
      pk.pack(*it);
   }
}


void
SettingLogger::WriteSettingMap(msgpack::sbuffer& sbuf,
      const SettingMap& values) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);

   pk.pack_array(values.size());
   for (SettingConstIterator it = values.begin(), end = values.end();
         it != end; ++it)
   {
      pk.pack_array(2);
      // key
      it->first.Write(sbuf);
      // value
      it->second->Write(sbuf);
   }
}


void
SettingLogger::WriteHistory(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);

   pk.pack_array(settingEvents_.size());
   for (std::vector<SettingEvent>::const_iterator it = settingEvents_.begin(),
         end = settingEvents_.end(); it != end; ++it)
   {
      it->Write(sbuf);
   }
}
