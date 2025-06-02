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

import sh.archon.workbench.WorkbenchUtils

def call(Map config = [:]) {
 // Create utility instance
 def utils = new WorkbenchUtils(this)

 // Required parameters
 def jobName = config.jobName ?: env.JOB_NAME
 def buildNumber = config.buildNumber ?: env.BUILD_NUMBER
 def description = config.description ?: "Jenkins build ${jobName} #${buildNumber} requires approval"
 def requestedApprovers = config.requestedApprovers // Required: array of arrays of approvers
 def categoryName = config.categoryName // Required: category ID in Workbench
 def workbenchUrl = config.workbenchUrl ?: env.WORKBENCH_URL // Base URL of Workbench API

 def categoryId = utils.lookupResourceByName('categories', categoryName).id

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

 echo "Setting up Workbench approval for ${jobName} #${buildNumber}"

 // Step 1: Get OAuth token
 def accessToken = utils.getAccessToken()

 // Step 2: Register webhook and get webhook URL
 def webhookUrl = null
 def hook = registerWebhook()
 webhookUrl = hook.getURL()
 def webhookSecret = hook.getToken()
 echo "Webhook registered at: ${webhookUrl}"

 // Initialize threadId outside try block for cleanup access
 def threadId = null

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
  threadId = threadData.id
  echo "Approval thread created with ID: ${threadId}"

  // Step 4: Wait for webhook callback
  echo '\n\n===== [Awaiting Approval] ====='
  echo 'To take actions, visit: '
  echo "${workbenchUrl}/category/${categoryId}/thread/${threadId}"
  echo '=================================\n\n'

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
 } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
  // This is thrown when the build is manually aborted
  echo "Build was interrupted/cancelled: ${e.message}"
  // Re-throw to maintain the interrupted status
  throw e
 } catch (hudson.AbortException e) {
  // This can be thrown by various Jenkins operations
  echo "Build was aborted: ${e.message}"
  throw e
 } catch (InterruptedException e) {
  // Another type of interruption
  echo "Build was interrupted: ${e.message}"
  throw e
 } catch (Exception e) {
  echo "Error during approval process: ${e.message}"
  throw e
 } finally {
  // Optional: Add any cleanup that should always run
  echo 'Cleanup completed for approval request'
 }
}
