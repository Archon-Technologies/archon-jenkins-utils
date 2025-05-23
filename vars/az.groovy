def loginWithWorkloadId() {
 echo 'Logging in with workload identity'
 sh '''
   set +x
   az login --federated-token "$(cat $AZURE_FEDERATED_TOKEN_FILE)" \
     --service-principal -u $AZURE_CLIENT_ID -t $AZURE_TENANT_ID
   set -x
 '''
}
