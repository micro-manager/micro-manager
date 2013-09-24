//
// PEAnalyzer - read Windows DLLs (and EXEs).
//

#pragma once

#include <Windows.h>

#include <exception>
#include <string>
#include <utility>
#include <vector>

#include <boost/shared_ptr.hpp>


namespace PEAnalyzer {


class Exception : public std::exception
{
public:
   explicit Exception(const std::string& message) : message_(message) {}
   virtual ~Exception() {}
   virtual const char* what() const { return message_.c_str(); }

private:
   const std::string message_;
};


class ModuleLoadFailedException : public Exception
{
public:
   explicit ModuleLoadFailedException(const std::string& message) : Exception(message) {}
};


class PEFormatException : public Exception
{
public:
   explicit PEFormatException(const std::string& message) : Exception(message) {}
};


class DataConsistencyException : public Exception
{
public:
   explicit DataConsistencyException(const std::string& message) : Exception(message) {}
};


/**
 * A memory-mapped file using Win32 API
 */
class MappedFile /* final */
{
public:
   typedef MappedFile Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(const std::string filename);

   ~MappedFile();

   void* GetBaseAddress();

private:
   MappedFile(HANDLE hFile, HANDLE hFileMapping, void* baseAddress);
   MappedFile& operator=(const MappedFile&); // Disable

   HANDLE hFile_;
   HANDLE hFileMapping_;
   void* baseAddress_;
};


/**
 * A memory-mapped Windows PE (portable executable) file
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

   static Ptr New(MappedFile::Ptr image);

   virtual ~PEFile() {}

   /// True if the DLL flag is set in the PE file
   bool IsDLL();

   /// Returns the list of imported DLLs
   boost::shared_ptr< std::vector<std::string> > GetImportNames();

   /// Returns the list of section names
   boost::shared_ptr< std::vector<std::string> > GetSectionNames();

   /// Returns a copy of the contents of the section with the given name
   std::pair<boost::shared_ptr<void>, size_t> GetSectionByName(const std::string& name);

   virtual bool IsMachine_x86() { return false; } // Default impl
   virtual bool IsMachine_x64() { return false; } // Default impl

protected:
   explicit PEFile(MappedFile::Ptr image);

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
   IMAGE_SECTION_HEADER* SectionBegin();
   IMAGE_SECTION_HEADER* SectionEnd();
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
   MappedFile::Ptr image_;
};


class PEFile32 : public PEFile
{
public:
   typedef PEFile32 Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(MappedFile::Ptr image);

   virtual bool IsMachine_x86() { return true; }

private:
   explicit PEFile32(MappedFile::Ptr image);
   IMAGE_OPTIONAL_HEADER32* GetOptionalHeader();
   virtual IMAGE_DATA_DIRECTORY* GetDataDirectory(size_t index);
};


class PEFile64 : public PEFile
{
public:
   typedef PEFile64 Self;
   typedef boost::shared_ptr<Self> Ptr;

   static Ptr New(MappedFile::Ptr image);

   virtual bool IsMachine_x64() { return true; }

private:
   explicit PEFile64(MappedFile::Ptr image);
   IMAGE_OPTIONAL_HEADER64* GetOptionalHeader();
   virtual IMAGE_DATA_DIRECTORY* GetDataDirectory(size_t index);
};


} // namespace PEAnalyzer
