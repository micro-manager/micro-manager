set JAVA_HOME=%JAVA8_HOME%
set Path=%JAVA_HOME%\bin;%Path%

echo %JAVA_HOME%
echo %ANT_HOME%
echo %Path%
java -version
cmd /c ant -version

set /p REV_3RDPARTYPUBLIC=<micro-manager\3rdpartypublic-revision
set /p REV_3RDPARTY=<micro-manager\3rdparty-revision

move C:\3rdpartypublic .\
cd 3rdpartypublic
svn update -q -r%REV_3RDPARTYPUBLIC%
cd %WORKSPACE%

move C:\3rdparty .\
cd 3rdparty
svn --username %SVN_USERNAME% --password %SVN_PASSWORD% ^
    update -q -r%REV_3RDPARTY%
cd %WORKSPACE%

cd micro-manager\mmCoreAndDevices
mkdir %USERPROFILE%\.ssh
copy %KNOWN_HOSTS_GITHUB% %USERPROFILE%\.ssh\known_hosts
copy %MM_SECRETDEVICEADAPTERS_SSHKEY% %USERPROFILE%\.ssh\id_ed25519
"C:\Program Files\Git\bin\bash" secret-device-adapters-checkout.sh use_ssh
del %USERPROFILE%\.ssh\id_ed25519
cd %WORKSPACE%

if exist ivy2-cache.zip (
    7z x -y ivy2-cache.zip -o%USERPROFILE%
    del ivy2-cache.zip
)

cd micro-manager\
git rev-parse --short HEAD > version.txt
cmd /c ant -f buildscripts\fetchdeps.xml -Dmm.ivy.failonerror=true
cmd /c ant -f buildscripts\nightly\nightlybuild_Windows.xml ^
    -listener org.apache.tools.ant.XmlLogger ^
    -logger org.apache.tools.ant.listener.SimpleBigProjectLogger ^
    -DXmlLogger.file=%WORKSPACE%\buildlog.xml ^
    -Dmm.build.failonerror=true
set RESULT=%ERRORLEVEL%

cd %WORKSPACE%
python micro-manager\buildscripts\nightly\genreport_Windows.py ^
    micro-manager buildlog.xml buildreport.html

7z a ivy2-cache.zip %USERPROFILE%\.ivy2

exit %RESULT%