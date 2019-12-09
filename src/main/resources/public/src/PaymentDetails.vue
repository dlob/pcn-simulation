<style scoped>
td {
  font-family: "Roboto Mono", monospace;
  font-size: 0.8em;
  opacity: 0.7;
}
</style>
<template>
    <div>
        <h2>Payment Details</h2>
        <table align="center">
            <thead>
                <tr>
                    <th></th>
                    <th>From&nbsp; &rarr; &nbsp;To</th>
                    <th>Amount</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="payment in payments.slice(0,10)">
                    <td>
                        <i v-if="payment.cycle === cycle" class="material-icons" style="font-size: 0.8em; color: #FFB400;">schedule</i>
                    </td>
                    <td>
                        {{payment.from}} &rarr; {{payment.to}}
                    </td>
                    <td>{{payment.amount.toFixed(4)}}</td>
                </tr>
            </tbody>
            <tfoot>
                <tr>
                    <td></td>
                    <td width="100%">
                        <span v-if="payments.length > 10">...</span>
                    </td>
                    <td></td>
                </tr>
            </tfoot>
        </table>
    </div>
</template>

<script>
export default {
  name: "simulation-details",
  props: ["socket", "cycle"],
  data() {
    return {
      payments: []
    };
  },
  watch: {
    cycle: function(newCycle) {
      this.payments = this.payments.filter(p => p.cycle >= newCycle);
    }
  },
  created() {
      var self = this;
      this.socket.addEventListener("message", function (event) {
          var msg = JSON.parse(event.data);
          if (msg.topic === "payments") {
              self.payments = msg.data;
          }
          if (msg.topic === "reset") {
              self.payments = [];
          }
      });
  },
  methods: {
    updatePayments(payments, cycle) {
      payments.forEach(p => this.payments.push(p));
    }
  }
};
</script>