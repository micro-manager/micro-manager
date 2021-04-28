#ifndef _STREAMWRITER_H_
#define _STREAMWRITER_H_

#include <string>

#include <boost/smart_ptr/shared_ptr.hpp>
#include <boost/thread/mutex.hpp>

class StackFile;
class TaskSet_CopyMemory;
class ThreadPool;
class Universal;

class StreamWriter
{
public:
    /**
    * Creates the writer. The streaming needs to be started with Start().
    * @param camera A pointer to the owner class.
    */
    StreamWriter(Universal* camera);
    /**
    * Deletes the object, stops the streaming if active.
    */
    ~StreamWriter();

    StreamWriter(const StreamWriter&)/* = delete*/;
    StreamWriter& operator=(const StreamWriter&)/* = delete*/;

public:
    /**
    * Stops the streaming if active and reconfigures the writer.
    * Returns DEVICE_OK on success, otherwise MM error code is returned.
    */
    int Setup(bool enabled, const std::string& dirRoot, size_t bitDepth, size_t frameBytes);

    /**
    * Stops the streaming if active, generates new session ID, creates target
    * folder and generates file with import instructions for ImageJ.
    * Returns DEVICE_OK on success, otherwise MM error code is returned.
    */
    int Start();

    /**
    * Stops the streaming if active.
    */
    void Stop();

    /**
    * Says whether the streaming is active or not.
    */
    bool IsActive() const;

    /**
    * Writes given frame to disk if active.
    * Returns DEVICE_OK on success or if not active, otherwise MM error code is returned.
    */
    int WriteFrame(const void* pFrame, size_t frameNr);

private:
    /**
    * Platform-agnostic wrapper that allocates page-aligned chunk of memory.
    */
    static void* AllocatePageAlignedBuffer(size_t bytes, size_t alignment);
    /**
    * Platform-agnostic wrapper that releases page-aligned chunk of memory.
    */
    static void FreePageAlignedBuffer(void* ptr);

private:
    /**
    * Stops the streaming if active without locking mutex.
    */
    void StopInternal();

    /**
    * Generates new session ID.
    */
    int GenerateNewSessionId(std::string& sessionId) const;
    /**
    * Creates folder structure.
    */
    int CreateDirectories(const std::string& path) const;
    /**
    * Generates file with instructions how to import created files to ImageJ.
    */
    int GenerateImportHints_ImageJ(const std::string& fileName) const;

    /**
    * Generates summary file with stats, lost frame numbers, etc.
    */
    int SaveSummary(const std::string& fileName) const;
    /**
    * When stack is closed and there were some frames lost, total summary is updated.
    */
    void MoveStackToTotalSummary();

private:
    const Universal* camera_;

    // TODO: Get some shared pool from outside
    const boost::shared_ptr<ThreadPool> threadPool_;
    const boost::shared_ptr<TaskSet_CopyMemory> tasksMemCopy_;

    size_t pageBytes_; // Set only once in constructor

    mutable boost::mutex mx_; // For serialization of public method calls

    bool isEnabled_; // User choice
    std::string dirRoot_; // User choice
    size_t bitDepth_; // Taken from PVCAM
    size_t frameBytes_; // Taken from PVCAM, may include metadata
    size_t frameBytesAligned_; // frameBytes_ aligned to pageBytes_
    size_t maxFramesPerStack_; // Max. number of frames that fit in 3 GB
    void* alignedBuffer_; // Allocated to frameBytesAligned_ if differs from frameBytes_

    std::string sessionId_; // Auto-generated as timestamp
    std::string path_; // dirRoot_ + session_
    bool isActive_; // True when set up and configured

    StackFile* stackFile_;
    std::string stackFileName_; // Only file name without path
    size_t stackFileIndex_;
    size_t stackFileFrameIndex_;

    mutable char convBuf_[1024];
    size_t totalFramesLost_;
    size_t stackFramesLost_;
    std::string totalSummary_;
    std::string stackSummary_;
    size_t lastFrameNr_;
};

#endif // _STREAMWRITER_H_
