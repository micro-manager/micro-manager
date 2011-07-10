if exist classes rmdir classes /s /q
mkdir classes
xcopy /E /y src classes\
"%JAVA_HOME%\bin\java" -cp ../../3rdpartypublic/classext/clojure.jar;../MMCoreJ_wrap/MMCoreJ.jar;../../3rdpartypublic/classext/ij.jar;../mmstudio/MMJ_.jar;../../3rdpartypublic/classext/bsh-2.0b4.jar;./src -Dclojure.compile.path=classes clojure.lang.Compile org.micromanager.acq-engine
"%JAVA_HOME%\bin\jar" cf MMAcqEngine.jar -C classes\ .    
copy /Y MMAcqEngine.jar ..\bin_Win32\
copy /Y MMAcqEngine.jar ..\bin_x64\
copy /Y MMAcqEngine.jar ..\Install_Win32\micro-manager\plugins\Micro-Manager"
copy /Y MMAcqEngine.jar ..\Install_x64\micro-manager\plugins\Micro-Manager"
