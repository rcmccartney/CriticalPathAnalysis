const {CallsRequest, CallsReply} = require('./kvprog_pb.js');
const {KvStoreClient} = require('./kvprog_grpc_web_pb.js');
import * as d3 from "./d3.min.js"

var client = new KvStoreClient('http://localhost:8080');
var request = new CallsRequest();

const enableDevTools = window.__GRPCWEB_DEVTOOLS__ || (() => {});
enableDevTools([
  client,
]);

function getCostList() {
    return new Promise(resolve => {
        client.calls(request, {}, (err, response) => {
                resolve(response.getCostListList());
            });
        });
}

const width = 900;
const height = 450;
const margin = { top: 50, bottom: 50, left: 50, right: 50 };

function display (result) {
    const costELementList = result.map(element => element.getElementList());
    console.log(costELementList);
    const costElement = [];
    costELementList[0].forEach(element => {
        costElement.push({x:element.getSource(),y:element.getCostSec()});
    });
    const svg = d3.select('#bar-graph')
        .append('svg')
        .attr('width', width - margin.left - margin.right)
        .attr('height', height - margin.top - margin.bottom)
        .attr("viewBox", [0, 0, width, height]);

    const xscale = d3.scaleBand()
        .domain(d3.range(costElement.length))
        .range([margin.left, width - margin.right])
        .padding(0.1);

    const yscale = d3.scaleLinear()
        .domain([0, 10])
        .range([height - margin.bottom, margin.top]);

    svg.append("g")
        .attr("fill", 'royalblue')
        .selectAll("rect")
        .data(costElement)
        .enter()
        .append("rect")
        .attr("x", (d, i) => xscale(i))
        .attr("y", d => yscale(d.y))
        .attr("height", (d) => {return  yscale(0)-yscale(d.y)})
        .attr("width", xscale.bandwidth());

    svg.append("g")
        .attr("transform", `translate(${margin.left}, 0)`)
        .call(d3.axisLeft(yscale).ticks(null, costElement.format))
        .attr("font-size", '20px');

    svg.append("g")
        .attr("transform", `translate(0,${height - margin.bottom})`)
        .call(d3.axisBottom(xscale).tickFormat(i => costElement[i].x))
        .attr("font-size", '20px');

    return svg.node();
}

async function f1() {
    var x = await getCostList();
    display(x);
}

f1();

