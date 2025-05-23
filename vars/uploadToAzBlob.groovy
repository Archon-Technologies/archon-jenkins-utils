def call(Map args) {
 def fileToUpload = args.file
 def destination = args.destination
 def container = args.container ?: env.MONITORING_ARTIFACT_CONTAINER
 def account = args.account ?: env.MONITORING_ARTIFACT_ACCOUNT
 def overwrite = args.overwrite ?: true

 if (!digest || !container || !account) {
  error 'Missing required parameters: digest, container, or account'
 }

 sh """
  az storage blob upload --file ${fileToUpload} \
   --container-name ${container} \
   --account-name ${account} \
   --name ${destination} \
   --auth-mode login ${overwrite ? '--overwrite' : ''}
 """
}
