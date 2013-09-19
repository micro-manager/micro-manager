//
// PEAnalyzer - read Windows DLLs.
//

#pragma once

#include <Windows.h>

#include <exception>
#include <string>
#include <vector>

#include <boost/shared_ptr.hpp>


namespace PEAnalyzer {


class Exception : public std::exception
{
public:
   Exception(const std::string& message) : message_(message) {}
   virtual ~Exception() {}
   virtual const char* what() const { return message_.c_str(); }

private:
   const std::string message_;
};


class ModuleLoadFailedException : public Exception
{
public:
   ModuleLoadFailedException(const std::string& message) : Exception(message) {}
};


class PEFormatException : public Exception
{
public:
   PEFormatException(const std::string& message) : Exception(message) {}
};


class DataConsistencyException : public Exception
{
public:
   DataConsistencyException(const std::string& message) : Exception(message) {}
};


/**
 * An executable image mapped to memory
 *
 */
class MappedImage /* final */
{
public:
   typedef MappedImage Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(const std::string filename);

   ~MappedImage();

   void* GetBaseAddress();

private:
   MappedImage(HANDLE hFile, HANDLE hFileMapping, void* baseAddress);
   MappedImage& operator=(const MappedImage&); // Disable

   HANDLE hFile_;
   HANDLE hFileMapping_;
   void* baseAddress_;
};


/**
 * Interpret a memory-mapped Windows PE (portable executable) file
 *
 * This class contains parts of the implementation that are common between
 * 32-bit and 64-bit PE files. Only i386 and amd64 binaries are supported.
 * Class PEFile is not designed for subclassing, with the exceptions of the
 * subclasses defined in this file (PEFile32 and PEFile64).
 */
class PEFile
{
public:
   typedef PEFile Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(MappedImage::Ptr image);
   virtual ~PEFile() {}

   bool IsDLL();

   virtual boost::shared_ptr< std::vector<std::string> > GetImportNames();

protected:
   PEFile(MappedImage::Ptr image);

   void* GetAddressForRVA(DWORD relativeVirtualAddress);
   IMAGE_NT_HEADERS* GetNTHeaders();
   IMAGE_FILE_HEADER* GetFileHeader();
   WORD GetNTOptionalHeaderMagic();

private:
   //
   // Architecture-independent
   //
   IMAGE_DOS_HEADER* GetDOSHeader();
   void CheckMagic();
   void* GetAddressForOffset(DWORD fileOffset);
   IMAGE_SECTION_HEADER* GetSectionContainingRVA(DWORD relativeVirtualAddress);
   IMAGE_IMPORT_DESCRIPTOR* GetImportDescriptors();

   //
   // Architecture-dependent
   //
   // Implementation supplied by subclasses
   virtual IMAGE_DATA_DIRECTORY* GetDataDirectory(size_t index);

   //
   // Data
   //
   MappedImage::Ptr image_;
};


class PEFile32 : public PEFile
{
public:
   typedef PEFile32 Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(MappedImage::Ptr image);

private:
   PEFile32(MappedImage::Ptr image);
   IMAGE_OPTIONAL_HEADER32* GetOptionalHeader();
   virtual IMAGE_DATA_DIRECTORY* GetDataDirectory(size_t index);
};


class PEFile64 : public PEFile
{
public:
   typedef PEFile64 Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(MappedImage::Ptr image);

private:
   PEFile64(MappedImage::Ptr image);
   IMAGE_OPTIONAL_HEADER64* GetOptionalHeader();
   virtual IMAGE_DATA_DIRECTORY* GetDataDirectory(size_t index);
};


} // namespace PEAnalyzer
