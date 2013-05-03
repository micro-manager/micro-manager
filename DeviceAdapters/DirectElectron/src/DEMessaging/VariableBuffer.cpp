#include "VariableBuffer.h"
#include <stdio.h>
#include <string.h>

VariableBuffer::VariableBuffer(std::size_t initialSize) :
	_buffer(NULL), _capacity(0), _offset(0)
{
	this->_buffer = new byte[initialSize];
	this->_capacity = initialSize;
}

VariableBuffer::~VariableBuffer()
{
	if (this->_buffer != NULL)
		delete[] this->_buffer;
}

byte* VariableBuffer::getBufferPtr() const
{
	return this->_buffer;
}

void VariableBuffer::resizeIfNeeded(std::size_t newSize)
{
	
	if (this->_buffer != NULL  && this->_capacity < newSize)
	{
		byte* oldBuffer = this->_buffer;
		std::size_t oldSize = this->_capacity;

		this->_buffer = new byte[newSize];
		memcpy(this->_buffer, oldBuffer, oldSize);

		delete[] oldBuffer;
	
		this->_capacity = newSize;
	}
}

void VariableBuffer::copy(void* from, std::size_t fromSize, std::size_t offset)
{
	this->resizeIfNeeded(fromSize + offset);
	memcpy(this->_buffer + offset, from, fromSize);
}

void VariableBuffer::resetOffset()
{
	this->_offset = 0;
}

void VariableBuffer::add(unsigned int val)
{
	this->copy(&val, sizeof(val), this->_offset);
	this->_offset += sizeof(val);
}

void VariableBuffer::add(void* from, std::size_t fromSize)
{
	this->copy(from, fromSize, this->_offset);
	this->_offset += fromSize;

}

void VariableBuffer::add(DEPacket& pkt)
{
	this->resizeIfNeeded(this->_offset + pkt.ByteSize());
	pkt.SerializeToArray(this->getBufferPtr() + this->_offset, pkt.ByteSize());
	this->_offset += pkt.ByteSize();
}

std::size_t VariableBuffer::getCapacity() const  { return this->_capacity; }

std::size_t VariableBuffer::getSize() const { return this->_offset; }