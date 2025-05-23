def call() {
 checkout scmGit(branches: [[name: '*/main']], extensions: [cloneOption(depth: 3, noTags: false, reference: '', shallow: true)], userRemoteConfigs: [[url: env.SPECS_REPO]])
}
