/**
Required Environment Variables:
OAUTH_WELL_KNOWN: The OAuth well-known configuration endpoint
WORKBENCH_URL: Base URL of the Workbench API

Required Jenkins Credentials:
OAUTH_CLIENT: Username/password credential containing client ID and secret

Example Usage:
cancelWorkbench([
    threadId: 'thread-uuid-here'
])

This function cancels a pending approval request in Workbench.

Required plugins:
- HTTP Request
- Pipeline Utility Steps
*/

import sh.archon.workbench.WorkbenchUtils

def call(Map config = [:]) {
 // Create utility instance
 def utils = new WorkbenchUtils(this)

 // Required parameters
 def threadId = config.threadId
 def workbenchUrl = config.workbenchUrl ?: env.WORKBENCH_URL

 // Validate required parameters
 if (!threadId) {
  error 'threadId is required'
 }
 if (!workbenchUrl) {
  error 'workbenchUrl is required (can be set via WORKBENCH_URL env var)'
 }

 echo "Cancelling Workbench approval thread: ${threadId}"

 // Get OAuth token
 def accessToken = utils.getAccessToken()

 try {
  // Call the cancel approval endpoint
  def cancelResponse = httpRequest(
            url: "${workbenchUrl}/api/v1/threads/${threadId}/actions/cancelApproval",
            httpMode: 'POST',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            customHeaders: [[name: 'Authorization', value: "Bearer ${accessToken}"]],
            validResponseCodes: '200,201,204'
        )

  echo "Approval thread ${threadId} cancelled successfully"

  // Return cancellation information
  return [
            threadId: threadId,
            cancelled: true,
            status: cancelResponse.status
        ]
    } catch (Exception e) {
  echo "Error cancelling approval thread: ${e.message}"
  throw e
 }
}
