import json
import matplotlib
import math
import numpy as np
import os
from scipy import interpolate
from scipy.signal import savgol_filter
import sys
import time
import re

matplotlib.use('PDF')
import matplotlib.pyplot as plt

lineTypes = {
	'basic': '^-k',
	'etora': 'x-k',
	'mdart': '|-k',
	'terp': 'o-k'
}

algNames = {
	'basic': 'BASIC',
	'etora': 'E-TORA',
	'mdart': 'M-DART',
	'terp': 'TERP'
}

plotAll = (len(sys.argv) > 1 and sys.argv[1] == 'all')

plotPath = os.path.abspath('eval-etora-mem-size.pdf')

if os.path.isfile(plotPath) and plotAll == False:
	print(plotPath + ' exists')
	sys.exit()
else:
	print(plotPath + ' plotting')

scaleF = 1.2
fig = plt.figure(figsize=(4.13 * scaleF, 3.3 * scaleF), tight_layout={'rect': [0, 0.13, 1, 1], 'w_pad': 4})
axs = fig.subplots(nrows=1, ncols=1, squeeze=False)

scenarios = ['commercial']

for scenarioName in scenarios:
	
	templateName = lambda f: os.path.basename(f).split('.')[0]
	templateSize = lambda f: templateName(f).split('-')[1]
	algorithmName = lambda f: os.path.basename(f).split('.')[2]

	# Plot MPROF
	mprofFiles = [
		os.path.join(dirpath, filename)
			for dirpath, dirnames, filenames in os.walk(scenarioName)
			for filename in filenames if filename.endswith('.mprof') and '1.simulate.' in filename
	]
	for mprofFile in mprofFiles:
		algName = algorithmName(mprofFile)
		if algName not in ['etora', 'terp']:
			continue
		t = []
		mem = []
		with open(mprofFile, 'r') as mprofFp:
			for line in mprofFp:
				if line.startswith('MEM'):
					field, m, ts = line.split(' ', 2)
					mem.append(float(m))
					t.append(float(ts))
		
		mem = np.asarray(mem)
		t = np.asarray(t)
		ind = t.argsort()
		mem = mem[ind]
		t = t[ind]
		global_start = float(t[0])
		t = t - global_start
		
		x_val = t
		y_val = mem
		tck = interpolate.splrep(x_val, y_val, s=0)
		x_val = np.linspace(0, x_val[-1], num=26)
		y_val = interpolate.splev(x_val, tck, der=0)
		
		xx = np.arange(0, 520, 20) # display cycles instead of seconds
		axs[0, 0].plot(xx, y_val, lineTypes[algName], label=algNames[algName] + ' ' + templateSize(mprofFile))

ax = axs[0, 0]
ax.set_xlabel('cycles')
ax.set_ylabel('memory usage (MiB)')
		
for ax in axs.flat:
	ax.set_xlim(left=-10, right=510)
	ax.grid()
	for ln in ax.get_lines():
		ln.set_markersize(5.0)
		ln.set_linewidth(0.9)
		if 'sm' in ln.get_label():
			ln.set_linestyle(':')
		if 'md' in ln.get_label():
			ln.set_linestyle('--')

handles, labels = axs[0, 0].get_legend_handles_labels()
fig.legend(handles, labels, loc='lower center', ncol=3, fontsize=9.2)

fig.savefig(plotPath)
#os.startfile(plotPath)
