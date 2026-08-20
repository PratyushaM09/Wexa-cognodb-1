param(
  [string]$Config = "config/platforms.properties",
  [string]$Platform = "cognodb"
)

mvn -q -DskipTests package
java -jar target/cognodb-cloud-benchmark-0.1.0.jar sample --edges data/smoke-edges.csv --nodes 1000 --relationships 5000
java -jar target/cognodb-cloud-benchmark-0.1.0.jar all --config $Config --platform $Platform --edges data/smoke-edges.csv --iterations 20 --warmup 5 --concurrency 4 --durationSeconds 10 --out results/raw

