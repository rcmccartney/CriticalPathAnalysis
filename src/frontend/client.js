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

const width = 1000;
const height = 450;
const margin = { top: 50, bottom: 50, left: 50, right: 50 };

function addToDropdown (result) {
    const criticalPathElementListList = result.map(element => element.getElementList());
    const list_element = document.getElementById("list");
    for (let i=0; i< list_element.length; i++) {
        list_element.remove(i);
    }
    for (let i = 0; i < criticalPathElementListList.length; i++) {
        let option = document.createElement("option");
        option.value = i;
        option.text = "Request-"+ i;
        list_element.appendChild(option);
    }

   /* var criticalPathElementList = criticalPathElementListList[0];
    for(let i = 0; i < criticalPathElementList.length; i++) {
        let index = criticalPathElementList[i].getSource().lastIndexOf("/");
        const cost = criticalPathElementList[i].getCostSec();
        const source = criticalPathElementList[i].getSource().substring(index+1);
        costElement.push({source, cost});
    }
    console.log(costElement);
    var svg = d3.select('#bar-graph').append('svg').
                    attr('height', 450).attr('width', 2000);

    svg.selectAll('circle')
            .data(costElement)
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
    return svg.node();*/
}

async function f1() {
    let response = await getCriticalPaths();
    addToDropdown(response);
    return response;
}

async function protoPrint(response, value) {
    let criticalPathList = await response;
    document.getElementById("json").textContent = JSON.stringify(criticalPathList[value].array, undefined, 2);
}

const dropDown = document.getElementById('list');
dropDown.addEventListener('click', (event) => {
    f1();
});

dropDown.addEventListener('change', (event) => {
    let response = f1();
    protoPrint(response, event.target.value);
});