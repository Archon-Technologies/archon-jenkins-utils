def call(String script) {
 // Check if the script is empty
 if (script == null || script.trim().isEmpty()) {
  error 'Script is empty'
 }

 // Execute the script and capture the output
 def output = sh(
    script: script,
    returnStdout: true
  ).trim()

 // Parse the output as JSON
 def jsonOutput = readJSON(text: output)

 return jsonOutput
}
