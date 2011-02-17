#include "VariableBuffer.h"
#include <stdio.h>
#include <string.h>

VariableBuffer::VariableBuffer(unsigned long initialSize) :
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

void VariableBuffer::resizeIfNeeded(unsigned long newSize)
{
	
	if (this->_buffer != NULL  && this->_capacity < newSize)
	{
		byte* oldBuffer = this->_buffer;
		unsigned long oldSize = this->_capacity;

		this->_buffer = new byte[newSize];
		memcpy(this->_buffer, oldBuffer, oldSize);

		delete[] oldBuffer;
	
		this->_capacity = newSize;
	}
}

void VariableBuffer::copy(void* from, unsigned long fromSize, unsigned long offset)
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
	this->copy(&val, sizeof(unsigned long), this->_offset);
	this->_offset += sizeof(unsigned long);
}

void VariableBuffer::add(void* from, unsigned long fromSize)
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

unsigned long VariableBuffer::getCapacity() const  { return this->_capacity; }

unsigned long VariableBuffer::getSize() const { return this->_offset; }