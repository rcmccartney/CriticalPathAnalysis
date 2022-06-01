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

const width = 1000;
const height = 450;
const margin = { top: 50, bottom: 50, left: 50, right: 50 };

function display (result) {
    const costELementList = result.map(element => element.getElementList());
    console.log(costELementList);
    const costElement = [];

    for (let i = 0; i < costELementList.length; i++) {
        var option = document.createElement("option");
        option.value = i;
        option.text = "Request-"+ i;
        document.getElementById("list").appendChild(option);
    }

    var firstElement = costELementList[0];
    for(let i = 0; i < firstElement.length; i++) {
        let index = firstElement[i].getSource().lastIndexOf("/");
        const cost = firstElement[i].getCostSec();
        const source = firstElement[i].getSource().substring(index+1);
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
    for(let i = 0; i < firstElement.length-1; i++) {
        var x2 = x+40;
         var line =    d3.select("svg")
                .append("line")
                .attr("x1", x)
                .attr("y1", 300)
                .attr("x2", x2)
                .attr("y2", 300)
                .attr("stroke", "red")
                .attr("text", "eg");

        line.append('text')
            .attr('class', 'barsEndlineText')
            .attr('text-anchor', 'middle')
            .attr("x", 0)
            .attr("y", ".35em")
            .text('eg');
        x = x2 + 40;
    }

    return svg.node();
}

async function f1() {
    var x = await getCostList();
    display(x);
}

f1();

