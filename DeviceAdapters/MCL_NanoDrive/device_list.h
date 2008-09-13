/*
File:		device_list.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MCL_DEVICE_LIST_
#define _MCL_DEVICE_LIST_

#include "HandleListType.h"

template <class T>
struct List
{
	T *pd;
	List *next;
};

template <class T>
struct DeviceList
{
	void Init(void* gHeap)
	{
		_head = 0;
		_tail = 0;
		_ref = 0;
		_deviceCount = 0;

		_gHeap = gHeap;
	}

	int AddToList(T *pd)
	{
		if(!pd)
			return -1;

		List<T>* pdl = 0;
		pdl =	(List<T>*)
				HeapAlloc(
					_gHeap,
					0,
					sizeof(List<T>)
					);

		if(pdl == 0)
			return -1;

		pdl->pd = pd;
		pdl->next = 0;

		if(!_tail)
			_tail = pdl;

		if(!_head)
			_head = pdl;
		else
		{
			pdl->next = _head;
			_head = pdl;
		}

		_deviceCount++;

		return 0;
	}

	int RemoveFromList(T *pd)
	{
		if(!pd)
			return -1;

		if(_deviceCount == 0)
			return 1;

		List<T> *prev = 0;
		List<T> *current = 0;

		current = _head;
		while(current)
		{
			if(current->pd == pd)
			{
				if(_head == current)
				{
					_head = current->next;
				}

				if(_ref == current)
				{
					_ref = current->next;
				}

				if(_tail == current)
				{
					_tail = prev;
				}

				if(prev)
				{
					prev->next = current->next;
				}

				HeapFree(_gHeap, 0, current);

				_deviceCount--;

				return 0;
			}

			prev = current;
			current = current->next;
		}

		return -1;
	}

	int DeleteSingleItem(T* pd)
	{
		if(!pd)
			return -1;

		if(_deviceCount == 0)
			return 1;

		List<T> *prev = 0;
		List<T> *current = 0;

		current = _head;

		while(current)
		{
			if(*current->pd == *pd)
			{
				if(_head == current)
				{
					_head = current->next;
				}

				if(_ref == current)
				{
				_ref = current->next;
				}

				if(_tail == current)
				{
					_tail = prev;
				}

				if(prev)
				{
					prev->next = current->next;
				}

				HeapFree(_gHeap, 0, current);

				_deviceCount--;

				return 0;
			}

			prev = current;
			current = current->next;
		}

		return -1;
	}

	void RemoveAll()
	{
		List<T> *current = _head;
		
		while(current)
		{
			List<T> *deleteme = current;
			current = current->next;
			HeapFree(_gHeap, 0, deleteme);
		}

		_head = 0;
		_tail = 0;
		_ref  = 0;
		_deviceCount = 0;
	}

	int MoveToHead(T* pd)
	{
		List<T> *prev = 0;
		List<T> *current = 0;

		current = _head;
		while(current)
		{
			if(current->pd == pd)
			{
				if(current == _head)
					return 0;

				if(current == _tail  && prev)
					_tail = prev;

				if(current == _ref)
					_ref = current->next;

				//Cut out the List object pointing to pd.
				prev->next = current->next;

				//Reinsert it at the beginning of the list.
				current->next = _head;
				_head = current;
			}
			prev = current;
			current = current->next;
		}

		return -1;
	}

	List<T>*& AdvanceRef()
	{
		if(_ref)
			_ref = _ref->next;

		return _ref;
	}

	void SetRef()
	{
		_ref = _head;
	}

	List<T>*& GetRef() 
	{ 
		return _ref; 
	}

	List<T>* GetHead() 
	{ 
		return _head; 
	}
	
	int GetDeviceCount() 
	{
		return _deviceCount; 
	}

private:

	List<T> *_ref;
	List<T> *_head;
	List<T> *_tail;

	void* _gHeap;

	int _deviceCount;
};

#endif