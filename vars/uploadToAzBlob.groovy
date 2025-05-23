def call(Map args) {
 def fileToUpload = args.file
 def destination = args.destination
 def container = args.container ?: env.MONITORING_ARTIFACT_CONTAINER
 def account = args.account ?: env.MONITORING_ARTIFACT_ACCOUNT
 def overwrite = args.overwrite ?: true

 if (!fileToUpload) {
  error 'File to upload is not specified'
 }

 sh """
  az storage blob upload --file ${fileToUpload} \
   --container-name ${container} \
   --account-name ${account} \
   --name ${destination} \
   --auth-mode login ${overwrite ? '--overwrite' : ''}
 """
}
