#!/bin/bash

# Run from source root!

REVISION=`git svn find-rev master`
BUILDNAME=r${REVISION}+VS2010
DSTAMP=`date +%Y%m%d`

git clean -df
pushd SecretDeviceAdapters
git clean -df
popd
rm -rf build stage dist

PACKAGEDIR=package-${DSTAMP}
rm -rf $PACKAGEDIR
mkdir -p $PACKAGEDIR

echo Creating source archive...
ARCHIVENAME=micromanager-src-${BUILDNAME}
git archive -o ${ARCHIVENAME}.zip -9 HEAD
mv ${ARCHIVENAME}.zip ${PACKAGEDIR}/

echo Creating patch set...
PATCHES=micromanager-patches-${BUILDNAME}
rm -rf ${PATCHES}
mkdir ${PATCHES}
git format-patch --minimal --no-binary --subject-prefix=VS2010 -o ${PATCHES} master
# Would be nice to zip this, but I'm not going to bother with installing zip.

echo Building...
REPORT=buildreport-${BUILDNAME}.html
TMPBAT=tmp-build.bat
cat > $TMPBAT <<EOF
@echo off

set log_filename=buildlog.xml
set report_filename=${REPORT}

if exist %log_filename% (
  del %log_filename%
)

call ant -f buildscripts\nightly\nightlybuild_Windows.xml ^
  -listener org.apache.tools.ant.XmlLogger ^
  -logger org.apache.tools.ant.listener.SimpleBigProjectLogger ^
  -DXmlLogger.file=%log_filename% ^
  -Dmm.versionstring=${BUILDNAME}

if not exist %log_filename% (
  echo Ant failed without producing XML log file
  exit /b 1
)

rem python 3.3
python buildscripts\nightly\genreport_Windows.py . %log_filename% %report_filename%
EOF
cmd "/c $TMPBAT"
rm $TMPBAT
mv ${REPORT} ${PACKAGEDIR}/
start ${PACKAGEDIR}/${REPORT}

echo Renaming installers...
INSTALLER32=MMSetup_32bit_${BUILDNAME}.exe
INSTALLER64=MMSetup_64bit_${BUILDNAME}.exe
mv dist/MMSetup_32bit.exe ${PACKAGEDIR}/${INSTALLER32}
mv dist/MMSetup_64bit.exe ${PACKAGEDIR}/${INSTALLER64}

echo Writing HTML paragraph...
HTML=files-${DSTAMP}.html
cat > $HTML <<EOF
<h4>${DSTAMP}: Branch based off of SVN r${REVISION}</h4>
<div>
<ul>
<li><a href="${ARCHIVENAME}.zip">Source code</a></li>
<li><a href="${PATCHES}.zip">Patch set</a> (excludes binary file changes, collected at end of set)</li>
<li><a href="${REPORT}">Build warnings summary</a> (there a lots, due to uniformly applying warning level 4)</li>
<li><a href="${INSTALLER32}">32-bit installer</a></li>
<li><a href="${INSTALLER64}">64-bit installer</a></li>
</ul>
</div>
EOF
mv ${HTML} ${PACKAGEDIR}/

echo TODO: Compress contents of ${PATCHES} to ${PACKAGEDIR}/${PATCHES}.zip
echo TODO: Remove SecretDeviceAdapters warnings from ${PACKAGEDIR}/${REPORT}
start ${PACKAGEDIR}/${HTML}

echo Done.
