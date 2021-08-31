# TCS Vertical Slice Demo

This project contains demo CSW assemblies and test client code based on the TCS API
as defined the the [ICD Database](https://github.com/tmtsoftware/icd).

It makes use of the Scala based CSW framework, and also links with native C/C++ code
(using [JNR - Java Native Runtime](https://github.com/jnr/jnr-ffi/blob/master/docs/README.md)).
For example, the pk (Pointing Kernel) assembly receives a `SlewToTarget` command and then
makes a call to C/C++ code that calls [TPK](https://github.com/tmtsoftware/TPK) routines to set a target and 
then posts events using the [CSW C API](https://github.com/tmtsoftware/csw-c).

You can subscribe to those events for test purposes using the [tcs-client](tcs-client) command line subproject,
which just prints out the events received on stdout.

Also the [CSW Event Monitor](https://github.com/tmtsoftware/csw-event-monitor) uses
the [ESW](https://github.com/tmtsoftware/esw) gateway
to subscribe to events and can be used to display tables and charts based on the event values.

Note: In order to test this project with the CSW Event Monitor, you will currently need to run 
the [icdwebserver](https://github.com/tmtsoftware/icd) on localhost and import a slightly
modified version of the [TCS-Model-Files](https://github.com/tmt-icd/TCS-Model-Files) repositor
(branch: `tcs-vslice-04-test`). The branch has been modified to use the CSW altAzCoord type instead
of separate parameters for alt and az, etc.
The pk assembly provided here assumes the use of the altAzCoord type and posts events using it.

## Dependencies

Currently this project requires that a number of shared libraries are installed in a known location (default: /usr/local/lib),
which must also be included (on Linux) in the LD_LIBRARY_PATH environment variable.

Run `make; sudo make install` in the following projects (Note: In these projects the Makefile calls `cmake` to do the actual build).
First follow the instructions in the [csw-c README](https://github.com/tmtsoftware/csw-c) to install the required C libraries (libhiredis, libcbor, libuuid). You can also use `make PREFIX=/my/dir` to change the installation directory.

* [TPK (branch: add-cmake-files)](https://github.com/tmtsoftware/TPK/tree/add-cmake-files)

* [CSW C API (csw-c)](https://github.com/tmtsoftware/csw-c)

* [tpk-jni (a C/C++ based subproject of this project)](tpk-jni)

## Running the pk assembly

To run the pk assembly, run: 
    
    csw-services start  # Note: Make sure you are using the version for csw-4.0.0-M1 or greater

    sbt stage

    ./target/universal/stage/bin/tcs-deploy --local ./tcs-deploy/src/main/resources/PkContainer.conf

To send a command to the pk assembly, you can use the pk-client command line application:

```
pk-client 0.0.1
Usage: pk-client [options]

  -c, --command <command>  The command to send to the pk assembly (One of: SlewToTarget, SetOffset. Default: SlewToTarget)
  -r, --ra <RA>            The RA coordinate for the command: (default: 12:13:14.15)
  -d, --dec <Dec>          The Dec coordinate for the command: (default: -30:31:32.3)
  -f, --frame <frame>      The frame of refererence for RA, Dec: (default: FK5)
  --pmx <pmx>              The primary motion x value: (default: 0.0)
  --pmy <pmy>              The primary motion y value: (default: 0.0)
  -x, --x <x>              The x offset in arcsec: (default: 0.0)
  -y, --y <y>              The y offset in arcsec: (default: 0.0)
  -o, --obsId <id>         The observation id: (default: 2021A-001-123)
  --help
  --version
```

Example:

    ./target/universal/stage/bin/pk-client -c SlewToTarget --ra 2:11:12 --dec 78:21:22

To see the events being fired from the C/C++ code, you can run the pk-event-client, which has the
same options as pk-client, but instead of exiting, subscribes to events and displays them on stdout:

    ./target/universal/stage/bin/pk-event-client

Or, for a more user-friendly view, you can run the [CSW Event Monitor](https://github.com/tmtsoftware/csw-event-monitor)
and display the event values in tables or charts.
This requires also running the following services:

* __esw-services__ start (from [esw](https://github.com/tmtsoftware/esw))
* __icdwebserver__ (from [icd](https://github.com/tmtsoftware/icd))

