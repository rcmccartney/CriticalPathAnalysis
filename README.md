# mygrpc

Simple grpc example code to showcase gradle build chain with grpc & protos.

## Usage

To install dependencies, including gRPC:

```
$ ./gradlew installDist -PskipAndroid=true
```

Run the server then run the client:

```
$ ./build/install/examples/bin/hello-world-server
$  ./build/install/examples/bin/hello-world-client
```
