#!/usr/bin/groovy

@Library(['github.com/indigo-dc/jenkins-pipeline-library']) _

def dockerhub_image_id = ''

pipeline {
    agent {
       label 'java-a4c'
    }
    
    environment {
        dockerhub_repo = "indigodatacloud/alien4cloud-deep"        
        docker_image_name = "automated_testing_alien4cloud-deep"
        mockorchestrator_port = 10443
    }

    stages {
        stage('Code fetching') {
            steps {
                checkout scm 
            }
        }

        stage('Style analysis') {
            steps {
                dir("indigodc-orchestrator-plugin") {
                    sh 'wget https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/google_checks.xml'
                    sh 'wget https://github.com/checkstyle/checkstyle/releases/download/checkstyle-8.13/checkstyle-8.13-all.jar'
                    sh 'java -jar checkstyle-8.13-all.jar -c google_checks.xml src/ -e src/test/ -e src/main/assembly/ -f xml -o checkstyle-result.xml'
                }
            }
            post {
                always {
                    CheckstyleReport('**/checkstyle-result.xml')
                }
            }
        }

        stage('Unit testing coverage') {
            steps {
                dir("$WORKSPACE/indigodc-orchestrator-plugin") {
                    MavenRun('clean test')
                }
            }
            post {
                always {
                    jacoco()
                }
            }
        }

        stage('Metrics gathering') {
            agent {
                label 'sloc'
            }
            steps {
                checkout scm
                dir("indigodc-orchestrator-plugin") {
                    SLOCRun()
                }
            }
            post {
                success {
                    dir("indigodc-orchestrator-plugin") {
                        SLOCPublish()
                    }
                }
            }
        }

        stage('Dependency check') {
            agent {
                label 'docker-build'
            }
            steps {
                checkout scm
                OWASPDependencyCheckRun("${env.WORKSPACE}/indigodc-orchestrator-plugin/src", project="alien4cloud-deep")
            }
            post {
                always {
                    OWASPDependencyCheckPublish()
                    HTMLReport('indigodc-orchestrator-plugin/src', 'dependency-check-report.html', 'OWASP Dependency Report')
                    deleteDir()
                }
            }
        }

        stage('Docker build') {
          when {
                anyOf {
                    branch 'master'
                    branch 'jenkins_integration_testing'
                    buildingTag()
                }
            }
            agent {
                label 'docker-build'
            }
            steps {
                checkout scm
                script {
                    dockerhub_image_id = DockerBuild(dockerhub_repo, env.BRANCH_NAME)
                    sh "echo 'docker image ID is ${dockerhub_image_id}'"
                }
            }
            post {
                failure {
                    DockerClean()
                }
//                always {
//                    cleanWs()
//                }
            }
        }

        stage('Functional testing') {
            agent {
                label 'docker-build'
            }
            steps {                
                dir("integration_testing/mockorchestrator") {
                  sh 'mvn clean install'
                }
                dir("integration_testing") {
                  sh 'curl --silent -X POST -k https://localhost:${mockorchestrator_port}/actuator/shutdown'
                  sh 'nohup java -jar mockorchestrator/target/mockorchestrator.war --server.port=${mockorchestrator_port} &'                  
                  sh 'while true; do c=`curl --silent -X GET -k https://localhost:${mockorchestrator_port}/ping`; echo "Waiting for mock orchestrator"; if [[ "OK" = ${c} ]] ; then echo "Mock orchestrator started"; break; fi; sleep 2; done'
                  sh "docker run -d --network="host" --name ${docker_image_name} -p 8088:8088 ${dockerhub_image_id}"
                  sh 'while true; do c=`docker logs ${docker_image_name} | grep -E "Started.*Bootstrap.*in"` ; if [[ ! -z ${c} ]] ; then echo "Alien4cloud started"; break; fi; sleep 2; done'
                  sh 'nodejs ui_a4c.js -h \'http://localhost:8088\' -u admin -p admin -t ./AutomatedApp.yml -s http://localhost:${mockorchestrator_port}'
                  sh 'curl --silent -X POST -k https://localhost:${mockorchestrator_port}/actuator/shutdown'
                }
            }
            post {
                always {
                  sh "docker kill ${docker_image_name}"
                  sh "docker rm ${docker_image_name}"
                  cleanWs()
                }

            }

        }

        stage('DockerHub delivery') {
            when {
                anyOf {
                    branch 'master'
                    buildingTag()
                }
            }
            steps {
                DockerPush(dockerhub_image_id)
            }
        }
        
        stage('Notifications') {
            when {
                buildingTag()
            }
            steps {
                JiraIssueNotification(
                    'DEEP',
                    'DPM',
                    '10204',
                    "[preview-testbed] New alien4cloud version ${env.BRANCH_NAME} available",
                    "Check new artifacts at:\n\t- Docker image: [${dockerhub_image_id}:${env.BRANCH_NAME}|https://hub.docker.com/r/${dockerhub_image_id}/tags/]\n",
                    ['wp3', 'preview-testbed', "alien4cloud-${env.BRANCH_NAME}"],
                    'Task',
                    'mariojmdavid'
                )
            }
        }
    }
}
