def call(Map args) {
 def fileToUpload = args.file
 def destination = args.destination
 def container = args.container ?: env.MONITORING_ARTIFACT_CONTAINER
 def account = args.account ?: env.MONITORING_ARTIFACT_ACCOUNT
 def overwrite = args.overwrite ?: true

 if (!fileToUpload) {
  error 'File to upload is not specified'
 }

 def json = sh(script: """
  az storage blob upload --file ${fileToUpload} \
   --container-name ${container} \
   --account-name ${account} \
   --name ${destination} \
   --auth-mode login ${overwrite ? '--overwrite' : ''}
 """, returnStdout: true).trim()

 if (json) {
  try {
   return readJSON(text: json)
  } catch (e) {
   error "Failed to parse JSON response: ${e.message}\nResponse: ${json}"
  }
 } else {
  error 'No JSON response received from Azure CLI upload command. Continuing with null return.'
  return null
 }
}
