#ifndef _ATCOREPLUSPLUS_H_
#define _ATCOREPLUSPLUS_H_

#include <string>
#include <sstream>
#include <list>
#include "atcore.h"
#include "atcore++ErrorHandling.h"
#include "andorvartypes.h"

namespace andor {

class ISubject;

class errorChecker
{
public:
   static void checkError(int _i_error)
   {
      switch (_i_error)
      {
      case AT_SUCCESS: break;
      case AT_ERR_NOTINITIALISED: throw NotInitialisedException("Exception[AT_ERR_NOTINITIALISED] thrown: Uninitialised Handle"); break;
      case AT_ERR_NOTIMPLEMENTED: throw NotImplementedException("Exception[AT_ERR_NOTIMPLEMENTED] thrown: Feature Not Implemented"); break;
      case AT_ERR_READONLY: throw ReadOnlyException("Exception[AT_ERR_READONLY] thrown: Feature is Readonly"); break;
      case AT_ERR_NOTREADABLE: throw NotReadableException("Exception[AT_ERR_NOTREADABLE] thrown: Feature not currently Readable"); break;
      case AT_ERR_NOTWRITABLE: throw NotWritableException("Exception[AT_ERR_NOTWRITABLE] thrown: Feature not currently Writable"); break;
      case AT_ERR_OUTOFRANGE: throw OutOfRangeException("Exception[AT_ERR_OUTOFRANGE] thrown: Value outside Min & Max Limits"); break;
      case AT_ERR_INDEXNOTAVAILABLE: throw EnumIndexNotAvailableException("Exception[AT_ERR_INDEXNOTAVAILABLE] thrown: Enum Index currently not Available"); break;
      case AT_ERR_INDEXNOTIMPLEMENTED: throw EnumIndexNotImplementedException("Exception[AT_ERR_INDEXNOTIMPLEMENTED] thrown: Enum Index Not Implemented"); break;
      case AT_ERR_EXCEEDEDMAXSTRINGLENGTH: throw ExceededMaxStringLengthException("Exception[AT_ERR_EXCEEDEDMAXSTRINGLENGTH] thrown: StringValue exceeds MaxLength"); break;
      case AT_ERR_CONNECTION: throw ConnectionException("Exception[AT_ERR_CONNECTION] thrown: Error Connecting / Disconnecting Hardware"); break;
      case AT_ERR_NODATA: throw NoDataException("Exception[AT_ERR_NODATA] thrown: No Internal Event or Internal Error"); break;
      case AT_ERR_TIMEDOUT: break; //Only returned on BufferWait timeout - boolean returned by atcore++ function
      case AT_ERR_BUFFERFULL: throw BufferFullException("Exception[AT_ERR_BUFFERFULL] thrown: Input Queue @Max Capacity"); break;
      case AT_ERR_INVALIDSIZE: throw InvalidSizeException("Exception[AT_ERR_INVALIDSIZE] thrown: Size of queued Buffer != Frame Size"); break;
      case AT_ERR_INVALIDALIGNMENT: throw InvalidAlignmentException("Exception[AT_ERR_INVALIDALIGNMENT] thrown: Queued Buffer not aligned 8-byte boundary"); break;
      case AT_ERR_COMM: throw ComException("Exception[AT_ERR_COMM] thrown: Error Communicating with Hardware"); break;
      case AT_ERR_STRINGNOTAVAILABLE: throw StringNotAvailableException("Exception[AT_ERR_STRINGNOTAVAILABLE] thrown: Enum Index / String not Available"); break;
      case AT_ERR_STRINGNOTIMPLEMENTED: throw StringNotImplementedException("Exception[AT_ERR_STRINGNOTIMPLEMENTED] thrown: Enum Index / String not Implemented"); break;
      case AT_ERR_NOMEMORY: throw NoMemoryException("Exception[AT_ERR_NOMEMORY] thrown: Out of Memory!"); break;
      case AT_ERR_HARDWARE_OVERFLOW: throw HardwareOverflowException("Exception[AT_ERR_HARDWARE_OVERFLOW] thrown: Internal Buffer Overflow: Slow Data retrieval from card/camera"); break;
      
      default: 
         {
            std::stringstream s_error;
            s_error << "Unrecognised Error Code [";
            s_error << _i_error << "]";
            throw UnrecognisedErrorCodeException(s_error.str().c_str() );
            break;
         }
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
         throw UnrecognisedObserverException("[TSubject::Detach] Observer not Recognised with this subject");
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

  andor64 Get()
  {
     andor64 i_returnInteger;
     int i_err = AT_GetInt(m_handle, m_feature.c_str(), &i_returnInteger);
     errorChecker::checkError(i_err);

     return(i_returnInteger);
  }

  void Set(long long Value)
  {
     int i_err = AT_SetInt(m_handle, m_feature.c_str(), Value);
     errorChecker::checkError(i_err);
  }

  andor64 Max()
  {
     andor64 i_returnInteger;
     int i_err = AT_GetIntMax(m_handle, m_feature.c_str(), &i_returnInteger);
     errorChecker::checkError(i_err);

     return(i_returnInteger);
  }

  andor64 Min()
  {
     andor64 i_returnInteger;
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
      AT_WC* ws_returnString = new AT_WC[50];   // N.B. 50 is arbitrary, but will work for these tests
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

      return (AT_ERR_TIMEDOUT == i_err ? false : true);
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

}//end namespace andor

#endif /* _ATCOREPLUSPLUS_H_ */