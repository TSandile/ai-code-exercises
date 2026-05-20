# ImageProcessor Memory Fix

## Summary

The fix changes `processImageFolder()` to process images sequentially instead of keeping all loaded and processed images in memory at the same time.

### Before fix

- The original code loaded every image from `sample_images` into a `List<BufferedImage>`.
- It then created a second `List<BufferedImage>` containing processed copies.
- This held two full-size images per file simultaneously.
- Result: memory usage scaled with the number of images and image resolution, causing `OutOfMemoryError` when batches were large.

### Before fix result

- The heap grew steadily as each image was read and retained in memory.
- After loading a large batch, memory usage could reach hundreds or thousands of megabytes depending on image dimensions.
- A second pass that created a processed copy of every image made the peak memory roughly double the original batch size.
- In practice, this often led to `java.lang.OutOfMemoryError` before the application finished saving all processed files.

=== Memory Stats at END ===
Used Memory: 1747 MB
Free Memory: 572 MB
Total Memory: 2320 MB
Maximum Memory: 4058 MB
==============================

### After fix

- The updated code reads one image file at a time.
- It immediately applies the effect and writes the processed image to disk.
- It drops the references to the original and processed `BufferedImage` objects before moving to the next file.
- This keeps the heap usage bounded to roughly one original image plus one processed image at a time.

## Code change

The main change is in `ImageProcessor.processImageFolder()`:

- removed `List<BufferedImage> images`
- removed `List<BufferedImage> processedImages`
- replaced batch loading with a per-file read/process/write loop

## Verified result

The fixed version was built and run successfully with Gradle. Output shows the memory profile and that all images were processed.

### Sample runtime output from fixed version

```
=== Memory Stats at START ===
Used Memory: 2 MB
Free Memory: 251 MB
Total Memory: 254 MB
Maximum Memory: 4058 MB
==============================

Processing images one by one to reduce memory usage...
... (image processing output) ...

=== Memory Stats at END ===
Used Memory: 112 MB
Free Memory: 141 MB
Total Memory: 254 MB
Maximum Memory: 4058 MB
==============================

All images processed successfully
```

## Why this fixes the issue

- `BufferedImage` objects are large because they store pixel data in memory.
- Holding all source and processed images at once multiplies that cost.
- Sequential processing keeps only one pair of images alive at a time.
- Garbage collection can reclaim each image before the next one is loaded.

## Next recommendations

- If images are extremely large, consider scaling them down before processing.
- If further memory tuning is needed, use a profiler such as Java VisualVM or Java Mission Control.
- For production, avoid explicit `System.gc()` calls; rely on GC and keep object retention low.
