<style scoped>
.wrapper {
  color: #0d1b2a;
  background-color: white;
  width: 13vw;
  border-radius: 20px;
  opacity: 0.98;
}

.details {
  font-size: 0.8em;
  text-align: left;
  padding: 15px 15px 15px 45px;
}

h2 {
  font-size: 1.2em;
  line-height: 1.2em;
  margin: 0.5em;
  font-family: "Roboto Mono", monospace;
}

h2 small {
  font-size: 0.8em;
}
</style>

<template>
  <div class="wrapper">
    <h2>
      <small>Node:
        <i>{{node.name}}</i>
      </small><br />{{shortWallet}}</h2>
    <button @click="deactivate">
      <span v-if="node.deactivated === true">Activate</span>
      <span v-else>Deactivate</span>
    </button>
    <button @click="remove">Remove</button>
    <div class="details">
      <div><strong>Balance:</strong> {{node.balance}}$</div>
      <div><strong>Locked Funding:</strong> {{node.lockedFunding.toFixed(2)}}$</div>
      <div><strong>Channels:</strong> {{node.channels.length}}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: "node-detail",
  props: ["node"],
  data() {
    return {
      msg: ""
    };
  },
  computed: {
    shortWallet() {
      return this.node.wallet.substring(0, 10);
    }
  },
  methods: {
    showReach() {
      this.$emit("showReach");
    },
    deactivate() {
      this.$emit("deactivate");
    },
    remove() {
      this.$emit("remove");
    }
  }
};
</script>