import 'material-design-lite/material.min.css'
import 'material-design-lite/material.min.js'
import './styles.css'

import Vue from 'vue'
import App from './App.vue'

Vue.directive('click-outside', {
  bind: function (el, binding, vnode) {
    el.event = function (event) {
      if (!(el == event.target || el.contains(event.target))) {
        vnode.context[binding.expression](event);
      }
    };
    document.body.addEventListener('click', el.event)
  },
  unbind: function (el) {
    document.body.removeEventListener('click', el.event)
  },
});

new Vue({
  el: '#app',
  render: h => h(App)
})