///////////////////////////////////////////////////////////////////////////////
// FILE:          ImageMetadata.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Metadata associated with the acquired image
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/07/2007
// COPYRIGHT:     University of California, San Francisco, 2007
//                100X Imaging Inc, 2008
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// CVS:           $Id: Configuration.h 2 2007-02-27 23:33:17Z nenad $
//
#ifndef _IMAGE_METADATA_H_
#define _IMAGE_METADATA_H_

#ifdef WIN32
// disable exception scpecification warnings in MSVC
#pragma warning( disable : 4290 )
#endif

#include "MMDeviceConstants.h"

#include <string>
#include <vector>
#include <map>
#include <sstream>
#include <stdio.h>
#include <stdlib.h>

///////////////////////////////////////////////////////////////////////////////
// MetadataError
// -------------
// Micro-Manager metadata error class, used to create exception objects
// 
class MetadataError
{
public:
   MetadataError(const char* msg) :
      message_(msg) {}

   virtual ~MetadataError() {}

   virtual std::string getMsg()
   {
      return message_;
   }

private:
   std::string message_;
};

class MetadataKeyError : public MetadataError
{
public:
   MetadataKeyError() :
      MetadataError("Undefined metadata key") {}
   ~MetadataKeyError() {}
};

class MetadataIndexError : public MetadataError
{
public:
   MetadataIndexError() :
      MetadataError("Metadata array index out of bounds") {}
   ~MetadataIndexError() {}
};


class MetadataSingleTag;
class MetadataArrayTag;

/**
 * Image information tags - metadata.
 */
class MetadataTag
{
public:
   MetadataTag() : name_("undefined"), deviceLabel_("undefined"), readOnly_(false) {}
   MetadataTag(const char* name, const char* device, bool readOnly) :
      name_(name), deviceLabel_(device), readOnly_(readOnly) {}
   virtual ~MetadataTag() {}

   const std::string& GetDevice() const {return deviceLabel_;}
   const std::string& GetName() const {return name_;}
   const std::string GetQualifiedName() const
   {
      std::string str;
      if (deviceLabel_.compare("_") != 0)
      {
         str.append(deviceLabel_).append("-");
      }
      str.append(name_);
      return str;
   }
   const bool IsReadOnly() const  {return readOnly_;}

   void SetDevice(const char* device) {deviceLabel_ = device;}
   void SetName(const char* name) {name_ = name;}
   void SetReadOnly(bool ro) {readOnly_ = ro;}

   /**
    * Equivalent of dynamic_cast<MetadataSingleTag*>(this), but does not use
    * RTTI. This makes it safe against multiple definitions when using 
    * dynamic libraries on Linux (original cause: JVM uses 
    * dlopen with RTLD_LOCAL when loading libraries.
    */
   virtual const MetadataSingleTag* ToSingleTag() const { return 0; }
   /**
    * Equivalent of dynamic_cast<MetadataArrayTag*>(this), but does not use
    * RTTI. @see ToSingleTag
    */
   virtual const MetadataArrayTag*  ToArrayTag()  const { return 0; }

   //inline  MetadataSingleTag* ToSingleTag() {
   //   const MetadataTag *p = this;
   //   return const_cast<MetadataSingleTag*>(p->ToSingleTag());
   //  }
   //inline  MetadataArrayTag* ToArrayTag() {
   //   const MetadataTag *p = this;
   //   return const_cast<MetadataArrayTag*>(p->ToArrayTag());
   //}

   virtual MetadataTag* Clone() = 0;
   virtual std::string Serialize() = 0;
   virtual bool Restore(const char* stream) = 0;
   virtual bool Restore(std::istringstream& is) = 0;

   static std::string ReadLine(std::istringstream& is)
   {
      std::string ret;
      std::getline(is, ret);
      return ret;
   }

private:
   std::string name_;
   std::string deviceLabel_;
   bool readOnly_;
};

class MetadataSingleTag : public MetadataTag
{
public:
   MetadataSingleTag() {}
   MetadataSingleTag(const char* name, const char* device, bool readOnly) :
      MetadataTag(name, device, readOnly) {}
   ~MetadataSingleTag() {}

   const std::string& GetValue() const {return value_;}
   void SetValue(const char* val) {value_ = val;}

   virtual const MetadataSingleTag* ToSingleTag() const { return this; }

   MetadataTag* Clone()
   {
      return new MetadataSingleTag(*this);
   }

   std::string Serialize()
   {
      std::string str;

      str.append(GetName()).append("\n");
      str.append(GetDevice()).append("\n");
      str.append(IsReadOnly() ? "1" : "0").append("\n");

      str.append(value_).append("\n");

      return str;
   }

   bool Restore(const char* stream)
   {
      std::istringstream is(stream);
      return Restore(is);
   }

   bool Restore(std::istringstream& is)
   {
      SetName(ReadLine(is).c_str());
      SetDevice(ReadLine(is).c_str());
      SetReadOnly(atoi(ReadLine(is).c_str()) != 0);

      value_ = ReadLine(is);

      return true;
   }

private:
   std::string value_;
};

class MetadataArrayTag : public MetadataTag
{
public:
   MetadataArrayTag() {}
   MetadataArrayTag(const char* name, const char* device, bool readOnly) :
      MetadataTag(name, device, readOnly) {}
   ~MetadataArrayTag() {}

   virtual const MetadataArrayTag* ToArrayTag() const { return this; }

   void AddValue(const char* val) {values_.push_back(val);}
   void SetValue(const char* val, size_t idx)
   {
      if (values_.size() < idx+1)
         values_.resize(idx+1);
      values_[idx] = val;
   }

   const std::string& GetValue(size_t idx) const {
      if (idx >= values_.size())
         throw MetadataIndexError();
      return values_[idx];
   }

   size_t GetSize() const {return values_.size();}

   MetadataTag* Clone()
   {
      return new MetadataArrayTag(*this);
   }

   std::string Serialize()
   {
      std::string str;

      str.append(GetName()).append("\n");
      str.append(GetDevice()).append("\n");
      str.append(IsReadOnly() ? "1" : "0").append("\n");

      std::stringstream os;
      os << values_.size();
      str.append(os.str()).append("\n");

      for (size_t i = 0; i < values_.size(); i++)
         str.append(values_[i]).append("\n");

      return str;
   }

   bool Restore(const char* stream)
   {
      std::istringstream is(stream);
      return Restore(is);
   }

   bool Restore(std::istringstream& is)
   {
      SetName(ReadLine(is).c_str());
      SetDevice(ReadLine(is).c_str());
      SetReadOnly(atoi(ReadLine(is).c_str()) != 0);

      size_t size = atol(ReadLine(is).c_str());

      values_.resize(size);

      for (size_t i = 0; i < size; i++)
         values_[i] = ReadLine(is);

      return true;
   }

private:
   std::vector<std::string> values_;
};

/**
 * Container for all metadata associated with a single image.
 */
class Metadata
{
public:

   Metadata() {} // empty constructor

   ~Metadata() // destructor
   {
      Clear();
   }

   Metadata(const Metadata& original) // copy constructor
   {
      for (TagConstIter it = original.tags_.begin(); it != original.tags_.end(); it++)
      {
         SetTag(*it->second);
      }
   }

   void Clear()
   {
      for (TagConstIter it=tags_.begin(); it != tags_.end(); it++)
         delete it->second;
      tags_.clear();
   }

   std::vector<std::string> GetKeys() const
   {
      std::vector<std::string> keyList;
      for (TagConstIter it = tags_.begin(), end = tags_.end(); it != end; ++it)
         keyList.push_back(it->first);
      return keyList;
   }

   bool HasTag(const char* key)
   {
      TagConstIter it = tags_.find(key);
      if (it != tags_.end())
         return true;
      else
         return false;
   }
   
   MetadataSingleTag GetSingleTag(const char* key) const throw (MetadataKeyError)
   {
      MetadataTag* tag = FindTag(key);
      const MetadataSingleTag* stag = tag->ToSingleTag();
      return *stag;
   }

   MetadataArrayTag GetArrayTag(const char* key) const throw (MetadataKeyError)
   {
      MetadataTag* tag = FindTag(key);
      const MetadataArrayTag* atag = tag->ToArrayTag();
      return *atag;
   }

   void SetTag(MetadataTag& tag)
   {
      MetadataTag* newTag = tag.Clone();
      const std::string key(tag.GetQualifiedName());
      RemoveTag(key.c_str());
      tags_[key] = newTag;
   }

   void RemoveTag(const char* key)
   {
      TagIter it = tags_.find(key);
      if (it != tags_.end())
      {
         delete it->second;
         tags_.erase(it); // Non-const iterator needed in pre-C++11 code
      }
   }

   /*
    * Convenience method to add a MetadataSingleTag
    */
   template <class anytype>
   void PutTag(std::string key, std::string deviceLabel, anytype value)
   {
      std::stringstream os;
      os << value;
      MetadataSingleTag* newTag = new MetadataSingleTag(key.c_str(), deviceLabel.c_str(), true);
      newTag->SetValue(os.str().c_str());
      tags_[newTag->GetQualifiedName()] = newTag;
   }

   /*
    * Add a tag not associated with any device.
    */
   template <class anytype>
   void PutImageTag(std::string key, anytype value)
   {
      PutTag(key, "_", value);
   }

   /*
    * Deprecated name. Equivalent to PutImageTag.
    */
   template <class anytype>
   void put(std::string key, anytype value)
   {
      PutImageTag(key, value);
   }

#ifndef SWIG
   Metadata& operator=(const Metadata& rhs)
   {
      Clear();
      
      for (TagConstIter it=rhs.tags_.begin(); it != rhs.tags_.end(); it++)
      {
         SetTag(*it->second);
      }

      return *this;
   }
#endif

   void Merge(const Metadata& newTags)
   {     
      for (TagConstIter it=newTags.tags_.begin(); it != newTags.tags_.end(); it++)
      {
         SetTag(*it->second);
      }
   }

   std::string Serialize() const
   {
      std::string str;

      std::ostringstream os;
      os << tags_.size();
      str.append(os.str()).append("\n");

      for (TagConstIter it = tags_.begin(); it != tags_.end(); it++)
      {
         const std::string id((it->second->ToArrayTag()) ? "a" : "s");
         str.append(id).append("\n");

         str.append(it->second->Serialize());
      }

      return str;
   }

   // TODO: Can this be removed?
   std::string readLine(std::istringstream &iss)
   {
      return MetadataTag::ReadLine(iss);
   }

   bool Restore(const char* stream)
   {
      Clear();

      std::istringstream is(stream);

      const size_t sz = atol(readLine(is).c_str());

      for (size_t i=0; i<sz; i++)
      {
         const std::string id(readLine(is));

         MetadataTag* newTag;
         if (id.compare("s") == 0)
         {
            newTag = new MetadataSingleTag();
         }
         else if (id.compare("a") == 0)
         {
            newTag = new MetadataArrayTag();
         }
         else
         {
            return false;
         }

         newTag->Restore(is);
         tags_[newTag->GetQualifiedName()] = newTag;
      }
      return true;
   }

   std::string Dump()
   {
      std::ostringstream os;

      os << tags_.size();
      for (TagConstIter it = tags_.begin(); it != tags_.end(); it++)
      {
         std::string id("s");
         if (it->second->ToArrayTag())
            id = "a";
         std::string ser = it->second->Serialize();
         os << id << " : " << ser << std::endl;
      }

      return os.str();
   }

private:
   MetadataTag* FindTag(const char* key) const
   {
      TagConstIter it = tags_.find(key);
      if (it != tags_.end())
         return it->second;
      else
         throw MetadataKeyError();
   }

   std::map<std::string, MetadataTag*> tags_;
   typedef std::map<std::string, MetadataTag*>::iterator TagIter;
   typedef std::map<std::string, MetadataTag*>::const_iterator TagConstIter;
};

#endif //_IMAGE_METADATA_H_
