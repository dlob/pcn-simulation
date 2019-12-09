import json
import matplotlib
import os
import sys

matplotlib.use('PDF')
import matplotlib.pyplot as plt

plotAll = (len(sys.argv) > 1 and sys.argv[1] == 'all')

# Find all scenarios
scenarios = [(f.split('.')[0], f) for f in os.listdir('.') if os.path.isfile(f) and f.endswith('.metrics.collect.json')]

for scenarioName, collectFile in scenarios:
	plotPath = os.path.abspath(scenarioName + '.metrics.pdf')
	if os.path.isfile(plotPath) and plotAll == False:
		print(scenarioName + ' exists')
		continue
	else:
		print(scenarioName + ' plotting ... ', end='', flush=True)

	scaleF = 1.8
	fig = plt.figure(figsize=(8.27 * scaleF, 11.69 * scaleF), tight_layout={'rect': [0, 0, 1, 0.98]})
	st = fig.suptitle(scenarioName, fontsize=18)
	st.set_y(0.99)
	axs = fig.subplots(nrows=4, ncols=2)
	
	with open(collectFile) as fp:  
		data = json.load(fp)
	
	# Plot Avg-Vertex-Degree
	x_val = [x[0] for x in data['avgVertexDegree']]
	y_val = [x[1] for x in data['avgVertexDegree']]
	ax = axs[0, 0]
	ax.set_title('avg vertex degree')
	ax.set_xlabel('node count')
	ax.set_ylabel('channel count/degree')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot Max-Vertex-Degree
	x_val = [x[0] for x in data['maxVertexDegree']]
	y_val = [x[1] for x in data['maxVertexDegree']]
	ax = axs[0, 1]
	ax.set_title('max vertex degree')
	ax.set_xlabel('node count')
	ax.set_ylabel('channel count/degree')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot Avg-Shortest-Path-Length
	x_val = [x[0] for x in data['avgShortestPathLength']]
	y_val = [x[1] for x in data['avgShortestPathLength']]
	ax = axs[1, 0]
	ax.set_title('avg shortest path length')
	ax.set_xlabel('node count')
	ax.set_ylabel('avg shortest path length')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot Avg-Clustering-Coefficient
	x_val = [x[0] for x in data['avgClustering']]
	y_val = [x[1] for x in data['avgClustering']]
	ax = axs[1, 1]
	ax.set_title('avg clustering coefficient')
	ax.set_xlabel('node count')
	ax.set_ylabel('avg clustering coefficient')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot Density
	x_val = [x[0] for x in data['density']]
	y_val = [x[1] for x in data['density']]
	ax = axs[2, 0]
	ax.set_title('density')
	ax.set_xlabel('node count')
	ax.set_ylabel('density')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot SM-Avg-Payment-Amount
	x_val = [x[0] for x in data['smAvgPaymentAmount']]
	y_val = [x[1] for x in data['smAvgPaymentAmount']]
	ax = axs[2, 1]
	ax.set_title('SM avg payment amount')
	ax.set_xlabel('cycle count')
	ax.set_ylabel('payment amount')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot MD-Avg-Payment-Amount
	x_val = [x[0] for x in data['mdAvgPaymentAmount']]
	y_val = [x[1] for x in data['mdAvgPaymentAmount']]
	ax = axs[3, 0]
	ax.set_title('MD avg payment amount')
	ax.set_xlabel('cycle count')
	ax.set_ylabel('payment amount')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
	# Plot LG-Avg-Payment-Amount
	x_val = [x[0] for x in data['lgAvgPaymentAmount']]
	y_val = [x[1] for x in data['lgAvgPaymentAmount']]
	ax = axs[3, 1]
	ax.set_title('LG avg payment amount')
	ax.set_xlabel('cycle count')
	ax.set_ylabel('payment amount')
	ax.plot(x_val, y_val, ',-r')
	ax.grid()
		
	fig.savefig(plotPath)
	print('done')
