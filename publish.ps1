lein clean
yarn install
npx shadow-cljs compile app
lein uberjar
$version = "1.0-" + (Get-Date -format "yyyy-MM-dd_HH-mm-ss")
$filename = "spicy-github_" + $version + ".jar"
$s3key = $filename
$s3location = "s3://elasticbeanstalk-us-west-2-445413863088/" + $s3key
aws s3 cp ./target/uberjar/spicy-github-0.1.0-SNAPSHOT-standalone.jar $s3location
aws elasticbeanstalk create-application-version --application-name SpicyGithubv3 --version-label $version --source-bundle S3Bucket="elasticbeanstalk-us-west-2-445413863088",S3Key=$s3key
aws elasticbeanstalk update-environment --application-name SpicyGithubv3 --environment-name SpicyGithubv3-env --version-label $version