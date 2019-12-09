import json
from graphviz import Graph, Digraph
import os
import subprocess

# Simulator
simBuildDir = r'pcn-simulation'
simBuild = r'"%s\gradlew.bat" jar' % (simBuildDir)
simExecDir = r'pcn-simulation\build\libs'
simExec = r'java -Xmx4g -Dorg.slf4j.simpleLogger.defaultLogLevel=error -Dorg.slf4j.simpleLogger.logFile=System.err -jar "%s\PCNSimulation-1.0-SNAPSHOT.jar" %s' % (simExecDir, '%s')

# Scenarios
scenarios = {
	'basic':		'-a HEAVY_CONSUMER=30.0 -a HUB=1.0',
	'lowpart':		'-a HEAVY_CONSUMER=25.0 -a PASSIVE_CONSUMER=5.0 -a HUB=1.0',
	'hub':			'-a HEAVY_CONSUMER=25.0 -a HUB=5.0 -a SECOND_LEVEL_HUB=1.0',
	'faulty':		'-a HEAVY_CONSUMER=25.0 -a FAULTY_USER=5.0 -a HUB=1.0',
	'malicious':	'-a HEAVY_CONSUMER=25.0 -a MALICIOUS_USER=5.0 -a HUB=1.0',
	'commercial':	'-a HEAVY_CONSUMER=48.0 -a PASSIVE_CONSUMER=20.0 -a SUBSCRIPTION_SERVICE=10.0 -a TRADER=12.0 -a HUB=9.0 -a SECOND_LEVEL_HUB=1.0'
}

# Network sizes per scenario
networkSizes = {
	'sm': 30,
	'md': 200,
	'lg': 1000
}

# Number of cycles per scenario
cycleCount = {
	'test': [4]
}
defaultCycleCount = [500]

# Number of templates per scenario
templateCount = {
	'basic': 1
}
defaultTemplateCount = 1

# Build simulator
subprocess.run(simBuild, cwd=simBuildDir)

templates = [
	(scName, scName + '-' + nsName + '-' + str(c) + '-' + str(i + 1), 'template "%s" -s ' + str(s) + ' -c ' + str(c) + ' ' + agents + ' --pretty')
		for scName, agents in scenarios.items()
		for nsName, s in networkSizes.items()
		for c in cycleCount.get(scName + '-' + nsName, cycleCount.get(scName, defaultCycleCount))
		for i in range(templateCount.get(scName + '-' + nsName, templateCount.get(scName, defaultTemplateCount)))
]

for (scName, name, args) in templates:
	if not os.path.isdir(scName):
		os.mkdir(scName)
	templatePath = os.path.abspath(os.path.join(scName, name + '.json'))
	stdoutPath = os.path.join(scName, name + '.template.stdout')
	stderrPath = os.path.join(scName, name + '.template.stderr')
	networkRenderingPath = os.path.join(scName, name + '.template.network.gv')
	cyclesRenderingPath = os.path.join(scName, name + '.template.cycles.gv')
	
	if os.path.isfile(templatePath):
		print(name + ' exists')
	else:
		print(name + ' generating')
		cmd = simExec % (args % (templatePath))
		print('  > ' + cmd)
		with open(stdoutPath, 'w') as stdoutFp:
			with open(stderrPath, 'w') as stderrFp:
				subprocess.run(cmd, cwd=simExecDir, stdout=stdoutFp, stderr=stderrFp, shell=True)
			if os.path.getsize(stderrPath) > 0:
				print('  FAILED')
				continue
			else:
				os.remove(stderrPath)
		
		print('  Rendering network ... ', end='', flush=True)
		template = {}
		with open(templatePath) as json_file:  
			template = json.load(json_file)
		nodes = dict(map(lambda n: (n['name'], n['walletAddress']), template['network']['nodes']))
		agents = dict(map(lambda a: (a['name'], a['role']), template['agents']))
		g = Graph(comment='Template', engine='sfdp')
		if '-sm-' in name or '-md-' in name:
			g.attr(overlap='false', splines='true') # too slow for large graphs
		else:
			g.attr(overlap='scale')
		for name, wa in nodes.items():
			g.node(wa, name + '\n' + agents[name], fontsize='8')
		for channel in template['network']['channels']:
			g.edge(channel['fromWallet'], channel['toWallet'])
		g.render(networkRenderingPath, format='pdf')
		print('done')
		
		print('  Rendering cycles ... ', end='', flush=True)
		g = Digraph(comment='Template', engine='sfdp')
		if '-sm-' in name or '-md-' in name:
			g.attr(overlap='false', splines='true') # too slow for large graphs
		else:
			g.attr(overlap='scale')
		for name, wa in nodes.items():
			g.node(wa, name + '\n' + agents[name], fontsize='8')
		cs = []
		for cycle in template['cycles']:
			c = list(filter(lambda c: c['from'] == cycle['from'] and c['to'] == cycle['to'], cs))
			if len(c) > 0:
				c[0]['count'] += 1
			else:
				cs.append({
					'from': cycle['from'],
					'to': cycle['to'],
					'count': 1
				})
		for c in cs:
			g.edge(c['from'], c['to'], penwidth=str(c['count']))
		g.render(cyclesRenderingPath, format='pdf')
		print('done')
