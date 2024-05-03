cargo build --release
./scripts/prepare/prepare-windows.bat
./gradlew clean
./gradlew bootJar
rmdir atomic-swaps-gui-windows /s
%userprofile%\.jdks\jbrsdk-17.0.9\bin\jpackage.exe --input app/build/libs/ --name atomic-swaps-gui-windows --main-jar app.jar --main-class org.springframework.boot.loader.PropertiesLauncher --type app-image
# you might need to edit this yourself, or run each command individually