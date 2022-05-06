# mygrpc

Simple grpc example code to showcase gradle build chain with grpc & protos.

## Usage

You must already have the JDK installed to run.  To install all other dependencies, including gRPC:

```
$ ./gradlew installDist -PskipAndroid=true
```

Run the server:

```
$ ./build/install/mygrpc/bin/kv-prog-server
```

Then run the client:

```
$ ./build/install/mygrpc/bin/kv-prog-client
```
