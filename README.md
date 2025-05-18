# Replication Package for: ShuffleBench: A Benchmark for Large-Scale Data Shuffling Operations with Distributed Stream Processing Frameworks

Our paper *ShuffleBench: A Benchmark for Large-Scale Data Shuffling Operations with Distributed Stream Processing Frameworks* (accepted for the industry track of the ICPE'24) introduces *ShuffleBench*, a novel benchmark to evaluate the performance of modern stream processing frameworks.
This document describes how to replicate the experimental evaluation of our study as well as how to run ShuffleBench for custom performance evaluations.

## Replication of the Experimental Evaluation

The following steps describe how to replicate the experimental evaluation of our study.

To only replicate or extend the analysis of our experimental results, skip to [Analyze Benchmark Results](#analyze-benchmark-results).

### Prerequisites

* To repeat our evaluation of ShuffleBench, access to a Kubernetes cluster is required. Preferably, this is a 3-node cluster from a public cloud provider. Alternatively, also a local cluster (e.g., created with k3d) works if the host system provides sufficient resources. (Tested on an Ubuntu laptop with 32~GB memory and a 14-core CPU.) In addition, kubectl, helm, and git must be installed.
* To replicate the exact experiments of our study, an AWS account is required as well as the tools eksctl, kubectl, helm, and git. Be aware that this creates a cost-intensive cluster and replicating all experiments takes more than two days.
* To analyze benchmark results (either the results of our experiments or your custom ones), a Python 3.10 installation, pip, the venv module, and a tool to open and execute Jupyter notebooks (e.g. VS Code) is required.

### Setup a Kubernetes Cluster

The experiments of our study were conducted on a 10-node Kubernetes cluster in Amazon Web Services with EBS volumes. This is a rather heavy-sized and, thus, cost-intensive setup. Alternatively, our experiments can be repeated on a smaller cluster Kubernetes cluster (e.g., in a local environment), which might, however, cause different results. 

#### Option 1: 10-Node AWS Cluster (Exact Setup as in Publication)

This option requires [eksctl](https://eksctl.io/), [kubectl](https://kubernetes.io/docs/tasks/tools/), and [helm](https://helm.sh/) to be installed on your machine. Create the Kubernetes cluster by running:

```sh
eksctl create cluster -f installation/eks-cluster.yaml
```

Note that the cluster creation takes some time (about 20 minutes in our case).

EKS does not set up the Kubernetes metric server by default, which means that `kubectl top` does not work out-of-the-box. To install it, run:

```sh
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

To later run Kafka, we need to create the `kafka` storage class. For AWS, this can be done by running:

```sh
kubectl apply -f installation/aws-kafka-storage-class.yaml
```

When installing Kafka, this storage class ensures that corresponding [Amazon EBS](https://aws.amazon.com/ebs/) volumes are created.

#### Option 2: Lightweight Cluster

Most Kubernetes installations should support running ShuffleBench. As ShuffleBench aims for benchmarking distributed stream processing systems, we recommend a cluster with at least three nodes. However, a sufficiently-dimensioned local machine with a local cluster created with [Docker Desktop](https://docs.docker.com/desktop/) or [k3d](https://k3d.io/) can work as well. For example, we successfully repeated our study with k3d v5.5.1 on an Ubuntu 22.04 laptop with 32~GB memory and a 14-core CPU. A simple k3d cluster can be created by running:

```sh
k3d cluster create
```

Again, [kubectl](https://kubernetes.io/docs/tasks/tools/) and helm are required to be installed on your machine.


### Install Theodolite

ShuffleBench uses the benchmarking framework [Theodolite](https://www.theodolite.rocks/). Installing Theodolite also installs Kafka and necessary monitoring infrastructure.

#### Option 1: Use Exact Setup as in Publication (AWS-specific)

Run the following commands to install Theodolite with the same configuration as in our study:

```sh
git clone https://github.com/cau-se/theodolite.git
helm dependencies update theodolite/helm
helm install theodolite theodolite/helm -f https://raw.githubusercontent.com/cau-se/theodolite/main/helm/preconfigs/extended-metrics.yaml -f installation/values.yaml -f installation/values-nodegroups.yaml -f installation/values-aws-kafka-storage.yaml
```

The installation may take some time. Before continuing, make sure all Pods are up and running.

#### Option 2: Vendor-neutral Setup with Nodes Groups (Similar to Publication)

This option requires a Kubernetes cluster where there are nodes with label `type=infra`, nodes with label `type=kafka`, and nodes with label `type=sut`. Nodes can be labeled in Kubernetes with `kubectl label node <NODE-NAME> type=<TYPE>` or via the interfaces of the cloud providers.

Run the following commands to install Theodolite in similar configuration as in our study:

```sh
git clone https://github.com/cau-se/theodolite.git
helm dependencies update theodolite/helm
helm install theodolite theodolite/helm -f https://raw.githubusercontent.com/cau-se/theodolite/main/helm/preconfigs/extended-metrics.yaml -f installation/values.yaml -f installation/values-nodegroups.yaml
```

The installation may take some time. Before continuing, make sure all Pods are up and running.

If you do not have sufficiently fast disks available, you could add more Kafka nodes to the cluster. Make sure to label them with `type=kafka` and set `strimzi.kafka.replicas` accordingly when installing the Helm chart.


#### Option 3: Lightweight Setup

Run the following commands to install Theodolite with a minimal configuration (e.g., only one Kafka broker):

```sh
git clone https://github.com/cau-se/theodolite.git
helm dependencies update theodolite/helm
helm install theodolite theodolite/helm -f https://raw.githubusercontent.com/cau-se/theodolite/main/helm/preconfigs/minimal.yaml -f installation/values.yaml
```

The installation may take some time. Before continuing, make sure all Pods are up and running.

### Install ShuffleBench Benchmarks

Theodolite automates benchmarking in Kubernetes by [Kubernetes CRDs](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) for benchmarks and their executions. ShuffleBench provides one benchmark for each evaluated framework. To install them, run the following commands:

```sh
# Delete configmaps if already created before
kubectl delete configmaps --ignore-not-found=true shufflebench-resources-load-generator shufflebench-resources-latency-exporter shufflebench-resources-kstreams shufflebench-resources-hzcast shufflebench-resources-flink shufflebench-resources-spark 
kubectl create configmap shufflebench-resources-load-generator --from-file shufflebench/kubernetes/shuffle-load-generator/
kubectl create configmap shufflebench-resources-latency-exporter --from-file shufflebench/kubernetes/shuffle-latency-exporter/
kubectl create configmap shufflebench-resources-kstreams --from-file shufflebench/kubernetes/shuffle-kstreams/
kubectl create configmap shufflebench-resources-hzcast --from-file shufflebench/kubernetes/shuffle-hzcast/
kubectl create configmap shufflebench-resources-flink --from-file shufflebench/kubernetes/shuffle-flink/
kubectl create configmap shufflebench-resources-spark --from-file shufflebench/kubernetes/shuffle-sparkStructuredStreaming/

kubectl apply -f shufflebench/kubernetes/theodolite-benchmark-kstreams.yaml
kubectl apply -f shufflebench/kubernetes/theodolite-benchmark-hzcast.yaml
kubectl apply -f shufflebench/kubernetes/theodolite-benchmark-flink.yaml
kubectl apply -f shufflebench/kubernetes/theodolite-benchmark-spark.yaml
```

You can check whether all benchmarks are ready to run with:

```sh
kubectl get benchmarks
```


### Run Benchmarks

To run the installed benchmarks, Theodolite *Execution* YAML file have to be deployed.
In the following, we describe how to run the same experiments as in our study as well as how to run experiments with a lightweight setup.

*See the Theodolite documentation for further information on how to observe the benchmark execution.*

#### Option 1: Repeat Same Experiments as in Publication

This option only makes sense if you have set up the same or a similar cluster as in our study. Otherwise, the benchmark execution might not start or even crash due to too high load.

All Execution YAML files for the experiments of our study are located in `executions/`.
For example, to repeat the baseline Kafka Streams ad-hoc throughput experiment, run:

```sh
kubectl apply -f executions/baseline-adhoc-throughput/kstreams-baseline-atp.yaml
```

This experiment runs with 3 repetitions, each for 15 minutes, resulting in about 45 minutes execution time. To get the current status of the benchmark execution, run:

```sh
kubectl get executions
```

To repeat the other experiments, deploy the corresponding Execution YAML files, for example:

```sh
kubectl apply -f executions/baseline-adhoc-throughput/flink-baseline-atp.yaml
kubectl apply -f executions/baseline-latency/kstreams-baseline-lty.yaml
kubectl apply -f executions/multicore-adhoc-throughput/kstreams-3cores-atp.yaml
# ...
```

#### Option 2: Run Experiments Adjusted for Lightweight Setup

If you have set up a lightweight cluster in the previous steps or just want to run short experiments (without statistical significant results), you should adjust the Execution YAML files from [Option 1](#option-1-repeat-same-experiments-as-in-publication). We provide examples for such adjusted Execution YAML files for the baseline evaluation in `example-executions/`.

For example, to conduct the Kafka Streams ad-hoc throughput experiment with only 3 instances and lower generated load, run:

```sh
kubectl apply -f example-executions/3instances/adhoc-throughput/kstreams-atp.yaml
```

For an even more trimmed deployment (suitable for a local execution), run:

```sh
kubectl apply -f example-executions/minimal/adhoc-throughput/kstreams-atp.yaml
```

These experiments run for about 10 minutes. To get the current status of the benchmark execution, run:

```sh
kubectl get executions
```


### Retrieve Benchmark Measurement Data

The measurement data from our study are provided in `results/`. We use these data for the following analysis and visualization.

To retrieve the measurement data from your own experiments, you have to copy them from the Theodolite container (or volume) to your local machine. To copy them to `results-local/`, run:

```sh
mkdir -p results-local
kubectl cp $(kubectl get pod -l app=theodolite -o jsonpath="{.items[0].metadata.name}"):results results-local -c results-access
```


### Analyze Benchmark Results

In the following, we describe the analysis with the results of our study's experiments, which are provided in `results/`. To analyze your own results, which you might have retrieved in the previous step, adjust the analysis notebooks accordingly.

To analyze and visualize benchmark results, we provide a Jupyter notebook. It requires a Python 3.10 installation with some libraries.
We recommend to create a virtual environment and install the required dependencies there:

```sh
cd analysis
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

(Depending on your setup, you might have to use `python3` and install pip or the venv module.)

The Jupyter notebook `results-analysis.ipynb` can be opened and executed with, for example, VS Code. If you created a virtual environment, make sure to select it as Python interpreter/kernel!



## Custom Performance Evaluations with ShuffleBench

ShuffleBench is maintained as an [open-source project at GitHub](https://github.com/dynatrace-research/ShuffleBench). It provides benchmark implementations for different state-of-the-art stream processing frameworks as well as associated tooling to automate running benchmarks in Kubernetes-based cloud environments. Documentation on how to [build and package the implementations from source](https://github.com/dynatrace-research/ShuffleBench/blob/main/README.md) and [how to run custom implementations](https://github.com/dynatrace-research/ShuffleBench/tree/main/kubernetes) is provided in the GitHub project.
