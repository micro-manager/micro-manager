// An Ant task that determines whether all .class files in a given directory
// are newer than a set of given (usually JAR) files. Used to trigger a rebuild
// when a dependency changes (thus facilitating work with not-so-modular JARs).

package org.micromanager.build;

import java.io.File;
import java.util.AbstractCollection;
import java.util.ArrayList;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;

public class ClassesUpToDate extends Task {
   private File classDirectory;
   private String propertyName;
   private AbstractCollection<String> dependencyFiles;

   public ClassesUpToDate() {
      dependencyFiles = new ArrayList<String>();
   }

   public void setDir(File directory) {
      classDirectory = directory;
   }

   public void setProperty(String property) {
      propertyName = property;
   }

   public void addConfiguredPath(Path path) {
      java.util.Collections.addAll(dependencyFiles, path.list());
   }

   @Override
   public void execute() throws BuildException {
      final long oldestClassTimestamp = getOldestClassTimestamp();
      final long newestDependencyTimestamp = getNewestDependencyTimestamp();

      if (oldestClassTimestamp < newestDependencyTimestamp) {
         getProject().setNewProperty(propertyName, "true");
      }
   }

   private long getOldestClassTimestamp() throws BuildException {
      long oldest = Long.MAX_VALUE;
      for (File file : findClassFiles(classDirectory)) {
         final long mtime = file.lastModified();
         if (mtime < oldest) {
            oldest = mtime;
         }
      }
      return oldest;
   }

   private AbstractCollection<File> findClassFiles(File directory) {
      AbstractCollection<File> classFiles = new ArrayList<File>();

      File[] files = directory.listFiles();
      if (files != null) {
         for (File f : directory.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".class")) {
               classFiles.add(f);
            }
            else if (f.isDirectory()) {
               classFiles.addAll(findClassFiles(f));
            }
         }
      }

      return classFiles;
   }

   private long getNewestDependencyTimestamp() {
      long newest = Long.MIN_VALUE;
      for (String fileName : dependencyFiles) {
         File file = new File(fileName);
         final long mtime = file.lastModified();
         if (mtime > newest) {
            newest = mtime;
         }
      }
      return newest;
   }
}
