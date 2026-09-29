package org.micromanager.testing;

import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.junit.rules.ExternalResource;


@org.junit.Ignore
public class MMCoreResource extends ExternalResource {
   protected CMMCore mmc_;

   public CMMCore getMMCore() {
      return mmc_;
   }

   @Override
   protected void before() throws Exception {
      // These environment variables must be defined by the build system or
      // test-running script
      String jniLibPath = System.getenv("MMCOREJ_LIBRARY_PATH");
      String adapterPaths = System.getenv("MMTEST_ADAPTER_PATH");

      System.setProperty("mmcorej.library.loading.stderr.log", "yes");
      if (jniLibPath != null) {
         System.setProperty("mmcorej.library.path", jniLibPath);
      }

      mmc_ = new CMMCore();

      mmc_.enableStderrLog(true);
      mmc_.enableDebugLog(true);

      StrVector paths = new StrVector();
      String pathSep =
         java.util.regex.Pattern.quote(java.io.File.pathSeparator);
      if (adapterPaths != null) {
         for (String path : adapterPaths.split(pathSep)) {
            paths.add(path);
         }
      }
      mmc_.setDeviceAdapterSearchPaths(paths);
   }

   @Override
   protected void after() {
      // Ensure CoreLog is finished
      mmc_.delete();
      mmc_ = null;
   }
}
