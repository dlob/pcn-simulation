import json
import matplotlib
import numpy as np
import os
from scipy import interpolate
from scipy.signal import savgol_filter
import sys

matplotlib.use('PDF')
import matplotlib.pyplot as plt

lineTypes = {
	'basic': '^-k',
	'commercial': 'x-k',
	'faulty': '1-k',
	'hub': '|-k',
	'lowpart': 's-k',
	'malicious': 'o-k'
}

plotAll = (len(sys.argv) > 1 and sys.argv[1] == 'all')

# Find all scenarios
scenarios = [(f.split('.')[0], f) for f in os.listdir('.') if os.path.isfile(f) and f.endswith('.metrics.collect.json')]

plotPath = os.path.abspath('summary.metrics.pdf')
if os.path.isfile(plotPath) and plotAll == False:
	print('summary exists')
	sys.exit()

scaleF = 1.4
fig = plt.figure(figsize=(8.27 * scaleF, 11.69 * scaleF), tight_layout={'rect': [0, 0.01, 1, 0.98]})
st = fig.suptitle('template', fontsize=18)
st.set_y(0.99)
axs = fig.subplots(nrows=4, ncols=2)

for scenarioName, collectFile in scenarios:
	print(scenarioName +' plotting ... ', end='', flush=True)
	
	with open(collectFile) as fp:  
		data = json.load(fp)
	
	def check_duplicates(x_val):
		dups = set([x for x in x_val if x_val.count(x) > 1])
		if len(dups) > 0:
			print('DUPLICATES FOUND: ' + scenarioName)
			print(dups)
	
	def plot(line, col, data, x_index = 0, y_index = 1, filtered=True, filter_window=21):
		x_val = [e[x_index] for e in data]
		y_val = [e[y_index] for e in data]
		check_duplicates(x_val)
		if filtered:
			y_val = savgol_filter(y_val, filter_window, 3)
		t, c, k = interpolate.splrep(x_val, y_val)
		spline = interpolate.BSpline(t, c, k, extrapolate=False)
		xx = np.arange(1, x_val[-1], 50)
		axs[line, col].plot(xx, spline(xx), lineTypes[scenarioName], label=scenarioName)
	
	plot(0, 0, data['avgVertexDegree'])
	plot(0, 1, data['maxVertexDegree'])
	plot(1, 0, data['avgShortestPathLength'])
	plot(1, 1, data['avgClustering'], filter_window=15)
	plot(2, 0, data['density'], filtered=False)
	plot(2, 1, data['smAvgPaymentAmount'])
	plot(3, 0, data['mdAvgPaymentAmount'])
	plot(3, 1, data['lgAvgPaymentAmount'])

	print('done')

ax = axs[0, 0]
ax.set_title('avg vertex degree')
ax.set_xlabel('node count')
ax.set_ylabel('channel count/degree')
ax = axs[0, 1]
ax.set_title('max vertex degree')
ax.set_xlabel('node count')
ax.set_ylabel('channel count/degree')
ax = axs[1, 0]
ax.set_title('avg shortest path length')
ax.set_xlabel('node count')
ax.set_ylabel('avg shortest path length')
ax = axs[1, 1]
ax.set_title('avg clustering coefficient')
ax.set_xlabel('node count')
ax.set_ylabel('avg clustering coefficient')
ax = axs[2, 0]
ax.set_title('density')
ax.set_xlabel('node count')
ax.set_ylabel('density')
ax = axs[2, 1]
ax.set_title('SM avg payment amount')
ax.set_xlabel('cycle count')
ax.set_ylabel('payment amount')
ax = axs[3, 0]
ax.set_title('MD avg payment amount')
ax.set_xlabel('cycle count')
ax.set_ylabel('payment amount')
ax = axs[3, 1]
ax.set_title('LG avg payment amount')
ax.set_xlabel('cycle count')
ax.set_ylabel('payment amount')

for ax in axs.flat:
	ax.grid()
	for ln in ax.get_lines():
		ln.set_markersize(4.0)
		ln.set_linewidth(0.8)

handles, labels = axs[0, 0].get_legend_handles_labels()
fig.legend(handles, labels, loc='lower center', ncol=6)

fig.savefig(plotPath)
