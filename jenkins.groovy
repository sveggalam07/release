podTemplate(yaml: '''
  apiVersion: v1
  kind: Pod
  spec:
    serviceAccountName: ci-unittest-env-vars-read-sa
    containers:
      - name: common
        image: 341118756977.dkr.ecr.ap-south-1.amazonaws.com/common-alpine:3.16
        imagePullPolicy: Always
        command:
          - sleep
        args:
          - 99d
        env:
          - name: GIT_PAT_READ
            valueFrom:
              secretKeyRef:
                name: jenkins-agent-secret
                key: GIT_PAT_READ
          - name: GIT_PAT_WRITE
            valueFrom:
              secretKeyRef:
                name: jenkins-agent-secret
                key: GIT_PAT_WRITE
          - name: GIT_USER
            value: bot_user
          - name: GIT_EMAIL
            value: bot_mail
    volumes:
      - name: dind-storage
        emptyDir: {}
      - name: dind-certs
        emptyDir: {}
''') {
  
  node(POD_LABEL){
    properties([
      parameters([
        string(defaultValue: '', description: 'Name of the repo', name: 'SERVICE_NAME'),
        string(defaultValue: '', description: 'Release Branch Name', name: 'RELEASE_BRANCH'),
        string(defaultValue: '', description: 'GITHUB_ORG', name: 'GITHUB_ORG'),
        string(defaultValue: '', description: 'Slack_Channel', name: 'SLACK_CHANNEL'),
        ])
    ])
    env.SERVICE_NAME = params.SERVICE_NAME
    env.SLACK_CHANNEL = params.SLACK_CHANNEL
    env.GITHUB_ORG = params.GITHUB_ORG
    env.RELEASE_BRANCH = params.RELEASE_BRANCH
    env.WORK_SPACE = pwd()

    buildName "${SERVICE_NAME}_#${env.BUILD_NUMBER}"

    try {
      stage('Init') {
        try {
          container('common') {
            env.GIT_PAT_READ = sh (script: 'echo "$GIT_PAT_READ"', returnStdout: true).trim()
            env.GIT_PAT_WRITE = sh (script: 'echo "$GIT_PAT_WRITE"', returnStdout: true).trim()
            env.GIT_USER = sh (script: 'echo "$GIT_USER"', returnStdout: true).trim()
            env.GIT_EMAIL = sh (script: 'echo "$GIT_EMAIL"', returnStdout: true).trim()

            sh '''
              #git config --global url."https://${env.GIT_PAT}@github.com/".insteadOf "git@github.com:"
              git clone https://"${GIT_PAT_READ}"@github.com/"${GITHUB_ORG}"/"${SERVICE_NAME}"
              cd "${SERVICE_NAME}"
              git checkout main
              #git remote add origin git@github.com:"${GITHUB_ORG}"/"${SERVICE_NAME}".git
              git checkout -b "${RELEASE_BRANCH}"

              git config --global user.email "${GIT_EMAIL}"
              git config --global user.name "${GIT_USER}"

              git push https://"${GIT_PAT_WRITE}"@github.com/"${RELEASE_BRANCH}"/"${RELEASE_BRANCH}"
              #git push origin "${RELEASE_BRANCH}":"${RELEASE_BRANCH}"
            '''
          }
        } catch (e) {
          throw e
        }
      }
    } catch(e) {
    //   stage('Sending Error Notification') {
    //     container('common'){
    //       commonUtil.sendPreCommitBuildFailEmail(SERVICE_NAME,env.JOB_NAME,env.BUILD_NUMBER,emailList,env.BUILD_URL,AWS_REGION,PR_NUMBER,failedStageName)
    //     }
    //     commonUtil.sendPreCommitBuildFailNotificationToSlack(SLACK_CHANNEL, SERVICE_NAME, env.BUILD_NUMBER, env.BUILD_URL, PR_NUMBER)
    //   }
      throw e
    }
  }
}
