<style scoped>
.link-wrapper {
  width: 15vw;
  color: #0d1b2a;
  background-color: white;
  font-family: "Roboto Mono", monospace;
  border-radius: 20px;
  padding: 4px;
  opacity: 0.98;
}

.balance {
  width: 28px;
  min-width: 40px;
  outline: none;
}

.fee {
  width: 18px;
  min-width: 40px;
  outline: none;
}

.container {
  display: inline-block;
  font-size: 0.8em;
}

.left {
  width: 40%;
  float: left;
}

.center {
  font-size: 30px;
  width: 20%;
  padding-top: 15px;
}

.right {
  width: 40%;
  float: right;
}

h3 {
  font-size: 1.1em;
  margin: 0;
  font-weight: 800;
  line-height: 30px;
}
</style>

<template>
  <div class="link-wrapper" draggable="true">
    <div class="left container">
      <h3>{{link.source.wallet}}</h3>
      <div><input class="balance" type="number" v-model="sourceBalance" step="0.01" min="0.0" v-bind:max="balance" />$</div>
      <div>Fee: 0.1%</div>
    </div>
    <div class="center container">
      <span>&harr;</span>
      <button @click="saveBalance" v-show="initialSourceBalance !== sourceBalance">Save</button>
    </div>
    <div class="right container">
      <h3>{{link.target.wallet}}</h3>
      <div><input class="balance" type="number" v-model="targetBalance" step="0.01" min="0.0" v-bind:max="balance" />$</div>
      <div>Fee: 0.2%</div>
    </div>
  </div>
</template>

<script>
export default {
  name: "link-detail",
  props: ["link", "socket"],
  data() {
    return {
      initialSourceBalance: this.link.sourceBalance.toFixed(2),
      sourceBalance: this.link.sourceBalance.toFixed(2),
      targetBalance: this.link.targetBalance.toFixed(2),
      balance: this.link.sourceBalance + this.link.targetBalance
    };
  },
  watch: {
    link: function(newValue) {
      this.initialSourceBalance = this.link.sourceBalance.toFixed(2);
      this.sourceBalance = this.link.sourceBalance.toFixed(2);
      this.targetBalance = this.link.targetBalance.toFixed(2);
      this.balance = this.link.sourceBalance + this.link.targetBalance;
    },
    sourceBalance: function(newValue) {
      this.targetBalance = (this.balance - this.sourceBalance).toFixed(2);
    },
    targetBalance: function(newValue) {
      this.sourceBalance = (this.balance - this.targetBalance).toFixed(2);
    }
  },
  methods: {
    saveBalance: function() {
      this.link.sourceBalance = parseFloat(this.sourceBalance);
      this.link.targetBalance = parseFloat(this.targetBalance);
      this.initialSourceBalance = this.link.sourceBalance.toFixed(2);
      var channel = {
        fromWallet: this.link.source.wallet,
        toWallet: this.link.target.wallet,
        fromBalance: this.link.sourceBalance,
        toBalance: this.link.targetBalance
      };
      this.socket.send("set-balance " + JSON.stringify(channel));
      this.$emit("closeLinkDetails");
    }
  }
};
</script>