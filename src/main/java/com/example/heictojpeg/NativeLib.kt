package com.example.heictojpeg

object NativeLib {

    init {
        // Load dependencies in the correct order
        try {
            System.loadLibrary("de265")      // Load libde265 first
            System.loadLibrary("heif")       // Then load libheif
            System.loadLibrary("heic_to_jpeg_rust") // Finally our main library
        } catch (e: UnsatisfiedLinkError) {
            throw RuntimeException("Failed to load native libraries: ${e.message}", e)
        }
    }

    /**
     * Test if the native library is loaded correctly
     * @return A success message from the Rust library
     */
    external fun testConnection(): String

    /**
     * Converts a HEIC file to JPEG format
     * @param inputPath Absolute path to the input HEIC file
     * @param outputPath Absolute path for the output JPEG file
     * @return A message indicating success or failure with details
     */
    external fun convertHeicToJpeg(inputPath: String, outputPath: String): String

    /**
     * Converts a batch of HEIC files to JPEG format.
     * @param inputPaths Array of absolute paths to the input HEIC files.
     * @param outputDir Absolute path to the directory where output JPEGs will be saved.
     * @return A message indicating the result of the batch operation.
     */
    external fun convertHeicBatchToJpeg(inputPaths: Array<String>, outputDir: String): String
}
