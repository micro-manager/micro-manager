// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
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
//
// AUTHOR:        Mark Tsuchida

#pragma once

#include "LogEntryFilter.h"
#include "LogLine.h"
#include "DefaultMetadata.h"

#include <boost/container/vector.hpp>
#include <boost/utility.hpp>

#include <exception>
#include <iostream>
#include <fstream>
#include <vector>


namespace mm
{
namespace logging
{

class CannotOpenFileException : public std::exception
{
public:
   virtual const char* what() const throw() { return "Cannot open log file"; }
};


namespace detail
{

inline const char*
LevelString(LogLevel logLevel)
{
   switch (logLevel)
   {
      case LogLevelTrace: return "trc";
      case LogLevelDebug: return "dbg";
      case LogLevelInfo: return "IFO";
      case LogLevelWarning: return "WRN";
      case LogLevelError: return "ERR";
      case LogLevelFatal: return "FTL";
      default: return "???";
   }
}


// Despite the template parameter, this is currently hard-coded to the default
// metadata.
template <typename TMetadata, typename ULineIterator>
void
WriteLinesToStreamWithStandardFormat(std::ostream& stream,
      ULineIterator first, ULineIterator last,
      boost::shared_ptr< GenericEntryFilter<TMetadata> > filter)
{
   bool beforeFirst = true;
   size_t openBracketCol = 0;
   size_t bracketedWidth = 0;
   for (ULineIterator it = first; it != last; ++it)
   {
      // Apply filter if present
      if (filter &&
            !filter->Filter(it->GetMetadataConstRef()))
         continue;

      // If soft newline (broken up just to fit into LogLine buffer), splice
      // the lines.
      if (it->GetLineLevel() == LineLevelSoftNewline)
      {
         stream << it->GetLine();
         continue;
      }

      // Close the previous output line.
      if (!beforeFirst)
         stream << '\n';

      // Write metadata on first line of entry; write empty prefix of same
      // width on subsequent lines.
      if (it->GetLineLevel() == LineLevelFirstLine)
      {
         // TODO This implementation should belong to the metadata class
         // TODO Avoid the slow tellp()
         std::ostream::pos_type prefixStart = stream.tellp();
         WriteTimeToStream(stream,
               it->GetMetadataConstRef().stampData_.GetTimestamp());
         stream << " tid" <<
            it->GetMetadataConstRef().stampData_.GetThreadId() << ' ';
         openBracketCol = static_cast<size_t>(stream.tellp() - prefixStart);
         stream << '[';
         std::ostream::pos_type bracketedStart = stream.tellp();
         stream <<
            LevelString(it->GetMetadataConstRef().entryData_.GetLevel()) <<
            ',' << it->GetMetadataConstRef().loggerData_.GetComponentLabel();
         bracketedWidth = static_cast<size_t>(stream.tellp() - bracketedStart);
         stream << ']';
      }
      else // LineLevelHardNewline
      {
         for (size_t i = 0; i < openBracketCol; ++i)
            stream.put(' ');
         stream << '[';
         for (size_t i = 0; i < bracketedWidth; ++i)
            stream.put(' ');
         stream << ']';
      }

      stream << ' ' << it->GetLine();
      beforeFirst = false;
   }

   // Close the last output line
   if (!beforeFirst)
      stream << '\n';
}


template <typename TMetadata>
class GenericLogSink
{
private:
   boost::shared_ptr< GenericEntryFilter<TMetadata> > filter_;

protected:
   boost::shared_ptr< GenericEntryFilter<TMetadata> > GetFilter() const
   { return filter_; }

public:
   typedef boost::container::vector< GenericLogLine<TMetadata> >
      LineVectorType;

   virtual ~GenericLogSink() {}
   virtual void Consume(const LineVectorType& lines)
      = 0;

   // Note: If setting the filter while the sink is in use, you must pause the
   // logger. See the LoggingCore member function AtomicSetSinkFilters().
   void SetFilter(boost::shared_ptr< GenericEntryFilter<TMetadata> > filter)
   { filter_ = filter; }
};


template <typename TMetadata>
class GenericStdErrLogSink : public GenericLogSink<TMetadata>
{
   bool hadError_;

public:
   typedef GenericLogSink<TMetadata> Super;
   typedef typename Super::LineVectorType LineVectorType;

   GenericStdErrLogSink() : hadError_(false) {}

   virtual void Consume(const LineVectorType& lines)
   {
      WriteLinesToStreamWithStandardFormat(std::clog,
            lines.begin(), lines.end(), this->GetFilter());
      try
      {
         std::clog.flush();
      }
      catch (const std::ios_base::failure& e)
      {
         if (!hadError_)
         {
            hadError_ = true;
            // There's not much we can do if stderr is failing on us. But let's
            // try anyway in a manner that doesn't throw.
            std::cerr << "Logging: cannot write to stderr: " <<
               e.what() << '\n';
         }
      }
   }
};


template <typename TMetadata>
class GenericFileLogSink : public GenericLogSink<TMetadata>, boost::noncopyable
{
   std::string filename_;
   std::ofstream fileStream_;
   bool hadError_;

public:
   typedef GenericLogSink<TMetadata> Super;
   typedef typename Super::LineVectorType LineVectorType;

   GenericFileLogSink(const std::string& filename, bool append = false) :
      filename_(filename),
      hadError_(false)
   {
      std::ios_base::openmode mode = std::ios_base::out;
      mode |= (append ? std::ios_base::app : std::ios_base::trunc);

      fileStream_.open(filename_.c_str(), mode);
      if (!fileStream_)
         throw CannotOpenFileException();
   }

   virtual void Consume(const LineVectorType& lines)
   {
      WriteLinesToStreamWithStandardFormat(fileStream_,
            lines.begin(), lines.end(), this->GetFilter());
      try
      {
         fileStream_.flush();
      }
      catch (const std::ios_base::failure& e)
      {
         if (!hadError_)
         {
            hadError_ = true;
            std::cerr << "Logging: cannot write to file " << filename_ <<
               ": " << e.what() << '\n';
         }
      }
   }
};

} // namespace detail
} // namespace logging
} // namespace mm
