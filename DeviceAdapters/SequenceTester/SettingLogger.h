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

#pragma once

#include "TextImage.h"

#include <msgpack.hpp>

#include <boost/lexical_cast.hpp>
#include <boost/shared_ptr.hpp>

#include <map>
#include <string>
#include <vector>


class Serializable
{
public:
   virtual ~Serializable() {}

   // As a rule, writes a single msgpack item
   virtual void Write(msgpack::sbuffer& sbuf) const = 0;
};


class SettingValue : public Serializable
{
public:
   virtual bool GetBool() const { return false; }
   virtual long GetInteger() const { return 0; }
   virtual double GetFloat() const { return 0.0; }
   virtual std::string GetString() const { return std::string(); }
};


class BoolSettingValue : public SettingValue
{
   const bool value_;
public:
   BoolSettingValue(bool value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual bool GetBool() const { return value_; }
   virtual std::string GetString() const { return value_ ? "true" : "false"; }
};


class IntegerSettingValue : public SettingValue
{
   const long value_;
public:
   IntegerSettingValue(long value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual long GetInteger() const { return value_; }
   virtual std::string GetString() const
   { return boost::lexical_cast<std::string>(value_); }
};


class FloatSettingValue : public SettingValue
{
   const double value_;
public:
   FloatSettingValue(double value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual double GetFloat() const { return value_; }
   virtual std::string GetString() const
   { return boost::lexical_cast<std::string>(value_); }
};


class StringSettingValue : public SettingValue
{
   const std::string value_;
public:
   StringSettingValue(const std::string& value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual std::string GetString() const { return value_; }
};


class OneShotSettingValue : public SettingValue
{
public:
   OneShotSettingValue() {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual std::string GetString() const { return "(one-shot)"; }
};


class SettingKey : public Serializable
{
   // Note: not const; assignment allowed for use as map key.
   std::string device_;
   std::string key_;
public:
   SettingKey(const std::string& device, const std::string& key) :
      device_(device), key_(key)
   {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   bool operator<(const SettingKey& rhs) const
   {
      return (this->device_ < rhs.device_) ||
         (this->device_ == rhs.device_ && this->key_ < rhs.key_);
   }

   std::string GetStringRep() const { return device_ + ',' + key_; }
};


class SettingEvent : public Serializable
{
   SettingKey key_;
   boost::shared_ptr<SettingValue> value_;
   size_t count_;
public:
   SettingEvent(SettingKey key, boost::shared_ptr<SettingValue> value,
         uint64_t counterValue) :
      key_(key), value_(value), count_(counterValue)
   {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual void Draw(TextImageCursor& cursor) const;
};


class CameraInfo : public Serializable
{
   std::string camera_;
   bool isSequence_;
   size_t serialNr_;
   size_t cumulativeNr_;
   size_t frameNr_;
public:
   CameraInfo(const std::string& camera, bool isSequence,
         size_t serialNr, size_t cumulativeNr, size_t frameNr) :
      camera_(camera),
      isSequence_(isSequence),
      serialNr_(serialNr),
      cumulativeNr_(cumulativeNr),
      frameNr_(frameNr)
   {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual void Draw(TextImageCursor& cursor) const;
};


class SettingLogger
{
public:
   SettingLogger() :
      counter_(0),
      counterAtLastReset_(0),
      nextGlobalImageNr_(0)
   {}

   // Recording and querying

   // These methods should be called by setting objects only, not directly
   void SetBool(const std::string& device, const std::string& key,
         bool value, bool logEvent = true);
   bool GetBool(const std::string& device, const std::string& key) const;
   void SetInteger(const std::string& device, const std::string& key,
         long value, bool logEvent = true);
   long GetInteger(const std::string& device, const std::string& key) const;
   void SetFloat(const std::string& device, const std::string& key,
         double value, bool logEvent = true);
   double GetFloat(const std::string& device, const std::string& key) const;
   void SetString(const std::string& device, const std::string& key,
         const std::string& value, bool logEvent = true);
   std::string GetString(const std::string& device, const std::string& key) const;
   void FireOneShot(const std::string& device, const std::string& key,
         bool logEvent = true);

   // Log retrieval
   bool DumpMsgPackToBuffer(char* dest, size_t destSize,
         const std::string& camera, bool isSequenceImage,
         size_t serialImageNr, size_t cumulativeImageNr, size_t frameNr);
   void DrawTextToBuffer(char* dest, size_t destWidth, size_t destHeight,
         const std::string& camera, bool isSequenceImage,
         size_t serialImageNr, size_t cumulativeImageNr, size_t frameNr);

   // Clear history and save current state as previous
   void Reset()
   {
      counterAtLastReset_ = counter_;
      startingValues_ = settingValues_;
      settingEvents_.clear();
   }

private:
   uint64_t counter_;
   uint64_t counterAtLastReset_;
   uint64_t nextGlobalImageNr_;

   typedef std::map< SettingKey, boost::shared_ptr<SettingValue> > SettingMap;
   typedef SettingMap::const_iterator SettingConstIterator;
   SettingMap settingValues_;
   SettingMap startingValues_;
   std::vector<SettingEvent> settingEvents_;

   // Helper functions to be called with mutex_ held
   uint64_t GetNextCount() { return counter_++; }
   uint64_t GetNextGlobalImageNr() { return nextGlobalImageNr_++; }
   void WriteSettingMap(msgpack::sbuffer& sbuf, const SettingMap& values) const;
   void DrawSettingMap(TextImageCursor& cursor,
         const SettingMap& values) const;
   void WriteHistory(msgpack::sbuffer& sbuf) const;
   void DrawHistory(TextImageCursor& cursor) const;
};
