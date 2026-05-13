package za.co.wethinkcode.taskmanager.app;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;
import za.co.wethinkcode.taskmanager.model.TaskStatus;
import za.co.wethinkcode.taskmanager.storage.TaskStorage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskManager {
    private final TaskStorage storage;

    public TaskManager(String storagePath) {
        this.storage = new TaskStorage(storagePath);
    }

    TaskStorage getStorage() {
        return storage;
    }

    /**
     * Creates a new task with the specified parameters and stores it in the task
     * storage.
     * The task is initialized with a TODO status and the current timestamp as
     * creation time.
     *
     * @param title         the title of the task (required, cannot be null)
     * @param description   the detailed description of the task (can be null or
     *                      empty)
     * @param priorityValue the priority level as an integer value:
     *                      1 = LOW, 2 = MEDIUM, 3 = HIGH, 4 = URGENT
     * @param dueDateStr    the due date in ISO date format (YYYY-MM-DD), can be
     *                      null or empty
     *                      for tasks without a due date
     * @param tags          a list of string tags to categorize the task, can be
     *                      null (treated as empty list)
     * @return the unique ID of the created task as a String, or null if the due
     *         date format is invalid
     * @throws IllegalArgumentException if the priorityValue does not correspond to
     *                                  a valid TaskPriority
     *                                  (must be 1, 2, 3, or 4)
     *
     * @example
     * 
     *          <pre>
     *          TaskManager manager = new TaskManager("tasks.json");
     *          String taskId = manager.createTask(
     *                  "Complete project report",
     *                  "Write and submit the quarterly project report",
     *                  3, // HIGH priority
     *                  "2024-12-31", // Due date
     *                  Arrays.asList("work", "urgent", "report") // Tags
     *          );
     *          if (taskId != null) {
     *              System.out.println("Task created with ID: " + taskId);
     *          } else {
     *              System.out.println("Failed to create task - invalid date format");
     *          }
     *          </pre>
     *
     * @note
     *       - The task ID is automatically generated as a UUID string
     *       - Invalid date formats will print an error message to stderr and return
     *       null
     *       - Priority values outside the range 1-4 will throw
     *       IllegalArgumentException
     *       - Due dates are stored as LocalDateTime at the end of the specified day
     *       (23:59:59.999999999)
     *       - Null or empty tag lists are converted to empty ArrayLists
     *       - The task status is automatically set to TODO upon creation
     */
    public String createTask(String title, String description, int priorityValue,
            String dueDateStr, List<String> tags) {
        TaskPriority priority = TaskPriority.fromValue(priorityValue);
        LocalDateTime dueDate = null;

        if (dueDateStr != null && !dueDateStr.isEmpty()) {
            try {
                LocalDate localDate = LocalDate.parse(dueDateStr, DateTimeFormatter.ISO_DATE);
                dueDate = LocalDateTime.of(localDate, LocalTime.MAX);
            } catch (DateTimeParseException e) {
                System.err.println("Invalid date format. Use YYYY-MM-DD");
                return null;
            }
        }

        Task task = new Task(title, description, priority, dueDate, tags);
        return getStorage().addTask(task);
    }

    public List<Task> listTasks(String statusFilter, Integer priorityFilter, boolean showOverdue) {
        if (showOverdue) {
            return getStorage().getOverdueTasks();
        }

        if (statusFilter != null) {
            TaskStatus status = TaskStatus.fromValue(statusFilter);
            return getStorage().getTasksByStatus(status);
        }

        if (priorityFilter != null) {
            TaskPriority priority = TaskPriority.fromValue(priorityFilter);
            return getStorage().getTasksByPriority(priority);
        }

        return getStorage().getAllTasks();
    }

    public boolean updateTaskStatus(String taskId, String newStatusValue) {
        TaskStatus newStatus = TaskStatus.fromValue(newStatusValue);
        Task task = getStorage().getTask(taskId);
        if (task != null) {
            task.setStatus(newStatus);
            if (newStatus == TaskStatus.DONE) {
                task.markAsDone();
            }
            getStorage().save();
            return true;
        }
        return false;
    }

    public boolean updateTaskPriority(String taskId, int newPriorityValue) {

        TaskPriority newPriority = null;
        try {
            newPriority = TaskPriority.fromValue(newPriorityValue);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return false;
        }

        Task updates = new Task("tempTitle");
        updates.setPriority(newPriority);
        return getStorage().updateTask(taskId, updates);
    }

    public boolean updateTaskDueDate(String taskId, String dueDateStr) {
        try {
            LocalDate localDate = LocalDate.parse(dueDateStr, DateTimeFormatter.ISO_DATE);
            LocalDateTime dueDate = LocalDateTime.of(localDate, LocalTime.MAX);

            Task updates = new Task("tempTitle");
            updates.setDueDate(dueDate);
            return getStorage().updateTask(taskId, updates);
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date format. Use YYYY-MM-DD");
            return false;
        }
    }

    public boolean deleteTask(String taskId) {
        return getStorage().deleteTask(taskId);
    }

    public Task getTaskDetails(String taskId) {
        return getStorage().getTask(taskId);
    }

    public boolean addTagToTask(String taskId, String tag) {
        Task task = getStorage().getTask(taskId);
        if (task != null) {
            task.addTag(tag);
            getStorage().save();
            return true;
        }
        return false;
    }

    public boolean removeTagFromTask(String taskId, String tag) {
        Task task = getStorage().getTask(taskId);
        if (task != null && task.removeTag(tag)) {
            getStorage().save();
            return true;
        }
        return false;
    }

    public Map<String, Object> getStatistics() {
        List<Task> tasks = getStorage().getAllTasks();
        int total = tasks.size();

        // Count by status
        Map<String, Integer> statusCounts = new HashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            statusCounts.put(status.getValue(), 0);
        }

        for (Task task : tasks) {
            String statusValue = task.getStatus().getValue();
            statusCounts.put(statusValue, statusCounts.get(statusValue) + 1);
        }

        // Count by priority
        Map<Integer, Integer> priorityCounts = new HashMap<>();
        for (TaskPriority priority : TaskPriority.values()) {
            priorityCounts.put(priority.getValue(), 0);
        }

        for (Task task : tasks) {
            int priorityValue = task.getPriority().getValue();
            priorityCounts.put(priorityValue, priorityCounts.get(priorityValue) + 1);
        }

        // Count overdue
        int overdueCount = (int) tasks.stream().filter(Task::isOverdue).count();

        // Count completed in last 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        int completedRecently = (int) tasks.stream()
                .filter(task -> task.getCompletedAt() != null && task.getCompletedAt().isAfter(sevenDaysAgo))
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("byStatus", statusCounts);
        stats.put("byPriority", priorityCounts);
        stats.put("overdue", overdueCount);
        stats.put("completedLastWeek", completedRecently);

        return stats;
    }
}
