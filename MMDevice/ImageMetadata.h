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

#include <string>
#include <vector>
#include <map>
#include <sstream>
#include "boost/foreach.hpp"

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
      std::ostringstream os;
      os << GetName() << std::endl << GetDevice() << std::endl << IsReadOnly() << value_ << std::endl;
      return os.str();
   }

   bool Restore(const char* stream)
   {
      std::istringstream is(stream);

      std::string name;
      is >> name;
      SetName(name.c_str());

      std::string device;
      is >> device;
      SetDevice(device.c_str());

      bool ro;
      is >> ro;
      SetReadOnly(ro);

      is >> value_;

      return true;
   }

private:
   std::string value_;
};

class MetadataArrayTag : public MetadataTag
{
public:
   MetadataArrayTag() {}
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
      std::ostringstream os;
      os << GetName() << std::endl << GetDevice() << std::endl << IsReadOnly() << values_.size();
      for (size_t i=0; i<values_.size(); i++)
         os << values_[i];
      return os.str();
   }

   bool Restore(const char* stream)
   {
      std::istringstream is(stream);

      std::string name;
      is >> name;
      SetName(name.c_str());

      std::string device;
      is >> device;
      SetDevice(device.c_str());

      bool ro;
      is >> ro;
      SetReadOnly(ro);

      size_t size;
      is >> size;

      values_.resize(size);

      for (size_t i=0; i<values_.size(); i++)
         is >> values_[i];

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

   Metadata() {}

   ~Metadata()
   {
      Clear();
   }

   void Clear() {
      for (TagIterator it=tags_.begin(); it != tags_.end(); it++)
         delete it->second;
      tags_.clear();
   }

   std::vector<std::string> GetKeys() const
   {
      std::vector<std::string> keyList;
      TagIterator it = tags_.begin();
      while(it != tags_.end())
         keyList.push_back(it++->first);

      return keyList;
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
      // create a tag copy
      MetadataTag* newTag = tag.Clone();

      // delete existing tag with the same key (if any)
      TagIterator it = tags_.find(tag.GetName());
      if (it != tags_.end())
         delete it->second;

      // assing a new tag
      tags_[tag.GetName()] = newTag;
   }

   Metadata& operator=(const Metadata& rhs)
   {
      Clear();
      
      for (TagIterator it=rhs.tags_.begin(); it != rhs.tags_.end(); it++)
      {
         SetTag(*it->second);
      }

      frameData = rhs.frameData;

      return *this;
   }

   std::string Serialize() const
   {
      std::ostringstream os;

      os << tags_.size();
      for (TagIterator it = tags_.begin(); it != tags_.end(); it++)
      {
         std::string id("s");
         if (it->second->ToArrayTag())
            id = "a";

         os << id << std::endl;
         os << it->second->GetName() << std::endl << it->second->GetDevice() << std::endl;
         os << (it->second->IsReadOnly() ? 1 : 0) << std::endl;

         if (id.compare("s") == 0)
         {
            const MetadataSingleTag* st = it->second->ToSingleTag();
				std::string  s1 = st->GetValue();
            os << s1 << std::endl;
         }
         else
         {
            const MetadataArrayTag* at = it->second->ToArrayTag();

            os << (long) at->GetSize() << std::endl;
            for (size_t i=0; i<at->GetSize(); i++)
               os << at->GetValue(i) << std::endl;
         }
      }

      return os.str();
   }

   bool Restore(const char* stream)
   {
      tags_.clear();
      std::istringstream is(stream);
      size_t sz;
      is >> sz;

      for (size_t i=0; i<sz; i++)
      {
         std::string id;
         std::string stream;
         is >> id;

         if (id.compare("s") == 0)
         {
            MetadataSingleTag ms;
            std::string strVal;
            is >> strVal;
            ms.SetName(strVal.c_str());
            is >> strVal;
            ms.SetDevice(strVal.c_str());
            int ro;
            is >> ro;
            ms.SetReadOnly(ro == 1 ? true : false);
            is >> strVal;
            ms.SetValue(strVal.c_str());

            MetadataTag* newTag = ms.Clone();
            tags_[ms.GetName()] = newTag;
         }
         else if (id.compare("a") == 0)
         {
            MetadataArrayTag as;
            std::string strVal;
            is >> strVal;
            as.SetName(strVal.c_str());
            is >> strVal;
            as.SetDevice(strVal.c_str());
            int ro;
            is >> ro;
            as.SetReadOnly(ro == 1 ? true : false);

            long sizea;
            is >> sizea;

            for (long i=0; i<sizea; i++)
            {
               is >> strVal;
               as.AddValue(strVal.c_str());
            }

            MetadataTag* newTag = as.Clone();
            tags_[as.GetName()] = newTag;
         }
         else
         {
            return false;
         }
      }
      return true;
   }

   std::string Dump()
   {
      std::ostringstream os;

      os << tags_.size();
      for (TagIterator it = tags_.begin(); it != tags_.end(); it++)
      {
         std::string id("s");
         if (it->second->ToArrayTag())
            id = "a";
         std::string ser = it->second->Serialize();
         os << id << " : " << ser << std::endl;
      }

      return os.str();
   }


   std::string get(std::string key)
   {
      return frameData[key];
   }

   void put(std::string key, std::string value)
   {
      frameData[key] = value;
   }

   std::vector<std::string> getFrameKeys()
   {
      std::pair<std::string,std::string> p;
      std::vector<std::string> keys;
      BOOST_FOREACH(p,frameData)
      {
         keys.push_back(p.first);
      }
      return keys;
   }

   int getIntProperty(char * key)
   {
      return atoi(frameData[key].c_str());
   }

   int getWidth()
   {
      return getIntProperty("Width");
   }

   int getHeight()
   {
      return getIntProperty("Height");
   }

   int getFrame()
   {
      return getIntProperty("Frame");
   }

   int getSlice()
   {
      return getIntProperty("Slice");
   }

   int getPositionIndex()
   {
      return getIntProperty("Position");
   }

   int getChannelIndex()
   {
      return getIntProperty("ChannelIndex");
   }

   std::string getChannel()
   {
      return frameData["Channel"];
   }

   std::map<std::string,std::string> frameData;

private:
   MetadataTag* FindTag(const char* key) const
   {
      TagIterator it = tags_.find(key);
      if (it != tags_.end())
         return it->second;
      else
         throw MetadataKeyError();
   }

   std::map<std::string, MetadataTag*> tags_;
   typedef std::map<std::string, MetadataTag*>::const_iterator TagIterator;
};

#endif //_IMAGE_METADATA_H_
