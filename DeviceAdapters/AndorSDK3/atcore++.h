#ifndef _ATCOREPLUSPLUS_H_
#define _ATCOREPLUSPLUS_H_

#include <string>
#include <list>
#include "atcore.h"
#include <map>

namespace andor {

//unchecked exceptions
class ReadOnlyException : public std::exception {};
class NotReadableException : public std::exception {};
class NotWritableException : public std::exception {};
class InvalidFeatureException : public std::exception {};
class OutOfRangeException : public std::exception {};
class EnumIndexNotAvailableException : public std::exception {};
class EnumIndexNotImplementedException : public std::exception {};
class StringLengthTooShortException : public std::exception {};
class InvalidSizeException : public std::exception {};
class InvalidBufferException : public std::exception {};
class BufferAlignmentException : public std::exception {};
class NotInitialisedException : public std::exception {};
class NotImplementedException : public std::exception {};
class ExceededMaxStringLengthException : public std::exception {};
class StringNotAvailableException : public std::exception {};
class StringNotImplementedException : public std::exception {};
class NoDataException : public std::exception {};
class BufferFullException : public std::exception {};
class InvalidAlignmentException : public std::exception {};
class UnrecognisedObserverException: public std::exception {};
class UnrecognisedErrorCodeException: public std::exception {};

//checked exceptions
class ComException : public std::exception {};
class ConnectionException : public std::exception {};
class TimedOutException : public std::exception {};
class HardwareOverflowException : public std::exception {};
class NoMemoryException : public std::exception {};

class ISubject;

class errorChecker
{
public:
	static void checkError(int _i_error)
	{
		switch (_i_error)
		{
		case AT_SUCCESS: break;
		case AT_ERR_COMM: throw ComException(); break;
		case AT_ERR_NOTIMPLEMENTED: throw NotImplementedException(); break;
		case AT_ERR_NOTREADABLE: throw NotReadableException(); break;
		case AT_ERR_OUTOFRANGE: throw OutOfRangeException(); break;
		case AT_ERR_READONLY: throw ReadOnlyException(); break;
		case AT_ERR_NOTWRITABLE: throw NotWritableException(); break;
		case AT_ERR_EXCEEDEDMAXSTRINGLENGTH: throw ExceededMaxStringLengthException(); break;
		case AT_ERR_INDEXNOTAVAILABLE: throw EnumIndexNotAvailableException(); break;
		case AT_ERR_INDEXNOTIMPLEMENTED: throw EnumIndexNotImplementedException(); break;
		case AT_ERR_STRINGNOTAVAILABLE: throw StringNotAvailableException(); break;
		case AT_ERR_STRINGNOTIMPLEMENTED: throw StringNotImplementedException(); break;
		case AT_ERR_HARDWARE_OVERFLOW: throw HardwareOverflowException(); break;
		case AT_ERR_NODATA: throw NoDataException(); break;
		case AT_ERR_NOMEMORY: throw NoMemoryException(); break;
		case AT_ERR_BUFFERFULL: throw BufferFullException(); break;
		case AT_ERR_INVALIDSIZE: throw InvalidSizeException(); break;
		case AT_ERR_INVALIDALIGNMENT: throw InvalidAlignmentException(); break;
		case AT_ERR_CONNECTION: throw ConnectionException(); break;
		case AT_ERR_NOTINITIALISED: throw NotInitialisedException(); break;
		default: throw UnrecognisedErrorCodeException();
		}
	}
};

class TestState
{
public:
	TestState(AT_H _handle, std::wstring _feature)
		: m_handle(_handle), m_feature(_feature)
	{

	}

	bool IsImplemented()
	{
		int i_isImplemented = 0;
		int i_err = AT_IsImplemented(m_handle, m_feature.c_str(), &i_isImplemented);
		errorChecker::checkError(i_err);		
		return(i_isImplemented==1);
	}

	bool IsReadable()
	{
		int i_isReadable = 0;
		int i_err = AT_IsReadable(m_handle, m_feature.c_str(), &i_isReadable);
		errorChecker::checkError(i_err);		
		return (i_isReadable == 1);
	}

	bool IsWritable()
	{
		int i_isWritable = 0;
		int i_err = AT_IsWritable(m_handle, m_feature.c_str(), &i_isWritable);
		errorChecker::checkError(i_err);		
		return (i_isWritable == 1);
	}

	bool IsReadOnly()
	{
		int i_isReadOnly = 0;
		int i_err = AT_IsReadOnly(m_handle, m_feature.c_str(), &i_isReadOnly);
		errorChecker::checkError(i_err);		
		return (i_isReadOnly == 1);
	}

private:
  AT_H m_handle;
  std::wstring m_feature;
};

class IObserver
{
public:
  virtual void Update(ISubject* Subject) = 0;
};

class ISubject
{
public:
  virtual void Attach(IObserver* Observer) = 0;
  virtual void Detach(IObserver* Observer) = 0;
};

class IFeature : public ISubject
{
public:
  virtual ~IFeature(){}
  virtual bool IsImplemented() = 0;
  virtual bool IsReadable() = 0;
  virtual bool IsWritable() = 0;
  virtual bool IsReadOnly() = 0;
};

class IInteger : public IFeature
{
public:
  virtual ~IInteger(){}
  virtual long long Get() = 0;
  virtual void Set(long long Value) = 0;
  virtual long long Max() = 0;
  virtual long long Min() = 0;
};

class IFloat : public IFeature
{
public:
  virtual ~IFloat(){}
  virtual double Get() = 0;
  virtual void Set(double Value) = 0;
  virtual double Max() = 0;
  virtual double Min() = 0;
};

class IBool : public IFeature
{
public:
  virtual ~IBool(){}
  virtual bool Get() = 0;
  virtual void Set(bool Value) = 0;
};

class ICommand : public IFeature
{
public:
  virtual ~ICommand(){}
  virtual void Do() = 0;
};

class IString : public IFeature
{
public:
  virtual ~IString(){}
  virtual std::wstring Get() = 0;
  virtual void Set(std::wstring Value) = 0;
  virtual int MaxLength() = 0;
};

class IEnum : public IFeature
{
public:
  virtual ~IEnum(){}
  virtual int GetIndex() = 0;
  virtual void Set(int Index) = 0;
  virtual void Set(std::wstring Value) = 0;
  virtual int Count() = 0;
  virtual std::wstring GetStringByIndex(int Index) = 0;
  virtual bool IsIndexAvailable(int Index) = 0;
  virtual bool IsIndexImplemented(int Index) = 0;
};

class IBufferControl
{
public:
  virtual ~IBufferControl(){}
  virtual void Queue(unsigned char* Buffer, int BufferSize) = 0;
  virtual bool Wait(unsigned char* &Buffer, int &BufferSize, int Timeout) = 0;
  virtual void Flush() = 0;
};

class IDevice
{
public:
  virtual ~IDevice(){};
  virtual IInteger* GetInteger(std::wstring Name) = 0;
  virtual IFloat* GetFloat(std::wstring Name) = 0;
  virtual IBool* GetBool(std::wstring Name) = 0;
  virtual ICommand* GetCommand(std::wstring Name) = 0;
  virtual IString* GetString(std::wstring Name) = 0;
  virtual IEnum* GetEnum(std::wstring Name) = 0;
  virtual IBufferControl* GetBufferControl() = 0;
  virtual void Release(IFeature* Feature) = 0;
  virtual void ReleaseBufferControl(IBufferControl* BufferControl) = 0;
  virtual AT_H GetHandle() = 0;
};

class IDeviceManager
{
public:
  virtual ~IDeviceManager(){};
  virtual IDevice* OpenDevice(int Index) = 0;
  virtual IDevice* OpenSystemDevice() = 0;
  virtual void CloseDevice(IDevice* Device) = 0;
};

struct subject_observer_pair {
	ISubject* p_subject;
	IObserver* p_observer;
};

class TSubject : public ISubject
{
public:
	TSubject(AT_H _handle, std::wstring _feature, ISubject* _subject)
		: m_handle(_handle), m_feature(_feature), parent_subject(_subject)
	{

	}

	static int AT_EXP_CONV TheWrapperCallback(AT_H /*Hndl*/, const AT_WC* /*Feature*/, void* Context)
	{
		subject_observer_pair* pair_ptr=(subject_observer_pair*)Context;
		(pair_ptr->p_observer)->Update(pair_ptr->p_subject);
		return(0);
	}

	void Attach(IObserver* Observer)
	{
		subject_observer_pair* new_pair = new subject_observer_pair;
		new_pair->p_observer = Observer;
		new_pair->p_subject = parent_subject;
		registered_pairs.push_back(new_pair);
		int i_err = AT_RegisterFeatureCallback(m_handle, m_feature.c_str(), TheWrapperCallback, (void*)new_pair);
		errorChecker::checkError(i_err);
	}

	void Detach(IObserver* Observer)
	{
		// Check if this subject/observer pair is listed
		bool found = false;
		std::list<subject_observer_pair*>::iterator i;
		for(i=registered_pairs.begin(); i != registered_pairs.end(); i++)
		{
			if((*i)->p_observer == Observer)
			{
				found = true;
				break;
			}
		}

		if(found)
		{
			int i_err = AT_UnregisterFeatureCallback(m_handle, m_feature.c_str(), TheWrapperCallback, (void*)*i);
			errorChecker::checkError(i_err);
			delete (*i);
			registered_pairs.erase(i);
		}
		else
		{
			// This Observer is not registered with this subject
			throw UnrecognisedObserverException();
		}
	}

private:
	AT_H m_handle;
	std::wstring m_feature;
	ISubject* parent_subject;
	std::list <subject_observer_pair*> registered_pairs;
};

class TInteger : public IInteger
{
public:
  TInteger(AT_H _handle, std::wstring _feature)
  :m_handle(_handle), m_feature(_feature)
  {
	  stateTester = new TestState(m_handle, m_feature);
	  callbackBooker = new TSubject(m_handle, m_feature, this);
  }

  long long Get()
  {
	  AT_64 i_returnInteger;
	  int i_err = AT_GetInt(m_handle, m_feature.c_str(), &i_returnInteger);
	  errorChecker::checkError(i_err);

	  return(i_returnInteger);
  }

  void Set(long long Value)
  {
	  int i_err = AT_SetInt(m_handle, m_feature.c_str(), Value);
	  errorChecker::checkError(i_err);
  }

  long long Max()
  {
	  AT_64 i_returnInteger;
	  int i_err = AT_GetIntMax(m_handle, m_feature.c_str(), &i_returnInteger);
	  errorChecker::checkError(i_err);

	  return(i_returnInteger);
  }

  long long Min()
  {
	  AT_64 i_returnInteger;
	  int i_err = AT_GetIntMin(m_handle, m_feature.c_str(), &i_returnInteger);
	  errorChecker::checkError(i_err);

	  return(i_returnInteger);
  }

  bool IsImplemented() {return(stateTester->IsImplemented()); }
  bool IsReadable() {return(stateTester->IsReadable()); }
  bool IsWritable() {return(stateTester->IsWritable()); }
  bool IsReadOnly() {return(stateTester->IsReadOnly()); }

  void Attach(IObserver* Observer) { callbackBooker->Attach(Observer); }
  void Detach(IObserver* Observer) { callbackBooker->Detach(Observer); }


private:
  AT_H m_handle;
  std::wstring m_feature;
  TestState* stateTester;
  ISubject* callbackBooker;
};

class TFloat : public IFloat
{
public:
	TFloat(AT_H _handle, std::wstring _feature)
		: m_handle(_handle), m_feature(_feature)
	{
		stateTester = new TestState(m_handle, m_feature);
	  callbackBooker = new TSubject(m_handle, m_feature, this);
	}

	double Get()
	{
		double d_returnFloat;
		int i_err = AT_GetFloat(m_handle, m_feature.c_str(), &d_returnFloat);
		errorChecker::checkError(i_err);

		return(d_returnFloat);
	}

	void Set(double Value)
	{
		int i_err = AT_SetFloat(m_handle, m_feature.c_str(), Value);
		errorChecker::checkError(i_err);
	}

	double Max()
	{
		double d_returnFloat;
		int i_err = AT_GetFloatMax(m_handle, m_feature.c_str(), &d_returnFloat);
		errorChecker::checkError(i_err);

		return(d_returnFloat);
	}

	double Min()
	{
		double d_returnFloat;
		int i_err = AT_GetFloatMin(m_handle, m_feature.c_str(), &d_returnFloat);
		errorChecker::checkError(i_err);

		return(d_returnFloat);
	}

	bool IsImplemented() {return(stateTester->IsImplemented()); }
	bool IsReadable() {return(stateTester->IsReadable()); }
	bool IsWritable() {return(stateTester->IsWritable()); }
	bool IsReadOnly() {return(stateTester->IsReadOnly()); }

	void Attach(IObserver* Observer) { callbackBooker->Attach(Observer); }
	void Detach(IObserver* Observer) { callbackBooker->Detach(Observer); }

private:
	AT_H m_handle;
	std::wstring m_feature;
	TestState* stateTester;
	ISubject* callbackBooker;
};

class TBool : public IBool
{
public:
	TBool(AT_H _handle, std::wstring _feature)
		: m_handle(_handle), m_feature(_feature)
	{
		stateTester = new TestState(m_handle, m_feature);
	  callbackBooker = new TSubject(m_handle, m_feature, this);
	}
	
	bool Get()
	{
		AT_BOOL i_returnBoolean;
		int i_err = AT_GetBool(m_handle, m_feature.c_str(), &i_returnBoolean);
		errorChecker::checkError(i_err);

		return(i_returnBoolean==1);
	}

	void Set(bool Value)
	{
		int i_err = AT_SetBool(m_handle, m_feature.c_str(), Value?AT_TRUE:AT_FALSE);
		errorChecker::checkError(i_err);
	}

	bool IsImplemented() {return(stateTester->IsImplemented()); }
	bool IsReadable() {return(stateTester->IsReadable()); }
	bool IsWritable() {return(stateTester->IsWritable()); }
	bool IsReadOnly() {return(stateTester->IsReadOnly()); }

	void Attach(IObserver* Observer) { callbackBooker->Attach(Observer); }
	void Detach(IObserver* Observer) { callbackBooker->Detach(Observer); }

private:
	AT_H m_handle;
	std::wstring m_feature;
	TestState* stateTester;
	ISubject* callbackBooker;
};

class TCommand : public ICommand
{
public:
	TCommand(AT_H _handle, std::wstring _feature)
		: m_handle(_handle), m_feature(_feature)
	{
		stateTester = new TestState(m_handle, m_feature);
	  callbackBooker = new TSubject(m_handle, m_feature, this);
	}

	void Do()
	{
		int i_err = AT_Command(m_handle, m_feature.c_str());
		errorChecker::checkError(i_err);
	}

	bool IsImplemented() {return(stateTester->IsImplemented()); }
	bool IsReadable() {return(stateTester->IsReadable()); }
	bool IsWritable() {return(stateTester->IsWritable()); }
	bool IsReadOnly() {return(stateTester->IsReadOnly()); }

	void Attach(IObserver* Observer) { callbackBooker->Attach(Observer); }
	void Detach(IObserver* Observer) { callbackBooker->Detach(Observer); }

private:
	AT_H m_handle;
	std::wstring m_feature;
	TestState* stateTester;
	ISubject* callbackBooker;
};

class TString : public IString
{
public:
	TString(AT_H _handle, std::wstring _feature)
		: m_handle(_handle), m_feature(_feature)
	{
		stateTester = new TestState(m_handle, m_feature);
	  callbackBooker = new TSubject(m_handle, m_feature, this);
	}

	std::wstring Get()
	{
		int i_maxStringLength;
		int i_err = AT_GetStringMaxLength(m_handle, m_feature.c_str(), &i_maxStringLength);
		errorChecker::checkError(i_err);

		AT_WC* ws_returnString = new AT_WC[i_maxStringLength];
		i_err = AT_GetString(m_handle, m_feature.c_str(), ws_returnString, i_maxStringLength);
		errorChecker::checkError(i_err);

		return(std::wstring(ws_returnString));
	}
	
	void Set(std::wstring Value)
	{
		int i_err = AT_SetString(m_handle, m_feature.c_str(), Value.c_str());
		errorChecker::checkError(i_err);
	}

	int MaxLength()
	{
		int i_maxStringLength;
		int i_err = AT_GetStringMaxLength(m_handle, m_feature.c_str(), &i_maxStringLength);
		errorChecker::checkError(i_err);

		return(i_maxStringLength);
	}

	bool IsImplemented() {return(stateTester->IsImplemented()); }
	bool IsReadable() {return(stateTester->IsReadable()); }
	bool IsWritable() {return(stateTester->IsWritable()); }
	bool IsReadOnly() {return(stateTester->IsReadOnly()); }

	void Attach(IObserver* Observer) { callbackBooker->Attach(Observer); }
	void Detach(IObserver* Observer) { callbackBooker->Detach(Observer); }

private:
	AT_H m_handle;
	std::wstring m_feature;
	TestState* stateTester;
	ISubject* callbackBooker;
};

class TEnum : public IEnum
{
public:
	TEnum(AT_H _handle, std::wstring _feature)
		: m_handle(_handle), m_feature(_feature)
	{
		stateTester = new TestState(m_handle, m_feature);
	  callbackBooker = new TSubject(m_handle, m_feature, this);
	}

	int GetIndex()
	{
		int i_returnIndex;
		int i_err = AT_GetEnumIndex(m_handle, m_feature.c_str(), &i_returnIndex);
		errorChecker::checkError(i_err);

		return(i_returnIndex);
	}

	void Set(int Index)
	{
		int i_err = AT_SetEnumIndex(m_handle, m_feature.c_str(), Index);
		errorChecker::checkError(i_err);
	}

	void Set(std::wstring Value)
	{
		int i_err = AT_SetEnumString(m_handle, m_feature.c_str(), Value.c_str());
		errorChecker::checkError(i_err);
	}
	
	std::wstring GetStringByIndex(int Index)
	{
		AT_WC* ws_returnString = new AT_WC[50];	// N.B. 50 is arbitrary, but will work for these tests
		int i_err = AT_GetEnumStringByIndex(m_handle, m_feature.c_str(), Index, ws_returnString, 50);
		errorChecker::checkError(i_err);

		return(std::wstring(ws_returnString));
	}

	int Count()
	{
		int i_returnCount;
		int i_err = AT_GetEnumCount(m_handle, m_feature.c_str(), &i_returnCount);
		errorChecker::checkError(i_err);

		return(i_returnCount);
	}

	bool IsIndexAvailable(int Index)
	{
		AT_BOOL i_returnBoolean;
		int i_err = AT_IsEnumIndexAvailable(m_handle, m_feature.c_str(), Index, &i_returnBoolean);
		errorChecker::checkError(i_err);

		return(i_returnBoolean==1);
	}

	bool IsIndexImplemented(int Index)
	{
		AT_BOOL i_returnBoolean;
		int i_err = AT_IsEnumIndexImplemented(m_handle, m_feature.c_str(), Index, &i_returnBoolean);
		errorChecker::checkError(i_err);

		return(i_returnBoolean==1);
	}

	bool IsImplemented() {return(stateTester->IsImplemented()); }
	bool IsReadable() {return(stateTester->IsReadable()); }
	bool IsWritable() {return(stateTester->IsWritable()); }
	bool IsReadOnly() {return(stateTester->IsReadOnly()); }

	void Attach(IObserver* Observer) { callbackBooker->Attach(Observer); }
	void Detach(IObserver* Observer) { callbackBooker->Detach(Observer); }

private:
	AT_H m_handle;
	std::wstring m_feature;
	TestState* stateTester;
	ISubject* callbackBooker;
};

class TBufferControl : public IBufferControl
{
public:
	TBufferControl(AT_H _handle) : m_handle(_handle)
	{

	}
	
	void Queue(unsigned char* Buffer, int BufferSize)
	{
		int i_err = AT_QueueBuffer(m_handle, Buffer, BufferSize);
		errorChecker::checkError(i_err);
	}

	bool Wait(unsigned char* &Buffer, int &BufferSize, int Timeout)
	{
		int i_err = AT_WaitBuffer(m_handle, &Buffer, &BufferSize, Timeout);
		errorChecker::checkError(i_err);

		return(i_err!=AT_ERR_TIMEDOUT?true:false);
	}

	void Flush()
	{
		int i_err = AT_Flush(m_handle);
		errorChecker::checkError(i_err);
	}

private:
	AT_H m_handle;
};

class TDevice : public IDevice
{
public:
  TDevice(AT_H _handle) : m_handle(_handle)
  {

  }

  ~TDevice()
  {

  }

  IInteger* GetInteger(std::wstring Name)
  {
    return new TInteger(m_handle, Name);
  }

  IFloat* GetFloat(std::wstring Name)
  {
	return new TFloat(m_handle, Name);
  }

  IBool* GetBool(std::wstring Name)
  {
    return new TBool(m_handle, Name);
  }

  ICommand* GetCommand(std::wstring Name)
  {
	return new TCommand(m_handle, Name);
  }

  IString* GetString(std::wstring Name)
  {
	return new TString(m_handle, Name);
  }

  IEnum* GetEnum(std::wstring Name)
  {
	return new TEnum(m_handle, Name);
  }

  IBufferControl* GetBufferControl()
  {
	return new TBufferControl(m_handle);
  }

  void Release(IFeature* Feature)
  {
	  delete Feature;
  }

  void ReleaseBufferControl(IBufferControl* BufferControl)
  {
	  delete BufferControl;
  }

  AT_H GetHandle(){return m_handle;}

private:
  AT_H m_handle;
};

class TDeviceManager : public IDeviceManager
{
public:
  TDeviceManager()
  {
	int i_err = AT_InitialiseLibrary();

	errorChecker::checkError(i_err);
  }

  ~TDeviceManager()
  {
	int i_err = AT_FinaliseLibrary();

	errorChecker::checkError(i_err);
  }

  IDevice* OpenDevice(int Index)
  {
	  AT_H temp_handle;
	  int i_err = AT_Open(Index, &temp_handle);
	  errorChecker::checkError(i_err);

	  return new TDevice(temp_handle);
  }

  IDevice* OpenSystemDevice()
  {
	  return new TDevice(AT_HANDLE_SYSTEM);
  }

  void CloseDevice(IDevice* Device)
  {
	  int i_err = AT_Close(Device->GetHandle());
	  errorChecker::checkError(i_err);
	  delete Device;
  }
};

}

#endif /* _ATCOREPLUSPLUS_H_ */