# This script builds binaries for three architectures (ppc, i386, and x86_64)
# from a single repository.
# It does a clean build, and therefore can best be used only for a full release

REPOSITORYROOT=/Users/MM/svn
BUILDDIR=/Users/MM/MMBuild
UPLOADPLACE=valelab.ucsf.edu:/home/MM/public_html/builds/1.4/Mac/

TARGET=$BUILDDIR/Micro-Manager1.4
PPC=$BUILDDIR/Micro-Manager1.4-ppc
I386=$BUILDDIR/Micro-Manager1.4-i386
X86_64=$BUILDDIR/Micro-Manager1.4-x86_64

REPOSITORY=$REPOSITORYROOT/micromanager1.4
RPPC=$REPOSITORYROOT/micromanager1.4-ppc
RI386=$REPOSITORYROOT/micromanager1.4-i386
RX86_64=$REPOSITORYROOT/micromanager1.4-x86_64

CLASSEXT=$REPOSITORYROOT/3rdpartypublic/classext


test -d $BUILDDIR && rm -rf $TARGET*
mkdir $BUILDDIR

cd $REPOSITORYROOT/3rdpartypublic/
svn update
cd $REPOSITORY
svn update
cd $REPOSITORY/SecretDeviceAdapters
svn update
cd $REPOSITORY

cp -r MacInstaller/Micro-Manager $TARGET
find $TARGET -name '.svn' -exec rm -fr {} \;
cp $CLASSEXT/ij.jar $TARGET
cp $CLASSEXT/bsh-2.0b4.jar $TARGET/plugins/
cp $CLASSEXT/clojure.jar $TARGET/plugins/
cp $CLASSEXT/swingx-0.9.5.jar $TARGET/plugins/
cp $CLASSEXT/swing-layout-1.0.4.jar $TARGET/plugins/
cp $CLASSEXT/commons-math-2.0.jar $TARGET/plugins/
cp -r $TARGET $PPC
cp -r $TARGET $I386
cp -r $TARGET $X86_64


./mmUnixBuild.sh || exit
cd $REPOSITORY

# set version variable and change version in java source code to include build date stamp
VERSION=`cat version.txt`
echo $VERSION
sed -i -e "s/\"1.4.*\"/\"$VERSION\"/"  mmstudio/src/org/micromanager/MMStudioMainFrame.java || exit

# build PPC
MACOSX_DEPLOYMENT_TARGET=10.4
./configure --with-imagej=$PPC --enable-python --enable-arch=ppc --with-boost=/usr/local/ppc CXX="g++-4.0" CXXFLAGS="-g -O2 -mmacosx-version-min=10.4 -isysroot /Developer/SDKs/MacOSX10.4u.sdk -arch ppc" --disable-dependency-tracking || exit
make clean || exit
make || exit
make install || exit

# build i386
MACOSX_DEPLOYMENT_TARGET=10.4
./configure --with-imagej=$I386 --enable-python --enable-arch=i386 --with-boost=/usr/local/i386 CXX="g++-4.0" CXXFLAGS="-g -O2 -mmacosx-version-min=10.4 -isysroot /Developer/SDKs/MacOSX10.4u.sdk -arch i386" --disable-dependency-tracking || exit
make clean || exit
make || exit
make install || exit

# build x86_64
export MACOSX_DEPLOYMENT_TARGET=10.5
./configure --with-imagej=$X86_64 --enable-arch=x86_64 --with-boost=/usr/local/x86_64 CXX="g++-4.2" CXXFLAGS="-g -O2 -mmacosx-version-min=10.5 -isysroot /Developer/SDKs/MacOSX10.5.sdk -arch x86_64" --disable-dependency-tracking || exit
make clean || exit
make || exit
make install || exit

# Use lipo to make Universal Binaries
lipo -create $PPC/libMMCoreJ_wrap.jnilib $I386/libMMCoreJ_wrap.jnilib $X86_64/libMMCoreJ_wrap.jnilib -o $TARGET/libMMCoreJ_wrap.jnilib
#strip -X -S $TARGET/libMMCoreJ_wrap.jnilib
cd $PPC

FILES=libmmgr*
for f in $FILES; do lipo -create $PPC/$f $I386/$f $X86_64/$f -o $TARGET/$f; done
#for f in $FILES; do strip -X -S $TARGET/$f; done

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

#Install Python, only I386
cp $RI386/bin/MMCorePy.py $TARGET/
cp $RI386/bin/_MMCorePy.so $TARGET/


cd $REPOSITORY/MacInstaller
./makemacdisk.sh -r -s $TARGET

# upload to mightly build server:
scp Micro-Manager$VERSION.dmg $UPLOADPLACE
