const {CallsRequest} = require('./kvprog_pb.js');
const {KvStoreClient} = require('./kvprog_grpc_web_pb.js');
import * as d3 from "./d3.v4.js";

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

let cp_data = getCriticalPaths()

async function addToDropdown () {
    let data_present = await cp_data;
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
    let data_present = await cp_data;
    const criticalPathElementListList = data_present.map(element => element.getElementList());
    const criticalPathElementList = criticalPathElementListList[path_number];

    let nodes = [];
    let links = [];
    for (let i = 0; i < criticalPathElementList.length; i++) {
        const str = slice(slice(slice(criticalPathElementList[i].getSource(), "/"), "."), "_");
        const cost = criticalPathElementList[i].getCostSec();
        let node = {"id" : i+1, "name" : str + ": " + cost }
        nodes.push(node);
        if (i+1 != criticalPathElementList.length) {
            let link = {"source": i + 1, "target": i + 2}
            links.push(link);
        }
    }

    let data = { "nodes": nodes, "links": links };

    // set the dimensions and margins of the graph
    var margin = {top: 10, right: 30, bottom: 30, left: 40},
    width = 2000 - margin.left - margin.right,
    height = 1000 - margin.top - margin.bottom;

    d3.selectAll('svg').remove();

    // append the svg object to the body of the page
    var svg = d3.select("#my_dataviz")
        .append("svg")
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.top + margin.bottom)
        .append("g")
        .attr("transform",
        "translate(" + margin.left + "," + margin.top + ")");

    // Initialize the links
    var link = svg
        .selectAll("line")
        .data(data.links)
        .enter()
        .append("line")
        .style("stroke", "#aaa");

    // Initialize the nodes
    var node = svg
        .selectAll("circle")
        .data(data.nodes)
        .enter()
        .append("circle")
        .call(d3.drag()
            .on("start", dragstarted)
            .on("drag", dragged)
            .on("end", dragended))
        .attr("r", 20)
        .style("fill", "#69b3a2");

    var text = svg.selectAll("text")
        .data(data.nodes)
        .enter()
        .append("text");

    var textLabels = text
        .text(function (d) {
            return d.name
        })
        .attr("font-family", "sans-serif")
        .attr("font-size", "10px")
        .attr("fill", "red");

    var simulation = d3.forceSimulation(data.nodes)
        .force("link", d3.forceLink()
            .id(function(d) { return d.id; })
            .links(data.links))
        .force("charge", d3.forceManyBody().strength(-400))
        .force("center", d3.forceCenter(width / 2, height / 2))
        .on("tick", ticked);

    // This function is run at each iteration of the force algorithm, updating the nodes position.
    function ticked() {
        link
            .attr("x1", function(d) { return d.source.x; })
            .attr("y1", function(d) { return d.source.y; })
            .attr("x2", function(d) { return d.target.x; })
            .attr("y2", function(d) { return d.target.y; });

        node
            .attr("cx", function (d) { return d.x+6; })
            .attr("cy", function(d) { return d.y-6; });

        textLabels
            .attr("x", function (d) { return d.x; })
            .attr("y", function (d) { return d.y; });
    }

    function dragstarted(d) {
        if (!d3.event.active) simulation.alphaTarget(0.3).restart();
        d.fx = d.x;
        d.fy = d.y;
    }

    function dragged(d) {
        d.fx = d3.event.x;
        d.fy = d3.event.y;
    }

    function dragended(d) {
        if (!d3.event.active) simulation.alphaTarget(0);
        d.fx = null;
        d.fy = null;
    }
}

async function protoPrint(value) {
    let data_present = await cp_data;
    document.getElementById("json").textContent = JSON.stringify(data_present[value].array, undefined, 2);
}

const dropDown = document.getElementById('list');
dropDown.addEventListener('change', (event) => {
    protoPrint(event.target.value);
    graph(event.target.value)
});

addToDropdown();
