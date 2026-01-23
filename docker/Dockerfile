################################################################################
# STAGE: Builder
################################################################################
FROM ubuntu:22.04 AS builder

################################################################################
#  Install micromanager dependencies
################################################################################
RUN apt update && apt install -y --no-install-recommends \
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
    ccache \
    unzip \
    sudo \
    udev && \
    rm -rf /var/lib/apt/lists/*

# Configure ccache for faster rebuilds
ENV CCACHE_DIR=/root/.ccache
ENV PATH=/usr/lib/ccache:$PATH
RUN mkdir -p /root/.ccache
    
    ### Download imageJ ###
ARG IMAGEJ_DIR=/opt/ImageJ
RUN wget https://wsr.imagej.net/distros/linux/ij154-linux64-java8.zip \
-O /tmp/ij154-linux64-java8.zip && unzip /tmp/ij154-linux64-java8.zip \
    -d /opt && rm -rf $IMAGEJ_DIR/jre && \
    chmod a+Xr $IMAGEJ_DIR

### Setup Micromanager from local source ###  
ARG MM_SRC_DIR=/root/mm-src
RUN mkdir -p $MM_SRC_DIR && \
    mkdir -p $MM_SRC_DIR/3rdpartypublic/classext && \
    wget https://svn.micro-manager.org/3rdpartypublic/classext/iconloader.jar -P $MM_SRC_DIR/3rdpartypublic/classext/

# Set SWIG path
ENV SWIG=/usr/bin/swig3.0

################################################################################
# Build everything
################################################################################
WORKDIR $MM_SRC_DIR/micro-manager

### Pre-fetch Java dependencies ###
# We copy only the files needed for dependency resolution to cache this slow step
COPY buildscripts/ivy.xml buildscripts/ivysettings.xml buildscripts/fetchdeps.xml buildscripts/
COPY 3rdpartypublic-revision 3rdparty-revision ./
RUN ant -f buildscripts/fetchdeps.xml resolve

COPY . .

# Optional hook for proprietary drivers or extra setup before build
# To use: create a script at docker/setup-drivers.sh in your local machine
RUN if [ -f ./docker/setup-drivers.sh ]; then \
        chmod +x ./docker/setup-drivers.sh && \
        ./docker/setup-drivers.sh; \
    fi

# Generate build files, configure, and fetch dependencies
RUN ./autogen.sh && \
    ./configure --enable-imagej-plugin=$IMAGEJ_DIR && \
    make fetchdeps

# Build and install (using ccache for speed)
RUN --mount=type=cache,target=/root/.ccache \
    make -j$(nproc) && make install

# Copy helper scripts
COPY ./docker/micromanager.sh $IMAGEJ_DIR/micromanager.sh

################################################################################
# STAGE: Final Image
################################################################################
FROM ubuntu:22.04

# Install only runtime dependencies
RUN apt update && apt install -y --no-install-recommends \
    openjdk-21-jdk \
    libjogl2-java \
    libboost-system-dev \
    libboost-thread-dev \
    libboost-filesystem-dev \
    libboost-iostreams-dev \
    libboost-chrono-dev \
    libboost-date-time-dev \
    libboost-atomic-dev \
    libltdl7 \
    zlib1g \
    libtiff5 \
    libpng16-16 \
    x11-apps \
    && rm -rf /var/lib/apt/lists/*

# Copy the built ImageJ with Micro-Manager from the builder stage
COPY --from=builder /opt/ImageJ /opt/ImageJ

################################################################################
#  setup environment
################################################################################
WORKDIR /workdir
ENTRYPOINT [ "/opt/ImageJ/micromanager.sh" ]

