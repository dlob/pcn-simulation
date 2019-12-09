<template>
  <svg @click="deselectEverything" v-bind:id="id" v-bind:width="width" v-bind:height="height"></svg>
</template>

<script>
import * as d3 from "d3";

const normal = "white";
const emphasis = "#FFB400";
const deactivated = "#403F4C";
const highlight = "#4392F1";

export default {
  name: "graph",
  props: ["width", "height", "id", "network", "static"],
  data() {
    return {
      simulation: null,
      link: null,
      node: null,
      selectedNode: null,
      newNode: null,
      showFlow: false,
      tick: 0
    };
  },
  watch: {
    network: function() {
      this.update();
    }
  },
  mounted() {
    this.init();
  },
  methods: {
    init() {
      var svg = d3.select("#" + this.id);
      var zoomArea = svg.append("g"),
        zoom = d3
          .zoom()
          .on("zoom", () => zoomArea.attr("transform", d3.event.transform));
      if (this.static === "false") {
        zoom(svg);
      }
      this.simulation = d3
        .forceSimulation()
        .force("link", d3.forceLink())
        .force("charge", d3.forceManyBody())
        .force("center", d3.forceCenter(this.width / 2, this.height / 2));
      this.link = zoomArea.append("g").selectAll("line");
      this.node = zoomArea.append("g").selectAll("circle");
      if (this.network !== null) {
        this.update();
      }
    },
    update() {
      this.node = this.node.data(this.network.nodes);
      this.node.exit().remove();
      this.node = this.node
        .enter()
        .append("circle")
        .attr("id", d => d.wallet)
        .attr("r", d => Math.max(Math.cbrt(d.balance) * 0.7, 1))
        .attr("fill", d => normal)
        .attr("class", "node")
        .merge(this.node);

      if (this.static === "false") {
        this.node = this.node.on("click", this.selectNode).call(
          d3
            .drag()
            .on("start", this.dragstarted)
            .on("drag", this.dragged)
            .on("end", this.dragended)
        );
      }

      this.link = this.link.data(this.network.links);
      this.link.exit().remove();
      this.link = this.link
        .enter()
        .append("line")
        .style(
          "stroke-width",
          d => Math.sqrt(d.sourceBalance + d.targetBalance) * 0.2
        )
        .style("stroke", d => normal)
        .on("click", this.selectLink)
        .merge(this.link);

      this.simulation.nodes(this.network.nodes).on("tick", this.ticked);
      d3.timer(this.timer, 50);
      this.simulation.force(
        "link",
        d3
          .forceLink(this.network.links)
          .id(d => d.wallet)
          .iterations(10)
      );
      this.simulation.alphaTarget(0.1).restart();
      if (this.static === "true") {
        this.simulation.stop();
        this.ticked();
      }
    },
    addNode(node) {
      this.network.nodes.push(node);
      this.update();
      return node;
    },
    addLink(link) {
      this.network.links.push(link);
      this.update();
      return link;
    },
    removeNode(node) {
      var index = this.network.nodes.indexOf(node);
      this.network.nodes.splice(index, 1);
      var links = this.getNeighborLinks(node);
      for (var link of links) {
        var index = this.network.links.indexOf(link);
        this.network.links.splice(index, 1);
      }
      this.update();
      this.colorNodes([], normal);
      this.colorLinks([], normal);
    },
    removeLink(link) {
      var index = this.network.links.indexOf(link);
      this.network.links.splice(index, 1);
      this.update();
      this.colorNodes([], normal);
      this.colorLinks([], normal);
    },
    selectNode(selectedNode) {
      d3.event.stopPropagation();
      this.$emit("nodeSelected", selectedNode);
      this.colorNode(selectedNode, highlight);
      this.colorLinks(this.getNeighborLinks(selectedNode), highlight);
    },
    selectLink(selectedLink) {
      d3.event.stopPropagation();
      this.$emit("linkSelected", selectedLink);
      this.colorNodes([selectedLink.source, selectedLink.target], highlight);
      this.colorLinks([selectedLink], highlight);
    },
    deselectEverything() {
      this.colorNodes(this.network.nodes, normal);
      this.colorLinks(this.network.links, normal);
    },
    showReach(selectedNode) {
      var peers = this.network.nodes.filter(n =>
        selectedNode.knownPeers.includes(n.wallet)
      );
      peers.push(selectedNode);
      this.colorNodes(peers, highlight);
      var links = this.getConnectedLinks(peers);
      this.colorLinks(links, highlight);
    },
    deactivateNode(node) {
      if (!node.deactivated || node.deactivated === undefined) {
        node.deactivated = true;
      } else {
        node.deactivated = false;
      }
      var links = this.getNeighborLinks(node);
      this.colorNode(node, normal);
      this.colorLinks(links, normal);
    },
    highlightPath(nodes) {
      var self = this;
      var links = this.getPathLinks(nodes);
      this.colorNodes(nodes, emphasis);
      this.colorLinks(links, emphasis);
    },
    resetHighlight() {
      this.colorNodes([], normal);
      this.colorLinks([], normal);
    },
    getNeighborLinks(selectedNode) {
      return this.network.links.filter(l =>
        this.isNeighborLink(selectedNode, l)
      );
    },
    getPathLinks(selectedNodes) {
      return this.network.links.filter(l => this.isPathLink(selectedNodes, l));
    },
    getConnectedLinks(selectedNodes) {
      return this.network.links.filter(l =>
        this.isConnectedLink(selectedNodes, l)
      );
    },
    isNeighborLink(node, link) {
      return (
        link.target.wallet === node.wallet || link.source.wallet === node.wallet
      );
    },
    isPathLink(path, link) {
      var onPath = false;
      for (var i = 0; i < path.length - 1; i++) {
        var source = path[i].wallet;
        var target = path[i + 1].wallet;
        onPath |=
          (link.source.wallet === source && link.target.wallet === target) ||
          (link.source.wallet === target && link.target.wallet === source);
      }
      return onPath;
    },
    isConnectedLink(nodes, link) {
      var isConnected = false;
      nodes.forEach(n1 => {
        nodes.forEach(n2 => {
          isConnected |=
            (link.source.wallet === n1.wallet &&
              link.target.wallet === n2.wallet) ||
            (link.source.wallet === n2.wallet &&
              link.target.wallet === n1.wallet);
        });
      });
      return isConnected;
    },
    colorLinks(links, color) {
      this.link.style("stroke", link => {
        if (link.source.deactivated || link.target.deactivated) {
          return deactivated;
        } else if (links.indexOf(link) > -1) {
          return color;
        } else {
          return normal;
        }
      });
    },
    colorNode(node, color) {
      this.colorNodes([node], color);
    },
    colorNodes(nodes, color) {
      this.node.attr("fill", node => {
        if (node.deactivated) {
          return deactivated;
        } else if (nodes.indexOf(node) > -1) {
          return color;
        } else {
          return normal;
        }
      });
    },
    ticked() {
      this.link
        .attr("x1", d => d.source.x)
        .attr("y1", d => d.source.y)
        .attr("x2", d => d.target.x)
        .attr("y2", d => d.target.y)
        .style(
          "stroke-width",
          d => Math.sqrt(d.sourceBalance + d.targetBalance) * 0.2
        );
      this.node
        .attr("cx", d => d.x)
        .attr("cy", d => d.y)
        .attr("r", d => Math.max(Math.cbrt(d.balance) * 0.7, 1));
    },
    timer() {
      if (this.showFlow && this.tick < 60000) {
        this.tick++;
      } else {
        this.tick = 0;
      }
      this.link
        .style("stroke-dasharray", d => (this.showFlow ? 2 : 0))
        .style(
          "stroke-dashoffset",
          d => (d.targetBalance - d.sourceBalance) * this.tick / 10
        );
    },
    dragstarted(d) {
      if (!d3.event.active) this.simulation.alphaTarget(0.3).restart();
      d.fx = d.x;
      d.fy = d.y;
    },
    dragged(d) {
      d.fx = d3.event.x;
      d.fy = d3.event.y;
    },
    dragended(d) {
      if (!d3.event.active) this.simulation.alphaTarget(0);
      d.fx = null;
      d.fy = null;
    }
  }
};
</script>