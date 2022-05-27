const {CallsRequest, CallsReply} = require('./kvprog_pb.js');
const {KvStoreClient} = require('./kvprog_grpc_web_pb.js');
import * as d3 from "./d3.min.js"

var client = new KvStoreClient('http://localhost:8080');
var request = new CallsRequest();

const enableDevTools = window.__GRPCWEB_DEVTOOLS__ || (() => {});
enableDevTools([
  client,
]);

var myData;
client.calls(request, {}, (err, response) => {
    myData = response.getCostListList();
});

console.log(myData);
let data = [31, 42, 56, 92, 84, 72, 53, 43, 29, 24, 64, 49];

const barWidth = 75;
const barOffset = 10;
const height = 600;
const width = 800;

let yScale = d3.scaleLinear()
    .domain([0, d3.max(data)])
    .range([0, height]);

d3.select('#bar-graph')
    .append('svg')
    .attr('width', width)
    .attr('height', height)
    .style('background', '#3A742C')
    .selectAll('rect')
    .data(data)
    .enter()
    .append('rect')
    .style('fill', '#30A08B')
    .attr('width', barWidth)
    .attr('height', (d) => {
        return yScale(d);
    })
    .attr('x', (d, i) => {
        return i * (barWidth + barOffset);
    })
    .attr('y', (d) => {
        return height - yScale(d);
    })
;
