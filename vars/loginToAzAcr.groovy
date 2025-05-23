def call() {
 sh """
   echo "Logging into ACR..."
   set +x
   REPO_USER="00000000-0000-0000-0000-000000000000"
   REPO_TOKEN=\$(az acr login --name ${env.INTERNAL_REGISTRY} --expose-token \
                --output tsv --query accessToken)
   echo "\$REPO_TOKEN" | cosign login "${env.INTERNAL_REGISTRY}" \
     -u "\$REPO_USER" --password-stdin
   echo "\$REPO_TOKEN" | skopeo login "${env.INTERNAL_REGISTRY}" \
     -u "\$REPO_USER" --password-stdin
   set -x
 """
}
