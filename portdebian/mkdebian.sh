########################################### MM

#package: portable files + binary (almost none portable)
ROOTPORT=/tmp/mmdeb
rm -Rf $ROOTPORT
mkdir $ROOTPORT

ROOTBIN=$ROOTPORT

if test `uname -m` = "x86_64"
then
ARCH="amd64"
else
ARCH="i386"
fi

VERSION=`cat version.txt`

echo $ARCH
echo "--$VERSION--"

##### Meta information   MM
mkdir -p $ROOTPORT/DEBIAN
mkdir -p $ROOTBIN/usr/lib/micro-manager/
cp doc/copyright.txt $ROOTPORT/DEBIAN/copyright
#cp portdebian/debiancontrol.port $ROOTPORT/DEBIAN/control
echo "#!/bin/sh" > $ROOTBIN/DEBIAN/postinst
chmod 0755 $ROOTBIN/DEBIAN/postinst

cat portdebian/debiancontrol.port | sed "s/ARCH/$ARCH/" | sed "s/VERSION/$VERSION/" > $ROOTPORT/DEBIAN/control


##### Programs
mkdir -p $ROOTBIN/usr/bin/
#cp Test_Serial/mm_testserial ModuleTest/mm_moduletest Test_MMCore/mm_testCore $ROOTBIN/usr/bin/
#strip $ROOTBIN/usr/bin/*


##### JAR-files
mkdir -p $ROOTPORT/usr/share/java/
cp MMCoreJ_wrap/MMCoreJ.jar $ROOTPORT/usr/share/java/
	#####../3rdpartypublic/classext/bsh-2.0b4.jar ../3rdpartypublic/classext/syntax.jar ../3rdpartypublic/classext/ij.jar #assume these are available

##### All plugins
cp DeviceAdapters/*/.libs/*.so $ROOTBIN/usr/lib/micro-manager/
strip $ROOTBIN/usr/lib/micro-manager/*
rename 's/\.so$/\.so\.0/' $ROOTBIN/usr/lib/micro-manager/lib*

##### Core
cp MMCoreJ_wrap/.libs/libMMCoreJ_wrap.so $ROOTBIN/usr/lib/micro-manager/


##### Make shared objects visible without hardcoding the path
mkdir -p $ROOTPORT/etc/ld.so.conf.d/
echo "/usr/lib/micro-manager" > $ROOTPORT/etc/ld.so.conf.d/micro-manager.conf
echo "/sbin/ldconfig" >> $ROOTBIN/DEBIAN/postinst


########################################## MMIJ

#package: micromanager-imagej
ROOTIJ=/tmp/mmijdeb
rm -Rf $ROOTIJ
mkdir $ROOTIJ


##### Meta information MMIJ
mkdir $ROOTIJ/DEBIAN
cp doc/copyright.txt $ROOTIJ/DEBIAN/copyright
#cp portdebian/debiancontrol.ij $ROOTIJ/DEBIAN/control

cat portdebian/debiancontrol.ij | sed "s/ARCH/$ARCH/" | sed "s/VERSION/$VERSION/" > $ROOTIJ/DEBIAN/control

##### JAR-files  MMIJ
mkdir -p $ROOTIJ/usr/share/imagej/plugins/Micro-Manager/
cp plugins/*.jar mmstudio/MMJ_.jar $ROOTIJ/usr/share/imagej/plugins/Micro-Manager/
ln -s /usr/share/java/bsh.jar $ROOTIJ/usr/share/imagej/plugins/Micro-Manager/bsh_.jar

##### Overwrite imagej with fixes
mkdir -p $ROOTIJ/usr/share/imagej/jni
echo "/usr/lib/java" > $ROOTIJ/usr/share/imagej/jni/debian
echo "/usr/lib/micro-manager" > $ROOTIJ/usr/share/imagej/jni/micromanager
mkdir -p $ROOTIJ/usr/bin
cp portdebian/newImagej $ROOTIJ/usr/bin/imagejmm
chmod 0755 $ROOTIJ/usr/bin/imagejmm

echo "#!/bin/sh" > $ROOTIJ/DEBIAN/postinst
chmod 0755 $ROOTIJ/DEBIAN/postinst
#echo "rm /usr/bin/imagej" >> $ROOTIJ/DEBIAN/postinst
#echo "ln -s /usr/bin/imagejmm /usr/bin/imagej" >> $ROOTIJ/DEBIAN/postinst

########################################## Put together
cd ..
dpkg-deb -b $ROOTPORT
dpkg-deb -b $ROOTIJ
CURDATE=`date +%s`
mv $ROOTPORT.deb "micromanager-$VERSION"-$CURDATE"_$ARCH.deb"
mv $ROOTIJ.deb "micromanager-ij-$VERSION"-$CURDATE"_all.deb"
