# This script builds binaries for three architectures (ppc, i386, and x86_64)
# from three repositories (which should be checked out already).
# It assumes the following directory structure:
# $REPOSITORYROOT - micro-manager1.3
#                 - micro-manager1.3-ppc
#                 - micro-manager1.3-i386
#                 - micro-manager1.3-x86_64
# contents of $BUILDDIR will be removed!!!

REPOSITORYROOT=/Users/MM/svn
BUILDDIR=/Users/MM/MMBuild
UPLOADPLACE=valelab.ucsf.edu:/home/MM/public_html/nightlyBuilds/1.3/Mac/

# No edits should be needed below this line

TARGET=$BUILDDIR/Micro-Manager1.3

PPC=$BUILDDIR/Micro-Manager1.3-ppc
I386=$BUILDDIR/Micro-Manager1.3-i386
X86_64=$BUILDDIR/Micro-Manager1.3-x86_64

REPOSITORY=$REPOSITORYROOT/micromanager1.3
RPPC=$REPOSITORYROOT/micromanager1.3-ppc
RI386=$REPOSITORYROOT/micromanager1.3-i386
RX86_64=$REPOSITORYROOT/micromanager1.3-x86_64

test -d $BUILDDIR && rm -rf $BUILDDIR
mkdir $BUILDDIR

cd $REPOSITORY
svn update
cd $REPOSITORY/SecretDeviceAdapters
svn update
cd $RPPC
svn update
cd $RPPC/SecretDeviceAdapters
svn update
cd $RI386
svn update
cd $RI386/SecretDeviceAdapters
svn update
cd $RX86_64
svn update
cd $RX86_64/SecretDeviceAdapters
svn update
cd $RPPC

cp -r MacInstaller/Micro-Manager $TARGET
cp classext/ij.jar $TARGET
cp classext/bsh-2.0b4.jar $TARGET/plugins/
cp classext/swingx-0.9.5.jar $TARGET/plugins/
cp classext/swing-layout-1.0.4.jar $TARGET/plugins/
cp classext/commons-math-2.0.jar $TARGET/plugins/
cp -r MacInstaller/Micro-Manager $PPC
cp classext/ij.jar $PPC
cp classext/bsh-2.0b4.jar $PPC/plugins/
cp classext/swingx-0.9.5.jar $PPC/plugins/
cp classext/swing-layout-1.0.4.jar $PPC/plugins/
cp classext/commons-math-2.0.jar $PPC/plugins/
cp -r MacInstaller/Micro-Manager $I386
cp classext/ij.jar $I386
cp classext/bsh-2.0b4.jar $I386/plugins/
cp classext/swingx-0.9.5.jar $I386/plugins/
cp classext/swing-layout-1.0.4.jar $I386/plugins/
cp classext/commons-math-2.0.jar $I386/plugins/
cp -r MacInstaller/Micro-Manager $X86_64
cp classext/ij.jar $X86_64
cp classext/bsh-2.0b4.jar $X86_64/plugins/
cp classext/swingx-0.9.5.jar $X86_64/plugins/
cp classext/swing-layout-1.0.4.jar $X86_64/plugins/
cp classext/commons-math-2.0.jar $X86_64/plugins/


# build PPC
cd $RPPC
# set version variable and change version in java source code to include build date stamp
VERSION=`cat version.txt`
#daily build
VERSION=$VERSION-`date "+%Y%m%d"`
echo $VERSION
sed -i -e "s/\"1.3.*\"/\"$VERSION\"/"  mmstudio/src/org/micromanager/MMStudioMainFrame.java || exit


autoreconf || exit
MACOSX_DEPLOYMENT_TARGET=10.4
./configure --with-imagej=$PPC --enable-python --enable-arch=ppc CXX="g++ -V 4.0.1" CXXFLAGS="-g -O2 -mmacosx-version-min=10.4 -isysroot /Developer/SDKs/MacOSX10.4u.sdk -arch ppc" --disable-dependency-tracking || exit
make || exit
make install || exit

# build i386
cd $RI386
autoreconf || exit
MACOSX_DEPLOYMENT_TARGET=10.4
./configure --with-imagej=$I386 --enable-arch=i386 CXX="g++ -V 4.0.1" CXXFLAGS="-g -O2 -mmacosx-version-min=10.4 -isysroot /Developer/SDKs/MacOSX10.4u.sdk -arch i386" --disable-dependency-tracking || exit
make || exit
make install || exit

# build x86_64
cd $RX86_64
autoreconf || exit
export MACOSX_DEPLOYMENT_TARGET=10.5
./configure --with-imagej=$X86_64 --enable-arch=x86_64 CXX="g++ -V 4.2.1" CXXFLAGS="-g -O2 -mmacosx-version-min=10.5 -isysroot /Developer/SDKs/MacOSX10.5.sdk -arch x86_64" --disable-dependency-tracking || exit
make || exit
make install || exit

# Use lipo to make Universal Binaries
lipo -create $PPC/libMMCoreJ_wrap.jnilib $I386/libMMCoreJ_wrap.jnilib $X86_64/libMMCoreJ_wrap.jnilib -o $TARGET/libMMCoreJ_wrap.jnilib
#strip -X -S $TARGET/libMMCoreJ_wrap.jnilib
cd $PPC
FILES=libmmgr*
for f in $FILES; do lipo -create $PPC/$f $I386/$f $X86_64/$f -o $TARGET/$f; done
#for f in $FILES; do strip -X -S $TARGET/$f; done
#lipo -create $PPC/_MMCorePy.so $I386/_MMCorePy.so $X86_64/_MMCorePy.so -o $TARGET/_MMCorePy.so


# copy installed files 
cp $PPC/plugins/Micro-Manager/* $TARGET/plugins/Micro-Manager/
cp $PPC/*.cfg $TARGET/
cp $PPC/_MMCorePy.so $TARGET/
cp -r $PPC/mmplugins $TARGET/
cp -r $PPC/mmautofocus $TARGET/ 
cp -r $PPC/scripts $TARGET/

# Build devicelist using 32-bit JVM (Set using /Applications/Utilities/Java Preferences)
cd $I386
java -cp plugins/Micro-Manager/MMJ_.jar:plugins/Micro-Manager/MMCoreJ.jar DeviceListBuilder
cp $I386/MMDeviceList.txt $TARGET/MMDeviceList.txt


cd $REPOSITORY/MacInstaller
./makemacdisk.sh -d -s $TARGET


# upload to mightly build server:
scp Micro-Manager$VERSION.dmg $UPLOADPLACE
