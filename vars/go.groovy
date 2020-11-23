@Library('JenkinsLibs')_
import com.vtspace.GlobalVars

call()
{
pipeline {
	agent {
		label "$JENKINS_SLAVE"
	}
	options { skipDefaultCheckout() }

    parameters {
        choice(
            choices: ['maikai', 'iwk2', 'magellan', 'ek', 'iwk', 'sandbox-us-east-1', 'dev-us-east-1', 'dev-us-west-2', 'qa-us-east-1', 'qa-us-west-2', 'staging-us-east-1', 'staging-us-west-2', 'prod-us-east-1', 'prod-us-west-2',],
            description: 'DC: Select DC name ',
            name: 'DC'
        )
        choice(
            choices: ['prodv2', 'staging', 'prod', 'sandbox', 'dev', 'devint', 'qa', 'staging', 'prod'],
            description: 'ENV: Select ENV name',
            name: 'ENV'
        )
		choice(
			choices: ['prod', 'staging', 'stage', 'dev'],
			description: 'BUILD_ENV: Select BUILD_ENV name',
			name: 'BUILD_ENV'
		)
		choice(
			choices: ['accountusermanager', 'license-consumer', 'apim', 'apisubscriptions', 'auth', 'batch', 'calendar', 'calendar-consumer', 'condition', 'confirmation', 'credentials', 'repair-tool', 'data', 'database', 'domain', 'inventory', 'libcreds', 'logsearch', 'middleware', 'nginx', 'nginxlocal', 'notification', 'observer', 'rest', 'servdisc', 'service_master', 'stats', 'target-consumer', 'trigger', 'tslog', 'mlog', 'twizzle-service', 'identity'],
			description: 'REPO: Select REPO To Compile',
			name: 'REPO'
		)
		string(description: 'Enter the GIT_PIPELINE_URL', name: 'GIT_PIPELINE_URL')
		string(description: 'Enter the GIT_PIPELINE_BRANCH', name: 'GIT_PIPELINE_BRANCH')
        string(description: 'Enter the GO_BUILDER_IMAGE, default is ".dkr.ecr.us-west-2.amazonaws.com/go-builder:1.13.4"', name: 'GO_BUILDER_IMAGE', defaultValue: 'dkr.ecr.us-west-2.amazonaws.com/go-builder:1.13.4')
        string(description: 'Enter the CONTACT Details', name: 'CONTACT')
        string(description: 'Enter the PLATFORM Details, default is "KUBE"', name: 'PLATFORM', defaultValue: 'KUBE')
        string(description: 'Enter the DCCONNECT_AUTH Details, default is #"', name: 'DCCONNECT_AUTH', defaultValue: #')
        string(description: 'Enter the CONTAINER_ID, default is "6e6f5fcfc73d"', name: 'CONTAINER_ID', defaultValue: '6e6f5fcfc73d')
        string(description: 'Enter the DOCKER_WORKSPACE, default is "/opt/slave_home/workspace/Verticals_AWS/TS/TS_AWS_DEPLOYMENT_PIPELINE/workspace"', name: 'DOCKER_WORKSPACE', defaultValue: '/opt/slave_home/workspace/Verticals_AWS/TS/TS_AWS_DEPLOYMENT_PIPELINE/workspace')
        string(description: 'Enter the Path of Cloned REPO, default is "${DOCKER_WORKSPACE}/go/src/stash.verizon.com/npdthing"', name: 'CLONED_REPO_PATH', defaultValue: '${DOCKER_WORKSPACE}/go/src/stash.verizon.com/npdthing')
        string(description: 'Enter the JENKINS_SLAVE, default is "docker"', name: 'JENKINS_SLAVE', defaultValue: 'docker')
        string(description: 'Enter the AWS Account Number, default is "349211140923"', name: 'AWS_ACCOUNT_NO', defaultValue: '349211140923')
        string(description: 'Enter the AWS Region, default is "us-east-1"', name: 'AWS_REGION', defaultValue: 'us-east-1')
        string(description: 'Enter the Service Name, default is "ts-npdthing-accountusermanager"', name: 'SERVICE_NAME', defaultValue: 'ts-npdthing-accountusermanager')
        string(description: 'Enter the Service Version, default is "ts-npdthing-accountusermanager-latest"', name: 'SERVICE_VERSION', defaultValue: 'ts-npdthing-accountusermanager-latest')
        string(description: 'DEPLOYMENT_JIRA_TICKET: Please mention the JIRA ticket of deployment.', name: 'DEPLOYMENT_JIRA_TICKET', defaultValue: 'TOTFT')
    }


	environment {
		GIT_URL = "${GIT_PIPELINE_URL}"
	    GIT_BRANCH = "${GIT_PIPELINE_BRANCH}"
	    JENKINS_SLAVE_CREDENTIALS = 'stash-deepthi'
        DOCKER_IMAGE_FILE = "${SERVICE_VERSION}"
        DOCKER_IMAGE_FILE_STASH_ID = "GO_PIPELINE"
		PIPELINE_WORKSPACE = "${WORKSPACE}"
        CLONED_REPO_BRANCH = sh (script: "sudo docker exec ${CONTAINER_ID} bash -c 'cd ${CLONED_REPO_PATH}/${REPO} && git status | head -1 | cut -d '/' -f2'", returnStdout: true ).trim()
	}

	stages {
		stage('VERIFY_PARAMETERS') {
			steps {
				sh """
				if [ -z ${DEPLOYMENT_JIRA_TICKET} ]; then
				echo "Parameter DEPLOYMENT_JIRA_TICKET can not be empty for this job"
				exit 1
				fi"""
			}
		}

	  // Code Checkout Stage
	    stage('Code: Checkout') {
	        steps {
	            // Perform a checkout
	            git branch: "${GIT_BRANCH}",
	            credentialsId: "${JENKINS_SLAVE_CREDENTIALS}",
	            url: "${GIT_URL}"
	        }
	    }//stage: Checkout

		stage ('COMPILE_CODE') {
			steps {
				script {
                    sh """
                    sudo docker exec ${CONTAINER_ID} bash -c "
                    set -xe
                    source ${DOCKER_WORKSPACE}/.envrc;
                    unset DOCKER_HOST
                    export CONTACT=${CONTACT} DC=${DC} ENV=${ENV} BUILD_ENV=${BUILD_ENV} DCCONNECT_AUTH=${DCCONNECT_AUTH} PLATFORM=${PLATFORM} TAG=${CLONED_REPO_BRANCH}
                    docker run -it -d -v ${DOCKER_WORKSPACE}:${DOCKER_WORKSPACE} -w ${CLONED_REPO_PATH}/${REPO} -e GOPATH=${DOCKER_WORKSPACE}/go:${DOCKER_WORKSPACE}/vendor-library:${DOCKER_WORKSPACE}/go:${DOCKER_WORKSPACE}/vendor-library -e CGO_ENABLED=0 -e GOOS=linux '${GO_BUILDER_IMAGE}'  go build -a -installsuffix cgo  -o ${REPO}"
                    sleep 60
                    """
				}
			}
		}

		stage ('BUILDING_DOCKER_IMAGE') {
            agent {label "${JENKINS_SLAVE}"}
			steps {
				script {
                    dir("${CLONED_REPO_PATH}/${REPO}") {
                    sh """
                    echo "Get Container ID"
                    CONTAINER_ID=`docker ps -a | grep ${GO_BUILDER_IMAGE} | head -1 | awk {'print \$1'}`
                    echo "CONTAINER_ID = \${CONTAINER_ID}"
                    echo "Creating Docker Image From Container"
                    docker commit \${CONTAINER_ID} ${REPO}
                    DOCKER_IMAGE_ID=`docker images | grep ${REPO} | head -1 | awk {'print \$3'}`
                    echo "DOCKER_IMAGE_ID = \${DOCKER_IMAGE_ID}"
                    cd ${CLONED_REPO_PATH}/${REPO}
                    docker image tag \${DOCKER_IMAGE_ID} ${REPO}:${CLONED_REPO_BRANCH}
                    docker images | grep ${CLONED_REPO_BRANCH} | head -1
                    docker save -o ${SERVICE_VERSION} \${DOCKER_IMAGE_ID}
                    ls -ltrh
                    pwd
                    """
                    stash includes:"${DOCKER_IMAGE_FILE}", name:"${DOCKER_IMAGE_FILE_STASH_ID}"
				}
			}
		}
    }
        
        stage('Unstash Docker Image And Tag') {
            agent {label "${JENKINS_SLAVE}"}
                steps {
                    unstash "${DOCKER_IMAGE_FILE_STASH_ID}"
                    sh """
                    ls -ltrh
                    pwd
                    docker load -i ${SERVICE_VERSION}
                    docker images | head -5
                    DOCKER_IMAGE_ID=`docker images | head -2 | tail -1 | awk {'print \$3'}`
                    echo "DOCKER_IMAGE_ID = \${DOCKER_IMAGE_ID}"
                    docker image tag \${DOCKER_IMAGE_ID} ${REPO}:${CLONED_REPO_BRANCH}
                    docker images | grep ${CLONED_REPO_BRANCH} | head -1
                    docker tag \${DOCKER_IMAGE_ID} ${AWS_ACCOUNT_NO}.dkr.ecr.${AWS_REGION}.amazonaws.com/${SERVICE_NAME}:${SERVICE_VERSION}
                    """  
            }
        }        

        stage('Push to ECR') {
            agent {label "${JENKINS_SLAVE}"}
                steps {
                    script {
                        dir("${PIPELINE_WORKSPACE}") {
                            if (env.ENV == "dev"){
                                sh """
                                ECR_DEV_LOGGING() {
                                    \$(aws --profile ${env.ENV} ecr get-login --no-include-email --region ${AWS_REGION})
                                }
                                ECR_DEV_LOGGING
                                """
                            }
                            else{
                                sh """
                                ECR_DEV_LOGGING() {
                                    \$(aws ecr get-login --no-include-email --region ${AWS_REGION}) 
                                }
                                ECR_DEV_LOGGING
                                """
                            }
                        }
                    }
                sh """
                docker push ${AWS_ACCOUNT_NO}.dkr.ecr.${AWS_REGION}.amazonaws.com/${SERVICE_NAME}:${SERVICE_VERSION}
                """
            }
        }              
	}
	post {
        success {
            script {
                currentBuild.result = 'SUCCESS'
            }
			this.notifyEmail()
        }
        failure {
            script {
                currentBuild.result = 'FAILURE'
            }
			this.notifyEmail()
        }
	}
}

def notifyEmail()
{
	archiveArtifacts '**/*.log'

	emailext body: '''$DEFAULT_CONTENT

	
	subject: "'${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}!  - DC (${env.DC}) - ENV(${env.ENV}) - RELEASE_BRANCH(${env.CLONED_REPO_BRANCH})'",

}
}