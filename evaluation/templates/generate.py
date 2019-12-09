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

# Build simulator
subprocess.run(simBuild, cwd=simBuildDir)

templates = [
	(scName, scName + '-s-' + str(s).zfill(4), 'template "%s" -s ' + str(s) + ' -c 0 ' + agents + ' --pretty')
		for scName, agents in scenarios.items()
		for s in range(10, 1010, 10)
] + [
	(scName, scName + '-c-' + str(s).zfill(4) + '-' + str(c).zfill(4), 'template "%s" -s ' + str(s) + ' -c ' + str(c) + ' ' + agents + ' --pretty')
		for scName, agents in scenarios.items()
		for s in [30, 200, 1000]
		for c in range(10, 1010, 10)
]

for (scName, name, args) in templates:
	if not os.path.isdir(scName):
		os.mkdir(scName)
	templatePath = os.path.abspath(os.path.join(scName, name + '.json'))
	stdoutPath = os.path.join(scName, name + '.template.stdout')
	stderrPath = os.path.join(scName, name + '.template.stderr')
	
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
