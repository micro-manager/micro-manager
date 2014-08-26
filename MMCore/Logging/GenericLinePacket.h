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


} // namespace internal
} // namespace logging
} // namespace mm
