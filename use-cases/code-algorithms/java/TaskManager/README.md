# Task Manager

## Build Instructions

To build the project:

```bash
./gradlew build
```

## Run instructions

To run the project:

```bash
./gradlew run --args="<commands>"
```

Available commands:

- `create <title> [description] [priority] [due_date] [tags]` - Create a new task
- `list [-s <status>] [-p <priority>] [-o]` - List tasks
- `status <task_id> <new_status>` - Update task status
- `priority <task_id> <new_priority>` - Update task priority
- `due <task_id> <new_due_date>` - Update task due date
- `tag <task_id> <tag>` - Add tag to task
- `untag <task_id> <tag>` - Remove tag from task
- `show <task_id>` - Show task details
- `delete <task_id>` - Delete task
- `stats` - Show task statistics
- `export [filename]` - Export tasks to CSV (default `tasks.csv`)

Examples:

```bash


./gradlew run --args="list"

# Export tasks to CSV next to tasks.json
./gradlew run --args="export tasks.csv"
```

```bash


./gradlew run --args="list"
```

## Test instructions

To run the tests:

```bash
./gradlew test
```
