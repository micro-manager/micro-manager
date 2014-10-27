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


void
IntegerSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(2);
   pk.pack(std::string("type"));
   pk.pack(std::string("int"));
   pk.pack(std::string("value"));
   pk.pack(value_);
}


void
FloatSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(2);
   pk.pack(std::string("type"));
   pk.pack(std::string("float"));
   pk.pack(std::string("value"));
   pk.pack(value_);
}


void
StringSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(2);
   pk.pack(std::string("type"));
   pk.pack(std::string("string"));
   pk.pack(std::string("value"));
   pk.pack(value_);
}


void
OneShotSettingValue::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(1);
   pk.pack(std::string("type"));
   pk.pack(std::string("one_shot"));
}


void
SettingKey::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(2);
   pk.pack(std::string("device"));
   pk.pack(device_);
   pk.pack(std::string("key"));
   pk.pack(key_);
}


void
SettingEvent::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(3);
   pk.pack(std::string("key"));
   key_.Write(sbuf);
   pk.pack(std::string("value"));
   value_->Write(sbuf);
   pk.pack(std::string("ctr"));
   pk.pack(count_);
}


void
CameraInfo::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_map(4);
   pk.pack(std::string("name"));
   pk.pack(camera_);
   pk.pack(std::string("is_sequence"));
   pk.pack(isSequence_);
   pk.pack(std::string("serial_number"));
   pk.pack(serialNum_);
   pk.pack(std::string("frame_number"));
   pk.pack(frameNum_);
}


void
SettingLogger::SetInteger(const std::string& device, const std::string& key,
      long value, bool logEvent)
{
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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
   GuardType g = Guard();

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

   {
      GuardType g = Guard();

      typedef std::string s;

      pk.pack_map(8);
      pk.pack(s("serial_number"));
      pk.pack(GetNextGlobalImageCount());
      pk.pack(s("camera"));
      cameraInfo.Write(sbuf);
      pk.pack(s("busy_devices"));
      WriteBusyDevices(sbuf);
      pk.pack(s("start_state"));
      WriteSettingMap(sbuf, startingValues_);
      pk.pack(s("current_state"));
      WriteSettingMap(sbuf, settingValues_);
      pk.pack(s("start_counter"));
      pk.pack(counterAtLastReset_);
      pk.pack(s("current_counter"));
      pk.pack(counter_);
      pk.pack(s("history"));
      WriteHistory(sbuf);

      Reset();
   }

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
      pk.pack_map(2);
      pk.pack(std::string("key"));
      it->first.Write(sbuf);
      pk.pack(std::string("value"));
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
