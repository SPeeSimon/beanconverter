pipeline {
  agent any
  stages {
    stage('Build configuration') {
      agent any
      steps {
        input(message: 'wait', ok: 'next')
        timeout(time: 30) {
          echo 'test'
        }

      }
    }
    stage('init') {
      agent {
        dockerfile {
          filename 'docker-compose.yaml'
        }

      }
      environment {
        Application = 'BAM'
      }
      steps {
        timeout(time: 30) {
          sleep 40
        }

      }
    }
  }
}