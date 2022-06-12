/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile.internal;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/**
 * A file-based lock to prevent profiles from being written by multiple
 * instances of the program.
 * <p>
 * Limitation: if the lock file is deleted by an external means, a second
 * acquisition will be possible (at least on Unix). (We don't make any attempt
 * to guard against intentional hacks.)
 *
 * @author Mark A. Tsuchida
 */
final class ProfileWriteLock {
   private final File file_;
   private RandomAccessFile raf_;
   private FileChannel channel_;
   private FileLock lock_;

   static ProfileWriteLock tryCreate(File lockFile) throws IOException {
      ProfileWriteLock ret = new ProfileWriteLock(lockFile);
      if (ret.tryLock()) {
         return ret;
      }
      return null;
   }

   private ProfileWriteLock(File lockFile) {
      file_ = lockFile;
   }

   boolean tryLock() throws IOException {
      try {
         raf_ = new RandomAccessFile(file_, "rw");
         channel_ = raf_.getChannel();
         try {
            lock_ = channel_.tryLock();
         } catch (OverlappingFileLockException e) {
            // Second write lock in this JVM
            return false;
         }
         return lock_ != null;
      } finally {
         if (lock_ == null) {
            if (channel_ != null) {
               channel_.close();
            }
            if (raf_ != null) {
               raf_.close();
            }
         }
         else { // Only delete if we acquired the lock
            file_.deleteOnExit();
         }
      }
   }

   @Override
   public String toString() {
      return String.format("<ProfileWriteLock (%s)>", file_.getAbsolutePath());
   }

   public static void main(String[] args) {
      try {
         ProfileWriteLock lock = tryCreate(new File("test-lock"));
         System.out.println(lock);
         if (lock != null) {
            Thread.sleep(15000);
         }
      } catch (IOException e) {
         System.err.println(e.getMessage());
      } catch (InterruptedException e) {
      }
   }
}