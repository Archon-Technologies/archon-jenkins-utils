def call(Map m) {
 def input = m.input
 def output = m.output

 if (!input || !output) {
  error 'Missing required parameters: input or output'
 }

 return sh(
   script: """
     trivy image \\
       --offline-scan \\
       --skip-db-update \\
       --cache-dir /root/.cache/trivy \\
       --input ${input} \\
       --format json \\
       --output ${output} \\
       --exit-code 1
   """.stripIndent(),
   returnStatus: true
 )
}
