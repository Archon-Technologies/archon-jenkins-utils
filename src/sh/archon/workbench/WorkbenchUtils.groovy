package sh.archon.workbench

/**
 * Utility class for Workbench integration
 */
class WorkbenchUtils implements Serializable {

 private def steps

 WorkbenchUtils(steps) {
  this.steps = steps
 }

    /**
     * Helper function to get OAuth access token
     */
 def getAccessToken() {
  def wellKnownUrl = steps.env.OAUTH_WELL_KNOWN
  if (!wellKnownUrl) {
   steps.error 'OAUTH_WELL_KNOWN environment variable is required'
  }

  return steps.withCredentials([steps.usernamePassword(
            credentialsId: 'OAUTH_CLIENT',
            usernameVariable: 'CLIENT_ID',
            passwordVariable: 'CLIENT_SECRET'
        )]) {
   steps.echo 'Fetching OAuth configuration from well-known endpoint...'
   def wellKnownResponse = steps.httpRequest(
                url: wellKnownUrl,
                httpMode: 'GET',
                acceptType: 'APPLICATION_JSON'
            )
   def wellKnownConfig = steps.readJSON(text: wellKnownResponse.content)
   def tokenEndpoint = wellKnownConfig.token_endpoint

   steps.echo 'Requesting access token...'
   def tokenResponse = steps.httpRequest(
                url: tokenEndpoint,
                httpMode: 'POST',
                acceptType: 'APPLICATION_JSON',
                contentType: 'APPLICATION_FORM',
                requestBody: "grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}",
                validResponseCodes: '200'
            )
   def tokenData = steps.readJSON(text: tokenResponse.content)
   return tokenData.access_token
        }
 }

    /**
     * Function to look up resources by name
     */
 def lookupResourceByName(String resourceType, String name) {
  def workbenchUrl = steps.env.WORKBENCH_URL
  if (!workbenchUrl) {
   steps.error 'WORKBENCH_URL environment variable is required'
  }

  steps.echo "Looking up ${resourceType}: ${name}"

  // Get OAuth token
  def accessToken = getAccessToken()

  try {
   // Query resources by name
   def resourcesResponse = steps.httpRequest(
                url: "${workbenchUrl}/api/v1/${resourceType}?searchTerm=${URLEncoder.encode(name, 'UTF-8')}",
                httpMode: 'GET',
                acceptType: 'APPLICATION_JSON',
                customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
                validResponseCodes: '200'
            )

   def resourcesResponseData = steps.readJSON(text: resourcesResponse.content)
   def resources = resourcesResponseData.data

   if (resources.size() == 0) {
    steps.echo "No ${resourceType} found with name: ${name}"
    return null
            } else if (resources.size() > 1) {
    steps.echo "Warning: Multiple ${resourceType} found with name: ${name}, returning first match"
   }

   def resource = resources[0]

   steps.echo "Found ${resourceType}: ${resource.name} (ID: ${resource.id})"
   return resource
        } catch (Exception e) {
   steps.echo "Error looking up ${resourceType}: ${e.message}"
   throw e
  }
 }

}
