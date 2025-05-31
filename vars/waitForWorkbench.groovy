import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

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

 // OAuth configuration
 def wellKnownUrl = env.OAUTH_WELL_KNOWN
 if (!wellKnownUrl) {
  error 'OAUTH_WELL_KNOWN environment variable is required'
 }

 // Generate a unique approval token for this build
 def approvalToken = UUID.randomUUID().toString()

 echo "Setting up Workbench approval for ${jobName} #${buildNumber}"

 // Step 1: Get OAuth token
 def accessToken = withCredentials([usernamePassword(
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
