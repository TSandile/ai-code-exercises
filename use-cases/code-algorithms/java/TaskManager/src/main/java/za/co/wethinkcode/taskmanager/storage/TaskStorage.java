// src/main/java/taskmanager/storage/TaskStorage.java
package za.co.wethinkcode.taskmanager.storage;

import com.google.gson.*;
import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;
import za.co.wethinkcode.taskmanager.model.TaskStatus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TaskStorage {
    private final String storagePath;
    private final Map<String, Task> tasks;
    private final Gson gson;

    public TaskStorage(String storagePath) {
        this.storagePath = storagePath;
        this.tasks = new HashMap<>();

        // Configure Gson with custom adapters for LocalDateTime
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
                .setPrettyPrinting()
                .create();

        load();
    }

    public void load() {
        File file = new File(storagePath);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Task[] loadedTasks = gson.fromJson(reader, Task[].class);
                if (loadedTasks != null) {
                    for (Task task : loadedTasks) {
                        tasks.put(task.getId(), task);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading tasks: " + e.getMessage());
            }
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(storagePath)) {
            gson.toJson(tasks.values(), writer);
        } catch (IOException e) {
            System.err.println("Error saving tasks: " + e.getMessage());
        }
    }

    public String addTask(Task task) {
        tasks.put(task.getId(), task);
        save();
        return task.getId();
    }

    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    public boolean updateTask(String taskId, Task updates) {
        Task task = getTask(taskId);
        if (task != null) {
            task.update(updates);
            save();
            return true;
        }
        return false;
    }

    public boolean deleteTask(String taskId) {
        if (tasks.containsKey(taskId)) {
            tasks.remove(taskId);
            save();
            return true;
        }
        return false;
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    public List<Task> getTasksByStatus(TaskStatus status) {
        return tasks.values().stream()
                .filter(task -> task.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Task> getTasksByPriority(TaskPriority priority) {
        return tasks.values().stream()
                .filter(task -> task.getPriority() == priority)
                .collect(Collectors.toList());
    }

    public List<Task> getOverdueTasks() {
        return tasks.values().stream()
                .filter(Task::isOverdue)
                .collect(Collectors.toList());
    }

    /**
     * Export tasks to a CSV file placed in the same directory as the JSON storage
     * file.
     * 
     * @param csvFileName file name to create (e.g. "tasks.csv")
     * @return true on success, false on error
     */
    public boolean exportToCsv(String csvFileName) {
        File storageFile = new File(storagePath);
        File parentDir = storageFile.getAbsoluteFile().getParentFile();
        File csvFile = parentDir != null ? new File(parentDir, csvFileName) : new File(csvFileName);

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
            // Header
            pw.println("id,title,description,priority,status,dueDate,tags,createdAt,updatedAt,completedAt");

            for (Task t : tasks.values()) {
                String tags = String.join(";", t.getTags());
                String due = t.getDueDate() != null ? t.getDueDate().toString() : "";
                String completed = t.getCompletedAt() != null ? t.getCompletedAt().toString() : "";

                pw.print(csvEscape(t.getId()));
                pw.print(',');
                pw.print(csvEscape(t.getTitle()));
                pw.print(',');
                pw.print(csvEscape(t.getDescription()));
                pw.print(',');
                pw.print(t.getPriority() != null ? t.getPriority().getValue() : "");
                pw.print(',');
                pw.print(t.getStatus() != null ? t.getStatus().getValue() : "");
                pw.print(',');
                pw.print(csvEscape(due));
                pw.print(',');
                pw.print(csvEscape(tags));
                pw.print(',');
                pw.print(csvEscape(t.getCreatedAt().toString()));
                pw.print(',');
                pw.print(csvEscape(t.getUpdatedAt().toString()));
                pw.print(',');
                pw.println(csvEscape(completed));
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error exporting tasks to CSV: " + e.getMessage());
            return false;
        }
    }

    // Helper to escape CSV fields by wrapping in quotes and doubling internal
    // quotes
    private String csvEscape(String s) {
        if (s == null)
            return "";
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    // Custom serializer for LocalDateTime
    private static class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }
    }

    // Custom deserializer for LocalDateTime
    private static class LocalDateTimeDeserializer implements JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
