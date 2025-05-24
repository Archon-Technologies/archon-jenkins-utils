def call(Map m) {
 // this is stdout from terraform apply
 def output = m.output

 // this is every AzureAD error that looks like this:
 /*
 Error: A resource with the ID "/subscriptions/4c896e3b-fe32-46a8-a931-0baff63f5a8d/resourceGroups/charlie-rg-wl/providers/Microsoft.ContainerService/managedClusters/charlie-cluster" already exists - to be managed via Terraform this resource needs to be imported into the State. Please see the resource documentation for "azurerm_kubernetes_cluster" for more information.

  with module.charlie_wl.azurerm_kubernetes_cluster.primary-aks,
  on .terraform/modules/charlie_wl/kube.tf line 70, in resource "azurerm_kubernetes_cluster" "primary-aks":
  70: resource "azurerm_kubernetes_cluster" "primary-aks" {
  */

 def pattern = java.util.regex.Pattern.compile('Error: A resource with the ID "(.*)" already exists.*\\n\\n.*with ([a-zA-Z0-9\\-\\_\\.]+),')
 def objectsToImport = []

 def matcher = output =~ pattern
 while (matcher.find()) {
  def resourceId = matcher.group(1)
  def terraformName = matcher.group(2)

  objectsToImport << [
    resourceId: resourceId,
    terraformName: terraformName
  ]
 }

 return objectsToImport
}
