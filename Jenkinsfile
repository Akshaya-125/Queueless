// QueueLess CI/CD pipeline for a Jenkins running on WINDOWS (uses bat / PowerShell).
// A Linux version (for the Jenkins-in-Docker setup) is kept in jenkins/Jenkinsfile.linux.
//
// Needed on the machine/account that runs Jenkins: Git, JDK 17+, Docker Desktop (docker on PATH).
// Jenkins credentials:
//   queueless-env-file        (Secret file)       your real .env
//   registry-credentials      (Username/Password) only when PUSH_IMAGES is ticked
//   queueless-admin-password  (Secret text)       only when RUN_UI_TESTS is ticked
pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        githubPush()          // needs a GitHub webhook; "Build Now" works without it
    }

    parameters {
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy to the dev environment with Docker Compose')
        booleanParam(name: 'PUSH_IMAGES', defaultValue: false, description: 'Push images to a registry (needs registry-credentials)')
        string(name: 'REGISTRY_HOST', defaultValue: 'docker.io', description: 'Registry host used for docker login')
        string(name: 'REGISTRY', defaultValue: 'docker.io/your-username', description: 'Registry/namespace used when pushing')
        booleanParam(name: 'RUN_UI_TESTS', defaultValue: false, description: 'Run Selenium tests (Chrome must be installed)')
        string(name: 'UI_BASE_URL', defaultValue: 'http://localhost:8090', description: 'Frontend URL (FRONTEND_PORT from .env)')
    }

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                bat 'git log -1 --oneline'
            }
        }

        stage('Tool check') {
            steps {
                bat 'java -version'
                bat 'docker version --format "client {{.Client.Version}}"'
                bat 'docker ps'
            }
        }

        stage('Backend: compile and test') {
            steps {
                dir('backend') {
                    bat 'mvnw.cmd -B -ntp clean verify'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Frontend: checks') {
            steps {
                bat 'powershell -NoProfile -ExecutionPolicy Bypass -File frontend\\check.ps1'
            }
        }

        stage('Build Docker images') {
            steps {
                bat 'docker build -t queueless-backend:%IMAGE_TAG% -t queueless-backend:latest backend'
                bat 'docker build -t queueless-frontend:%IMAGE_TAG% -t queueless-frontend:latest frontend'
                bat 'docker images queueless-backend'
                bat 'docker images queueless-frontend'
            }
        }

        stage('Publish images') {
            when { expression { return params.PUSH_IMAGES } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-credentials',
                                                  usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    // single quotes: cmd reads the secret from the environment, Groovy never interpolates it
                    bat 'echo %REG_PASS%| docker login %REGISTRY_HOST% -u %REG_USER% --password-stdin'
                    bat 'docker tag queueless-backend:%IMAGE_TAG% %REGISTRY%/queueless-backend:%IMAGE_TAG% && docker push %REGISTRY%/queueless-backend:%IMAGE_TAG%'
                    bat 'docker tag queueless-frontend:%IMAGE_TAG% %REGISTRY%/queueless-frontend:%IMAGE_TAG% && docker push %REGISTRY%/queueless-frontend:%IMAGE_TAG%'
                    bat 'docker logout %REGISTRY_HOST%'
                }
            }
        }

        stage('Deploy (dev)') {
            when { expression { return params.DEPLOY } }
            steps {
                withCredentials([file(credentialsId: 'queueless-env-file', variable: 'ENV_FILE')]) {
                    bat 'copy /Y "%ENV_FILE%" .env > nul'
                    bat 'docker compose up -d --build'
                }
            }
        }

        stage('Health check') {
            when { expression { return params.DEPLOY } }
            steps {
                bat 'powershell -NoProfile -ExecutionPolicy Bypass -File jenkins\\wait-healthy.ps1 queueless-mysql queueless-backend queueless-frontend'
            }
        }

        stage('Selenium UI tests') {
            when { expression { return params.RUN_UI_TESTS } }
            steps {
                withCredentials([string(credentialsId: 'queueless-admin-password', variable: 'ADMIN_PASSWORD')]) {
                    withEnv(["BASE_URL=${params.UI_BASE_URL}"]) {
                        dir('selenium-tests') {
                            bat 'mvnw.cmd -B -ntp test'
                        }
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'selenium-tests/target/surefire-reports/*.xml'
                }
            }
        }
    }

    post {
        always {
            bat 'if exist .env del /f /q .env'
        }
        success { echo 'Pipeline finished successfully.' }
        failure { echo 'Pipeline failed - read the first failing stage above.' }
    }
}
