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

#include <boost/lexical_cast.hpp>
#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>
#include <string>
#include <vector>


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
SettingEvent::Draw(TextImageCursor& cursor) const
{
   DrawStringOnImage(cursor,
         "[" + boost::lexical_cast<std::string>(count_) + "]" +
         key_.GetStringRep() + "=" + value_->GetString());
}


void
CameraInfo::Write(msgpack::sbuffer& sbuf) const
{
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);
   pk.pack_array(5);
   // name
   pk.pack(camera_);
   // serialImageNr
   pk.pack(serialNr_);
   // isSequence
   pk.pack(isSequence_);
   // cumulativeImageNr
   pk.pack(cumulativeNr_);
   // frameNr
   pk.pack(frameNr_);
}


void
CameraInfo::Draw(TextImageCursor& cursor) const
{
   DrawStringOnImage(cursor, "camera,name=" + camera_);
   cursor.Space();
   DrawStringOnImage(cursor, "camera,serialImageNr=" +
         boost::lexical_cast<std::string>(serialNr_));
   cursor.Space();
   DrawStringOnImage(cursor, "camera,isSequence=" +
         std::string(isSequence_ ? "true" : "false"));
   cursor.Space();
   DrawStringOnImage(cursor,
         (isSequence_ ? "camera,sequenceImageNr=" : "camera,snapImageNr=") +
         boost::lexical_cast<std::string>(cumulativeNr_));
   if (isSequence_)
   {
      cursor.Space();
      DrawStringOnImage(cursor, "camera,frameNr=" +
            boost::lexical_cast<std::string>(frameNr_));
   }
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


bool
SettingLogger::DumpMsgPackToBuffer(char* dest, size_t destSize,
      const std::string& camera, bool isSequenceImage,
      size_t serialImageNr, size_t cumulativeImageNr, size_t frameNr)
{
   CameraInfo cameraInfo(camera, isSequenceImage,
         serialImageNr, cumulativeImageNr, frameNr);

   msgpack::sbuffer sbuf;
   msgpack::packer<msgpack::sbuffer> pk(&sbuf);

   pk.pack_array(7);
   // packetNumber
   pk.pack(GetNextGlobalImageNr());
   // camera
   cameraInfo.Write(sbuf);
   // startCounter
   pk.pack(counterAtLastReset_);
   // currentCounter
   pk.pack(counter_);
   // startState
   WriteSettingMap(sbuf, startingValues_);
   // currentState
   WriteSettingMap(sbuf, settingValues_);
   // history
   WriteHistory(sbuf);

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
SettingLogger::DrawTextToBuffer(char* dest, size_t destWidth,
      size_t destHeight, const std::string& camera, bool isSequenceImage,
      size_t serialImageNr, size_t cumulativeImageNr, size_t frameNr)
{
   memset(dest, 0, destWidth * destHeight);
   TextImageCursor cursor(reinterpret_cast<uint8_t*>(dest),
         destWidth, destHeight);

   DrawStringOnImage(cursor, "HubGlobalPacketNr=" +
         boost::lexical_cast<std::string>(GetNextGlobalImageNr()));
   cursor.NewLine();

   CameraInfo cameraInfo(camera, isSequenceImage,
         serialImageNr, cumulativeImageNr, frameNr);
   cameraInfo.Draw(cursor);
   cursor.NewLine();
   cursor.NewLine();

   DrawStringOnImage(cursor, "State");
   cursor.NewLine();
   DrawSettingMap(cursor, settingValues_);
   cursor.NewLine();
   cursor.NewLine();

   DrawStringOnImage(cursor, "History");
   cursor.NewLine();
   DrawHistory(cursor);
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
SettingLogger::DrawSettingMap(TextImageCursor& cursor,
      const SettingMap& values) const
{
   bool first = true;
   for (SettingConstIterator it = values.begin(), end = values.end();
         it != end; ++it)
   {
      if (boost::dynamic_pointer_cast<OneShotSettingValue>(it->second))
         continue; // Skip one-shot settings

      if (first)
         first = false;
      else
         cursor.Space();
      DrawStringOnImage(cursor, it->first.GetStringRep() + '=' +
            it->second->GetString());
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


void
SettingLogger::DrawHistory(TextImageCursor& cursor) const
{
   for (std::vector<SettingEvent>::const_iterator
         begin = settingEvents_.begin(), it = begin,
         end = settingEvents_.end(); it != end; ++it)
   {
      if (it != begin)
         cursor.Space();
      it->Draw(cursor);
   }
}
