# Implementing Critical Path Tracing: A Practitioner’s Approach

Our project implements a distributed profiler that can be used to trace critical path in distributed applications using the [Dagger Producers](https://dagger.dev/dev-guide/producers.html) framework. The project consists of the following major components -

### 1. Top level server
 Top lever server consists of two subcomponents - put and get. Put and get subcomponents conditionally call other service subcomponents.
### 2. Server B and Server C
Server B and Server C both have two subcomponents - b1, b2 and c1, c2 respectively. Different latency for each subcomponent is simulated by sleeping the corresponding thread. Further, each subcomponent may conditionally call other subcomponents depending on the call type.
### 4. Common
This module implements RPC interceptor to collect latency information and also computes critical path.
### 5. Client / Load Generator
The client or the load generator is used to make calls to the top level service. Further, it also collects the RPC data corresponding to different subcomponent which consists of the call graph and the latencies corresponding to each subcomponent call.
### 6. Frontend
Frontend uses D3 data visualization library to display CPT information of each request.

## Usage

### Command-line usage

You must already have the JDK installed to run. To install all other dependencies, including gRPC:

```
$ ./gradlew installDist -PskipAndroid=true
```

Run the server:

```
$ ./build/install/mygrpc/bin/top-level-server
```

Then run the client:

```
$ ./build/install/mygrpc/bin/client
```

To run all tests:

```
$ ./gradlew test
```

Various command line flags change the behavior of server and client. Pass
`-h` to the above Run commands to see the available options.

### UI usage

To run the UI requires [grpc-web](https://github.com/grpc/grpc-web/tree/master/net/grpc/gateway/examples/helloworld),
so there are significantly more dependencies:

* [protoc](https://github.com/protocolbuffers/protobuf/releases)
* [protoc-gen-grpc-web](https://github.com/grpc/grpc-web/releases)
* [NodeJS](https://nodejs.org/en/)
* [docker](https://www.docker.com/)
* [python3](https://www.python.org/downloads/)
* On Mac: [Command Line Tools package](https://apple.stackexchange.com/questions/254380/why-am-i-getting-an-invalid-active-developer-path-when-attempting-to-use-git-a)
* Optional: [gRPC-Web Dev Tools](https://github.com/SafetyCulture/grpc-web-devtools)
* Optional: [Scabbard](https://arunkumar9t2.github.io/scabbard/) has a dependency on graphviz
if you want to generate `png`'s of the component graphs.

Once all dependencies are installed, use the following script:

```
$ ./run
```


## References

* [Distributed Latency Profiling through Critical Path Tracing](https://queue.acm.org/detail.cfm?id=3526967#:~:text=Critical%20path%20tracing%20(CPT)%20is,day%20data%20for%20latency%20analysis.)

* [Dagger](https://dagger.dev/dev-guide/)

* [Protocol Buffers](https://developers.google.com/protocol-buffers)

* [gRPC](https://github.com/grpc/grpc-java)

* [D3.js](https://d3js.org/)
