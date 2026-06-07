name: Build Mod JAR

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Grant Gradle permission
        run: chmod +x gradlew

      - name: Build JAR
        run: ./gradlew build

      - name: Upload JAR
        uses: actions/upload-artifact@v4
        with:
          name: PYES-Inventory-Layout-JAR
          path: build/libs/PYES-Inventory-Layout-*.jar
