#pragma once;
#include "DEServer.h"

typedef unsigned char byte;
using namespace DEMessaging;

namespace DEMessaging
{
	class VariableBuffer
	{
	public:
		VariableBuffer(std::size_t initialSize = 0);
		virtual ~VariableBuffer();
		byte * getBufferPtr() const;
		void resizeIfNeeded(std::size_t newSize);
		void copy(void* from, std::size_t fromSize, std::size_t offset = 0);
		void resetOffset();
		void add(unsigned int val);
		void add(void* from, std::size_t fromSize);
		void add(DEPacket& pkt);
		std::size_t getCapacity() const;
		std::size_t getSize() const;

	private:
		byte* _buffer;
		std::size_t _capacity;
		std::size_t _offset;
	};
}