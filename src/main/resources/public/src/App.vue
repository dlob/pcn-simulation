<style scoped>
#app {
  text-align: center;
}

header {
  position: relative;
  z-index: 11;
  box-shadow: 0 0 5px #0d1b2a;
  background-color: white;
  color: #0d1b2a;
  text-align: center;
  width: 100%;
  padding: 1vh 0;
}

h1 {
  font-size: 2em;
  font-weight: 100;
  opacity: 0.8;
}

h1 strong {
  font-weight: 400;
  opacity: 1;
}

h3 {
  font-size: 1em;
  margin: 0.3em;
  font-family: "Roboto Mono", monospace;
}

.mdl-grid {
  padding: 0;
  width: 100%;
}

.mdl-cell {
  margin-top: 0;
}

.controls {
  position: relative;
  z-index: 10;
  background-color: #4392f1;
  color: white;
  padding: 0.5vh;
  box-shadow: 0 5px 10px black;
  text-align: center;
  font-size: 15px;
  vertical-align: text-top;
}

.node-details,
.link-details {
  position: absolute;
}

.control-button {
  color: white;
  width: fit-content;
  min-width: fit-content;
  padding: 0 10px;
}

.template-control .control-button {
  margin-top: 20px;
}

.control-item {
  display: inline-block;
  margin: 10px 20px 0 20px;
}

.control-label {
  font-size: 12px;
  opacity: 0.8;
  display: block;
  margin-right: 5px;
  margin-bottom: 3px;
}

.control-select {
  width: 100%;
}

.results {
  height: 85vh;
  z-index: 10;
  background-color: #cad8de;
  color: #0d1b2a;
  margin-right: -8px;
  box-shadow: 0 5px 10px black;
}

.template-info {
  position: absolute;
  bottom: 1vh;
  font-size: 0.8em;
  font-family: "Roboto Mono", monospace;
}

.scroll-down {
  position: absolute;
  bottom: 1vh;
  left: 45%;
  font-size: 0.8em;
}

.template-toggle {
  position: absolute;
  top: 26vh;
  z-index: 2;
}

.template-control {
  padding-left: 120px;
  text-align: left;
}

.table-number {
  text-align: right;
}
</style>
<template>
  <div id="app">
    <header>
      <h1>Simulating
        <strong>Payment Channel Networks</strong> for Parameter Tuning</h1>
    </header>
    <navigation ref="navigation" :socket="socket" :cycle="cycle"></navigation>
    <div class="mdl-grid">
      <div class="mdl-cell mdl-cell--2-col">
        <network-properties ref="networkProperties" :socket="socket" v-on:selectedNetworkChanged="onSelectedNetworkChanged" v-on:regenerated="reset"></network-properties>
      </div>
      <div class="mdl-cell mdl-cell--6-col">
        <h2>Simulation</h2>
        <div class="template-toggle">
          <label class="mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect" for="show-channel-liquidity">
            <input type="checkbox" id="show-channel-liquidity" class="mdl-checkbox__input" v-model="showChannelLiquidity">
            <span class="mdl-checkbox__label" style="font-size: 11px;">Show channel liquidity</span>
          </label><br/>
          <label class="mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect" for="show-channel-usage">
            <input type="checkbox" id="show-channel-usage" class="mdl-checkbox__input" v-model="showChannelUsage">
            <span class="mdl-checkbox__label" style="font-size: 11px;">Show channel usage</span>
          </label>
        </div>
        <div class="template-info">nodes online: {{nodesOnline}} channels open: {{channelsActive}} tx/s: {{transactionsPerSecond}}</div>
        <div style="position: relative;">
          <node-detail v-click-outside="closeNodeDetails" v-bind:style="{top: nodeDetailsY, left: nodeDetailsX}" class="node-details" v-if="showNodeDetail" :node="selectedNode" v-on:remove="removeNode" v-on:deactivate="deactivateNode" v-on:showReach="showReach"></node-detail>
          <link-detail v-click-outside="closeLinkDetails" v-bind:style="{top: linkDetailsY, left: linkDetailsX}" class="link-details" v-if="showLinkDetail" :socket="socket" :link="selectedLink" v-on:closeLinkDetails="closeLinkDetails"></link-detail>
          <graph ref="graph" id="network" width="800" height="680" static="false" :network="network" v-on:nodeSelected="onNodeSelected" v-on:linkSelected="onLinkSelected"></graph>
        </div>
      </div>
      <div class="mdl-cell mdl-cell--2-col">
        <payment-details ref="paymentDetails" :socket="socket" :cycle="cycle"></payment-details>
      </div>
      <div class="mdl-cell mdl-cell--2-col results">
        <h2>Results</h2>
        <line-chart ref="lineChart" id="line-chart" width="200" height="200" min="0" :max="100" :data="data"></line-chart>
        <div>Current Reliability: {{reliability.toFixed(2)}}%</div>
        <div>{{successfulPayments}} / {{paymentCount}}</div>
        <h3>Network Properties</h3>
        <label>Vertex Degree (avg): avg</label><br/>
        <label>Vertex Connectivity (avg): avg</label><br/>
        <label>Locked Up in Channels: ...</label><br/>
        <h3>Detailed Results</h3>
        <table style="width: 70%; margin: 0 auto; text-align: left;">
          <tr>
            <td>
              <b>Issued single:</b>
            </td>
            <td class="table-number">{{singlePaymentsIssued}}</td>
          </tr>
          <tr>
            <td>
              <b>Failed single:</b>
            </td>
            <td class="table-number">{{singlePaymentsFailed}}</td>
          </tr>

          <tr>
            <td>
              <b>Successful single:</b>
            </td>
            <td class="table-number">{{singlePaymentsSuccessful}}</td>
          </tr>
          <tr>
            <td>
              <b>Issued multi:</b>
            </td>
            <td class="table-number">{{multiPaymentsIssued}}</td>
          </tr>
          <tr>
            <td>
              <b>Failed multi:</b>
            </td>
            <td class="table-number">{{multiPaymentsFailed}}</td>
          </tr>
          <tr>
            <td>
              <b>Successful multi:</b>
            </td>
            <td class="table-number">{{multiPaymentsSuccessful}}</td>
          </tr>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
import Graph from "./Graph.vue";
import NodeDetail from "./NodeDetail.vue";
import LinkDetail from "./LinkDetail.vue";
import LineChart from "./LineChart.vue";
import NetworkProperties from "./NetworkProperties.vue";
import Navigation from "./Navigation.vue";
import PaymentDetails from "./PaymentDetails.vue";
import { generate, transform, createNode, createLink } from "./generator";

export default {
  components: {
    graph: Graph,
    networkProperties: NetworkProperties,
    nodeDetail: NodeDetail,
    linkDetail: LinkDetail,
    navigation: Navigation,
    lineChart: LineChart,
    paymentDetails: PaymentDetails
  },
  name: "app",
  data() {
    return {
      singlePaymentsIssued: 0,
      multiPaymentsIssued: 0,
      singlePaymentsFailed: 0,
      multiPaymentsFailed: 0,
      singlePaymentsSuccessful: 0,
      multiPaymentsSuccessful: 0,
      socket: null,
      network: null,
      selectedNode: null,
      selectedLink: null,
      newNode: null,
      newLink: null,
      showNodeDetail: false,
      showLinkDetail: false,
      index: 1,
      cycle: 0,
      transactionsPerSecond: 0,
      showChannelLiquidity: false,
      showChannelUsage: false,
      data: [{ x: 0, y: 100 }]
    };
  },
  watch: {
    showChannelLiquidity: function(newValue) {
      this.$refs.graph.showFlow = newValue;
    },
    reliability: function(newValue) {
      this.addDataPoint(newValue);
    }
  },
  computed: {
    nodesOnline: function() {
      if (this.network) {
        return this.network.nodes.length;
      } else {
        return 0;
      }
    },
    channelsActive: function() {
      if (this.network) {
        return this.network.links.length;
      } else {
        return 0;
      }
    },
    nodeDetailsX: function() {
      return this.selectedNode.x.toFixed(0) + "px";
    },
    nodeDetailsY: function() {
      return this.selectedNode.y.toFixed(0) + "px";
    },
    linkDetailsX: function() {
      return this.selectedLink.target.x.toFixed(0) + "px";
    },
    linkDetailsY: function() {
      return this.selectedLink.target.y.toFixed(0) + "px";
    },
    reliability: function() {
      if (this.successfulPayments === 0 && this.failedPayments === 0) {
        return 0.0;
      }
      var result =
        100 *
        this.successfulPayments /
        (this.successfulPayments + this.failedPayments);
      return result;
    },
    successfulPayments: function() {
      return this.singlePaymentsSuccessful + this.multiPaymentsSuccessful;
    },
    failedPayments: function() {
      return this.singlePaymentsFailed + this.multiPaymentsFailed;
    },
    paymentCount: function() {
      return this.successfulPayments + this.failedPayments;
    }
  },
  created() {
    var self = this;
    this.socket = new WebSocket("ws://" + location.hostname + ":" + location.port + "/websocket");
    this.socket.addEventListener("open", function(event) {
      self.socket.send("reset");
    });
    this.socket.addEventListener("message", function(event) {
      var msg = JSON.parse(event.data);
      console.log(msg);
      if (msg.topic === "reset") {
        self.reset();
      }
      if (msg.topic === "cycle") {
        self.cycle = msg.data;
        self.$refs.graph.resetHighlight();
        self.socket.send("payments " + JSON.stringify({ start: self.cycle, end: self.cycle + 10 }));
      }
      if (msg.topic === "network" || msg.topic === "reset" || msg.topic === "regenerate") {
        var graph = transform(msg.data);
        self.network = graph;
        self.$refs.networkProperties.loading = false;
        self.$refs.graph.resetHighlight();
      }
      if (msg.topic === "channel" || msg.topic === "unlocked") {
        var link = self.network.links.filter(
          l =>
            l.source.wallet === msg.data.fromWallet &&
            l.target.wallet === msg.data.toWallet
        )[0];
        link.sourceBalance = msg.data.fromBalance;
        link.targetBalance = msg.data.toBalance;
        if (msg.topic === "channel") {
          self.highlightPath([msg.data.fromWallet, msg.data.toWallet]);
          self.channelTCount++;
        }
      }
      if (msg.topic === "single-payment") {
        self.singlePaymentsIssued++;
      }
      if (msg.topic === "multi-payment") {
        self.multiPaymentsIssued++;
      }
      if (msg.topic === "single-payment-successful") {
        self.singlePaymentsSuccessful += 1;
      }
      if (msg.topic === "single-payment-failed") {
        self.singlePaymentsFailed += 1;
      }
      if (msg.topic === "multi-payment-successful") {
        self.multiPaymentsSuccessful += 1;
      }
      if (msg.topic === "multi-payment-failed") {
        self.multiPaymentsFailed += 1;
      }
      if (msg.topic === "multi-channel") {
        self.highlightPath(msg.data.second);
      }
      if (msg.topic === "close-channel") {
        self.closeChannel(msg.data);
      }
      if (msg.topic === "open-channel") {
        self.openChannel(msg.data);
      }
    });
  },
  mounted() {
    var element = document.getElementById("network");
    var rect = element.getBoundingClientRect();
    this.canvasX = rect.x;
    this.canvasY = rect.y;
  },
  methods: {
    reset: function() {
      this.cycle = 0;
      this.index = 1;
      this.data = [{ x: 0, y: 100 }];
      this.singlePaymentsIssued = 0;
      this.multiPaymentsIssued = 0;
      this.singlePaymentsFailed = 0;
      this.multiPaymentsFailed = 0;
      this.singlePaymentsSuccessful = 0;
      this.multiPaymentsSuccessful = 0;
    },
    onSelectedNetworkChanged(newNetwork) {
      //this.socket.send("network " + newNetwork.toLowerCase());
    },
    showReach() {
      this.$refs.graph.showReach(this.selectedNode);
    },
    addDataPoint(dataPoint) {
      this.$refs.lineChart.addDataPoint({
        x: this.index++,
        y: dataPoint
      });
    },
    generate(size, probability) {
      return generate(size, probability);
    },
    onNodeSelected(selected) {
      this.selectedNode = selected;
      this.showNodeDetail = true;
      this.showLinkDetail = false;
    },
    onLinkSelected(selected) {
      this.selectedLink = selected;
      this.showLinkDetail = true;
      this.showNodeDetail = false;
    },
    closeNodeDetails() {
      this.showNodeDetail = false;
    },
    closeLinkDetails() {
      this.showLinkDetail = false;
    },
    highlightPath(nodes) {
      nodes = nodes.map(
        wallet => this.network.nodes.filter(n1 => n1.wallet === wallet)[0]
      );
      this.$refs.graph.highlightPath(nodes);
    },
    openChannel(channel) {
      this.$refs.graph.addLink({
        source: channel.fromWallet,
        sourceBalance: channel.fromBalance,
        target: channel.toWallet,
        targetBalance: channel.toBalance
      });
    },
    closeChannel(channel) {
      var link = this.network.links.filter(
        l => l.source.wallet === channel.first && l.target.wallet === channel.second
      )[0];
      this.$refs.graph.removeLink(link);
    },
    deactivateNode() {
      this.$refs.graph.deactivateNode(this.selectedNode);
    },
    addNode() {
      const node = createNode();
      this.newNode = this.$refs.graph.addNode(node);
    },
    addLink() {
      const link = createLink(this.selectedNode, this.newNode);
      this.newLink = this.$refs.graph.addLink(link);
    },
    removeNode() {
      this.$refs.graph.removeNode(this.selectedNode);
      this.showNodeDetail = false;
    },
    removeLink() {
      this.$refs.graph.removeLink(this.selectedLink);
    }
  }
};
</script>