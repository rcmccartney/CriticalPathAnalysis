const {CallsRequest, CallsReply} = require('./kvprog_pb.js');
const {KvStoreClient} = require('./kvprog_grpc_web_pb.js');

var client = new KvStoreClient('http://localhost:8080');
var request = new CallsRequest();

const enableDevTools = window.__GRPCWEB_DEVTOOLS__ || (() => {});
enableDevTools([
  client,
]);

client.calls(request, {}, (err, response) => {
    console.log(response.getCallInfoList());
});
