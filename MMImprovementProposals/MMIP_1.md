# MMIP 1: Roadmap for a more performant, accessible, and flexible Micro-Manager 

| Author         | Status     | Created date | Updated date |
|--------------------------|-----------|------------|-----|
| [Henry Pinkard](https://github.com/henrypinkard)| Draft | 2023-2-11 |    |


## **Abstract**

This document describes a roadmap of proposed changes to Micro-Manager to modernize its design and create many new capabilities both users and builders of microscopes. The proposal has arisen from several years of discussions both among Micro-Manager developers and with the larger community, including [refelctions on the original design of the Micro-Manager Core](https://henrypinkard.github.io/assets/pdf/MMCoreDesign.pdf), [discussions about its current limitations and ideas for improvement
](https://github.com/micro-manager/futureMMCore), and the [2020 Janelia Software for Microscopy Workshop](https://arxiv.org/pdf/2005.00082) aimed at bringing together developers and users to plot a path forward. It includes five general areas: 

1. Performance improvements to enable handling large amounts of image data (in memory and writing to disk) 
2. An improved API with **progressive disclosure of complexity**: When high-level functionality doesn't exactly match the intended use case, direct control of hardware should be accessible while still retaining the convenience of high-level APIs.
3. Expanding the Python-accessible features of Micro-Manager
4. Development of a general purpose acquisition engine that is not specific to any one modality of microscopy
5. Expanding the API of existing devices and adding new devices to make it easier to synchronize devices via hardware triggering




## **Motivation and Scope**

In the 18 years since the initial development of Micro-Manager, the field of microscopy has undergone significant advancements and change: Orders of magnitude more data is produced; The popularity of programming languages has shifted (included those used in scientific computing); image acquisition and analysis have become more tightly coupled; and a variety of new modalities of microscopy have been developed. These changes, as well as the widespread adoption of Micro-Manager, have exposed several areas where addressing current limitations holds the promise of significantly expanding its capabilities.



### Data throughput

The rate at which scientific cameras can produce data has continued to increase and the write speed of data storage devices has also increased while their cost has fallen. This is particularly relevant to light sheet microscopy, which is at times limited by the maximum frame rate of cameras. Micro-Manager needs to continue increasing the upper limit of its performance to keep up with these data intensive modalities. 

More quantitatively, modern scientific cameras can go at 5 GB/s and Micro-Manager is likely unable to support this (though its not entirely clear where the bottleneck lies). This leads some device adapters having to implement their own file saving [^1]. Depending on the number of PCIE slots used, a single NVMe drive can write at speeds up to 3.9 GB/s [^2]. RAID0 configurations of multiple NVMe drives can increase this speed by an order or magnitude or more [^3]. The cost of storage is around $0.01 /GB and continues to fall [^4].

[^1]: https://github.com/micro-manager/mmCoreAndDevices/issues/226#issuecomment-1208390444
[^2]: https://www.bittware.com/resources/ssd-write-performance/
[^3]: https://www.bittware.com/resources/ssd-write-performance/
[^4]: https://www.backblaze.com/blog/wp-content/uploads/2022/11/3.jpg


The highest performance file I/O option in Micro-Manager is currently [NDTiff](https://github.com/micro-manager/NDTiffStorage), which can sustain write speeds of multiple GB/s for hours at a time [^5] (on an an NVME RAID configuration). [Code for benchmarking file saving performance](https://github.com/micro-manager/micro-manager/blob/main/mmstudio/src/main/java/org/micromanager/acquisition/internal/acqengjcompat/speedtest/SpeedTest.java), [a python script](https://github.com/micro-manager/pycro-manager/blob/main/scripts/speed_test.py) for running it, and a jupyter notebook for [analyzing the performance of various buffers and queues along the way](https://github.com/micro-manager/micro-manager/blob/main/acqengj_speed_test/analysis.ipynb) with NDTiff + the Java acquisition engine (used by Pycro-Manager and, currently experimentally, Micro-Manager), should give more insight into performance limits and bottlenecks as the situation is improved.

[^5]: https://forum.image.sc/t/loading-micromanager-tif-series-into-napari/62398/17


### Acquisition and a hard-coded implicit microscope

An important limitation in Micro-Manager present even in the low-level MMCore API arises the model of what components a microscope consists of imposed by its API. Since its original creation, many new types of microscope hardware have become available, and the ways people combine and use hardware has expanded. Micro-Manager was originally written to run commercial body turnkey systems from around 2005 in the context of cell biology. Though it has been able to expand and change in many ways over the years to support new applications, the are limits to have far this can go with the current architecture. A few examples of this are:

**How image data is handled**: This was originally designed for a single camera. Support for multiple cameras acquiring simultaneously was added through the introduction of the [multi-camera device adapter](https://micro-manager.org/Utilities), which treats multiple physical cameras as a single logical camera, this comes with constraints: e.g. the images produced by both cameras must have the same dimensions and bytes per pixel; and challenges related to data throughput as described above.

**Hardware triggering** support was added after the original design of MMCore, and only works in a specific situation: when the camera is the "leader" device and any other devices are "followers", and no delays are implemented between devices. The camera API does not have the necessary calls for hardware triggering, and as a result this functionality is often exposed through device-specific properties, preventing higher level code from easily utilizing it in a way that abstracts across multiple devices. 

<a id="device_roles"></a>
**Device Roles**: The Core has properties for devices with different "roles", such as "Camera", "XY stage", "Shutter", etc. This higher level code is often written based on the presence of these assumed roles (e.g. snap an image on _the_ camera). Although this can be circumvented by swapping different devices into a role sequentially, it is at best somewhat confusing and at worst difficult to hack around in certain applications. Some of these limitations arise at the level of the Core, and some arise at the level of the Acquisition Engine.

#### Current acquisition engines are special-purpose

An acquisition engine is a software module that controls and synchronizes hardware devices and acquires image data. For all but the simplest applications, this requires knowledge and synchronization of multiple devices. Which devices they are, and how they are controlled, becomes an assumption that is hard-coded into the engine. In concert with the Device Roles described above, this often results in functions like [this one](https://github.com/micro-manager/AcqEngJ/blob/c5031a5d025ed12f724772348b72d43bb5c8d352/src/main/java/org/micromanager/acqj/internal/Engine.java#L479), which specifically assume the presence of one Z-drive, one XY-stage, etc. Microscope modalities that don't conform to these hard-coded assumptions must either 1) write their own acquisition engines from scratch, or 2) inject custom code (e.g. [acquisition hooks](https://pycro-manager.readthedocs.io/en/latest/acq_hooks.html)) into the acquisition pipeline to control non-standard hardware. 

Making a general purpose acquisition engine holds the promise of realizing the best of both worlds: the flexibility to define different ways of controlling different types of microscope hardware, while not having rewrite lots of boilerplate code. Furthermore, expressing non-standard hardware setups through configuration files, rather than scripts, would make novel setups easier to share, modify, and re-use. For example, one commonly encountered limitation with Micro-Manager is that it doesn't work (well) with point-scanning systems. Adding the ability to swap camera types (2D sensor -> to point scanning) in a configuration file would help rectify this (though changes to the memory handling would also be needed).


### The shift towards Python


The popularity of different languages has always changed over time[^6], and in recent years Python has dramatically increased in popularity[^7], both as a general purpose programming language, and also withing the fields of data science[^8], scientific computing[^9], and open source software[^10]. Micro-Manager added expanded python capabilities via [PyMMCore](https://github.com/micro-manager/pymmcore) and [Pycro-Manager](https://github.com/micro-manager/pycro-manager) in recent years, and continuing to expand python capabilities will make it both more useful to users accessible to more contributors.


[^6]: [most popular programming languages 1965-2022](https://statisticsanddata.org/data/the-most-popular-programming-languages-1965-2022-new-update/)
[^7]: [Stack overflow questions](https://www.startpage.com/av/proxy-image?piurl=https%3A%2F%2Fwww.zepl.com%2Fwp-content%2Fuploads%2F2021%2F01%2FUntitled-design-3-1024x640.png&sp=1676251053Tb4cc60bf2ab3f264246b44930123507a4b28cbe9edde33ec4075e3e14992de33)
[^8]: [Trends in searches for "Python science" and "Java science"](https://trends.google.com/trends/explore?date=all&geo=US&q=python%20science,java%20science)
[^9]: [Trends in searches for "Python data" and "Java data"](https://trends.google.com/trends/explore?date=all&geo=US&q=python%20data,java%20data)
[^10]: [Code on github by language](https://www.youtube.com/watch?v=eCUy0F-oVXA)





## **Detailed description**

<!-- This section should provide a detailed description of the proposed change. It should include examples of how the new functionality would be used, intended use-cases, and pseudocode illustrating its use. -->



### 1) Performance improvements

#### New image buffer API
To improve upon the [current limitations](https://github.com/micro-manager/mmCoreAndDevices/issues/171#issuecomment-1058312997) of Micro-Manager's circular buffer and multiple-camera support, a new API for [image buffers is needed](https://github.com/micro-manager/mmCoreAndDevices/issues/168
). It (or multiple instances of it) must support multiple cameras with different sizes data types, and synchronous or asynchronous usage. It would be ideal to be able [get a hash or UUID of an image](https://github.com/micro-manager/futureMMCore/issues/29#issuecomment-847900786) after it is acquired so that it can be manipulated or passed around the software before being read out. It should be separated from the device layer by an API, so that it can support either local or remote buffers. It should work with new types of cameras, be able to allocate buffers and provide them to cameras (or other data generating devices) so they can write directly to it without forced copies (this will also require changes to the camera API). It should have controllable metadata handling to be able to tune performance or complexity. An important thing for high performance is to eliminate polling: Ideally there should be no calls to `Sleep()` in the device, Core, and application, when using the new interface. Full discussion of the development of this API can be found in [this issue](https://github.com/micro-manager/mmCoreAndDevices/issues/244).


<a id="ndbin"></a>
#### Fast streaming to disk with `NDBin` format

In addition, low-level code for streaming image data to disk at high frame rates is needed. [@edyoshikun](https://github.com/edyoshikun) has achieved this with many cameras in parallel using c++ code that writes blocks image data at a time to disk, the the block size tuned to a specific value to maximize performance. [This code can be readily adapted to provide such functionality in the c++ layer of Micro-Manager](https://github.com/micro-manager/mmCoreAndDevices/issues/323). 


In order to quickly integrate the reading and testing of this code into existing pipelines, a variant of NDTiff is proposed called **NDBin**. This would be essentially the same as NDTiff, allowing existing Python/Java reading code to take advantage of it. It would be almost the same format as NDTiff, only with contiguous blocks of image data instead of blocks of image data interspersed with ~100 byte TIFF headers.



<a id="mmkernel"></a>
### 2) Direct, low-level device access using `MMKernel`

A frequent desire of Micro-Manager users is for a more direct route to access and control individual devices (e.g. [here](https://github.com/micro-manager/futureMMCore/issues/32)). Currently, the standard way of using Micro-Manager is to have a single MMCore object (through C, Java, or Python), and to interact with all devices through it. e.g.: `core.setXYPosition(123.45)` or `core.setXYPosition("XYStage", 123.45)`. All devices share several monolithic objects: the configuration of all devices is loaded at once, they all log to the same file, etc. In addition, there are [Device Roles](#device_roles) that imply to users an implicit model of what hardware is present (these can be ignored, but the presence can nonetheless be confusing).

Having a better low-level interface to individual devices would be easier to use, and it would potentially expand the community by making the device layer more accessible to users who only want to control specific piece of hardware through a generic API, thereby increasing the incentive to create device adapters and benefiting the entire community.

The proposed solution is to create a new API called `MMKernel`. Initially, this would be its own alternative experimental API for accessing devices separate from MMCore. Longer term, it could be swapped in to sit below MMCore. The process to create it would consists of:

- Refactoring the existing MMCore code so that all access to devices routes through c++ "instance classes" like [this one](https://github.com/micro-manager/mmCoreAndDevices/blob/main/MMCore/Devices/CameraInstance.h). This process was partially (or almost entirely?) completed by [@marktsuchida](https://github.com/marktsuchida) several years ago
- Writing and API for getting pointers to individual devices, like:

  ```python
  camera = kernel.get_camera("SpecificCameraDevice")
  camera.snap_image()
  camera.get_image()
  ```
- Making wrappers in something like SWIG to expose Python and Java version of this code (similar to MMCoreJ and pymmcore).
- Reimplementing MMCoreJ and pymmcore in terms of these wrappers (maybe in c++, or maybe in each language respectively)

While doing this, there are several other changes, some of which may need to be done at the same time, some of which can be done separately, which would improve usability of the device layer:

- Resolving confusion about [properties vs functions]( https://github.com/micro-manager/futureMMCore/issues/30) and the [difficultly of dealing with the bloat of property names](https://github.com/micro-manager/futureMMCore/issues/28) by making hierarchical properties, some of which have [API names which are standardized](https://github.com/micro-manager/mmCoreAndDevices/issues/258).

- Enhancing callback mechanisms from devices by improving the [`onPropertiesChanged()`](https://github.com/micro-manager/mmCoreAndDevices/issues/31) function and adding [generic events from devices
](https://github.com/micro-manager/mmCoreAndDevices/issues/257).
      
- Clean up / remove unused functions from c++ layer [[1](https://github.com/micro-manager/mmCoreAndDevices/issues/295)], [[2](https://github.com/micro-manager/mmCoreAndDevices/issues/284)]
  
- Making more customizable [logging](https://github.com/micro-manager/futureMMCore/issues/12) and [timeouts](https://github.com/micro-manager/futureMMCore/issues/31) for individual devices.
    
- Adding support for [array-valued properties](https://github.com/micro-manager/futureMMCore/issues/15)
    
  
**TODO: Better support for threading/blocking calls**
https://github.com/micro-manager/mmCoreAndDevices/pull/296#issuecomment-1310704453

### 3) Expanding Python functionality using `AcqEngPy`

Micro-Manager currently has two official Python entry points: Pymmcore and Pycro-Manager. Pymmcore gives direct access to the Micro-Manager Core by wrapping its C API. Pycro-Manager gives access to the Core through the Java wrapper (MMCoreJ) and a ZMQ-based Java-Python translation layer. This comes with the advantage that it can make use of Java libraries like the various GUI components and NDTiffStorage library. However, it comes with the disadvantage that the ZMQ bridge has a speed limit of ~100-200 MB/s, meaning that data-intensive applications cannot stream data directly to Python without saving to disk first. This is limiting in certain situations, such as when one want to run live mode as fast as possible without saving to disk. It is thus desirable to build out a pure Python backend to pycro-manager's acquisition system.  

Much of the heavy lifting of Pycro-Manager's acquistion system is done through Java libraries: [AcqEngJ](https://github.com/micro-manager/AcqEngJ), [NDTiffStorage](https://github.com/henrypinkard/NDTiffStorage), and [NDViewer](https://github.com/micro-manager/NDViewer). Building a pure Python backend would require developing alternatives to each of these. NDViewer is the easiest to replace, since Pycro-Manager can already be used with a napari-based viewer. With the implementation of the [NDBin](#ndbin) format in the c++ layers, this format will be able to be written by either Python or Java. This leaves the need for a pure Python acquisition engine.

The easiest and most straightforward way to do this is by [making an `AcqEngPy` that is identical in its API and functionality to `AcqEngJ`](https://github.com/micro-manager/pycro-manager/issues/552). This could be created sooner by accessing devices through pymmcore, or it could wait to route throught the newer [MMKernel](#mmkernel) API. It could reuse all the same automated tests as AcqEngJ does (in the pycro-manager repository), thus ensuring it could quickly become stable and usable. And by initially developing using experimental functions outside the API it could be a very useful prototyping tool for new acquisition engine abstractions.



### 4) A general purpose acquisition engine

It is not trivial to run [many types of Microscopes](https://github.com/micro-manager/futureMMCore/issues/23) using Micro-Manager, and big part of the reason for this is the hard-coded implicit microscope model in the Core and Acquisition Engine. This can be addressed by creating an additional layer of configuration that abstracts out what is now hard-coded instructions about what hardware to control and how. This layer is (tenatively) called an [Acquisition Protocol](https://github.com/micro-manager/AcqEngJ/blob/c2ef88e98b2baf4117d3422fca3a6a37204e1c6d/src/main/java/org/micromanager/acqj/internal/acqengj/Engine.java#L410)

Adding this type of configuration layers promises to:
- Prevent users from repeatedly having to write new acquisition engines when doing different modalities of microscopy
- Make custom modalities more portable (since configuration files are better than full scripts)
- Cleanly separate out logic of how microscope is controlled from specific instructions for controlling it



### 5) New APIs and devices to support hardware triggering

Those building microscopes often report that hardware synchronization and debugging is the most difficult aspect of writing control software. In high-performance applications, dispatching instructions to hardware devices one at a time is not an option, and devices must instead be programmed to interact via "hardware triggers" -- TTL pulses that carry signals directly from one device to another. This is often done via devices like FPGAs, Arduinos, National Instruments boards, or custom programmable logic controllers. Software like [LabVIEW](https://github.com/micro-manager/futureMMCore/issues/22) or custom scripts are used to configure these devices.

Micro-Manager has some support for hardware triggering, but much of it is through device-specific properties, thus preventing general purpose code like an acquisition engine from automating it. Expanding Micro-Manager's capabilities in this regard wil require several related changes in the device layer:

- Building out a new and better camera API with standardized methods/properties for triggering support. There has already been much [discussion about how to do this](https://github.com/micro-manager/mmCoreAndDevices/issues/243) and the [new_camera_api](https://github.com/micro-manager/mmCoreAndDevices/tree/new_camera_api) branch holds a work in progress prototype. It's completion woudl benefit from implementation of [properties with API names which are standardized](https://github.com/micro-manager/mmCoreAndDevices/issues/258).

In addition, the introduction of several new types of devices would expand Micro-Manager's capabilities in this regard. Specifically:

- [A clock type device](https://github.com/micro-manager/mmCoreAndDevices/issues/195)
- [Signal IO device](https://github.com/micro-manager/mmCoreAndDevices/issues/141)
- [Adding ability to acquire arbitrary data](https://github.com/micro-manager/mmCoreAndDevices/pull/313)

Completion of this work in the device layer will then open the possibility of far more powerful (and possibly complex) acquisition engine features that assist in automating hardware triggering.





## **Implementation**


<!-- This section lists the major steps required to implement the MMIP. Where possible, it should be noted where one step is dependent on another, and which steps may be optionally omitted. Where it makes sense, each step should include a link related pull requests as the implementation progresses. -->

#### General principles for implementation of this roadmap:

- Start at the lowest levels of the software and build upwards
- Prioritize features that can be merged sooner rather than later and yield short term benefits
- Merge in features through APIs marked as "experimental" early so long as they don't disrupt existing functionality
- One feature per a branch; if multiple features depend on one another, make branches of a branch


#### Subprojects

- [ ] [New image buffer](https://github.com/micro-manager/mmCoreAndDevices/issues/244)
- [ ] [Fast c++ layer saving with NDBin file format](https://github.com/micro-manager/mmCoreAndDevices/issues/323)

- [ ] MMKernel for direct device access (TODO make issue)
- [ ] [Python acquisition engine (AcqEngPy)](https://github.com/micro-manager/pycro-manager/issues/552)
- [ ] [Acquisition protocols](https://github.com/micro-manager/micro-manager/issues/1524)
- [ ] Expand Device APIs
- [ ] [Camera API](https://github.com/micro-manager/mmCoreAndDevices/issues/243)
- [ ] Signalling API





## **Backward compatibility**

<!-- This section describes the ways in which the MMIP breaks backward compatibility. -->


## **Discussion**

<!-- This section may just be a bullet list including links to any discussions regarding the MMIP, but could also contain additional comments about that discussion: -->


<!-- * This includes links to discussion forum threads or relevant GitHub discussions. -->




## **References and Footnotes**





