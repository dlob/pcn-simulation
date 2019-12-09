<style scoped>
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

.control-button {
  color: white;
  width: fit-content;
  min-width: fit-content;
  padding: 0 10px;
}

.control-item {
  text-align: left;
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
</style>

<template>
  <div class="controls">
    <div style="display: inline-block; position: relative; top: -10px;">
      <button class="mdl-button mdl-js-button control-button" @click="reset">
        <i class="material-icons">replay</i>
      </button>
      <button class="mdl-button mdl-js-button control-button focus-button" @click="togglePlay">
        <i v-if="play" class="material-icons">pause</i>
        <i v-if="!play" class="material-icons">play_arrow</i>
      </button>
      <button class="mdl-button mdl-js-button control-button" @click="step">
        <i class="material-icons">skip_next</i>
      </button>
    </div>
    <div class="control-item" style="margin-right: 80px; margin-left: 10px;">
      <label class="control-label">Cycle</label>
      <div>{{cycleDisplay}}</div>
    </div>
    <div class="control-item">
      <label class="control-label">Fee Adjustment Period:</label>
      <select class="control-select">
        <option value="volvo">10 Cycles</option>
        <option value="saab">20 Cycles</option>
        <option value="mercedes">50 Cycles</option>
        <option value="audi">100 Cycles</option>
      </select>
    </div>
    <div class="control-item">
      <label class="control-label">Adjustment Rate:</label>
      <select class="control-select">
        <option value="volvo">0.01</option>
        <option value="saab">0.1</option>
        <option value="mercedes">0.25</option>
        <option value="audi">0.5</option>
      </select>
    </div>
    <div class="control-item">
      <label class="control-label">Agent Strategy:</label>
      <select class="control-select">
        <option value="volvo">Rational</option>
        <option value="saab">Random</option>
        <option value="mercedes">Risk-Averse</option>
        <option value="audi">Economical</option>
      </select>
    </div>
    <div class="control-item">
      <label class="control-label">Routing Strategy:</label>
      <select class="control-select">
        <option value="volvo">Flare</option>
        <option value="saab">AODV</option>
        <option value="mercedes">Onion</option>
        <option value="audi">AODV-Ext</option>
      </select>
    </div>
    <div class="control-item">
      <label class="control-label">Fee Strategy:</label>
      <select class="control-select">
        <option value="volvo">Fixed Rate</option>
        <option value="saab">Dynamic Demand Rate</option>
        <option value="mercedes">Dynamic Channel Flow</option>
        <option value="audi">DCHS Rate</option>
      </select>
    </div>
  </div>
</template>

<script>
export default {
  name: "navigation",
  props: ["socket", "cycle"],
  data() {
    return {
      play: false
    };
  },
  computed: {
    cycleDisplay() {
      var a = ("00000" + this.cycle).slice(-6);
      return [a.slice(0, 3), ",", a.slice(3)].join("");
    }
  },
  watch: {
    cycle: function(newCycle) {
      this.cycle = newCycle;
    }
  },
  created() {
    var self = this;
    this.socket.addEventListener("message", function(event) {
      var msg = JSON.parse(event.data);
      if (msg.topic === "play") {
        self.play = true;
      }
      if (msg.topic === "pause") {
        self.play = false;
      }
    });
  },
  methods: {
    togglePlay() {
      if (!this.play) {
        this.socket.send("play");
      } else {
        this.socket.send("pause");
      }
    },
    reset() {
      this.socket.send("reset");
    },
    step() {
      this.socket.send("step");
    }
  }
};
</script>