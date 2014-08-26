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

#include <cstddef>
#include <vector>


namespace mm
{
namespace logging
{
namespace detail
{

enum LineLevel
{
   LineLevelFirstLine,
   LineLevelHardNewline,
   LineLevelSoftNewline,
};


/**
 * Lines of a partially formatted log entry.
 *
 * This is a fixed-size data structure so that we can minimize the frequency of
 * memory allocation by the logger. Log lines serve as input to log sinks, and
 * are the elements of the queue used to send content to the asynchronous
 * backend.
 */
template <typename TMetadata>
class GenericLinePacket
{
public:
   // A reasonable size to break lines into (the vast majority of entry lines
   // fit in this size in practice), allowing for a fixed-size buffer to be
   // used.
   static const std::size_t MaxLogLineLen = 127;

private:
   LineLevel level_;
   TMetadata metadata_;
   char line_[MaxLogLineLen + 1];

public:
   GenericLinePacket(LineLevel level,
         typename TMetadata::LoggerDataType loggerData,
         typename TMetadata::EntryDataType entryData,
         typename TMetadata::StampDataType stampData) :
      level_(level),
      metadata_(loggerData, entryData, stampData)
   { line_[0] = '\0'; }

   // For C-style access
   char* GetLineBufferPtr() { return line_; }

   LineLevel GetLineLevel() const { return level_; }
   const TMetadata& GetMetadataConstRef() const { return metadata_; }
   const char* GetLine() const { return line_; }
};


template <typename TMetadata, class ULineVector>
void SplitEntryIntoLines(
      ULineVector& lines,
      typename TMetadata::LoggerDataType loggerData,
      typename TMetadata::EntryDataType entryData,
      typename TMetadata::StampDataType stampData,
      const char* entryText)
{
   // Break up entryText into lines, either at CRLF or LF (hard newline), or
   // at MaxLogLineLen (soft newline).
   //
   // Do all that without scanning through entryText more than once, and
   // writing into the vector of lines in linear address order. (Okay, this
   // is probably overkill, but it's easy enough.)

   typedef GenericLinePacket<TMetadata> LogLineType;

   const char* pText = entryText;
   LineLevel nextLevel = LineLevelFirstLine;
   std::size_t pastLastNonEmptyIndex = 0;
   do
   {
      lines.emplace_back(nextLevel, loggerData, entryData, stampData);

      nextLevel = LineLevelSoftNewline;

      char* pLine = lines.back().GetLineBufferPtr();
      const char* endLine = pLine + LogLineType::MaxLogLineLen;
      while (*pText && pLine < endLine)
      {
         // The sequences "\r", "\r\n", and "\n" are considered newlines.
         // Finish this line with a hard newline if we see one of these.
         // At which point, pText will point to the next char after the
         // newline sequence.
         if (*pText == '\r')
         {
            if (!*pText++)
               break;
            if (*pText == '\n')
            {
               if (!*pText++)
                  break;
               nextLevel = LineLevelHardNewline;
               break;
            }
            nextLevel = LineLevelHardNewline;
            break;
         }
         if (*pText == '\n')
         {
            if (!*pText++)
               break;
            nextLevel = LineLevelHardNewline;
            break;
         }

         *pLine++ = *pText++;
      }
      *pLine = '\0';
      if (pLine > lines.back().GetLineBufferPtr())
      {
         pastLastNonEmptyIndex = lines.size();
      }
   } while (*pText);

   // Remove trailing empty lines (but keep at least one line).
   if (pastLastNonEmptyIndex == 0)
      pastLastNonEmptyIndex++;
   lines.erase(lines.begin() + pastLastNonEmptyIndex, lines.end());
}

} // namespace detail
} // namespace logging
} // namespace mm
