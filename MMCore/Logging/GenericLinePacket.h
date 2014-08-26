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
namespace internal
{

enum PacketState
{
   PacketStateEntryFirstLine,
   PacketStateNewLine,
   PacketStateLineContinuation,
};


/**
 * Packet data structure for asynchronous logging
 *
 * This is a fixed-size data structure so that we can minimize the frequency of
 * memory allocation by the logger. Log packets serve as input to log sinks,
 * and are the elements of the queue used to send content to the asynchronous
 * backend.
 */
template <class TMetadata>
class GenericLinePacket
{
public:
   // A reasonable size to break lines into (the vast majority of entry lines
   // fit in this size in practice), allowing for a fixed-size buffer to be
   // used.
   static const std::size_t PacketTextLen = 127;

private:
   PacketState state_;
   TMetadata metadata_;
   char text_[PacketTextLen + 1];

public:
   GenericLinePacket(PacketState packetState,
         typename TMetadata::LoggerDataType loggerData,
         typename TMetadata::EntryDataType entryData,
         typename TMetadata::StampDataType stampData) :
      state_(packetState),
      metadata_(loggerData, entryData, stampData)
   { text_[0] = '\0'; }

   // For C-style access
   char* GetTextBuffer() { return text_; }

   PacketState GetPacketState() const { return state_; }
   const TMetadata& GetMetadataConstRef() const { return metadata_; }
   const char* GetText() const { return text_; }
};


template <class TMetadata, class UPacketVector>
void SplitEntryIntoPackets(
      UPacketVector& packets,
      typename TMetadata::LoggerDataType loggerData,
      typename TMetadata::EntryDataType entryData,
      typename TMetadata::StampDataType stampData,
      const char* entryText)
{
   // Break up entryText into packets, either at CRLF or LF (new line), or at
   // PacketTextLen (line continuation).
   //
   // Do all that without scanning through entryText more than once, and
   // writing into the vector of packets in linear address order. (Okay, this
   // is probably overkill, but it's easy enough.)

   typedef GenericLinePacket<TMetadata> LinePacketType;

   const char* pText = entryText;
   PacketState nextState = PacketStateEntryFirstLine;
   std::size_t pastLastNonEmptyIndex = 0;
   do
   {
      packets.emplace_back(nextState, loggerData, entryData, stampData);

      nextState = PacketStateLineContinuation;

      char* pLine = packets.back().GetTextBuffer();
      const char* endLine = pLine + LinePacketType::PacketTextLen;
      while (*pText && pLine < endLine)
      {
         // The sequences "\r", "\r\n", and "\n" are considered newlines. If we
         // see one of those, mark the next as new line state. At which point,
         // pText will point to the next char after the newline sequence.
         if (*pText == '\r')
         {
            if (!*pText++)
               break;
            if (*pText == '\n')
            {
               if (!*pText++)
                  break;
               nextState = PacketStateNewLine;
               break;
            }
            nextState = PacketStateNewLine;
            break;
         }
         if (*pText == '\n')
         {
            if (!*pText++)
               break;
            nextState = PacketStateNewLine;
            break;
         }

         *pLine++ = *pText++;
      }
      *pLine = '\0';
      if (pLine > packets.back().GetTextBuffer())
      {
         pastLastNonEmptyIndex = packets.size();
      }
   } while (*pText);

   // Remove trailing empty lines (but keep at least one line).
   if (pastLastNonEmptyIndex == 0)
      pastLastNonEmptyIndex++;
   packets.erase(packets.begin() + pastLastNonEmptyIndex, packets.end());
}

} // namespace internal
} // namespace logging
} // namespace mm
