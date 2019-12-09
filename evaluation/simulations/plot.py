import json
import matplotlib
import math
import numpy as np
import os
from scipy import interpolate
import sys
import time
import re

matplotlib.use('PDF')
import matplotlib.pyplot as plt

plotAll = (len(sys.argv) > 1 and sys.argv[1] == 'all')

# Find all scenarios
templates = [
	(os.path.splitext(filename)[0], os.path.join(dirpath, filename))
		for dirpath, dirnames, filenames in os.walk('.')
		for filename in filenames if filename.endswith('.json') and dirpath.startswith('.\\' + filename.split('-')[0])
]
for templateName, templatePath in templates:
	templateDir = os.path.dirname(templatePath)
	plotPath = os.path.abspath(os.path.join(templateDir, templateName + '.metrics.pdf'))

	if os.path.isfile(plotPath) and plotAll == False:
		print(templateName + ' exists')
		continue
	else:
		print(templateName + ' plotting')

	scaleF = 1.8
	fig = plt.figure(figsize=(8.27 * scaleF, 11.69 * scaleF), tight_layout={'rect': [0, 0, 1, 0.98]})
	st = fig.suptitle(templateName, fontsize=18)
	st.set_y(0.99)
	axs = fig.subplots(nrows=9, ncols=4, sharey='row')
	
	# Plot MPROF
	mprofFiles = [
		os.path.join(dirpath, filename)
			for dirpath, dirnames, filenames in os.walk(templateDir)
			for filename in filenames if filename.endswith('.mprof') and filename.startswith(templateName)
	]
	fileIndex = -1
	for mprofFile in mprofFiles:
		fileIndex += 1
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

		mem_line_label = time.strftime("%d / %m / %Y - start at %H:%M:%S", time.localtime(global_start)) + ".{0:03d}".format(int(round(math.modf(global_start)[0] * 1000)))
		
		x_val = t
		y_val = mem
		tck = interpolate.splrep(x_val, y_val, s=0)
		x_val = np.linspace(0, x_val[-1], num=500)
		y_val = interpolate.splev(x_val, tck, der=0)	
		
		ax = axs[0, fileIndex]
		ax.set_title(os.path.basename(mprofFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('time (in seconds)')
		ax.set_ylabel('memory used (MiB)')
		ax.plot(x_val, y_val, ',-r', label=mem_line_label)
		ax.grid()

	# Plot STDOUT
	stdoutFiles = [
		os.path.join(dirpath, filename)
			for dirpath, dirnames, filenames in os.walk(templateDir)
			for filename in filenames if filename.endswith('.stdout') and filename.startswith(templateName + '.simulate.')
	]
	fileIndex = -1
	templateChannelCount = 0
	templateNodeCount = 0
	with open(templatePath, 'r') as templateFd:
		template = json.load(templateFd)
		templateChannelCount = len(template['network']['channels'])
		templateNodeCount = len(template['network']['nodes'])
	for stdoutFile in stdoutFiles:
		fileIndex += 1
		currentCycle = 0
		paymentCount = 0
		paymentSuccessCount = 0
		hopCount = 0
		lastHopCount = 0
		fee = 0.0
		lastFee = 0.0
		channelCount = templateChannelCount
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
					avgChannelCounts.append((currentCycle, channelCount * 2 / templateNodeCount))
					currentCycle = int(data)					
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
				
		# Plot Success-Ratio
		x_val = [x[0] for x in successRates]
		y_val = [x[1] for x in successRates]
		ax = axs[1, fileIndex]
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('success ratio')
		ax.set_ylim(bottom=0, top=1)
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Avg-Hop-Count
		x_val = [x[0] for x in avgHopCount]
		y_val = [x[1] for x in avgHopCount]
		ax = axs[2, fileIndex]
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('avg hop count')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Avg-Fee
		x_val = [x[0] for x in avgFee]
		y_val = [x[1] for x in avgFee]
		ax = axs[3, fileIndex]
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('avg fee')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Channel-Count
		x_val = [x[0] for x in avgChannelCounts]
		y_val = [x[1] for x in avgChannelCounts]
		ax = axs[4, fileIndex]
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('avg channel count')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Node-Packet-Count
		x_val = [x[0] for x in nodeNetworkUsage]
		y_val = [x[1] for x in nodeNetworkUsage]
		tck = interpolate.splrep(x_val, y_val, s=0)
		x_val = np.arange(1, x_val[-1], 5)
		y_val = interpolate.splev(x_val, tck, der=0)		
		ax = axs[5, fileIndex]
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('node packet count')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Node-Packet-Size
		x_val = [x[0] for x in nodeNetworkUsage]
		y_val = [x[2] for x in nodeNetworkUsage]
		tck = interpolate.splrep(x_val, y_val, s=0)
		x_val = np.arange(1, x_val[-1], 5)
		y_val = interpolate.splev(x_val, tck, der=0)		
		ax = axs[6, fileIndex]
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('node packet size (MiB)')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Router-Packet-Count
		x_val = [x[0] for x in routerNetworkUsage]
		y_val = [x[1] for x in routerNetworkUsage]
		tck = interpolate.splrep(x_val, y_val, s=0)
		x_val = np.arange(1, x_val[-1], 5)
		y_val = interpolate.splev(x_val, tck, der=0)		
		ax = axs[7, fileIndex]
		ax.get_shared_y_axes().remove(ax)
		ax.yaxis.major = matplotlib.axis.Ticker()
		ax.yaxis.set_major_locator(matplotlib.ticker.AutoLocator())
		ax.yaxis.set_major_formatter(matplotlib.ticker.ScalarFormatter())
		ax.yaxis.set_tick_params(which='both', labelleft=True)
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('router packet count')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		# Plot Router-Packet-Size
		x_val = [x[0] for x in routerNetworkUsage]
		y_val = [x[2] for x in routerNetworkUsage]
		tck = interpolate.splrep(x_val, y_val, s=0)
		x_val = np.arange(1, x_val[-1], 5)
		y_val = interpolate.splev(x_val, tck, der=0)		
		ax = axs[8, fileIndex]
		ax.get_shared_y_axes().remove(ax)
		ax.yaxis.major = matplotlib.axis.Ticker()
		ax.yaxis.set_major_locator(matplotlib.ticker.AutoLocator())
		ax.yaxis.set_major_formatter(matplotlib.ticker.ScalarFormatter())
		ax.yaxis.set_tick_params(which='both', labelleft=True)
		ax.set_title(os.path.basename(stdoutFile).replace(templateName + '.simulate.', ''))
		ax.set_xlabel('cycles')
		ax.set_ylabel('router packet size (MiB)')
		ax.plot(x_val, y_val, ',-r')
		ax.grid()
		
	fig.savefig(plotPath)

