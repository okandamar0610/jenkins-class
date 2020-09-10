// Uniq name for the pod or slave 
def k8slabel = "jenkins-pipeline-${UUID.randomUUID().toString()}"
def branch = "${scm.branches[0].name}".replaceAll(/^\*\//, '')
def gitCommitHash = " "

properties([
    [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false], 
    parameters([
        booleanParam(defaultValue: false, description: 'Select the be able to push the latest', name: 'pushLatest')
        ])
    ])


// yaml def for slaves 
def slavePodTemplate = """
      metadata:
        labels:
          k8s-label: ${k8slabel}
        annotations:
          jenkinsjoblabel: ${env.JOB_NAME}-${env.BUILD_NUMBER}
      spec:
        affinity:
          podAntiAffinity:
            requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                - key: component
                  operator: In
                  values:
                  - jenkins-jenkins-master
              topologyKey: "kubernetes.io/hostname"
        containers:
        - name: docker
          image: docker:latest
          imagePullPolicy: IfNotPresent
          command:
          - cat
          tty: true
          volumeMounts:
            - mountPath: /var/run/docker.sock
              name: docker-sock
        serviceAccountName: default
        securityContext:
          runAsUser: 0
          fsGroup: 0
        volumes:
          - name: docker-sock
            hostPath:
              path: /var/run/docker.sock
    """
    podTemplate(name: k8slabel, label: k8slabel, yaml: slavePodTemplate, showRawYaml: false) {
      node(k8slabel) {

          stage("Checkout SCM"){
              checkout scm 
              gitCommitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
          }
          dir('deployments/docker') {
              container('docker'){
                withCredentials([usernamePassword(credentialsId: 'docker-creds', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                  
                    stage("Docker build"){  
                        sh 'docker build -t artemis .'
                    }

                    stage("Docker Login"){
                        sh "docker login --username $USERNAME --password $PASSWORD "
                    }

                    stage("Docker Push"){ 

                        if (params.pushLatest) {

                            println('Pushing the latest version')
                            sh "docker tag artemis okandamar0610/artemis:latest"
                            sh "docker push okandamar0610/artemis:latest"
                        }
                    
                        sh "docker tag artemis okandamar0610/artemis:${gitCommitHash}"
                        sh "docker push okandamar0610/artemis:${gitCommitHash}"
                    }
                }
             }

        }

      }
    }