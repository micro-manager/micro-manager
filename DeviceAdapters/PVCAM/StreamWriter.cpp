#include "StreamWriter.h"

// MMDevice
#include "FixSnprintf.h"

// Local
#include "PVCAMAdapter.h"
#include "TaskSet_CopyMemory.h"
#include "ThreadPool.h"

// Boost
#include <boost/filesystem.hpp>
#include <boost/make_shared.hpp>
#include <boost/thread/locks.hpp>

// System
#include <ctime>
#include <cstdio>
#include <cstring> // ::memset
#include <fstream>
#include <limits>

#ifdef _WIN32
    #include <Windows.h>
    #include <malloc.h> // _aligned_malloc
    typedef void* FileHandle;
    #define cInvalidFileHandle (INVALID_HANDLE_VALUE)
#else
    #include <stdlib.h> // aligned_alloc
    #include <sys/types.h> // open
    #include <sys/stat.h> // open
    #include <fcntl.h> // open
    #include <unistd.h> // close, write, sysconf
    typedef int FileHandle;
    #define cInvalidFileHandle ((int)-1)
#endif

class StackFile
{
public:
    /// Opens the file in non-buffered mode
    StackFile(const char* fileName)
    {
#ifdef _WIN32
        // The FILE_FLAG_NO_BUFFERING flag is the key on Windows
        const int flags = FILE_FLAG_NO_BUFFERING;
        hFile_ = ::CreateFileA(fileName, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, flags, NULL);
#else
        // The O_DIRECT flag is the key on Linux
        const int flags = O_DIRECT | (O_WRONLY | O_CREAT | O_TRUNC);
        const mode_t mode = S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH;
        hFile_ = ::open(fileName, flags, mode);
#endif
    }
    /// Closes the file
    ~StackFile()
    {
        if (hFile_ == cInvalidFileHandle)
            return;
#ifdef _WIN32
        ::CloseHandle(hFile_);
#else
        ::close(hFile_);
#endif
    }

public:
    /// Reports the file was successfully open in constructor
    bool IsOpen() const
    {
        return (hFile_ != cInvalidFileHandle);
    }

    /// Writes the data to the file.
    /// The pData buffer must be page-aligned buffer that starts and ends on
    /// a page boundary, i.e. the size must be also page-aligned.
    bool Write(const void* pData, size_t bytes)
    {
        if (hFile_ == cInvalidFileHandle)
            return false;
#ifdef _WIN32
        if (bytes > (std::numeric_limits<DWORD>::max)())
            return false;
        DWORD bytesWritten = 0;
        return (::WriteFile(hFile_, pData, (DWORD)bytes, &bytesWritten, NULL) == TRUE
                && bytes == bytesWritten);
#else
        if (bytes > (std::numeric_limits<ssize_t>::max)())
            return false;
        return (::write(hFile_, pData, bytes) == (ssize_t)bytes);
#endif
    }

private:
    FileHandle hFile_;
};

StreamWriter::StreamWriter(Universal* camera)
    : camera_(camera),
    threadPool_(boost::make_shared<ThreadPool>()),
    tasksMemCopy_(boost::make_shared<TaskSet_CopyMemory>(threadPool_)),
    pageBytes_(0),
    isEnabled_(false),
    dirRoot_(),
    bitDepth_(0),
    frameBytes_(0),
    frameBytesAligned_(0),
    maxFramesPerStack_(0),
    alignedBuffer_(NULL),
    sessionId_(),
    path_(),
    isActive_(false),
    stackFile_(NULL),
    stackFileName_(),
    stackFileIndex_(0),
    stackFileFrameIndex_(0),
    //convBuf_(),
    totalFramesLost_(0),
    stackFramesLost_(0),
    totalSummary_(),
    stackSummary_(),
    lastFrameNr_(0)
{
    // Optimized/non-buffered streaming requires all file writes aligned to page size
#ifdef _WIN32
    SYSTEM_INFO sysInfo;
    ::GetSystemInfo(&sysInfo);
    pageBytes_ = sysInfo.dwPageSize;
#else
    pageBytes_ = ::sysconf(_SC_PAGESIZE);
#endif
    if (pageBytes_ == 0)
        pageBytes_ = 4096; // Use 4kB page size in case of any error
}

StreamWriter::~StreamWriter()
{
    boost::lock_guard<boost::mutex> lock(mx_);

    StopInternal();
    FreePageAlignedBuffer(alignedBuffer_);
}

int StreamWriter::Setup(bool enabled, const std::string& dirRoot, size_t bitDepth, size_t frameBytes)
{
    boost::lock_guard<boost::mutex> lock(mx_);

    StopInternal();

    isEnabled_ = enabled;

    if (dirRoot_ != dirRoot)
    {
        dirRoot_ = dirRoot;
        // Replace back-slashes by slashes and append trailing path separator if missing
        if (!dirRoot_.empty())
        {
            std::replace(dirRoot_.begin(), dirRoot_.end(), '\\', '/');
            if (dirRoot_.at(dirRoot_.size() - 1) != '/')
                dirRoot_ += '/';
        }
    }

    bitDepth_ = bitDepth;

    if (frameBytes_ != frameBytes)
    {
        frameBytes_ = frameBytes;
        const size_t frameBytesAligned =
            ((frameBytes_ + pageBytes_ - 1) / pageBytes_) * pageBytes_;

        if (frameBytesAligned_ != frameBytesAligned)
        {
            frameBytesAligned_ = frameBytesAligned;

            // Limit the stack file size to not be larger than 3 GB
            static const size_t maxFileBytes = size_t(3) * 1024 * 1024 * 1024;
            maxFramesPerStack_ = maxFileBytes / frameBytesAligned_;

            // Allocate page-aligned buffer as required by StackFile::Write.
            FreePageAlignedBuffer(alignedBuffer_);
            alignedBuffer_ = AllocatePageAlignedBuffer(frameBytesAligned_, pageBytes_);
            if (!alignedBuffer_)
            {
                return camera_->LogAdapterError(DEVICE_ERR, __LINE__,
                        std::string("Failed to allocate page-aligned buffer for streaming"));
            }
            ::memset(alignedBuffer_, 0, frameBytesAligned_);
        }
    }

    return DEVICE_OK;
}

int StreamWriter::Start()
{
    boost::lock_guard<boost::mutex> lock(mx_);

    StopInternal();

    if (!isEnabled_)
        return DEVICE_OK;
    if (dirRoot_.empty())
        return DEVICE_OK;
    if (frameBytesAligned_ == 0)
        return DEVICE_OK;

    int errCore = GenerateNewSessionId(sessionId_);
    if (errCore != DEVICE_OK)
        return errCore;

    path_ = dirRoot_ + sessionId_ + '/';

    errCore = CreateDirectories(path_);
    if (errCore != DEVICE_OK)
        return errCore;

    const std::string importFileName = path_ + "0_import_imagej.txt";
    errCore = GenerateImportHints_ImageJ(importFileName);
    if (errCore != DEVICE_OK)
        return errCore;

    stackFileIndex_ = 0;
    stackFileFrameIndex_ = 0;

    totalFramesLost_ = 0;
    stackFramesLost_ = 0;
    totalSummary_.clear();
    stackSummary_.clear();
    lastFrameNr_ = 0;

    isActive_ = true;

    camera_->LogAdapterMessage(std::string("Started streaming to '") + path_ + "'", false);

    return DEVICE_OK;
}

void StreamWriter::Stop()
{
    boost::lock_guard<boost::mutex> lock(mx_);

    StopInternal();
}

bool StreamWriter::IsActive() const
{
    boost::lock_guard<boost::mutex> lock(mx_);

    return isActive_;
}

int StreamWriter::WriteFrame(const void* pFrame, size_t frameNr)
{
    boost::lock_guard<boost::mutex> lock(mx_);

    if (!isActive_)
        return DEVICE_OK;

    if (stackFileFrameIndex_ == 0)
    {
        // Cannot use "%zu" to build on Linux without C++11
        //snprintf(convBuf_, sizeof(convBuf_), "stack-%05zu_fr-%06zu.raw", stackFileIndex_, frameNr);
        snprintf(convBuf_, sizeof(convBuf_), "stack-%05llu_fr-%06llu.raw",
                (unsigned long long)stackFileIndex_, (unsigned long long)frameNr);
        stackFileName_ = convBuf_;

        const std::string fullStackFileName = path_ + convBuf_;
        stackFile_ = new (std::nothrow) StackFile(fullStackFileName.c_str());
        if (!stackFile_ || !stackFile_->IsOpen())
        {
            StopInternal();
            return camera_->LogAdapterError(ERR_FILE_OPERATION_FAILED, __LINE__,
                    std::string("Failed to create file '") + fullStackFileName + "'");
        }
    }

    const size_t framesLost = frameNr - (lastFrameNr_ + 1);
    if (framesLost > 0)
    {
        if (framesLost == 1)
        {
            snprintf(convBuf_, sizeof(convBuf_), "%llu\n",
                    (unsigned long long)(lastFrameNr_ + 1));
        }
        else
        {
            snprintf(convBuf_, sizeof(convBuf_), "%llu-%llu\n",
                    (unsigned long long)(lastFrameNr_ + 1),
                    (unsigned long long)(frameNr - 1));
        }
        stackSummary_ += convBuf_;
        stackFramesLost_ += framesLost;
    }
    lastFrameNr_ = frameNr;

    const void* writeBuffer = pFrame;
    const bool isFrameAligned =
        ((size_t)pFrame % pageBytes_ == 0 && frameBytes_ == frameBytesAligned_);
    if (!isFrameAligned)
    {
        // Standard memcpy is too slow for Kinetix, do parallel copy instead
        //memcpy(alignedBuffer_, pFrame, frameBytes_);
        tasksMemCopy_->MemCopy(alignedBuffer_, pFrame, frameBytes_);
        writeBuffer = alignedBuffer_;
    }

    if (!stackFile_->Write(writeBuffer, frameBytesAligned_))
    {
        StopInternal();
        return camera_->LogAdapterError(ERR_FILE_OPERATION_FAILED, __LINE__,
                std::string("Failed to write frame to file '") + path_ + stackFileName_ + "'");
    }

    stackFileFrameIndex_++;
    if (stackFileFrameIndex_ == maxFramesPerStack_)
    {
        delete stackFile_;
        stackFile_ = NULL;
        stackFileIndex_++;
        stackFileFrameIndex_ = 0;

        MoveStackToTotalSummary();
    }

    return DEVICE_OK;
}

void* StreamWriter::AllocatePageAlignedBuffer(size_t bytes, size_t alignment)
{
    // Alignment must be power of two
    assert((alignment & (alignment - 1)) == 0);
    // Alignment must be multiple of pointer size (relies on check above)
    assert(alignment >= sizeof(void*));

    // The bytes argument should be multiple of alignment as per C11, otherwise
    // undefined behavior. This applies only to the non-Windows version.
#ifdef _WIN32
    return ::_aligned_malloc(bytes, alignment);
#else
    const size_t bytesAligned = (bytes + (alignment - 1)) & ~(alignment - 1);
    return ::aligned_alloc(alignment, bytesAligned);
#endif
}

void StreamWriter::FreePageAlignedBuffer(void* ptr)
{
#ifdef _WIN32
    ::_aligned_free(ptr);
#else
    ::free(ptr);
#endif
}

void StreamWriter::StopInternal()
{
    if (!isActive_)
        return;

    isActive_ = false;

    delete stackFile_;
    stackFile_ = NULL;

    MoveStackToTotalSummary();

    const std::string summaryFileName = path_ + "0_summary.txt";
    SaveSummary(summaryFileName);

    camera_->LogAdapterMessage(std::string("Stopped streaming to '") + path_ + "'", false);
}

int StreamWriter::GenerateNewSessionId(std::string& sessionId) const
{
    const std::time_t time = std::time(NULL);

    std::tm tm;
    // Thread-safe conversion to local time
#if defined(_WIN32)
    localtime_s(&tm, &time);
#else // POSIX
    localtime_r(&time, &tm);
#endif

    static const char format[]  = "YYYY-MM-DD_HH-MM-SS";
    static const char formatStr[] = "%Y-%m-%d_%H-%M-%S";
    char buffer[sizeof(format)];
    if (std::strftime(buffer, sizeof(buffer), formatStr, &tm) != sizeof(format) - 1)
    {
        return camera_->LogAdapterError(DEVICE_ERR, __LINE__,
                std::string("Failed to generate new streaming session ID"));
    }

    sessionId = buffer;

    return DEVICE_OK;
}

int StreamWriter::CreateDirectories(const std::string& path) const
{
    try
    {
        // Cannot rely on return value, it's false for path with trailing slash
        boost::filesystem::create_directories(path);
    }
    catch (...)
    {
        return camera_->LogAdapterError(DEVICE_ERR, __LINE__,
                std::string("Failed to create folder structure '") + path + "'");
    }
    return DEVICE_OK;
}

int StreamWriter::GenerateImportHints_ImageJ(const std::string& fileName) const
{
    const size_t width  = camera_->GetImageWidth();
    const size_t height = camera_->GetImageHeight();

    const char* typeStr = "";
    size_t pixelBytes = 0;
    if (bitDepth_ <= 8)
    {
        typeStr = "8-bit";
        pixelBytes = 1;
    }
    else if (bitDepth_ <= 16)
    {
        typeStr = "16-bit Unsigned";
        pixelBytes = 2;
    }
    else if (bitDepth_ <= 32)
    {
        typeStr = "32-bit Unsigned";
        pixelBytes = 4;
    }
    else
    {
        // Cannot use "%zu" to build on Linux without C++11
        snprintf(convBuf_, sizeof(convBuf_), "%llu", (unsigned long long)bitDepth_);
        return camera_->LogAdapterError(DEVICE_ERR, __LINE__,
                std::string("Unsupported bit depth for streaming: ") + convBuf_);
    }

    // ImageJ requires to know a "gap" between images, rather than offset or
    // something like that. ImageJ apparently calculates the next bitmap offset
    // from the given WxH definition, however, our frame size is not just WxH,
    // it may have padding and alignment. So we need to calculate the "gap"
    // using the WxH and count with any padding.

    const size_t rawDataBytes = pixelBytes * width * height;
    const bool pvcamHasMetadata = (rawDataBytes != frameBytes_);
    const size_t pvcamMetadataBytes = pvcamHasMetadata
        ? sizeof(md_frame_header) + sizeof(md_frame_roi_header)
        : 0;

    const size_t frameDataOffset = pvcamMetadataBytes;
    const size_t firstFrameDataOffset = frameDataOffset;
    const size_t gap = frameBytesAligned_ - frameBytes_ + frameDataOffset;

    std::ofstream nfo(fileName.c_str(), std::ios::binary | std::ios::trunc);
    if (!nfo.is_open())
    {
        return camera_->LogAdapterError(ERR_FILE_OPERATION_FAILED, __LINE__,
                std::string("Failed to save ImageJ import instructions to file '")
                + fileName + "'");
    }

    nfo << "To import the stack in ImageJ, use following procedure.\n"
        << "\n"
        << "- Drag & drop the .raw file into the ImageJ window or select File -> Import -> Raw...\n"
        << "- In the 'Import' dialog, set the following:\n"
        << "-- Image type: '" << typeStr << "'\n"
        << "-- Width: " << width << " pixels\n"
        << "-- Height: " << height << " pixels\n"
        << "-- Offset to first image: " << firstFrameDataOffset << " bytes\n"
        << "-- Number of images: " << maxFramesPerStack_ << "\n"
        << "   (it is max. configured value, ImageJ loads all available)\n"
        << "-- Gap between images: " << gap << " bytes\n"
        << "-- White is zero: Unchecked\n"
        << "-- Little-endian byte order: checked\n"
        << "-- Open all files in folder: possibly checked\n"
        << "   (caution with huge stacks, ImageJ loads all data to RAM)\n"
        << "-- Use virtual stack: keep unchecked\n"
        << "   (check only when importing a single huge file to avoid RAM caching)\n";

    nfo.close();

    return DEVICE_OK;
}

int StreamWriter::SaveSummary(const std::string& fileName) const
{
    std::ofstream sum(fileName.c_str(), std::ios::trunc);
    if (!sum.is_open())
    {
        return camera_->LogAdapterError(ERR_FILE_OPERATION_FAILED, __LINE__,
                std::string("Failed to save summary to file '") + fileName + "'");
    }

    size_t totalFrameCount = maxFramesPerStack_ * stackFileIndex_ + stackFileFrameIndex_;

    if (totalFramesLost_ == 0)
    {
        sum << "All " << totalFrameCount << " frames saved.\n";
    }
    else
    {
        sum << "Saved " << totalFrameCount << " frames, " << totalFramesLost_ << " frames lost.\n"
            << totalSummary_;

    }

    sum.close();

    return DEVICE_OK;
}

void StreamWriter::MoveStackToTotalSummary()
{
    if (stackFramesLost_ == 0)
        return;

    snprintf(convBuf_, sizeof(convBuf_), "%llu", (unsigned long long)stackFramesLost_);
    totalSummary_ += "\n" + stackFileName_ + " - lost " + convBuf_ + " frames:\n" + stackSummary_;
    totalFramesLost_ += stackFramesLost_;

    stackFramesLost_ = 0;
    stackSummary_.clear();
}
