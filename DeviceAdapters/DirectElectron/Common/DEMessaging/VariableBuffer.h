#pragma once;
#include "DEServer.pb.h"

typedef unsigned char byte;
using namespace DEMessaging;

namespace DEMessaging
{
	class VariableBuffer
	{
	public:
		VariableBuffer(unsigned long initialSize = 0);
		virtual ~VariableBuffer();
		byte * getBufferPtr() const;
		void resizeIfNeeded(unsigned long newSize);
		void copy(void* from, unsigned long fromSize, unsigned long offset = 0);
		void resetOffset();
		void add(unsigned int val);
		void add(void* from, unsigned long fromSize);
		void add(DEPacket& pkt);
		unsigned long getCapacity() const;
		unsigned long getSize() const;

	private:
		byte* _buffer;
		unsigned long _capacity;
		unsigned long _offset;
	};
}