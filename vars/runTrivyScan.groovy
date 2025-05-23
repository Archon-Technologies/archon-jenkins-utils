def call(Map m) {
 def input = m.file
 def img = m.img
 def output = m.output

 if (!(img || input) || !output) {
  error 'Missing required parameters: (either img or file) and output'
 }

 def trivyParams = [
  '--offline-scan',
  '--skip-db-update',
  '--cache-dir /root/.cache/trivy',
  '--format json',
  "--output ${output}",
  '--exit-code 1'
 ]

 if (img) {
  //add image to beginning of trivy command
  trivyParams.add(0, img)
 } else if (input) {
  //add input to beginning of trivy command
  trivyParams.add("--input ${input}")
 }

 return sh(
   script: "trivy image ${trivyParams.join(' ')}",
   returnStatus: true
 )
}
