# README

## What is this?

*PCN Simulator* allows you to simulate payments in a payment channel network and observe occuring network changes. This includes changes in topology and liquidity, depending on the behavior of nodes, as well as various routing and protocol parameters. The next section will get you started!

![Screenshot of the tool](./screen-shot.png)

The simulator in this repository is based on *octn-simulation* published by CSIRO that can be found in the [CSIRO Data Access Portal](http://hdl.handle.net/102.100.100/220996?index=1) ([original repository](https://bitbucket.csiro.au/projects/AAPDEVBC/repos/octn-simulation)).

## Getting Started

System Requirements:
 * JDK 1.8 (_JAVA_HOME_ needs to be set)
 * npm
 * IntelliJ / Eclipse
 * [Kotlin Plugin](https://kotlinlang.org/docs/tutorials/getting-started.html) for IntelliJ / [Kotlin Plugin](https://kotlinlang.org/docs/tutorials/getting-started-eclipse.html) for Eclipse
 * *Optional:* Docker
 
To get started, import the project as a Gradle project into your Java IDE. Alternatively, you can use the Gradle wrapper
provided in the project. The first execution of `gradlew` on Linux and macOS or `gradlew.bat` on Windows will install
the correct version of Gradle for local use within the project.

## Build

To build the project, follow these steps:

1. **Install frontend dependencies**: In the directory `src/main/resources/public` run `npm install`
2. **Build the frontend**: In the directory `src/main/resources/public` run `npm run build`
3. **Create a jar**: In the project root directory, run `./gradlew jar`
4. **Create code documentation**: In the project root directory, run `./gradlew dokka`
5. **Build a Docker image**: In the project root directory, run `docker build -t pcn-simulator:0.2.0 .`

## Run

**Run in IDE:** To run the app in your IDE. The program entry point is located in the `Main.kt` class.

**Run container**: To run a Docker container, type `docker run -p 8081:8081 pcn-simulator:0.2.0`. For both cases, the tool is will now be available at [http://127.0.0.1:8081](http://127.0.0.1:8081).

## Evaluation Process

To run the evaluation you need additional tools:
 * [Python 3](https://www.python.org/)
 * [matplotlib](https://pypi.org/project/matplotlib/)
 * [graphviz](https://pypi.org/project/graphviz/) (also needs [graphviz executables](https://graphviz.org/) v2.38)
 * [numpy](https://pypi.org/project/numpy/)
 * [scipy](https://pypi.org/project/scipy/)
 * [memory-profiler](https://pypi.org/project/memory-profiler/)

Evaluate template generation:

```shell
> cd evaluation/templates
# 1. create and render templates
> python generate.py
# 2. collect metrics from templates
> python collect.py
# 3. create diagrams
> python plot.py                           # diagram per metric and scenario
> python plot-summary.py                   # summarizing metrics and scenarios on one page
> python plot-eval-template-properties.py  # final selection of metrics
```

Evaluate routing algorithms:

```shell
> cd evaluation/simulations
# 1. create templates per scenario and network size 
> python template.py
# 2. simulate templates
> python simulate.py
# 3. create diagrams
> python plot.py            # diagram per metric and scenario
> python plot-summary.py    # summarizing metrics per scenario
# 4. create diagrams for final selection of metrics
> python plot-eval-channels-malicious.py
> python plot-eval-etora-mem-sizes.py
> python plot-eval-etora-success-sizes.py
> python plot-eval-fees-hub.py
> python plot-eval-hops-hub.py
> python plot-eval-mdart-success-sizes.py
> python plot-eval-packet-count-commercial.py
> python plot-eval-packet-size-commercial.py
> python plot-eval-success-faulty.py
> python plot-eval-success-hub.py
> python plot-eval-success-lowpart.py
> python plot-eval-success-malicious.py
> python plot-eval-terp-success-sizes.py
```

## Evaluation Results

The results of the template-evaluation can be found in [evaluation/templates](./evaluation/templates).

The results of the evaluation of the routing algorithms can be found in [evaluation/simulations](./evaluation/simulations).
A sub-directory exists per scenario, containing the selected templates (\*.json files) in three sizes (sm, md, lg).


## License

Refer to [LICENSE.txt](./LICENSE.txt)

## Authors

* Rafael Konlechner
* David Lobmaier (dlob)
