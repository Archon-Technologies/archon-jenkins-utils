def call(String subscriptionId, String rg, String name, String kubeconfig) {
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
