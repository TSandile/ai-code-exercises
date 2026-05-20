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

    /**
     * JSON serializer for {@link java.time.LocalDateTime}.
     *
     * <p>Converts a {@code LocalDateTime} instance into a JSON string using the
     * {@link java.time.format.DateTimeFormatter#ISO_LOCAL_DATE_TIME} format.
     * This class is registered with Gson so date/time fields on {@code Task}
     * objects are written as ISO-8601 formatted strings (for example
     * {@code 2026-05-20T15:30:00}).</p>
     *
     * <h3>Example</h3>
     * <pre>
     * Gson gson = new GsonBuilder()
     *     .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
     *     .create();
     * String json = gson.toJson(localDateTimeInstance);
     * </pre>
     *
     * <h3>Notes</h3>
     * <ul>
     *   <li>The serializer uses ISO_LOCAL_DATE_TIME and does not include timezone
     *   information.</li>
     *   <li>Gson normally skips null values; this serializer does not handle
     *   {@code null} explicitly and assumes {@code src} is non-null when called.</li>
     *   <li>Precision is preserved to the granularity provided by
     *   {@link LocalDateTime#toString()} formatted by the ISO formatter.</li>
     * </ul>
     */
    private static class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        /**
         * Serialize a {@link LocalDateTime} to a {@link JsonElement}.
         *
         * @param src the {@code LocalDateTime} instance to serialize (expected non-null)
         * @param typeOfSrc the actual type (usually {@code LocalDateTime.class})
         * @param context serialization context provided by Gson (not used)
         * @return a {@link JsonElement} containing the ISO-8601 formatted date-time string
         */
        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }
    }

    /**
     * JSON deserializer for {@link java.time.LocalDateTime}.
     *
     * <p>Parses ISO-8601 date-time strings produced by
     * {@link LocalDateTimeSerializer} and converts them back into
     * {@code LocalDateTime} instances using {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}.</p>
     *
     * <h3>Example</h3>
     * <pre>
     * Gson gson = new GsonBuilder()
     *     .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
     *     .create();
     * LocalDateTime dt = gson.fromJson("\"2026-05-20T15:30:00\"", LocalDateTime.class);
     * </pre>
     *
     * <h3>Notes &amp; Edge Cases</h3>
     * <ul>
     *   <li>If the JSON value is not a valid ISO_LOCAL_DATE_TIME string, a
     *   {@link com.google.gson.JsonParseException} will be thrown.</li>
     *   <li>Timezone/offset information is not supported by this parser; strings
     *   containing offsets (e.g. {@code 2026-05-20T15:30:00Z} or
     *   {@code 2026-05-20T15:30:00+02:00}) will fail to parse with
     *   {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}.</li>
     *   <li>Null checks: Gson may not call the deserializer for {@code null}
     *   values; callers should assume the JSON element is non-null.</li>
     * </ul>
     */
    private static class LocalDateTimeDeserializer implements JsonDeserializer<LocalDateTime> {
        /**
         * Deserialize a {@link JsonElement} containing an ISO-8601 date-time
         * string into a {@link LocalDateTime}.
         *
         * @param json the {@code JsonElement} expected to contain a JSON string
         *             formatted as ISO-8601 local date-time
         * @param typeOfT the target type (usually {@code LocalDateTime.class})
         * @param context deserialization context provided by Gson (not used)
         * @return the parsed {@link LocalDateTime}
         * @throws JsonParseException if the JSON is not a valid ISO_LOCAL_DATE_TIME string
         */
        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
