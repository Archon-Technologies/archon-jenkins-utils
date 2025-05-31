import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

/**
Required Environment Variables:
OAUTH_WELL_KNOWN: The OAuth well-known configuration endpoint
WORKBENCH_URL: Base URL of the Workbench API
WORKBENCH_DEFAULT_CATEGORY_ID: (Optional) Default category for simplified usage

Required Jenkins Credentials:
OAUTH_CLIENT: Username/password credential containing client ID and secret

Example Usage:
waitForWorkbench([
    categoryId: 'deployment-approvals',
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

Or simply (with default category ID):
// Requires WORKBENCH_DEFAULT_CATEGORY_ID env var
waitForWorkbench([
    [type: 'user', email: 'approver@example.com'],
    [type: 'group', name: 'DevOps']
], 'Quick approval needed')
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
  def wellKnownConfig = new JsonSlurper().parseText(wellKnownResponse.content)
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
  def tokenData = new JsonSlurper().parseText(tokenResponse.content)
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
   url: "${workbenchUrl}/api/groups?name=${URLEncoder.encode(groupName, 'UTF-8')}",
   httpMode: 'GET',
   acceptType: 'APPLICATION_JSON',
   customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
   validResponseCodes: '200'
  )

  def groups = new JsonSlurper().parseText(groupsResponse.content)

  if (groups.size() == 0) {
   echo "No group found with name: ${groupName}"
   return null
        } else if (groups.size() > 1) {
   echo "Warning: Multiple groups found with name: ${groupName}, returning first match"
  }

  def group = groups[0]
  echo "Found group: ${group.name} (ID: ${group.id})"
  return group
    } catch (Exception e) {
  echo "Error looking up group: ${e.message}"
  throw e
 }
}

// Function to look up all groups matching a pattern
def lookupGroupsByPattern(String pattern) {
 def workbenchUrl = env.WORKBENCH_URL
 if (!workbenchUrl) {
  error 'WORKBENCH_URL environment variable is required'
 }

 echo "Looking up groups matching pattern: ${pattern}"

 // Get OAuth token
 def accessToken = getAccessToken()

 try {
  // Query all groups and filter by pattern
  def groupsResponse = httpRequest(
   url: "${workbenchUrl}/api/groups",
   httpMode: 'GET',
   acceptType: 'APPLICATION_JSON',
   customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
   validResponseCodes: '200'
  )

  def allGroups = new JsonSlurper().parseText(groupsResponse.content)
  def matchingGroups = allGroups.findAll { group ->
   group.name.matches(pattern)
  }

  echo "Found ${matchingGroups.size()} groups matching pattern"
  return matchingGroups
    } catch (Exception e) {
  echo "Error looking up groups: ${e.message}"
  throw e
 }
}

def call(Map config = [:]) {
 // Required parameters
 def jobName = config.jobName ?: env.JOB_NAME
 def buildNumber = config.buildNumber ?: env.BUILD_NUMBER
 def description = config.description ?: "Jenkins build ${jobName} #${buildNumber} requires approval"
 def requestedApprovers = config.requestedApprovers // Required: array of arrays of approvers
 def categoryId = config.categoryId // Required: category ID in Workbench
 def workbenchUrl = config.workbenchUrl ?: env.WORKBENCH_URL // Base URL of Workbench API

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
 echo "Webhook registered at: ${webhookUrl}"

 try {
  // Step 3: Create approval thread in Workbench
  def threadPayload = new JsonBuilder()
  threadPayload {
   title "Jenkins Approval: ${jobName} #${buildNumber}"
   template 'jenkins-approval'
   categoryId categoryId
   data {
    jobName jobName
    buildNumber buildNumber
    description description
    webhookUrl webhookUrl
    approvalToken approvalToken
    requestedApprovers requestedApprovers
   }
  }

  echo 'Creating approval thread in Workbench...'
  def createThreadResponse = httpRequest(
   url: "${workbenchUrl}/api/threads",
   httpMode: 'POST',
   acceptType: 'APPLICATION_JSON',
   contentType: 'APPLICATION_JSON',
   customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
   requestBody: threadPayload.toString(),
   validResponseCodes: '200,201'
  )

  def threadData = new JsonSlurper().parseText(createThreadResponse.content)
  def threadId = threadData.id
  echo "Approval thread created with ID: ${threadId}"

  // Step 4: Wait for webhook callback
  echo 'Waiting for approval...'
  def webhookData = waitForWebhook(hook)

  // Parse webhook payload
  def webhookPayload = readJSON(text: webhookData)

  // Verify the approval token matches
  if (webhookPayload.approvalToken != approvalToken) {
   error 'Invalid approval token received'
  }

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

// Overloaded method for simpler single-stage approvals
def call(List<Map> approvers, String description = null) {
 def config = [:]
 config.requestedApprovers = [approvers] // Single stage with provided approvers
 if (description) {
  config.description = description
 }

 // Look for default category ID in environment
 if (env.WORKBENCH_DEFAULT_CATEGORY_ID) {
  config.categoryId = env.WORKBENCH_DEFAULT_CATEGORY_ID
    } else {
  error 'WORKBENCH_DEFAULT_CATEGORY_ID environment variable is required for simplified call'
 }

 return call(config)
}
