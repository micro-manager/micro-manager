FROM ubuntu:22.04

################################################################################
# pre-setup
################################################################################
ENV TZ=Europe/London
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN apt update && apt install  -y --no-install-recommends unzip sudo awscli udev

################################################################################
#  Setup user (non-root)
################################################################################
ARG UID=9000
ARG GID=9001
RUN groupadd -g $GID engineering-team
RUN useradd -N -l -u $UID -g engineering-team -G sudo,users,dialout,video engineer
COPY ./docker/.bashrc /home/engineer/.bashrc
RUN chown -R engineer:engineering-team /home/engineer
# Allowing engineer sudo without password
RUN echo 'engineer ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
USER engineer

################################################################################
#  PVCAM installation skipped (optional proprietary camera driver)
################################################################################
# Uncomment the section below if you need PVCAM support and have credentials
# ARG PVCAM_SRC_DIR=/home/engineer/pvcam-src
# RUN mkdir $PVCAM_SRC_DIR
# WORKDIR $PVCAM_SRC_DIR
# ARG TMP_AWS=/home/engineer/.aws/
# COPY docker/credentials $TMP_AWS/credentials
# ENV AWS_CONFIG_FILE=$TMP_AWS/credentials
# RUN aws s3 cp s3://adagamma-os/files/drivers/camera/PVCAM-Linux-3-10-0-3.zip \
#         ./PVCAM-Linux-3-10-0-3.zip
# RUN sudo rm -rf $TMP_AWS
# ENV AWS_CONFIG_FILE=''
# RUN unzip ./PVCAM-Linux-3-10-0-3.zip && \
#     yes Y | bash ./pvcam/pvcam__install_helper-Ubuntu.sh
# RUN yes Y | bash ./pvcam-sdk/pvcam-sdk__install_helper-Ubuntu.sh
# ENV PVCAM_VERSION=3.10.0.3
# ENV PVCAM_UMD_PATH=/opt/pvcam/drivers/user-mode

################################################################################
#  Install micromanager dependencies
################################################################################
RUN sudo apt update 
RUN sudo apt install  -y --no-install-recommends \
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
ENV CCACHE_DIR=/home/engineer/.ccache
ENV PATH=/usr/lib/ccache:$PATH
RUN sudo mkdir -p /home/engineer/.ccache && \
    sudo chown -R engineer:engineering-team /home/engineer/.ccache
    
    ### Download imageJ ###
ARG IMAGEJ_DIR=/home/engineer/ImageJ
RUN wget https://wsr.imagej.net/distros/linux/ij154-linux64-java8.zip \
-O /tmp/ij154-linux64-java8.zip && unzip /tmp/ij154-linux64-java8.zip \
    -d /home/engineer && rm -rf $IMAGEJ_DIR/jre && \
    chmod a+Xr $IMAGEJ_DIR

### Setup Micromanager from local source ###  
ARG MM_SRC_DIR=/home/engineer/mm-src
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
COPY --chown=engineer:engineering-team \
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

COPY --chown=engineer:engineering-team ./buildscripts $MM_SRC_DIR/micro-manager/buildscripts
COPY --chown=engineer:engineering-team ./mmstudio $MM_SRC_DIR/micro-manager/mmstudio
COPY --chown=engineer:engineering-team ./acqEngine $MM_SRC_DIR/micro-manager/acqEngine
COPY --chown=engineer:engineering-team ./libraries $MM_SRC_DIR/micro-manager/libraries
COPY --chown=engineer:engineering-team ./autofocus $MM_SRC_DIR/micro-manager/autofocus
COPY --chown=engineer:engineering-team ./plugins $MM_SRC_DIR/micro-manager/plugins
COPY --chown=engineer:engineering-team ./mmAsImageJMacros $MM_SRC_DIR/micro-manager/mmAsImageJMacros
COPY --chown=engineer:engineering-team ./scripts $MM_SRC_DIR/micro-manager/scripts
COPY --chown=engineer:engineering-team ./bindist $MM_SRC_DIR/micro-manager/bindist
COPY --chown=engineer:engineering-team ./testing $MM_SRC_DIR/micro-manager/testing
COPY --chown=engineer:engineering-team ./systemtest $MM_SRC_DIR/micro-manager/systemtest

# Core C++ components (required for MMCoreJ wrapper)
COPY --chown=engineer:engineering-team ./mmCoreAndDevices/MMCore $MM_SRC_DIR/micro-manager/mmCoreAndDevices/MMCore
COPY --chown=engineer:engineering-team ./mmCoreAndDevices/MMDevice $MM_SRC_DIR/micro-manager/mmCoreAndDevices/MMDevice
COPY --chown=engineer:engineering-team ./mmCoreAndDevices/MMCoreJ_wrap $MM_SRC_DIR/micro-manager/mmCoreAndDevices/MMCoreJ_wrap
COPY --chown=engineer:engineering-team ./mmCoreAndDevices/m4 $MM_SRC_DIR/micro-manager/mmCoreAndDevices/m4

# Create a mock DeviceAdapters structure so 'configure' succeeds without the bulk of C++ code
RUN mkdir -p $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters && \
    echo "AC_PREREQ([2.69])\nAC_INIT([MM-DeviceAdapters],[2])\nAM_INIT_AUTOMAKE([foreign])\nAC_PROG_CC\nAC_PROG_CXX\nAC_CONFIG_FILES([Makefile])\nAC_OUTPUT" > $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters/configure.ac && \
    echo "SUBDIRS =" > $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters/Makefile.am && \
    mkdir -p $MM_SRC_DIR/micro-manager/mmCoreAndDevices/SecretDeviceAdapters

WORKDIR $MM_SRC_DIR/micro-manager

# Configure and build Java components
RUN ./autogen.sh && \
    ./configure --enable-imagej-plugin=$IMAGEJ_DIR
RUN make fetchdeps

# Build and install Java + Core C++ (caching happens here)
RUN --mount=type=cache,target=/home/engineer/.ccache,uid=9000,gid=9001 \
    make -j$(nproc)
RUN make install

################################################################################
# STAGE: Build C++ Device Adapters (frequently changing layer)
################################################################################
# Now copy the real C++ device adapters (this will invalidate only this and later layers)
COPY --chown=engineer:engineering-team ./mmCoreAndDevices/DeviceAdapters $MM_SRC_DIR/micro-manager/mmCoreAndDevices/DeviceAdapters

# Re-run autogen/configure to pick up real device adapters
RUN ./autogen.sh && \
    ./configure --enable-imagej-plugin=$IMAGEJ_DIR

# Rebuild everything (MMCore/Java will be up to date, mostly builds adapters)
RUN --mount=type=cache,target=/home/engineer/.ccache,uid=9000,gid=9001 \
    make -j$(nproc)
RUN make install

# Copy helper scripts
COPY ./docker/entrypoint.sh /home/engineer/entrypoint.sh
COPY ./docker/micromanager.sh /home/engineer/ImageJ/micromanager.sh
COPY ./docker/rebuild-cpp.sh /home/engineer/rebuild-cpp.sh



################################################################################
#  setup environment
################################################################################
ENV ROOT_DIR=/workdir
ENV AWS_CONFIG_FILE=/workdir/.aws/credentials
WORKDIR /home/engineer/ImageJ
ENTRYPOINT ["/home/engineer/entrypoint.sh"]

