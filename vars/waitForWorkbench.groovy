/**
Required Environment Variables:
OAUTH_WELL_KNOWN: The OAuth well-known configuration endpoint
WORKBENCH_URL: Base URL of the Workbench API

Required Jenkins Credentials:
OAUTH_CLIENT: Username/password credential containing client ID and secret

Example Usage:
waitForWorkbench([
    categoryName: 'deployment-approvals',
    requestedApprovers: [
        [ // Stage 1
            [type: 'user', email: 'lead@example.com'],
            [type: 'group', name: 'DevOps']
        ],
        [ // Stage 2
            [type: 'user', email: 'manager@example.com']
        ]
    ],
    description: 'Deploy build to production environment'
])

Required plugins:
- HTTP Request
- Webhook Step
- Pipeline Utility Steps
*/

// Helper function to get OAuth access token
def getAccessToken() {
 def wellKnownUrl = env.OAUTH_WELL_KNOWN
 if (!wellKnownUrl) {
  error 'OAUTH_WELL_KNOWN environment variable is required'
 }

 return withCredentials([usernamePassword(
  credentialsId: 'OAUTH_CLIENT',
  usernameVariable: 'CLIENT_ID',
  passwordVariable: 'CLIENT_SECRET'
 )]) {
  echo 'Fetching OAuth configuration from well-known endpoint...'
  def wellKnownResponse = httpRequest(
   url: wellKnownUrl,
   httpMode: 'GET',
   acceptType: 'APPLICATION_JSON'
  )
  def wellKnownConfig = readJSON(text: wellKnownResponse.content)
  def tokenEndpoint = wellKnownConfig.token_endpoint

  echo 'Requesting access token...'
  def tokenResponse = httpRequest(
   url: tokenEndpoint,
   httpMode: 'POST',
   acceptType: 'APPLICATION_JSON',
   contentType: 'APPLICATION_FORM',
   requestBody: "grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}",
   validResponseCodes: '200'
  )
  def tokenData = readJSON(text: tokenResponse.content)
  return tokenData.access_token
 }
}

// Function to look up groups by name
def lookupGroupByName(String groupName) {
 def workbenchUrl = env.WORKBENCH_URL
 if (!workbenchUrl) {
  error 'WORKBENCH_URL environment variable is required'
 }

 echo "Looking up group: ${groupName}"

 // Get OAuth token
 def accessToken = getAccessToken()

 try {
  // Query groups by name
  def groupsResponse = httpRequest(
   url: "${workbenchUrl}/api/v1/groups?name=${URLEncoder.encode(groupName, 'UTF-8')}",
   httpMode: 'GET',
   acceptType: 'APPLICATION_JSON',
   customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
   validResponseCodes: '200'
  )

  def groupsResponseData = readJSON(text: groupsResponse.content)
  def groups = groupsResponseData.data

  if (groups.size() == 0) {
   echo "No group found with name: ${groupName}"
   return null
  } else if (groups.size() > 1) {
   echo "Warning: Multiple groups found with name: ${groupName}, returning first match"
  }

  def group = groups[0]
  echo group

  echo "Found group: ${group.name} (ID: ${group.id})"
  return group
 } catch (Exception e) {
  echo "Error looking up group: ${e.message}"
  throw e
 }
}

def call(Map config = [:]) {
 // Required parameters
 def jobName = config.jobName ?: env.JOB_NAME
 def buildNumber = config.buildNumber ?: env.BUILD_NUMBER
 def description = config.description ?: "Jenkins build ${jobName} #${buildNumber} requires approval"
 def requestedApprovers = config.requestedApprovers // Required: array of arrays of approvers
 def categoryName = config.categoryName // Required: category ID in Workbench
 def workbenchUrl = config.workbenchUrl ?: env.WORKBENCH_URL // Base URL of Workbench API

 def categoryId = lookupGroupByName(categoryName).id

 // Validate required parameters
 if (!requestedApprovers) {
  error 'requestedApprovers is required'
 }
 if (!categoryId) {
  error 'categoryId is required'
 }
 if (!workbenchUrl) {
  error 'workbenchUrl is required (can be set via WORKBENCH_URL env var)'
 }

 // Generate a unique approval token for this build
 def approvalToken = UUID.randomUUID().toString()

 echo "Setting up Workbench approval for ${jobName} #${buildNumber}"

 // Step 1: Get OAuth token
 def accessToken = getAccessToken()

 // Step 2: Register webhook and get webhook URL
 def webhookUrl = null
 def hook = registerWebhook()
 webhookUrl = hook.getURL()
 def webhookSecret = hook.getSecret()
 echo "Webhook registered at: ${webhookUrl}"

 try {
  // Step 3: Create approval thread in Workbench

  def threadPayload = writeJSON(json: [
   title: "Jenkins Approval: ${jobName} #${buildNumber}",
   template: 'jenkins-approval',
   categoryId: categoryId,
   data: [
    jobName: jobName,
    buildNumber: buildNumber,
    description: description,
    webhookUrl: webhookUrl,
    approvalToken: webhookSecret,
    requestedApprovers: requestedApprovers
   ]
  ], returnText: true)

  echo 'Creating approval thread in Workbench...'
  def createThreadResponse = httpRequest(
   url: "${workbenchUrl}/api/v1/threads/templated",
   httpMode: 'POST',
   acceptType: 'APPLICATION_JSON',
   contentType: 'APPLICATION_JSON',
   customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
   requestBody: threadPayload,
   validResponseCodes: '200,201'
  )

  def threadResponseData = readJSON(text: createThreadResponse.content)
  def threadData = threadResponseData.data
  def threadId = threadData.id
  echo "Approval thread created with ID: ${threadId}"

  // Step 4: Wait for webhook callback
  echo 'Waiting for approval...'
  def webhookData = waitForWebhook(hook)

  // Parse webhook payload
  def webhookPayload = readJSON(text: webhookData)

  // Verify job details match
  if (webhookPayload.jobName != jobName || webhookPayload.buildNumber != buildNumber) {
   error 'Webhook callback does not match expected job details'
  }

  echo 'Approval received! Continuing build...'

  // Return thread information for downstream use
  return [
   threadId: threadId,
   approved: true,
   jobName: jobName,
   buildNumber: buildNumber
  ]
 } catch (Exception e) {
  echo "Error during approval process: ${e.message}"
  throw e
 }
}
