def call(String destination = null) {
 if (destination == null) {
  destination = '.'
 }
 withCredentials([
    string(credentialsId: 'archon-account', variable: 'ARCHON_ACCOUNT'),
    string(credentialsId: 'archon-api-key', variable: 'ARCHON_API_KEY')
  ]) {
  sh """
    BUNDLE_URL="https://bundle.archon.sh/archon-bundle.tar.gz"
    curl -u "\$ARCHON_ACCOUNT:\$ARCHON_API_KEY" -L "\$BUNDLE_URL" -o archon-bundle.tar.gz

    tar -xzf archon-bundle.tar.gz -C ${destination}
  """
  }
}
