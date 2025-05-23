def getCliData(GString command) {
 // is this an az command?
 if (!command.startsWith('az')) {
  error "Command is not an az command: ${command}"
 }

 // ensure we return JSON
 if (!command.contains('-o json')) {
  command += ' -o json'
 }

 def json = sh(
   script: command,
   returnStdout: true
 ).trim()
 return readJSON(text: json)
}

def connectToAks(String subscriptionId, String rg, String name, String kubeconfig) {
 sh """
 az aks get-credentials \
   --subscription ${subscriptionId} \
   --resource-group ${rg} \
   --name ${name} \
   --file ${kubeconfig} \
   --overwrite-existing

 kubelogin convert-kubeconfig -l azurecli
 """
}
