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

# Find all scenarios
scenarios = [
	dirname
		for dirpath, dirnames, filenames in os.walk('.') if dirpath == '.'
		for dirname in dirnames if not dirname.startswith('.')
]

for scenarioName in scenarios:
	plotPath = os.path.abspath('eval-raw-' + scenarioName + '.pdf')

	if os.path.isfile(plotPath) and plotAll == False:
		print(scenarioName + ' exists')
		continue
	else:
		print(scenarioName + ' plotting')

	scaleF = 1.7
	fig = plt.figure(figsize=(8.27 * scaleF, 11 * scaleF), tight_layout={'rect': [0, 0.01, 1, 1]})
	#fig = plt.figure(figsize=(8.27 * scaleF, 11.69 * scaleF), tight_layout={'rect': [0, 0.01, 1, 0.98]})
	#st = fig.suptitle(scenarioName, fontsize=18)
	#st.set_y(0.99)
	axs = fig.subplots(nrows=9, ncols=3)
	
	templateName = lambda f: os.path.basename(f).split('.')[0]
	templateSize = lambda f: templateName(f).split('-')[1]
	algorithmName = lambda f: os.path.basename(f).split('.')[2]
	colIndex = lambda f: ['sm', 'md', 'lg'].index(templateSize(f))

	# Plot MPROF
	mprofFiles = [
		os.path.join(dirpath, filename)
			for dirpath, dirnames, filenames in os.walk(scenarioName)
			for filename in filenames if filename.endswith('.mprof') and '1.simulate.' in filename
	]
	for mprofFile in mprofFiles:
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
		
		i = colIndex(mprofFile)
		algName = algorithmName(mprofFile)
		xx = np.arange(0, 520, 20) # display cycles instead of seconds
		axs[0, i].plot(xx, y_val, lineTypes[algName], label=algNames[algName])
	
	# Plot STDOUT
	stdoutFiles = [
		os.path.join(dirpath, filename)
			for dirpath, dirnames, filenames in os.walk(scenarioName)
			for filename in filenames if filename.endswith('.stdout') and '1.simulate.' in filename
	]
	for stdoutFile in stdoutFiles:
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
		
		i = colIndex(stdoutFile)
		algName = algorithmName(stdoutFile)
		
		def check_duplicates(x_val):
			dups = set([x for x in x_val if x_val.count(x) > 1])
			if len(dups) > 0:
				print('DUPLICATES FOUND: ' + stdoutFile)
				print(dups)
		
		def plot(line, data, x_index = 0, y_index = 1):
			x_val = [e[x_index] for e in data]
			y_val = [e[y_index] for e in data]
			check_duplicates(x_val)
			axs[line, i].plot(x_val, y_val, lineTypes[algName], label=algNames[algName])
		
		def plotSmooth(line, data, x_index = 0, y_index = 1):
			x_val = [e[x_index] for e in data]
			y_val = [e[y_index] for e in data]
			check_duplicates(x_val)
			y_val = savgol_filter(y_val, 31, 3)
			t, c, k = interpolate.splrep(x_val, y_val, s=0)
			spline = interpolate.BSpline(t, c, k, extrapolate=False)
			xx = np.arange(0, 520, 20)
			xx[0] = 1
			axs[line, i].plot(xx, spline(xx), lineTypes[algName], label=algNames[algName])
		
		def plotInterpolated(line, data, x_index = 0, y_index = 1):
			x_val = [e[x_index] for e in data]
			y_val = [e[y_index] for e in data]
			check_duplicates(x_val)
			t, c, k = interpolate.splrep(x_val, y_val)
			spline = interpolate.BSpline(t, c, k, extrapolate=False)
			xx = np.arange(0, 520, 20)
			xx[0] = 1
			axs[line, i].plot(xx, spline(xx), lineTypes[algName], label=algNames[algName])
		
		#plotInterpolated(1, successRates)
		#plotInterpolated(2, avgHopCount)
		#plotInterpolated(3, avgFee)
		#plotInterpolated(4, avgChannelCounts)
		#plotSmooth(5, nodeNetworkUsage)
		#plotSmooth(6, nodeNetworkUsage, y_index=2)
		#plotSmooth(7, routerNetworkUsage)
		#plotSmooth(8, routerNetworkUsage, y_index=2)
		
		plotInterpolated(1, successRates)
		plotInterpolated(2, avgHopCount)
		plotInterpolated(3, avgFee)
		plotInterpolated(4, avgChannelCounts)
		plotInterpolated(5, nodeNetworkUsage)
		plotInterpolated(6, nodeNetworkUsage, y_index=2)
		plotInterpolated(7, routerNetworkUsage)
		plotInterpolated(8, routerNetworkUsage, y_index=2)
		
	
	for i, sizeName in enumerate(['sm', 'md', 'lg']):
		# 1: memory usage
		ax = axs[0, i]
		ax.set_title('memory usage (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('memory used (MiB)')
		# 2: success ratio
		ax = axs[1, i]
		ax.set_title('success ratio (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('success ratio')
		ax.set_ylim(bottom=-0.05, top=1.05)
		# 3: avg hop count
		ax = axs[2, i]
		ax.set_title('avg hop count (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('avg hop count')
		# 4: avg fee count
		ax = axs[3, i]
		ax.set_title('avg fee (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('avg fee')
		# 5: channel count
		ax = axs[4, i]
		ax.set_title('avg channel count (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('avg channel count')
		# 6: node packet count
		ax = axs[5, i]
		ax.set_title('node packet count (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('node packet count')
		# 7: node packet size
		ax = axs[6, i]
		ax.set_title('node packet size (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('node packet size (MiB)')
		# 8: router packet count
		ax = axs[7, i]
		ax.set_title('router packet count (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('router packet count')
		# 9: router packet size
		ax = axs[8, i]
		ax.set_title('router packet size (' + sizeName + ')')
		ax.set_xlabel('cycles')
		ax.set_ylabel('router packet size (MiB)')
		
	for ax in axs.flat:
		ax.set_xlim(left=-10, right=510)
		ax.grid()
		for ln in ax.get_lines():
			ln.set_markersize(4.0)
			ln.set_linewidth(0.8)

	handles, labels = axs[0, 0].get_legend_handles_labels()
	fig.legend(handles, labels, loc='lower center', ncol=4)

	fig.savefig(plotPath)
