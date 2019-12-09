import json
import networkx as nx
import os
import sys

collectAll = (len(sys.argv) > 1 and sys.argv[1] == 'all')

# Find all scenarios
scenarios = [
	(
		dirname,
		[f for f in os.listdir(dirname) if f.endswith('.json') and '-s-' in f],
		[f for f in os.listdir(dirname) if f.endswith('.json') and '-c-0030-' in f],
		[f for f in os.listdir(dirname) if f.endswith('.json') and '-c-0200-' in f],
		[f for f in os.listdir(dirname) if f.endswith('.json') and '-c-1000-' in f]
	)
		for dirname in os.listdir('.') if os.path.isdir(dirname) and not dirname.startswith('.')
]

for scenarioName, sizeTemplates, smCycleTemplates, mdCycleTemplates, lgCycleTemplates in scenarios:
	collectPath = os.path.abspath(scenarioName + '.metrics.collect.json')
	if os.path.isfile(collectPath) and collectAll == False:
		print(scenarioName +' exists')
		continue
	else:
		print(scenarioName +' collecting ', end='', flush=True)
	
	avgVertexDegree=[]
	maxVertexDegree=[]
	density=[]
	avgShortestPathLength=[]
	avgClustering=[]
	for sizeTemplate in sizeTemplates:
		template = {}
		with open(os.path.join(scenarioName, sizeTemplate)) as json_file:  
			template = json.load(json_file)

		nodeCount = len(template['network']['nodes'])
		channelCount = len(template['network']['channels'])

		density.append((nodeCount, (2 * channelCount) / (nodeCount * (nodeCount - 1))))

		g = nx.Graph()
		for node in template['network']['nodes']:
			g.add_node(node['walletAddress'])
		for channel in template['network']['channels']:
			g.add_edge(channel['fromWallet'], channel['toWallet'], weight=channel['fromBalance'] + channel['toBalance'])		
		largest_connected_subgraph = g.subgraph(max(nx.connected_components(g), key=len))
		
		degrees = dict(g.degree()).values()
		avgVertexDegree.append((nodeCount, sum(degrees) / nodeCount))
		maxVertexDegree.append((nodeCount, max(degrees)))
		
		avgShortestPathLength.append((nodeCount, nx.average_shortest_path_length(largest_connected_subgraph)))
		avgClustering.append((nodeCount, nx.average_clustering(largest_connected_subgraph)))
		
		print('.', end='', flush=True)
	
	smAvgPaymentAmount=[]
	mdAvgPaymentAmount=[]
	lgAvgPaymentAmount=[]
	for cycleTemplates, avgPaymentAmount in [(smCycleTemplates, smAvgPaymentAmount), (mdCycleTemplates, mdAvgPaymentAmount), (lgCycleTemplates, lgAvgPaymentAmount)]:
		for cycleTemplate in cycleTemplates:
			template = {}
			with open(os.path.join(scenarioName, cycleTemplate)) as json_file:  
				template = json.load(json_file)

			paymentCount = len(template['cycles'])
			paymentSum = sum([c['amount'] for c in template['cycles']])
			avgPaymentAmount.append((paymentCount, paymentSum / paymentCount))
			
			print('.', end='', flush=True)
	
	data = {
		'avgVertexDegree': avgVertexDegree,
		'maxVertexDegree': maxVertexDegree,
		'density': density,
		'avgShortestPathLength': avgShortestPathLength,
		'avgClustering': avgClustering,
		'smAvgPaymentAmount': smAvgPaymentAmount,
		'mdAvgPaymentAmount': mdAvgPaymentAmount,
		'lgAvgPaymentAmount': lgAvgPaymentAmount
	}
	with open(collectPath, 'w', encoding='utf-8') as fp:
		json.dump(data, fp, ensure_ascii=False, indent=4)
	
	print(' done')
