def call(Map params) {
 def subscriptionId = params.get('subscriptionId', null)
 def rg = params.resourceGroup
 def name = params.clusterName
 def kubeconfig = params.get('kubeconfig', null)
 def skipKubelogin = params.get('skipKubelogin', false)

 if (!rg || !name) {
  error 'Missing required parameters: resourceGroup or clusterName'
 }

 def azCmd = 'az aks get-credentials'
 if (subscriptionId) {
  azCmd += " --subscription ${subscriptionId}"
 }
 azCmd += " --resource-group ${rg} --name ${name}"
 if (kubeconfig) {
  azCmd += " --file ${kubeconfig}"
 }
 azCmd += ' --overwrite-existing'

 echo azCmd
 sh azCmd

 if (!skipKubelogin) {
  def klCommand = 'kubelogin convert-kubeconfig -l azurecli'
  if (kubeconfig) {
   klCommand = "KUBECONFIG=${kubeconfig} ${klCommand}"
  }
  echo klCommand
  sh klCommand
 }
}
