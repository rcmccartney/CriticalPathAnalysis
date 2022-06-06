const {CallsRequest} = require('./kvprog_pb.js');
const {KvStoreClient} = require('./kvprog_grpc_web_pb.js');
import * as d3 from "./d3.min.js"

let client = new KvStoreClient('http://localhost:8080');
let request = new CallsRequest();

const enableDevTools = window.__GRPCWEB_DEVTOOLS__ || (() => {});
enableDevTools([
  client,
]);

async function getCriticalPaths() {
    return new Promise(resolve => {
        client.calls(request, {}, (err, response) => {
                resolve(response.getCriticalPathList());
            });
        });
}

let data = getCriticalPaths()

async function addToDropdown () {
    let data_present = await data;
    const list_element = document.getElementById("list");
    for (let i = 0; i < data_present.length; i++) {
        let option = document.createElement("option");
        option.value = i;
        option.text = "Request-" + i;
        list_element.appendChild(option);
    }
}

function slice (str, char) {
    return str.substring(str.lastIndexOf(char)+1);
}

async function graph (path_number) {
    let data_present = await data;
    const criticalPathElementListList = data_present.map(element => element.getElementList());
    const criticalPathElementList = criticalPathElementListList[path_number];
    let pathElementNodes = [];
    for(let i = 0; i < criticalPathElementList.length; i++) {
        const str = slice(slice(slice(criticalPathElementList[i].getSource(), "/"), "."), "_");
        const cost = criticalPathElementList[i].getCostSec();
        pathElementNodes.push(cost);
    }
    console.log(pathElementNodes);
    d3.select('#graph').select("*").remove();
    var svg = d3.select('#graph')
        .append('svg')
        .attr('height', 450)
        .attr('width', 2000);

    svg.selectAll('circle')
        .data(pathElementNodes)
        .enter().append('circle')
        .attr('cx', function(d, i) {return 200 + (i * 80)})
        .attr('cy','300')
        .attr('r', 20)
        .style('fill', 'green');

    var x =  220;
    for (let i = 0; i < criticalPathElementList.length-1; i++) {
         var x2 = x+40;
         var line =  d3.select("svg")
                .append("line")
                .attr("x1", x)
                .attr("y1", 300)
                .attr("x2", x2)
                .attr("y2", 300)
                .attr("stroke", "red")

        line.append('text')
            .attr('class', 'barsEndlineText')
            .attr('text-anchor', 'middle')
            .text('eg');
        x = x2 + 40;
    }
}

async function protoPrint(value) {
    let data_present = await data;
    document.getElementById("json").textContent = JSON.stringify(data_present[value].array, undefined, 2);
}

const dropDown = document.getElementById('list');
dropDown.addEventListener('change', (event) => {
    protoPrint(event.target.value);
    graph(event.target.value)
});

addToDropdown();
