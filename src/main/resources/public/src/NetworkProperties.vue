<style scoped>
.mdl-grid {
  width: 100%;
}

.mdl-cell {
  margin-top: 0;
}

.network-sizes {
  padding: 0;
  width: 70%;
}

.network-size {
  cursor: pointer;
  font-size: 10px;
}

.preview {
  width: 64px;
  height: 64px;
  padding: 2px;
  opacity: 0.7;
  border-style: solid;
  border-color: white;
  border-width: 1px;
  border-radius: 2px;
  pointer-events: none;
}

.preview.selected {
  padding: 1px;
  width: 64px;
  height: 64px;
  border-width: 2px;
  opacity: 1;
}

.role-config {
  font-size: 10px;
  line-height: 12px;
  align-items: center;
}
</style>

<template>
  <div class="network-properties">
    <h2>Network Properties</h2>
    <p>What network size do you want to use?</p>
    <div class="mdl-grid network-sizes">
      <div class="mdl-cell mdl-cell--6-col network-size" v-bind:key="network.name" v-for="network in networks" @click="selectedNetwork = network">
        <div>{{network.name}}</div>
        <graph class="preview" v-bind:class="{'selected': selectedNetwork === network}" ref="graph" :id="network.name" width="64" height="64" static="true" :network="network.network"></graph>
      </div>
    </div>
    <div style="margin: 20px 0;">
      <p>How should the roles be distributed to nodes?</p>
      <p v-for="role in roles" class="role-config">
        <label :for="role.key">{{role.name}}: {{role.proportion}}</label>
        <input :id="role.key" class="mdl-slider mdl-js-slider" type="range" min="0" max="1" step="0.01" v-model.number="role.proportion" tabindex="0">
      </p>
    </div>
    <div style="margin-top: 15px;">
      <button class="mdl-button mdl-js-button mdl-button--raised mdl-button--accent" @click="regenerate">Regenerate</button>
      <div v-if="loading" style="width: 15px; height: 15px; position: relative; top: 4px;" class="mdl-spinner mdl-spinner--single-color mdl-js-spinner is-active"></div>
    </div>
  </div>
</template>

<script>
import Graph from "./Graph.vue";
import LineChart from "./LineChart.vue";
import { generate } from "./generator";

export default {
  components: {
    graph: Graph,
    lineChart: LineChart
  },
  name: "network-properties",
  props: ["socket"],
  data() {
    return {
      networks: [
        {
          name: "Tiny",
          network: generate(5, 0.4),
          size: 5
        },
        {
          name: "Small",
          network: generate(10, 0.2),
          size: 10
        },
        {
          name: "Medium",
          network: generate(40, 0.08),
          size: 100
        },
        {
          name: "Large",
          network: generate(100, 0.02),
          size: 1000
        }
      ],
      roles: [
        {
          name: "Passive Consumer",
          key: "PASSIVE_CONSUMER",
          proportion: 0.5
        },
        {
          name: "Heavy Consumer",
          key: "HEAVY_CONSUMER",
          proportion: 0.4
        },
        {
          name: "Malicious User",
          key: "MALICIOUS_USER",
          proportion: 0.0
        },
        {
          name: "Faulty User",
          key: "FAULTY_USER",
          proportion: 0.0
        },
        {
          name: "Subscription Service",
          key: "SUBSCRIPTION_SERVICE",
          proportion: 0.0
        },
        {
          name: "Trader",
          key: "TRADER",
          proportion: 0.0
        },
        {
          name: "Hub",
          key: "HUB",
          proportion: 0.1
        },
        {
          name: "Second Level Hub",
          key: "SECOND_LEVEL_HUB",
          proportion: 0.0
        }
      ],
      selectedNetwork: null,
      loading: false
    };
  },
  created() {
    this.selectedNetwork = this.networks[0];
    componentHandler.upgradeAllRegistered();
  },
  watch: {
    selectedNetwork: function(newSelection, oldSelection) {
      if (oldSelection !== null) {
        this.$emit("selectedNetworkChanged", newSelection.name);
      }
    },
  },
  updated() {
    componentHandler.upgradeAllRegistered();
  },
  methods: {
    regenerate() {
      var size = this.selectedNetwork.name.toLowerCase();
      var roles = this.roles
              .filter(r => r.proportion > 0)
              .reduce((rs, r) => (rs[r.key] = r.proportion, rs), {});
      var x = {
        roles: roles
      };
      this.loading = true;
      this.$emit("regenerated");
      this.socket.send("config " + size + " " + JSON.stringify(x));
    },
    generate(size, probability) {
      return generate(size, probability);
    }
  }
};
</script>
