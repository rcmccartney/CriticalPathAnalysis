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
   // console.log(result);
    const costELementList = result.map(element => element.getElementList());
    console.log(costELementList);
    console.log("print each cost element");
    const xy = [];
    costELementList[0].forEach(element => {
        xy.push({x:element.getSource(),y:element.getCostSec()});
    });
    console.log(xy);
    const svg = d3.select('#bar-graph')
        .append('svg')
        .attr('width', width - margin.left - margin.right)
        .attr('height', height - margin.top - margin.bottom)
        .attr("viewBox", [0, 0, width, height]);

    const x = d3.scaleBand()
        .domain(d3.range(xy.length))
        .range([margin.left, width - margin.right])
        .padding(0.1)

    const y = d3.scaleLinear()
        .domain([0, 10])
        .range([height - margin.bottom, margin.top])

    svg.append("g")
        .attr("fill", 'royalblue')
        .selectAll("rect")
        .data(xy.sort((a, b) => d3.descending(a.y, b.y)))
        .join("rect")
        .attr("x", (d, i) => x(i))
        .attr("y", d => y(xy.y))
        .attr('title', (d) => xy.y)
        .attr("class", "rect")
        .attr("height", (d) => {return  y(d)})
        .attr("width", x.bandwidth());

    svg.append("g")
        .attr("transform", `translate(${margin.left}, 0)`)
        .call(d3.axisLeft(y).ticks(null, xy.format))
        .attr("font-size", '20px');

    svg.append("g")
        .attr("transform", `translate(0,${height - margin.bottom})`)
        .call(d3.axisBottom(x).tickFormat(i => xy[i].x))
        .attr("font-size", '20px');

    return svg.node();
}

async function f1() {
    var x = await getCostList();
    display(x);
}

f1();

