FROM ubuntu:22.04

################################################################################
# pre-setup
################################################################################
ENV TZ=Europe/London
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN apt update && apt install  -y --no-install-recommends unzip udev

################################################################################
#  Install micromanager dependencies
################################################################################
RUN apt update 
RUN apt install  -y --no-install-recommends \
    build-essential \
    autoconf \
    automake \
    libtool \
    autoconf-archive \
    pkg-config \
    libboost-all-dev \
    zlib1g-dev \
    ant \
    libjogl2-java \
    wget \
    git \
    openjdk-21-jdk \
    swig3.0 \
    ccache

# Configure ccache for faster rebuilds
ENV CCACHE_DIR=/root/.ccache
ENV PATH=/usr/lib/ccache:$PATH
RUN mkdir -p /root/.ccache
    
    ### Download imageJ ###
ARG IMAGEJ_DIR=/root/ImageJ
RUN wget https://wsr.imagej.net/distros/linux/ij154-linux64-java8.zip \
-O /tmp/ij154-linux64-java8.zip && unzip /tmp/ij154-linux64-java8.zip \
    -d /root && rm -rf $IMAGEJ_DIR/jre && \
    chmod a+Xr $IMAGEJ_DIR

### Setup Micromanager from local source ###  
ARG MM_SRC_DIR=/root/mm-src
RUN mkdir -p $MM_SRC_DIR && \
    mkdir -p $MM_SRC_DIR/3rdpartypublic/classext && \
    wget --no-check-certificate https://svn.micro-manager.org/3rdpartypublic/classext/iconloader.jar -P $MM_SRC_DIR/3rdpartypublic/classext/

# Set SWIG path
ENV SWIG=/usr/bin/swig3.0

################################################################################
# STAGE: Build Java components (cached layer)
################################################################################
# Copy only files needed for Java build configuration 
# (Adding missing metadata files and entire directories required by configure)
COPY \
    ./autogen.sh \
    ./configure.ac \
    ./Makefile.am \
    ./version.txt \
    ./aclocal.m4 \
    ./config.* \
    ./ltmain.sh \
    ./compile \
    ./depcomp \
    ./install-sh \
    ./missing \
    ./test-driver \
    ./3rdparty-revision \
    ./3rdpartypublic-revision \
    $MM_SRC_DIR/micro-manager/

COPY ./buildscripts $MM_SRC_DIR/micro-manager/buildscripts
COPY ./mmstudio $MM_SRC_DIR/micro-manager/mmstudio
COPY ./acqEngine $MM_SRC_DIR/micro-manager/acqEngine
COPY ./libraries $MM_SRC_DIR/micro-manager/libraries
COPY ./autofocus $MM_SRC_DIR/micro-manager/autofocus
COPY ./plugins $MM_SRC_DIR/micro-manager/plugins
COPY ./mmAsImageJMacros $MM_SRC_DIR/micro-manager/mmAsImageJMacros
COPY ./scripts $MM_SRC_DIR/micro-manager/scripts
COPY ./bindist $MM_SRC_DIR/micro-manager/bindist
COPY ./testing $MM_SRC_DIR/micro-manager/testing
COPY ./systemtest $MM_SRC_DIR/micro-manager/systemtest

# Core C++ components (required for MMCoreJ wrapper)
COPY ./mmCoreAndDevices/MMCore $MM_SRC_DIR/micro-manager/mmCoreAndDevices/MMCore
COPY ./mmCoreAndDevices/MMDevice $MM_SRC_DIR/micro-manager/mmCoreAndDevices/MMDevice
COPY ./mmCoreAndDevices/MMCoreJ_wrap $MM_SRC_DIR/micro-manager/mmCoreAndDevices/MMCoreJ_wrap
COPY ./mmCoreAndDevices/m4 $MM_SRC_DIR/micro-manager/mmCoreAndDevices/m4

# Create a mock DeviceAdapters structure so 'configure' succeeds without the bulk of C++ code
RUN mkdir -p $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters && \
    printf "AC_PREREQ([2.69])\nAC_INIT([MM-DeviceAdapters],[2])\nAM_INIT_AUTOMAKE([foreign])\nAC_PROG_CC\nAC_PROG_CXX\nAC_CONFIG_FILES([Makefile])\nAC_OUTPUT\n" > $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters/configure.ac && \
    echo "SUBDIRS =" > $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters/Makefile.am && \
    mkdir -p $MM_SRC_DIR/micro-manager/mmCoreAndDevices/SecretDeviceAdapters

WORKDIR $MM_SRC_DIR/micro-manager

# Configure and build everything sequentially
RUN ./autogen.sh && \
    ./configure --enable-imagej-plugin=$IMAGEJ_DIR
RUN make fetchdeps

# Build and install everything in a single step to ensure correct dependency ordering
RUN --mount=type=cache,target=/root/.ccache \
    make && make install

################################################################################
# STAGE: Build C++ Device Adapters (frequently changing layer)
################################################################################
# Now copy the real C++ device adapters (this will invalidate only this and later layers)
COPY ./mmCoreAndDevices/DeviceAdapters $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters

# Re-run autogen/configure to pick up real device adapters
RUN ./autogen.sh && \
    ./configure --enable-imagej-plugin=$IMAGEJ_DIR

# Rebuild everything (MMCore/Java will be up to date, mostly builds adapters)
RUN --mount=type=cache,target=/root/.ccache \
    make -j$(nproc)
RUN make install

# Copy helper scripts
COPY ./docker/micromanager.sh /root/ImageJ/micromanager.sh



################################################################################
#  setup environment
################################################################################
ENV ROOT_DIR=/workdir
WORKDIR /root/ImageJ
ENTRYPOINT [ "/root/ImageJ/micromanager.sh" ]

