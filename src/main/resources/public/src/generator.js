import math from 'mathjs'

export function transform(network) {
  network.links = network.channels;
  delete network.channels;
  network.links.forEach(function (channel) {
    channel.source = channel.fromWallet
    channel.sourceBalance = channel.fromBalance
    channel.target = channel.toWallet
    channel.targetBalance = channel.toBalance
    delete channel.fromWallet
    delete channel.fromBalance
    delete channel.toWallet
    delete channel.toBalance
  });
  network.nodes.forEach(function (node) {
    node.wallet = node.walletAddress
    node.deactivated = false
    delete node.walletAddress
  });

  return network;
}

export function generate(count, connectionProbability) {
  const graph = {
    nodes: [],
    links: []
  };
  for (var i = 0; i < count; i++) {
    graph.nodes.push(createNode(Math.floor(Math.random() * 10)))
  }
  graph.nodes.forEach(function (a) {
    graph.nodes.forEach(function (b) {
      if (a !== b && Math.random() < connectionProbability) {
        a.channels.push(b.wallet);
        a.knownPeers.push(b)
        b.channels.push(a.wallet);
        b.knownPeers.push(a);
        graph.links.push({
          source: a.wallet,
          sourceBalance: Math.floor(Math.random() * 10),
          target: b.wallet,
          targetBalance: Math.floor(Math.random() * 10),
        })
      }
    });
  });
  graph.nodes.forEach(function (a) {
    a.knownPeers.forEach(function (b) {
      b.knownPeers.forEach(function (c) {
        if (a.knownPeers.indexOf(c) === -1) {
          a.knownPeers.push(c)
        }
      });
    });
  });
  return graph;
}

export function uniformMean(scale) {
  return scale / 2
}
export function normalMean(mean, dev) {
  return mean
}

export function betaMean(alpha, beta, scale) {
  return scale / (1.0 + (beta / alpha))
}


export function generateUniform(count, scale) {
  var data = [];
  for (var i = 0; i < count; i++) {
    var x = i / 10
    data.push({
      x: i,
      y: scale
    })
  }
  return data;
}

export function generateBeta(count, alpha, beta) {
  var data = [];
  for (var i = 0; i < count; i++) {
    var x = i / count
    data.push({
      x: i,
      y: Math.pow(x, (alpha - 1)) * Math.pow((1 - x), (beta - 1)) / b(alpha, beta)
    })
  }
  return data
}

function b(alpha, beta) {
  return math.gamma(alpha) * math.gamma(beta) / math.gamma(alpha + beta)
}

export function generateNormal(count, mu, sigmaSquared) {
  var data = [];
  for (var i = 0; i < count; i++) {
    var x = i / 5
    data.push({
      x: i,
      y: (1 / (Math.sqrt(2 * Math.PI * sigmaSquared))) * Math.exp(-(Math.pow(x - mu, 2) / (2 * sigmaSquared)))
    })
  }
  return data
}

export function createNode(balance) {
  return {
    ip: "10.0.0.0",
    wallet: makeWallet(),
    balance: balance,
    channels: [],
    knownPeers: []
  }
}
export function createLink(a, b) {
  return {
    source: a.wallet,
    sourceBalance: a.balance,
    target: b.wallet,
    targetBalance: b.balance
  }
}

function makeWallet() {
  var text = "0x";
  var possible = "ABCDEF0123456789";

  for (var i = 0; i < 10; i++)
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  return text;
}