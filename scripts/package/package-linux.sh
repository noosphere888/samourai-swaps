./scripts/prepare/prepare-linux.sh
./gradlew clean
./gradlew bootJar
rm -rf atomic-swaps-gui-linux
jpackage --input app/build/libs/ \
  --name atomic-swaps-gui-linux \
  --main-jar app.jar \
  --main-class org.springframework.boot.loader.PropertiesLauncher \
  --type app-image