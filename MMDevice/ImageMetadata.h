///////////////////////////////////////////////////////////////////////////////
// FILE:          ImageMetadata.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Metadata associated with the acquired image
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/07/2007
// COPYRIGHT:     University of California, San Francisco, 2007
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

///////////////////////////////////////////////////////////////////////////////
// MetadataError
// -------------
// Micro-Manager metadta error class, used to create exception objects
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

   virtual MetadataTag* Clone() = 0;

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

   const std::string& GetValue() {return value_;}
   void SetValue(const char* val) {value_ = val;}

   virtual MetadataTag* Clone()
   {
      return new MetadataSingleTag(*this);
   }

private:
   std::string value_;
};

class MetadataArrayTag : public MetadataTag
{
public:
   MetadataArrayTag() {}
   ~MetadataArrayTag() {}

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

   size_t GetSize() {return values_.size();}

   virtual MetadataTag* Clone()
   {
      return new MetadataArrayTag(*this);
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
   
   MetadataSingleTag GetSingleTag(const char* key) const
   {
      MetadataTag* tag = FindTag(key);
      MetadataSingleTag* stag = dynamic_cast<MetadataSingleTag*>(tag);
      return *stag;
   }

   MetadataArrayTag GetArrayTag(const char* key) const
   {
      MetadataTag* tag = FindTag(key);
      MetadataArrayTag* atag = dynamic_cast<MetadataArrayTag*>(tag);
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

      return *this;
   }

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
