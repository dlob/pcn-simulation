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

plotPath = os.path.abspath('eval-success-lowpart.pdf')

if os.path.isfile(plotPath) and plotAll == False:
	print(plotPath + ' exists')
	sys.exit()
else:
	print(plotPath + ' plotting')

scaleF = 1.2
fig = plt.figure(figsize=(4.13 * scaleF, 3.3 * scaleF), tight_layout={'rect': [0, 0.13, 1, 1], 'w_pad': 4})
axs = fig.subplots(nrows=1, ncols=1, squeeze=False)

scenarios = ['basic', 'lowpart']

for scenarioName in scenarios:
	
	templateName = lambda f: os.path.basename(f).split('.')[0]
	templateSize = lambda f: templateName(f).split('-')[1]
	algorithmName = lambda f: os.path.basename(f).split('.')[2]

	# Plot STDOUT
	stdoutFiles = [
		os.path.join(dirpath, filename)
			for dirpath, dirnames, filenames in os.walk(scenarioName)
			for filename in filenames if filename.endswith('.stdout') and '1.simulate.' in filename
	]
	for stdoutFile in stdoutFiles:
		if '-md-' not in stdoutFile:
			continue
		if '.basic.' in stdoutFile:
			continue
		currentCycle = 0
		paymentCount = 0
		paymentSuccessCount = 0
		hopCount = 0
		lastHopCount = 0
		fee = 0.0
		lastFee = 0.0

		channelCount = 0
		nodeCount = 0
		with open(os.path.join(scenarioName, templateName(stdoutFile) + '.json'), 'r') as templateFd:
			template = json.load(templateFd)
			channelCount = len(template['network']['channels'])
			nodeCount = len(template['network']['nodes'])
		successRates = []
		avgHopCount = []
		avgFee = []
		avgChannelCounts = []
		nodeNetworkUsage = []
		routerNetworkUsage = []
		with open(stdoutFile, 'r') as stdoutFp:
			for line in stdoutFp:
				# Parse data lines
				dataLine = re.search(r'^Data\(topic=(.+), data=(.+)\)$', line)
				if not dataLine:
					continue
				
				topic = dataLine.group(1)
				data = dataLine.group(2)
				
				if topic == 'cycle':
					currentCycle = int(data)
					avgChannelCounts.append((currentCycle, channelCount * 2 / nodeCount))
				if topic.endswith('-payment'):
					paymentCount += 1
				if topic.endswith('-payment-successful'):
					paymentSuccessCount += 1
					successRates.append((currentCycle, paymentSuccessCount / paymentCount))
					hopCount += lastHopCount
					avgHopCount.append((currentCycle, hopCount / paymentSuccessCount))
					fee += lastFee
					avgFee.append((currentCycle, fee / paymentSuccessCount))
				if topic.endswith('-payment-failed') and (len(successRates) == 0 or successRates[-1][0] != currentCycle):
					successRates.append((currentCycle, paymentSuccessCount / paymentCount))
					defaultAvgHopCount = 0 if len(avgHopCount) == 0 else avgHopCount[-1][1]
					avgHopCount.append((currentCycle, defaultAvgHopCount))
					defaultAvgFee = 0 if len(avgFee) == 0 else avgFee[-1][1]
					avgFee.append((currentCycle, defaultAvgFee))
				if topic == 'single-payment':
					lastFee = 0.0
				if topic == 'channel':
					lastHopCount = 1
				if topic == 'multi-channel':
					lastHopCount = data.count('0x') - 1
				if topic == 'payment-fees':
					lastFee = float(data.split(',')[1].strip(' )'))
				if topic == 'node-network-usage':
					nodeNetworkUsage.append((currentCycle, int(data.split(',')[0].strip(' (')), int(data.split(',')[1].strip(' )')) / 1024 / 1024))
				if topic == 'router-network-usage':
					routerNetworkUsage.append((currentCycle, int(data.split(',')[0].strip(' (')), int(data.split(',')[1].strip(' )')) / 1024 / 1024))
				if topic == 'open-channel':
					channelCount += 1
				if topic == 'close-channel':
					channelCount -= 1

		algName = algorithmName(stdoutFile)
		
		x_val = [e[0] for e in successRates]
		y_val = [e[1] for e in successRates]
		t, c, k = interpolate.splrep(x_val, y_val)
		spline = interpolate.BSpline(t, c, k, extrapolate=False)
		xx = np.arange(0, 520, 20)
		xx[0] = 1
		axs[0, 0].plot(xx, spline(xx), lineTypes[algName], label=algNames[algName] + ' ' + scenarioName)
		

ax = axs[0, 0]
ax.set_xlabel('cycles')
ax.set_ylabel('success ratio')

for ax in axs.flat:
	ax.set_ylim(bottom=-0.05, top=1.05)
	ax.set_xlim(left=-10, right=510)
	ax.grid()
	for ln in ax.get_lines():
		ln.set_markersize(5.0)
		ln.set_linewidth(0.9)
		if ' lowpart' in ln.get_label():
			ln.set_linestyle(':')

handles, labels = axs[0, 0].get_legend_handles_labels()
index, handles, labels = zip(*sorted(zip(range(6), handles, labels), key=lambda t: (t[0] % 3) * 2 + (t[0] // 3)))
fig.legend(handles, labels, loc='lower center', ncol=3, fontsize=9.2)

fig.savefig(plotPath)
#os.startfile(plotPath)
