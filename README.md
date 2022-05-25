# mygrpc

Simple grpc example code to showcase gradle build chain with grpc & protos.

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
