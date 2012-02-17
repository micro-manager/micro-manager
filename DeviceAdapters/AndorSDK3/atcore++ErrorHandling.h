#ifndef _ATCOREPLUSPLUS_ERRORHANDLING_H
#define _ATCOREPLUSPLUS_ERRORHANDLING_H


class NotInitialisedException : public std::exception
{
public:
   NotInitialisedException(const char * _p)
      : exception(_p) {};
};

class NotImplementedException : public std::exception
{
public:
   NotImplementedException(const char * _p)
      : exception(_p) {};
};

class ReadOnlyException : public std::exception
{
public:
   ReadOnlyException(const char * _p)
      : exception(_p) {};
};

class NotReadableException : public std::exception
{
public:
   NotReadableException(const char * _p)
      : exception(_p) {};
};

class NotWritableException : public std::exception
{
public:
   NotWritableException(const char * _p)
      : exception(_p) {};
};

class OutOfRangeException : public std::exception 
{
public:
   OutOfRangeException(const char * _p)
      : exception(_p) {};
};

class EnumIndexNotAvailableException : public std::exception
{
public:
   EnumIndexNotAvailableException(const char * _p)
      : exception(_p) {};
};

class EnumIndexNotImplementedException : public std::exception
{
public:
   EnumIndexNotImplementedException(const char * _p)
      : exception(_p) {};
};

class ExceededMaxStringLengthException : public std::exception
{
public:
   ExceededMaxStringLengthException(const char * _p)
      : exception(_p) {};
};

class ConnectionException : public std::exception
{
public:
   ConnectionException(const char * _p)
      : exception(_p) {};
};

class NoDataException : public std::exception
{
public:
   NoDataException(const char * _p)
      : exception(_p) {};
};

class TimedOutException : public std::exception
{
public:
   TimedOutException(const char * _p)
      : exception(_p) {};
};

class BufferFullException : public std::exception
{
public:
   BufferFullException(const char * _p)
      : exception(_p) {};
};

class InvalidSizeException : public std::exception
{
public:
   InvalidSizeException(const char * _p)
      : exception(_p) {};
};

class InvalidAlignmentException : public std::exception
{
public:
   InvalidAlignmentException(const char * _p)
      : exception(_p) {};
};

class ComException : public std::exception
{
public:
   ComException(const char * _p)
      : exception(_p) {};
};

class StringNotAvailableException : public std::exception
{
public:
   StringNotAvailableException(const char * _p)
      : exception(_p) {};
};

class StringNotImplementedException : public std::exception
{
public:
   StringNotImplementedException(const char * _p)
      : exception(_p) {};
};


class NoMemoryException : public std::exception
{
public:
   NoMemoryException(const char * _p)
      : exception(_p) {};
};


class HardwareOverflowException : public std::exception
{
public:
   HardwareOverflowException(const char * _p)
      : exception(_p) {};
};

//end of atcore error codes exceptions

//Thrown by atcore on detach
class UnrecognisedObserverException: public std::exception
{
public:
   UnrecognisedObserverException(const char * _p)
      : exception(_p) {};
};

//thrown by atcore if can't throw any other exception!!
class UnrecognisedErrorCodeException: public std::exception
{
public:
   UnrecognisedErrorCodeException(const char * _p)
      : exception(_p) {};
};



#endif //include only once
