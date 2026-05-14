# Project Structure Overview

## Summary
This repository is a Java-based command-line Task Manager application built with Gradle. It follows standard Gradle/Java conventions and uses a package namespace under `za.co.wethinkcode.taskmanager`.

## Key Configuration Files
- `build.gradle.kts` - Gradle build script written in Kotlin DSL.
- `settings.gradle.kts` - Gradle settings file defining the root project.
- `gradlew` / `gradlew.bat` - Gradle wrapper scripts for Unix and Windows.
- `.gitignore` - Files and directories excluded from Git.
- `README.md` - Build and usage instructions.
- `tasks.json` - Application data storage file used at runtime to persist tasks.

## Technology Stack
- Java
- Gradle (build tool)
- Apache Commons CLI (`commons-cli`) for parsing command-line arguments
- Gson (`com.google.code.gson:gson`) for JSON serialization/deserialization
- JUnit 5 (`org.junit.jupiter:junit-jupiter`) for unit tests
- Mockito (`org.mockito:mockito-core`) for mocking in tests
- AssertJ (`org.assertj:assertj-core`) for fluent assertions in tests

## Main Project Folders

### `src/main/java/za/co/wethinkcode/taskmanager/`
This is the primary source code area.

- `app/`
  - Contains core application logic.
  - Example: `TaskManager.java` manages creation, updates, deletion, and query operations for tasks.

- `cli/`
  - Contains the command-line interface layer.
  - Example: `TaskManagerCli.java` is the application entry point and command parser.

- `model/`
  - Contains domain model classes for tasks.
  - Examples: `Task.java`, `TaskPriority.java`, `TaskStatus.java`.

- `storage/`
  - Contains persistence logic.
  - Example: `TaskStorage.java` handles saving and loading task data to/from JSON.

- `util/`
  - Contains supporting helper classes and utilities.
  - Typically used for text parsing, merging, or formatting logic.

### `src/test/java/za/co/wethinkcode/taskmanager/`
Contains unit tests for the application logic and utility helpers.
- `app/` - Tests for core task manager behavior.
- `util/` - Tests for helper classes.

## Application Entry Points
- `src/main/java/za/co/wethinkcode/taskmanager/cli/TaskManagerCli.java`
  - This class contains `public static void main(String[] args)`.
  - It is configured as the main class in `build.gradle.kts`.
- `src/main/java/za/co/wethinkcode/taskmanager/app/TaskManager.java`
  - This is the central application service for task operations.

## How the Application Runs
- Build: `./gradlew build`
- Run: `./gradlew run --args="<command>"`
- Example commands:
  - `create <title> [description] [priority] [due_date] [tags]`
  - `list [-s <status>] [-p <priority>] [-o]`
  - `status <task_id> <new_status>`
  - `priority <task_id> <new_priority>`
  - `delete <task_id>`

## Recommended Exploration Exercise
1. Open `TaskManagerCli.java` and identify how command arguments are parsed.
2. Open `TaskManager.java` and trace the flow of one command, such as `create` or `list`.
3. Run the app with a sample command:
   - `./gradlew run --args="create 'Test Task' 'Sample' 2 2026-12-31 work"`
   - `./gradlew run --args="list"`
4. Inspect `tasks.json` after running commands to verify how task data is saved.

## Notes
- The project does not use Maven or a database.
- Task state and persistence are managed through a JSON file rather than a relational storage system.
- The package path follows a Java reverse-domain naming style: `za.co.wethinkcode.taskmanager`.
