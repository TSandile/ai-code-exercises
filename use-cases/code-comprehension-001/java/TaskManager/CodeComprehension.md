# Codebase Comprehension

Use of AI prompts to understand the Task Manager codebase since is an unfamiliar project I have encountered.

## My Initial Understanding

-This is a system that hekp user managaer a Task(Peforem Task CRUD operations).
-User able to manage a sing task per session, but can retreive list of all task.
-The tech stack: JAVA, gradle, Kotlin and uses comman line instead of web.

## Final Understanding of The Task Manager Codebase

-Initial understanding validated and correct

### Corrections

-Project does not use kotlin, build file is written in gradle Kotlin DSL, source code is all in Java
-package path: za.co.wethinkcode.taskmanager

## The Most Valuable Insights Gained From Each Prompt

-TaskManager.java = Contains core task business login/service methods
-TaskManagerCli.java = Is the actual entry point and CLI command dispatcher
-What each main folder likely contains

1. app : Core Business logic and application service layer.
2. cli: Command-line interface and program entry
3. model: Domain model class and enums
4. storage: Persistence layer/ file-based storage
5. Utility helpers and reusable support code

## My Approach To Implementing New Business Rule

## Strategies I developed For Approaching Unfamiliar Code
