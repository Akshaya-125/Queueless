// QueueLess CI/CD pipeline (declarative).
// Required Jenkins credentials (Manage Jenkins > Credentials), see README "Jenkins":
//   queueless-env-file   (Secret file)   the contents of your .env
//   registry-credentials (Username/Password)   only needed when PUSH_IMAGES is ticked
pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        githubPush()          // needs the GitHub webhook; use "Build Now" for a purely local setup
    }

    parameters {
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy to the dev environment with Docker Compose')
        booleanParam(name: 'PUSH_IMAGES', defaultValue: false, description: 'Push images to a registry (needs registry-credentials)')
        string(name: 'REGISTRY', defaultValue: 'docker.io/your-username', description: 'Registry/namespace used when pushing')
        booleanParam(name: 'RUN_UI_TESTS', defaultValue: false, description: 'Run Selenium tests (agent needs Chrome installed)')
        string(name: 'UI_BASE_URL', defaultValue: 'http://host.docker.internal:8080', description: 'Where the deployed frontend is reachable from the agent')
    }

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Backend: compile and test') {
            steps {
                dir('backend') {
                    sh 'mvn -B -ntp clean verify'
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
                sh 'sh frontend/check.sh'
            }
        }

        stage('Build Docker images') {
            steps {
                sh 'docker build -t queueless-backend:${IMAGE_TAG} -t queueless-backend:latest backend'
                sh 'docker build -t queueless-frontend:${IMAGE_TAG} -t queueless-frontend:latest frontend'
                sh 'docker images | grep queueless'
            }
        }

        stage('Publish images') {
            when { expression { return params.PUSH_IMAGES } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-credentials',
                                                  usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    // single-quoted: the secret is read from the environment by the shell, never interpolated by Groovy
                    sh '''
                        echo "$REG_PASS" | docker login "${REGISTRY%%/*}" -u "$REG_USER" --password-stdin
                        for app in backend frontend; do
                          docker tag queueless-$app:${IMAGE_TAG} ${REGISTRY}/queueless-$app:${IMAGE_TAG}
                          docker push ${REGISTRY}/queueless-$app:${IMAGE_TAG}
                        done
                        docker logout "${REGISTRY%%/*}"
                    '''
                }
            }
        }

        stage('Deploy (dev)') {
            when { expression { return params.DEPLOY } }
            steps {
                withCredentials([file(credentialsId: 'queueless-env-file', variable: 'ENV_FILE')]) {
                    sh '''
                        cp "$ENV_FILE" .env
                        docker compose up -d --build
                    '''
                }
            }
        }

        stage('Health check') {
            when { expression { return params.DEPLOY } }
            steps {
                sh '''
                    for c in queueless-mysql queueless-backend queueless-frontend; do
                      echo "Waiting for $c to become healthy..."
                      ok=0
                      for i in $(seq 1 40); do
                        status=$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null || echo missing)
                        if [ "$status" = "healthy" ]; then ok=1; break; fi
                        sleep 5
                      done
                      if [ "$ok" -ne 1 ]; then
                        echo "$c did not become healthy (last status: $status)"
                        docker logs --tail 50 "$c" || true
                        exit 1
                      fi
                      echo "$c is healthy"
                    done
                '''
            }
        }

        stage('Selenium UI tests') {
            when { expression { return params.RUN_UI_TESTS } }
            steps {
                withCredentials([file(credentialsId: 'queueless-env-file', variable: 'ENV_FILE')]) {
                    sh '''
                        set -a; . "$ENV_FILE"; set +a
                        cd selenium-tests
                        BASE_URL="$UI_BASE_URL" mvn -B -ntp test
                    '''
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
            sh 'rm -f .env || true'
        }
        success { echo 'Pipeline finished successfully.' }
        failure { echo 'Pipeline failed - read the first failing stage above.' }
    }
}
