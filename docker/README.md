# Micro-Manager Docker Development Environment

This Docker setup is optimized for developing features in mmCoreAndDevices with fast iteration cycles.

## Building the Image

Build the Docker image from the project root:

```bash
docker build -t micro-manager-dev -f docker/Dockerfile .
```

**Note:** The initial build includes both Java and C++ components and may take considerable time.

## Running the Container

**To run Micro-Manager:**
```bash
docker run -it micro-manager-dev
```

**To rebuild C++ components:**
```bash
docker run -it \
  -v $(pwd)/mmCoreAndDevices:/root/mm-src/micro-manager/mmCoreAndDevices \
  micro-manager-dev \
  bash -c "cd /root/mm-src/micro-manager && make -j$(nproc) && make install"
```

**To run a custom command:**
```bash
docker run -it micro-manager-dev bash
```

## Development Workflow for mmCoreAndDevices

### Interactive development

For an interactive shell to work with the code:

```bash
docker run -it \
  -v $(pwd)/mmCoreAndDevices:/root/mm-src/micro-manager/mmCoreAndDevices \
  micro-manager-dev \
  bash
```

Then inside the container:
- Manual build commands as needed

### Manual rebuild

Inside the container:

```bash
cd /root/mm-src/micro-manager
make clean
./configure --without-java --enable-imagej-plugin=/root/ImageJ
make -j8
make install
```

## Build Optimization Strategy

The Dockerfile uses layer caching to optimize rebuilds:

1. **System dependencies** (rarely changes)
2. **ImageJ download** (rarely changes)
3. **Source copy** (changes with code)
4. **Full build with Java** (initial build only)

When you rebuild the image after source changes, Docker will use cached layers for steps 1-2, only rebuilding from step 3 onwards.

## Directory Structure

- `/root/mm-src/micro-manager` - Source code
- `/home/engineer/ImageJ` - ImageJ installation with Micro-Manager plugin
- `/home/engineer/rebuild-cpp.sh` - Quick rebuild script for C++ only
- `/home/engineer/entrypoint.sh` - Flexible entrypoint script

## Tips

- Mount your local mmCoreAndDevices as a volume for live development
- Use `rebuild-cpp` argument for fast C++ iterations
- Run without arguments to start Micro-Manager
- The Java components are built once during image creation
- Only rebuild the full image when dependencies or system setup changes
