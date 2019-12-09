import memory_profiler as mp
import os
import subprocess

# Simulator
simBuildDir = r'pcn-simulation'
simBuild = r'"%s\gradlew.bat" jar' % (simBuildDir)
simExecDir = r'pcn-simulation\build\libs'
simExec = r'java -Xmx4g -Dorg.slf4j.simpleLogger.defaultLogLevel=error -Dorg.slf4j.simpleLogger.logFile=System.err -jar "%s\PCNSimulation-1.0-SNAPSHOT.jar" %s' % (simExecDir, '%s')

# Routing algorithms
routingAlgorithms = {
	"basic": ['basic', 'etora', 'mdart', 'terp']
}
routingAlgorithmsDefault=['etora', 'mdart', 'terp']

# Build simulator
subprocess.run(simBuild, cwd=simBuildDir)

# Find all scenarios
templates = [
	(os.path.splitext(filename)[0], os.path.join(dirpath, filename))
		for dirpath, dirnames, filenames in os.walk(".")
		for filename in filenames if filename.endswith(".json") and dirpath.startswith('.\\' + filename.split('-')[0])
]

for templateName, templatePath in templates:
	templateDir = os.path.dirname(templatePath)
	templateRoutingAlgorithms = [ras for k, ras in routingAlgorithms.items() if k in templateName]
	if len(templateRoutingAlgorithms) == 0:
		templateRoutingAlgorithms = routingAlgorithmsDefault
	else:
		templateRoutingAlgorithms = templateRoutingAlgorithms[-1]
	
	for routingAlgorithm in templateRoutingAlgorithms:	
		stdoutPath = os.path.join(templateDir, templateName + '.simulate.' + routingAlgorithm + '.stdout')
		stderrPath = os.path.join(templateDir, templateName + '.simulate.' + routingAlgorithm + '.stderr')
		mprofPath = os.path.join(templateDir, templateName + '.simulate.' + routingAlgorithm + '.mprof')
		
		if os.path.isfile(stdoutPath) and not os.path.isfile(stderrPath):
			print(templateName + ' ' + routingAlgorithm + ' exists')
		else:
			print(templateName + ' ' + routingAlgorithm + ' simulating')
			cmd = simExec % ('simulate -t "%s" --routing %s' % (os.path.abspath(templatePath), routingAlgorithm.upper()))
			print('  > ' + cmd)
			with open(stdoutPath, 'w') as stdoutFp:
				with open(stderrPath, 'w') as stderrFp:
					with open(mprofPath, 'w') as mprofFp:
						with subprocess.Popen(cmd, cwd=simExecDir, stdout=stdoutFp, stderr=stderrFp) as p:
							mp.memory_usage(proc=p, interval=0.1, include_children=True, stream=mprofFp)
			if os.path.getsize(stderrPath) > 0:
				print('  FAILED')
				continue
			else:
				os.remove(stderrPath)
